package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CallQueueItemEntity
import com.example.data.local.QueueSource
import com.example.data.local.QueueStatus
import com.example.data.local.WhatsAppAccountEntity
import com.example.domain.PhoneNumberExtractor
import com.example.ui.components.QueueTableCard
import com.example.ui.components.SenderDropdownAndViewToggleRow
import com.example.ui.components.resolvedCapturedSender
import com.example.ui.theme.LocalHaloColors
import com.example.ui.viewmodel.HaloAntriUiState
import com.example.ui.viewmodel.QueueFilter

@Composable
fun QueueScreen(
  uiState: HaloAntriUiState,
  onSelectFilter: (QueueFilter) -> Unit,
  onSelectSenderFilter: (String?) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onToggleStealthMute: () -> Unit,
  onOpenAudioSpeaker: () -> Unit,
  onSelectActiveAccount: (Long) -> Unit,
  onOpenAccountsTab: () -> Unit,
  onOpenCaptureTab: () -> Unit,
  onOpenManualEntry: () -> Unit,
  onCallNext: () -> Unit,
  onCallItem: (CallQueueItemEntity) -> Unit,
  onSendEmail: (CallQueueItemEntity) -> Unit,
  onMarkCompleted: (CallQueueItemEntity) -> Unit,
  onSkipItem: (CallQueueItemEntity) -> Unit,
  onRequeueItem: (CallQueueItemEntity) -> Unit,
  onEditItem: (CallQueueItemEntity) -> Unit,
  onDeleteItem: (CallQueueItemEntity) -> Unit,
  onClearFinished: () -> Unit,
) {
  var isTableView by remember { mutableStateOf(false) }

  val waitingCount =
    remember(uiState.queueItems) {
      uiState.queueItems.count {
        it.status == QueueStatus.WAITING || it.status == QueueStatus.CALLING
      }
    }

  val senderCounts =
    remember(uiState.queueItems) {
      uiState.queueItems
        .map { it.resolvedCapturedSender() }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
    }
  val capturedSenders = remember(senderCounts) { senderCounts.keys.sorted() }

  val filteredItems =
    remember(
      uiState.queueItems,
      uiState.queueFilter,
      uiState.selectedSenderFilter,
      uiState.searchQuery,
    ) {
      val byStatus =
        when (uiState.queueFilter) {
          QueueFilter.WAITING ->
            uiState.queueItems.filter {
              it.status == QueueStatus.WAITING || it.status == QueueStatus.CALLING
            }
          QueueFilter.FINISHED ->
            uiState.queueItems.filter {
              it.status == QueueStatus.COMPLETED || it.status == QueueStatus.SKIPPED
            }
          QueueFilter.ALL -> uiState.queueItems
        }
      val bySender =
        if (uiState.selectedSenderFilter.isNullOrBlank()) byStatus
        else byStatus.filter { it.resolvedCapturedSender() == uiState.selectedSenderFilter }
      val q = uiState.searchQuery.trim()
      if (q.isEmpty()) bySender
      else {
        bySender.filter {
          it.name.contains(q, ignoreCase = true) ||
            it.phoneNumber.contains(q, ignoreCase = true) ||
            it.idNumber.contains(q, ignoreCase = true) ||
            it.email.contains(q, ignoreCase = true) ||
            it.senderName.contains(q, ignoreCase = true) ||
            it.capturedMessage.contains(q, ignoreCase = true)
        }
      }
    }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Tactical Command Hero Card
      item(key = "hero_card") {
        CommandHeroCard(
          activeAccount = uiState.activeAccount,
          accounts = uiState.accounts,
          waitingCount = waitingCount,
          isStealthMuted = uiState.isStealthMuted,
          isAudioPlaying = uiState.audioPlaybackState.isPlaying,
          isNotificationAccessGranted = uiState.isNotificationAccessGranted,
          onToggleStealthMute = onToggleStealthMute,
          onOpenAudioSpeaker = onOpenAudioSpeaker,
          onSelectActiveAccount = onSelectActiveAccount,
          onOpenAccountsTab = onOpenAccountsTab,
          onOpenCaptureTab = onOpenCaptureTab,
          onCallNext = onCallNext,
        )
      }

      // Search & Filter Controls
      item(key = "search_filters") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Cari nama, nomor HP, ID, atau email...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
              if (uiState.searchQuery.isNotEmpty()) {
                IconButton(
                  onClick = { onSearchQueryChange("") },
                  modifier = Modifier.testTag("clear_search_button"),
                ) {
                  Icon(Icons.Rounded.Clear, contentDescription = "Hapus pencarian")
                }
              }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("queue_search_input"),
          )

          SenderDropdownAndViewToggleRow(
            capturedSenders = capturedSenders,
            senderCounts = senderCounts,
            totalItemsCount = uiState.queueItems.size,
            selectedSenderFilter = uiState.selectedSenderFilter,
            isTableView = isTableView,
            onSelectSenderFilter = onSelectSenderFilter,
            onToggleTableView = { isTableView = !isTableView },
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              QueueFilter.entries.forEach { filter ->
                FilterChip(
                  selected = uiState.queueFilter == filter,
                  onClick = { onSelectFilter(filter) },
                  label = { Text(filter.label) },
                  modifier = Modifier.heightIn(min = 48.dp).testTag("filter_${filter.name}"),
                )
              }
            }
            if (
              uiState.queueFilter == QueueFilter.FINISHED &&
                filteredItems.isNotEmpty()
            ) {
              TextButton(
                onClick = onClearFinished,
                modifier = Modifier.testTag("clear_finished_button"),
              ) {
                Text("Bersihkan")
              }
            }
          }
        }
      }

      if (filteredItems.isEmpty()) {
        item(key = "empty_state") {
          EmptyQueueCard(
            hasSearch = uiState.searchQuery.isNotBlank() || uiState.selectedSenderFilter != null,
            filter = uiState.queueFilter,
            onAddManual = onOpenManualEntry,
            onOpenCapture = onOpenCaptureTab,
          )
        }
      } else if (isTableView) {
        item(key = "table_view") {
          QueueTableCard(
            items = filteredItems,
            onCallItem = onCallItem,
            onSendEmail = onSendEmail,
            onEditItem = onEditItem,
          )
        }
      } else {
        items(filteredItems, key = { it.id }) { item ->
          QueueContactCard(
            item = item,
            onCall = { onCallItem(item) },
            onSendEmail = { onSendEmail(item) },
            onComplete = { onMarkCompleted(item) },
            onSkip = { onSkipItem(item) },
            onRequeue = { onRequeueItem(item) },
            onEdit = { onEditItem(item) },
            onDelete = { onDeleteItem(item) },
          )
        }
      }
    }
  }
}

@Composable
private fun CommandHeroCard(
  activeAccount: WhatsAppAccountEntity?,
  accounts: List<WhatsAppAccountEntity>,
  waitingCount: Int,
  isStealthMuted: Boolean,
  isAudioPlaying: Boolean,
  isNotificationAccessGranted: Boolean,
  onToggleStealthMute: () -> Unit,
  onOpenAudioSpeaker: () -> Unit,
  onSelectActiveAccount: (Long) -> Unit,
  onOpenAccountsTab: () -> Unit,
  onOpenCaptureTab: () -> Unit,
  onCallNext: () -> Unit,
) {
  val haloColors = LocalHaloColors.current
  var showAccountDropdown by remember { mutableStateOf(false) }

  Card(
    shape = MaterialTheme.shapes.large,
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            Brush.linearGradient(colors = listOf(haloColors.heroStart, haloColors.heroEnd))
          )
          .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Antrian Panggilan",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
          Text(
            text = "$waitingCount kontak menunggu giliran",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
          )
        }

        // Capture Notification Status Pill
        Surface(
          shape = MaterialTheme.shapes.small,
          color = Color.White.copy(alpha = 0.16f),
          modifier =
            Modifier.clip(MaterialTheme.shapes.small)
              .clickable(onClick = onOpenCaptureTab)
              .testTag("capture_status_pill"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector =
                if (isNotificationAccessGranted) Icons.Rounded.NotificationsActive
                else Icons.Rounded.NotificationsOff,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = if (isNotificationAccessGranted) "Tangkap Aktif" else "Aktifkan Tangkap",
              style = MaterialTheme.typography.labelLarge,
              color = Color.White,
            )
          }
        }
      }

      // Active WhatsApp Line Switcher + Stealth Mute Quick Toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(modifier = Modifier.weight(1f)) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = 0.15f),
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                  if (accounts.isEmpty()) onOpenAccountsTab()
                  else showAccountDropdown = true
                }
                .testTag("active_wa_line_selector"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Nomor WA Pemanggil",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.78f),
                )
                Text(
                  text =
                    activeAccount?.let { "${it.label} (${it.appFlavor.displayName})" }
                      ?: "Tambah Nomor WA (${accounts.size})",
                  style = MaterialTheme.typography.labelLarge,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Icon(
                Icons.Rounded.SwapHoriz,
                contentDescription = "Ganti nomor WhatsApp",
                tint = Color.White,
              )
            }
          }

          DropdownMenu(
            expanded = showAccountDropdown,
            onDismissRequest = { showAccountDropdown = false },
          ) {
            accounts.forEach { acc ->
              DropdownMenuItem(
                text = {
                  Column {
                    Text(acc.label, fontWeight = FontWeight.SemiBold)
                    Text(
                      "${PhoneNumberExtractor.formatDisplayPhone(acc.phoneNumber)} • ${acc.appFlavor.displayName}",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                },
                onClick = {
                  onSelectActiveAccount(acc.id)
                  showAccountDropdown = false
                },
              )
            }
            DropdownMenuItem(
              text = { Text("+ Kelola Semua Nomor WA (${accounts.size})") },
              onClick = {
                showAccountDropdown = false
                onOpenAccountsTab()
              },
            )
          }
        }

        // Quick Stealth Mute Pill + Speaker Audio Player Pill
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color =
              if (isStealthMuted) haloColors.stealthAccent
              else Color.White.copy(alpha = 0.15f),
            modifier =
              Modifier.heightIn(min = 52.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onToggleStealthMute)
                .testTag("hero_stealth_mute_toggle"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = if (isStealthMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                contentDescription = "Toggle bisu senyap",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = if (isStealthMuted) "Bisu" else "Mic",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
              )
            }
          }

          Surface(
            shape = MaterialTheme.shapes.medium,
            color =
              if (isAudioPlaying) Color.White
              else Color.White.copy(alpha = 0.15f),
            modifier =
              Modifier.heightIn(min = 52.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onOpenAudioSpeaker)
                .testTag("hero_audio_speaker_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector =
                  if (isAudioPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.RecordVoiceOver,
                contentDescription = "Pemutar audio pembicara",
                tint = if (isAudioPlaying) haloColors.heroStart else Color.White,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = if (isAudioPlaying) "Putar" else "Pembicara",
                style = MaterialTheme.typography.labelLarge,
                color = if (isAudioPlaying) haloColors.heroStart else Color.White,
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = waitingCount > 0) {
        Button(
          onClick = onCallNext,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color.White,
              contentColor = haloColors.heroStart,
            ),
          modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("call_next_button"),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Mulai Panggil Berikutnya", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun QueueContactCard(
  item: CallQueueItemEntity,
  onCall: () -> Unit,
  onSendEmail: () -> Unit,
  onComplete: () -> Unit,
  onSkip: () -> Unit,
  onRequeue: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val haloColors = LocalHaloColors.current
  val isWaiting = item.status == QueueStatus.WAITING || item.status == QueueStatus.CALLING

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth().testTag("queue_item_card_${item.id}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Top badges row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color =
              if (item.source == QueueSource.MANUAL) MaterialTheme.colorScheme.secondaryContainer
              else MaterialTheme.colorScheme.primaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (item.source != QueueSource.MANUAL) {
                Icon(
                  Icons.Rounded.MarkChatRead,
                  contentDescription = null,
                  modifier = Modifier.size(13.dp),
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
              Text(
                text = item.source.label,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (item.source == QueueSource.MANUAL)
                    MaterialTheme.colorScheme.onSecondaryContainer
                  else MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }

          if (item.priority > 0) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = haloColors.priorityContainer,
            ) {
              Text(
                text = "PRIORITAS",
                style = MaterialTheme.typography.labelSmall,
                color = haloColors.priorityAccent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }

          if (item.status == QueueStatus.CALLING) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = haloColors.stealthContainer,
            ) {
              Text(
                text = "SEDANG DITELPON",
                style = MaterialTheme.typography.labelSmall,
                color = haloColors.onStealthContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }

        Row {
          IconButton(
            onClick = onEdit,
            modifier = Modifier.size(40.dp).testTag("edit_queue_item_${item.id}"),
          ) {
            Icon(
              Icons.Rounded.Edit,
              contentDescription = "Edit kontak",
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(40.dp).testTag("delete_queue_item_${item.id}"),
          ) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = "Hapus kontak",
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      // Main Identity: Name, Phone Number, ID Number
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = item.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = PhoneNumberExtractor.formatDisplayPhone(item.phoneNumber),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          if (item.idNumber.isNotBlank()) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = haloColors.monoNumberSurface,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Rounded.Badge,
                  contentDescription = null,
                  modifier = Modifier.size(13.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "Kartu / ID: ${item.idNumber}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
        val capturedSender = item.resolvedCapturedSender()
        if (capturedSender.isNotBlank()) {
          Text(
            text = "Pengirim: $capturedSender",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        ) {
          Icon(
            Icons.Rounded.Email,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint =
              if (item.email.isNotBlank()) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text =
              if (item.email.isNotBlank()) "Email: ${item.email}"
              else "Email: Belum diisi (ketuk untuk isi)",
            style = MaterialTheme.typography.bodyMedium,
            color =
              if (item.email.isNotBlank()) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Captured message or notes snippet
      val detailText = item.capturedMessage.ifBlank { item.notes }
      if (detailText.isNotBlank()) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = detailText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(10.dp),
          )
        }
      }

      // Action buttons row
      if (isWaiting) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = onCall,
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("call_item_${item.id}"),
            ) {
              Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Telpon WA")
            }
            FilledTonalButton(
              onClick = onComplete,
              modifier = Modifier.heightIn(min = 48.dp).testTag("complete_item_${item.id}"),
            ) {
              Icon(Icons.Rounded.CheckCircle, contentDescription = "Tandai selesai")
            }
            OutlinedButton(
              onClick = onSkip,
              modifier = Modifier.heightIn(min = 48.dp).testTag("skip_item_${item.id}"),
            ) {
              Icon(Icons.Rounded.SkipNext, contentDescription = "Lewati")
            }
          }

          FilledTonalButton(
            onClick = onSendEmail,
            modifier =
              Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("email_item_${item.id}"),
          ) {
            Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text =
                if (item.email.isNotBlank()) "Kirim Email • ${item.email}"
                else "Kirim Email (Isi Alamat)",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Text(
              text = item.status.label,
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
          Spacer(modifier = Modifier.weight(1f))
          OutlinedButton(
            onClick = onSendEmail,
            modifier = Modifier.heightIn(min = 48.dp).testTag("email_item_${item.id}"),
          ) {
            Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Email")
          }
          FilledTonalButton(
            onClick = onRequeue,
            modifier = Modifier.heightIn(min = 48.dp).testTag("requeue_item_${item.id}"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Antrikan Lagi")
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyQueueCard(
  hasSearch: Boolean,
  filter: QueueFilter,
  onAddManual: () -> Unit,
  onOpenCapture: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_empty_queue_1794855352586),
        contentDescription = "Ilustrasi antrian panggilan kosong",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(160.dp).clip(MaterialTheme.shapes.medium),
      )
      Text(
        text =
          when {
            hasSearch -> "Tidak Ada Hasil Cocok"
            filter == QueueFilter.FINISHED -> "Belum Ada Panggilan Selesai"
            else -> "Antrian Panggilan Masih Kosong"
          },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text =
          "Tambahkan kontak manual (Nama, Nomor HP, Nomor ID) atau aktifkan penangkap pesan masuk agar nomor otomatis masuk antrian.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onAddManual,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("empty_add_manual_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Tambah Manual")
        }
        OutlinedButton(
          onClick = onOpenCapture,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("empty_open_capture_button"),
        ) {
          Text("Tangkap Pesan")
        }
      }
    }
  }
}
