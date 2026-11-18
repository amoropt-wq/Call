package com.example.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.example.data.local.QueueSource
import com.example.data.repository.HaloAntriRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WhatsAppNotificationListenerService : NotificationListenerService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null) return
    val notification = sbn.notification ?: return

    // Skip ongoing call notifications or group summary duplicates
    if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
    if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

    val pkg = sbn.packageName ?: return
    if (pkg == applicationContext.packageName) return

    val extras = notification.extras ?: Bundle.EMPTY
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val body = extractNotificationBody(extras)
    if (title.isBlank() && body.isBlank()) return

    serviceScope.launch {
      val repo = HaloAntriRepository.getInstance(applicationContext)
      val settings = repo.settings.first()
      if (!settings.autoCaptureEnabled) return@launch

      val source =
        when (pkg) {
          "com.whatsapp" -> if (settings.captureWhatsApp) QueueSource.NOTIFICATION_WA else null
          "com.whatsapp.w4b" ->
            if (settings.captureWhatsAppBusiness) QueueSource.NOTIFICATION_WA_BUSINESS else null
          in OTHER_MESSAGING_PACKAGES ->
            if (settings.captureOtherMessages) QueueSource.NOTIFICATION_OTHER else null
          else -> null
        } ?: return@launch

      repo.enqueueFromCapturedMessage(
        senderTitle = title,
        messageText = body,
        source = source,
      )
    }
  }

  private fun extractNotificationBody(extras: Bundle): String {
    val parts = mutableListOf<String>()
    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let {
      if (it.isNotBlank()) parts.add(it)
    }
    extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let {
      if (it.isNotBlank() && !parts.contains(it)) parts.add(it)
    }
    extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
      val str = line?.toString().orEmpty()
      if (str.isNotBlank() && !parts.contains(str)) parts.add(str)
    }
    return parts.joinToString("\n")
  }

  override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()
  }

  companion object {
    private val OTHER_MESSAGING_PACKAGES =
      setOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "org.telegram.messenger",
        "com.samsung.android.messaging",
      )

    fun isNotificationAccessEnabled(context: Context): Boolean {
      return NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
    }

    fun createSettingsIntent(): Intent {
      return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  }
}
