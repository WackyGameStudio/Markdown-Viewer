package com.example.markdownviewer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
  @Test
  fun missingOrUnknownPreferenceDefaultsToKorean() {
    assertEquals(AppLanguage.Korean, AppLanguage.fromStorageValue(null))
    assertEquals(AppLanguage.Korean, AppLanguage.fromStorageValue("unknown"))
  }

  @Test
  fun storedLanguageTagsRestoreSupportedLanguages() {
    assertEquals(AppLanguage.Korean, AppLanguage.fromStorageValue("ko"))
    assertEquals(AppLanguage.English, AppLanguage.fromStorageValue("en"))
  }
}
