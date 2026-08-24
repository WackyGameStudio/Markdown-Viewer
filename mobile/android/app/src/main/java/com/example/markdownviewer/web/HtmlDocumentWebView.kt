package com.example.markdownviewer.web

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.example.markdownviewer.data.ViewerSettings
import com.example.markdownviewer.model.DocumentNode
import com.example.markdownviewer.model.GestureTrigger
import com.example.markdownviewer.ui.nativeDocumentGestures
import java.io.ByteArrayInputStream
import java.io.InputStream

@SuppressLint("SetJavaScriptEnabled", "MissingOnRenderProcessGone")
@Composable
fun HtmlDocumentWebView(
  document: DocumentNode,
  settings: ViewerSettings,
  focusMode: Boolean,
  resolveResource: (activePath: String, reference: String) -> String?,
  isAllowedUri: (String) -> Boolean,
  openResource: (String) -> InputStream?,
  resourceMimeType: (String) -> String,
  onGestureTrigger: (GestureTrigger) -> Unit,
  onNavigate: (activePath: String, reference: String) -> Unit,
  onOpenExternal: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val webViewBackground = MaterialTheme.colorScheme.background.toArgb()
  val currentResolve by rememberUpdatedState(resolveResource)
  val currentIsAllowed by rememberUpdatedState(isAllowedUri)
  val currentOpenResource by rememberUpdatedState(openResource)
  val currentMimeType by rememberUpdatedState(resourceMimeType)
  val currentNavigate by rememberUpdatedState(onNavigate)
  val currentOpenExternal by rememberUpdatedState(onOpenExternal)
  var rendererGeneration by remember { mutableIntStateOf(0) }
  var managedWebView by remember(document.uri, rendererGeneration) { mutableStateOf<WebView?>(null) }

  DisposableEffect(document.uri, rendererGeneration) {
    onDispose {
      managedWebView?.stopLoading()
      managedWebView?.destroy()
      managedWebView = null
    }
  }

  key(document.uri, rendererGeneration) {
    AndroidView(
      modifier = modifier.nativeDocumentGestures(settings, focusMode, onGestureTrigger),
      factory = { context ->
        val assetLoader =
          WebViewAssetLoader.Builder()
            .addPathHandler(
              HTML_PATH_PREFIX,
              HtmlPathHandler(
                document = document,
                resolveResource = { activePath, reference -> currentResolve(activePath, reference) },
                isAllowed = { currentIsAllowed(it) },
                openStream = { currentOpenResource(it) },
                mimeType = { currentMimeType(it) },
              ),
            )
            .build()

        WebView(context).apply {
          managedWebView = this
          setBackgroundColor(webViewBackground)
          alpha = 0f
          val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
          if (debuggable) WebView.setWebContentsDebuggingEnabled(true)
          this.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            setSupportMultipleWindows(false)
            setSupportZoom(settings.pinchZoomHtml)
            builtInZoomControls = settings.pinchZoomHtml
            displayZoomControls = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
          }
          webViewClient =
            object : WebViewClientCompat() {
              override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
              ): WebResourceResponse? {
                val url = request?.url ?: return blockedResponse()
                if (url.scheme != "https" || url.host != HTML_HOST) return blockedResponse()
                return assetLoader.shouldInterceptRequest(url) ?: blockedResponse()
              }

              override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
              ): Boolean {
                val url = request.url
                if (url.scheme == "https" && url.host == HTML_HOST) {
                  val initial = HtmlDocumentUrl.from(document)
                  if (url.toString().substringBefore('#') == initial.substringBefore('#')) return false
                  val reference = HtmlDocumentUrl.referenceFrom(url.toString()) ?: return true
                  currentNavigate(document.relativePath, reference)
                  return true
                }
                if (url.scheme in setOf("http", "https", "mailto")) {
                  currentOpenExternal(url.toString())
                }
                return true
              }

              override fun onPageCommitVisible(view: WebView, url: String) {
                revealHtmlWebView(view)
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                revealHtmlWebView(view)
              }

              override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
              ): Boolean {
                Log.e(HTML_LOG_TAG, "HTML renderer exited (crashed=${detail.didCrash()})")
                view.destroy()
                managedWebView = null
                rendererGeneration += 1
                return true
              }
            }
          loadUrl(HtmlDocumentUrl.from(document))
        }
      },
      update = { webView ->
        webView.setBackgroundColor(webViewBackground)
        webView.settings.apply {
          setSupportZoom(settings.pinchZoomHtml)
          builtInZoomControls = settings.pinchZoomHtml
          displayZoomControls = false
        }
      },
    )
  }
}

private fun revealHtmlWebView(view: WebView?) {
  if (view == null || view.alpha >= 0.99f) return
  view.animate().cancel()
  view.animate().alpha(1f).setDuration(HTML_WEB_VIEW_REVEAL_MILLIS).start()
}

private class HtmlPathHandler(
  private val document: DocumentNode,
  private val resolveResource: (String, String) -> String?,
  private val isAllowed: (String) -> Boolean,
  private val openStream: (String) -> InputStream?,
  private val mimeType: (String) -> String,
) : WebViewAssetLoader.PathHandler {
  override fun handle(path: String): WebResourceResponse? {
    val documentUri = DocumentUrl.decodeToken(path) ?: return null
    if (documentUri != document.uri || !isAllowed(documentUri)) return null
    val encodedReference = path.substringAfter('/', missingDelimiterValue = "")
    val reference = android.net.Uri.decode(encodedReference)
    val targetUri =
      if (reference.isBlank() || reference == document.name) {
        documentUri
      } else {
        resolveResource(document.relativePath, reference) ?: return null
      }
    if (!isAllowed(targetUri)) return null
    val stream = runCatching { openStream(targetUri) }.getOrNull() ?: return null
    val mime = runCatching { mimeType(targetUri) }.getOrDefault("application/octet-stream")
    val encoding = if (mime.startsWith("text/") || mime.endsWith("+xml") || mime == "application/xml") "utf-8" else null
    val headers =
      if (mime == "text/html") {
        mapOf(
          "Content-Security-Policy" to HTML_CONTENT_SECURITY_POLICY,
          "Cache-Control" to "no-store",
          "X-Content-Type-Options" to "nosniff",
        )
      } else {
        mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff")
      }
    return WebResourceResponse(mime, encoding, 200, "OK", headers, stream)
  }
}

private object HtmlDocumentUrl {
  fun from(document: DocumentNode): String =
    "${DocumentUrl.ORIGIN}$HTML_PATH_PREFIX${DocumentUrl.tokenFor(document.uri)}/${android.net.Uri.encode(document.name)}"

  fun referenceFrom(url: String): String? {
    val uri = runCatching { url.toUri() }.getOrNull() ?: return null
    val prefix = HTML_PATH_PREFIX.trimEnd('/')
    val path = uri.encodedPath ?: return null
    if (!path.startsWith("$prefix/")) return null
    val tail = path.removePrefix("$prefix/")
    if (!tail.contains('/')) return null
    return android.net.Uri.decode(tail.substringAfter('/'))
  }
}

private fun blockedResponse(): WebResourceResponse =
  WebResourceResponse(
    "text/plain",
    "utf-8",
    403,
    "Blocked",
    mapOf("Cache-Control" to "no-store"),
    ByteArrayInputStream("Blocked".toByteArray()),
  )

private const val HTML_PATH_PREFIX = "/html/"
private const val HTML_HOST = "appassets.androidplatform.net"
private const val HTML_LOG_TAG = "MarkdownViewerHtml"
private const val HTML_WEB_VIEW_REVEAL_MILLIS = 140L
private const val HTML_CONTENT_SECURITY_POLICY =
  "default-src 'none'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; " +
    "font-src 'self' data:; media-src 'self'; script-src 'none'; connect-src 'none'; " +
    "frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
