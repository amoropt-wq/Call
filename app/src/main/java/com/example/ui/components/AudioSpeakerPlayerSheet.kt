package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SpeakerPhone
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.AudioClipEntity
import com.example.service.AudioPlaybackState
import com.example.service.AudioRecordingState
import com.example.service.PickedAudioMeta
import com.example.service.RecordedAudioDraft
import com.example.service.SpeakerAudioPlayerController
import com.example.service.SpeakerOutputRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSpeakerPlayerSheet(
  audioClips: List<AudioClipEntity>,
  playbackState: AudioPlaybackState,
  recordingState: AudioRecordingState,
  pendingRecordedDraft: RecordedAudioDraft?,
  isStealthMuted: Boolean,
  audioModeLabel: String,
  onDismiss: () -> Unit,
  onImportAndSaveAudio: (uri: Uri, customName: String) -> Unit,
  onStartRecording: () -> Unit,
  onStopRecording: () -> Unit,
  onCancelRecording: () -> Unit,
  onSaveRecordedDraft: (customName: String) -> Unit,
  onDiscardRecordedDraft: () -> Unit,
  onRenameClip: (AudioClipEntity, String) -> Unit,
  onDeleteClip: (AudioClipEntity) -> Unit,
  onTogglePlayPause: (AudioClipEntity) -> Unit,
  onStopPlayback: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onToggleSpeakerphone: (Boolean) -> Unit,
  onToggleLoop: (Boolean) -> Unit,
  onToggleVoiceBoost: (Boolean) -> Unit,
  onToggleAutoUnmuteMic: (Boolean) -> Unit,
  onSelectOutputRoute: (SpeakerOutputRoute) -> Unit,
  onCheckRootAccess: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var pendingImport by remember { mutableStateOf<Pair<Uri, PickedAudioMeta>?>(null) }
  var pendingNameInput by remember { mutableStateOf("") }
  var recordedNameInput by remember { mutableStateOf("") }
  var renamingClip by remember { mutableStateOf<AudioClipEntity?>(null) }
  var renameInput by remember { mutableStateOf("") }
  var recordPermissionDenied by remember { mutableStateOf(false) }

  LaunchedEffect(pendingRecordedDraft) {
    if (pendingRecordedDraft != null) {
      val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
      recordedNameInput = "Rekaman Pembicara $stamp"
    }
  }

  val audioPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch {
          val meta = SpeakerAudioPlayerController.readPickedAudioMeta(context, uri)
          pendingNameInput = meta.suggestedName
          pendingImport = uri to meta
        }
      }
    }

  val recordPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        recordPermissionDenied = false
        onStartRecording()
      } else {
        recordPermissionDenied = true
      }
    }

  fun requestOrStartRecording() {
    val hasMicPermission =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (hasMicPermission) {
      recordPermissionDenied = false
      onStartRecording()
    } else {
      recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp).navigationBarsPadding(),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "header") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector =
                  if (playbackState.isPlaying) Icons.Rounded.GraphicEq
                  else Icons.Rounded.RecordVoiceOver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Pemutar Audio Pembicara",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Upload atau rekam suara, beri nama pilihan, & putar lewat speaker",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item(key = "upload_record_and_modes") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (recordingState.isRecording) {
            Card(
              shape = MaterialTheme.shapes.medium,
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.errorContainer
                ),
              modifier = Modifier.fillMaxWidth().testTag("active_recording_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
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
                      Icons.Rounded.FiberManualRecord,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = "Merekam Audio Pembicara...",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                  }
                  Text(
                    text = SpeakerAudioPlayerController.formatDuration(recordingState.elapsedMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedButton(
                    onClick = onCancelRecording,
                    modifier =
                      Modifier.weight(1f).heightIn(min = 48.dp).testTag("cancel_recording_button"),
                  ) {
                    Text("Batal")
                  }
                  Button(
                    onClick = onStopRecording,
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                      ),
                    modifier =
                      Modifier.weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("stop_and_save_recording_button"),
                  ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Selesai & Simpan")
                  }
                }
              }
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilledTonalButton(
                onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                modifier =
                  Modifier.weight(1f).heightIn(min = 50.dp).testTag("upload_audio_button"),
              ) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload Audio")
              }
              Button(
                onClick = { requestOrStartRecording() },
                modifier =
                  Modifier.weight(1f).heightIn(min = 50.dp).testTag("record_audio_button"),
              ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Rekam Audio")
              }
            }
          }

          if (recordPermissionDenied) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text =
                  "Izin mikrofon (RECORD_AUDIO) diperlukan untuk merekam suara. Anda tetap bisa memakai Upload Audio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(10.dp),
              )
            }
          }

          // WhatsApp Output Readiness Banner
          val micBlockedWarning = isStealthMuted && !playbackState.autoUnmuteMicEnabled
          Surface(
            shape = MaterialTheme.shapes.medium,
            color =
              when {
                micBlockedWarning -> MaterialTheme.colorScheme.errorContainer
                playbackState.isPlaying && playbackState.micTemporarilyUnmuted ->
                  MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
              },
            modifier = Modifier.fillMaxWidth().testTag("audio_wa_status_banner"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector =
                  when {
                    micBlockedWarning -> Icons.Rounded.WarningAmber
                    isStealthMuted && !playbackState.micTemporarilyUnmuted -> Icons.Rounded.MicOff
                    else -> Icons.Rounded.Mic
                  },
                contentDescription = null,
                tint =
                  when {
                    micBlockedWarning -> MaterialTheme.colorScheme.error
                    playbackState.isPlaying && playbackState.micTemporarilyUnmuted ->
                      MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                  },
                modifier = Modifier.size(18.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Status Panggilan: $audioModeLabel",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color =
                    if (micBlockedWarning) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    when {
                      micBlockedWarning ->
                        "Mic sedang dibisukan! Aktifkan \"Buka Mic saat Putar\" agar audio speaker masuk ke WA."
                      playbackState.isPlaying && playbackState.micTemporarilyUnmuted ->
                        "Mic dibuka sementara agar suara speaker masuk jelas ke WhatsApp (kembali bisu saat selesai)."
                      isStealthMuted ->
                        "Bisu Senyap aktif — mic otomatis dibuka saat diputar agar lawan bicara WA mendengar audio."
                      else ->
                        "Mic aktif & speaker diperkuat — arahkan panggilan WA ke mode speaker ponsel agar suara masuk jelas."
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color =
                    if (micBlockedWarning) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = playbackState.speakerphoneEnabled,
              onClick = { onToggleSpeakerphone(!playbackState.speakerphoneEnabled) },
              label = { Text("Output Speaker") },
              leadingIcon = {
                Icon(
                  Icons.AutoMirrored.Rounded.VolumeUp,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier =
                Modifier.weight(1f).heightIn(min = 48.dp).testTag("audio_speakerphone_chip"),
            )
            FilterChip(
              selected = playbackState.voiceBoostEnabled,
              onClick = { onToggleVoiceBoost(!playbackState.voiceBoostEnabled) },
              label = { Text("Penguat Suara") },
              leadingIcon = {
                Icon(
                  Icons.Rounded.HighQuality,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier =
                Modifier.weight(1f).heightIn(min = 48.dp).testTag("audio_voice_boost_chip"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = playbackState.autoUnmuteMicEnabled,
              onClick = { onToggleAutoUnmuteMic(!playbackState.autoUnmuteMicEnabled) },
              label = { Text("Buka Mic saat Putar") },
              leadingIcon = {
                Icon(
                  Icons.Rounded.Mic,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier =
                Modifier.weight(1f).heightIn(min = 48.dp).testTag("audio_auto_unmute_chip"),
            )
            FilterChip(
              selected = playbackState.loopEnabled,
              onClick = { onToggleLoop(!playbackState.loopEnabled) },
              label = { Text("Putar Ulang") },
              leadingIcon = {
                Icon(
                  Icons.Rounded.Repeat,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("audio_loop_chip"),
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Jalur Output Audio ke WhatsApp",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
            )

            // Root Access Status & Check Button
            val rootDiag = playbackState.rootDiagnostics
            Surface(
              shape = MaterialTheme.shapes.small,
              color =
                if (rootDiag.isRootGranted)
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
              modifier = Modifier.fillMaxWidth().testTag("root_audio_status_card"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  modifier = Modifier.weight(1f),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint =
                      if (rootDiag.isRootGranted) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = rootDiag.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                TextButton(
                  onClick = onCheckRootAccess,
                  modifier = Modifier.testTag("check_root_audio_button"),
                ) {
                  Text(if (rootDiag.isChecked) "Cek Ulang" else "Cek Root (su)")
                }
              }
            }

            // Root inject chip + standard speaker/call chips
            FilterChip(
              selected = playbackState.outputRoute == SpeakerOutputRoute.ROOT_CALL_INJECT,
              onClick = { onSelectOutputRoute(SpeakerOutputRoute.ROOT_CALL_INJECT) },
              label = { Text(SpeakerOutputRoute.ROOT_CALL_INJECT.label) },
              leadingIcon = {
                Icon(
                  Icons.Rounded.Security,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("audio_route_${SpeakerOutputRoute.ROOT_CALL_INJECT.name}"),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              listOf(SpeakerOutputRoute.MEDIA_SPEAKER, SpeakerOutputRoute.VOICE_CALL_SPEAKER)
                .forEach { route ->
                  FilterChip(
                    selected = playbackState.outputRoute == route,
                    onClick = { onSelectOutputRoute(route) },
                    label = { Text(route.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                      Icon(
                        imageVector =
                          if (route == SpeakerOutputRoute.MEDIA_SPEAKER) Icons.Rounded.SpeakerPhone
                          else Icons.Rounded.PhoneInTalk,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                    },
                    modifier =
                      Modifier.weight(1f).heightIn(min = 48.dp).testTag("audio_route_${route.name}"),
                  )
                }
            }
            Text(
              text = playbackState.outputRoute.hint,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      val activeError = recordingState.errorMessage ?: playbackState.errorMessage
      if (activeError != null) {
        item(key = "error_banner") {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = activeError,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
      }

      val activeClip = audioClips.firstOrNull { it.id == playbackState.activeClipId }
      if (activeClip != null && (playbackState.isPlaying || playbackState.isPaused)) {
        item(key = "now_playing") {
          Card(
            shape = MaterialTheme.shapes.medium,
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
              ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text =
                      when {
                        playbackState.isPlaying && playbackState.activeRouteDescription.isNotBlank() ->
                          "DIPUTAR (${playbackState.activeRouteDescription.uppercase()})"
                        playbackState.isPlaying -> "SEDANG DIPUTAR (PEMBICARA)"
                        else -> "DIJEDA"
                      },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = activeClip.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  FilledIconButton(
                    onClick = { onTogglePlayPause(activeClip) },
                    modifier = Modifier.size(48.dp).testTag("audio_now_playing_toggle"),
                  ) {
                    Icon(
                      imageVector =
                        if (playbackState.isPlaying) Icons.Rounded.Pause
                        else Icons.Rounded.PlayArrow,
                      contentDescription = if (playbackState.isPlaying) "Jeda" else "Putar",
                    )
                  }
                  FilledTonalIconButton(
                    onClick = onStopPlayback,
                    modifier = Modifier.size(48.dp).testTag("audio_now_playing_stop"),
                  ) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Hentikan audio")
                  }
                }
              }

              val totalMs = playbackState.durationMs.coerceAtLeast(1L)
              Slider(
                value = playbackState.positionMs.toFloat().coerceIn(0f, totalMs.toFloat()),
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..totalMs.toFloat(),
                modifier = Modifier.fillMaxWidth(),
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = SpeakerAudioPlayerController.formatDuration(playbackState.positionMs),
                  style = MaterialTheme.typography.labelSmall,
                )
                Text(
                  text = SpeakerAudioPlayerController.formatDuration(playbackState.durationMs),
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }
          }
        }
      }

      item(key = "library_header") {
        Text(
          text = "Daftar Audio Tersimpan (${audioClips.size})",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
      }

      if (audioClips.isEmpty()) {
        item(key = "empty_audio") {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                Icons.Rounded.AudioFile,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Belum Ada Audio Tersimpan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text =
                  "Gunakan Upload Audio atau Rekam Audio di atas untuk menyimpan suara dengan nama sesuai keinginan Anda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(audioClips, key = { it.id }) { clip ->
          val isActive = playbackState.activeClipId == clip.id
          val isThisPlaying = isActive && playbackState.isPlaying
          AudioClipRowCard(
            clip = clip,
            isActive = isActive,
            isPlaying = isThisPlaying,
            onPlayPause = { onTogglePlayPause(clip) },
            onRename = {
              renamingClip = clip
              renameInput = clip.name
            },
            onDelete = { onDeleteClip(clip) },
          )
        }
      }
    }
  }

  // Dialog to enter custom name right after picking an uploaded audio file
  val importPair = pendingImport
  if (importPair != null) {
    val (uri, meta) = importPair
    AlertDialog(
      onDismissRequest = { pendingImport = null },
      title = { Text("Simpan Audio dengan Nama") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text =
              "File: ${meta.originalFileName}" +
                if (meta.sizeBytes > 0L)
                  " (${SpeakerAudioPlayerController.formatFileSize(meta.sizeBytes)})"
                else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = pendingNameInput,
            onValueChange = { pendingNameInput = it },
            label = { Text("Nama Audio *") },
            placeholder = { Text("Contoh: Salam Pembuka Pelanggan") },
            trailingIcon = {
              if (pendingNameInput.isNotEmpty()) {
                IconButton(onClick = { pendingNameInput = "" }) {
                  Icon(Icons.Rounded.Clear, contentDescription = "Hapus teks")
                }
              }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("audio_custom_name_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onImportAndSaveAudio(uri, pendingNameInput)
            pendingImport = null
          },
          enabled = pendingNameInput.isNotBlank(),
          modifier = Modifier.testTag("audio_save_name_confirm"),
        ) {
          Text("Simpan Audio")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingImport = null }) {
          Text("Batal")
        }
      },
    )
  }

  // Dialog to enter custom name right after recording audio
  if (pendingRecordedDraft != null) {
    AlertDialog(
      onDismissRequest = onDiscardRecordedDraft,
      title = { Text("Simpan Rekaman dengan Nama") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text =
              "Durasi: ${SpeakerAudioPlayerController.formatDuration(pendingRecordedDraft.durationMs)} • ${SpeakerAudioPlayerController.formatFileSize(pendingRecordedDraft.fileSizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = recordedNameInput,
            onValueChange = { recordedNameInput = it },
            label = { Text("Nama Rekaman *") },
            placeholder = { Text("Contoh: Suara Pembuka Layanan") },
            trailingIcon = {
              if (recordedNameInput.isNotEmpty()) {
                IconButton(onClick = { recordedNameInput = "" }) {
                  Icon(Icons.Rounded.Clear, contentDescription = "Hapus teks")
                }
              }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("recorded_audio_name_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { onSaveRecordedDraft(recordedNameInput) },
          enabled = recordedNameInput.isNotBlank(),
          modifier = Modifier.testTag("recorded_audio_save_confirm"),
        ) {
          Text("Simpan Rekaman")
        }
      },
      dismissButton = {
        TextButton(onClick = onDiscardRecordedDraft) {
          Text("Buang")
        }
      },
    )
  }

  // Dialog to rename an existing saved audio clip
  val clipToRename = renamingClip
  if (clipToRename != null) {
    AlertDialog(
      onDismissRequest = { renamingClip = null },
      title = { Text("Ubah Nama Audio") },
      text = {
        OutlinedTextField(
          value = renameInput,
          onValueChange = { renameInput = it },
          label = { Text("Nama Audio Baru *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("audio_rename_input"),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onRenameClip(clipToRename, renameInput)
            renamingClip = null
          },
          enabled = renameInput.isNotBlank(),
          modifier = Modifier.testTag("audio_rename_confirm"),
        ) {
          Text("Simpan")
        }
      },
      dismissButton = {
        TextButton(onClick = { renamingClip = null }) {
          Text("Batal")
        }
      },
    )
  }
}

@Composable
private fun AudioClipRowCard(
  clip: AudioClipEntity,
  isActive: Boolean,
  isPlaying: Boolean,
  onPlayPause: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
          else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
      ),
    border =
      if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
      else null,
    modifier = Modifier.fillMaxWidth().testTag("audio_clip_card_${clip.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      FilledIconButton(
        onClick = onPlayPause,
        colors =
          IconButtonDefaults.filledIconButtonColors(
            containerColor =
              if (isPlaying) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.secondaryContainer,
            contentColor =
              if (isPlaying) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSecondaryContainer,
          ),
        modifier = Modifier.size(48.dp).testTag("audio_play_pause_${clip.id}"),
      ) {
        Icon(
          imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = if (isPlaying) "Jeda ${clip.name}" else "Putar ${clip.name}",
        )
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = clip.name,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            buildString {
              append(SpeakerAudioPlayerController.formatDuration(clip.durationMs))
              if (clip.fileSizeBytes > 0L) {
                append(" • ")
                append(SpeakerAudioPlayerController.formatFileSize(clip.fileSizeBytes))
              }
              if (clip.originalFileName.isNotBlank()) {
                append(" • ")
                append(clip.originalFileName)
              }
            },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onRename,
        modifier = Modifier.size(48.dp).testTag("audio_rename_${clip.id}"),
      ) {
        Icon(
          Icons.Rounded.Edit,
          contentDescription = "Ubah nama ${clip.name}",
          modifier = Modifier.size(18.dp),
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(48.dp).testTag("audio_delete_${clip.id}"),
      ) {
        Icon(
          Icons.Rounded.DeleteOutline,
          contentDescription = "Hapus ${clip.name}",
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}
