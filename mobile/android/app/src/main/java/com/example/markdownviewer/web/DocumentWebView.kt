package com.example.markdownviewer.web

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebChromeClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.core.view.doOnLayout
import androidx.core.net.toUri
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.example.markdownviewer.model.DocumentKind
import com.example.markdownviewer.model.DocumentNode
import com.example.markdownviewer.model.GestureTrigger
import com.example.markdownviewer.data.ViewerSettings
import org.json.JSONObject

data class HeadingRequest(val index: Int, val nonce: Long)

// WebKit 1.17 lint does not recognize the implemented onRenderProcessGone override on this
// Compose-owned anonymous client. Keep the suppression beside the recovery implementation.
@SuppressLint("SetJavaScriptEnabled", "MissingOnRenderProcessGone")
@Composable
fun DocumentWebView(
  document: DocumentNode,
  markdown: String,
  contentWidth: Int,
  headingRequest: HeadingRequest?,
  initialViewState: String?,
  settings: ViewerSettings,
  focusMode: Boolean,
  viewerControlsVisible: Boolean,
  resolveResource: (activePath: String, reference: String) -> String?,
  isAllowedUri: (String) -> Boolean,
  openResource: (String) -> java.io.InputStream?,
  resourceMimeType: (String) -> String,
  onViewStateChanged: (activePath: String, serializedState: String) -> Unit,
  onGestureTrigger: (GestureTrigger) -> Unit,
  onNavigate: (activePath: String, reference: String) -> Unit,
  onOpenExternal: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val darkTheme = isSystemInDarkTheme()
  val webViewBackground = MaterialTheme.colorScheme.background.toArgb()
  val currentResolve by rememberUpdatedState(resolveResource)
  val currentNavigate by rememberUpdatedState(onNavigate)
  val currentOpenExternal by rememberUpdatedState(onOpenExternal)
  val currentIsAllowed by rememberUpdatedState(isAllowedUri)
  val currentOpenResource by rememberUpdatedState(openResource)
  val currentMimeType by rememberUpdatedState(resourceMimeType)
  val currentViewStateChanged by rememberUpdatedState(onViewStateChanged)
  val currentGestureTrigger by rememberUpdatedState(onGestureTrigger)
  var pageReady by remember { mutableStateOf(false) }
  var lastPayload by remember { mutableStateOf<String?>(null) }
  var lastHeadingNonce by remember { mutableStateOf<Long?>(null) }
  var rendererGeneration by remember { mutableIntStateOf(0) }

  val payload =
    remember(
      document.uri,
      markdown,
      contentWidth,
      darkTheme,
      initialViewState,
      settings,
      focusMode,
      viewerControlsVisible,
    ) {
      val restoredState =
        initialViewState?.let { serialized -> runCatching { JSONObject(serialized) }.getOrNull() }
      val edgeGesturesEnabled = !settings.edgeGesturesFocusOnly || focusMode
      val pinchZoom =
        when (document.kind) {
          DocumentKind.Markdown -> settings.pinchZoomMarkdown
          DocumentKind.Image -> settings.pinchZoomImage
          DocumentKind.Pdf -> settings.pinchZoomPdf
          DocumentKind.Word,
          DocumentKind.Presentation -> settings.pinchZoomOffice
          DocumentKind.Video -> settings.pinchZoomVideo
          DocumentKind.Folder,
          DocumentKind.Html,
          DocumentKind.Resource -> false
        }
      JSONObject()
        .put("kind", document.kind.webName)
        .put("name", document.name)
        .put("activePath", document.relativePath)
        .put("content", markdown)
        .put(
          "resourceUrl",
          if (
            document.kind == DocumentKind.Image ||
              document.kind == DocumentKind.Pdf ||
              ((document.kind == DocumentKind.Word || document.kind == DocumentKind.Presentation) &&
                !document.isLegacyOffice)
          ) {
            DocumentUrl.fromUri(document.uri)
          } else {
            ""
          },
        )
        .put("contentWidth", contentWidth)
        .put("legacyOffice", document.isLegacyOffice)
        .put("dark", darkTheme)
        .put("focusMode", focusMode)
        .put("viewerControlsVisible", viewerControlsVisible)
        .put(
          "gestures",
          JSONObject()
            .put("threeFingerTap", settings.gestureBindings.isBound(GestureTrigger.ThreeFingerTap))
            .put("tripleTap", settings.gestureBindings.isBound(GestureTrigger.TripleTap))
            .put("pinchZoom", pinchZoom)
            .put("edgeLeft", settings.gestureBindings.isBound(GestureTrigger.EdgeLeftIn) && edgeGesturesEnabled)
            .put("edgeRight", settings.gestureBindings.isBound(GestureTrigger.EdgeRightIn) && edgeGesturesEnabled)
            .put("edgeTop", settings.gestureBindings.isBound(GestureTrigger.EdgeTopIn) && edgeGesturesEnabled),
        )
        .put("viewState", restoredState ?: JSONObject.NULL)
        .toString()
    }

  key(rendererGeneration) {
    AndroidView(
      modifier = modifier,
      factory = { context ->
      val assetLoader =
        WebViewAssetLoader.Builder()
          .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
          .addPathHandler(
            "/document/",
            DocumentPathHandler(
              isAllowed = { currentIsAllowed(it) },
              openStream = { currentOpenResource(it) },
              mimeType = { currentMimeType(it) },
            ),
          )
          .build()

      WebView(context).apply {
        setBackgroundColor(webViewBackground)
        alpha = 0f
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) WebView.setWebContentsDebuggingEnabled(true)
        this.settings.javaScriptEnabled = true
        this.settings.allowFileAccess = false
        this.settings.allowContentAccess = false
        this.settings.domStorageEnabled = false
        this.settings.setSupportMultipleWindows(false)
        this.settings.setSupportZoom(false)
        this.settings.builtInZoomControls = false
        this.settings.displayZoomControls = false
        this.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        addJavascriptInterface(
          ViewerBridge(
            onReady = { pageReady = true },
            resolveResource = { activePath, reference ->
              currentResolve(activePath, reference)?.let(DocumentUrl::fromUri).orEmpty()
            },
            navigate = { activePath, reference -> currentNavigate(activePath, reference) },
            openExternal = { currentOpenExternal(it) },
            saveViewState = { activePath, serializedState ->
              currentViewStateChanged(activePath, serializedState)
            },
            requestTrigger = { currentGestureTrigger(it) },
          ),
          BRIDGE_NAME,
        )
        if (debuggable) {
          webChromeClient =
            object : WebChromeClient() {
              override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                  LOG_TAG,
                  "${consoleMessage.messageLevel()}: ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
                )
                return true
              }
            }
        }
        webViewClient =
          object : WebViewClientCompat() {
            override fun onRenderProcessGone(
              view: WebView,
              detail: RenderProcessGoneDetail,
            ): Boolean {
              Log.e(
                LOG_TAG,
                "WebView renderer exited; recreating it (crashed=${detail.didCrash()})",
              )
              pageReady = false
              lastPayload = null
              lastHeadingNonce = null
              view.removeJavascriptInterface(BRIDGE_NAME)
              view.destroy()
              rendererGeneration += 1
              return true
            }

            override fun shouldInterceptRequest(
              view: WebView?,
              request: WebResourceRequest?,
            ) = request?.url?.let(assetLoader::shouldInterceptRequest)

            override fun shouldOverrideUrlLoading(
              view: WebView,
              request: WebResourceRequest,
            ): Boolean {
              val url = request.url.toString()
              if (url.startsWith(DocumentUrl.ORIGIN)) return false
              if (url.isNotBlank()) currentOpenExternal(url)
              return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
              if (url?.startsWith(DocumentUrl.ORIGIN) == true) {
                pageReady = true
                revealDocumentWebView(view)
              }
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
              if (url.startsWith(DocumentUrl.ORIGIN)) revealDocumentWebView(view)
            }
          }
        doOnLayout {
          if (url == null) loadUrl(DocumentUrl.VIEWER_URL)
        }
      }
      },
      update = { webView ->
        webView.setBackgroundColor(webViewBackground)
        if (pageReady && payload != lastPayload) {
          val quoted = JSONObject.quote(payload)
          webView.evaluateJavascript("window.MarkdownViewer.setDocument(JSON.parse($quoted));", null)
          lastPayload = payload
        }
        val request = headingRequest
        if (pageReady && request != null && request.nonce != lastHeadingNonce) {
          webView.evaluateJavascript("window.MarkdownViewer.scrollToHeading(${request.index});", null)
          lastHeadingNonce = request.nonce
        }
      },
    )
  }
}

private fun revealDocumentWebView(view: WebView?) {
  if (view == null || view.alpha >= 0.99f) return
  view.animate().cancel()
  view.animate().alpha(1f).setDuration(WEB_VIEW_REVEAL_MILLIS).start()
}

private class ViewerBridge(
  private val onReady: () -> Unit,
  private val resolveResource: (String, String) -> String,
  private val navigate: (String, String) -> Unit,
  private val openExternal: (String) -> Unit,
  private val saveViewState: (String, String) -> Unit,
  private val requestTrigger: (GestureTrigger) -> Unit,
) {
  private val mainHandler = Handler(Looper.getMainLooper())

  @JavascriptInterface
  fun ready() {
    mainHandler.post(onReady)
  }

  @JavascriptInterface
  fun resolveResource(activePath: String, reference: String): String =
    resolveResource.invoke(activePath, reference)

  @JavascriptInterface
  fun openDocument(activePath: String, reference: String) {
    mainHandler.post { navigate(activePath, reference) }
  }

  @JavascriptInterface
  fun openExternal(url: String) {
    val scheme = runCatching { url.toUri().scheme?.lowercase() }.getOrNull()
    if (scheme !in setOf("http", "https", "mailto")) return
    mainHandler.post { openExternal.invoke(url) }
  }

  @JavascriptInterface
  fun saveViewState(activePath: String, serializedState: String) {
    mainHandler.post { saveViewState.invoke(activePath, serializedState) }
  }

  @JavascriptInterface
  fun requestTrigger(trigger: String) {
    val parsed =
      when (trigger) {
        "triple-tap" -> GestureTrigger.TripleTap
        "three-finger-tap" -> GestureTrigger.ThreeFingerTap
        "edge-left-in" -> GestureTrigger.EdgeLeftIn
        "edge-right-in" -> GestureTrigger.EdgeRightIn
        "edge-top-in" -> GestureTrigger.EdgeTopIn
        else -> return
      }
    mainHandler.post { requestTrigger.invoke(parsed) }
  }
}

private val DocumentKind.webName: String
  get() =
    when (this) {
      DocumentKind.Folder -> "folder"
      DocumentKind.Markdown -> "markdown"
      DocumentKind.Image -> "image"
      DocumentKind.Pdf -> "pdf"
      DocumentKind.Video -> "video"
      DocumentKind.Word -> "word"
      DocumentKind.Presentation -> "presentation"
      DocumentKind.Html -> "html"
      DocumentKind.Resource -> "resource"
    }

private const val BRIDGE_NAME = "AndroidBridge"
private const val LOG_TAG = "MarkdownViewerWeb"
private const val WEB_VIEW_REVEAL_MILLIS = 140L
