package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WhatsAppFlavor(val packageName: String, val displayName: String) {
  PERSONAL("com.whatsapp", "WhatsApp"),
  BUSINESS("com.whatsapp.w4b", "WhatsApp Business"),
}

enum class QueueSource(val label: String) {
  MANUAL("Input Manual"),
  NOTIFICATION_WA("Pesan Masuk WA"),
  NOTIFICATION_WA_BUSINESS("Pesan Masuk WA Bisnis"),
  NOTIFICATION_OTHER("Pesan Masuk Lainnya"),
  WHATSAPP_WEB("WhatsApp Web"),
}

enum class QueueStatus(val label: String) {
  WAITING("Menunggu"),
  CALLING("Sedang Ditelpon"),
  COMPLETED("Selesai"),
  SKIPPED("Dilewati"),
}

@Entity(tableName = "whatsapp_accounts")
data class WhatsAppAccountEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val label: String,
  val phoneNumber: String,
  val appFlavor: WhatsAppFlavor = WhatsAppFlavor.PERSONAL,
  val isActive: Boolean = false,
  val callCount: Int = 0,
  val lastUsedAt: Long? = null,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "call_queue")
data class CallQueueItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val phoneNumber: String,
  val idNumber: String = "",
  val email: String = "",
  val notes: String = "",
  val source: QueueSource = QueueSource.MANUAL,
  val senderName: String = "",
  val capturedMessage: String = "",
  val status: QueueStatus = QueueStatus.WAITING,
  val priority: Int = 0, // 0 = Normal, 1 = Prioritas
  val assignedAccountId: Long? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "email_templates")
data class EmailTemplateEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val subject: String,
  val headerTitle: String,
  val headerStyle: String = "SOLID_LEFT",
  val headerLogoUrl: String = "",
  val accentColorHex: String = "#007A5E",
  val backgroundStyle: String = "CLEAN_LIGHT",
  val bodyHtml: String,
  val ctaLabel: String = "",
  val ctaUrl: String = "",
  val footerText: String = "",
  val footerLogoUrl: String = "",
  val useRawHtmlOnly: Boolean = false,
  val isDefault: Boolean = false,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val contactName: String,
  val phoneNumber: String,
  val idNumber: String = "",
  val waAccountLabel: String,
  val waAccountNumber: String,
  val stealthMuted: Boolean,
  val durationSeconds: Int,
  val outcome: String,
  val calledAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "audio_clips")
data class AudioClipEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val originalFileName: String = "",
  val filePath: String,
  val mimeType: String = "audio/*",
  val durationMs: Long = 0L,
  val fileSizeBytes: Long = 0L,
  val createdAt: Long = System.currentTimeMillis(),
)
