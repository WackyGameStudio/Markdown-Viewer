package com.example.markdownviewer.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri
import com.example.markdownviewer.data.RandomAccessDocument
import com.example.markdownviewer.data.SmbDocumentUri
import com.example.markdownviewer.data.ViewerSettings
import com.example.markdownviewer.model.DocumentKind
import com.example.markdownviewer.model.DocumentNode
import com.example.markdownviewer.model.FolderReference
import com.example.markdownviewer.model.GestureTrigger
import com.example.markdownviewer.model.TocItem
import com.example.markdownviewer.model.ViewerAction
import com.example.markdownviewer.ui.components.DocumentPane
import com.example.markdownviewer.ui.components.DocumentTreePanel
import com.example.markdownviewer.ui.components.SmbConnectionsDialog
import com.example.markdownviewer.ui.components.TocPanel
import com.example.markdownviewer.ui.components.ViewerSettingsDialog
import com.example.markdownviewer.web.HeadingRequest
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class EdgePanel {
  Explorer,
  Details,
  Controls,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownViewerApp(viewModel: MarkdownViewerViewModel = viewModel()) {
  val uiState by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  var showRecent by rememberSaveable { mutableStateOf(false) }
  var showBookmarks by rememberSaveable { mutableStateOf(false) }
  var showTocSheet by rememberSaveable { mutableStateOf(false) }
  var showWidthDialog by rememberSaveable { mutableStateOf(false) }
  var showSettings by rememberSaveable { mutableStateOf(false) }
  var showSmbConnections by rememberSaveable { mutableStateOf(false) }
  var edgePanel by remember { mutableStateOf<EdgePanel?>(null) }
  var headingRequest by remember { mutableStateOf<HeadingRequest?>(null) }
  val hapticFeedback = LocalHapticFeedback.current
  val coroutineScope = rememberCoroutineScope()
  val openFolder =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) viewModel.openFolder(uri)
    }

  LaunchedEffect(uiState.errorMessage) {
    val message = uiState.errorMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    viewModel.clearError()
  }

  LaunchedEffect(uiState.activeDocument?.uri) {
    headingRequest = null
    showTocSheet = false
    edgePanel = null
  }

  ImmersiveSystemBarsEffect(uiState.isFocusMode && uiState.settings.immersiveSystemBars)
  val edgeGesturesEnabled =
    uiState.activeDocument != null &&
      (!uiState.settings.edgeGesturesFocusOnly || uiState.isFocusMode)
  SystemGestureExclusionEffect(
    excludeLeft = edgeGesturesEnabled && uiState.settings.gestureBindings.isBound(GestureTrigger.EdgeLeftIn),
    excludeRight = edgeGesturesEnabled && uiState.settings.gestureBindings.isBound(GestureTrigger.EdgeRightIn),
  )

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val layout = classifyWindow(maxWidth.value.roundToInt(), maxHeight.value.roundToInt())
    val compactDocumentOpen = layout == WindowLayout.Compact && uiState.activeDocument != null
    val openDocumentExternally: (DocumentNode) -> Unit = { document ->
      if (SmbDocumentUri.isSmb(document.uri)) {
        Toast.makeText(context, "SMB 문서를 외부 앱용으로 준비하는 중입니다.", Toast.LENGTH_SHORT).show()
      }
      coroutineScope.launch {
        runCatching { viewModel.externalDocumentUri(document) }
          .onSuccess { uri ->
            openExternalDocument(
              context = context,
              document = document,
              uri = uri,
              mimeType = viewModel.resourceMimeType(document.uri),
            )
          }
          .onFailure { failure ->
            Toast.makeText(
                context,
                failure.message ?: "외부 앱으로 전달할 문서를 준비하지 못했습니다.",
                Toast.LENGTH_LONG,
              )
              .show()
          }
      }
    }
    val handleGestureTrigger: (GestureTrigger) -> Unit = { trigger ->
      val action = uiState.settings.gestureBindings.actionFor(trigger)
      if (action != null && uiState.settings.hapticFeedback) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
      }
      when (action) {
        ViewerAction.ToggleFocus -> {
          edgePanel = null
          viewModel.toggleFocusMode()
        }
        ViewerAction.ToggleExplorer ->
          edgePanel = if (edgePanel == EdgePanel.Explorer) null else EdgePanel.Explorer
        ViewerAction.ToggleDetails ->
          edgePanel = if (edgePanel == EdgePanel.Details) null else EdgePanel.Details
        ViewerAction.ToggleControls ->
          edgePanel = if (edgePanel == EdgePanel.Controls) null else EdgePanel.Controls
        ViewerAction.OpenExternalApp -> uiState.activeDocument?.let(openDocumentExternally)
        null -> Unit
      }
    }

    BackHandler(enabled = edgePanel != null || uiState.isFocusMode || compactDocumentOpen) {
      when {
        edgePanel != null -> edgePanel = null
        uiState.isFocusMode -> viewModel.exitFocusMode()
        else -> viewModel.closeCompactDocument()
      }
    }

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        if (!uiState.isFocusMode) TopAppBar(
          title = {
            Text(
              text =
                if (compactDocumentOpen) {
                  uiState.activeDocument?.name.orEmpty()
                } else {
                  "Markdown Viewer"
                },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          navigationIcon = {
            if (compactDocumentOpen) {
              IconButton(onClick = viewModel::closeCompactDocument) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "탐색기로 돌아가기")
              }
            }
          },
          actions = {
            if (uiState.activeDocument?.kind == DocumentKind.Markdown && layout != WindowLayout.Large) {
              IconButton(onClick = { showTocSheet = true }) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "목차")
              }
            }
            if (uiState.activeDocument?.kind == DocumentKind.Markdown) {
              IconButton(onClick = { showWidthDialog = true }) {
                Icon(Icons.Outlined.Tune, contentDescription = "본문 너비")
              }
            }
            if (uiState.activeDocument != null) {
              IconButton(onClick = viewModel::toggleFocusMode) {
                Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "집중 모드")
              }
            }
            if (
              uiState.activeDocument != null &&
                uiState.settings.showExternalOpenButton &&
                layout != WindowLayout.Compact
            ) {
              IconButton(onClick = { uiState.activeDocument?.let(openDocumentExternally) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "외부 앱으로 열기")
              }
            }
            SavedFoldersMenu(
              expanded = showRecent,
              folders = uiState.recentFolders,
              icon = Icons.Outlined.History,
              contentDescription = "최근 폴더",
              emptyMessage = "최근 폴더가 없습니다.",
              onExpandedChange = {
                showRecent = it
                if (it) showBookmarks = false
              },
              onSelect = {
                showRecent = false
                viewModel.openSavedFolder(it)
              },
            )
            SavedFoldersMenu(
              expanded = showBookmarks,
              folders = uiState.bookmarks,
              icon = Icons.Outlined.Bookmarks,
              contentDescription = "즐겨찾기",
              emptyMessage = "즐겨찾기가 없습니다.",
              onExpandedChange = {
                showBookmarks = it
                if (it) showRecent = false
              },
              onSelect = {
                showBookmarks = false
                viewModel.openSavedFolder(it)
              },
            )
            IconButton(
              onClick = {
                val initialUri =
                  uiState.rootFolder?.uri?.toUri()?.takeIf { it.scheme == "content" }
                openFolder.launch(initialUri)
              }
            ) {
              Icon(Icons.Outlined.FolderOpen, contentDescription = "문서 폴더 열기")
            }
            IconButton(onClick = { showSmbConnections = true }) {
              Icon(Icons.Outlined.Storage, contentDescription = "SMB 네트워크 폴더")
            }
            IconButton(onClick = { showSettings = true }) {
              Icon(Icons.Outlined.Settings, contentDescription = "설정")
            }
          },
        )
      },
    ) { innerPadding ->
      AdaptiveWorkspace(
        layout = layout,
        uiState = uiState,
        headingRequest = headingRequest,
        onSelectHeading = { item ->
          headingRequest = HeadingRequest(item.headingIndex, System.nanoTime())
          showTocSheet = false
        },
        onSelectDocument = viewModel::selectDocument,
        onRefresh = viewModel::refreshFolder,
        onToggleBookmark = viewModel::toggleBookmark,
        onGoBack = viewModel::goBack,
        onGoForward = viewModel::goForward,
        onShowToc = { showTocSheet = true },
        resolveResource = viewModel::resolveResourceUri,
        isAllowedUri = viewModel::isAllowedUri,
        openResource = viewModel::openResource,
        openRandomAccessDocument = viewModel::openRandomAccessDocument,
        resourceMimeType = viewModel::resourceMimeType,
        onViewStateChanged = viewModel::saveDocumentViewState,
        onNavigate = viewModel::navigateToReference,
        onOpenExternal = { url ->
          runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
            .onFailure {
              Toast.makeText(context, "링크를 열 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        },
        onOpenDocumentExternal = openDocumentExternally,
        edgePanel = edgePanel,
        onDismissEdgePanel = { edgePanel = null },
        onToggleFocus = {
          edgePanel = null
          viewModel.toggleFocusMode()
        },
        onShowSettings = {
          edgePanel = null
          showSettings = true
        },
        onShowExplorer = { edgePanel = EdgePanel.Explorer },
        onShowDetails = { edgePanel = EdgePanel.Details },
        onGestureTrigger = handleGestureTrigger,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      )
    }

    if (showTocSheet && layout != WindowLayout.Large) {
      ModalBottomSheet(onDismissRequest = { showTocSheet = false }) {
        TocPanel(
          items = uiState.toc,
          onSelect = { item ->
            headingRequest = HeadingRequest(item.headingIndex, System.nanoTime())
            showTocSheet = false
          },
          modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 620.dp),
        )
      }
    }

    if (showWidthDialog) {
      ContentWidthDialog(
        currentWidth = uiState.contentWidth,
        onDismiss = { showWidthDialog = false },
        onWidthChanged = viewModel::setContentWidth,
      )
    }

    if (showSettings) {
      ViewerSettingsDialog(
        settings = uiState.settings,
        onSettingChanged = viewModel::updateSetting,
        onGestureBindingChanged = viewModel::bindGesture,
        onResetGestureBindings = viewModel::resetGestureBindings,
        onDismiss = { showSettings = false },
      )
    }

    if (showSmbConnections) {
      SmbConnectionsDialog(
        connections = uiState.smbConnections,
        onConnect = viewModel::openSmbConnection,
        onDelete = viewModel::deleteSmbConnection,
        onDismiss = { showSmbConnections = false },
      )
    }
  }
}

@Composable
private fun AdaptiveWorkspace(
  layout: WindowLayout,
  uiState: ViewerUiState,
  headingRequest: HeadingRequest?,
  onSelectHeading: (TocItem) -> Unit,
  onSelectDocument: (DocumentNode) -> Unit,
  onRefresh: () -> Unit,
  onToggleBookmark: () -> Unit,
  onGoBack: () -> Unit,
  onGoForward: () -> Unit,
  onShowToc: () -> Unit,
  resolveResource: (String, String) -> String?,
  isAllowedUri: (String) -> Boolean,
  openResource: (String) -> java.io.InputStream?,
  openRandomAccessDocument: (String) -> RandomAccessDocument?,
  resourceMimeType: (String) -> String,
  onViewStateChanged: (String, String) -> Unit,
  onNavigate: (String, String) -> Unit,
  onOpenExternal: (String) -> Unit,
  onOpenDocumentExternal: (DocumentNode) -> Unit,
  edgePanel: EdgePanel?,
  onDismissEdgePanel: () -> Unit,
  onToggleFocus: () -> Unit,
  onShowSettings: () -> Unit,
  onShowExplorer: () -> Unit,
  onShowDetails: () -> Unit,
  onGestureTrigger: (GestureTrigger) -> Unit,
  modifier: Modifier = Modifier,
) {
  val bookmarked = uiState.rootFolder?.uri?.let { uri -> uiState.bookmarks.any { it.uri == uri } } == true

  val explorer: @Composable (Modifier) -> Unit = { panelModifier ->
    DocumentTreePanel(
      root = uiState.root,
      activeDocument = uiState.activeDocument,
      expandedFolders = uiState.expandedFolders,
      isLoading = uiState.isTreeLoading,
      isBookmarked = bookmarked,
      onSelect = onSelectDocument,
      onRefresh = onRefresh,
      onToggleBookmark = onToggleBookmark,
      modifier = panelModifier,
    )
  }
  val document: @Composable (Modifier, Boolean) -> Unit = { panelModifier, showTocAction ->
    DocumentPane(
      document = uiState.activeDocument,
      markdown = uiState.markdown,
      isLoading = uiState.isDocumentLoading,
      documentErrorMessage = uiState.documentErrorMessage,
      contentWidth = uiState.contentWidth,
      canGoBack = uiState.canGoBack,
      canGoForward = uiState.canGoForward,
      showTocAction = showTocAction,
      headingRequest = headingRequest,
      initialViewState = uiState.viewState,
      settings = uiState.settings,
      focusMode = uiState.isFocusMode,
      viewerControlsVisible = !uiState.isFocusMode || edgePanel == EdgePanel.Controls,
      showDocumentHeader = !uiState.isFocusMode,
      showDocumentExternalButton = layout == WindowLayout.Compact,
      onGoBack = onGoBack,
      onGoForward = onGoForward,
      onShowToc = onShowToc,
      resolveResource = resolveResource,
      isAllowedUri = isAllowedUri,
      openResource = openResource,
      openRandomAccessDocument = openRandomAccessDocument,
      resourceMimeType = resourceMimeType,
      onViewStateChanged = onViewStateChanged,
      onGestureTrigger = onGestureTrigger,
      onNavigate = onNavigate,
      onOpenExternal = onOpenExternal,
      onOpenDocumentExternal = onOpenDocumentExternal,
      modifier = panelModifier,
    )
  }

  Box(modifier) {
    if (uiState.isFocusMode) {
      document(Modifier.fillMaxSize(), false)
    } else {
      when (layout) {
        WindowLayout.Compact -> {
          if (uiState.activeDocument == null) {
            explorer(Modifier.fillMaxSize())
          } else {
            document(Modifier.fillMaxSize(), false)
          }
        }
        WindowLayout.Medium, WindowLayout.Expanded -> {
          val explorerWidth = if (layout == WindowLayout.Medium) 260.dp else 300.dp
          Row(Modifier.fillMaxSize()) {
            explorer(Modifier.width(explorerWidth).fillMaxHeight())
            VerticalDivider()
            document(Modifier.weight(1f).fillMaxHeight(), true)
          }
        }
        WindowLayout.Large -> {
          Row(Modifier.fillMaxSize()) {
            explorer(Modifier.width(320.dp).fillMaxHeight())
            VerticalDivider()
            document(Modifier.weight(1f).fillMaxHeight(), false)
            if (uiState.activeDocument?.kind == DocumentKind.Markdown) {
              VerticalDivider()
              TocPanel(
                items = uiState.toc,
                onSelect = onSelectHeading,
                modifier = Modifier.width(280.dp).fillMaxHeight(),
              )
            }
          }
        }
      }
    }

    if (edgePanel != null) {
      Box(
        Modifier.fillMaxSize()
          .background(Color.Black.copy(alpha = 0.34f))
          .clickable(onClick = onDismissEdgePanel),
      )
    }

    AnimatedVisibility(
      visible = edgePanel == EdgePanel.Explorer,
      modifier = Modifier.align(Alignment.CenterStart),
      enter = slideInHorizontally { -it } + fadeIn(),
      exit = slideOutHorizontally { -it } + fadeOut(),
    ) {
      Surface(
        modifier =
          Modifier.widthIn(max = 380.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .dismissOnHorizontalSwipe(
              dismissOnPositive = false,
              onDismiss = onDismissEdgePanel,
            )
            .shadow(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
      ) {
        DocumentTreePanel(
          root = uiState.root,
          activeDocument = uiState.activeDocument,
          expandedFolders = uiState.expandedFolders,
          isLoading = uiState.isTreeLoading,
          isBookmarked = bookmarked,
          onSelect = { node ->
            onSelectDocument(node)
            if (!node.isFolder) onDismissEdgePanel()
          },
          onRefresh = onRefresh,
          onToggleBookmark = onToggleBookmark,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }

    AnimatedVisibility(
      visible = edgePanel == EdgePanel.Details,
      modifier = Modifier.align(Alignment.CenterEnd),
      enter = slideInHorizontally { it } + fadeIn(),
      exit = slideOutHorizontally { it } + fadeOut(),
    ) {
      Surface(
        modifier =
          Modifier.widthIn(max = 380.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .dismissOnHorizontalSwipe(
              dismissOnPositive = true,
              onDismiss = onDismissEdgePanel,
            )
            .shadow(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
      ) {
        if (uiState.activeDocument?.kind == DocumentKind.Markdown) {
          TocPanel(
            items = uiState.toc,
            onSelect = { item ->
              onSelectHeading(item)
              onDismissEdgePanel()
            },
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          DocumentInfoPanel(uiState.activeDocument, Modifier.fillMaxSize())
        }
      }
    }

    AnimatedVisibility(
      visible = edgePanel == EdgePanel.Controls,
      modifier = Modifier.align(Alignment.TopCenter),
      enter = slideInVertically { -it } + fadeIn(),
      exit = slideOutVertically { -it } + fadeOut(),
    ) {
      FocusControlsPanel(
        document = uiState.activeDocument,
        canGoBack = uiState.canGoBack,
        canGoForward = uiState.canGoForward,
        focusMode = uiState.isFocusMode,
        onGoBack = onGoBack,
        onGoForward = onGoForward,
        onShowExplorer = onShowExplorer,
        onShowDetails = onShowDetails,
        onToggleFocus = onToggleFocus,
        onShowSettings = onShowSettings,
        showExternalOpen = uiState.settings.showExternalOpenButton,
        onOpenExternal = { uiState.activeDocument?.let(onOpenDocumentExternal) },
        onDismiss = onDismissEdgePanel,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    }
  }
}

@Composable
private fun FocusControlsPanel(
  document: DocumentNode?,
  canGoBack: Boolean,
  canGoForward: Boolean,
  focusMode: Boolean,
  onGoBack: () -> Unit,
  onGoForward: () -> Unit,
  onShowExplorer: () -> Unit,
  onShowDetails: () -> Unit,
  onToggleFocus: () -> Unit,
  onShowSettings: () -> Unit,
  showExternalOpen: Boolean,
  onOpenExternal: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier =
      modifier.fillMaxWidth()
        .statusBarsPadding()
        .dismissOnVerticalSwipe(dismissOnPositive = false, onDismiss = onDismiss)
        .shadow(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 4.dp,
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          document?.name ?: "문서 도구",
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.labelLarge,
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Outlined.Close, contentDescription = "문서 도구 닫기")
        }
      }
      HorizontalDivider()
      Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onGoBack, enabled = canGoBack) {
          Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "이전 문서")
        }
        IconButton(onClick = onGoForward, enabled = canGoForward) {
          Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "다음 문서")
        }
        IconButton(onClick = onShowExplorer) {
          Icon(Icons.Outlined.FolderOpen, contentDescription = "탐색기")
        }
        IconButton(onClick = onShowDetails, enabled = document != null) {
          Icon(
            if (document?.kind == DocumentKind.Markdown) {
              Icons.AutoMirrored.Outlined.MenuBook
            } else {
              Icons.Outlined.Info
            },
            contentDescription = if (document?.kind == DocumentKind.Markdown) "목차" else "문서 정보",
          )
        }
        IconButton(onClick = onToggleFocus, enabled = document != null) {
          Icon(
            if (focusMode) Icons.Outlined.FullscreenExit else Icons.Outlined.CenterFocusStrong,
            contentDescription = if (focusMode) "집중 모드 종료" else "집중 모드",
          )
        }
        if (showExternalOpen && document != null) {
          IconButton(onClick = onOpenExternal) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "외부 앱으로 열기")
          }
        }
        IconButton(onClick = onShowSettings) {
          Icon(Icons.Outlined.Settings, contentDescription = "설정")
        }
      }
    }
  }
}

@Composable
private fun DocumentInfoPanel(document: DocumentNode?, modifier: Modifier = Modifier) {
  Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
    Row(
      modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Outlined.Info,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.width(10.dp))
      Text("문서 정보", style = MaterialTheme.typography.labelLarge)
    }
    HorizontalDivider()
    if (document == null) {
      Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("선택한 문서가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        InfoRow("이름", document.name)
        InfoRow("종류", document.kind.displayName)
        InfoRow("크기", formatFileSize(document.sizeBytes))
        InfoRow("경로", document.relativePath.ifBlank { document.name })
      }
    }
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    Text(value, style = MaterialTheme.typography.bodyMedium)
  }
}

private val DocumentKind.displayName: String
  get() =
    when (this) {
      DocumentKind.Folder -> "폴더"
      DocumentKind.Markdown -> "Markdown"
      DocumentKind.Image -> "이미지"
      DocumentKind.Pdf -> "PDF"
      DocumentKind.Video -> "영상"
      DocumentKind.Word -> "Word 문서"
      DocumentKind.Presentation -> "PowerPoint 프레젠테이션"
      DocumentKind.Html -> "HTML"
      DocumentKind.Resource -> "HTML 리소스"
    }

private fun openExternalDocument(
  context: Context,
  document: DocumentNode,
  uri: android.net.Uri,
  mimeType: String,
) {
  val target =
    Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, mimeType)
      clipData = ClipData.newRawUri(document.name, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  runCatching {
      context.startActivity(Intent.createChooser(target, "외부 앱으로 열기"))
    }
    .onFailure {
      Toast.makeText(context, "이 문서를 열 수 있는 외부 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun formatFileSize(bytes: Long): String {
  if (bytes < 1_024) return "$bytes B"
  val units = listOf("KB", "MB", "GB")
  var value = bytes.toDouble()
  var unitIndex = -1
  while (value >= 1_024 && unitIndex < units.lastIndex) {
    value /= 1_024
    unitIndex += 1
  }
  return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
}

private fun Modifier.dismissOnHorizontalSwipe(
  dismissOnPositive: Boolean,
  onDismiss: () -> Unit,
): Modifier =
  pointerInput(dismissOnPositive, onDismiss) {
    var distance = 0f
    val threshold = 72.dp.toPx()
    detectHorizontalDragGestures(
      onDragStart = { distance = 0f },
      onDragCancel = { distance = 0f },
      onDragEnd = {
        if ((dismissOnPositive && distance >= threshold) || (!dismissOnPositive && distance <= -threshold)) {
          onDismiss()
        }
        distance = 0f
      },
    ) { change, amount ->
      distance += amount
      if (distance.absoluteValue > 8.dp.toPx()) change.consume()
    }
  }

private fun Modifier.dismissOnVerticalSwipe(
  dismissOnPositive: Boolean,
  onDismiss: () -> Unit,
): Modifier =
  pointerInput(dismissOnPositive, onDismiss) {
    var distance = 0f
    val threshold = 64.dp.toPx()
    detectVerticalDragGestures(
      onDragStart = { distance = 0f },
      onDragCancel = { distance = 0f },
      onDragEnd = {
        if ((dismissOnPositive && distance >= threshold) || (!dismissOnPositive && distance <= -threshold)) {
          onDismiss()
        }
        distance = 0f
      },
    ) { change, amount ->
      distance += amount
      if (distance.absoluteValue > 8.dp.toPx()) change.consume()
    }
  }

@Composable
private fun ImmersiveSystemBarsEffect(enabled: Boolean) {
  val context = LocalContext.current
  val view = LocalView.current
  DisposableEffect(context, view, enabled) {
    val activity = context.findActivity()
    val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
    if (enabled) {
      controller?.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller?.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      controller?.show(WindowInsetsCompat.Type.systemBars())
    }
    onDispose {
      if (enabled) controller?.show(WindowInsetsCompat.Type.systemBars())
    }
  }
}

@Composable
private fun SystemGestureExclusionEffect(excludeLeft: Boolean, excludeRight: Boolean) {
  val view = LocalView.current
  DisposableEffect(view, excludeLeft, excludeRight) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@DisposableEffect onDispose {}

    fun updateExclusions() {
      if (view.width <= 0 || view.height <= 0) return
      val density = view.resources.displayMetrics.density
      val edgeWidth = (24f * density).roundToInt().coerceAtLeast(1)
      val segmentHeight = (200f * density).roundToInt().coerceAtMost(view.height)
      val top = (view.height - segmentHeight) / 2
      val bottom = top + segmentHeight
      view.systemGestureExclusionRects =
        buildList {
          if (excludeLeft) add(Rect(0, top, edgeWidth, bottom))
          if (excludeRight) add(Rect(view.width - edgeWidth, top, view.width, bottom))
        }
    }

    val layoutListener =
      View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateExclusions() }
    view.addOnLayoutChangeListener(layoutListener)
    updateExclusions()
    onDispose {
      view.removeOnLayoutChangeListener(layoutListener)
      view.systemGestureExclusionRects = emptyList()
    }
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

@Composable
private fun SavedFoldersMenu(
  expanded: Boolean,
  folders: List<FolderReference>,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  emptyMessage: String,
  onExpandedChange: (Boolean) -> Unit,
  onSelect: (FolderReference) -> Unit,
) {
  Box {
    IconButton(onClick = { onExpandedChange(!expanded) }) {
      Icon(icon, contentDescription = contentDescription)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
      if (folders.isEmpty()) {
        DropdownMenuItem(text = { Text(emptyMessage) }, onClick = { onExpandedChange(false) }, enabled = false)
      } else {
        folders.forEach { folder ->
          DropdownMenuItem(
            text = {
              androidx.compose.foundation.layout.Column {
                Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  folder.uri,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
            onClick = { onSelect(folder) },
          )
        }
      }
    }
  }
}

@Composable
private fun ContentWidthDialog(
  currentWidth: Int,
  onDismiss: () -> Unit,
  onWidthChanged: (Int) -> Unit,
) {
  var width by remember(currentWidth) { mutableFloatStateOf(currentWidth.toFloat()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Markdown 본문 너비") },
    text = {
      androidx.compose.foundation.layout.Column {
        Text("${width.roundToInt()} dp", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
          value = width,
          onValueChange = { width = it },
          valueRange = 560f..1600f,
          onValueChangeFinished = { onWidthChanged(width.roundToInt()) },
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onWidthChanged(width.roundToInt())
          onDismiss()
        }
      ) {
        Text("완료")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
  )
}
