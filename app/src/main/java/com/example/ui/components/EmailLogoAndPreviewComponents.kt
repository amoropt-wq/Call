package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.example.domain.EmailHeaderStyle
import com.example.domain.EmailTemplateBuilder
import com.example.domain.EmailVisualTheme
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun encodePickedLogoToDataUri(context: Context, uri: Uri): String? =
  withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return@withContext null

        var sampleSize = 1
        while (rawW / sampleSize > 512 || rawH / sampleSize > 512) {
          sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded =
          resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
          } ?: return@withContext null

        val maxSide = 220f
        val scale = minOf(maxSide / max(decoded.width, 1), maxSide / max(decoded.height, 1), 1f)
        val scaled =
          if (scale < 1f) {
            Bitmap.createScaledBitmap(
              decoded,
              max((decoded.width * scale).roundToInt(), 1),
              max((decoded.height * scale).roundToInt(), 1),
              true,
            )
          } else {
            decoded
          }

        val pngOut = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 95, pngOut)
        val bytes =
          if (pngOut.size() <= 45_000) {
            pngOut.toByteArray() to "image/png"
          } else {
            val jpgOut = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, jpgOut)
            jpgOut.toByteArray() to "image/jpeg"
          }

        if (scaled !== decoded) decoded.recycle()
        scaled.recycle()

        val b64 = Base64.encodeToString(bytes.first, Base64.NO_WRAP)
        "data:${bytes.second};base64,$b64"
      }
      .getOrNull()
  }

@Composable
fun LogoPickerField(
  title: String,
  logoValue: String,
  placeholderUrl: String,
  testTagPrefix: String,
  onValueChange: (String) -> Unit,
  onPickFromGallery: () -> Unit,
  onClear: () -> Unit,
) {
  val thumbnailBitmap =
    remember(logoValue) {
      if (logoValue.startsWith("data:image/") && logoValue.contains("base64,")) {
        runCatching {
            val b64 = logoValue.substringAfter("base64,")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
          }
          .getOrNull()
      } else {
        null
      }
    }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(text = title, style = MaterialTheme.typography.labelLarge)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (thumbnailBitmap != null) {
        Image(
          bitmap = thumbnailBitmap,
          contentDescription = title,
          contentScale = ContentScale.Fit,
          modifier = Modifier.size(44.dp),
        )
      }
      OutlinedTextField(
        value =
          if (logoValue.startsWith("data:image/")) "[Logo Gambar Terpasang]" else logoValue,
        onValueChange = { typed ->
          if (!logoValue.startsWith("data:image/")) {
            onValueChange(typed)
          }
        },
        readOnly = logoValue.startsWith("data:image/"),
        placeholder = { Text(placeholderUrl) },
        leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
        trailingIcon = {
          if (logoValue.isNotBlank()) {
            IconButton(
              onClick = onClear,
              modifier = Modifier.testTag("${testTagPrefix}_clear_button"),
            ) {
              Icon(Icons.Rounded.Close, contentDescription = "Hapus $title")
            }
          }
        },
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("${testTagPrefix}_input"),
      )
      OutlinedButton(
        onClick = onPickFromGallery,
        modifier = Modifier.heightIn(min = 52.dp).testTag("${testTagPrefix}_pick_button"),
      ) {
        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Pilih")
      }
    }
    if (logoValue.startsWith("data:image/")) {
      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
      ) {
        Text(
          text = "Logo gambar lokal siap disematkan ke email HTML.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    }
  }
}

@Composable
fun HtmlEmailPreviewWebView(htmlContent: String, modifier: Modifier = Modifier) {
  var renderFailed by remember { mutableStateOf(false) }

  if (renderFailed) {
    val plainFallback =
      remember(htmlContent) {
        HtmlCompat.fromHtml(htmlContent, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
      }
    Column(
      modifier =
        modifier
          .background(MaterialTheme.colorScheme.surface)
          .verticalScroll(rememberScrollState())
          .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = plainFallback,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      OutlinedButton(
        onClick = { renderFailed = false },
        modifier = Modifier.heightIn(min = 44.dp),
      ) {
        Text("Muat Ulang Pratinjau HTML")
      }
    }
    return
  }

  AndroidView(
    modifier = modifier.testTag("email_html_preview_webview"),
    factory = { context ->
      WebView(context).apply {
        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }
        settings.apply {
          javaScriptEnabled = false
          loadsImagesAutomatically = true
          useWideViewPort = false
          loadWithOverviewMode = false
          cacheMode = WebSettings.LOAD_NO_CACHE
          allowFileAccess = false
          allowContentAccess = false
        }
        webViewClient =
          object : WebViewClient() {
            override fun onRenderProcessGone(
              view: WebView?,
              detail: RenderProcessGoneDetail?,
            ): Boolean {
              view?.let { dead ->
                dead.stopLoading()
                (dead.parent as? ViewGroup)?.removeView(dead)
                dead.removeAllViews()
                dead.destroy()
              }
              renderFailed = true
              return true
            }
          }
        tag = htmlContent
        loadDataWithBaseURL("about:blank", htmlContent, "text/html", "UTF-8", null)
      }
    },
    update = { webView ->
      if (webView.tag != htmlContent) {
        webView.tag = htmlContent
        webView.loadDataWithBaseURL("about:blank", htmlContent, "text/html", "UTF-8", null)
      }
    },
    onRelease = { webView ->
      webView.stopLoading()
      webView.loadUrl("about:blank")
      webView.clearHistory()
      webView.removeAllViews()
      webView.destroy()
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeaderStyleAndColorControls(
  headerStyle: String,
  onSelectHeaderStyle: (String) -> Unit,
  onSelectHeaderTitlePreset: (String) -> Unit,
  accentColorHex: String,
  onSelectAccentColorHex: (String) -> Unit,
  backgroundStyle: String,
  onSelectBackgroundStyle: (String) -> Unit,
) {
  var customHexInput by remember(accentColorHex) { mutableStateOf(accentColorHex) }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Preset Judul Header Cepat", style = MaterialTheme.typography.labelLarge)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      EmailTemplateBuilder.HEADER_TITLE_PRESETS.forEach { preset ->
        AssistChip(
          onClick = { onSelectHeaderTitlePreset(preset) },
          label = { Text(preset) },
        )
      }
    }

    Text("Gaya Desain Header", style = MaterialTheme.typography.labelLarge)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      EmailHeaderStyle.entries.forEach { style ->
        FilterChip(
          selected = headerStyle == style.key,
          onClick = { onSelectHeaderStyle(style.key) },
          label = { Text(style.label) },
          modifier = Modifier.heightIn(min = 48.dp).testTag("header_style_${style.key}"),
        )
      }
    }

    Text("Warna Aksen Header & Tombol", style = MaterialTheme.typography.labelLarge)
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      EmailTemplateBuilder.ACCENT_COLOR_PRESETS.forEach { (hex, label) ->
        val swatchColor = Color(android.graphics.Color.parseColor(hex))
        val selected = accentColorHex.equals(hex, ignoreCase = true)
        Box(
          modifier =
            Modifier.size(40.dp)
              .clip(CircleShape)
              .background(swatchColor)
              .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                else Modifier
              )
              .clickable {
                customHexInput = hex
                onSelectAccentColorHex(hex)
              }
              .testTag("swatch_$label"),
          contentAlignment = Alignment.Center,
        ) {
          if (selected) {
            Icon(
              Icons.Rounded.Check,
              contentDescription = label,
              tint = Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }

    val safePreviewHex = EmailTemplateBuilder.sanitizeHexColor(accentColorHex)
    val previewColor = Color(android.graphics.Color.parseColor(safePreviewHex))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(36.dp)
            .clip(CircleShape)
            .background(previewColor)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
      )
      OutlinedTextField(
        value = customHexInput,
        onValueChange = { raw ->
          customHexInput = raw.take(7)
          val normalized = if (raw.startsWith("#")) raw else "#$raw"
          if (Regex("^#[0-9A-Fa-f]{6}$").matches(normalized.trim())) {
            onSelectAccentColorHex(normalized.trim().uppercase())
          }
        },
        label = { Text("Kode Warna Kustom (#HEX)") },
        placeholder = { Text("#007A5E") },
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("template_custom_hex_input"),
      )
    }

    Text("Tema Latar Belakang Email", style = MaterialTheme.typography.labelLarge)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      EmailVisualTheme.entries.forEach { theme ->
        FilterChip(
          selected = backgroundStyle == theme.key,
          onClick = { onSelectBackgroundStyle(theme.key) },
          label = { Text(theme.label) },
          modifier = Modifier.heightIn(min = 48.dp).testTag("bg_theme_${theme.key}"),
        )
      }
    }
  }
}
