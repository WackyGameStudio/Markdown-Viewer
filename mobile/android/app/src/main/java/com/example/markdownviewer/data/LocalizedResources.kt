package com.example.markdownviewer.data

import android.content.Context
import androidx.annotation.StringRes

internal fun Context.localizedString(@StringRes resourceId: Int, vararg formatArgs: Any): String =
  AppLanguagePreferences.localizedContext(this).getString(resourceId, *formatArgs)
