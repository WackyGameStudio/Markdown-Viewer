package com.example.markdownviewer.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.example.markdownviewer.R
import com.example.markdownviewer.model.DocumentKind
import com.example.markdownviewer.model.DocumentNode
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafDocumentRepository(private val context: Context) {
  private val resolver: ContentResolver = context.contentResolver

  fun persistReadPermission(uri: Uri) {
    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }

  fun hasPersistedPermission(uri: Uri): Boolean =
    resolver.persistedUriPermissions.any { permission ->
      permission.isReadPermission && permission.uri == uri
    }

  fun releasePermissionsExcept(keptUris: Set<String>) {
    resolver.persistedUriPermissions
      .filter { it.isReadPermission && it.uri.toString() !in keptUris }
      .forEach { permission ->
        runCatching {
          resolver.releasePersistableUriPermission(
            permission.uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
      }
  }

  suspend fun loadTree(treeUri: Uri): DocumentNode =
    withContext(Dispatchers.IO) {
      val root =
        DocumentFile.fromTreeUri(context, treeUri)
          ?: error(context.localizedString(R.string.error_selected_folder_open))
      if (!root.exists() || !root.isDirectory) {
        error(context.localizedString(R.string.error_selected_location_not_folder))
      }
      buildNode(root, relativePath = "", includeEmptyFolder = true)
        ?: error(context.localizedString(R.string.error_selected_folder_read))
    }

  suspend fun readMarkdown(uri: String): String =
    withContext(Dispatchers.IO) {
      val parsed = uri.toUri()
      resolver.openInputStream(parsed)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
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
      } ?: error(context.localizedString(R.string.error_document_read))
    }

  suspend fun validateOfficePackage(uri: String, kind: DocumentKind) {
    withContext(Dispatchers.IO) {
      resolver.openInputStream(uri.toUri())?.use { stream ->
        OfficePackageValidator.validate(stream, kind, OfficeValidationStrings.forContext(context))
      } ?: error(context.localizedString(R.string.error_office_read))
    }
  }

  fun openStream(uri: String, maxBytes: Long, requirePdfHeader: Boolean): InputStream? {
    val raw = resolver.openInputStream(uri.toUri()) ?: return null
    val buffered = BufferedInputStream(raw)
    if (requirePdfHeader && !hasPdfHeader(buffered)) {
      buffered.close()
      return null
    }
    return SizeLimitedInputStream(
      buffered,
      maxBytes,
      context.localizedString(R.string.error_document_size_limit),
    )
  }

  fun mimeType(uri: String, fileName: String = ""): String =
    resolver.getType(uri.toUri()) ?: mimeTypeFromName(fileName)

  private fun buildNode(
    file: DocumentFile,
    relativePath: String,
    includeEmptyFolder: Boolean = false,
  ): DocumentNode? {
    val name =
      file.name?.takeIf { it.isNotBlank() }
        ?: context.localizedString(R.string.document_unnamed)
    if (name.startsWith('.') && relativePath.isNotEmpty()) return null

    if (file.isDirectory) {
      val children =
        runCatching { file.listFiles().toList() }
          .getOrDefault(emptyList())
          .mapNotNull { child ->
            val childName = child.name ?: return@mapNotNull null
            val childPath = if (relativePath.isEmpty()) childName else "$relativePath/$childName"
            buildNode(child, childPath)
          }
          .sortedWith(
            compareBy<DocumentNode> { !it.isFolder }
              .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
          )

      if (!includeEmptyFolder && children.isEmpty()) return null
      return DocumentNode(
        uri = file.uri.toString(),
        name = name,
        relativePath = relativePath,
        kind = DocumentKind.Folder,
        children = children,
      )
    }

    val kind = documentKind(name) ?: return null
    return DocumentNode(
      uri = file.uri.toString(),
      name = name,
      relativePath = relativePath,
      kind = kind,
      sizeBytes = file.length().coerceAtLeast(0),
    )
  }

  private fun hasPdfHeader(stream: BufferedInputStream): Boolean {
    stream.mark(PDF_HEADER_SCAN_BYTES)
    val bytes = ByteArray(PDF_HEADER_SCAN_BYTES)
    val count = stream.read(bytes)
    stream.reset()
    if (count <= 0) return false
    return bytes.copyOf(count).toString(Charsets.ISO_8859_1).contains("%PDF-")
  }

  companion object {
    private const val MAX_MARKDOWN_CHARS = 12 * 1024 * 1024

    fun documentKind(name: String): DocumentKind? =
      when (name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)) {
        "md" -> DocumentKind.Markdown
        "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp" -> DocumentKind.Image
        "pdf" -> DocumentKind.Pdf
        "mp4", "m4v", "webm", "mkv", "mov" -> DocumentKind.Video
        "doc", "docx" -> DocumentKind.Word
        "ppt", "pptx" -> DocumentKind.Presentation
        "html", "htm" -> DocumentKind.Html
        "css", "js", "json", "xml", "woff", "woff2", "ttf", "otf", "eot", "ico", "vtt", "srt" ->
          DocumentKind.Resource
        else -> null
      }

    fun normalizeReference(activePath: String, reference: String): String? {
      val trimmed = reference.trim()
      if (trimmed.isEmpty()) return null
      if (SCHEME_REGEX.containsMatchIn(trimmed) || trimmed.startsWith("//")) return null

      val withoutSuffix = trimmed.substringBefore('#').substringBefore('?')
      val decoded =
        runCatching { URLDecoder.decode(withoutSuffix.replace("+", "%2B"), Charsets.UTF_8.name()) }
          .getOrNull() ?: return null
      val normalizedSlashes = decoded.replace('\\', '/')
      val base =
        if (normalizedSlashes.startsWith('/')) {
          emptyList()
        } else {
          activePath.substringBeforeLast('/', missingDelimiterValue = "")
            .split('/')
            .filter { it.isNotEmpty() }
        }
      val result = base.toMutableList()
      normalizedSlashes.split('/').forEach { part ->
        when (part) {
          "", "." -> Unit
          ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex) else return null
          else -> result += part
        }
      }
      return result.joinToString("/")
    }

    fun mimeTypeFromName(name: String): String =
      when (name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)) {
        "md" -> "text/markdown"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "bmp" -> "image/bmp"
        "pdf" -> "application/pdf"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js" -> "text/javascript"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "eot" -> "application/vnd.ms-fontobject"
        "ico" -> "image/x-icon"
        "vtt" -> "text/vtt"
        "srt" -> "application/x-subrip"
        else -> "application/octet-stream"
      }

    private val SCHEME_REGEX = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private const val PDF_HEADER_SCAN_BYTES = 1024
  }
}

internal class SizeLimitedInputStream(
  source: InputStream,
  private val maxBytes: Long,
  private val limitExceededMessage: String = "문서가 허용 크기를 초과했습니다.",
) : FilterInputStream(source) {
  private var consumed = 0L
  private var markedConsumed = -1L

  override fun read(): Int {
    val value = super.read()
    if (value >= 0) record(1)
    return value
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (offset < 0 || length < 0 || offset > buffer.size - length) {
      throw IndexOutOfBoundsException()
    }
    if (length == 0) return 0
    val allowed = (maxBytes - consumed + 1).coerceIn(1, length.toLong()).toInt()
    val count = super.read(buffer, offset, allowed)
    if (count > 0) record(count.toLong())
    return count
  }

  override fun skip(count: Long): Long {
    if (count <= 0) return 0
    val allowed = (maxBytes - consumed + 1).coerceIn(1, count.coerceAtLeast(1))
    val skipped = super.skip(allowed)
    if (skipped > 0) record(skipped)
    return skipped
  }

  @Synchronized
  override fun mark(readLimit: Int) {
    super.mark(readLimit)
    markedConsumed = consumed
  }

  @Synchronized
  override fun reset() {
    super.reset()
    if (markedConsumed >= 0) consumed = markedConsumed
  }

  private fun record(count: Long) {
    consumed += count
    if (consumed > maxBytes) throw IOException(limitExceededMessage)
  }
}
