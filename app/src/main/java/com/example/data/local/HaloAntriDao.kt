package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HaloAntriDao {

  // --- WhatsApp Accounts (unlimited) ---
  @Query("SELECT * FROM whatsapp_accounts ORDER BY isActive DESC, createdAt DESC")
  fun observeAccounts(): Flow<List<WhatsAppAccountEntity>>

  @Query("SELECT * FROM whatsapp_accounts WHERE isActive = 1 LIMIT 1")
  fun observeActiveAccount(): Flow<WhatsAppAccountEntity?>

  @Query("SELECT * FROM whatsapp_accounts WHERE isActive = 1 LIMIT 1")
  suspend fun getActiveAccount(): WhatsAppAccountEntity?

  @Query("SELECT COUNT(*) FROM whatsapp_accounts")
  suspend fun getAccountCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAccount(account: WhatsAppAccountEntity): Long

  @Update
  suspend fun updateAccount(account: WhatsAppAccountEntity)

  @Query("UPDATE whatsapp_accounts SET isActive = 0")
  suspend fun clearAllActiveAccounts()

  @Query("UPDATE whatsapp_accounts SET isActive = 1 WHERE id = :accountId")
  suspend fun markAccountActive(accountId: Long)

  @Transaction
  suspend fun setActiveAccount(accountId: Long) {
    clearAllActiveAccounts()
    markAccountActive(accountId)
  }

  @Query("UPDATE whatsapp_accounts SET callCount = callCount + 1, lastUsedAt = :now WHERE id = :accountId")
  suspend fun incrementCallCount(accountId: Long, now: Long = System.currentTimeMillis())

  @Query("DELETE FROM whatsapp_accounts WHERE id = :accountId")
  suspend fun deleteAccountById(accountId: Long)

  // --- Call Queue ---
  @Query("SELECT * FROM call_queue ORDER BY CASE WHEN status = 'CALLING' THEN 0 WHEN status = 'WAITING' THEN 1 ELSE 2 END, priority DESC, createdAt ASC")
  fun observeQueue(): Flow<List<CallQueueItemEntity>>

  @Query("SELECT * FROM call_queue WHERE phoneNumber = :normalizedPhone AND status IN ('WAITING', 'CALLING') LIMIT 1")
  suspend fun findActiveQueueItemByPhone(normalizedPhone: String): CallQueueItemEntity?

  @Query("SELECT * FROM call_queue WHERE status = 'WAITING' ORDER BY priority DESC, createdAt ASC LIMIT 1")
  suspend fun getNextWaitingItem(): CallQueueItemEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertQueueItem(item: CallQueueItemEntity): Long

  @Update
  suspend fun updateQueueItem(item: CallQueueItemEntity)

  @Query("UPDATE call_queue SET status = :status, updatedAt = :now WHERE id = :itemId")
  suspend fun updateQueueStatus(itemId: Long, status: QueueStatus, now: Long = System.currentTimeMillis())

  @Query("DELETE FROM call_queue WHERE id = :itemId")
  suspend fun deleteQueueItemById(itemId: Long)

  @Query("DELETE FROM call_queue WHERE status IN ('COMPLETED', 'SKIPPED')")
  suspend fun clearFinishedQueueItems()

  // --- Call Logs ---
  @Query("SELECT * FROM call_logs ORDER BY calledAt DESC LIMIT 100")
  fun observeCallLogs(): Flow<List<CallLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCallLog(log: CallLogEntity): Long

  @Query("DELETE FROM call_logs")
  suspend fun clearCallLogs()

  // --- Email Templates ---
  @Query("SELECT * FROM email_templates ORDER BY isDefault DESC, updatedAt DESC")
  fun observeEmailTemplates(): Flow<List<EmailTemplateEntity>>

  @Query("SELECT * FROM email_templates WHERE isDefault = 1 LIMIT 1")
  fun observeDefaultEmailTemplate(): Flow<EmailTemplateEntity?>

  @Query("SELECT COUNT(*) FROM email_templates")
  suspend fun getEmailTemplateCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEmailTemplate(template: EmailTemplateEntity): Long

  @Update
  suspend fun updateEmailTemplate(template: EmailTemplateEntity)

  @Query("UPDATE email_templates SET isDefault = 0")
  suspend fun clearDefaultEmailTemplates()

  @Query("UPDATE email_templates SET isDefault = 1 WHERE id = :templateId")
  suspend fun markEmailTemplateDefault(templateId: Long)

  @Transaction
  suspend fun setDefaultEmailTemplate(templateId: Long) {
    clearDefaultEmailTemplates()
    markEmailTemplateDefault(templateId)
  }

  @Query("DELETE FROM email_templates WHERE id = :templateId")
  suspend fun deleteEmailTemplateById(templateId: Long)

  // --- Audio Clips (Speaker Player) ---
  @Query("SELECT * FROM audio_clips ORDER BY createdAt DESC")
  fun observeAudioClips(): Flow<List<AudioClipEntity>>

  @Query("SELECT * FROM audio_clips WHERE id = :clipId LIMIT 1")
  suspend fun getAudioClipById(clipId: Long): AudioClipEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAudioClip(clip: AudioClipEntity): Long

  @Update
  suspend fun updateAudioClip(clip: AudioClipEntity)

  @Query("DELETE FROM audio_clips WHERE id = :clipId")
  suspend fun deleteAudioClipById(clipId: Long)
}
