package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.example.data.local.WhatsAppAccountEntity
import com.example.domain.PhoneNumberExtractor
import com.example.ui.theme.LocalHaloColors

@Composable
fun WhatsAppWebOverlay(
  account: WhatsAppAccountEntity?,
  targetPhoneNumber: String?,
  isStealthMuted: Boolean,
  isAudioPlaying: Boolean,
  onToggleStealthMute: () -> Unit,
  onOpenAudioSpeaker: () -> Unit,
  onCapturedWebMessage: (sender: String, body: String) -> Unit,
  onClose: () -> Unit,
) {
  BackHandler(onBack = onClose)

  val context = LocalContext.current
  val haloColors = LocalHaloColors.current
  val profileKey = account?.let { "wa_account_${it.id}" } ?: "wa_account_default"
  val multiProfileSupported = remember {
    WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
  }

  val initialUrl = remember(targetPhoneNumber) {
    val digits = targetPhoneNumber?.let { PhoneNumberExtractor.toWhatsAppDigits(it) }.orEmpty()
    if (digits.isNotBlank()) "https://web.whatsapp.com/send?phone=$digits"
    else WHATSAPP_WEB_URL
  }

  var useDesktopMode by remember { mutableStateOf(true) }
  var lightweightMode by remember { mutableStateOf(false) }
  var rendererCrashedOut by remember { mutableStateOf(false) }
  var resetSignal by remember { mutableIntStateOf(0) }
  var loadProgress by remember { mutableIntStateOf(0) }
  var isLoading by remember { mutableStateOf(true) }
  var webStatus by remember { mutableStateOf("LOADING_QR") }
  var errorBanner by remember { mutableStateOf<String?>(null) }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  fun resetAndReloadSession() {
    errorBanner = null
    rendererCrashedOut = false
    webStatus = "LOADING_QR"
    val cookieManager =
      if (!lightweightMode && multiProfileSupported) {
        runCatching { ProfileStore.getInstance().getOrCreateProfile(profileKey).cookieManager }
          .getOrNull() ?: CookieManager.getInstance()
      } else {
        CookieManager.getInstance()
      }
    cookieManager.removeAllCookies {
      cookieManager.flush()
      webViewRef?.clearCache(true)
      resetSignal++
    }
  }

  fun focusQrInView() {
    webViewRef?.evaluateJavascript(
      """
      (function() {
        var qr = document.querySelector('canvas[aria-label], [data-ref] canvas, [data-testid="qrcode"] canvas');
        if (qr) {
          qr.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' });
        }
      })();
      """.trimIndent(),
      null,
    )
  }

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
      // Top Control Bar
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
                onClick = onClose,
                modifier = Modifier.testTag("wa_web_close_button"),
              ) {
                Icon(
                  Icons.AutoMirrored.Rounded.ArrowBack,
                  contentDescription = "Tutup WhatsApp Web",
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = account?.label ?: "WhatsApp Web",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    when {
                      rendererCrashedOut -> "WebView berhenti (memori rendah)"
                      !useDesktopMode -> "Mode Mobile • JS & DOM Storage Aktif"
                      webStatus == "QR_READY" -> "QR Code siap dipindai"
                      webStatus == "CONNECTED" -> "WhatsApp Web terhubung"
                      lightweightMode -> "Mode Ringan • Memuat WhatsApp Web..."
                      else -> "Memuat QR WhatsApp Web..."
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Surface(
                shape = MaterialTheme.shapes.small,
                color =
                  if (isStealthMuted) haloColors.stealthContainer
                  else MaterialTheme.colorScheme.surfaceVariant,
              ) {
                IconButton(
                  onClick = onToggleStealthMute,
                  modifier = Modifier.testTag("wa_web_stealth_mute_button"),
                ) {
                  Icon(
                    imageVector =
                      if (isStealthMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = "Toggle bisu senyap",
                    tint =
                      if (isStealthMuted) haloColors.stealthAccent
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Surface(
                shape = MaterialTheme.shapes.small,
                color =
                  if (isAudioPlaying) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant,
              ) {
                IconButton(
                  onClick = onOpenAudioSpeaker,
                  modifier = Modifier.testTag("wa_web_audio_speaker_button"),
                ) {
                  Icon(
                    imageVector =
                      if (isAudioPlaying) Icons.Rounded.GraphicEq
                      else Icons.Rounded.RecordVoiceOver,
                    contentDescription = "Pemutar audio pembicara",
                    tint =
                      if (isAudioPlaying) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              IconButton(
                onClick = { focusQrInView() },
                modifier = Modifier.testTag("wa_web_focus_qr_button"),
              ) {
                Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Fokus ke QR Code")
              }
              IconButton(
                onClick = { resetAndReloadSession() },
                modifier = Modifier.testTag("wa_web_reset_session_button"),
              ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Reset sesi")
              }
              IconButton(
                onClick = {
                  errorBanner = null
                  rendererCrashedOut = false
                  webViewRef?.loadUrl(initialUrl) ?: run { resetSignal++ }
                },
                modifier = Modifier.testTag("wa_web_reload_button"),
              ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Muat ulang")
              }
            }
          }

          // Mode Switcher Bar (Desktop QR default vs Mobile)
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            FilterChip(
              selected = useDesktopMode,
              onClick = {
                rendererCrashedOut = false
                useDesktopMode = true
              },
              label = { Text("Desktop (QR)") },
              leadingIcon = {
                Icon(
                  Icons.Rounded.DesktopWindows,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier = Modifier.testTag("wa_web_mode_desktop"),
            )
            FilterChip(
              selected = !useDesktopMode,
              onClick = {
                rendererCrashedOut = false
                useDesktopMode = false
              },
              label = { Text("Mobile") },
              leadingIcon = {
                Icon(
                  Icons.Rounded.PhoneAndroid,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier = Modifier.testTag("wa_web_mode_mobile"),
            )
            FilterChip(
              selected = lightweightMode,
              onClick = {
                rendererCrashedOut = false
                lightweightMode = !lightweightMode
              },
              label = { Text("Ringan") },
              modifier = Modifier.testTag("wa_web_mode_lightweight"),
            )
          }

          Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Rounded.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text =
                  if (useDesktopMode)
                    "Tunggu hingga QR muncul (bisa pilih \"Tautkan dengan nomor telepon\"). Jika perangkat memori terbatas, aktifkan Mode Ringan."
                  else
                    "Mode Mobile aktif. Pindah ke tab Desktop (QR) di atas untuk memunculkan kode QR penautan.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }

          if (errorBanner != null) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = errorBanner.orEmpty(),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.weight(1f),
                )
                TextButton(
                  onClick = {
                    errorBanner = null
                    rendererCrashedOut = false
                    resetSignal++
                  }
                ) {
                  Text("Coba Lagi")
                }
              }
            }
          }

          if (isLoading && loadProgress in 1..99 && !rendererCrashedOut) {
            LinearProgressIndicator(
              progress = { loadProgress / 100f },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }

      Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (rendererCrashedOut) {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(20.dp),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
              )
              Text(
                text = "Renderer WebView Terhenti",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text =
                  "Mesin WebView di perangkat/emulator menghentikan proses halaman WhatsApp Web karena keterbatasan memori.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
              Button(
                onClick = {
                  useDesktopMode = false
                  lightweightMode = true
                  rendererCrashedOut = false
                  resetSignal++
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
              ) {
                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Muat Ulang Mode Mobile (Hemat Memori)")
              }
              OutlinedButton(
                onClick = {
                  useDesktopMode = true
                  lightweightMode = true
                  rendererCrashedOut = false
                  resetSignal++
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
              ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Muat Ulang Desktop (Mode Ringan)")
              }
              OutlinedButton(
                onClick = {
                  runCatching {
                    context.startActivity(
                      Intent(Intent.ACTION_VIEW, Uri.parse(initialUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                      }
                    )
                  }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
              ) {
                Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Buka di Browser Chrome")
              }
            }
          }
        } else {
          WhatsAppWebView(
            modifier = Modifier.fillMaxSize(),
            url = initialUrl,
            profileKey = profileKey,
            useDesktopUserAgent = useDesktopMode,
            lightweightMode = lightweightMode,
            resetSignal = resetSignal,
            onWebViewCreated = { webViewRef = it },
            onLoadingChanged = { isLoading = it },
            onProgressChanged = { loadProgress = it },
            onError = { errorBanner = it },
            onWebStatusChanged = { webStatus = it },
            onRenderProcessGone = {
              rendererCrashedOut = true
              errorBanner = null
            },
            onCapturedWebMessage = onCapturedWebMessage,
          )
        }
      }
    }
  }
}
