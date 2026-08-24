package com.example.markdownviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class MarkdownViewerLaunchTest {
  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun appShowsExplorerAndOpenFolderAction() {
    composeRule.onNodeWithText("탐색기").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("문서 폴더 열기").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("설정").assertIsDisplayed()
  }

  @Test
  fun settingsDialogShowsGestureSections() {
    composeRule.onNodeWithContentDescription("설정").performClick()
    composeRule.onNodeWithText("제스처 바인딩").assertIsDisplayed()
    composeRule.onNodeWithText("집중 모드 토글").assertIsDisplayed()
    composeRule.onNodeWithText("빠른 3회 탭").assertIsDisplayed()
    composeRule.onNodeWithTag("settings-list").performScrollToNode(hasText("핀치 확대"))
    composeRule.onNodeWithText("핀치 확대").assertIsDisplayed()
    composeRule.onNodeWithTag("settings-list").performScrollToNode(hasText("외부 앱으로 열기 버튼 표시"))
    composeRule.onNodeWithText("외부 앱으로 열기 버튼 표시").assertIsDisplayed()
  }
}
