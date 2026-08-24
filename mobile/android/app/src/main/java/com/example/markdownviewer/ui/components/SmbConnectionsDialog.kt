package com.example.markdownviewer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.markdownviewer.R
import com.example.markdownviewer.data.SmbConnectionConfig

@Composable
fun SmbConnectionsDialog(
  connections: List<SmbConnectionConfig>,
  onConnect: (SmbConnectionConfig) -> Unit,
  onDelete: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var draft by remember { mutableStateOf(connections.firstOrNull() ?: SmbConnectionConfig.defaultDraft()) }
  var portText by remember { mutableStateOf(draft.port.toString()) }
  var validationMessage by remember { mutableStateOf<String?>(null) }
  var pendingDelete by remember { mutableStateOf<SmbConnectionConfig?>(null) }
  val invalidInputMessage = stringResource(R.string.smb_invalid_input)
  val localizedValidationMessages =
    mapOf(
      "서버 주소를 입력해 주세요." to stringResource(R.string.smb_validation_host_required),
      "서버 주소에는 경로를 포함할 수 없습니다." to stringResource(R.string.smb_validation_host_path),
      "포트는 1~65535 사이여야 합니다." to stringResource(R.string.smb_validation_port),
      "공유 이름을 입력해 주세요." to stringResource(R.string.smb_validation_share_required),
      "공유 이름에는 경로 구분자를 포함할 수 없습니다." to
        stringResource(R.string.smb_validation_share_path),
      "SMB 연결 식별자가 올바르지 않습니다." to stringResource(R.string.smb_validation_id),
      "상위 폴더(..) 경로는 사용할 수 없습니다." to
        stringResource(R.string.smb_validation_parent_path),
      "폴더 경로에 제어 문자를 사용할 수 없습니다." to
        stringResource(R.string.smb_validation_control_character),
    )

  LaunchedEffect(connections) {
    val refreshed = connections.firstOrNull { it.id == draft.id }
    if (refreshed != null) {
      draft = refreshed
      portText = refreshed.port.toString()
    }
  }

  fun select(config: SmbConnectionConfig) {
    draft = config
    portText = config.port.toString()
    validationMessage = null
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
      Surface(
        modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f).widthIn(max = 720.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Outlined.Storage, contentDescription = null)
            Text(
              stringResource(R.string.smb_title),
              modifier = Modifier.weight(1f).padding(start = 10.dp),
              style = MaterialTheme.typography.headlineSmall,
            )
            IconButton(onClick = onDismiss) {
              Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.smb_close))
            }
          }
          HorizontalDivider()
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            item {
              SmbNotice(stringResource(R.string.smb_notice))
            }
            if (connections.isNotEmpty()) {
              item {
                Text(
                  stringResource(R.string.smb_saved_connections),
                  modifier = Modifier.padding(horizontal = 24.dp),
                  color = MaterialTheme.colorScheme.primary,
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              item {
                LazyRow(
                  contentPadding = PaddingValues(horizontal = 20.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  items(connections, key = SmbConnectionConfig::id) { connection ->
                    val selected = connection.id == draft.id
                    Surface(
                      modifier = Modifier.widthIn(min = 150.dp, max = 260.dp).clickable { select(connection) },
                      color =
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                      shape = MaterialTheme.shapes.medium,
                    ) {
                      Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                          connection.name,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                          "\\\\${connection.host}\\${connection.share}",
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          style = MaterialTheme.typography.bodySmall,
                        )
                      }
                    }
                  }
                  item {
                    TextButton(onClick = { select(SmbConnectionConfig.defaultDraft()) }) {
                      Icon(Icons.Outlined.Add, contentDescription = null)
                      Text(
                        stringResource(R.string.smb_new_connection),
                        modifier = Modifier.padding(start = 4.dp),
                      )
                    }
                  }
                }
              }
            }
            item {
              SmbTextField(
                stringResource(R.string.smb_connection_name),
                draft.name,
                { draft = draft.copy(name = it) },
              )
            }
            item {
              SmbTextField(
                stringResource(R.string.smb_server_address),
                draft.host,
                { draft = draft.copy(host = it) },
              )
            }
            item {
              SmbTextField(
                label = stringResource(R.string.smb_port),
                value = portText,
                onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                keyboardType = KeyboardType.Number,
              )
            }
            item {
              SmbTextField(
                stringResource(R.string.smb_share_name),
                draft.share,
                { draft = draft.copy(share = it) },
              )
            }
            item {
              SmbTextField(
                stringResource(R.string.smb_start_folder),
                draft.initialPath,
                { draft = draft.copy(initialPath = it) },
                supportingText = stringResource(R.string.smb_start_folder_help),
              )
            }
            item {
              Text(
                stringResource(R.string.smb_account_section),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
              )
            }
            item {
              SmbTextField(
                stringResource(R.string.smb_username),
                draft.username,
                { draft = draft.copy(username = it) },
                supportingText = stringResource(R.string.smb_username_help),
              )
            }
            item {
              SmbTextField(
                label = stringResource(R.string.smb_password),
                value = draft.password,
                onValueChange = { draft = draft.copy(password = it) },
                keyboardType = KeyboardType.Password,
                password = true,
              )
            }
            item {
              SmbTextField(
                stringResource(R.string.smb_domain),
                draft.domain,
                { draft = draft.copy(domain = it) },
              )
            }
            item {
              SmbSwitchRow(
                title = stringResource(R.string.smb_require_signing),
                description = stringResource(R.string.smb_require_signing_description),
                checked = draft.requireSigning,
                onCheckedChange = { draft = draft.copy(requireSigning = it) },
              )
            }
            item {
              SmbSwitchRow(
                title = stringResource(R.string.smb_require_encryption),
                description = stringResource(R.string.smb_require_encryption_description),
                checked = draft.requireEncryption,
                onCheckedChange = { draft = draft.copy(requireEncryption = it) },
              )
            }
            validationMessage?.let { message ->
              item {
                Text(
                  message,
                  modifier = Modifier.padding(horizontal = 24.dp),
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
            item {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (connections.any { it.id == draft.id }) {
                  TextButton(onClick = { pendingDelete = draft }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Text(
                      stringResource(R.string.common_delete),
                      modifier = Modifier.padding(start = 4.dp),
                    )
                  }
                }
                Button(
                  onClick = {
                    val validated =
                      runCatching { draft.copy(port = portText.toIntOrNull() ?: 0).validated() }
                        .getOrElse { failure ->
                          validationMessage =
                            localizedValidationMessages[failure.message] ?: failure.message ?: invalidInputMessage
                          return@Button
                        }
                    onConnect(validated)
                    onDismiss()
                  }
                ) {
                  Text(stringResource(R.string.smb_save_and_connect))
                }
              }
            }
          }
        }
      }
    }
  }

  val deleting = pendingDelete
  if (deleting != null) {
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text(stringResource(R.string.smb_delete_title)) },
      text = { Text(stringResource(R.string.smb_delete_message, deleting.name)) },
      confirmButton = {
        TextButton(
          onClick = {
            onDelete(deleting.id)
            pendingDelete = null
            select(connections.firstOrNull { it.id != deleting.id } ?: SmbConnectionConfig.defaultDraft())
          }
        ) {
          Text(stringResource(R.string.common_delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDelete = null }) {
          Text(stringResource(R.string.common_cancel))
        }
      },
    )
  }
}

@Composable
private fun SmbNotice(message: String) {
  Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.medium,
  ) {
    Text(
      message,
      modifier = Modifier.padding(16.dp),
      color = MaterialTheme.colorScheme.onSecondaryContainer,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
private fun SmbTextField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  supportingText: String? = null,
  keyboardType: KeyboardType = KeyboardType.Text,
  password: Boolean = false,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    supportingText = supportingText?.let { text -> ({ Text(text) }) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
  )
}

@Composable
private fun SmbSwitchRow(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(role = Role.Switch) { onCheckedChange(!checked) }
        .padding(horizontal = 24.dp, vertical = 8.dp),
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
