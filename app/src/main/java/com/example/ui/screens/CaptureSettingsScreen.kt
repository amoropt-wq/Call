package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.EmailTemplateEntity
import com.example.data.prefs.HaloAntriSettings
import com.example.data.prefs.SmtpConfig
import com.example.domain.PhoneNumberExtractor
import com.example.service.WhatsAppNotificationListenerService
import com.example.ui.components.EmailTemplatesCard
import com.example.ui.components.SmtpConnectionCard
import com.example.ui.theme.LocalHaloColors
import com.example.ui.viewmodel.HaloAntriUiState

@Composable
fun CaptureSettingsScreen(
  uiState: HaloAntriUiState,
  onUpdateSettings: ((HaloAntriSettings) -> HaloAntriSettings) -> Unit,
  onToggleStealthMute: () -> Unit,
  onOpenAudioSpeaker: () -> Unit,
  onTestCaptureMessage: (senderName: String, messageBody: String) -> Unit,
  onSaveSmtpConfig: (SmtpConfig) -> Unit,
  onTestSmtpConnection: (SmtpConfig) -> Unit,
  onCreateEmailTemplate: () -> Unit,
  onEditEmailTemplate: (EmailTemplateEntity) -> Unit,
  onSetDefaultEmailTemplate: (Long) -> Unit,
  onDeleteEmailTemplate: (Long) -> Unit,
  onOpenManualEmail: () -> Unit = {},
) {
  val context = LocalContext.current
  val haloColors = LocalHaloColors.current

  var testSender by remember { mutableStateOf("") }
  var testMessage by remember { mutableStateOf("") }
  val previewParsed = remember(testSender, testMessage) {
    PhoneNumberExtractor.extractStructuredContact(testSender, testMessage)
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Notification Listener Permission Card
      item(key = "permission_card") {
        Card(
          shape = MaterialTheme.shapes.large,
          colors =
            CardDefaults.cardColors(
              containerColor =
                if (uiState.isNotificationAccessGranted)
                  MaterialTheme.colorScheme.primaryContainer
                else haloColors.stealthContainer
            ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector =
                  if (uiState.isNotificationAccessGranted) Icons.Rounded.NotificationsActive
                  else Icons.Rounded.NotificationsOff,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint =
                  if (uiState.isNotificationAccessGranted) MaterialTheme.colorScheme.primary
                  else haloColors.stealthAccent,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text =
                    if (uiState.isNotificationAccessGranted)
                      "Penangkap Notifikasi Aktif"
                    else "Izin Akses Notifikasi Diperlukan",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    if (uiState.isNotificationAccessGranted)
                      "Pesan masuk WhatsApp yang mengandung nomor HP otomatis dimasukkan ke antrian."
                    else
                      "Aktifkan \"Penangkap Pesan HaloAntri\" di Pengaturan Akses Notifikasi Android.",
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
            Button(
              onClick = {
                context.startActivity(WhatsAppNotificationListenerService.createSettingsIntent())
              },
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("open_notification_access_button"),
            ) {
              Icon(Icons.Rounded.OpenInNew, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                if (uiState.isNotificationAccessGranted) "Kelola Akses Notifikasi"
                else "Buka Pengaturan Akses Notifikasi"
              )
            }
          }
        }
      }

      // Email SMTP Connection Settings
      item(key = "smtp_connection_card") {
        SmtpConnectionCard(
          smtpConfig = uiState.settings.smtpConfig,
          isTesting = uiState.isTestingSmtp,
          statusMessage = uiState.smtpTestStatusMessage,
          onSaveConfig = onSaveSmtpConfig,
          onTestConnection = onTestSmtpConnection,
        )
      }

      // HTML & Visual Email Templates
      item(key = "email_templates_card") {
        EmailTemplatesCard(
          templates = uiState.emailTemplates,
          onCreateTemplate = onCreateEmailTemplate,
          onEditTemplate = onEditEmailTemplate,
          onSetDefaultTemplate = onSetDefaultEmailTemplate,
          onDeleteTemplate = onDeleteEmailTemplate,
          onOpenManualEmail = onOpenManualEmail,
        )
      }

      // Capture Source Toggles
      item(key = "capture_toggles") {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Sumber Pesan Masuk",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            SettingSwitchRow(
              title = "Tangkap Pesan Masuk Otomatis",
              subtitle = "Ekstrak nomor HP dari notifikasi yang masuk ke antrian",
              checked = uiState.settings.autoCaptureEnabled,
              testTag = "switch_auto_capture",
              onCheckedChange = { checked ->
                onUpdateSettings { it.copy(autoCaptureEnabled = checked) }
              },
            )
            HorizontalDivider()
            SettingSwitchRow(
              title = "WhatsApp (Personal)",
              subtitle = "Deteksi pesan masuk dari com.whatsapp",
              checked = uiState.settings.captureWhatsApp,
              testTag = "switch_capture_wa",
              onCheckedChange = { checked ->
                onUpdateSettings { it.copy(captureWhatsApp = checked) }
              },
            )
            SettingSwitchRow(
              title = "WhatsApp Business",
              subtitle = "Deteksi pesan masuk dari com.whatsapp.w4b",
              checked = uiState.settings.captureWhatsAppBusiness,
              testTag = "switch_capture_wab",
              onCheckedChange = { checked ->
                onUpdateSettings { it.copy(captureWhatsAppBusiness = checked) }
              },
            )
            SettingSwitchRow(
              title = "SMS & Aplikasi Pesan Lain",
              subtitle = "Juga tangkap nomor dari SMS / Telegram",
              checked = uiState.settings.captureOtherMessages,
              testTag = "switch_capture_other",
              onCheckedChange = { checked ->
                onUpdateSettings { it.copy(captureOtherMessages = checked) }
              },
            )
          }
        }
      }

      // Stealth Mute & Queue Settings
      item(key = "stealth_settings") {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Rounded.MicOff,
                contentDescription = null,
                tint = haloColors.stealthAccent,
              )
              Text(
                text = "Kontrol Bisu Senyap & Antrian",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
            SettingSwitchRow(
              title = "Bisu Senyap Otomatis Saat Menelpon",
              subtitle =
                "Langsung membisukan mikrofon sistem Android setiap panggilan dimulai tanpa menyentuh tombol mute WhatsApp",
              checked = uiState.settings.stealthMuteAutoLock,
              testTag = "switch_stealth_autolock",
              onCheckedChange = { checked ->
                onUpdateSettings { it.copy(stealthMuteAutoLock = checked) }
              },
            )
            HorizontalDivider()
            SettingSwitchRow(
              title = "Lanjut Antrian Otomatis",
              subtitle = "Otomatis memanggil nomor berikutnya setelah panggilan selesai",
              checked = uiState.settings.autoAdvanceQueue,
              testTag = "switch_auto_advance",
              onCheckedChange = { checked ->
                onUpdateSettings { it.copy(autoAdvanceQueue = checked) }
              },
            )
            HorizontalDivider()
            SettingSwitchRow(
              title = "Status Mikrofon Perangkat Saat Ini",
              subtitle =
                if (uiState.isStealthMuted) "Sedang DIBISUKAN (${uiState.audioModeLabel})"
                else "Mikrofon Aktif (${uiState.audioModeLabel})",
              checked = uiState.isStealthMuted,
              testTag = "switch_live_mic_mute",
              onCheckedChange = { onToggleStealthMute() },
            )
            HorizontalDivider()
            FilledTonalButton(
              onClick = onOpenAudioSpeaker,
              modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings_audio_speaker_button"),
            ) {
              Icon(
                imageVector =
                  if (uiState.audioPlaybackState.isPlaying) Icons.Rounded.GraphicEq
                  else Icons.Rounded.RecordVoiceOver,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text =
                  if (uiState.audioPlaybackState.isPlaying)
                    "Pembicara Aktif: ${uiState.audioPlaybackState.activeClipName}"
                  else
                    "Kelola & Putar Audio Pembicara (${uiState.audioClips.size})"
              )
            }
          }
        }
      }

      // Live Incoming Message Extractor Tester
      item(key = "message_parser_test") {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Rounded.Science,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Uji Ekstraktor Pesan Masuk",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
            Text(
              text =
                "Tempel teks pesan untuk menguji deteksi nomor HP & Nomor ID otomatis sebelum memasukkannya ke antrian.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
              value = testSender,
              onValueChange = { testSender = it },
              label = { Text("Nama Pengirim (Opsional)") },
              placeholder = { Text("Contoh: Andi Wijaya") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("test_capture_sender_input"),
            )

            OutlinedTextField(
              value = testMessage,
              onValueChange = { testMessage = it },
              label = { Text("Isi Pesan Masuk") },
              placeholder = {
                Text(
                  "Contoh:\nNama PURWANTO\nNomor hp 08995385444\nNomor kartu 4799 3111 0126 8348\nEmail/yaho mfajar444@gmail.com"
                )
              },
              minLines = 3,
              maxLines = 6,
              modifier = Modifier.fillMaxWidth().testTag("test_capture_message_input"),
            )

            if (
              previewParsed.phoneNumbers.isNotEmpty() ||
                previewParsed.idOrCardNumber.isNotBlank() ||
                previewParsed.email.isNotBlank()
            ) {
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  if (previewParsed.senderName.isNotBlank()) {
                    Text(
                      text = "Pengirim (Tetap Pengirim): ${previewParsed.senderName}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                  if (previewParsed.contactName.isNotBlank()) {
                    Text(
                      text = "Nama: ${previewParsed.contactName}",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                  if (previewParsed.phoneNumbers.isNotEmpty()) {
                    Text(
                      text =
                        "Nomor HP: ${previewParsed.phoneNumbers.joinToString { PhoneNumberExtractor.formatDisplayPhone(it) }}",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                  if (previewParsed.idOrCardNumber.isNotBlank()) {
                    Text(
                      text = "Nomor Kartu / ID: ${previewParsed.idOrCardNumber}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                  if (previewParsed.email.isNotBlank()) {
                    Text(
                      text = "Email: ${previewParsed.email}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                }
              }
            }

            Button(
              onClick = {
                onTestCaptureMessage(testSender, testMessage)
                testSender = ""
                testMessage = ""
              },
              modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("test_capture_submit_button"),
            ) {
              Icon(Icons.Rounded.AutoAwesomeMotion, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Tangkap & Masukkan Antrian")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  testTag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}
