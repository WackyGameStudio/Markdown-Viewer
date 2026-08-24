package com.example.markdownviewer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.markdownviewer.data.SmbConnectionPreferences
import com.example.markdownviewer.data.SmbDocumentRepository
import com.example.markdownviewer.model.DocumentKind
import com.example.markdownviewer.model.flatten
import org.junit.Assert.assertArrayEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmbRepositoryDeviceTest {
  @Test
  fun savedConnectionListsSupportedDocuments() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val connection = SmbConnectionPreferences(context).connections().firstOrNull()
    assumeNotNull(connection)

    val root = SmbDocumentRepository(context).loadTree(requireNotNull(connection))

    assertTrue(
      "The SMB share connected but no supported documents were discovered.",
      root.flatten().count() > 1,
    )
  }

  @Test
  fun savedConnectionSupportsRandomReads() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val connection = SmbConnectionPreferences(context).connections().firstOrNull()
    assumeNotNull(connection)
    val repository = SmbDocumentRepository(context)
    val root = repository.loadTree(requireNotNull(connection))
    val pdf = root.flatten().firstOrNull { it.kind == DocumentKind.Pdf }
    assumeNotNull(pdf)

    repository.openRandomAccessDocument(requireNotNull(connection), requireNotNull(pdf).uri)
      .use { document ->
        requireNotNull(document)
        val header = ByteArray(5)
        assertTrue(document.read(0, header, 0, header.size) == header.size)
        assertArrayEquals("%PDF-".toByteArray(Charsets.US_ASCII), header)
      }
  }

  @Test
  fun savedConnectionCanMaterializeExternalDocument() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val connection = SmbConnectionPreferences(context).connections().firstOrNull()
    assumeNotNull(connection)
    val repository = SmbDocumentRepository(context)
    val root = repository.loadTree(requireNotNull(connection))
    val pdf = root.flatten().firstOrNull { it.kind == DocumentKind.Pdf }
    assumeNotNull(pdf)

    val externalUri =
      repository.materializeForExternalApp(requireNotNull(connection), requireNotNull(pdf))
    val header = ByteArray(5)
    context.contentResolver.openInputStream(externalUri).use { stream ->
      requireNotNull(stream)
      assertTrue(stream.read(header) == header.size)
    }
    assertArrayEquals("%PDF-".toByteArray(Charsets.US_ASCII), header)
  }
}
