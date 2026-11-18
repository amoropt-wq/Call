package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "haloantri_settings")

data class SmtpConfig(
  val host: String = "",
  val port: Int = 465,
  val useSsl: Boolean = true,
  val username: String = "",
  val password: String = "",
  val senderName: String = "HaloAntri",
  val isVerified: Boolean = false,
) {
  val isConfigured: Boolean
    get() = host.isNotBlank() && port > 0 && username.isNotBlank() && password.isNotBlank()
}

data class HaloAntriSettings(
  val autoCaptureEnabled: Boolean = true,
  val captureWhatsApp: Boolean = true,
  val captureWhatsAppBusiness: Boolean = true,
  val captureOtherMessages: Boolean = false,
  val stealthMuteAutoLock: Boolean = true,
  val autoAdvanceQueue: Boolean = true,
  val smtpConfig: SmtpConfig = SmtpConfig(),
)

class SettingsPreferences(private val context: Context) {

  private object Keys {
    val AUTO_CAPTURE_ENABLED = booleanPreferencesKey("auto_capture_enabled")
    val CAPTURE_WHATSAPP = booleanPreferencesKey("capture_whatsapp")
    val CAPTURE_WHATSAPP_BUSINESS = booleanPreferencesKey("capture_whatsapp_business")
    val CAPTURE_OTHER_MESSAGES = booleanPreferencesKey("capture_other_messages")
    val STEALTH_MUTE_AUTO_LOCK = booleanPreferencesKey("stealth_mute_auto_lock")
    val AUTO_ADVANCE_QUEUE = booleanPreferencesKey("auto_advance_queue")

    val SMTP_HOST = stringPreferencesKey("smtp_host")
    val SMTP_PORT = intPreferencesKey("smtp_port")
    val SMTP_USE_SSL = booleanPreferencesKey("smtp_use_ssl")
    val SMTP_USERNAME = stringPreferencesKey("smtp_username")
    val SMTP_PASSWORD = stringPreferencesKey("smtp_password")
    val SMTP_SENDER_NAME = stringPreferencesKey("smtp_sender_name")
    val SMTP_VERIFIED = booleanPreferencesKey("smtp_verified")
  }

  val settingsFlow: Flow<HaloAntriSettings> =
    context.dataStore.data.map { prefs ->
      HaloAntriSettings(
        autoCaptureEnabled = prefs[Keys.AUTO_CAPTURE_ENABLED] ?: true,
        captureWhatsApp = prefs[Keys.CAPTURE_WHATSAPP] ?: true,
        captureWhatsAppBusiness = prefs[Keys.CAPTURE_WHATSAPP_BUSINESS] ?: true,
        captureOtherMessages = prefs[Keys.CAPTURE_OTHER_MESSAGES] ?: false,
        stealthMuteAutoLock = prefs[Keys.STEALTH_MUTE_AUTO_LOCK] ?: true,
        autoAdvanceQueue = prefs[Keys.AUTO_ADVANCE_QUEUE] ?: true,
        smtpConfig =
          SmtpConfig(
            host = prefs[Keys.SMTP_HOST] ?: "",
            port = prefs[Keys.SMTP_PORT] ?: 465,
            useSsl = prefs[Keys.SMTP_USE_SSL] ?: true,
            username = prefs[Keys.SMTP_USERNAME] ?: "",
            password = prefs[Keys.SMTP_PASSWORD] ?: "",
            senderName = prefs[Keys.SMTP_SENDER_NAME] ?: "HaloAntri",
            isVerified = prefs[Keys.SMTP_VERIFIED] ?: false,
          ),
      )
    }

  suspend fun updateSettings(transform: (HaloAntriSettings) -> HaloAntriSettings) {
    context.dataStore.edit { prefs ->
      val current =
        HaloAntriSettings(
          autoCaptureEnabled = prefs[Keys.AUTO_CAPTURE_ENABLED] ?: true,
          captureWhatsApp = prefs[Keys.CAPTURE_WHATSAPP] ?: true,
          captureWhatsAppBusiness = prefs[Keys.CAPTURE_WHATSAPP_BUSINESS] ?: true,
          captureOtherMessages = prefs[Keys.CAPTURE_OTHER_MESSAGES] ?: false,
          stealthMuteAutoLock = prefs[Keys.STEALTH_MUTE_AUTO_LOCK] ?: true,
          autoAdvanceQueue = prefs[Keys.AUTO_ADVANCE_QUEUE] ?: true,
          smtpConfig =
            SmtpConfig(
              host = prefs[Keys.SMTP_HOST] ?: "",
              port = prefs[Keys.SMTP_PORT] ?: 465,
              useSsl = prefs[Keys.SMTP_USE_SSL] ?: true,
              username = prefs[Keys.SMTP_USERNAME] ?: "",
              password = prefs[Keys.SMTP_PASSWORD] ?: "",
              senderName = prefs[Keys.SMTP_SENDER_NAME] ?: "HaloAntri",
              isVerified = prefs[Keys.SMTP_VERIFIED] ?: false,
            ),
        )
      val updated = transform(current)
      prefs[Keys.AUTO_CAPTURE_ENABLED] = updated.autoCaptureEnabled
      prefs[Keys.CAPTURE_WHATSAPP] = updated.captureWhatsApp
      prefs[Keys.CAPTURE_WHATSAPP_BUSINESS] = updated.captureWhatsAppBusiness
      prefs[Keys.CAPTURE_OTHER_MESSAGES] = updated.captureOtherMessages
      prefs[Keys.STEALTH_MUTE_AUTO_LOCK] = updated.stealthMuteAutoLock
      prefs[Keys.AUTO_ADVANCE_QUEUE] = updated.autoAdvanceQueue

      prefs[Keys.SMTP_HOST] = updated.smtpConfig.host
      prefs[Keys.SMTP_PORT] = updated.smtpConfig.port
      prefs[Keys.SMTP_USE_SSL] = updated.smtpConfig.useSsl
      prefs[Keys.SMTP_USERNAME] = updated.smtpConfig.username
      prefs[Keys.SMTP_PASSWORD] = updated.smtpConfig.password
      prefs[Keys.SMTP_SENDER_NAME] = updated.smtpConfig.senderName
      prefs[Keys.SMTP_VERIFIED] = updated.smtpConfig.isVerified
    }
  }
}
