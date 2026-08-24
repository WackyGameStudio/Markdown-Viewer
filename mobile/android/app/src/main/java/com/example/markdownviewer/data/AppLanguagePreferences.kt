package com.example.markdownviewer.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val languageTag: String) {
  Korean("ko"),
  English("en");

  companion object {
    fun fromStorageValue(value: String?): AppLanguage =
      entries.firstOrNull { it.languageTag == value } ?: Korean
  }
}

object AppLanguagePreferences {
  private const val PREFERENCES_NAME = "app_language"
  private const val LANGUAGE_KEY = "language_tag"

  fun current(context: Context): AppLanguage =
    AppLanguage.fromStorageValue(
      context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getString(LANGUAGE_KEY, null)
    )

  fun set(context: Context, language: AppLanguage): Boolean =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(LANGUAGE_KEY, language.languageTag)
      .commit()

  fun localizedContext(base: Context): Context {
    val locale = Locale.forLanguageTag(current(base).languageTag)
    val configuration = Configuration(base.resources.configuration)
    configuration.setLocale(locale)
    configuration.setLocales(LocaleList(locale))
    return base.createConfigurationContext(configuration)
  }
}
