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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CallLogEntity
import com.example.domain.PhoneNumberExtractor
import com.example.ui.theme.LocalHaloColors
import com.example.ui.viewmodel.HaloAntriUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DialerScreen(
  uiState: HaloAntriUiState,
  onDirectCall: (name: String, phone: String, idNumber: String) -> Unit,
  onAddToQueue: (name: String, phone: String, idNumber: String) -> Unit,
  onToggleStealthMute: () -> Unit,
  onOpenAudioSpeaker: () -> Unit,
  onRedial: (CallLogEntity) -> Unit,
  onClearLogs: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var idNumber by remember { mutableStateOf("") }
  val haloColors = LocalHaloColors.current

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Stealth Mute Pre-Arm Card
      item(key = "stealth_card") {
        Card(
          shape = MaterialTheme.shapes.large,
          colors =
            CardDefaults.cardColors(
              containerColor =
                if (uiState.isStealthMuted) haloColors.stealthContainer
                else MaterialTheme.colorScheme.surface
            ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector =
                    if (uiState.isStealthMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                  contentDescription = null,
                  tint =
                    if (uiState.isStealthMuted) haloColors.stealthAccent
                    else MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp),
                )
                Column {
                  Text(
                    text =
                      if (uiState.isStealthMuted) "Mode Bisu Senyap Aktif"
                      else "Mode Bisu Senyap Nonaktif",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                      if (uiState.isStealthMuted) haloColors.onStealthContainer
                      else MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = "Bisukan mikrofon OS sebelum & selama telpon tanpa diketahui lawan bicara.",
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                      if (uiState.isStealthMuted) haloColors.onStealthContainer.copy(alpha = 0.85f)
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Switch(
                checked = uiState.isStealthMuted,
                onCheckedChange = { onToggleStealthMute() },
                modifier = Modifier.testTag("dialer_stealth_switch"),
              )
            }

            FilledTonalButton(
              onClick = onOpenAudioSpeaker,
              modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dialer_audio_speaker_button"),
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
                    "Pembicara: Memutar ${uiState.audioPlaybackState.activeClipName}"
                  else
                    "Pemutar Audio Pembicara (${uiState.audioClips.size} tersimpan)"
              )
            }
          }
        }
      }

      // Direct WhatsApp Dialer Card
      item(key = "dialer_card") {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Panggil Langsung WhatsApp",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Text(
                  text = uiState.activeAccount?.label ?: "WA Default",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }

            OutlinedTextField(
              value = phone,
              onValueChange = { phone = it },
              label = { Text("Nomor HP Tujuan *") },
              placeholder = { Text("081234567890 / +62812...") },
              leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              modifier = Modifier.fillMaxWidth().testTag("dialer_phone_input"),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama") },
                placeholder = { Text("Opsional") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.weight(1f).testTag("dialer_name_input"),
              )
              OutlinedTextField(
                value = idNumber,
                onValueChange = { idNumber = it },
                label = { Text("Nomor ID") },
                placeholder = { Text("Opsional") },
                leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f).testTag("dialer_id_input"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = {
                  onAddToQueue(name, phone, idNumber)
                  name = ""
                  phone = ""
                  idNumber = ""
                },
                modifier =
                  Modifier.weight(1f).heightIn(min = 50.dp).testTag("dialer_enqueue_button"),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Antrikan")
              }
              Button(
                onClick = { onDirectCall(name, phone, idNumber) },
                modifier = Modifier.weight(1f).heightIn(min = 50.dp).testTag("dialer_call_button"),
              ) {
                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Telpon WA")
              }
            }
          }
        }
      }

      // Call History Header
      item(key = "history_header") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.History,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Riwayat Panggilan (${uiState.callLogs.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          if (uiState.callLogs.isNotEmpty()) {
            TextButton(
              onClick = onClearLogs,
              modifier = Modifier.testTag("clear_logs_button"),
            ) {
              Icon(
                Icons.Rounded.DeleteSweep,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Hapus")
            }
          }
        }
      }

      if (uiState.callLogs.isEmpty()) {
        item(key = "empty_logs") {
          Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Belum ada riwayat panggilan. Panggilan dari antrian atau dialer akan tercatat di sini.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      } else {
        items(uiState.callLogs, key = { it.id }) { log ->
          CallLogRowCard(log = log, onRedial = { onRedial(log) })
        }
      }
    }
  }
}

@Composable
private fun CallLogRowCard(log: CallLogEntity, onRedial: () -> Unit) {
  val haloColors = LocalHaloColors.current
  val formatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = log.contactName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text =
            "${PhoneNumberExtractor.formatDisplayPhone(log.phoneNumber)}" +
              if (log.idNumber.isNotBlank()) " • ID: ${log.idNumber}" else "",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "${log.waAccountLabel} • ${log.durationSeconds}d • ${formatter.format(Date(log.calledAt))}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (log.stealthMuted) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = haloColors.stealthContainer,
          ) {
            Text(
              text = "Bisu Senyap Aktif",
              style = MaterialTheme.typography.labelSmall,
              color = haloColors.onStealthContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
        }
      }
      FilledTonalButton(
        onClick = onRedial,
        modifier = Modifier.heightIn(min = 48.dp).testTag("redial_log_${log.id}"),
      ) {
        Icon(Icons.Rounded.Call, contentDescription = "Panggil ulang")
      }
    }
  }
}
