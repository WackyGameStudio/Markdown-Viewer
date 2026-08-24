package com.example.markdownviewer.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.markdownviewer.R
import com.example.markdownviewer.model.DocumentKind
import com.example.markdownviewer.model.DocumentNode
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmbDocumentRepository(private val context: Context) {
  suspend fun loadTree(config: SmbConnectionConfig): DocumentNode =
    withContext(Dispatchers.IO) {
      val normalized = config.validated()
      connect(normalized).use { handle ->
        if (normalized.initialPath.isNotEmpty() && !handle.share.folderExists(normalized.initialPath)) {
          error(context.localizedString(R.string.error_smb_start_folder_missing, normalized.initialPath))
        }
        val counter = NodeCounter(context.localizedString(R.string.error_smb_tree_too_large))
        val children =
          buildChildren(
            handle = handle,
            config = normalized,
            directoryPath = normalized.initialPath,
            relativeDirectory = "",
            depth = 0,
            counter = counter,
          )
        DocumentNode(
          uri = SmbDocumentUri.build(normalized.id, normalized.initialPath),
          name = normalized.name,
          relativePath = "",
          kind = DocumentKind.Folder,
          children = children,
        )
      }
    }

  suspend fun readMarkdown(config: SmbConnectionConfig, uri: String): String =
    withContext(Dispatchers.IO) {
      openStream(config, uri, MAX_MARKDOWN_BYTES, requirePdfHeader = false)?.bufferedReader(Charsets.UTF_8)
        ?.use { reader ->
          val result = StringBuilder()
          val buffer = CharArray(DEFAULT_BUFFER_SIZE)
          var total = 0
          while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_MARKDOWN_CHARS) {
              error(context.localizedString(R.string.error_markdown_too_large))
            }
            result.append(buffer, 0, read)
          }
          result.toString()
        } ?: error(context.localizedString(R.string.error_smb_document_read))
    }

  suspend fun validateOfficePackage(
    config: SmbConnectionConfig,
    uri: String,
    kind: DocumentKind,
  ) {
    withContext(Dispatchers.IO) {
      openStream(config, uri, MAX_OFFICE_VALIDATION_BYTES, requirePdfHeader = false)?.use { stream ->
        OfficePackageValidator.validate(stream, kind, OfficeValidationStrings.forContext(context))
      } ?: error(context.localizedString(R.string.error_smb_office_read))
    }
  }

  fun openStream(
    config: SmbConnectionConfig,
    uri: String,
    maxBytes: Long,
    requirePdfHeader: Boolean,
  ): InputStream? {
    val location = locationFor(config, uri) ?: return null
    var handle: SmbShareHandle? = null
    var file: SmbFile? = null
    return try {
      handle = connect(config.validated())
      file = openReadOnlyFile(handle.share, location.path, sequential = true)
      if (file.length > maxBytes) {
        file.closeSilently()
        handle.close()
        return null
      }
      val raw = file.inputStream
      val closing =
        ClosingInputStream(raw) {
          file.closeSilently()
          handle.close()
        }
      val buffered = BufferedInputStream(closing)
      if (requirePdfHeader && !hasPdfHeader(buffered)) {
        buffered.close()
        null
      } else {
        SizeLimitedInputStream(
          buffered,
          maxBytes,
          context.localizedString(R.string.error_document_size_limit),
        )
      }
    } catch (_: Exception) {
      file?.closeSilently()
      runCatching { handle?.close() }
      null
    }
  }

  fun openRandomAccessDocument(
    config: SmbConnectionConfig,
    uri: String,
  ): RandomAccessDocument? {
    val location = locationFor(config, uri) ?: return null
    var handle: SmbShareHandle? = null
    return try {
      handle = connect(config.validated())
      val file = openReadOnlyFile(handle.share, location.path, sequential = false)
      SmbRandomAccessDocument(file = file, handle = handle)
    } catch (_: Exception) {
      runCatching { handle?.close() }
      null
    }
  }

  suspend fun materializeForExternalApp(
    config: SmbConnectionConfig,
    document: DocumentNode,
  ): Uri =
    withContext(Dispatchers.IO) {
      require(document.sizeBytes <= MAX_EXTERNAL_FILE_BYTES) {
        context.localizedString(R.string.error_external_file_too_large)
      }
      val location =
        locationFor(config, document.uri)
          ?: error(context.localizedString(R.string.error_smb_document_uri))
      val safeName = sanitizeFileName(document.name)
      val connectionDirectory = File(context.cacheDir, "external/${location.connectionId}")
      check(connectionDirectory.exists() || connectionDirectory.mkdirs()) {
        context.localizedString(R.string.error_temp_folder_create)
      }
      val target = File(connectionDirectory, "${document.uri.hashCode().toUInt().toString(16)}-$safeName")
      val temporary = File.createTempFile("copy-", ".tmp", connectionDirectory)
      try {
        openStream(
            config = config,
            uri = document.uri,
            maxBytes = MAX_EXTERNAL_FILE_BYTES,
            requirePdfHeader = document.kind == DocumentKind.Pdf,
          )
          ?.use { input -> temporary.outputStream().buffered().use(input::copyTo) }
          ?: error(context.localizedString(R.string.error_smb_download))
        if (target.exists() && !target.delete()) {
          error(context.localizedString(R.string.error_temp_document_replace))
        }
        if (!temporary.renameTo(target)) {
          temporary.copyTo(target, overwrite = true)
          temporary.delete()
        }
      } finally {
        if (temporary.exists()) temporary.delete()
      }
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }

  fun mimeType(fileName: String): String = SafDocumentRepository.mimeTypeFromName(fileName)

  private fun buildChildren(
    handle: SmbShareHandle,
    config: SmbConnectionConfig,
    directoryPath: String,
    relativeDirectory: String,
    depth: Int,
    counter: NodeCounter,
  ): List<DocumentNode> {
    if (depth > MAX_TREE_DEPTH) {
      error(context.localizedString(R.string.error_smb_tree_too_deep))
    }
    val entries = handle.share.list(directoryPath)
    return entries
      .asSequence()
      .filterNot { it.fileName == "." || it.fileName == ".." || it.fileName.startsWith('.') }
      .mapNotNull { entry ->
        counter.increment()
        val name = entry.fileName
        val actualPath = joinSmbPath(directoryPath, name)
        val relativePath =
          if (relativeDirectory.isEmpty()) name else "$relativeDirectory/$name"
        when {
          entry.hasAttribute(FileAttributes.FILE_ATTRIBUTE_DIRECTORY) -> {
            if (entry.hasAttribute(FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT)) return@mapNotNull null
            val children =
              buildChildren(
                handle = handle,
                config = config,
                directoryPath = actualPath,
                relativeDirectory = relativePath,
                depth = depth + 1,
                counter = counter,
              )
            if (children.isEmpty()) null
            else
              DocumentNode(
                uri = SmbDocumentUri.build(config.id, actualPath),
                name = name,
                relativePath = relativePath,
                kind = DocumentKind.Folder,
                children = children,
              )
          }
          else -> {
            val kind = SafDocumentRepository.documentKind(name) ?: return@mapNotNull null
            DocumentNode(
              uri = SmbDocumentUri.build(config.id, actualPath),
              name = name,
              relativePath = relativePath,
              kind = kind,
              sizeBytes = entry.endOfFile.coerceAtLeast(0),
            )
          }
        }
      }
      .sortedWith(
        compareBy<DocumentNode> { !it.isFolder }
          .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
      )
      .toList()
  }

  private fun connect(config: SmbConnectionConfig): SmbShareHandle {
    val smbConfig =
      SmbConfig.builder()
        .withSigningEnabled(true)
        .withSigningRequired(config.requireSigning)
        .withEncryptData(config.requireEncryption)
        .withTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .withSoTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    val client = SMBClient(smbConfig)
    var connection: Connection? = null
    var session: Session? = null
    try {
      connection = client.connect(config.host, config.port)
      val authentication =
        if (config.username.isBlank() && config.password.isBlank()) {
          AuthenticationContext.guest()
        } else {
          AuthenticationContext(config.username, config.password.toCharArray(), config.domain)
        }
      session = connection.authenticate(authentication)
      val share = session.connectShare(config.share) as? DiskShare
        ?: error(context.localizedString(R.string.error_smb_not_file_share))
      return SmbShareHandle(client, connection, session, share)
    } catch (failure: Exception) {
      runCatching { session?.close() }
      runCatching { connection?.close() }
      runCatching { client.close() }
      throw IOException(smbErrorMessage(failure), failure)
    }
  }

  private fun openReadOnlyFile(share: DiskShare, path: String, sequential: Boolean): SmbFile {
    require(path.isNotBlank()) { context.localizedString(R.string.error_smb_file_path_empty) }
    val options = EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
    options +=
      if (sequential) SMB2CreateOptions.FILE_SEQUENTIAL_ONLY else SMB2CreateOptions.FILE_RANDOM_ACCESS
    return share.openFile(
      path,
      EnumSet.of(AccessMask.GENERIC_READ),
      EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
      SMB2ShareAccess.ALL,
      SMB2CreateDisposition.FILE_OPEN,
      options,
    )
  }

  private fun locationFor(config: SmbConnectionConfig, uri: String): SmbDocumentLocation? =
    SmbDocumentUri.parse(uri)?.takeIf { it.connectionId == config.id && it.path.isNotBlank() }

  private fun hasPdfHeader(stream: BufferedInputStream): Boolean {
    stream.mark(PDF_HEADER_SCAN_BYTES)
    val bytes = ByteArray(PDF_HEADER_SCAN_BYTES)
    val count = stream.read(bytes)
    stream.reset()
    return count > 0 && bytes.copyOf(count).toString(Charsets.ISO_8859_1).contains("%PDF-")
  }

  private fun FileIdBothDirectoryInformation.hasAttribute(attribute: FileAttributes): Boolean =
    fileAttributes and attribute.value != 0L

  private fun smbErrorMessage(failure: Throwable): String {
    val detail = failure.message?.takeIf { it.isNotBlank() }
    return buildString {
      append(context.localizedString(R.string.error_smb_connect))
      if (detail != null) append("\n").append(detail)
    }
  }

  private fun sanitizeFileName(value: String): String {
    val leaf = value.substringAfterLast('/').substringAfterLast('\\')
    val sanitized = leaf.replace(UNSAFE_FILE_NAME, "_").trim().take(180)
    return sanitized.ifBlank { "document" }
  }

  private class NodeCounter(private val limitExceededMessage: String) {
    private var count = 0

    fun increment() {
      count += 1
      if (count > MAX_TREE_NODES) {
        error(limitExceededMessage)
      }
    }
  }

  private companion object {
    const val CONNECTION_TIMEOUT_SECONDS = 20L
    const val MAX_TREE_DEPTH = 48
    const val MAX_TREE_NODES = 10_000
    const val MAX_MARKDOWN_CHARS = 12 * 1024 * 1024
    const val MAX_MARKDOWN_BYTES = 48L * 1024 * 1024
    const val MAX_OFFICE_VALIDATION_BYTES = 60L * 1024 * 1024
    const val MAX_EXTERNAL_FILE_BYTES = 1024L * 1024 * 1024
    const val PDF_HEADER_SCAN_BYTES = 1024
    val UNSAFE_FILE_NAME = Regex("[\\x00-\\x1f<>:\"/\\\\|?*]")
  }
}

interface RandomAccessDocument : Closeable {
  val length: Long

  fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

private class SmbRandomAccessDocument(
  private val file: SmbFile,
  private val handle: SmbShareHandle,
) : RandomAccessDocument {
  override val length: Long = file.length

  @Synchronized
  override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int =
    file.read(buffer, position, offset, length)

  @Synchronized
  override fun close() {
    file.closeSilently()
    handle.close()
  }
}

private class SmbShareHandle(
  private val client: SMBClient,
  private val connection: Connection,
  private val session: Session,
  val share: DiskShare,
) : Closeable {
  @Volatile private var closed = false

  @Synchronized
  override fun close() {
    if (closed) return
    closed = true
    runCatching { share.close() }
    runCatching { session.close() }
    runCatching { connection.close() }
    runCatching { client.close() }
  }
}

private class ClosingInputStream(
  source: InputStream,
  private val onClosed: () -> Unit,
) : FilterInputStream(source) {
  private var closed = false

  @Synchronized
  override fun close() {
    if (closed) return
    closed = true
    try {
      super.close()
    } finally {
      onClosed()
    }
  }
}

private fun joinSmbPath(parent: String, child: String): String =
  if (parent.isBlank()) child else "$parent\\$child"
