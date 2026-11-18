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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.WhatsAppAccountEntity
import com.example.data.local.WhatsAppFlavor
import com.example.domain.PhoneNumberExtractor
import com.example.ui.viewmodel.HaloAntriUiState

@Composable
fun WhatsAppAccountsScreen(
  uiState: HaloAntriUiState,
  onAddAccount: () -> Unit,
  onActivateAccount: (Long) -> Unit,
  onOpenWhatsAppWeb: (WhatsAppAccountEntity?) -> Unit,
  onEditAccount: (WhatsAppAccountEntity) -> Unit,
  onDeleteAccount: (WhatsAppAccountEntity) -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "accounts_header") {
        Card(
          shape = MaterialTheme.shapes.large,
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Multi-Nomor WhatsApp",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                  text = "${uiState.accounts.size} nomor terdaftar • Tambah tanpa batas",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = onAddAccount,
                modifier =
                  Modifier.weight(1f).heightIn(min = 48.dp).testTag("add_wa_account_button"),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tambah Nomor")
              }
              OutlinedButton(
                onClick = { onOpenWhatsAppWeb(uiState.activeAccount) },
                modifier =
                  Modifier.weight(1f).heightIn(min = 48.dp).testTag("open_wa_web_header_button"),
              ) {
                Icon(Icons.Rounded.QrCode2, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("WhatsApp Web")
              }
            }
          }
        }
      }

      if (uiState.accounts.isEmpty()) {
        item(key = "accounts_empty") {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Belum Ada Nomor WhatsApp Pemanggil",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text =
                  "Tambahkan nomor WhatsApp Personal maupun WhatsApp Business sebanyak yang Anda perlukan untuk berganti jalur panggilan kapan saja.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(uiState.accounts, key = { it.id }) { account ->
          WhatsAppAccountCard(
            account = account,
            onActivate = { onActivateAccount(account.id) },
            onOpenWeb = { onOpenWhatsAppWeb(account) },
            onEdit = { onEditAccount(account) },
            onDelete = { onDeleteAccount(account) },
          )
        }
      }
    }
  }
}

@Composable
private fun WhatsAppAccountCard(
  account: WhatsAppAccountEntity,
  onActivate: () -> Unit,
  onOpenWeb: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = if (account.isActive) 2.dp else 1.dp),
    modifier = Modifier.fillMaxWidth().testTag("wa_account_card_${account.id}"),
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
          Surface(
            shape = MaterialTheme.shapes.small,
            color =
              if (account.appFlavor == WhatsAppFlavor.BUSINESS)
                MaterialTheme.colorScheme.tertiaryContainer
              else MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector =
                  if (account.appFlavor == WhatsAppFlavor.BUSINESS) Icons.Rounded.Business
                  else Icons.Rounded.Phone,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = account.appFlavor.displayName,
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }
          if (account.isActive) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = "JALUR AKTIF",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }
        Row {
          IconButton(
            onClick = onEdit,
            modifier = Modifier.size(40.dp).testTag("edit_wa_account_${account.id}"),
          ) {
            Icon(
              Icons.Rounded.Edit,
              contentDescription = "Edit nomor WA",
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(40.dp).testTag("delete_wa_account_${account.id}"),
          ) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = "Hapus nomor WA",
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = account.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = PhoneNumberExtractor.formatDisplayPhone(account.phoneNumber),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Total panggilan: ${account.callCount}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (!account.isActive) {
          FilledTonalButton(
            onClick = onActivate,
            modifier = Modifier.heightIn(min = 48.dp).testTag("activate_wa_account_${account.id}"),
          ) {
            Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Aktifkan")
          }
        } else {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Dipakai",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }

      OutlinedButton(
        onClick = onOpenWeb,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_wa_web_${account.id}"),
      ) {
        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Hubungkan / Buka WhatsApp Web (Sesi Tersendiri)")
      }
    }
  }
}
