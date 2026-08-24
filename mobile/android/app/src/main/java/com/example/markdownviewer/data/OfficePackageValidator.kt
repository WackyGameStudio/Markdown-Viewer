package com.example.markdownviewer.data

import android.content.Context
import com.example.markdownviewer.R
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

internal data class OfficeValidationStrings(
  val unsafePath: String,
  val tooManyEntries: String,
  val entryTooLarge: String,
  val uncompressedTooLarge: String,
  val packageReadFailed: String,
  val wrongFormat: (String) -> String,
) {
  companion object {
    val Korean =
      OfficeValidationStrings(
        unsafePath = "Office 문서에 안전하지 않은 경로가 있습니다.",
        tooManyEntries = "Office 문서의 내부 파일 수가 너무 많습니다.",
        entryTooLarge = "Office 문서의 내부 파일 하나가 너무 큽니다.",
        uncompressedTooLarge = "Office 문서를 압축 해제한 크기가 허용 범위를 초과합니다.",
        packageReadFailed = "Office 문서 압축 구조를 읽을 수 없습니다.",
        wrongFormat = { format -> "올바른 $format 문서가 아닙니다." },
      )

    fun forContext(context: Context): OfficeValidationStrings =
      OfficeValidationStrings(
        unsafePath = context.localizedString(R.string.error_office_unsafe_path),
        tooManyEntries = context.localizedString(R.string.error_office_too_many_entries),
        entryTooLarge = context.localizedString(R.string.error_office_entry_too_large),
        uncompressedTooLarge = context.localizedString(R.string.error_office_uncompressed_too_large),
        packageReadFailed = context.localizedString(R.string.error_office_zip_read),
        wrongFormat = { format -> context.localizedString(R.string.error_office_wrong_format, format) },
      )
  }
}

internal object OfficePackageValidator {
  fun validate(
    source: InputStream,
    kind: DocumentKind,
    strings: OfficeValidationStrings = OfficeValidationStrings.Korean,
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
          if (!isSafeEntryName(name)) throw IOException(strings.unsafePath)
          if (entry.isDirectory) {
            zip.closeEntry()
            continue
          }
          entries += 1
          if (entries > limits.maxEntries) throw IOException(strings.tooManyEntries)
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
              throw IOException(strings.entryTooLarge)
            }
            if (totalBytes > limits.maxTotalBytes || mediaBytes > limits.maxMediaBytes) {
              throw IOException(strings.uncompressedTooLarge)
            }
          }
          zip.closeEntry()
        }
      }
    } catch (failure: IOException) {
      throw failure
    } catch (failure: RuntimeException) {
      throw IOException(strings.packageReadFailed, failure)
    }

    if (entries == 0 || !hasContentTypes || !hasMainDocument) {
      throw IOException(strings.wrongFormat(if (kind == DocumentKind.Word) "DOCX" else "PPTX"))
    }
  }

  private fun isSafeEntryName(name: String): Boolean {
    if (name.isBlank() || name.startsWith('/') || name.contains(':')) return false
    return name.split('/').none { it == ".." }
  }
}
