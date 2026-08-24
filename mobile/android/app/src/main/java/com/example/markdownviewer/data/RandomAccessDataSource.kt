package com.example.markdownviewer.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.EOFException
import java.io.IOException

@UnstableApi
class RandomAccessDataSource(
  private val opener: (String) -> RandomAccessDocument?,
  private val openErrorMessage: String = "SMB 영상 스트림을 열 수 없습니다.",
  private val positionErrorMessage: String = "요청한 재생 위치가 파일 범위를 벗어났습니다.",
) : BaseDataSource(true) {
  private var document: RandomAccessDocument? = null
  private var currentUri: Uri? = null
  private var position = 0L
  private var bytesRemaining = 0L
  private var transferStarted = false

  @Throws(IOException::class)
  override fun open(dataSpec: DataSpec): Long {
    transferInitializing(dataSpec)
    val opened = opener(dataSpec.uri.toString()) ?: throw IOException(openErrorMessage)
    if (dataSpec.position > opened.length) {
      opened.close()
      throw EOFException(positionErrorMessage)
    }
    document = opened
    currentUri = dataSpec.uri
    position = dataSpec.position
    val available = (opened.length - position).coerceAtLeast(0)
    bytesRemaining =
      if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else minOf(available, dataSpec.length)
    transferStarted(dataSpec)
    transferStarted = true
    return bytesRemaining
  }

  @Throws(IOException::class)
  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length == 0) return 0
    if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
    val requested = minOf(bytesRemaining, length.toLong()).toInt()
    val count = document?.read(position, buffer, offset, requested) ?: return C.RESULT_END_OF_INPUT
    if (count <= 0) return C.RESULT_END_OF_INPUT
    position += count
    bytesRemaining -= count
    bytesTransferred(count)
    return count
  }

  override fun getUri(): Uri? = currentUri

  override fun close() {
    try {
      document?.close()
    } finally {
      document = null
      currentUri = null
      position = 0
      bytesRemaining = 0
      if (transferStarted) {
        transferStarted = false
        transferEnded()
      }
    }
  }

  class Factory(
    private val opener: (String) -> RandomAccessDocument?,
    private val openErrorMessage: String = "SMB 영상 스트림을 열 수 없습니다.",
    private val positionErrorMessage: String = "요청한 재생 위치가 파일 범위를 벗어났습니다.",
  ) : DataSource.Factory {
    override fun createDataSource(): DataSource =
      RandomAccessDataSource(opener, openErrorMessage, positionErrorMessage)
  }
}
