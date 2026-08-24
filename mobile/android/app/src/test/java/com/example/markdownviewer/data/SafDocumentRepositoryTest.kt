package com.example.markdownviewer.data

import com.example.markdownviewer.model.DocumentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafDocumentRepositoryTest {
  @Test
  fun resolvesRelativeDocumentReferencesInsideSelectedRoot() {
    assertEquals(
      "guide/images/diagram.png",
      SafDocumentRepository.normalizeReference("guide/start.md", "./images/diagram.png"),
    )
    assertEquals(
      "shared/note.md",
      SafDocumentRepository.normalizeReference("guide/start.md", "../shared/note.md#details"),
    )
  }

  @Test
  fun rejectsExternalAndEscapingReferences() {
    assertNull(SafDocumentRepository.normalizeReference("guide/start.md", "https://example.com/a.png"))
    assertNull(SafDocumentRepository.normalizeReference("start.md", "../private.md"))
  }

  @Test
  fun recognizesVideoExtensionsAndMimeTypesCaseInsensitively() {
    assertEquals(DocumentKind.Video, SafDocumentRepository.documentKind("clip.MP4"))
    assertEquals(DocumentKind.Video, SafDocumentRepository.documentKind("recording.webm"))
    assertEquals(DocumentKind.Video, SafDocumentRepository.documentKind("movie.mkv"))
    assertEquals("video/mp4", SafDocumentRepository.mimeTypeFromName("clip.m4v"))
    assertEquals("video/quicktime", SafDocumentRepository.mimeTypeFromName("clip.MOV"))
  }

  @Test
  fun recognizesOfficeHtmlAndLocalHtmlResources() {
    assertEquals(DocumentKind.Word, SafDocumentRepository.documentKind("report.DOCX"))
    assertEquals(DocumentKind.Word, SafDocumentRepository.documentKind("legacy.doc"))
    assertEquals(DocumentKind.Presentation, SafDocumentRepository.documentKind("deck.pptx"))
    assertEquals(DocumentKind.Presentation, SafDocumentRepository.documentKind("legacy.PPT"))
    assertEquals(DocumentKind.Html, SafDocumentRepository.documentKind("index.HTML"))
    assertEquals(DocumentKind.Resource, SafDocumentRepository.documentKind("screen.css"))
    assertEquals(DocumentKind.Resource, SafDocumentRepository.documentKind("font.woff2"))
    assertEquals(
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      SafDocumentRepository.mimeTypeFromName("report.docx"),
    )
  }
}
