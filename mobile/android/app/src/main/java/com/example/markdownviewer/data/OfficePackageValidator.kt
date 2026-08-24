package com.example.markdownviewer.data

import com.example.markdownviewer.model.DocumentKind
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

internal data class OfficePackageLimits(
  val maxEntries: Int = 4_000,
  val maxEntryBytes: Long = 32L * 1024 * 1024,
  val maxTotalBytes: Long = 256L * 1024 * 1024,
  val maxMediaBytes: Long = 192L * 1024 * 1024,
)

internal object OfficePackageValidator {
  fun validate(
    source: InputStream,
    kind: DocumentKind,
    limits: OfficePackageLimits = OfficePackageLimits(),
  ) {
    require(kind == DocumentKind.Word || kind == DocumentKind.Presentation)
    var entries = 0
    var totalBytes = 0L
    var mediaBytes = 0L
    var hasContentTypes = false
    var hasMainDocument = false
    val mainDocument =
      if (kind == DocumentKind.Word) "word/document.xml" else "ppt/presentation.xml"
    val mediaPrefix = if (kind == DocumentKind.Word) "word/media/" else "ppt/media/"
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    try {
      ZipInputStream(BufferedInputStream(source)).use { zip ->
        while (true) {
          val entry = zip.nextEntry ?: break
          val name = entry.name.replace('\\', '/')
          if (!isSafeEntryName(name)) throw IOException("Office 문서에 안전하지 않은 경로가 있습니다.")
          if (entry.isDirectory) {
            zip.closeEntry()
            continue
          }
          entries += 1
          if (entries > limits.maxEntries) throw IOException("Office 문서의 내부 파일 수가 너무 많습니다.")
          if (name == "[Content_Types].xml") hasContentTypes = true
          if (name == mainDocument) hasMainDocument = true

          var entryBytes = 0L
          while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            entryBytes += read
            totalBytes += read
            if (name.startsWith(mediaPrefix)) mediaBytes += read
            if (entryBytes > limits.maxEntryBytes) {
              throw IOException("Office 문서의 내부 파일 하나가 너무 큽니다.")
            }
            if (totalBytes > limits.maxTotalBytes || mediaBytes > limits.maxMediaBytes) {
              throw IOException("Office 문서를 압축 해제한 크기가 허용 범위를 초과합니다.")
            }
          }
          zip.closeEntry()
        }
      }
    } catch (failure: IOException) {
      throw failure
    } catch (failure: RuntimeException) {
      throw IOException("Office 문서 압축 구조를 읽을 수 없습니다.", failure)
    }

    if (entries == 0 || !hasContentTypes || !hasMainDocument) {
      throw IOException("올바른 ${if (kind == DocumentKind.Word) "DOCX" else "PPTX"} 문서가 아닙니다.")
    }
  }

  private fun isSafeEntryName(name: String): Boolean {
    if (name.isBlank() || name.startsWith('/') || name.contains(':')) return false
    return name.split('/').none { it == ".." }
  }
}
