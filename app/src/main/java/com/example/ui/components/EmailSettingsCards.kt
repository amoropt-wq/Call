package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.EmailTemplateEntity
import com.example.data.prefs.SmtpConfig
import com.example.domain.EmailVisualTheme

@Composable
fun SmtpConnectionCard(
  smtpConfig: SmtpConfig,
  isTesting: Boolean,
  statusMessage: String?,
  onSaveConfig: (SmtpConfig) -> Unit,
  onTestConnection: (SmtpConfig) -> Unit,
) {
  var host by remember(smtpConfig) { mutableStateOf(smtpConfig.host) }
  var portText by remember(smtpConfig) { mutableStateOf(smtpConfig.port.toString()) }
  var useSsl by remember(smtpConfig) { mutableStateOf(smtpConfig.useSsl) }
  var senderName by remember(smtpConfig) { mutableStateOf(smtpConfig.senderName) }
  var username by remember(smtpConfig) { mutableStateOf(smtpConfig.username) }
  var password by remember(smtpConfig) { mutableStateOf(smtpConfig.password) }
  var passwordVisible by remember { mutableStateOf(false) }

  fun buildDraft(): SmtpConfig {
    val parsedPort = portText.trim().toIntOrNull() ?: if (useSsl) 465 else 587
    return SmtpConfig(
      host = host.trim(),
      port = parsedPort,
      useSsl = useSsl,
      username = username.trim(),
      password = password,
      senderName = senderName.trim().ifBlank { "HaloAntri" },
      isVerified = smtpConfig.isVerified,
    )
  }

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
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Rounded.Email,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Koneksi Email (SMTP)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
        Surface(
          shape = MaterialTheme.shapes.small,
          color =
            if (smtpConfig.isVerified) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector =
                if (smtpConfig.isVerified) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
              tint =
                if (smtpConfig.isVerified) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text =
                when {
                  smtpConfig.isVerified -> "Terhubung"
                  smtpConfig.isConfigured -> "Belum Diuji"
                  else -> "Belum Diatur"
                },
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }

      Text(
        text =
          "Hubungkan akun SMTP (Gmail App Password, Outlook, atau server domain sendiri) untuk mengirim email HTML langsung dari antrian.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Provider Quick Presets
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = host.equals("smtp.gmail.com", ignoreCase = true),
          onClick = {
            host = "smtp.gmail.com"
            portText = "465"
            useSsl = true
          },
          label = { Text("Gmail (SSL)") },
          modifier = Modifier.heightIn(min = 48.dp).testTag("smtp_preset_gmail"),
        )
        FilterChip(
          selected = host.equals("smtp.office365.com", ignoreCase = true),
          onClick = {
            host = "smtp.office365.com"
            portText = "587"
            useSsl = false
          },
          label = { Text("Outlook (TLS)") },
          modifier = Modifier.heightIn(min = 48.dp).testTag("smtp_preset_outlook"),
        )
        FilterChip(
          selected =
            host.isNotBlank() &&
              !host.equals("smtp.gmail.com", ignoreCase = true) &&
              !host.equals("smtp.office365.com", ignoreCase = true),
          onClick = {
            if (host == "smtp.gmail.com" || host == "smtp.office365.com") {
              host = ""
            }
          },
          label = { Text("Custom") },
          modifier = Modifier.heightIn(min = 48.dp).testTag("smtp_preset_custom"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = host,
          onValueChange = { host = it },
          label = { Text("Host Server SMTP") },
          placeholder = { Text("smtp.gmail.com") },
          singleLine = true,
          modifier = Modifier.weight(2f).testTag("smtp_host_input"),
        )
        OutlinedTextField(
          value = portText,
          onValueChange = { portText = it.filter(Char::isDigit) },
          label = { Text("Port") },
          placeholder = { Text("465") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f).testTag("smtp_port_input"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (useSsl) "Enkripsi SSL Langsung (Port 465)" else "Enkripsi STARTTLS (Port 587)",
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = "Gmail direkomendasikan memakai SSL 465 + App Password",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = useSsl,
          onCheckedChange = { checked ->
            useSsl = checked
            if (portText == "465" || portText == "587" || portText.isBlank()) {
              portText = if (checked) "465" else "587"
            }
          },
          modifier = Modifier.testTag("smtp_ssl_switch"),
        )
      }

      OutlinedTextField(
        value = senderName,
        onValueChange = { senderName = it },
        label = { Text("Nama Pengirim") },
        placeholder = { Text("HaloAntri CS") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("smtp_sender_name_input"),
      )

      OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Email Pengirim / Username SMTP") },
        placeholder = { Text("admin@domain.com") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth().testTag("smtp_username_input"),
      )

      OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password / App Password") },
        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
        trailingIcon = {
          IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(
              imageVector =
                if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
              contentDescription = if (passwordVisible) "Sembunyikan password" else "Lihat password",
            )
          }
        },
        visualTransformation =
          if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().testTag("smtp_password_input"),
      )

      if (!statusMessage.isNullOrBlank()) {
        val isError = statusMessage.startsWith("Gagal", ignoreCase = true)
        Surface(
          shape = MaterialTheme.shapes.small,
          color =
            if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color =
              if (isError) MaterialTheme.colorScheme.onErrorContainer
              else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(10.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = { onTestConnection(buildDraft()) },
          enabled = !isTesting,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("smtp_test_button"),
        ) {
          if (isTesting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Menguji...")
          } else {
            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Tes Koneksi")
          }
        }
        Button(
          onClick = { onSaveConfig(buildDraft()) },
          enabled = !isTesting,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("smtp_save_button"),
        ) {
          Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Simpan SMTP")
        }
      }
    }
  }
}

@Composable
fun EmailTemplatesCard(
  templates: List<EmailTemplateEntity>,
  onCreateTemplate: () -> Unit,
  onEditTemplate: (EmailTemplateEntity) -> Unit,
  onSetDefaultTemplate: (Long) -> Unit,
  onDeleteTemplate: (Long) -> Unit,
  onOpenManualEmail: (() -> Unit)? = null,
) {
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
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            Icons.Rounded.Html,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Template Email HTML & Visual",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
        OutlinedButton(
          onClick = onCreateTemplate,
          modifier = Modifier.heightIn(min = 48.dp).testTag("new_email_template_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Baru")
        }
      }

      Text(
        text =
          "Atur desain visual, warna header, tombol CTA, dan kode HTML dengan variabel {{nama}}, {{nomor_hp}}, {{nomor_id}}, {{email}}, dan {{catatan}}.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (onOpenManualEmail != null) {
        OutlinedButton(
          onClick = onOpenManualEmail,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings_open_manual_email_button"),
        ) {
          Icon(
            Icons.AutoMirrored.Rounded.Send,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Buka Menu Kirim Email Manual")
        }
      }

      templates.forEachIndexed { idx, tpl ->
        if (idx > 0) HorizontalDivider()
        val themeLabel = EmailVisualTheme.fromKey(tpl.backgroundStyle).label
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = tpl.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (tpl.isDefault) {
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                  Text(
                    text = "DEFAULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
              }
            }
            Text(
              text =
                if (tpl.useRawHtmlOnly) "Mode HTML Penuh • ${tpl.subject}"
                else "Tema $themeLabel • ${tpl.subject}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            if (!tpl.isDefault) {
              TextButton(
                onClick = { onSetDefaultTemplate(tpl.id) },
                modifier = Modifier.testTag("set_default_template_${tpl.id}"),
              ) {
                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("Pakai")
              }
            }
            IconButton(
              onClick = { onEditTemplate(tpl) },
              modifier = Modifier.size(48.dp).testTag("edit_template_${tpl.id}"),
            ) {
              Icon(Icons.Rounded.Edit, contentDescription = "Edit template")
            }
            if (templates.size > 1) {
              IconButton(
                onClick = { onDeleteTemplate(tpl.id) },
                modifier = Modifier.size(48.dp).testTag("delete_template_${tpl.id}"),
              ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Hapus template")
              }
            }
          }
        }
      }
    }
  }
}
