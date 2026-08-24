package com.example.markdownviewer.data

import com.example.markdownviewer.model.DocumentKind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class OfficePackageValidatorTest {
  @Test
  fun acceptsMinimalDocxAndPptxPackages() {
    OfficePackageValidator.validate(
      packageStream("[Content_Types].xml" to "types", "word/document.xml" to "document"),
      DocumentKind.Word,
    )
    OfficePackageValidator.validate(
      packageStream("[Content_Types].xml" to "types", "ppt/presentation.xml" to "presentation"),
      DocumentKind.Presentation,
    )
  }

  @Test
  fun rejectsMissingMainPartAndUnsafePaths() {
    assertThrows(IOException::class.java) {
      OfficePackageValidator.validate(
        packageStream("[Content_Types].xml" to "types"),
        DocumentKind.Word,
      )
    }
    assertThrows(IOException::class.java) {
      OfficePackageValidator.validate(
        packageStream(
          "[Content_Types].xml" to "types",
          "word/document.xml" to "document",
          "../escape.xml" to "bad",
        ),
        DocumentKind.Word,
      )
    }
  }

  @Test
  fun enforcesActualDecompressedByteLimits() {
    assertThrows(IOException::class.java) {
      OfficePackageValidator.validate(
        packageStream("[Content_Types].xml" to "types", "word/document.xml" to "0123456789"),
        DocumentKind.Word,
        limits = OfficePackageLimits(maxEntryBytes = 6, maxTotalBytes = 100, maxMediaBytes = 100),
      )
    }
  }

  private fun packageStream(vararg entries: Pair<String, String>): ByteArrayInputStream {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
      entries.forEach { (name, value) ->
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray())
        zip.closeEntry()
      }
    }
    return ByteArrayInputStream(output.toByteArray())
  }
}
