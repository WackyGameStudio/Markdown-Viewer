package com.example.markdownviewer.data

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SizeLimitedInputStreamTest {
  @Test
  fun exactLimitCanBeReadCompletely() {
    val bytes = byteArrayOf(1, 2, 3, 4)
    val stream = SizeLimitedInputStream(ByteArrayInputStream(bytes), bytes.size.toLong())

    assertArrayEquals(bytes, stream.readBytes())
  }

  @Test
  fun readingPastLimitFails() {
    val stream = SizeLimitedInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2)
    val buffer = ByteArray(3)

    assertEquals(2, stream.read(buffer, 0, 2))
    assertThrows(IOException::class.java) { stream.read() }
  }

  @Test
  fun resetRestoresTheConsumedByteCount() {
    val stream = SizeLimitedInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3)
    stream.mark(3)
    assertEquals(1, stream.read())
    stream.reset()

    assertArrayEquals(byteArrayOf(1, 2, 3), stream.readBytes())
  }

  @Test
  fun invalidBufferRangeFailsEvenForZeroLengthRead() {
    val stream = SizeLimitedInputStream(ByteArrayInputStream(byteArrayOf(1)), 1)

    assertThrows(IndexOutOfBoundsException::class.java) {
      stream.read(ByteArray(1), 2, 0)
    }
  }
}
