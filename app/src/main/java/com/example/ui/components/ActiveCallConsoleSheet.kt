package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.PhoneNumberExtractor
import com.example.service.AudioPlaybackState
import com.example.ui.theme.LocalHaloColors
import com.example.ui.viewmodel.ActiveCallSession

@Composable
fun ActiveCallConsoleOverlay(
  session: ActiveCallSession,
  isStealthMuted: Boolean,
  audioModeLabel: String,
  audioPlaybackState: AudioPlaybackState,
  savedAudioCount: Int,
  waitingQueueCount: Int,
  onToggleStealthMute: () -> Unit,
  onToggleQuickSpeaker: () -> Unit,
  onOpenAudioSpeaker: () -> Unit,
  onReopenWhatsApp: () -> Unit,
  onOpenWhatsAppWeb: () -> Unit,
  onCompleteAndNext: () -> Unit,
  onSkipCall: () -> Unit,
  onDismissBackToQueue: () -> Unit,
) {
  BackHandler(onBack = onDismissBackToQueue)

  val haloColors = LocalHaloColors.current
  val muteContainerColor by
    animateColorAsState(
      targetValue =
        if (isStealthMuted) haloColors.stealthContainer else MaterialTheme.colorScheme.surfaceVariant,
      label = "mute_container",
    )
  val muteContentColor by
    animateColorAsState(
      targetValue =
        if (isStealthMuted) haloColors.onStealthContainer else MaterialTheme.colorScheme.onSurface,
      label = "mute_content",
    )

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(haloColors.heroStart.copy(alpha = 0.22f), MaterialTheme.colorScheme.background)
            )
          )
          .statusBarsPadding()
          .navigationBarsPadding(),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 600.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onDismissBackToQueue,
            modifier = Modifier.testTag("call_console_back_button"),
          ) {
            Icon(
              Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = "Kembali ke antrian",
            )
          }
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text =
                "Jalur: ${session.usingAccount?.label ?: "WhatsApp Default"}",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }

        // Contact Avatar & Details
        Box(
          modifier =
            Modifier.size(84.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Rounded.PhoneInTalk,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = session.contactName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = PhoneNumberExtractor.formatDisplayPhone(session.phoneNumber),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
            color = MaterialTheme.colorScheme.primary,
          )
          if (session.idNumber.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Rounded.Badge,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "ID: ${session.idNumber}",
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = formatElapsedTime(session.elapsedSeconds),
            style = MaterialTheme.typography.displaySmall,
            fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
          )
        }

        if (session.launchStatusMessage.isNotBlank()) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = session.launchStatusMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        // Stealth Mute Big Card
        Card(
          onClick = onToggleStealthMute,
          colors = CardDefaults.cardColors(containerColor = muteContainerColor),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth().testTag("active_call_stealth_mute_card"),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(
              modifier =
                Modifier.size(64.dp)
                  .clip(CircleShape)
                  .background(
                    if (isStealthMuted) haloColors.stealthAccent.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surface
                  ),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = if (isStealthMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                contentDescription = if (isStealthMuted) "Bisu senyap aktif" else "Mikrofon aktif",
                modifier = Modifier.size(34.dp),
                tint = if (isStealthMuted) haloColors.stealthAccent else MaterialTheme.colorScheme.primary,
              )
            }
            Text(
              text = if (isStealthMuted) "BISU SENYAP AKTIF" else "AKTIFKAN BISU SENYAP",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = muteContentColor,
            )
            Text(
              text =
                if (isStealthMuted) {
                  "Mikrofon hardware Android dibisukan. Lawan bicara tidak melihat ikon/notifikasi bisu di WhatsApp."
                } else {
                  "Ketuk untuk membisukan mikrofon perangkat secara diam-diam tanpa menekan tombol mute WhatsApp."
                },
              style = MaterialTheme.typography.bodyMedium,
              color = muteContentColor.copy(alpha = 0.85f),
              textAlign = TextAlign.Center,
            )
            Text(
              text = "Status Audio Sistem: $audioModeLabel",
              style = MaterialTheme.typography.labelSmall,
              color = muteContentColor.copy(alpha = 0.75f),
            )
          }
        }

        // Speaker Audio Player Companion Card (near Stealth Mute)
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor =
                if (audioPlaybackState.isPlaying)
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surface
            ),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth().testTag("active_call_audio_speaker_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector =
                if (audioPlaybackState.isPlaying) Icons.Rounded.GraphicEq
                else Icons.Rounded.RecordVoiceOver,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(26.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text =
                  if (audioPlaybackState.isPlaying) "Pembicara Aktif"
                  else "Pemutar Audio Pembicara",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text =
                  when {
                    audioPlaybackState.activeClipName.isNotBlank() ->
                      audioPlaybackState.activeClipName
                    savedAudioCount > 0 -> "$savedAudioCount rekaman audio siap diputar"
                    else -> "Upload audio dengan nama pilihan untuk diputar"
                  },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (savedAudioCount > 0) {
              FilledTonalButton(
                onClick = onToggleQuickSpeaker,
                modifier = Modifier.heightIn(min = 48.dp).testTag("active_call_audio_quick_play"),
              ) {
                Icon(
                  imageVector =
                    if (audioPlaybackState.isPlaying) Icons.Rounded.Pause
                    else Icons.Rounded.PlayArrow,
                  contentDescription =
                    if (audioPlaybackState.isPlaying) "Jeda audio pembicara"
                    else "Putar audio pembicara",
                )
              }
            }
            OutlinedButton(
              onClick = onOpenAudioSpeaker,
              modifier = Modifier.heightIn(min = 48.dp).testTag("active_call_audio_open_library"),
            ) {
              Text(if (savedAudioCount > 0) "Pilih" else "Upload")
            }
          }
        }

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          FilledTonalButton(
            onClick = onReopenWhatsApp,
            modifier =
              Modifier.weight(1f).heightIn(min = 50.dp).testTag("reopen_whatsapp_button"),
          ) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text("Aplikasi WA")
          }
          OutlinedButton(
            onClick = onOpenWhatsAppWeb,
            modifier =
              Modifier.weight(1f).heightIn(min = 50.dp).testTag("open_active_wa_web_button"),
          ) {
            Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text("WA Web")
          }
        }

        Button(
          onClick = onCompleteAndNext,
          modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("complete_and_next_button"),
        ) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null)
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            if (waitingQueueCount > 0) "Selesai & Panggil Berikutnya ($waitingQueueCount)"
            else "Tandai Selesai"
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedButton(
            onClick = onSkipCall,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("skip_call_button"),
          ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = null)
            Spacer(modifier = Modifier.size(6.dp))
            Text("Lewati")
          }
          Button(
            onClick = onDismissBackToQueue,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
              ),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("end_session_button"),
          ) {
            Icon(Icons.Rounded.CallEnd, contentDescription = null)
            Spacer(modifier = Modifier.size(6.dp))
            Text("Tahan")
          }
        }
      }
    }
  }
}

private fun formatElapsedTime(totalSeconds: Int): String {
  val mins = totalSeconds / 60
  val secs = totalSeconds % 60
  return "%02d:%02d".format(mins, secs)
}
