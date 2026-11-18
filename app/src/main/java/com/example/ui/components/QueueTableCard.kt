package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CallQueueItemEntity
import com.example.data.local.QueueSource
import com.example.domain.PhoneNumberExtractor

fun CallQueueItemEntity.resolvedCapturedSender(): String = senderName.trim()

@Composable
fun SenderDropdownAndViewToggleRow(
  capturedSenders: List<String>,
  senderCounts: Map<String, Int>,
  totalItemsCount: Int,
  selectedSenderFilter: String?,
  isTableView: Boolean,
  onSelectSenderFilter: (String?) -> Unit,
  onToggleTableView: () -> Unit,
) {
  var dropdownExpanded by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.weight(1f)) {
      OutlinedButton(
        onClick = { dropdownExpanded = true },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier =
          Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sender_filter_dropdown"),
      ) {
        Icon(
          Icons.Rounded.FilterList,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text =
            if (selectedSenderFilter == null) "Pengirim: Tampilkan Semua"
            else "Pengirim: $selectedSenderFilter",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.ExpandMore, contentDescription = "Pilih pengirim yang ditangkap")
      }

      DropdownMenu(
        expanded = dropdownExpanded,
        onDismissRequest = { dropdownExpanded = false },
      ) {
        DropdownMenuItem(
          text = {
            Text(
              text = "Tampilkan Semua ($totalItemsCount)",
              fontWeight = if (selectedSenderFilter == null) FontWeight.Bold else FontWeight.Normal,
            )
          },
          leadingIcon = {
            if (selectedSenderFilter == null) {
              Icon(Icons.Rounded.Check, contentDescription = null)
            }
          },
          onClick = {
            onSelectSenderFilter(null)
            dropdownExpanded = false
          },
          modifier = Modifier.testTag("sender_filter_all"),
        )

        if (capturedSenders.isEmpty()) {
          DropdownMenuItem(
            text = {
              Text(
                text = "Belum ada nama pengirim tertangkap",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            },
            enabled = false,
            onClick = {},
          )
        } else {
          capturedSenders.forEach { sender ->
            val count = senderCounts[sender] ?: 0
            val isSelected = selectedSenderFilter == sender
            DropdownMenuItem(
              text = {
                Text(
                  text = "$sender ($count)",
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
              },
              leadingIcon = {
                if (isSelected) {
                  Icon(Icons.Rounded.Check, contentDescription = null)
                }
              },
              onClick = {
                onSelectSenderFilter(sender)
                dropdownExpanded = false
              },
              modifier = Modifier.testTag("sender_filter_item_$sender"),
            )
          }
        }
      }
    }

    FilterChip(
      selected = isTableView,
      onClick = onToggleTableView,
      label = { Text(if (isTableView) "Tabel" else "Kartu") },
      leadingIcon = {
        Icon(
          imageVector = if (isTableView) Icons.Rounded.TableRows else Icons.Rounded.ViewAgenda,
          contentDescription = "Ubah tampilan kolom tabel",
          modifier = Modifier.size(18.dp),
        )
      },
      modifier = Modifier.heightIn(min = 48.dp).testTag("queue_view_mode_toggle"),
    )
  }
}

@Composable
fun QueueTableCard(
  items: List<CallQueueItemEntity>,
  onCallItem: (CallQueueItemEntity) -> Unit,
  onSendEmail: (CallQueueItemEntity) -> Unit,
  onEditItem: (CallQueueItemEntity) -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth().testTag("queue_table_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
      // Table Header
      Row(
        modifier =
          Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TableHeaderCell("Pengirim", 145.dp)
        TableHeaderCell("Nama", 140.dp)
        TableHeaderCell("Nomor HP", 145.dp)
        TableHeaderCell("Nomor Kartu / ID", 180.dp)
        TableHeaderCell("Email", 200.dp)
        TableHeaderCell("Aksi", 220.dp)
      }
      HorizontalDivider()

      items.forEachIndexed { idx, item ->
        if (idx > 0) HorizontalDivider()
        val capturedSender = item.resolvedCapturedSender()
        Row(
          modifier =
            Modifier.clickable { onEditItem(item) }
              .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TableBodyCell(
            text = capturedSender.ifBlank { item.source.label },
            width = 145.dp,
            muted = capturedSender.isBlank(),
          )
          TableBodyCell(item.name, 140.dp, bold = true)
          TableBodyCell(PhoneNumberExtractor.formatDisplayPhone(item.phoneNumber), 145.dp)
          TableBodyCell(item.idNumber.ifBlank { "-" }, 180.dp, muted = item.idNumber.isBlank())
          TableBodyCell(
            text = item.email.ifBlank { "-" },
            width = 200.dp,
            muted = item.email.isBlank(),
          )
          Row(
            modifier = Modifier.width(220.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            FilledTonalButton(
              onClick = { onSendEmail(item) },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier.heightIn(min = 42.dp).testTag("table_email_item_${item.id}"),
            ) {
              Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Email")
            }
            IconButton(
              onClick = { onCallItem(item) },
              modifier = Modifier.size(42.dp).testTag("table_call_item_${item.id}"),
            ) {
              Icon(
                Icons.Rounded.Call,
                contentDescription = "Telpon WA",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
            IconButton(
              onClick = { onEditItem(item) },
              modifier = Modifier.size(42.dp).testTag("table_edit_item_${item.id}"),
            ) {
              Icon(Icons.Rounded.Edit, contentDescription = "Edit kontak")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TableHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.width(width).padding(end = 8.dp),
  )
}

@Composable
private fun TableBodyCell(
  text: String,
  width: androidx.compose.ui.unit.Dp,
  bold: Boolean = false,
  muted: Boolean = false,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    color =
      if (muted) MaterialTheme.colorScheme.onSurfaceVariant
      else MaterialTheme.colorScheme.onSurface,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.width(width).padding(end = 8.dp),
  )
}
