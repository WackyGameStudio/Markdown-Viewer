package com.example.markdownviewer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.markdownviewer.R
import com.example.markdownviewer.model.DocumentKind
import com.example.markdownviewer.model.DocumentNode

@Composable
fun DocumentTreePanel(
  root: DocumentNode?,
  activeDocument: DocumentNode?,
  expandedFolders: Set<String>,
  isLoading: Boolean,
  isBookmarked: Boolean,
  onSelect: (DocumentNode) -> Unit,
  onRefresh: () -> Unit,
  onToggleBookmark: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.explorer_title),
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (root != null) {
          IconButton(onClick = onRefresh) {
            Icon(
              Icons.Outlined.Refresh,
              contentDescription = stringResource(R.string.explorer_refresh),
            )
          }
          IconButton(onClick = onToggleBookmark) {
            Icon(
              imageVector = if (isBookmarked) Icons.Outlined.BookmarkAdded else Icons.Outlined.Bookmark,
              contentDescription =
                if (isBookmarked) {
                  stringResource(R.string.explorer_remove_bookmark)
                } else {
                  stringResource(R.string.explorer_add_bookmark)
                },
              tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      HorizontalDivider()

      when {
        isLoading ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        root == null ->
          Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
              text = stringResource(R.string.explorer_empty_help),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        else -> {
          val rows = remember(root, expandedFolders) { visibleRows(root, expandedFolders) }
          LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
            items(rows, key = { it.node.uri }) { row ->
              DocumentTreeRow(
                row = row,
                selected = row.node.uri == activeDocument?.uri,
                expanded = row.node.uri in expandedFolders,
                onClick = { onSelect(row.node) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DocumentTreeRow(
  row: TreeRow,
  selected: Boolean,
  expanded: Boolean,
  onClick: () -> Unit,
) {
  val background =
    if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
  Surface(color = background, shape = MaterialTheme.shapes.small) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .height(44.dp)
          .clickable(onClick = onClick)
          .padding(start = (8 + row.depth * 16).dp, end = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (row.node.isFolder) {
        Icon(
          imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Spacer(Modifier.width(18.dp))
      }
      Spacer(Modifier.width(4.dp))
      Icon(
        imageVector = row.node.icon(expanded),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.width(8.dp))
      Text(
        text = row.node.name,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

private data class TreeRow(val node: DocumentNode, val depth: Int)

private fun visibleRows(root: DocumentNode, expanded: Set<String>): List<TreeRow> =
  buildList {
    fun visit(node: DocumentNode, depth: Int) {
      if (!node.isVisibleInTree) return
      add(TreeRow(node, depth))
      if (node.isFolder && node.uri in expanded) {
        node.children.filter(DocumentNode::hasVisibleContent).forEach { visit(it, depth + 1) }
      }
    }
    visit(root, 0)
  }

private fun DocumentNode.hasVisibleContent(): Boolean =
  when {
    !isVisibleInTree -> false
    !isFolder -> true
    else -> children.any(DocumentNode::hasVisibleContent)
  }

private fun DocumentNode.icon(expanded: Boolean): ImageVector =
  when (kind) {
    DocumentKind.Folder -> if (expanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder
    DocumentKind.Markdown -> Icons.Outlined.Description
    DocumentKind.Image -> Icons.Outlined.Image
    DocumentKind.Pdf -> Icons.Outlined.PictureAsPdf
    DocumentKind.Video -> Icons.Outlined.VideoFile
    DocumentKind.Word -> Icons.AutoMirrored.Outlined.TextSnippet
    DocumentKind.Presentation -> Icons.Outlined.Slideshow
    DocumentKind.Html -> Icons.Outlined.Language
    DocumentKind.Resource -> Icons.Outlined.Description
  }
