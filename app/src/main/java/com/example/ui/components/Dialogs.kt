package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.data.local.CallQueueItemEntity
import com.example.data.local.WhatsAppAccountEntity
import com.example.data.local.WhatsAppFlavor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditQueueContactSheet(
  existingItem: CallQueueItemEntity?,
  onDismiss: () -> Unit,
  onSave: (name: String, phone: String, idNumber: String, email: String, notes: String, priority: Int) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var name by remember(existingItem) { mutableStateOf(existingItem?.name.orEmpty()) }
  var phone by remember(existingItem) { mutableStateOf(existingItem?.phoneNumber.orEmpty()) }
  var idNumber by remember(existingItem) { mutableStateOf(existingItem?.idNumber.orEmpty()) }
  var email by remember(existingItem) { mutableStateOf(existingItem?.email.orEmpty()) }
  var notes by remember(existingItem) { mutableStateOf(existingItem?.notes.orEmpty()) }
  var isPriority by remember(existingItem) { mutableStateOf((existingItem?.priority ?: 0) > 0) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp)
          .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (existingItem == null) "Tambah Data Manual ke Antrian" else "Edit Data Kontak",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Masukkan nama, nomor HP WhatsApp, dan nomor ID untuk dimasukkan ke antrian panggilan.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Nama Lengkap") },
        placeholder = { Text("Contoh: Budi Santoso") },
        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth().testTag("manual_name_input"),
      )

      OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Nomor HP / WhatsApp *") },
        placeholder = { Text("Contoh: 081234567890 atau +62812...") },
        leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth().testTag("manual_phone_input"),
      )

      OutlinedTextField(
        value = idNumber,
        onValueChange = { idNumber = it },
        label = { Text("Nomor Kartu / ID") },
        placeholder = { Text("Contoh: 4799 3111 0126 8348 atau ID-2026-001") },
        leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth().testTag("manual_id_input"),
      )

      OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        placeholder = { Text("Contoh: pelanggan@email.com") },
        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth().testTag("manual_email_input"),
      )

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Catatan (Opsional)") },
        placeholder = { Text("Keperluan panggilan atau keterangan tambahan") },
        leadingIcon = { Icon(Icons.Rounded.Notes, contentDescription = null) },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth().testTag("manual_notes_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Rounded.PriorityHigh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column {
            Text("Prioritas Tinggi", style = MaterialTheme.typography.titleMedium)
            Text(
              "Tempatkan di urutan teratas antrian",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Switch(
          checked = isPriority,
          onCheckedChange = { isPriority = it },
          modifier = Modifier.testTag("manual_priority_switch"),
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("manual_cancel_button"),
        ) {
          Text("Batal")
        }
        Button(
          onClick = { onSave(name, phone, idNumber, email, notes, if (isPriority) 1 else 0) },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("manual_save_button"),
        ) {
          Text(if (existingItem == null) "Simpan ke Antrian" else "Simpan Perubahan")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditWhatsAppAccountSheet(
  existingAccount: WhatsAppAccountEntity?,
  onDismiss: () -> Unit,
  onSave: (label: String, phone: String, flavor: WhatsAppFlavor, makeActive: Boolean) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var label by remember(existingAccount) { mutableStateOf(existingAccount?.label.orEmpty()) }
  var phone by remember(existingAccount) { mutableStateOf(existingAccount?.phoneNumber.orEmpty()) }
  var flavor by remember(existingAccount) {
    mutableStateOf(existingAccount?.appFlavor ?: WhatsAppFlavor.PERSONAL)
  }
  var makeActive by remember(existingAccount) {
    mutableStateOf(existingAccount?.isActive ?: true)
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp)
          .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (existingAccount == null) "Tambah Nomor WhatsApp" else "Edit Nomor WhatsApp",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Tambahkan sebanyak mungkin nomor WhatsApp pemanggil untuk dikelola dari satu konsol.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedTextField(
        value = label,
        onValueChange = { label = it },
        label = { Text("Nama Label Nomor *") },
        placeholder = { Text("Contoh: CS Utama, Sales 2, Admin Pagi") },
        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth().testTag("wa_account_label_input"),
      )

      OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Nomor WhatsApp *") },
        placeholder = { Text("Contoh: 081234567890") },
        leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth().testTag("wa_account_phone_input"),
      )

      Text("Jenis Aplikasi WhatsApp", style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WhatsAppFlavor.entries.forEach { option ->
          FilterChip(
            selected = flavor == option,
            onClick = { flavor = option },
            label = { Text(option.displayName) },
            leadingIcon = {
              Icon(
                if (option == WhatsAppFlavor.BUSINESS) Icons.Rounded.Business
                else Icons.Rounded.Phone,
                contentDescription = null,
              )
            },
            modifier = Modifier.heightIn(min = 48.dp).testTag("wa_flavor_${option.name}"),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Jadikan Nomor Pemanggil Aktif", style = MaterialTheme.typography.titleMedium)
          Text(
            "Dipakai otomatis saat menekan tombol Panggil WA",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = makeActive,
          onCheckedChange = { makeActive = it },
          modifier = Modifier.testTag("wa_make_active_switch"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("wa_account_cancel_button"),
        ) {
          Text("Batal")
        }
        Button(
          onClick = { onSave(label, phone, flavor, makeActive) },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("wa_account_save_button"),
        ) {
          Text(if (existingAccount == null) "Tambahkan" else "Simpan")
        }
      }
    }
  }
}
