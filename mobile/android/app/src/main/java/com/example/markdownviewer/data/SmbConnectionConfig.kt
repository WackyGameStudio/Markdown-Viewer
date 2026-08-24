package com.example.markdownviewer.data

import android.net.Uri
import androidx.core.net.toUri
import java.util.UUID

data class SmbConnectionConfig(
  val id: String,
  val name: String,
  val host: String,
  val port: Int = DEFAULT_SMB_PORT,
  val share: String,
  val initialPath: String = "",
  val username: String = "",
  val password: String = "",
  val domain: String = "",
  val requireSigning: Boolean = true,
  val requireEncryption: Boolean = false,
) {
  fun validated(): SmbConnectionConfig {
    val normalizedHost = host.trim().removeSurrounding("[", "]")
    require(normalizedHost.isNotBlank()) { "서버 주소를 입력해 주세요." }
    require(normalizedHost.none { it == '/' || it == '\\' || it.isISOControl() }) {
      "서버 주소에는 경로를 포함할 수 없습니다."
    }
    require(port in 1..65_535) { "포트는 1~65535 사이여야 합니다." }

    val normalizedShare = share.trim()
    require(normalizedShare.isNotBlank()) { "공유 이름을 입력해 주세요." }
    require(normalizedShare.none { it == '/' || it == '\\' || it.isISOControl() }) {
      "공유 이름에는 경로 구분자를 포함할 수 없습니다."
    }

    val normalizedPath = normalizeSmbPath(initialPath)
    val normalizedId = id.ifBlank { UUID.randomUUID().toString() }
    require(ID_PATTERN.matches(normalizedId)) { "SMB 연결 식별자가 올바르지 않습니다." }

    return copy(
      id = normalizedId,
      name = name.trim().ifBlank { normalizedShare },
      host = normalizedHost,
      share = normalizedShare,
      initialPath = normalizedPath,
      username = username.trim(),
      domain = domain.trim(),
    )
  }

  val uncPath: String
    get() =
      buildString {
        append("\\\\")
        append(host)
        append('\\')
        append(share)
        if (initialPath.isNotBlank()) {
          append('\\')
          append(initialPath)
        }
      }

  companion object {
    fun defaultDraft(): SmbConnectionConfig =
      SmbConnectionConfig(
        id = UUID.randomUUID().toString(),
        name = "n100-share",
        host = "100.69.138.65",
        share = "n100-share",
      )

    private val ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
  }
}

data class SmbDocumentLocation(
  val connectionId: String,
  val path: String,
)

object SmbDocumentUri {
  const val SCHEME = "smb-doc"

  fun build(connectionId: String, path: String = ""): String {
    val builder = Uri.Builder().scheme(SCHEME).authority(connectionId)
    normalizeSmbPath(path)
      .split('\\')
      .filter(String::isNotEmpty)
      .forEach(builder::appendPath)
    return builder.build().toString()
  }

  fun parse(value: String): SmbDocumentLocation? {
    val uri = runCatching { value.toUri() }.getOrNull() ?: return null
    if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
    val connectionId = uri.authority?.takeIf { it.isNotBlank() } ?: return null
    val path =
      runCatching { normalizeSmbPath(uri.pathSegments.joinToString("\\")) }.getOrNull()
        ?: return null
    return SmbDocumentLocation(connectionId = connectionId, path = path)
  }

  fun isSmb(value: String): Boolean = parse(value) != null
}

internal fun normalizeSmbPath(value: String): String {
  val result = mutableListOf<String>()
  value.replace('/', '\\').split('\\').forEach { rawPart ->
    val part = rawPart.trim()
    when {
      part.isEmpty() || part == "." -> Unit
      part == ".." -> throw IllegalArgumentException("상위 폴더(..) 경로는 사용할 수 없습니다.")
      part.any { it.isISOControl() } ->
        throw IllegalArgumentException("폴더 경로에 제어 문자를 사용할 수 없습니다.")
      else -> result += part
    }
  }
  return result.joinToString("\\")
}

const val DEFAULT_SMB_PORT = 445
