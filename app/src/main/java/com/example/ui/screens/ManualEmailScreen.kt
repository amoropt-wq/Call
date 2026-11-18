package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CallQueueItemEntity
import com.example.data.local.EmailTemplateEntity
import com.example.domain.EmailTemplateBuilder
import com.example.ui.components.HtmlEmailPreviewWebView
import com.example.ui.viewmodel.HaloAntriUiState

private enum class ManualEmailMode(val label: String) {
  TEMPLATE("Pakai Template"),
  CUSTOM("Tulis Bebas"),
}

@Composable
fun ManualEmailScreen(
  uiState: HaloAntriUiState,
  onOpenSmtpSettings: () -> Unit,
  onCreateTemplate: () -> Unit,
  onEditTemplate: (EmailTemplateEntity) -> Unit,
  onSendViaSmtp: (
    linkedContact: CallQueueItemEntity?,
    recipientEmail: String,
    subject: String,
    htmlBody: String,
    onSuccess: () -> Unit,
  ) -> Unit,
  onSendViaDeviceApp: (
    linkedContact: CallQueueItemEntity?,
    recipientEmail: String,
    subject: String,
    htmlBody: String,
    onSuccess: () -> Unit,
  ) -> Unit,
) {
  val templates = uiState.emailTemplates.ifEmpty { listOf(uiState.defaultEmailTemplate) }
  var selectedTemplateId by remember(uiState.defaultEmailTemplate.id) {
    mutableStateOf(uiState.defaultEmailTemplate.id)
  }
  val selectedTemplate =
    remember(templates, selectedTemplateId, uiState.defaultEmailTemplate) {
      templates.firstOrNull { it.id == selectedTemplateId } ?: uiState.defaultEmailTemplate
    }

  var linkedContact by remember { mutableStateOf<CallQueueItemEntity?>(null) }
  var recipientName by remember { mutableStateOf("") }
  var recipientEmail by remember { mutableStateOf("") }
  var extraNotes by remember { mutableStateOf("") }

  var emailMode by remember { mutableStateOf(ManualEmailMode.TEMPLATE) }
  var customSubject by remember { mutableStateOf("") }
  var customBody by remember { mutableStateOf("") }
  var wrapWithTemplateDesign by remember { mutableStateOf(true) }

  var showContactPicker by remember { mutableStateOf(false) }
  var showTemplateDropdown by remember { mutableStateOf(false) }

  val previewTimestamp = remember(
    recipientEmail,
    recipientName,
    extraNotes,
    selectedTemplate,
    emailMode,
    customSubject,
    customBody,
    wrapWithTemplateDesign,
  ) {
    System.currentTimeMillis()
  }

  val draftContact =
    remember(linkedContact, recipientEmail, recipientName, extraNotes) {
      CallQueueItemEntity(
        id = linkedContact?.id ?: 0L,
        name = recipientName.trim().ifBlank { "Pelanggan" },
        phoneNumber = linkedContact?.phoneNumber.orEmpty(),
        idNumber = linkedContact?.idNumber.orEmpty(),
        email = recipientEmail.trim(),
        notes = extraNotes.trim(),
      )
    }

  val effectiveSubject =
    remember(emailMode, customSubject, selectedTemplate, draftContact, previewTimestamp) {
      val templateSubject =
        EmailTemplateBuilder.renderSubject(selectedTemplate, draftContact, previewTimestamp)
      when {
        customSubject.isNotBlank() ->
          EmailTemplateBuilder.substituteTextPlaceholders(
            customSubject,
            draftContact,
            previewTimestamp,
          )
        emailMode == ManualEmailMode.TEMPLATE -> templateSubject
        else -> "Informasi dari HaloAntri"
      }
    }

  val renderedHtml =
    remember(
      emailMode,
      customBody,
      selectedTemplate,
      draftContact,
      wrapWithTemplateDesign,
      previewTimestamp,
    ) {
      if (emailMode == ManualEmailMode.TEMPLATE) {
        EmailTemplateBuilder.buildRenderedHtml(selectedTemplate, draftContact, previewTimestamp)
      } else {
        EmailTemplateBuilder.buildCustomManualEmailHtml(
          bodyText = customBody.ifBlank { "Halo {{nama}},\n\nSilakan ketik isi pesan email manual Anda di atas." },
          template = selectedTemplate,
          contact = draftContact,
          wrapWithTemplateDesign = wrapWithTemplateDesign,
          sentAtMillis = previewTimestamp,
        )
      }
    }

  fun resetForm() {
    linkedContact = null
    recipientName = ""
    recipientEmail = ""
    extraNotes = ""
    customSubject = ""
    customBody = ""
  }

  val smtpConfig = uiState.settings.smtpConfig
  val queueContactsWithEmail =
    remember(uiState.queueItems) {
      uiState.queueItems.sortedByDescending { it.email.isNotBlank() }
    }
  val hasValidRecipient = recipientEmail.trim().contains("@") && recipientEmail.trim().contains(".")

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // SMTP Status Banner
      item(key = "smtp_status_banner") {
        Card(
          shape = MaterialTheme.shapes.large,
          colors =
            CardDefaults.cardColors(
              containerColor =
                if (smtpConfig.isConfigured) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector =
                  if (smtpConfig.isConfigured) Icons.Rounded.CheckCircle
                  else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint =
                  if (smtpConfig.isConfigured) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.error,
              )
              Column {
                Text(
                  text = "Kirim Email Manual",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    if (smtpConfig.isConfigured) "SMTP aktif: ${smtpConfig.username}"
                    else "SMTP belum diatur • gunakan Pengaturan atau kirim via aplikasi email HP",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            TextButton(
              onClick = onOpenSmtpSettings,
              modifier = Modifier.heightIn(min = 48.dp).testTag("manual_email_open_smtp"),
            ) {
              Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(if (smtpConfig.isConfigured) "SMTP" else "Atur SMTP")
            }
          }
        }
      }

      // Recipient & Contact Data Card
      item(key = "recipient_card") {
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
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Tujuan & Data Penerima",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                  OutlinedButton(
                    onClick = { showContactPicker = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier =
                      Modifier.heightIn(min = 42.dp).testTag("manual_email_pick_queue_button"),
                  ) {
                    Icon(
                      Icons.Rounded.PersonSearch,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dari Antrian")
                  }
                  DropdownMenu(
                    expanded = showContactPicker,
                    onDismissRequest = { showContactPicker = false },
                  ) {
                    if (queueContactsWithEmail.isEmpty()) {
                      DropdownMenuItem(
                        text = { Text("Belum ada data di antrian") },
                        enabled = false,
                        onClick = {},
                      )
                    } else {
                      queueContactsWithEmail.take(25).forEach { item ->
                        DropdownMenuItem(
                          text = {
                            Column {
                              Text(
                                text = item.name,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                              )
                              Text(
                                text =
                                  listOfNotNull(
                                      item.email.takeIf { it.isNotBlank() },
                                      item.phoneNumber.takeIf { it.isNotBlank() },
                                    )
                                    .joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                              )
                            }
                          },
                          onClick = {
                            linkedContact = item
                            recipientName = item.name
                            recipientEmail = item.email
                            extraNotes = item.notes
                            showContactPicker = false
                          },
                          modifier = Modifier.testTag("manual_email_queue_pick_${item.id}"),
                        )
                      }
                    }
                  }
                }

                if (
                  recipientEmail.isNotBlank() ||
                    recipientName.isNotBlank() ||
                    extraNotes.isNotBlank() ||
                    customSubject.isNotBlank() ||
                    customBody.isNotBlank()
                ) {
                  IconButton(
                    onClick = ::resetForm,
                    modifier = Modifier.size(42.dp).testTag("manual_email_reset_button"),
                  ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset formulir email")
                  }
                }
              }
            }

            OutlinedTextField(
              value = recipientName,
              onValueChange = { recipientName = it },
              label = { Text("Nama Penerima") },
              placeholder = { Text("Contoh: PURWANTO") },
              leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
              trailingIcon = {
                if (recipientName.isNotEmpty()) {
                  IconButton(onClick = { recipientName = "" }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Hapus nama")
                  }
                }
              },
              singleLine = true,
              keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
              modifier = Modifier.fillMaxWidth().testTag("manual_email_name_input"),
            )

            OutlinedTextField(
              value = recipientEmail,
              onValueChange = {
                recipientEmail = it
                if (linkedContact != null && it.trim() != linkedContact?.email?.trim()) {
                  linkedContact = null
                }
              },
              label = { Text("Email Tujuan *") },
              placeholder = { Text("pelanggan@gmail.com") },
              leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
              trailingIcon = {
                if (recipientEmail.isNotEmpty()) {
                  IconButton(onClick = { recipientEmail = "" }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Hapus email")
                  }
                }
              },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
              modifier = Modifier.fillMaxWidth().testTag("manual_email_recipient_input"),
            )
          }
        }
      }

      // Email Composer & Template Card
      item(key = "composer_card") {
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
              text = "Format & Isi Email",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              ManualEmailMode.entries.forEach { mode ->
                FilterChip(
                  selected = emailMode == mode,
                  onClick = { emailMode = mode },
                  label = { Text(mode.label) },
                  leadingIcon = {
                    Icon(
                      imageVector =
                        if (mode == ManualEmailMode.TEMPLATE) Icons.Rounded.Style
                        else Icons.Rounded.Edit,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  },
                  modifier =
                    Modifier.weight(1f).heightIn(min = 48.dp).testTag("manual_email_mode_${mode.name}"),
                )
              }
            }

            if (emailMode == ManualEmailMode.TEMPLATE || wrapWithTemplateDesign) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(modifier = Modifier.weight(1f)) {
                  OutlinedButton(
                    onClick = { showTemplateDropdown = true },
                    modifier =
                      Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manual_email_template_dropdown"),
                  ) {
                    Text(
                      text = selectedTemplate.name,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = "Pilih template email")
                  }
                  DropdownMenu(
                    expanded = showTemplateDropdown,
                    onDismissRequest = { showTemplateDropdown = false },
                  ) {
                    templates.forEach { tpl ->
                      DropdownMenuItem(
                        text = { Text(tpl.name) },
                        onClick = {
                          selectedTemplateId = tpl.id
                          showTemplateDropdown = false
                        },
                      )
                    }
                  }
                }
                OutlinedButton(
                  onClick = { onEditTemplate(selectedTemplate) },
                  modifier = Modifier.heightIn(min = 48.dp).testTag("manual_email_edit_template"),
                ) {
                  Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Edit")
                }
                IconButton(
                  onClick = onCreateTemplate,
                  modifier = Modifier.size(48.dp).testTag("manual_email_new_template"),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = "Buat template email baru")
                }
              }
            }

            OutlinedTextField(
              value = customSubject,
              onValueChange = { customSubject = it },
              label = {
                Text(
                  if (emailMode == ManualEmailMode.TEMPLATE)
                    "Subjek Email (kosong = ikuti template)"
                  else "Subjek Email *"
                )
              },
              placeholder = {
                Text(
                  if (emailMode == ManualEmailMode.TEMPLATE)
                    EmailTemplateBuilder.renderSubject(selectedTemplate, draftContact, previewTimestamp)
                  else "Contoh: Konfirmasi Layanan Pelanggan"
                )
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("manual_email_subject_input"),
            )

            if (emailMode == ManualEmailMode.TEMPLATE) {
              OutlinedTextField(
                value = extraNotes,
                onValueChange = { extraNotes = it },
                label = { Text("Catatan / Pesan Tambahan ({{catatan}})") },
                placeholder = { Text("Masukkan pesan tambahan untuk pelanggan...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("manual_email_notes_input"),
              )
            } else {
              OutlinedTextField(
                value = customBody,
                onValueChange = { customBody = it },
                label = { Text("Isi Pesan Email Manual *") },
                placeholder = {
                  Text(
                    "Halo {{nama}},\n\nBerikut informasi layanan untuk nomor {{nomor_hp}}..."
                  )
                },
                minLines = 5,
                maxLines = 9,
                modifier = Modifier.fillMaxWidth().testTag("manual_email_custom_body_input"),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Gunakan Header, Logo & Footer Template",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = "Bungkus pesan manual dengan desain visual & logo template terpilih",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = wrapWithTemplateDesign,
                  onCheckedChange = { wrapWithTemplateDesign = it },
                  modifier = Modifier.testTag("manual_email_wrap_switch"),
                )
              }
            }

            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Rounded.Schedule,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                  text =
                    "Tanggal & Waktu Otomatis: ${EmailTemplateBuilder.formatDateTime(previewTimestamp)}",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }
        }
      }

      // Live HTML Preview Card
      item(key = "preview_card") {
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
              text = "Pratinjau Email HTML & Visual",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Subjek: $effectiveSubject",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Surface(
              shape = MaterialTheme.shapes.medium,
              tonalElevation = 1.dp,
              modifier =
                Modifier.fillMaxWidth()
                  .height(260.dp)
                  .clip(MaterialTheme.shapes.medium),
            ) {
              HtmlEmailPreviewWebView(
                htmlContent = renderedHtml,
                modifier = Modifier.fillMaxWidth(),
              )
            }

            Button(
              onClick = {
                val sendNow = System.currentTimeMillis()
                val finalSubject =
                  if (customSubject.isNotBlank()) {
                    EmailTemplateBuilder.substituteTextPlaceholders(
                      customSubject,
                      draftContact,
                      sendNow,
                    )
                  } else if (emailMode == ManualEmailMode.TEMPLATE) {
                    EmailTemplateBuilder.renderSubject(selectedTemplate, draftContact, sendNow)
                  } else {
                    "Informasi dari HaloAntri"
                  }
                val finalHtml =
                  if (emailMode == ManualEmailMode.TEMPLATE) {
                    EmailTemplateBuilder.buildRenderedHtml(selectedTemplate, draftContact, sendNow)
                  } else {
                    EmailTemplateBuilder.buildCustomManualEmailHtml(
                      bodyText = customBody,
                      template = selectedTemplate,
                      contact = draftContact,
                      wrapWithTemplateDesign = wrapWithTemplateDesign,
                      sentAtMillis = sendNow,
                    )
                  }
                onSendViaSmtp(
                  linkedContact,
                  recipientEmail,
                  finalSubject,
                  finalHtml,
                  ::resetForm,
                )
              },
              enabled =
                !uiState.isSendingEmail &&
                  smtpConfig.isConfigured &&
                  hasValidRecipient &&
                  (emailMode == ManualEmailMode.TEMPLATE || customBody.isNotBlank()),
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 50.dp)
                  .testTag("manual_email_send_smtp_button"),
            ) {
              if (uiState.isSendingEmail) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mengirim Email...")
              } else {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kirim Langsung via SMTP")
              }
            }

            OutlinedButton(
              onClick = {
                val sendNow = System.currentTimeMillis()
                val finalSubject =
                  if (customSubject.isNotBlank()) {
                    EmailTemplateBuilder.substituteTextPlaceholders(
                      customSubject,
                      draftContact,
                      sendNow,
                    )
                  } else if (emailMode == ManualEmailMode.TEMPLATE) {
                    EmailTemplateBuilder.renderSubject(selectedTemplate, draftContact, sendNow)
                  } else {
                    "Informasi dari HaloAntri"
                  }
                val finalHtml =
                  if (emailMode == ManualEmailMode.TEMPLATE) {
                    EmailTemplateBuilder.buildRenderedHtml(selectedTemplate, draftContact, sendNow)
                  } else {
                    EmailTemplateBuilder.buildCustomManualEmailHtml(
                      bodyText = customBody,
                      template = selectedTemplate,
                      contact = draftContact,
                      wrapWithTemplateDesign = wrapWithTemplateDesign,
                      sentAtMillis = sendNow,
                    )
                  }
                onSendViaDeviceApp(
                  linkedContact,
                  recipientEmail,
                  finalSubject,
                  finalHtml,
                  {},
                )
              },
              enabled =
                hasValidRecipient &&
                  (emailMode == ManualEmailMode.TEMPLATE || customBody.isNotBlank()),
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("manual_email_send_app_button"),
            ) {
              Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Kirim via Aplikasi Email Perangkat")
            }
          }
        }
      }
    }
  }
}
