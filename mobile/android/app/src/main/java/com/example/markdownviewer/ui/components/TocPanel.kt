package com.example.markdownviewer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.markdownviewer.R
import com.example.markdownviewer.model.TocItem

@Composable
fun TocPanel(
  items: List<TocItem>,
  onSelect: (TocItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        Text(
          text = stringResource(R.string.toc_page_title),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      HorizontalDivider()
      if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
          Text(stringResource(R.string.toc_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      } else {
        LazyColumn(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
          items(items, key = { it.headingIndex }) { item ->
            Text(
              text = item.text,
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { onSelect(item) }
                  .padding(start = (12 + (item.level - 1) * 12).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
