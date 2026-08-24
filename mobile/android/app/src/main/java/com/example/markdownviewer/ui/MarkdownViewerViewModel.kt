package com.example.markdownviewer.ui

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.markdownviewer.data.FolderPreferences
import com.example.markdownviewer.data.RandomAccessDocument
import com.example.markdownviewer.data.SafDocumentRepository
import com.example.markdownviewer.data.SmbConnectionConfig
import com.example.markdownviewer.data.SmbConnectionPreferences
import com.example.markdownviewer.data.SmbDocumentRepository
import com.example.markdownviewer.data.SmbDocumentUri
import com.example.markdownviewer.data.ViewerSettingKey
import com.example.markdownviewer.data.ViewerSettings
import com.example.markdownviewer.data.ViewerSettingsRepository
import com.example.markdownviewer.model.DocumentKind
import com.example.markdownviewer.model.DocumentNode
import com.example.markdownviewer.model.FolderReference
import com.example.markdownviewer.model.GestureTrigger
import com.example.markdownviewer.model.TocItem
import com.example.markdownviewer.model.ViewerAction
import com.example.markdownviewer.model.flatten
import java.util.Locale
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewerUiState(
  val root: DocumentNode? = null,
  val rootFolder: FolderReference? = null,
  val activeDocument: DocumentNode? = null,
  val markdown: String = "",
  val toc: List<TocItem> = emptyList(),
  val expandedFolders: Set<String> = emptySet(),
  val recentFolders: List<FolderReference> = emptyList(),
  val bookmarks: List<FolderReference> = emptyList(),
  val smbConnections: List<SmbConnectionConfig> = emptyList(),
  val contentWidth: Int = 900,
  val settings: ViewerSettings = ViewerSettings(),
  val isFocusMode: Boolean = false,
  val viewState: String? = null,
  val isTreeLoading: Boolean = false,
  val isDocumentLoading: Boolean = false,
  val documentErrorMessage: String? = null,
  val canGoBack: Boolean = false,
  val canGoForward: Boolean = false,
  val errorMessage: String? = null,
)

class MarkdownViewerViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = SafDocumentRepository(application)
  private val smbRepository = SmbDocumentRepository(application)
  private val preferences = FolderPreferences(application)
  private val smbPreferences = SmbConnectionPreferences(application)
  private val initialSmbConnections = smbPreferences.connections()
  private val settingsRepository = ViewerSettingsRepository(application)
  private val mutableState =
    MutableStateFlow(
      ViewerUiState(
        recentFolders = preferences.recentFolders(),
        bookmarks = preferences.bookmarks(),
        smbConnections = initialSmbConnections,
        contentWidth = preferences.contentWidth(),
      )
    )

  val state: StateFlow<ViewerUiState> = mutableState.asStateFlow()

  @Volatile private var nodesByPath: Map<String, DocumentNode> = emptyMap()
  @Volatile private var nodesByUri: Map<String, DocumentNode> = emptyMap()
  @Volatile private var smbConnectionsById: Map<String, SmbConnectionConfig> =
    initialSmbConnections.associateBy(SmbConnectionConfig::id)
  private var history: List<DocumentNode> = emptyList()
  private var historyIndex = -1
  private var documentLoadId = 0L
  private val documentViewStates = mutableMapOf<String, String>()
  private val validatedOfficeUris = ConcurrentHashMap.newKeySet<String>()

  init {
    viewModelScope.launch {
      settingsRepository.settings.collect { settings ->
        mutableState.update { it.copy(settings = settings) }
      }
    }
    val latest = mutableState.value.recentFolders.firstOrNull()
    if (latest != null) {
      val smbConfig = smbConfigForUri(latest.uri)
      when {
        smbConfig != null -> openSmbConnection(smbConfig, save = false)
        repository.hasPersistedPermission(latest.uri.toUri()) ->
          openFolder(latest.uri.toUri(), persistPermission = false)
      }
    }
  }

  fun openFolder(uri: Uri, persistPermission: Boolean = true) {
    if (persistPermission) {
      runCatching { repository.persistReadPermission(uri) }
        .onFailure { failure ->
          showError(failure.message ?: "폴더 읽기 권한을 저장하지 못했습니다.")
          return
        }
    }

    viewModelScope.launch {
      documentLoadId += 1
      validatedOfficeUris.clear()
      mutableState.update {
        it.copy(
          root = null,
          rootFolder = null,
          activeDocument = null,
          markdown = "",
          toc = emptyList(),
          viewState = null,
          isFocusMode = false,
          expandedFolders = emptySet(),
          isTreeLoading = true,
          isDocumentLoading = false,
          documentErrorMessage = null,
          errorMessage = null,
        )
      }
      runCatching { repository.loadTree(uri) }
        .onSuccess { root ->
          val folder = FolderReference(uri = uri.toString(), name = root.name)
          val allNodes = root.flatten().toList()
          val recentFolders = preferences.addRecent(folder)
          val bookmarks = mutableState.value.bookmarks
          repository.releasePermissionsExcept(
            (recentFolders.map { it.uri } + bookmarks.map { it.uri } + folder.uri).toSet()
          )
          nodesByPath = allNodes.associateBy { pathKey(it.relativePath) }
          nodesByUri = allNodes.associateBy(DocumentNode::uri)
          documentViewStates.keys.retainAll(nodesByUri.keys)
          history = emptyList()
          historyIndex = -1
          mutableState.update {
            it.copy(
              root = root,
              rootFolder = folder,
              activeDocument = null,
              markdown = "",
              toc = emptyList(),
              viewState = null,
              expandedFolders = setOf(root.uri),
              recentFolders = recentFolders,
              isTreeLoading = false,
              canGoBack = false,
              canGoForward = false,
              errorMessage = null,
            )
          }
        }
        .onFailure { failure ->
          nodesByPath = emptyMap()
          nodesByUri = emptyMap()
          mutableState.update {
            it.copy(
              isTreeLoading = false,
              errorMessage = failure.message ?: "폴더를 불러오지 못했습니다.",
            )
          }
        }
    }
  }

  fun refreshFolder() {
    val folder = mutableState.value.rootFolder ?: return
    val smbConfig = smbConfigForUri(folder.uri)
    if (smbConfig != null) {
      openSmbConnection(smbConfig, save = false)
    } else {
      openFolder(folder.uri.toUri(), persistPermission = false)
    }
  }

  fun openSmbConnection(config: SmbConnectionConfig, save: Boolean = true) {
    val normalized =
      runCatching { config.validated() }
        .getOrElse { failure ->
          showError(failure.message ?: "SMB 연결 설정이 올바르지 않습니다.")
          return
        }

    viewModelScope.launch {
      mutableState.update {
        it.copy(
          isTreeLoading = true,
          errorMessage = null,
        )
      }
      val root =
        runCatching { smbRepository.loadTree(normalized) }
          .getOrElse { failure ->
            mutableState.update {
              it.copy(
                isTreeLoading = false,
                errorMessage = failure.message ?: "SMB 폴더를 불러오지 못했습니다.",
              )
            }
            return@launch
          }
      val connections =
        runCatching {
            if (save) smbPreferences.save(normalized) else smbConnectionsById.values.toList()
          }
          .getOrElse { failure ->
            mutableState.update {
              it.copy(
                isTreeLoading = false,
                errorMessage = failure.message ?: "SMB 연결 설정을 저장하지 못했습니다.",
              )
            }
            return@launch
          }
      if (save) smbConnectionsById = connections.associateBy(SmbConnectionConfig::id)

      documentLoadId += 1
      validatedOfficeUris.clear()
      val folder = FolderReference(uri = root.uri, name = root.name)
      val allNodes = root.flatten().toList()
      val recentFolders = preferences.addRecent(folder)
      val bookmarks = mutableState.value.bookmarks
      repository.releasePermissionsExcept(
        (recentFolders.map { it.uri } + bookmarks.map { it.uri }).toSet()
      )
      nodesByPath = allNodes.associateBy { pathKey(it.relativePath) }
      nodesByUri = allNodes.associateBy(DocumentNode::uri)
      documentViewStates.keys.retainAll(nodesByUri.keys)
      history = emptyList()
      historyIndex = -1
      mutableState.update {
        it.copy(
          root = root,
          rootFolder = folder,
          activeDocument = null,
          markdown = "",
          toc = emptyList(),
          viewState = null,
          isFocusMode = false,
          expandedFolders = setOf(root.uri),
          recentFolders = recentFolders,
          smbConnections = connections,
          isTreeLoading = false,
          isDocumentLoading = false,
          documentErrorMessage = null,
          canGoBack = false,
          canGoForward = false,
          errorMessage = null,
        )
      }
    }
  }

  fun deleteSmbConnection(connectionId: String) {
    val connections =
      runCatching { smbPreferences.delete(connectionId) }
        .getOrElse {
          showError("SMB 연결 설정을 삭제하지 못했습니다.")
          return
        }
    smbConnectionsById = connections.associateBy(SmbConnectionConfig::id)
    val affectedFolders =
      (preferences.recentFolders() + preferences.bookmarks())
        .filter { SmbDocumentUri.parse(it.uri)?.connectionId == connectionId }
    affectedFolders.forEach { preferences.removeFolder(it.uri) }
    val currentWasDeleted =
      SmbDocumentUri.parse(mutableState.value.rootFolder?.uri.orEmpty())?.connectionId == connectionId
    mutableState.update {
      it.copy(
        root = if (currentWasDeleted) null else it.root,
        rootFolder = if (currentWasDeleted) null else it.rootFolder,
        activeDocument = if (currentWasDeleted) null else it.activeDocument,
        markdown = if (currentWasDeleted) "" else it.markdown,
        toc = if (currentWasDeleted) emptyList() else it.toc,
        smbConnections = connections,
        recentFolders = preferences.recentFolders(),
        bookmarks = preferences.bookmarks(),
      )
    }
    if (currentWasDeleted) {
      nodesByPath = emptyMap()
      nodesByUri = emptyMap()
      history = emptyList()
      historyIndex = -1
    }
  }

  fun toggleFolder(node: DocumentNode) {
    if (!node.isFolder) return
    mutableState.update { current ->
      val expanded = current.expandedFolders.toMutableSet()
      if (!expanded.add(node.uri)) expanded.remove(node.uri)
      current.copy(expandedFolders = expanded)
    }
  }

  fun selectDocument(node: DocumentNode) {
    if (node.isFolder) {
      toggleFolder(node)
      return
    }
    if (!node.isVisibleInTree) {
      showError("이 파일은 HTML 페이지에서 사용하는 리소스입니다.")
      return
    }
    val retained = history.take(historyIndex + 1)
    if (retained.lastOrNull()?.uri != node.uri) {
      history = retained + node
      historyIndex = history.lastIndex
    }
    showDocument(node)
  }

  fun navigateToReference(activePath: String, reference: String) {
    val node = resolveNode(activePath, reference)
    if (node == null || node.isFolder || !node.isVisibleInTree) {
      showError("연결된 문서를 찾을 수 없습니다: $reference")
      return
    }
    selectDocument(node)
  }

  fun resolveResourceUri(activePath: String, reference: String): String? =
    resolveNode(activePath, reference)?.takeIf { !it.isFolder }?.uri

  fun isAllowedUri(uri: String): Boolean = nodesByUri.containsKey(uri)

  fun documentForUri(uri: String): DocumentNode? = nodesByUri[uri]

  fun openResource(uri: String): InputStream? {
    val node = nodesByUri[uri] ?: return null
    if (
      (node.kind == DocumentKind.Word || node.kind == DocumentKind.Presentation) &&
        !node.isLegacyOffice &&
        uri !in validatedOfficeUris
    ) {
      return null
    }
    val limit =
      when (node.kind) {
        DocumentKind.Image -> MAX_IMAGE_BYTES
        DocumentKind.Pdf -> MAX_PDF_BYTES
        DocumentKind.Word, DocumentKind.Presentation -> MAX_OFFICE_BYTES
        DocumentKind.Html -> MAX_HTML_BYTES
        DocumentKind.Resource -> MAX_HTML_RESOURCE_BYTES
        else -> return null
      }
    if (node.sizeBytes > limit) return null
    val smbConfig = smbConfigForUri(uri)
    return if (smbConfig != null) {
      smbRepository.openStream(
        config = smbConfig,
        uri = uri,
        maxBytes = limit,
        requirePdfHeader = node.kind == DocumentKind.Pdf,
      )
    } else {
      repository.openStream(
        uri = uri,
        maxBytes = limit,
        requirePdfHeader = node.kind == DocumentKind.Pdf,
      )
    }
  }

  fun resourceMimeType(uri: String): String {
    val node = documentForUri(uri)
    return if (SmbDocumentUri.isSmb(uri)) {
      smbRepository.mimeType(node?.name.orEmpty())
    } else {
      repository.mimeType(uri, node?.name.orEmpty())
    }
  }

  fun openRandomAccessDocument(uri: String): RandomAccessDocument? {
    val config = smbConfigForUri(uri) ?: return null
    return smbRepository.openRandomAccessDocument(config, uri)
  }

  suspend fun externalDocumentUri(document: DocumentNode): Uri {
    val config = smbConfigForUri(document.uri)
    return if (config != null) {
      smbRepository.materializeForExternalApp(config, document)
    } else {
      document.uri.toUri()
    }
  }

  fun goBack() {
    if (historyIndex <= 0) return
    historyIndex -= 1
    showDocument(history[historyIndex])
  }

  fun goForward() {
    if (historyIndex >= history.lastIndex) return
    historyIndex += 1
    showDocument(history[historyIndex])
  }

  fun closeCompactDocument() {
    documentLoadId += 1
    mutableState.update {
      it.copy(
        activeDocument = null,
        markdown = "",
        toc = emptyList(),
        viewState = null,
        isFocusMode = false,
        isDocumentLoading = false,
        documentErrorMessage = null,
      )
    }
  }

  fun saveDocumentViewState(activePath: String, serializedState: String) {
    if (serializedState.length !in 2..MAX_VIEW_STATE_CHARS) return
    val node = nodesByPath[pathKey(activePath)]?.takeIf { !it.isFolder } ?: return
    val expectedKind =
      when (node.kind) {
        DocumentKind.Markdown -> "markdown"
        DocumentKind.Image -> "image"
        DocumentKind.Pdf -> "pdf"
        DocumentKind.Video -> "video"
        DocumentKind.Word -> "word"
        DocumentKind.Presentation -> "presentation"
        DocumentKind.Html -> "html"
        DocumentKind.Folder, DocumentKind.Resource -> return
      }
    if (node.kind == DocumentKind.Video && !mutableState.value.settings.videoRememberPosition) return
    val normalized =
      runCatching {
          val state = JSONObject(serializedState)
          if (state.optString("kind") != expectedKind) return
          state.toString()
        }
        .getOrNull() ?: return
    documentViewStates[node.uri] = normalized
  }

  fun toggleBookmark() {
    val folder = mutableState.value.rootFolder ?: return
    val bookmarks = preferences.toggleBookmark(folder)
    val recentFolders = mutableState.value.recentFolders
    repository.releasePermissionsExcept(
      (recentFolders.map { it.uri } + bookmarks.map { it.uri } + folder.uri).toSet()
    )
    mutableState.update { it.copy(bookmarks = bookmarks) }
  }

  fun openSavedFolder(folder: FolderReference) {
    val smbLocation = SmbDocumentUri.parse(folder.uri)
    if (smbLocation != null) {
      val config = smbConnectionsById[smbLocation.connectionId]
      if (config == null) {
        preferences.removeFolder(folder.uri)
        mutableState.update {
          it.copy(
            recentFolders = preferences.recentFolders(),
            bookmarks = preferences.bookmarks(),
            errorMessage = "저장된 SMB 연결 설정을 찾을 수 없습니다. 연결을 다시 등록해 주세요.",
          )
        }
      } else {
        openSmbConnection(config, save = false)
      }
      return
    }
    val uri = folder.uri.toUri()
    if (!repository.hasPersistedPermission(uri)) {
      preferences.removeFolder(folder.uri)
      mutableState.update {
        it.copy(
          recentFolders = preferences.recentFolders(),
          bookmarks = preferences.bookmarks(),
          errorMessage = "이 폴더의 읽기 권한이 만료되었습니다. 폴더를 다시 선택해 주세요.",
        )
      }
      return
    }
    openFolder(uri, persistPermission = false)
  }

  fun setContentWidth(width: Int) {
    val clamped = width.coerceIn(560, 1600)
    preferences.setContentWidth(clamped)
    mutableState.update { it.copy(contentWidth = clamped) }
  }

  fun updateSetting(key: ViewerSettingKey, enabled: Boolean) {
    viewModelScope.launch {
      runCatching { settingsRepository.set(key, enabled) }
        .onFailure { showError("설정을 저장하지 못했습니다.") }
    }
  }

  fun bindGesture(action: ViewerAction, trigger: GestureTrigger) {
    viewModelScope.launch {
      runCatching { settingsRepository.bindGesture(action, trigger) }
        .onFailure { showError("제스처 설정을 저장하지 못했습니다.") }
    }
  }

  fun resetGestureBindings() {
    viewModelScope.launch {
      runCatching { settingsRepository.resetGestureBindings() }
        .onFailure { showError("기본 제스처 설정을 복원하지 못했습니다.") }
    }
  }

  fun toggleFocusMode() {
    if (mutableState.value.activeDocument == null) return
    mutableState.update { it.copy(isFocusMode = !it.isFocusMode) }
  }

  fun exitFocusMode() {
    mutableState.update { it.copy(isFocusMode = false) }
  }

  fun clearError() {
    mutableState.update { it.copy(errorMessage = null) }
  }

  private fun showDocument(node: DocumentNode) {
    val loadId = ++documentLoadId
    val requiresOfficeValidation =
      (node.kind == DocumentKind.Word || node.kind == DocumentKind.Presentation) &&
        !node.isLegacyOffice &&
        node.uri !in validatedOfficeUris
    mutableState.update {
      it.copy(
        activeDocument = node,
        markdown = "",
        toc = emptyList(),
        viewState =
          if (node.kind == DocumentKind.Video && !it.settings.videoRememberPosition) {
            null
          } else {
            documentViewStates[node.uri]
          },
        isDocumentLoading = node.kind == DocumentKind.Markdown || requiresOfficeValidation,
        documentErrorMessage = null,
        canGoBack = historyIndex > 0,
        canGoForward = historyIndex in 0 until history.lastIndex,
        errorMessage = null,
      )
    }
    if (requiresOfficeValidation) {
      viewModelScope.launch {
        runCatching {
            val smbConfig = smbConfigForUri(node.uri)
            if (smbConfig != null) {
              smbRepository.validateOfficePackage(smbConfig, node.uri, node.kind)
            } else {
              repository.validateOfficePackage(node.uri, node.kind)
            }
          }
          .onSuccess {
            validatedOfficeUris += node.uri
            if (loadId != documentLoadId || mutableState.value.activeDocument?.uri != node.uri) return@onSuccess
            mutableState.update { it.copy(isDocumentLoading = false, documentErrorMessage = null) }
          }
          .onFailure { failure ->
            if (loadId != documentLoadId || mutableState.value.activeDocument?.uri != node.uri) return@onFailure
            mutableState.update {
              it.copy(
                isDocumentLoading = false,
                documentErrorMessage =
                  failure.message ?: "Office 문서가 손상되었거나 허용 범위를 초과했습니다.",
              )
            }
          }
      }
      return
    }
    if (node.kind != DocumentKind.Markdown) return

    viewModelScope.launch {
      runCatching {
          val smbConfig = smbConfigForUri(node.uri)
          if (smbConfig != null) smbRepository.readMarkdown(smbConfig, node.uri)
          else repository.readMarkdown(node.uri)
        }
        .onSuccess { markdown ->
          if (loadId != documentLoadId || mutableState.value.activeDocument?.uri != node.uri) return@onSuccess
          mutableState.update {
            it.copy(
              markdown = markdown,
              toc = extractToc(markdown),
              isDocumentLoading = false,
              documentErrorMessage = null,
            )
          }
        }
        .onFailure { failure ->
          if (loadId != documentLoadId || mutableState.value.activeDocument?.uri != node.uri) return@onFailure
          mutableState.update {
            it.copy(
              isDocumentLoading = false,
              documentErrorMessage = failure.message ?: "Markdown 문서를 읽지 못했습니다.",
            )
          }
        }
    }
  }

  private fun resolveNode(activePath: String, reference: String): DocumentNode? {
    val normalized = SafDocumentRepository.normalizeReference(activePath, reference) ?: return null
    return nodesByPath[pathKey(normalized)]
  }

  private fun showError(message: String) {
    mutableState.update { it.copy(errorMessage = message) }
  }

  private fun smbConfigForUri(uri: String): SmbConnectionConfig? {
    val location = SmbDocumentUri.parse(uri) ?: return null
    return smbConnectionsById[location.connectionId]
  }

  private fun pathKey(path: String): String = path.replace('\\', '/').lowercase(Locale.ROOT)

  companion object {
    fun extractToc(markdown: String): List<TocItem> {
      val result = mutableListOf<TocItem>()
      var inFence = false
      var fenceMarker = ""
      markdown.lineSequence().forEach { line ->
        val trimmed = line.trimStart()
        val marker = when {
          trimmed.startsWith("```") -> "```"
          trimmed.startsWith("~~~") -> "~~~"
          else -> ""
        }
        if (marker.isNotEmpty()) {
          if (!inFence) {
            inFence = true
            fenceMarker = marker
          } else if (marker == fenceMarker) {
            inFence = false
            fenceMarker = ""
          }
          return@forEach
        }
        if (inFence) return@forEach

        val match = HEADING_REGEX.matchEntire(line) ?: return@forEach
        val level = match.groupValues[1].length
        val text = match.groupValues[2].trim().replace(TRAILING_HASHES, "").trim()
        if (text.isNotEmpty()) {
          result += TocItem(text = text, level = level, headingIndex = result.size)
        }
      }
      return result
    }

    private val HEADING_REGEX = Regex("^\\s*(#{1,6})\\s+(.+?)\\s*$")
    private val TRAILING_HASHES = Regex("\\s+#+$")
    private const val MAX_VIEW_STATE_CHARS = 1024
    private const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
    private const val MAX_PDF_BYTES = 50L * 1024 * 1024
    private const val MAX_OFFICE_BYTES = 60L * 1024 * 1024
    private const val MAX_HTML_BYTES = 10L * 1024 * 1024
    private const val MAX_HTML_RESOURCE_BYTES = 24L * 1024 * 1024
  }
}
