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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
  settings: ViewerSettings,
  onSettingChanged: (ViewerSettingKey, Boolean) -> Unit,
  onGestureBindingChanged: (ViewerAction, GestureTrigger) -> Unit,
  onResetGestureBindings: () -> Unit,
  onDismiss: () -> Unit,
) {
  var pendingBinding by remember { mutableStateOf<PendingBinding?>(null) }

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
            Text("설정", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onDismiss) {
              Icon(Icons.Outlined.Close, contentDescription = "설정 닫기")
            }
          }
          HorizontalDivider()
          LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings-list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
          ) {
            item {
              SettingsNotice(
                "하나의 제스처는 한 기능에만 지정됩니다. 빠른 3회 탭은 화면 확대, 세 손가락 탭은 TalkBack과 충돌할 수 있습니다."
              )
            }
            item { SettingsSectionTitle("제스처 바인딩") }
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
                  Text("기본값 복원", modifier = Modifier.padding(start = 6.dp))
                }
              }
            }
            settingsSection(
              title = "제스처 동작",
              rows =
                listOf(
                  SettingRow(
                    ViewerSettingKey.EdgeGesturesFocusOnly,
                    "가장자리 제스처는 집중 모드에서만",
                    "일반 화면에서는 왼쪽·오른쪽·위쪽 가장자리 입력을 사용하지 않습니다.",
                    ViewerSettings::edgeGesturesFocusOnly,
                  ),
                  SettingRow(
                    ViewerSettingKey.HapticFeedback,
                    "제스처 진동",
                    "바인딩된 기능이 실행되면 짧은 진동으로 알려줍니다.",
                    ViewerSettings::hapticFeedback,
                  ),
                  SettingRow(
                    ViewerSettingKey.ImmersiveSystemBars,
                    "집중 모드에서 시스템 바도 숨기기",
                    "집중 모드에서 상태 표시줄과 내비게이션 바를 함께 숨깁니다.",
                    ViewerSettings::immersiveSystemBars,
                  ),
                ),
              settings = settings,
              onSettingChanged = onSettingChanged,
            )
            settingsSection(
              title = "핀치 확대",
              rows =
                listOf(
                  SettingRow(
                    ViewerSettingKey.PinchZoomMarkdown,
                    "Markdown 글자 크기",
                    "핀치로 본문 글자 크기를 조절합니다.",
                    ViewerSettings::pinchZoomMarkdown,
                  ),
                  SettingRow(
                    ViewerSettingKey.PinchZoomImage,
                    "이미지 확대",
                    "핀치로 이미지 확대율을 조절합니다.",
                    ViewerSettings::pinchZoomImage,
                  ),
                  SettingRow(
                    ViewerSettingKey.PinchZoomPdf,
                    "PDF 확대",
                    "핀치로 현재 PDF 페이지 확대율을 조절합니다.",
                    ViewerSettings::pinchZoomPdf,
                  ),
                  SettingRow(
                    ViewerSettingKey.PinchZoomOffice,
                    "Office 문서 확대",
                    "핀치로 DOCX와 PPTX 문서의 확대율을 조절합니다.",
                    ViewerSettings::pinchZoomOffice,
                  ),
                  SettingRow(
                    ViewerSettingKey.PinchZoomHtml,
                    "HTML 페이지 확대",
                    "핀치로 로컬 HTML 페이지를 확대하거나 축소합니다.",
                    ViewerSettings::pinchZoomHtml,
                  ),
                  SettingRow(
                    ViewerSettingKey.PinchZoomVideo,
                    "영상 화면 맞춤",
                    "영상에서 핀치하면 화면 맞춤과 채우기를 전환합니다.",
                    ViewerSettings::pinchZoomVideo,
                  ),
                ),
              settings = settings,
              onSettingChanged = onSettingChanged,
            )
            settingsSection(
              title = "문서",
              rows =
                listOf(
                  SettingRow(
                    ViewerSettingKey.ShowExternalOpenButton,
                    "외부 앱으로 열기 버튼 표시",
                    "문서 상단 우측에 설치된 다른 앱으로 여는 버튼을 표시합니다.",
                    ViewerSettings::showExternalOpenButton,
                  )
                ),
              settings = settings,
              onSettingChanged = onSettingChanged,
            )
            settingsSection(
              title = "영상",
              rows =
                listOf(
                  SettingRow(
                    ViewerSettingKey.VideoAutoplay,
                    "자동 재생",
                    "영상을 열면 바로 재생합니다.",
                    ViewerSettings::videoAutoplay,
                  ),
                  SettingRow(
                    ViewerSettingKey.VideoRememberPosition,
                    "재생 위치 기억",
                    "다른 문서로 이동했다 돌아오면 마지막 위치부터 시작합니다.",
                    ViewerSettings::videoRememberPosition,
                  ),
                  SettingRow(
                    ViewerSettingKey.VideoKeepScreenOn,
                    "재생 중 화면 켜기",
                    "영상이 재생되는 동안 화면 자동 꺼짐을 막습니다.",
                    ViewerSettings::videoKeepScreenOn,
                  ),
                ),
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
      title = { Text("제스처 바인딩 변경") },
      text = {
        Text(
          "‘${pending.trigger.displayName}’은 현재 ‘${pending.conflictingAction.displayName}’ 기능에 지정되어 있습니다. " +
            "이 제스처를 ‘${pending.action.displayName}’ 기능으로 변경하시겠습니까?\n\n" +
            "변경하면 ‘${pending.conflictingAction.displayName}’은 ‘지정 안 함’으로 변경되며 다시 설정해야 합니다."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onGestureBindingChanged(pending.action, pending.trigger)
            pendingBinding = null
          }
        ) {
          Text("변경")
        }
      },
      dismissButton = { TextButton(onClick = { pendingBinding = null }) { Text("취소") } },
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
      Text(action.displayName, style = MaterialTheme.typography.bodyLarge)
      Text(
        action.description,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
    }
    Box {
      TextButton(onClick = { expanded = true }) {
        Text(selected.displayName)
        Icon(Icons.Outlined.ArrowDropDown, contentDescription = "제스처 선택")
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        GestureTrigger.entries.forEach { trigger ->
          DropdownMenuItem(
            text = { Text(trigger.displayName) },
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

private fun androidx.compose.foundation.lazy.LazyListScope.settingsSection(
  title: String,
  rows: List<SettingRow>,
  settings: ViewerSettings,
  onSettingChanged: (ViewerSettingKey, Boolean) -> Unit,
) {
  item { SettingsSectionTitle(title) }
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

private val ViewerAction.displayName: String
  get() =
    when (this) {
      ViewerAction.ToggleFocus -> "집중 모드 토글"
      ViewerAction.ToggleExplorer -> "탐색기 열기·닫기"
      ViewerAction.ToggleDetails -> "목차·문서 정보 열기·닫기"
      ViewerAction.ToggleControls -> "문서 도구 열기·닫기"
      ViewerAction.OpenExternalApp -> "외부 앱으로 열기"
    }

private val ViewerAction.description: String
  get() =
    when (this) {
      ViewerAction.ToggleFocus -> "문서만 표시하는 집중 모드로 전환합니다."
      ViewerAction.ToggleExplorer -> "왼쪽 탐색기 패널을 전환합니다."
      ViewerAction.ToggleDetails -> "오른쪽 목차·문서 정보 패널을 전환합니다."
      ViewerAction.ToggleControls -> "위쪽 문서 도구 패널을 전환합니다."
      ViewerAction.OpenExternalApp -> "현재 문서를 설치된 다른 앱으로 엽니다."
    }

private val GestureTrigger.displayName: String
  get() =
    when (this) {
      GestureTrigger.None -> "지정 안 함"
      GestureTrigger.TripleTap -> "빠른 3회 탭"
      GestureTrigger.ThreeFingerTap -> "세 손가락 동시 탭"
      GestureTrigger.EdgeLeftIn -> "왼쪽 가장자리 안쪽으로 스와이프"
      GestureTrigger.EdgeRightIn -> "오른쪽 가장자리 안쪽으로 스와이프"
      GestureTrigger.EdgeTopIn -> "위쪽 가장자리 안쪽으로 스와이프"
    }
