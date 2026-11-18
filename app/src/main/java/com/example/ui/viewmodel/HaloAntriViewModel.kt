package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.example.data.local.AudioClipEntity
import com.example.data.local.CallLogEntity
import com.example.data.local.CallQueueItemEntity
import com.example.data.local.EmailTemplateEntity
import com.example.data.local.QueueSource
import com.example.data.local.QueueStatus
import com.example.data.local.WhatsAppAccountEntity
import com.example.data.local.WhatsAppFlavor
import com.example.data.prefs.HaloAntriSettings
import com.example.data.prefs.SmtpConfig
import com.example.data.repository.HaloAntriRepository
import com.example.domain.EmailTemplateBuilder
import com.example.domain.PhoneNumberExtractor
import com.example.service.AudioPlaybackState
import com.example.service.AudioRecordingState
import com.example.service.CallLaunchResult
import com.example.service.EmailSendResult
import com.example.service.RecordedAudioDraft
import com.example.service.SmtpEmailSender
import com.example.service.SpeakerAudioPlayerController
import com.example.service.SpeakerOutputRoute
import com.example.service.StealthAudioController
import com.example.service.WhatsAppCallLauncher
import com.example.service.WhatsAppNotificationListenerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MainTab {
  QUEUE,
  DIAL,
  EMAIL,
  ACCOUNTS,
  CAPTURE,
}

enum class QueueFilter(val label: String) {
  WAITING("Menunggu"),
  FINISHED("Selesai"),
  ALL("Semua"),
}

data class ActiveCallSession(
  val queueItemId: Long?,
  val contactName: String,
  val phoneNumber: String,
  val idNumber: String,
  val usingAccount: WhatsAppAccountEntity?,
  val startedAtMillis: Long,
  val elapsedSeconds: Int = 0,
  val launchStatusMessage: String = "",
)

data class WhatsAppWebSession(
  val account: WhatsAppAccountEntity?,
  val targetPhoneNumber: String? = null,
)

data class HaloAntriUiState(
  val selectedTab: MainTab = MainTab.QUEUE,
  val queueFilter: QueueFilter = QueueFilter.WAITING,
  val selectedSenderFilter: String? = null,
  val searchQuery: String = "",
  val queueItems: List<CallQueueItemEntity> = emptyList(),
  val accounts: List<WhatsAppAccountEntity> = emptyList(),
  val activeAccount: WhatsAppAccountEntity? = null,
  val callLogs: List<CallLogEntity> = emptyList(),
  val settings: HaloAntriSettings = HaloAntriSettings(),
  val emailTemplates: List<EmailTemplateEntity> = emptyList(),
  val defaultEmailTemplate: EmailTemplateEntity = EmailTemplateBuilder.defaultTemplate(),
  val isStealthMuted: Boolean = false,
  val audioModeLabel: String = "Siaga Normal",
  val isNotificationAccessGranted: Boolean = false,
  val activeCallSession: ActiveCallSession? = null,
  val whatsAppWebSession: WhatsAppWebSession? = null,
  val editingQueueItem: CallQueueItemEntity? = null,
  val showManualContactSheet: Boolean = false,
  val editingAccount: WhatsAppAccountEntity? = null,
  val showAccountSheet: Boolean = false,
  val sendingEmailContact: CallQueueItemEntity? = null,
  val editingEmailTemplate: EmailTemplateEntity? = null,
  val showEmailTemplateEditor: Boolean = false,
  val isSendingEmail: Boolean = false,
  val isTestingSmtp: Boolean = false,
  val smtpTestStatusMessage: String? = null,
  val audioClips: List<AudioClipEntity> = emptyList(),
  val audioPlaybackState: AudioPlaybackState = AudioPlaybackState(),
  val audioRecordingState: AudioRecordingState = AudioRecordingState(),
  val pendingRecordedDraft: RecordedAudioDraft? = null,
  val showAudioSpeakerSheet: Boolean = false,
)

class HaloAntriViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val repository = HaloAntriRepository.getInstance(appContext)
  private val stealthAudioController = StealthAudioController(appContext)
  private val speakerAudioPlayer =
    SpeakerAudioPlayerController(appContext, viewModelScope, stealthAudioController)

  private val selectedTab = MutableStateFlow(MainTab.QUEUE)
  private val queueFilter = MutableStateFlow(QueueFilter.WAITING)
  private val selectedSenderFilter = MutableStateFlow<String?>(null)
  private val searchQuery = MutableStateFlow("")
  private val notificationAccessGranted =
    MutableStateFlow(WhatsAppNotificationListenerService.isNotificationAccessEnabled(appContext))
  private val activeCallSession = MutableStateFlow<ActiveCallSession?>(null)
  private val whatsAppWebSession = MutableStateFlow<WhatsAppWebSession?>(null)
  private val editingQueueItem = MutableStateFlow<CallQueueItemEntity?>(null)
  private val showManualContactSheet = MutableStateFlow(false)
  private val editingAccount = MutableStateFlow<WhatsAppAccountEntity?>(null)
  private val showAccountSheet = MutableStateFlow(false)
  private val sendingEmailContact = MutableStateFlow<CallQueueItemEntity?>(null)
  private val editingEmailTemplate = MutableStateFlow<EmailTemplateEntity?>(null)
  private val showEmailTemplateEditor = MutableStateFlow(false)
  private val isSendingEmail = MutableStateFlow(false)
  private val isTestingSmtp = MutableStateFlow(false)
  private val smtpTestStatusMessage = MutableStateFlow<String?>(null)
  private val showAudioSpeakerSheet = MutableStateFlow(false)
  private val pendingRecordedDraft = MutableStateFlow<RecordedAudioDraft?>(null)

  private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

  private var callTimerJob: Job? = null

  private data class CoreDataBundle(
    val queue: List<CallQueueItemEntity>,
    val accounts: List<WhatsAppAccountEntity>,
    val activeAccount: WhatsAppAccountEntity?,
    val logs: List<CallLogEntity>,
    val settings: HaloAntriSettings,
    val emailTemplates: List<EmailTemplateEntity> = emptyList(),
    val defaultEmailTemplate: EmailTemplateEntity = EmailTemplateBuilder.defaultTemplate(),
  )

  private data class AudioAndUiBundle(
    val tab: MainTab,
    val filter: QueueFilter,
    val senderFilter: String? = null,
    val query: String,
    val muted: Boolean,
    val audioMode: String,
    val notifAccess: Boolean,
    val activeSession: ActiveCallSession?,
  )

  private data class SheetBundle(
    val editQueue: CallQueueItemEntity?,
    val showContactSheet: Boolean,
    val editAccount: WhatsAppAccountEntity?,
    val showAccountSheet: Boolean,
    val webSession: WhatsAppWebSession?,
  )

  private data class EmailUiBundle(
    val sendingContact: CallQueueItemEntity?,
    val editingTemplate: EmailTemplateEntity?,
    val showTemplateEditor: Boolean,
    val isSending: Boolean,
    val isTestingSmtp: Boolean,
    val smtpStatus: String?,
  )

  private data class SpeakerAudioBundle(
    val clips: List<AudioClipEntity>,
    val playbackState: AudioPlaybackState,
    val recordingState: AudioRecordingState,
    val pendingDraft: RecordedAudioDraft?,
    val showSheet: Boolean,
  )

  private val coreDataFlow =
    combine(
      repository.queueItems,
      repository.accounts,
      repository.activeAccount,
      repository.callLogs,
      repository.settings,
    ) { queue, accounts, active, logs, settings ->
      CoreDataBundle(queue, accounts, active, logs, settings)
    }.combine(repository.emailTemplates) { bundle, templates ->
      bundle.copy(emailTemplates = templates)
    }.combine(repository.defaultEmailTemplate) { bundle, defaultTpl ->
      bundle.copy(
        defaultEmailTemplate =
          defaultTpl ?: bundle.emailTemplates.firstOrNull() ?: EmailTemplateBuilder.defaultTemplate()
      )
    }

  private val audioAndUiFlow =
    combine(
      selectedTab,
      queueFilter,
      searchQuery,
      stealthAudioController.isMicMuted,
      stealthAudioController.audioModeLabel,
    ) { tab, filter, query, muted, audioMode ->
      AudioAndUiBundle(tab, filter, null, query, muted, audioMode, false, null)
    }.combine(selectedSenderFilter) { bundle, sender ->
      bundle.copy(senderFilter = sender)
    }.combine(notificationAccessGranted) { bundle, notif ->
      bundle.copy(notifAccess = notif)
    }.combine(activeCallSession) { bundle, session ->
      bundle.copy(activeSession = session)
    }

  private val sheetFlow =
    combine(
      editingQueueItem,
      showManualContactSheet,
      editingAccount,
      showAccountSheet,
      whatsAppWebSession,
    ) { editQ, showQ, editAcc, showAcc, webSession ->
      SheetBundle(editQ, showQ, editAcc, showAcc, webSession)
    }

  private val emailUiFlow =
    combine(
      sendingEmailContact,
      editingEmailTemplate,
      showEmailTemplateEditor,
      isSendingEmail,
      isTestingSmtp,
    ) { sendingContact, editTpl, showEditor, sending, testing ->
      EmailUiBundle(sendingContact, editTpl, showEditor, sending, testing, null)
    }.combine(smtpTestStatusMessage) { bundle, status ->
      bundle.copy(smtpStatus = status)
    }

  private val speakerAudioFlow =
    combine(
      repository.audioClips,
      speakerAudioPlayer.playbackState,
      speakerAudioPlayer.recordingState,
      pendingRecordedDraft,
      showAudioSpeakerSheet,
    ) { clips, playback, recording, draft, showSheet ->
      SpeakerAudioBundle(clips, playback, recording, draft, showSheet)
    }

  val uiState: StateFlow<HaloAntriUiState> =
    combine(coreDataFlow, audioAndUiFlow, sheetFlow, emailUiFlow, speakerAudioFlow) {
        core,
        ui,
        sheets,
        emailUi,
        speakerAudio ->
        HaloAntriUiState(
          selectedTab = ui.tab,
          queueFilter = ui.filter,
          selectedSenderFilter = ui.senderFilter,
          searchQuery = ui.query,
          queueItems = core.queue,
          accounts = core.accounts,
          activeAccount = core.activeAccount,
          callLogs = core.logs,
          settings = core.settings,
          emailTemplates = core.emailTemplates,
          defaultEmailTemplate = core.defaultEmailTemplate,
          isStealthMuted = ui.muted,
          audioModeLabel = ui.audioMode,
          isNotificationAccessGranted = ui.notifAccess,
          activeCallSession = ui.activeSession,
          whatsAppWebSession = sheets.webSession,
          editingQueueItem = sheets.editQueue,
          showManualContactSheet = sheets.showContactSheet,
          editingAccount = sheets.editAccount,
          showAccountSheet = sheets.showAccountSheet,
          sendingEmailContact = emailUi.sendingContact,
          editingEmailTemplate = emailUi.editingTemplate,
          showEmailTemplateEditor = emailUi.showTemplateEditor,
          isSendingEmail = emailUi.isSending,
          isTestingSmtp = emailUi.isTestingSmtp,
          smtpTestStatusMessage = emailUi.smtpStatus,
          audioClips = speakerAudio.clips,
          audioPlaybackState = speakerAudio.playbackState,
          audioRecordingState = speakerAudio.recordingState,
          pendingRecordedDraft = speakerAudio.pendingDraft,
          showAudioSpeakerSheet = speakerAudio.showSheet,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HaloAntriUiState(),
      )

  init {
    refreshSystemStates()
    viewModelScope.launch { repository.ensureDefaultEmailTemplate() }
  }

  fun refreshSystemStates() {
    notificationAccessGranted.value =
      WhatsAppNotificationListenerService.isNotificationAccessEnabled(appContext)
    stealthAudioController.syncHardwareState()
  }

  fun selectTab(tab: MainTab) {
    selectedTab.value = tab
    refreshSystemStates()
  }

  fun setQueueFilter(filter: QueueFilter) {
    queueFilter.value = filter
  }

  fun setSenderFilter(senderName: String?) {
    selectedSenderFilter.value = senderName?.trim()?.takeIf { it.isNotEmpty() }
  }

  fun setSearchQuery(query: String) {
    searchQuery.value = query
  }

  // --- Sheet Controls ---
  fun openAddManualContact() {
    editingQueueItem.value = null
    showManualContactSheet.value = true
  }

  fun openEditQueueItem(item: CallQueueItemEntity) {
    editingQueueItem.value = item
    showManualContactSheet.value = true
  }

  fun dismissManualContactSheet() {
    showManualContactSheet.value = false
    editingQueueItem.value = null
  }

  fun openAddWhatsAppAccount() {
    editingAccount.value = null
    showAccountSheet.value = true
  }

  fun openEditWhatsAppAccount(account: WhatsAppAccountEntity) {
    editingAccount.value = account
    showAccountSheet.value = true
  }

  fun dismissAccountSheet() {
    showAccountSheet.value = false
    editingAccount.value = null
  }

  // --- WhatsApp Accounts (Unlimited) ---
  fun saveWhatsAppAccount(
    existing: WhatsAppAccountEntity?,
    label: String,
    phoneNumber: String,
    flavor: WhatsAppFlavor,
    makeActive: Boolean,
  ) {
    val cleanLabel = label.trim()
    val normalized = PhoneNumberExtractor.normalizePhoneNumber(phoneNumber)
    if (cleanLabel.isBlank() || normalized.count { it.isDigit() } < 8) {
      _toastEvents.tryEmit("Isi nama label dan nomor WhatsApp yang valid.")
      return
    }
    viewModelScope.launch {
      if (existing != null) {
        repository.updateWhatsAppAccount(
          existing.copy(label = cleanLabel, phoneNumber = normalized, appFlavor = flavor)
        )
        if (makeActive) {
          repository.setActiveAccount(existing.id)
        }
        _toastEvents.tryEmit("Nomor WhatsApp \"$cleanLabel\" diperbarui.")
      } else {
        repository.addWhatsAppAccount(cleanLabel, normalized, flavor, makeActive)
        _toastEvents.tryEmit("Nomor WhatsApp \"$cleanLabel\" ditambahkan.")
      }
      dismissAccountSheet()
    }
  }

  fun activateAccount(accountId: Long) {
    viewModelScope.launch {
      repository.setActiveAccount(accountId)
      _toastEvents.tryEmit("Jalur WhatsApp pemanggil aktif diganti.")
    }
  }

  fun deleteAccount(account: WhatsAppAccountEntity) {
    viewModelScope.launch {
      repository.deleteAccount(account)
      if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        runCatching { ProfileStore.getInstance().deleteProfile("wa_account_${account.id}") }
      }
      _toastEvents.tryEmit("Nomor \"${account.label}\" dihapus.")
    }
  }

  fun openWhatsAppWeb(account: WhatsAppAccountEntity?, targetPhoneNumber: String? = null) {
    val resolvedAccount = account ?: uiState.value.activeAccount
    whatsAppWebSession.value =
      WhatsAppWebSession(account = resolvedAccount, targetPhoneNumber = targetPhoneNumber)
  }

  fun closeWhatsAppWeb() {
    whatsAppWebSession.value = null
  }

  fun enqueueFromWhatsAppWeb(sender: String, body: String) {
    viewModelScope.launch {
      val count =
        repository.enqueueFromCapturedMessage(
          senderTitle = sender,
          messageText = body,
          source = QueueSource.WHATSAPP_WEB,
        )
      if (count > 0) {
        _toastEvents.tryEmit("$count nomor dari WhatsApp Web masuk antrian.")
      }
    }
  }

  // --- Manual Entry & Queue Management ---
  fun saveManualContact(
    existingId: Long?,
    name: String,
    phoneNumber: String,
    idNumber: String,
    email: String = "",
    notes: String,
    priority: Int,
  ) {
    val normalized = PhoneNumberExtractor.normalizePhoneNumber(phoneNumber)
    if (normalized.count { it.isDigit() } < 8) {
      _toastEvents.tryEmit("Masukkan nomor HP yang valid (minimal 8 digit).")
      return
    }
    viewModelScope.launch {
      repository.addOrUpdateManualContact(
        existingId = existingId,
        name = name,
        phoneNumber = normalized,
        idNumber = idNumber,
        email = email,
        notes = notes,
        priority = priority,
      )
      _toastEvents.tryEmit(
        if (existingId != null) "Data kontak diperbarui." else "Kontak masuk ke antrian panggilan."
      )
      dismissManualContactSheet()
    }
  }

  fun testCaptureMessage(senderName: String, messageBody: String) {
    if (messageBody.isBlank() && senderName.isBlank()) {
      _toastEvents.tryEmit("Masukkan isi pesan contoh yang berisi nomor HP.")
      return
    }
    viewModelScope.launch {
      val count =
        repository.enqueueFromCapturedMessage(
          senderTitle = senderName,
          messageText = messageBody,
          source = QueueSource.NOTIFICATION_WA,
        )
      if (count > 0) {
        _toastEvents.tryEmit("$count nomor berhasil ditangkap & masuk antrian!")
      } else {
        _toastEvents.tryEmit("Tidak ditemukan nomor HP yang valid di dalam pesan tersebut.")
      }
    }
  }

  fun markQueueItemStatus(item: CallQueueItemEntity, status: QueueStatus) {
    viewModelScope.launch {
      repository.updateQueueStatus(item.id, status)
    }
  }

  fun requeueItem(item: CallQueueItemEntity) {
    viewModelScope.launch {
      repository.requeueItem(item)
      _toastEvents.tryEmit("${item.name} dikembalikan ke antrian.")
    }
  }

  fun deleteQueueItem(item: CallQueueItemEntity) {
    viewModelScope.launch {
      repository.deleteQueueItem(item.id)
    }
  }

  fun clearFinishedQueue() {
    viewModelScope.launch {
      repository.clearFinishedQueue()
      _toastEvents.tryEmit("Antrian yang sudah selesai dibersihkan.")
    }
  }

  // --- Stealth Mute & Active WhatsApp Calling ---
  fun toggleStealthMute() {
    val muted = stealthAudioController.toggleStealthMute()
    _toastEvents.tryEmit(
      if (muted) "Mode Bisu Senyap AKTIF — mikrofon dibisukan tanpa notifikasi ke lawan."
      else "Mikrofon kembali aktif."
    )
  }

  fun setStealthMute(muted: Boolean) {
    stealthAudioController.setStealthMute(muted)
  }

  fun callNextInQueue() {
    viewModelScope.launch {
      val next = repository.getNextWaitingItem()
      if (next == null) {
        _toastEvents.tryEmit("Antrian kosong. Tambahkan data manual atau tangkap pesan masuk.")
        return@launch
      }
      startCallForQueueItem(next)
    }
  }

  fun startCallForQueueItem(item: CallQueueItemEntity) {
    viewModelScope.launch {
      repository.updateQueueStatus(item.id, QueueStatus.CALLING)
      val activeAcc = repository.getActiveAccount()
      launchCallSession(
        queueItemId = item.id,
        name = item.name,
        phoneNumber = item.phoneNumber,
        idNumber = item.idNumber,
        account = activeAcc,
      )
    }
  }

  fun startDirectCall(name: String, phoneNumber: String, idNumber: String) {
    val normalized = PhoneNumberExtractor.normalizePhoneNumber(phoneNumber)
    if (normalized.count { it.isDigit() } < 8) {
      _toastEvents.tryEmit("Nomor HP tujuan tidak valid.")
      return
    }
    viewModelScope.launch {
      val activeAcc = repository.getActiveAccount()
      val displayName = name.trim().ifEmpty { PhoneNumberExtractor.formatDisplayPhone(normalized) }
      launchCallSession(
        queueItemId = null,
        name = displayName,
        phoneNumber = normalized,
        idNumber = idNumber.trim(),
        account = activeAcc,
      )
    }
  }

  private fun launchCallSession(
    queueItemId: Long?,
    name: String,
    phoneNumber: String,
    idNumber: String,
    account: WhatsAppAccountEntity?,
  ) {
    val currentSettings = uiState.value.settings
    if (currentSettings.stealthMuteAutoLock) {
      stealthAudioController.setStealthMute(true)
    } else {
      stealthAudioController.syncHardwareState()
    }

    val flavor = account?.appFlavor ?: WhatsAppFlavor.PERSONAL
    val launchResult = WhatsAppCallLauncher.launchWhatsAppCallSession(appContext, phoneNumber, flavor)
    val statusMsg =
      when (launchResult) {
        is CallLaunchResult.LaunchedWhatsApp ->
          "Membuka ${launchResult.flavorName} via ${account?.label ?: "Jalur Default"}"
        is CallLaunchResult.WhatsAppUnavailableFallback -> launchResult.reason
      }

    activeCallSession.value =
      ActiveCallSession(
        queueItemId = queueItemId,
        contactName = name,
        phoneNumber = phoneNumber,
        idNumber = idNumber,
        usingAccount = account,
        startedAtMillis = System.currentTimeMillis(),
        elapsedSeconds = 0,
        launchStatusMessage = statusMsg,
      )
    startCallTimer()
  }

  fun reopenWhatsAppForActiveCall() {
    val session = activeCallSession.value ?: return
    val flavor = session.usingAccount?.appFlavor ?: WhatsAppFlavor.PERSONAL
    val result = WhatsAppCallLauncher.launchWhatsAppCallSession(appContext, session.phoneNumber, flavor)
    if (result is CallLaunchResult.WhatsAppUnavailableFallback) {
      _toastEvents.tryEmit(result.reason)
    }
  }

  fun finishActiveCall(markCompleted: Boolean, callNextAutomatically: Boolean) {
    val session = activeCallSession.value ?: return
    callTimerJob?.cancel()
    val wasMuted = uiState.value.isStealthMuted

    viewModelScope.launch {
      session.queueItemId?.let { qId ->
        repository.updateQueueStatus(
          qId,
          if (markCompleted) QueueStatus.COMPLETED else QueueStatus.SKIPPED,
        )
      }
      repository.recordCallOutcome(
        contactName = session.contactName,
        phoneNumber = session.phoneNumber,
        idNumber = session.idNumber,
        account = session.usingAccount,
        stealthMuted = wasMuted,
        durationSeconds = session.elapsedSeconds,
        outcome = if (markCompleted) "Selesai" else "Dilewati",
      )
      activeCallSession.value = null

      if (callNextAutomatically && uiState.value.settings.autoAdvanceQueue) {
        val next = repository.getNextWaitingItem()
        if (next != null) {
          startCallForQueueItem(next)
          return@launch
        } else {
          _toastEvents.tryEmit("Semua antrian panggilan telah selesai.")
        }
      }

      if (!uiState.value.settings.stealthMuteAutoLock) {
        stealthAudioController.setStealthMute(false)
      }
    }
  }

  fun dismissActiveCallSessionWithoutCompleting() {
    val session = activeCallSession.value ?: return
    callTimerJob?.cancel()
    viewModelScope.launch {
      session.queueItemId?.let { qId ->
        repository.updateQueueStatus(qId, QueueStatus.WAITING)
      }
      activeCallSession.value = null
    }
  }

  private fun startCallTimer() {
    callTimerJob?.cancel()
    callTimerJob =
      viewModelScope.launch {
        while (isActive) {
          delay(500)
          stealthAudioController.syncHardwareState()
          val current = activeCallSession.value ?: break
          val elapsed = ((System.currentTimeMillis() - current.startedAtMillis) / 1000L).toInt()
          if (elapsed != current.elapsedSeconds) {
            activeCallSession.value = current.copy(elapsedSeconds = elapsed)
          }
        }
      }
  }

  // --- Email Sending, HTML/Visual Templates & SMTP Settings ---
  fun openSendEmailSheet(item: CallQueueItemEntity) {
    sendingEmailContact.value = item
  }

  fun dismissSendEmailSheet() {
    sendingEmailContact.value = null
  }

  fun openEmailTemplateEditor(template: EmailTemplateEntity? = null) {
    editingEmailTemplate.value = template
    showEmailTemplateEditor.value = true
  }

  fun dismissEmailTemplateEditor() {
    showEmailTemplateEditor.value = false
    editingEmailTemplate.value = null
  }

  fun saveEmailTemplate(template: EmailTemplateEntity, makeDefault: Boolean) {
    viewModelScope.launch {
      repository.saveEmailTemplate(template, makeDefault)
      dismissEmailTemplateEditor()
      _toastEvents.tryEmit("Template email \"${template.name}\" disimpan.")
    }
  }

  fun setDefaultEmailTemplate(templateId: Long) {
    viewModelScope.launch {
      repository.setDefaultEmailTemplate(templateId)
      _toastEvents.tryEmit("Template email default diperbarui.")
    }
  }

  fun deleteEmailTemplate(templateId: Long) {
    viewModelScope.launch {
      repository.deleteEmailTemplate(templateId)
      _toastEvents.tryEmit("Template email dihapus.")
    }
  }

  fun saveSmtpConfig(config: SmtpConfig) {
    viewModelScope.launch {
      val current = uiState.value.settings.smtpConfig
      val sameCredentials =
        current.host == config.host &&
          current.port == config.port &&
          current.useSsl == config.useSsl &&
          current.username == config.username &&
          current.password == config.password
      repository.updateSettings {
        it.copy(smtpConfig = config.copy(isVerified = if (sameCredentials) current.isVerified else false))
      }
      smtpTestStatusMessage.value = null
      _toastEvents.tryEmit("Pengaturan koneksi email SMTP disimpan.")
    }
  }

  fun testSmtpConnection(config: SmtpConfig) {
    if (isTestingSmtp.value) return
    isTestingSmtp.value = true
    smtpTestStatusMessage.value = null
    viewModelScope.launch {
      try {
        when (val result = SmtpEmailSender.testConnection(config)) {
          is EmailSendResult.Success -> {
            repository.updateSettings { it.copy(smtpConfig = config.copy(isVerified = true)) }
            smtpTestStatusMessage.value = result.message
            _toastEvents.tryEmit(result.message)
          }
          is EmailSendResult.Failure -> {
            repository.updateSettings { it.copy(smtpConfig = config.copy(isVerified = false)) }
            smtpTestStatusMessage.value = "Gagal: ${result.error}"
            _toastEvents.tryEmit("Koneksi SMTP gagal: ${result.error}")
          }
        }
      } finally {
        isTestingSmtp.value = false
      }
    }
  }

  fun sendEmailViaSmtp(
    contact: CallQueueItemEntity?,
    recipientEmail: String,
    subject: String,
    htmlBody: String,
    onSuccess: () -> Unit = {},
  ) {
    val cleanRecipient = recipientEmail.trim()
    if (!cleanRecipient.contains("@")) {
      _toastEvents.tryEmit("Masukkan alamat email tujuan yang valid.")
      return
    }
    if (isSendingEmail.value) return
    isSendingEmail.value = true
    viewModelScope.launch {
      try {
        if (contact != null && contact.id > 0L && cleanRecipient != contact.email.trim()) {
          repository.updateQueueItemEmail(contact, cleanRecipient)
        }
        val config = uiState.value.settings.smtpConfig
        when (val result = SmtpEmailSender.sendHtmlEmail(config, cleanRecipient, subject, htmlBody)) {
          is EmailSendResult.Success -> {
            _toastEvents.tryEmit(result.message)
            dismissSendEmailSheet()
            onSuccess()
          }
          is EmailSendResult.Failure -> {
            _toastEvents.tryEmit("Gagal kirim email: ${result.error}")
          }
        }
      } finally {
        isSendingEmail.value = false
      }
    }
  }

  fun sendEmailViaDeviceApp(
    contact: CallQueueItemEntity?,
    recipientEmail: String,
    subject: String,
    htmlBody: String,
    onSuccess: () -> Unit = {},
  ) {
    val cleanRecipient = recipientEmail.trim()
    if (!cleanRecipient.contains("@")) {
      _toastEvents.tryEmit("Masukkan alamat email tujuan yang valid.")
      return
    }
    viewModelScope.launch {
      if (contact != null && contact.id > 0L && cleanRecipient != contact.email.trim()) {
        repository.updateQueueItemEmail(contact, cleanRecipient)
      }
    }
    val launched = SmtpEmailSender.launchExternalEmailApp(appContext, cleanRecipient, subject, htmlBody)
    if (launched) {
      dismissSendEmailSheet()
      onSuccess()
    } else {
      _toastEvents.tryEmit("Tidak ditemukan aplikasi email di perangkat ini.")
    }
  }

  // --- Settings Updates ---
  fun updateSettings(transform: (HaloAntriSettings) -> HaloAntriSettings) {
    viewModelScope.launch { repository.updateSettings(transform) }
  }

  fun clearCallLogs() {
    viewModelScope.launch {
      repository.clearCallLogs()
      _toastEvents.tryEmit("Riwayat panggilan dihapus.")
    }
  }

  // --- Speaker Audio Player & Library ---
  fun openAudioSpeakerSheet() {
    showAudioSpeakerSheet.value = true
  }

  fun dismissAudioSpeakerSheet() {
    if (speakerAudioPlayer.recordingState.value.isRecording) {
      speakerAudioPlayer.cancelRecording()
    }
    showAudioSpeakerSheet.value = false
  }

  fun startAudioRecording() {
    speakerAudioPlayer.startRecording()
  }

  fun stopAudioRecording() {
    val draft = speakerAudioPlayer.stopRecordingAndGetDraft()
    if (draft != null) {
      pendingRecordedDraft.value = draft
    }
  }

  fun cancelAudioRecording() {
    speakerAudioPlayer.cancelRecording()
  }

  fun saveRecordedAudioDraft(customName: String) {
    val draft = pendingRecordedDraft.value ?: return
    val clip = SpeakerAudioPlayerController.finalizeRecordedDraft(draft, customName)
    pendingRecordedDraft.value = null
    viewModelScope.launch {
      repository.saveAudioClip(clip)
      _toastEvents.tryEmit("Rekaman \"${clip.name}\" disimpan.")
    }
  }

  fun discardRecordedAudioDraft() {
    pendingRecordedDraft.value?.let { SpeakerAudioPlayerController.discardRecordedDraft(it) }
    pendingRecordedDraft.value = null
  }

  fun importAndSaveAudioClip(uri: Uri, customName: String) {
    viewModelScope.launch {
      val imported = SpeakerAudioPlayerController.importAudioFromUri(appContext, uri, customName)
      if (imported != null) {
        repository.saveAudioClip(imported)
        _toastEvents.tryEmit("Audio \"${imported.name}\" disimpan.")
      } else {
        _toastEvents.tryEmit("Gagal mengimpor file audio. Pastikan format audio didukung.")
      }
    }
  }

  fun renameAudioClip(clip: AudioClipEntity, newName: String) {
    val clean = newName.trim()
    if (clean.isBlank()) {
      _toastEvents.tryEmit("Nama audio tidak boleh kosong.")
      return
    }
    viewModelScope.launch {
      repository.renameAudioClip(clip, clean)
      _toastEvents.tryEmit("Nama audio diubah menjadi \"$clean\".")
    }
  }

  fun deleteAudioClip(clip: AudioClipEntity) {
    if (uiState.value.audioPlaybackState.activeClipId == clip.id) {
      speakerAudioPlayer.stop()
    }
    viewModelScope.launch {
      repository.deleteAudioClip(clip)
      _toastEvents.tryEmit("Audio \"${clip.name}\" dihapus.")
    }
  }

  fun togglePlayPauseAudio(clip: AudioClipEntity) {
    speakerAudioPlayer.togglePlayPause(clip)
  }

  fun toggleQuickSpeakerPlayback() {
    val state = uiState.value
    val playback = state.audioPlaybackState
    val activeClip = state.audioClips.firstOrNull { it.id == playback.activeClipId }
    val targetClip = activeClip ?: state.audioClips.firstOrNull()
    if (targetClip == null) {
      openAudioSpeakerSheet()
      return
    }
    speakerAudioPlayer.togglePlayPause(targetClip)
  }

  fun stopAudioPlayback() {
    speakerAudioPlayer.stop()
  }

  fun seekAudioTo(positionMs: Long) {
    speakerAudioPlayer.seekTo(positionMs)
  }

  fun setAudioSpeakerphoneEnabled(enabled: Boolean) {
    speakerAudioPlayer.setSpeakerphoneEnabled(enabled)
  }

  fun setAudioLoopEnabled(enabled: Boolean) {
    speakerAudioPlayer.setLoopEnabled(enabled)
  }

  fun setAudioVoiceBoostEnabled(enabled: Boolean) {
    speakerAudioPlayer.setVoiceBoostEnabled(enabled)
  }

  fun setAudioAutoUnmuteMicEnabled(enabled: Boolean) {
    speakerAudioPlayer.setAutoUnmuteMicEnabled(enabled)
  }

  fun setAudioOutputRoute(route: SpeakerOutputRoute) {
    speakerAudioPlayer.setOutputRoute(route)
  }

  fun checkRootAudioAccess() {
    speakerAudioPlayer.checkRootAccess()
  }

  override fun onCleared() {
    super.onCleared()
    callTimerJob?.cancel()
    speakerAudioPlayer.release()
  }
}
