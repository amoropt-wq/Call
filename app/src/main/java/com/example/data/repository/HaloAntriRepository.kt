package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.AudioClipEntity
import com.example.data.local.CallLogEntity
import com.example.data.local.CallQueueItemEntity
import com.example.data.local.EmailTemplateEntity
import com.example.data.local.HaloAntriDao
import com.example.data.local.QueueSource
import com.example.data.local.QueueStatus
import com.example.data.local.WhatsAppAccountEntity
import com.example.data.local.WhatsAppFlavor
import com.example.data.prefs.HaloAntriSettings
import com.example.data.prefs.SettingsPreferences
import com.example.domain.EmailTemplateBuilder
import com.example.domain.PhoneNumberExtractor
import com.example.service.SpeakerAudioPlayerController
import kotlinx.coroutines.flow.Flow

class HaloAntriRepository(
  private val dao: HaloAntriDao,
  private val settingsPreferences: SettingsPreferences,
) {

  val accounts: Flow<List<WhatsAppAccountEntity>> = dao.observeAccounts()
  val activeAccount: Flow<WhatsAppAccountEntity?> = dao.observeActiveAccount()
  val queueItems: Flow<List<CallQueueItemEntity>> = dao.observeQueue()
  val callLogs: Flow<List<CallLogEntity>> = dao.observeCallLogs()
  val emailTemplates: Flow<List<EmailTemplateEntity>> = dao.observeEmailTemplates()
  val defaultEmailTemplate: Flow<EmailTemplateEntity?> = dao.observeDefaultEmailTemplate()
  val audioClips: Flow<List<AudioClipEntity>> = dao.observeAudioClips()
  val settings: Flow<HaloAntriSettings> = settingsPreferences.settingsFlow

  suspend fun ensureDefaultEmailTemplate() {
    if (dao.getEmailTemplateCount() == 0) {
      dao.insertEmailTemplate(EmailTemplateBuilder.defaultTemplate())
    }
  }

  suspend fun saveEmailTemplate(template: EmailTemplateEntity, makeDefault: Boolean): Long {
    val now = System.currentTimeMillis()
    val payload = template.copy(updatedAt = now, isDefault = makeDefault || template.isDefault)
    if (makeDefault) {
      dao.clearDefaultEmailTemplates()
    }
    return if (payload.id > 0L) {
      dao.updateEmailTemplate(payload)
      payload.id
    } else {
      dao.insertEmailTemplate(payload)
    }
  }

  suspend fun setDefaultEmailTemplate(templateId: Long) {
    dao.setDefaultEmailTemplate(templateId)
  }

  suspend fun deleteEmailTemplate(templateId: Long) {
    dao.deleteEmailTemplateById(templateId)
    ensureDefaultEmailTemplate()
  }

  suspend fun saveAudioClip(clip: AudioClipEntity): Long {
    return dao.insertAudioClip(clip)
  }

  suspend fun renameAudioClip(clip: AudioClipEntity, newName: String) {
    val cleanName = newName.trim()
    if (cleanName.isNotBlank()) {
      dao.updateAudioClip(clip.copy(name = cleanName))
    }
  }

  suspend fun deleteAudioClip(clip: AudioClipEntity) {
    dao.deleteAudioClipById(clip.id)
    SpeakerAudioPlayerController.deleteStoredFile(clip.filePath)
  }

  suspend fun addWhatsAppAccount(
    label: String,
    phoneNumber: String,
    flavor: WhatsAppFlavor,
    makeActive: Boolean = false,
  ): Long {
    val normalized = PhoneNumberExtractor.normalizePhoneNumber(phoneNumber)
    val shouldBeActive = makeActive || dao.getAccountCount() == 0
    if (shouldBeActive) {
      dao.clearAllActiveAccounts()
    }
    return dao.insertAccount(
      WhatsAppAccountEntity(
        label = label.trim(),
        phoneNumber = normalized,
        appFlavor = flavor,
        isActive = shouldBeActive,
      )
    )
  }

  suspend fun updateWhatsAppAccount(account: WhatsAppAccountEntity) {
    val normalized = PhoneNumberExtractor.normalizePhoneNumber(account.phoneNumber)
    dao.updateAccount(account.copy(label = account.label.trim(), phoneNumber = normalized))
  }

  suspend fun setActiveAccount(accountId: Long) {
    dao.setActiveAccount(accountId)
  }

  suspend fun deleteAccount(account: WhatsAppAccountEntity) {
    dao.deleteAccountById(account.id)
  }

  suspend fun addOrUpdateManualContact(
    existingId: Long? = null,
    name: String,
    phoneNumber: String,
    idNumber: String,
    email: String = "",
    notes: String = "",
    priority: Int = 0,
  ): Long {
    val normalized = PhoneNumberExtractor.normalizePhoneNumber(phoneNumber)
    val cleanEmail = email.trim()
    val now = System.currentTimeMillis()
    if (existingId != null && existingId > 0L) {
      val existingItem = dao.findActiveQueueItemByPhone(normalized)
      val item =
        CallQueueItemEntity(
          id = existingId,
          name = name.trim().ifEmpty { PhoneNumberExtractor.formatDisplayPhone(normalized) },
          phoneNumber = normalized,
          idNumber = idNumber.trim(),
          email = cleanEmail,
          notes = notes.trim(),
          source = existingItem?.takeIf { it.id == existingId }?.source ?: QueueSource.MANUAL,
          senderName = existingItem?.takeIf { it.id == existingId }?.senderName.orEmpty(),
          capturedMessage = existingItem?.takeIf { it.id == existingId }?.capturedMessage.orEmpty(),
          status = QueueStatus.WAITING,
          priority = priority,
          updatedAt = now,
        )
      dao.updateQueueItem(item)
      return existingId
    }

    val existingActive = dao.findActiveQueueItemByPhone(normalized)
    return if (existingActive != null) {
      dao.updateQueueItem(
        existingActive.copy(
          name = name.trim().ifEmpty { existingActive.name },
          idNumber = idNumber.trim().ifEmpty { existingActive.idNumber },
          email = cleanEmail.ifEmpty { existingActive.email },
          notes = notes.trim().ifEmpty { existingActive.notes },
          priority = maxOf(existingActive.priority, priority),
          updatedAt = now,
        )
      )
      existingActive.id
    } else {
      dao.insertQueueItem(
        CallQueueItemEntity(
          name = name.trim().ifEmpty { PhoneNumberExtractor.formatDisplayPhone(normalized) },
          phoneNumber = normalized,
          idNumber = idNumber.trim(),
          email = cleanEmail,
          notes = notes.trim(),
          source = QueueSource.MANUAL,
          status = QueueStatus.WAITING,
          priority = priority,
          createdAt = now,
          updatedAt = now,
        )
      )
    }
  }

  suspend fun enqueueFromCapturedMessage(
    senderTitle: String,
    messageText: String,
    source: QueueSource,
  ): Int {
    val parsed = PhoneNumberExtractor.extractStructuredContact(senderTitle, messageText)
    if (parsed.phoneNumbers.isEmpty()) return 0

    val cleanSenderName = parsed.senderName.ifBlank { source.label }
    val now = System.currentTimeMillis()
    var addedOrUpdated = 0

    for (phone in parsed.phoneNumbers) {
      val existing = dao.findActiveQueueItemByPhone(phone)
      if (existing != null) {
        val currentIsPhoneFallback =
          PhoneNumberExtractor.extractPhoneNumbers(existing.name).isNotEmpty()
        val updatedName =
          when {
            parsed.contactName.isNotBlank() &&
              (existing.name.isBlank() || currentIsPhoneFallback) -> parsed.contactName
            else -> existing.name
          }
        dao.updateQueueItem(
          existing.copy(
            name = updatedName,
            capturedMessage = messageText.take(500),
            senderName = existing.senderName.ifBlank { cleanSenderName },
            idNumber = existing.idNumber.ifBlank { parsed.idOrCardNumber },
            email = existing.email.ifBlank { parsed.email },
            updatedAt = now,
          )
        )
      } else {
        val resolvedName =
          parsed.contactName.ifBlank { PhoneNumberExtractor.formatDisplayPhone(phone) }
        dao.insertQueueItem(
          CallQueueItemEntity(
            name = resolvedName,
            phoneNumber = phone,
            idNumber = parsed.idOrCardNumber,
            email = parsed.email,
            source = source,
            senderName = cleanSenderName,
            capturedMessage = messageText.take(500),
            status = QueueStatus.WAITING,
            priority = 0,
            createdAt = now,
            updatedAt = now,
          )
        )
      }
      addedOrUpdated++
    }
    return addedOrUpdated
  }

  suspend fun updateQueueItemEmail(item: CallQueueItemEntity, email: String) {
    dao.updateQueueItem(item.copy(email = email.trim(), updatedAt = System.currentTimeMillis()))
  }

  suspend fun updateQueueStatus(itemId: Long, status: QueueStatus) {
    dao.updateQueueStatus(itemId, status)
  }

  suspend fun requeueItem(item: CallQueueItemEntity) {
    dao.updateQueueItem(
      item.copy(status = QueueStatus.WAITING, updatedAt = System.currentTimeMillis())
    )
  }

  suspend fun deleteQueueItem(itemId: Long) {
    dao.deleteQueueItemById(itemId)
  }

  suspend fun clearFinishedQueue() {
    dao.clearFinishedQueueItems()
  }

  suspend fun getNextWaitingItem(): CallQueueItemEntity? = dao.getNextWaitingItem()

  suspend fun getActiveAccount(): WhatsAppAccountEntity? = dao.getActiveAccount()

  suspend fun recordCallOutcome(
    contactName: String,
    phoneNumber: String,
    idNumber: String,
    account: WhatsAppAccountEntity?,
    stealthMuted: Boolean,
    durationSeconds: Int,
    outcome: String,
  ) {
    if (account != null) {
      dao.incrementCallCount(account.id)
    }
    dao.insertCallLog(
      CallLogEntity(
        contactName = contactName,
        phoneNumber = phoneNumber,
        idNumber = idNumber,
        waAccountLabel = account?.label ?: "WhatsApp Default",
        waAccountNumber = account?.phoneNumber ?: "-",
        stealthMuted = stealthMuted,
        durationSeconds = durationSeconds,
        outcome = outcome,
      )
    )
  }

  suspend fun clearCallLogs() {
    dao.clearCallLogs()
  }

  suspend fun updateSettings(transform: (HaloAntriSettings) -> HaloAntriSettings) {
    settingsPreferences.updateSettings(transform)
  }

  companion object {
    @Volatile private var INSTANCE: HaloAntriRepository? = null

    fun getInstance(context: Context): HaloAntriRepository {
      return INSTANCE
        ?: synchronized(this) {
          val appContext = context.applicationContext
          val db = AppDatabase.getInstance(appContext)
          val prefs = SettingsPreferences(appContext)
          val repo = HaloAntriRepository(db.haloAntriDao(), prefs)
          INSTANCE = repo
          repo
        }
    }
  }
}
