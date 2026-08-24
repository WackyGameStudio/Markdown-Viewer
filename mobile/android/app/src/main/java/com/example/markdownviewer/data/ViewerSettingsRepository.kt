package com.example.markdownviewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.markdownviewer.model.GestureBindings
import com.example.markdownviewer.model.GestureTrigger
import com.example.markdownviewer.model.ViewerAction
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class ViewerSettings(
  val gestureBindings: GestureBindings = GestureBindings.Default,
  val pinchZoomMarkdown: Boolean = false,
  val pinchZoomImage: Boolean = true,
  val pinchZoomPdf: Boolean = true,
  val pinchZoomOffice: Boolean = true,
  val pinchZoomHtml: Boolean = true,
  val pinchZoomVideo: Boolean = true,
  val edgeGesturesFocusOnly: Boolean = true,
  val hapticFeedback: Boolean = true,
  val immersiveSystemBars: Boolean = false,
  val showExternalOpenButton: Boolean = true,
  val videoAutoplay: Boolean = false,
  val videoRememberPosition: Boolean = true,
  val videoKeepScreenOn: Boolean = true,
)

enum class ViewerSettingKey {
  PinchZoomMarkdown,
  PinchZoomImage,
  PinchZoomPdf,
  PinchZoomOffice,
  PinchZoomHtml,
  PinchZoomVideo,
  EdgeGesturesFocusOnly,
  HapticFeedback,
  ImmersiveSystemBars,
  ShowExternalOpenButton,
  VideoAutoplay,
  VideoRememberPosition,
  VideoKeepScreenOn,
}

private val Context.viewerSettingsDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "viewer_settings")

class ViewerSettingsRepository(context: Context) {
  private val dataStore = context.applicationContext.viewerSettingsDataStore

  val settings: Flow<ViewerSettings> =
    dataStore.data
      .catch { failure ->
        if (failure is IOException) emit(emptyPreferences()) else throw failure
      }
      .map(::readSettings)

  suspend fun set(key: ViewerSettingKey, enabled: Boolean) {
    dataStore.edit { preferences -> preferences[key.preferenceKey] = enabled }
  }

  suspend fun bindGesture(action: ViewerAction, trigger: GestureTrigger) {
    dataStore.edit { preferences ->
      writeBindings(preferences, readBindings(preferences).rebind(action, trigger))
    }
  }

  suspend fun resetGestureBindings() {
    dataStore.edit { preferences -> writeBindings(preferences, GestureBindings.Default) }
  }

  private fun readSettings(preferences: Preferences): ViewerSettings =
    ViewerSettings(
      gestureBindings = readBindings(preferences),
      pinchZoomMarkdown = preferences[Keys.pinchZoomMarkdown] ?: false,
      pinchZoomImage = preferences[Keys.pinchZoomImage] ?: true,
      pinchZoomPdf = preferences[Keys.pinchZoomPdf] ?: true,
      pinchZoomOffice = preferences[Keys.pinchZoomOffice] ?: true,
      pinchZoomHtml = preferences[Keys.pinchZoomHtml] ?: true,
      pinchZoomVideo = preferences[Keys.pinchZoomVideo] ?: true,
      edgeGesturesFocusOnly = preferences[Keys.edgeGesturesFocusOnly] ?: true,
      hapticFeedback = preferences[Keys.hapticFeedback] ?: true,
      immersiveSystemBars = preferences[Keys.immersiveSystemBars] ?: false,
      showExternalOpenButton = preferences[Keys.showExternalOpenButton] ?: true,
      videoAutoplay = preferences[Keys.videoAutoplay] ?: false,
      videoRememberPosition = preferences[Keys.videoRememberPosition] ?: true,
      videoKeepScreenOn = preferences[Keys.videoKeepScreenOn] ?: true,
    )

  private fun readBindings(preferences: Preferences): GestureBindings {
    val storedFocus = GestureTrigger.fromStorageValue(preferences[Keys.bindToggleFocus])
    val hasLegacyFocusSetting =
      preferences[Keys.legacyTripleTapFocus] != null ||
        preferences[Keys.legacyThreeFingerTapFocus] != null
    val focus =
      storedFocus
        ?: when {
          preferences[Keys.legacyTripleTapFocus] == true -> GestureTrigger.TripleTap
          preferences[Keys.legacyThreeFingerTapFocus] == true -> GestureTrigger.ThreeFingerTap
          hasLegacyFocusSetting -> GestureTrigger.None
          else -> GestureTrigger.TripleTap
        }
    val explorer =
      GestureTrigger.fromStorageValue(preferences[Keys.bindToggleExplorer])
        ?: if (preferences[Keys.legacyEdgeExplorer] != false) GestureTrigger.EdgeLeftIn else GestureTrigger.None
    val details =
      GestureTrigger.fromStorageValue(preferences[Keys.bindToggleDetails])
        ?: if (preferences[Keys.legacyEdgeDetails] != false) GestureTrigger.EdgeRightIn else GestureTrigger.None
    val controls =
      GestureTrigger.fromStorageValue(preferences[Keys.bindToggleControls])
        ?: if (preferences[Keys.legacyEdgeControls] != false) GestureTrigger.EdgeTopIn else GestureTrigger.None
    val external =
      GestureTrigger.fromStorageValue(preferences[Keys.bindOpenExternal]) ?: GestureTrigger.None
    return GestureBindings.normalized(
      GestureBindings(
        toggleFocus = focus,
        toggleExplorer = explorer,
        toggleDetails = details,
        toggleControls = controls,
        openExternalApp = external,
      )
    )
  }

  private fun writeBindings(preferences: MutablePreferences, bindings: GestureBindings) {
    preferences[Keys.bindToggleFocus] = bindings.toggleFocus.storageValue
    preferences[Keys.bindToggleExplorer] = bindings.toggleExplorer.storageValue
    preferences[Keys.bindToggleDetails] = bindings.toggleDetails.storageValue
    preferences[Keys.bindToggleControls] = bindings.toggleControls.storageValue
    preferences[Keys.bindOpenExternal] = bindings.openExternalApp.storageValue
  }

  private val ViewerSettingKey.preferenceKey: Preferences.Key<Boolean>
    get() =
      when (this) {
        ViewerSettingKey.PinchZoomMarkdown -> Keys.pinchZoomMarkdown
        ViewerSettingKey.PinchZoomImage -> Keys.pinchZoomImage
        ViewerSettingKey.PinchZoomPdf -> Keys.pinchZoomPdf
        ViewerSettingKey.PinchZoomOffice -> Keys.pinchZoomOffice
        ViewerSettingKey.PinchZoomHtml -> Keys.pinchZoomHtml
        ViewerSettingKey.PinchZoomVideo -> Keys.pinchZoomVideo
        ViewerSettingKey.EdgeGesturesFocusOnly -> Keys.edgeGesturesFocusOnly
        ViewerSettingKey.HapticFeedback -> Keys.hapticFeedback
        ViewerSettingKey.ImmersiveSystemBars -> Keys.immersiveSystemBars
        ViewerSettingKey.ShowExternalOpenButton -> Keys.showExternalOpenButton
        ViewerSettingKey.VideoAutoplay -> Keys.videoAutoplay
        ViewerSettingKey.VideoRememberPosition -> Keys.videoRememberPosition
        ViewerSettingKey.VideoKeepScreenOn -> Keys.videoKeepScreenOn
      }

  private object Keys {
    val bindToggleFocus = stringPreferencesKey("binding_toggle_focus")
    val bindToggleExplorer = stringPreferencesKey("binding_toggle_explorer")
    val bindToggleDetails = stringPreferencesKey("binding_toggle_details")
    val bindToggleControls = stringPreferencesKey("binding_toggle_controls")
    val bindOpenExternal = stringPreferencesKey("binding_open_external")

    val legacyThreeFingerTapFocus = booleanPreferencesKey("gesture_three_finger_focus")
    val legacyTripleTapFocus = booleanPreferencesKey("gesture_triple_tap_focus")
    val legacyEdgeExplorer = booleanPreferencesKey("edge_explorer")
    val legacyEdgeDetails = booleanPreferencesKey("edge_details")
    val legacyEdgeControls = booleanPreferencesKey("edge_controls")

    val pinchZoomMarkdown = booleanPreferencesKey("pinch_zoom_markdown")
    val pinchZoomImage = booleanPreferencesKey("pinch_zoom_image")
    val pinchZoomPdf = booleanPreferencesKey("pinch_zoom_pdf")
    val pinchZoomOffice = booleanPreferencesKey("pinch_zoom_office")
    val pinchZoomHtml = booleanPreferencesKey("pinch_zoom_html")
    val pinchZoomVideo = booleanPreferencesKey("pinch_zoom_video")
    val edgeGesturesFocusOnly = booleanPreferencesKey("edge_focus_only")
    val hapticFeedback = booleanPreferencesKey("gesture_haptic")
    val immersiveSystemBars = booleanPreferencesKey("focus_immersive_system_bars")
    val showExternalOpenButton = booleanPreferencesKey("document_external_open_button")
    val videoAutoplay = booleanPreferencesKey("video_autoplay")
    val videoRememberPosition = booleanPreferencesKey("video_remember_position")
    val videoKeepScreenOn = booleanPreferencesKey("video_keep_screen_on")
  }
}
