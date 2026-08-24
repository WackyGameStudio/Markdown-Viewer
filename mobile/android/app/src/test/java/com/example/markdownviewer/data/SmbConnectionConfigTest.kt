package com.example.markdownviewer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SmbConnectionConfigTest {
  @Test
  fun defaultDraftUsesProvidedShare() {
    val config = SmbConnectionConfig.defaultDraft()

    assertEquals("100.69.138.65", config.host)
    assertEquals("n100-share", config.share)
    assertEquals("\\\\100.69.138.65\\n100-share", config.uncPath)
  }

  @Test
  fun validatedNormalizesFolderSeparators() {
    val config =
      SmbConnectionConfig(
          id = "connection-1",
          name = " Documents ",
          host = " server.local ",
          share = " share ",
          initialPath = " notes/2026\\August ",
        )
        .validated()

    assertEquals("Documents", config.name)
    assertEquals("server.local", config.host)
    assertEquals("share", config.share)
    assertEquals("notes\\2026\\August", config.initialPath)
  }

  @Test
  fun validatedRejectsTraversal() {
    assertThrows(IllegalArgumentException::class.java) {
      SmbConnectionConfig(
          id = "connection-1",
          name = "share",
          host = "server.local",
          share = "share",
          initialPath = "notes/../private",
        )
        .validated()
    }
  }
}
