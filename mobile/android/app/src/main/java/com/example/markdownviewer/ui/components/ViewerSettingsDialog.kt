package com.example.markdownviewer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.markdownviewer.R
import com.example.markdownviewer.data.AppLanguage
import com.example.markdownviewer.data.ViewerSettingKey
import com.example.markdownviewer.data.ViewerSettings
import com.example.markdownviewer.model.GestureTrigger
import com.example.markdownviewer.model.ViewerAction

private data class SettingRow(
  val key: ViewerSettingKey,
  val title: String,
  val description: String,
  val enabled: (ViewerSettings) -> Boolean,
)

private data class PendingBinding(
  val action: ViewerAction,
  val trigger: GestureTrigger,
  val conflictingAction: ViewerAction,
)

@Composable
fun ViewerSettingsDialog(
  language: AppLanguage,
  settings: ViewerSettings,
  onLanguageChanged: (AppLanguage) -> Unit,
  onSettingChanged: (ViewerSettingKey, Boolean) -> Unit,
  onGestureBindingChanged: (ViewerAction, GestureTrigger) -> Unit,
  onResetGestureBindings: () -> Unit,
  onDismiss: () -> Unit,
) {
  var pendingBinding by remember { mutableStateOf<PendingBinding?>(null) }
  val gestureBehaviorRows =
    listOf(
      SettingRow(
        ViewerSettingKey.EdgeGesturesFocusOnly,
        stringResource(R.string.settings_edge_focus_only),
        stringResource(R.string.settings_edge_focus_only_description),
        ViewerSettings::edgeGesturesFocusOnly,
      ),
      SettingRow(
        ViewerSettingKey.HapticFeedback,
        stringResource(R.string.settings_haptic),
        stringResource(R.string.settings_haptic_description),
        ViewerSettings::hapticFeedback,
      ),
      SettingRow(
        ViewerSettingKey.ImmersiveSystemBars,
        stringResource(R.string.settings_immersive_bars),
        stringResource(R.string.settings_immersive_bars_description),
        ViewerSettings::immersiveSystemBars,
      ),
    )
  val pinchRows =
    listOf(
      SettingRow(
        ViewerSettingKey.PinchZoomMarkdown,
        stringResource(R.string.settings_pinch_markdown),
        stringResource(R.string.settings_pinch_markdown_description),
        ViewerSettings::pinchZoomMarkdown,
      ),
      SettingRow(
        ViewerSettingKey.PinchZoomImage,
        stringResource(R.string.settings_pinch_image),
        stringResource(R.string.settings_pinch_image_description),
        ViewerSettings::pinchZoomImage,
      ),
      SettingRow(
        ViewerSettingKey.PinchZoomPdf,
        stringResource(R.string.settings_pinch_pdf),
        stringResource(R.string.settings_pinch_pdf_description),
        ViewerSettings::pinchZoomPdf,
      ),
      SettingRow(
        ViewerSettingKey.PinchZoomOffice,
        stringResource(R.string.settings_pinch_office),
        stringResource(R.string.settings_pinch_office_description),
        ViewerSettings::pinchZoomOffice,
      ),
      SettingRow(
        ViewerSettingKey.PinchZoomHtml,
        stringResource(R.string.settings_pinch_html),
        stringResource(R.string.settings_pinch_html_description),
        ViewerSettings::pinchZoomHtml,
      ),
      SettingRow(
        ViewerSettingKey.PinchZoomVideo,
        stringResource(R.string.settings_pinch_video),
        stringResource(R.string.settings_pinch_video_description),
        ViewerSettings::pinchZoomVideo,
      ),
    )
  val documentRows =
    listOf(
      SettingRow(
        ViewerSettingKey.ShowExternalOpenButton,
        stringResource(R.string.settings_external_button),
        stringResource(R.string.settings_external_button_description),
        ViewerSettings::showExternalOpenButton,
      )
    )
  val videoRows =
    listOf(
      SettingRow(
        ViewerSettingKey.VideoAutoplay,
        stringResource(R.string.settings_video_autoplay),
        stringResource(R.string.settings_video_autoplay_description),
        ViewerSettings::videoAutoplay,
      ),
      SettingRow(
        ViewerSettingKey.VideoRememberPosition,
        stringResource(R.string.settings_video_remember),
        stringResource(R.string.settings_video_remember_description),
        ViewerSettings::videoRememberPosition,
      ),
      SettingRow(
        ViewerSettingKey.VideoKeepScreenOn,
        stringResource(R.string.settings_video_keep_screen_on),
        stringResource(R.string.settings_video_keep_screen_on_description),
        ViewerSettings::videoKeepScreenOn,
      ),
    )

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
      Surface(
        modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f).widthIn(max = 680.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              stringResource(R.string.settings_title),
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.headlineSmall,
            )
            IconButton(onClick = onDismiss) {
              Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.settings_close))
            }
          }
          HorizontalDivider()
          LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings-list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
          ) {
            item { SettingsSectionTitle(stringResource(R.string.settings_general_section)) }
            item {
              LanguageSettingRow(
                language = language,
                onLanguageChanged = onLanguageChanged,
              )
            }
            item {
              SettingsNotice(stringResource(R.string.settings_gesture_notice))
            }
            item { SettingsSectionTitle(stringResource(R.string.settings_gesture_bindings_section)) }
            items(ViewerAction.entries, key = { "binding-${it.name}" }) { action ->
              GestureBindingRow(
                action = action,
                selected = settings.gestureBindings[action],
                onSelected = { trigger ->
                  val conflict = settings.gestureBindings.actionFor(trigger)?.takeIf { it != action }
                  if (conflict == null) {
                    onGestureBindingChanged(action, trigger)
                  } else {
                    pendingBinding = PendingBinding(action, trigger, conflict)
                  }
                },
              )
            }
            item {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
              ) {
                TextButton(onClick = onResetGestureBindings) {
                  Icon(Icons.Outlined.Restore, contentDescription = null)
                  Text(
                    stringResource(R.string.settings_restore_defaults),
                    modifier = Modifier.padding(start = 6.dp),
                  )
                }
              }
            }
            settingsSection(
              titleRes = R.string.settings_gesture_behavior_section,
              rows = gestureBehaviorRows,
              settings = settings,
              onSettingChanged = onSettingChanged,
            )
            settingsSection(
              titleRes = R.string.settings_pinch_section,
              rows = pinchRows,
              settings = settings,
              onSettingChanged = onSettingChanged,
            )
            settingsSection(
              titleRes = R.string.settings_document_section,
              rows = documentRows,
              settings = settings,
              onSettingChanged = onSettingChanged,
            )
            settingsSection(
              titleRes = R.string.settings_video_section,
              rows = videoRows,
              settings = settings,
              onSettingChanged = onSettingChanged,
            )
          }
        }
      }
    }
  }

  val pending = pendingBinding
  if (pending != null) {
    AlertDialog(
      onDismissRequest = { pendingBinding = null },
      title = { Text(stringResource(R.string.settings_binding_change_title)) },
      text = {
        Text(
          stringResource(
            R.string.settings_binding_change_message,
            gestureTriggerDisplayName(pending.trigger),
            viewerActionDisplayName(pending.conflictingAction),
            viewerActionDisplayName(pending.action),
            gestureTriggerDisplayName(GestureTrigger.None),
          )
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onGestureBindingChanged(pending.action, pending.trigger)
            pendingBinding = null
          }
        ) {
          Text(stringResource(R.string.common_change))
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingBinding = null }) {
          Text(stringResource(R.string.common_cancel))
        }
      },
    )
  }
}

@Composable
private fun GestureBindingRow(
  action: ViewerAction,
  selected: GestureTrigger,
  onSelected: (GestureTrigger) -> Unit,
) {
  var expanded by remember(action) { mutableStateOf(false) }
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(viewerActionDisplayName(action), style = MaterialTheme.typography.bodyLarge)
      Text(
        viewerActionDescription(action),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
    }
    Box {
      TextButton(onClick = { expanded = true }) {
        Text(gestureTriggerDisplayName(selected))
        Icon(
          Icons.Outlined.ArrowDropDown,
          contentDescription = stringResource(R.string.settings_gesture_select),
        )
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        GestureTrigger.entries.forEach { trigger ->
          DropdownMenuItem(
            text = { Text(gestureTriggerDisplayName(trigger)) },
            onClick = {
              expanded = false
              if (trigger != selected) onSelected(trigger)
            },
          )
        }
      }
    }
  }
}

@Composable
private fun LanguageSettingRow(
  language: AppLanguage,
  onLanguageChanged: (AppLanguage) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("language-setting")
        .padding(horizontal = 24.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.bodyLarge)
      Text(
        stringResource(R.string.settings_language_description),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
    }
    Box {
      TextButton(onClick = { expanded = true }) {
        Text(appLanguageDisplayName(language))
        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        AppLanguage.entries.forEach { option ->
          DropdownMenuItem(
            modifier = Modifier.testTag("language-${option.languageTag}"),
            text = { Text(appLanguageDisplayName(option)) },
            onClick = {
              expanded = false
              if (option != language) onLanguageChanged(option)
            },
          )
        }
      }
    }
  }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsSection(
  titleRes: Int,
  rows: List<SettingRow>,
  settings: ViewerSettings,
  onSettingChanged: (ViewerSettingKey, Boolean) -> Unit,
) {
  item { SettingsSectionTitle(stringResource(titleRes)) }
  items(rows, key = { it.key.name }) { row ->
    SettingSwitchRow(
      title = row.title,
      description = row.description,
      checked = row.enabled(settings),
      onCheckedChange = { onSettingChanged(row.key, it) },
    )
  }
}

@Composable
private fun SettingsSectionTitle(title: String) {
  Text(
    text = title,
    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 8.dp),
    color = MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.titleMedium,
  )
}

@Composable
private fun SettingsNotice(message: String) {
  Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.medium,
  ) {
    Text(
      text = message,
      modifier = Modifier.padding(16.dp),
      color = MaterialTheme.colorScheme.onSecondaryContainer,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
private fun SettingSwitchRow(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(role = Role.Switch) { onCheckedChange(!checked) }
        .padding(horizontal = 24.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      Text(
        description,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun viewerActionDisplayName(action: ViewerAction): String =
  when (action) {
    ViewerAction.ToggleFocus -> stringResource(R.string.action_toggle_focus)
    ViewerAction.ToggleExplorer -> stringResource(R.string.action_toggle_explorer)
    ViewerAction.ToggleDetails -> stringResource(R.string.action_toggle_details)
    ViewerAction.ToggleControls -> stringResource(R.string.action_toggle_controls)
    ViewerAction.OpenExternalApp -> stringResource(R.string.action_open_external)
  }

@Composable
private fun viewerActionDescription(action: ViewerAction): String =
  when (action) {
    ViewerAction.ToggleFocus -> stringResource(R.string.action_toggle_focus_description)
    ViewerAction.ToggleExplorer -> stringResource(R.string.action_toggle_explorer_description)
    ViewerAction.ToggleDetails -> stringResource(R.string.action_toggle_details_description)
    ViewerAction.ToggleControls -> stringResource(R.string.action_toggle_controls_description)
    ViewerAction.OpenExternalApp -> stringResource(R.string.action_open_external_description)
  }

@Composable
private fun gestureTriggerDisplayName(trigger: GestureTrigger): String =
  when (trigger) {
    GestureTrigger.None -> stringResource(R.string.gesture_none)
    GestureTrigger.TripleTap -> stringResource(R.string.gesture_triple_tap)
    GestureTrigger.ThreeFingerTap -> stringResource(R.string.gesture_three_finger_tap)
    GestureTrigger.EdgeLeftIn -> stringResource(R.string.gesture_edge_left)
    GestureTrigger.EdgeRightIn -> stringResource(R.string.gesture_edge_right)
    GestureTrigger.EdgeTopIn -> stringResource(R.string.gesture_edge_top)
  }

@Composable
private fun appLanguageDisplayName(language: AppLanguage): String =
  when (language) {
    AppLanguage.Korean -> stringResource(R.string.language_korean)
    AppLanguage.English -> stringResource(R.string.language_english)
  }
