package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProfileStore
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

const val WHATSAPP_WEB_URL = "https://web.whatsapp.com/"

const val MOBILE_WHATSAPP_USER_AGENT =
  "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

private fun resolveDesktopUserAgent(context: Context): Pair<String, String> {
  val defaultUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("")
  val match = Regex("""Chrome/(\d+)\.(\d+\.\d+\.\d+)""").find(defaultUa)
  val majorInt = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 131
  val fullVer =
    if (majorInt >= 115 && match != null) "${match.groupValues[1]}.${match.groupValues[2]}"
    else "131.0.0.0"
  val majorStr = if (majorInt >= 115) majorInt.toString() else "131"
  val ua =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$fullVer Safari/537.36"
  return ua to majorStr
}

private const val DESKTOP_VIEWPORT_SHIM_JS =
  """
  (function() {
    try {
      Object.defineProperty(navigator, 'platform', { get: function() { return 'Win32'; } });
      Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 0; } });
    } catch (e) {}
    function applyViewport() {
      try {
        var meta = document.querySelector('meta[name="viewport"]');
        if (!meta) {
          meta = document.createElement('meta');
          meta.name = 'viewport';
          if (document.head) document.head.appendChild(meta);
        }
        if (meta && meta.getAttribute('content') !== 'width=980') {
          meta.setAttribute('content', 'width=980');
        }
      } catch (e) {}
    }
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', applyViewport, { once: true });
    } else {
      applyViewport();
    }
  })();
  """

private const val LIGHTWEIGHT_POLL_JS =
  """
  (function() {
    if (window.__haloAntriPollInstalled) return;
    window.__haloAntriPollInstalled = true;
    var seenTexts = new Set();
    var lastStatus = "";

    function reportStatus(status) {
      if (status !== lastStatus) {
        lastStatus = status;
        if (window.HaloAntriBridge && window.HaloAntriBridge.onWebStatusChanged) {
          window.HaloAntriBridge.onWebStatusChanged(status);
        }
      }
    }

    function pollDom() {
      try {
        var chatPane = document.querySelector('#pane-side, [data-testid="chat-list"]');
        if (chatPane) {
          reportStatus("CONNECTED");
          var headerEl = document.querySelector('#main header span[dir="auto"]');
          var senderName = (headerEl && headerEl.textContent) ? headerEl.textContent.trim() : "WhatsApp Web";
          if (!senderName) senderName = "WhatsApp Web";
          var nodes = document.querySelectorAll('[data-testid="msg-container"] span.selectable-text');
          var limit = Math.min(nodes.length, 25);
          for (var i = Math.max(0, nodes.length - limit); i < nodes.length; i++) {
            var txt = (nodes[i].textContent || "").trim();
            if (txt.length >= 8 && txt.length <= 1200 && !seenTexts.has(txt)) {
              if (/(?:\+62|62|0)[\s\-().]*8(?:[\s\-().]*\d){6,}|\+\d(?:[\s\-().]*\d){7,}/.test(txt)) {
                seenTexts.add(txt);
                if (seenTexts.size > 200) seenTexts.clear();
                if (window.HaloAntriBridge) {
                  window.HaloAntriBridge.onIncomingWebMessage(senderName, txt);
                }
              }
            }
          }
          return;
        }
        var qrEl = document.querySelector('canvas[aria-label], [data-ref] canvas, [data-testid="qrcode"] canvas');
        if (qrEl && qrEl.width > 0 && qrEl.height > 0) {
          reportStatus("QR_READY");
          if (!window.__haloQrScrolled) {
            window.__haloQrScrolled = true;
            qrEl.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' });
          }
        }
      } catch (e) {}
    }

    setInterval(pollDom, 2500);
    setTimeout(pollDom, 800);
  })();
  """

class WhatsAppWebBridge(
  private val onMessageCaptured: (String, String) -> Unit,
  private val onStatusChanged: (String) -> Unit,
) {
  @JavascriptInterface
  fun onIncomingWebMessage(sender: String, body: String) {
    onMessageCaptured(sender, body)
  }

  @JavascriptInterface
  fun onWebStatusChanged(status: String) {
    onStatusChanged(status)
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WhatsAppWebView(
  modifier: Modifier = Modifier,
  url: String = WHATSAPP_WEB_URL,
  profileKey: String = "wa_account_default",
  useDesktopUserAgent: Boolean = false,
  lightweightMode: Boolean = false,
  resetSignal: Int = 0,
  onWebViewCreated: (WebView?) -> Unit = {},
  onLoadingChanged: (Boolean) -> Unit = {},
  onProgressChanged: (Int) -> Unit = {},
  onError: (String?) -> Unit = {},
  onWebStatusChanged: (String) -> Unit = {},
  onRenderProcessGone: (didCrash: Boolean) -> Unit = {},
  onCapturedWebMessage: (sender: String, body: String) -> Unit = { _, _ -> },
) {
  val useMultiProfile =
    !lightweightMode &&
      profileKey != "wa_account_default" &&
      WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

  key(profileKey, useDesktopUserAgent, lightweightMode, resetSignal) {
    AndroidView(
      modifier = modifier.testTag("wa_web_webview"),
      factory = { context ->
        val (desktopUa, chromeMajor) = resolveDesktopUserAgent(context)

        WebView(context).apply {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
          }
          if (lightweightMode) {
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
          }

          if (useMultiProfile) {
            runCatching {
              val profile = ProfileStore.getInstance().getOrCreateProfile(profileKey)
              WebViewCompat.setProfile(this, profileKey)
              profile.cookieManager.setAcceptCookie(true)
              profile.cookieManager.setAcceptThirdPartyCookies(this, true)
            }
          } else {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
          }

          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowContentAccess = true
            loadsImagesAutomatically = true
            blockNetworkImage = false
            javaScriptCanOpenWindowsAutomatically = !lightweightMode
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString =
              if (useDesktopUserAgent) desktopUa else MOBILE_WHATSAPP_USER_AGENT
            mediaPlaybackRequiresUserGesture = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(!lightweightMode)
            builtInZoomControls = !lightweightMode
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
          }

          if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            runCatching {
              val uaMetadata =
                if (useDesktopUserAgent) {
                  UserAgentMetadata.Builder()
                    .setBrandVersionList(
                      listOf(
                        UserAgentMetadata.BrandVersion.Builder()
                          .setBrand("Chromium")
                          .setMajorVersion(chromeMajor)
                          .setFullVersion("$chromeMajor.0.0.0")
                          .build(),
                        UserAgentMetadata.BrandVersion.Builder()
                          .setBrand("Google Chrome")
                          .setMajorVersion(chromeMajor)
                          .setFullVersion("$chromeMajor.0.0.0")
                          .build(),
                      )
                    )
                    .setMobile(false)
                    .setPlatform("Windows")
                    .setPlatformVersion("10.0.0")
                    .setArchitecture("x86")
                    .setBitness(64)
                    .build()
                } else {
                  UserAgentMetadata.Builder()
                    .setMobile(true)
                    .setPlatform("Android")
                    .setPlatformVersion("14.0.0")
                    .build()
                }
              WebSettingsCompat.setUserAgentMetadata(settings, uaMetadata)
            }
          }

          if (
            !lightweightMode &&
              useDesktopUserAgent &&
              WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
          ) {
            runCatching {
              WebViewCompat.addDocumentStartJavaScript(
                this,
                DESKTOP_VIEWPORT_SHIM_JS,
                setOf("https://web.whatsapp.com"),
              )
            }
          }

          if (!lightweightMode) {
            addJavascriptInterface(
              WhatsAppWebBridge(onCapturedWebMessage, onWebStatusChanged),
              "HaloAntriBridge",
            )
          }

          webViewClient =
            object : WebViewClient() {
              override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
              ): Boolean {
                val targetUrl = request?.url?.toString().orEmpty()
                if (useDesktopUserAgent && targetUrl.startsWith("https://www.whatsapp.com")) {
                  view?.loadUrl(url)
                  return true
                }
                return !(targetUrl.startsWith("https://") || targetUrl.startsWith("http://"))
              }

              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                onLoadingChanged(true)
                onError(null)
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                onLoadingChanged(false)
                if (!lightweightMode) {
                  if (useDesktopUserAgent) {
                    view?.evaluateJavascript(DESKTOP_VIEWPORT_SHIM_JS, null)
                  }
                  view?.evaluateJavascript(LIGHTWEIGHT_POLL_JS, null)
                }
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
              ) {
                if (request?.isForMainFrame == true) {
                  onLoadingChanged(false)
                  onError(
                    "Gagal memuat WhatsApp Web (${error?.description ?: "periksa koneksi internet"})."
                  )
                }
              }

              override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
              ): Boolean {
                val didCrash =
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    detail?.didCrash() == true
                  } else {
                    true
                  }
                Log.e("WhatsAppWebView", "Renderer process gone (didCrash=$didCrash)")
                view?.let { deadView ->
                  deadView.stopLoading()
                  (deadView.parent as? ViewGroup)?.removeView(deadView)
                  deadView.removeAllViews()
                  deadView.destroy()
                }
                onWebViewCreated(null)
                onLoadingChanged(false)
                onRenderProcessGone(didCrash)
                return true
              }
            }

          webChromeClient =
            object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChanged(newProgress)
              }

              override fun onPermissionRequest(request: PermissionRequest?) {
                val resources = request?.resources ?: return
                val safeResources =
                  resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }.toTypedArray()
                if (safeResources.isNotEmpty()) {
                  request.grant(safeResources)
                } else {
                  request.deny()
                }
              }
            }

          loadUrl(url)
          onWebViewCreated(this)
        }
      },
      onRelease = { wv ->
        wv.stopLoading()
        runCatching { wv.removeJavascriptInterface("HaloAntriBridge") }
        wv.loadUrl("about:blank")
        wv.clearHistory()
        wv.removeAllViews()
        wv.destroy()
        onWebViewCreated(null)
      },
    )
  }
}
