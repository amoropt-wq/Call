package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.EmailTemplateEntity
import com.example.domain.EmailTemplateBuilder
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.launch

@Composable
fun EmailTemplateEditorOverlay(
  existingTemplate: EmailTemplateEntity?,
  onSave: (template: EmailTemplateEntity, makeDefault: Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  BackHandler(onBack = onDismiss)

  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val base = existingTemplate ?: EmailTemplateBuilder.defaultTemplate()
  var name by remember(existingTemplate) { mutableStateOf(base.name) }
  var subject by remember(existingTemplate) { mutableStateOf(base.subject) }
  var headerTitle by remember(existingTemplate) { mutableStateOf(base.headerTitle) }
  var headerStyle by remember(existingTemplate) { mutableStateOf(base.headerStyle) }
  var headerLogoUrl by remember(existingTemplate) { mutableStateOf(base.headerLogoUrl) }
  var accentColorHex by remember(existingTemplate) { mutableStateOf(base.accentColorHex) }
  var backgroundStyle by remember(existingTemplate) { mutableStateOf(base.backgroundStyle) }
  var bodyHtml by remember(existingTemplate) { mutableStateOf(base.bodyHtml) }
  var ctaLabel by remember(existingTemplate) { mutableStateOf(base.ctaLabel) }
  var ctaUrl by remember(existingTemplate) { mutableStateOf(base.ctaUrl) }
  var footerText by remember(existingTemplate) { mutableStateOf(base.footerText) }
  var footerLogoUrl by remember(existingTemplate) { mutableStateOf(base.footerLogoUrl) }
  var useRawHtmlOnly by remember(existingTemplate) { mutableStateOf(base.useRawHtmlOnly) }
  var makeDefault by remember(existingTemplate) {
    mutableStateOf(existingTemplate?.isDefault ?: true)
  }

  val headerLogoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        coroutineScope.launch {
          encodePickedLogoToDataUri(context, uri)?.let { dataUri -> headerLogoUrl = dataUri }
        }
      }
    }

  val footerLogoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        coroutineScope.launch {
          encodePickedLogoToDataUri(context, uri)?.let { dataUri -> footerLogoUrl = dataUri }
        }
      }
    }

  var editorTabIndex by remember { mutableIntStateOf(0) } // 0 = Editor, 1 = Live Visual Preview

  val draftTemplate =
    EmailTemplateEntity(
      id = existingTemplate?.id ?: 0L,
      name = name.ifBlank { "Template Email" },
      subject = subject,
      headerTitle = headerTitle,
      headerStyle = headerStyle,
      headerLogoUrl = headerLogoUrl,
      accentColorHex = accentColorHex,
      backgroundStyle = backgroundStyle,
      bodyHtml = bodyHtml,
      ctaLabel = ctaLabel,
      ctaUrl = ctaUrl,
      footerText = footerText,
      footerLogoUrl = footerLogoUrl,
      useRawHtmlOnly = useRawHtmlOnly,
      isDefault = makeDefault,
    )

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
      // Top Bar
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("template_editor_back"),
              ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Tutup editor")
              }
              Column {
                Text(
                  text = "Template Email HTML & Visual",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Atur tampilan, kode HTML, dan variabel data antrian",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Button(
              onClick = { onSave(draftTemplate, makeDefault) },
              modifier = Modifier.heightIn(min = 44.dp).testTag("template_save_button"),
            ) {
              Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.size(6.dp))
              Text("Simpan")
            }
          }

          TabRow(selectedTabIndex = editorTabIndex) {
            Tab(
              selected = editorTabIndex == 0,
              onClick = { editorTabIndex = 0 },
              text = { Text("Editor HTML & Visual") },
              icon = { Icon(Icons.Rounded.Code, contentDescription = null) },
              modifier = Modifier.testTag("template_tab_editor"),
            )
            Tab(
              selected = editorTabIndex == 1,
              onClick = { editorTabIndex = 1 },
              text = { Text("Pratinjau Visual") },
              icon = { Icon(Icons.Rounded.Visibility, contentDescription = null) },
              modifier = Modifier.testTag("template_tab_preview"),
            )
          }
        }
      }

      if (editorTabIndex == 0) {
        Column(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nama Template") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("template_name_input"),
          )

          OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("Subjek Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("template_subject_input"),
          )

          // Placeholder chips
          Text(
            text = "Sisipkan Variabel Antrian ke Isi HTML:",
            style = MaterialTheme.typography.labelLarge,
          )
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf(
                "{{nama}}",
                "{{nomor_hp}}",
                "{{nomor_id}}",
                "{{email}}",
                "{{catatan}}",
                "{{tanggal}}",
                "{{waktu}}",
                "{{tanggal_waktu}}",
              )
              .forEach { tag ->
                AssistChip(
                  onClick = { bodyHtml += if (bodyHtml.endsWith(" ")) tag else " $tag" },
                  label = { Text(tag, fontFamily = JetBrainsMonoFamily) },
                )
              }
          }

          // Toggle Raw HTML vs Visual Builder
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Mode HTML Penuh (Tanpa Bingkai Visual)", style = MaterialTheme.typography.titleMedium)
              Text(
                "Aktifkan jika ingin menulis dokumen HTML lengkap sendiri",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = useRawHtmlOnly,
              onCheckedChange = { useRawHtmlOnly = it },
              modifier = Modifier.testTag("template_raw_html_switch"),
            )
          }

          if (!useRawHtmlOnly) {
            // Visual customization section
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Text("Pengaturan Tampilan Visual", style = MaterialTheme.typography.titleMedium)
                }

                OutlinedTextField(
                  value = headerTitle,
                  onValueChange = { headerTitle = it },
                  label = { Text("Judul Header Banner") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("template_header_input"),
                )

                LogoPickerField(
                  title = "Logo Header (URL atau Pilih Gambar)",
                  logoValue = headerLogoUrl,
                  placeholderUrl = "https://domain.com/logo-header.png",
                  testTagPrefix = "template_header_logo",
                  onValueChange = { headerLogoUrl = it },
                  onPickFromGallery = {
                    headerLogoPicker.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                  onClear = { headerLogoUrl = "" },
                )

                HeaderStyleAndColorControls(
                  headerStyle = headerStyle,
                  onSelectHeaderStyle = { headerStyle = it },
                  onSelectHeaderTitlePreset = { headerTitle = it },
                  accentColorHex = accentColorHex,
                  onSelectAccentColorHex = { accentColorHex = it },
                  backgroundStyle = backgroundStyle,
                  onSelectBackgroundStyle = { backgroundStyle = it },
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedTextField(
                    value = ctaLabel,
                    onValueChange = { ctaLabel = it },
                    label = { Text("Teks Tombol CTA") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                  )
                  OutlinedTextField(
                    value = ctaUrl,
                    onValueChange = { ctaUrl = it },
                    label = { Text("Link Tombol") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                  )
                }

                OutlinedTextField(
                  value = footerText,
                  onValueChange = { footerText = it },
                  label = { Text("Teks Footer") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                )

                LogoPickerField(
                  title = "Logo Footer (URL atau Pilih Gambar)",
                  logoValue = footerLogoUrl,
                  placeholderUrl = "https://domain.com/logo-footer.png",
                  testTagPrefix = "template_footer_logo",
                  onValueChange = { footerLogoUrl = it },
                  onPickFromGallery = {
                    footerLogoPicker.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                  onClear = { footerLogoUrl = "" },
                )
              }
            }
          }

          OutlinedTextField(
            value = bodyHtml,
            onValueChange = { bodyHtml = it },
            label = { Text("Konten HTML Email") },
            textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 13.sp),
            minLines = 7,
            maxLines = 14,
            modifier = Modifier.fillMaxWidth().testTag("template_body_html_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Jadikan Template Default", style = MaterialTheme.typography.titleMedium)
            Switch(
              checked = makeDefault,
              onCheckedChange = { makeDefault = it },
              modifier = Modifier.testTag("template_default_switch"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = { editorTabIndex = 1 },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
              Icon(Icons.Rounded.Visibility, contentDescription = null)
              Spacer(modifier = Modifier.size(6.dp))
              Text("Lihat Pratinjau")
            }
            Button(
              onClick = { onSave(draftTemplate, makeDefault) },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
              Text("Simpan Template")
            }
          }
        }
      } else {
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Subjek: ${EmailTemplateBuilder.renderSubject(draftTemplate, null)}",
              style = MaterialTheme.typography.labelLarge,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
          }
          HtmlEmailPreviewWebView(
            htmlContent = EmailTemplateBuilder.buildRenderedHtml(draftTemplate, null),
            modifier = Modifier.weight(1f).fillMaxWidth(),
          )
        }
      }
    }
  }
}
