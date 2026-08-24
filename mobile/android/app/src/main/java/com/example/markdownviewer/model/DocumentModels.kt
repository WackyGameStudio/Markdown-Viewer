package com.example.markdownviewer.model

enum class DocumentKind {
  Folder,
  Markdown,
  Image,
  Pdf,
  Video,
  Word,
  Presentation,
  Html,
  Resource,
}

data class DocumentNode(
  val uri: String,
  val name: String,
  val relativePath: String,
  val kind: DocumentKind,
  val sizeBytes: Long = 0,
  val children: List<DocumentNode> = emptyList(),
) {
  val isFolder: Boolean
    get() = kind == DocumentKind.Folder

  val isVisibleInTree: Boolean
    get() = kind != DocumentKind.Resource

  val extension: String
    get() = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

  val isLegacyOffice: Boolean
    get() = extension == "doc" || extension == "ppt"
}

data class FolderReference(
  val uri: String,
  val name: String,
)

data class TocItem(
  val text: String,
  val level: Int,
  val headingIndex: Int,
)

fun DocumentNode.flatten(): Sequence<DocumentNode> =
  sequence {
    yield(this@flatten)
    children.forEach { yieldAll(it.flatten()) }
  }
