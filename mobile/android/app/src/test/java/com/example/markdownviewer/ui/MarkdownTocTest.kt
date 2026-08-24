package com.example.markdownviewer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTocTest {
  @Test
  fun extractsHeadingsButIgnoresFencedCode() {
    val markdown =
      """
      # 시작
      ```markdown
      ## 코드 안 제목
      ```
      ### 세부 항목 ###
      """.trimIndent()

    val toc = MarkdownViewerViewModel.extractToc(markdown)

    assertEquals(listOf("시작", "세부 항목"), toc.map { it.text })
    assertEquals(listOf(1, 3), toc.map { it.level })
  }
}
