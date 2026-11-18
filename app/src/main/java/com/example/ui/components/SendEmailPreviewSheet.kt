package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CallQueueItemEntity
import com.example.data.local.EmailTemplateEntity
import com.example.data.prefs.SmtpConfig
import com.example.domain.EmailTemplateBuilder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendEmailPreviewSheet(
  contact: CallQueueItemEntity,
  templates: List<EmailTemplateEntity>,
  defaultTemplate: EmailTemplateEntity,
  smtpConfig: SmtpConfig,
  isSending: Boolean,
  onDismiss: () -> Unit,
  onOpenSmtpSettings: () -> Unit,
  onEditTemplate: (EmailTemplateEntity) -> Unit,
  onSendViaSmtp: (recipientEmail: String, subject: String, htmlBody: String) -> Unit,
  onSendViaDeviceApp: (recipientEmail: String, subject: String, htmlBody: String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var recipientEmail by remember(contact) { mutableStateOf(contact.email) }
  var selectedTemplate by remember(defaultTemplate, templates) {
    mutableStateOf(defaultTemplate)
  }
  var showTemplateDropdown by remember { mutableStateOf(false) }

  val previewTimestamp = remember(contact, selectedTemplate) { System.currentTimeMillis() }
  val contactPreview = remember(contact, recipientEmail) { contact.copy(email = recipientEmail) }
  var subject by remember(selectedTemplate, contactPreview) {
    mutableStateOf(
      EmailTemplateBuilder.renderSubject(selectedTemplate, contactPreview, previewTimestamp)
    )
  }
  val renderedHtml =
    remember(selectedTemplate, contactPreview, previewTimestamp) {
      EmailTemplateBuilder.buildRenderedHtml(selectedTemplate, contactPreview, previewTimestamp)
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Kirim Email ke ${contact.name}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              if (smtpConfig.isConfigured) "Pengirim SMTP: ${smtpConfig.username}"
              else "Koneksi SMTP belum diatur (bisa kirim via aplikasi email HP)",
            style = MaterialTheme.typography.bodyMedium,
            color =
              if (smtpConfig.isConfigured) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.error,
          )
        }
        if (!smtpConfig.isConfigured) {
          TextButton(onClick = onOpenSmtpSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Atur SMTP")
          }
        }
      }

      OutlinedTextField(
        value = recipientEmail,
        onValueChange = { recipientEmail = it },
        label = { Text("Email Tujuan *") },
        placeholder = { Text("nama@email.com") },
        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth().testTag("send_email_recipient_input"),
      )

      // Template selector + Edit shortcut
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.weight(1f)) {
          OutlinedButton(
            onClick = { showTemplateDropdown = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
          ) {
            Text(selectedTemplate.name, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore, contentDescription = "Pilih template")
          }
          DropdownMenu(
            expanded = showTemplateDropdown,
            onDismissRequest = { showTemplateDropdown = false },
          ) {
            val list = templates.ifEmpty { listOf(defaultTemplate) }
            list.forEach { tpl ->
              DropdownMenuItem(
                text = { Text(tpl.name) },
                onClick = {
                  selectedTemplate = tpl
                  subject = EmailTemplateBuilder.renderSubject(tpl, contactPreview)
                  showTemplateDropdown = false
                },
              )
            }
          }
        }
        OutlinedButton(
          onClick = { onEditTemplate(selectedTemplate) },
          modifier = Modifier.heightIn(min = 48.dp).testTag("send_email_edit_template_button"),
        ) {
          Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Edit Template")
        }
      }

      OutlinedTextField(
        value = subject,
        onValueChange = { subject = it },
        label = { Text("Subjek") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("send_email_subject_input"),
      )

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
            text = "Tanggal & Waktu Otomatis: ${EmailTemplateBuilder.formatDateTime(previewTimestamp)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }

      Text("Pratinjau Email HTML & Visual", style = MaterialTheme.typography.labelLarge)
      Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier =
          Modifier.fillMaxWidth()
            .height(250.dp)
            .clip(MaterialTheme.shapes.medium),
      ) {
        HtmlEmailPreviewWebView(htmlContent = renderedHtml, modifier = Modifier.fillMaxWidth())
      }

      Button(
        onClick = {
          val sendNow = System.currentTimeMillis()
          val finalSubject =
            EmailTemplateBuilder.substituteTextPlaceholders(subject, contactPreview, sendNow)
          val finalHtml =
            EmailTemplateBuilder.buildRenderedHtml(selectedTemplate, contactPreview, sendNow)
          onSendViaSmtp(recipientEmail, finalSubject, finalHtml)
        },
        enabled = !isSending && smtpConfig.isConfigured && recipientEmail.contains("@"),
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("send_email_smtp_button"),
      ) {
        if (isSending) {
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
            EmailTemplateBuilder.substituteTextPlaceholders(subject, contactPreview, sendNow)
          val finalHtml =
            EmailTemplateBuilder.buildRenderedHtml(selectedTemplate, contactPreview, sendNow)
          onSendViaDeviceApp(recipientEmail, finalSubject, finalHtml)
        },
        enabled = recipientEmail.contains("@"),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("send_email_app_button"),
      ) {
        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Kirim via Aplikasi Email Perangkat")
      }
    }
  }
}
