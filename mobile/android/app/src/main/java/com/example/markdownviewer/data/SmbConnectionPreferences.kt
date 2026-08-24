package com.example.markdownviewer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

class SmbConnectionPreferences(context: Context) {
  private val preferences =
    context.getSharedPreferences("markdown_viewer_smb_connections", Context.MODE_PRIVATE)
  private val secretStore = AndroidSecretStore()

  fun connections(): List<SmbConnectionConfig> {
    val raw = preferences.getString(KEY_CONNECTIONS, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
          for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val host = item.optString("host")
            val share = item.optString("share")
            if (id.isBlank() || host.isBlank() || share.isBlank()) continue
            add(
              SmbConnectionConfig(
                id = id,
                name = item.optString("name", share),
                host = host,
                port = item.optInt("port", DEFAULT_SMB_PORT),
                share = share,
                initialPath = item.optString("initialPath"),
                username = item.optString("username"),
                password = secretStore.decrypt(item.optString("password")),
                domain = item.optString("domain"),
                requireSigning = item.optBoolean("requireSigning", true),
                requireEncryption = item.optBoolean("requireEncryption", false),
              ).validated()
            )
          }
        }
      }
      .getOrDefault(emptyList())
  }

  fun find(id: String): SmbConnectionConfig? = connections().firstOrNull { it.id == id }

  fun save(config: SmbConnectionConfig): List<SmbConnectionConfig> {
    val normalized = config.validated()
    val current = connections()
    val existingIndex = current.indexOfFirst { it.id == normalized.id }
    val next = current.toMutableList()
    if (existingIndex >= 0) next[existingIndex] = normalized else next += normalized
    write(next)
    return next
  }

  fun delete(id: String): List<SmbConnectionConfig> =
    connections().filterNot { it.id == id }.also(::write)

  private fun write(connections: List<SmbConnectionConfig>) {
    val array = JSONArray()
    connections.forEach { connection ->
      array.put(
        JSONObject()
          .put("id", connection.id)
          .put("name", connection.name)
          .put("host", connection.host)
          .put("port", connection.port)
          .put("share", connection.share)
          .put("initialPath", connection.initialPath)
          .put("username", connection.username)
          .put("password", secretStore.encrypt(connection.password))
          .put("domain", connection.domain)
          .put("requireSigning", connection.requireSigning)
          .put("requireEncryption", connection.requireEncryption)
      )
    }
    preferences.edit { putString(KEY_CONNECTIONS, array.toString()) }
  }

  private companion object {
    const val KEY_CONNECTIONS = "connections"
  }
}

private class AndroidSecretStore {
  fun encrypt(value: String): String {
    if (value.isEmpty()) return ""
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    val payload = ByteArray(1 + cipher.iv.size + encrypted.size)
    payload[0] = cipher.iv.size.toByte()
    cipher.iv.copyInto(payload, destinationOffset = 1)
    encrypted.copyInto(payload, destinationOffset = 1 + cipher.iv.size)
    return Base64.encodeToString(payload, Base64.NO_WRAP)
  }

  fun decrypt(value: String): String {
    if (value.isEmpty()) return ""
    return runCatching {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.isNotEmpty())
        val ivLength = payload[0].toInt() and 0xff
        require(ivLength in 12..32 && payload.size > 1 + ivLength)
        val iv = payload.copyOfRange(1, 1 + ivLength)
        val encrypted = payload.copyOfRange(1 + ivLength, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
      }
      .getOrDefault("")
  }

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
    generator.init(
      KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build()
    )
    return generator.generateKey()
  }

  private companion object {
    const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    const val KEY_ALIAS = "markdown_viewer_smb_passwords_v1"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
  }
}
