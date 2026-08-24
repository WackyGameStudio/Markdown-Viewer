package com.example.markdownviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.markdownviewer.R
import com.example.markdownviewer.model.DocumentKind
import com.example.markdownviewer.model.DocumentNode
import com.example.markdownviewer.model.GestureTrigger
import com.example.markdownviewer.data.ViewerSettings
import com.example.markdownviewer.data.RandomAccessDocument
import com.example.markdownviewer.web.DocumentWebView
import com.example.markdownviewer.web.HeadingRequest
import com.example.markdownviewer.web.HtmlDocumentWebView

@Composable
fun DocumentPane(
  document: DocumentNode?,
  markdown: String,
  isLoading: Boolean,
  documentErrorMessage: String?,
  contentWidth: Int,
  canGoBack: Boolean,
  canGoForward: Boolean,
  showTocAction: Boolean,
  headingRequest: HeadingRequest?,
  initialViewState: String?,
  settings: ViewerSettings,
  focusMode: Boolean,
  viewerControlsVisible: Boolean,
  showDocumentHeader: Boolean,
  showDocumentExternalButton: Boolean,
  onGoBack: () -> Unit,
  onGoForward: () -> Unit,
  onShowToc: () -> Unit,
  resolveResource: (String, String) -> String?,
  isAllowedUri: (String) -> Boolean,
  openResource: (String) -> java.io.InputStream?,
  openRandomAccessDocument: (String) -> RandomAccessDocument?,
  resourceMimeType: (String) -> String,
  onViewStateChanged: (String, String) -> Unit,
  onGestureTrigger: (GestureTrigger) -> Unit,
  onNavigate: (String, String) -> Unit,
  onOpenExternal: (String) -> Unit,
  onOpenDocumentExternal: (DocumentNode) -> Unit,
  modifier: Modifier = Modifier,
) {
  val incomingSnapshot =
    remember(document, markdown, documentErrorMessage, initialViewState) {
      DocumentRenderSnapshot(
        document = document,
        markdown = markdown,
        errorMessage = documentErrorMessage,
        initialViewState = initialViewState,
      )
    }
  var lastSettledSnapshot by remember { mutableStateOf<DocumentRenderSnapshot?>(null) }
  SideEffect {
    if (!isLoading) lastSettledSnapshot = incomingSnapshot
  }
  val keepsPreviousDocument = isLoading && lastSettledSnapshot?.document != null
  val displayedSnapshot =
    if (keepsPreviousDocument) requireNotNull(lastSettledSnapshot) else incomingSnapshot
  val displayedDocument = displayedSnapshot.document

  Column(modifier.fillMaxSize()) {
    if (displayedDocument != null && showDocumentHeader) {
      Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onGoBack, enabled = canGoBack) {
          Icon(
            Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.nav_previous_document),
          )
        }
        IconButton(onClick = onGoForward, enabled = canGoForward) {
          Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = stringResource(R.string.nav_next_document),
          )
        }
        Icon(
          imageVector = displayedDocument.kind.icon,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = displayedDocument.name,
          modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.labelLarge,
        )
        if (!keepsPreviousDocument && showTocAction && displayedDocument.kind == DocumentKind.Markdown) {
          IconButton(onClick = onShowToc) {
            Icon(
              Icons.AutoMirrored.Outlined.MenuBook,
              contentDescription = stringResource(R.string.nav_open_toc),
            )
          }
        }
        if (settings.showExternalOpenButton && showDocumentExternalButton) {
          IconButton(onClick = { onOpenDocumentExternal(displayedDocument) }) {
            Icon(
              Icons.AutoMirrored.Outlined.OpenInNew,
              contentDescription = stringResource(R.string.nav_open_external),
            )
          }
        }
      }
      HorizontalDivider()
    }

    Box(
      Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
    ) {
      when {
        displayedDocument == null -> EmptyDocumentState()
        isLoading && !keepsPreviousDocument ->
          CircularProgressIndicator(Modifier.align(Alignment.Center))
        displayedSnapshot.errorMessage != null ->
          DocumentErrorState(displayedSnapshot.errorMessage)
        displayedDocument.kind == DocumentKind.Video ->
          key(displayedDocument.uri) {
            VideoPlayer(
              document = displayedDocument,
              initialViewState = displayedSnapshot.initialViewState,
              settings = settings,
              focusMode = focusMode,
              controlsVisible = viewerControlsVisible,
              onViewStateChanged = onViewStateChanged,
              onGestureTrigger = onGestureTrigger,
              openRandomAccessDocument = openRandomAccessDocument,
              modifier = Modifier.fillMaxSize(),
            )
          }
        displayedDocument.kind == DocumentKind.Html ->
          key(displayedDocument.uri) {
            HtmlDocumentWebView(
              document = displayedDocument,
              settings = settings,
              focusMode = focusMode,
              resolveResource = resolveResource,
              isAllowedUri = isAllowedUri,
              openResource = openResource,
              resourceMimeType = resourceMimeType,
              onGestureTrigger = onGestureTrigger,
              onNavigate = onNavigate,
              onOpenExternal = onOpenExternal,
              modifier = Modifier.fillMaxSize(),
            )
          }
        else ->
          DocumentWebView(
            document = displayedDocument,
            markdown = displayedSnapshot.markdown,
            contentWidth = contentWidth,
            headingRequest = if (keepsPreviousDocument) null else headingRequest,
            initialViewState = displayedSnapshot.initialViewState,
            settings = settings,
            focusMode = focusMode,
            viewerControlsVisible = viewerControlsVisible,
            resolveResource = resolveResource,
            isAllowedUri = isAllowedUri,
            openResource = openResource,
            resourceMimeType = resourceMimeType,
            onViewStateChanged = onViewStateChanged,
            onGestureTrigger = onGestureTrigger,
            onNavigate = onNavigate,
            onOpenExternal = onOpenExternal,
            modifier = Modifier.fillMaxSize(),
          )
      }
      if (keepsPreviousDocument) {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        )
      }
    }
  }
}

private data class DocumentRenderSnapshot(
  val document: DocumentNode?,
  val markdown: String,
  val errorMessage: String?,
  val initialViewState: String?,
)

@Composable
private fun DocumentErrorState(message: String) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      Icons.Outlined.ErrorOutline,
      contentDescription = null,
      modifier = Modifier.size(52.dp),
      tint = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.document_open_error_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
      message,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
private fun EmptyDocumentState() {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      Icons.Outlined.Description,
      contentDescription = null,
      modifier = Modifier.size(52.dp),
      tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.document_select_prompt), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Text(
      stringResource(R.string.document_supported_types),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private val DocumentKind.icon: androidx.compose.ui.graphics.vector.ImageVector
  get() =
    when (this) {
      DocumentKind.Folder, DocumentKind.Markdown -> Icons.Outlined.Description
      DocumentKind.Image -> Icons.Outlined.Image
      DocumentKind.Pdf -> Icons.Outlined.PictureAsPdf
      DocumentKind.Video -> Icons.Outlined.VideoFile
      DocumentKind.Word -> Icons.AutoMirrored.Outlined.TextSnippet
      DocumentKind.Presentation -> Icons.Outlined.Slideshow
      DocumentKind.Html -> Icons.Outlined.Language
      DocumentKind.Resource -> Icons.Outlined.Description
    }
