package com.example.markdownviewer.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.compose.ContentFrame
import com.example.markdownviewer.data.ViewerSettings
import com.example.markdownviewer.data.RandomAccessDataSource
import com.example.markdownviewer.data.RandomAccessDocument
import com.example.markdownviewer.data.SmbDocumentUri
import com.example.markdownviewer.model.DocumentNode
import com.example.markdownviewer.model.GestureTrigger
import com.example.markdownviewer.ui.nativeDocumentGestures
import java.util.Locale
import kotlinx.coroutines.delay
import org.json.JSONObject

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
  document: DocumentNode,
  initialViewState: String?,
  settings: ViewerSettings,
  focusMode: Boolean,
  controlsVisible: Boolean,
  onViewStateChanged: (activePath: String, serializedState: String) -> Unit,
  onGestureTrigger: (GestureTrigger) -> Unit,
  openRandomAccessDocument: (String) -> RandomAccessDocument?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val hostView = LocalView.current
  val restoredPosition =
    if (settings.videoRememberPosition) videoPositionFromState(initialViewState) else 0L
  val player =
    remember(document.uri) {
      ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(AudioAttributes.DEFAULT, true)
        setHandleAudioBecomingNoisy(true)
        val mediaItem = MediaItem.fromUri(document.uri.toUri())
        if (SmbDocumentUri.isSmb(document.uri)) {
          setMediaSource(
            ProgressiveMediaSource.Factory(RandomAccessDataSource.Factory(openRandomAccessDocument))
              .createMediaSource(mediaItem)
          )
        } else {
          setMediaItem(mediaItem)
        }
        if (restoredPosition > 0) seekTo(restoredPosition)
        prepare()
        playWhenReady = settings.videoAutoplay
      }
    }
  var isPlaying by remember { mutableStateOf(player.isPlaying) }
  var playbackState by remember { mutableIntStateOf(player.playbackState) }
  var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
  var position by remember { mutableLongStateOf(restoredPosition) }
  var error by remember { mutableStateOf<String?>(null) }
  var localControlsVisible by remember { mutableStateOf(true) }
  var fillScreen by remember { mutableStateOf(false) }
  var pinchAccumulator by remember { mutableFloatStateOf(1f) }
  val transformState =
    rememberTransformableState { _, zoomChange, _, _ ->
      if (!settings.pinchZoomVideo) return@rememberTransformableState
      pinchAccumulator *= zoomChange
      when {
        pinchAccumulator >= 1.16f -> {
          fillScreen = true
          pinchAccumulator = 1f
        }
        pinchAccumulator <= 0.86f -> {
          fillScreen = false
          pinchAccumulator = 1f
        }
      }
    }

  fun savePosition() {
    if (!settings.videoRememberPosition) return
    onViewStateChanged(
      document.relativePath,
      JSONObject().put("kind", "video").put("positionMs", player.currentPosition.coerceAtLeast(0L)).toString(),
    )
  }

  DisposableEffect(player) {
    val listener =
      object : Player.Listener {
        override fun onIsPlayingChanged(value: Boolean) {
          isPlaying = value
        }

        override fun onPlaybackStateChanged(state: Int) {
          playbackState = state
          duration = player.duration.coerceAtLeast(0L)
        }

        override fun onPlayerError(playbackError: PlaybackException) {
          error = playbackError.errorCodeName
        }
      }
    player.addListener(listener)
    onDispose {
      savePosition()
      player.removeListener(listener)
      player.release()
    }
  }

  DisposableEffect(lifecycleOwner, player) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
          savePosition()
          player.pause()
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  DisposableEffect(settings.videoKeepScreenOn, isPlaying, hostView) {
    hostView.keepScreenOn = settings.videoKeepScreenOn && isPlaying
    onDispose { hostView.keepScreenOn = false }
  }

  LaunchedEffect(player, settings.videoRememberPosition) {
    while (true) {
      position = player.currentPosition.coerceAtLeast(0L)
      duration = player.duration.coerceAtLeast(0L)
      if (settings.videoRememberPosition && isPlaying) savePosition()
      delay(1_000)
    }
  }

  LaunchedEffect(isPlaying, controlsVisible) {
    if (isPlaying && !controlsVisible) {
      delay(2_500)
      localControlsVisible = false
    }
  }

  Box(
    modifier =
      modifier.background(Color.Black)
        .nativeDocumentGestures(settings, focusMode, onGestureTrigger)
        .transformable(transformState, enabled = settings.pinchZoomVideo)
        .clickable { localControlsVisible = !localControlsVisible },
  ) {
    ContentFrame(
      player = player,
      modifier = Modifier.fillMaxSize(),
      contentScale = if (fillScreen) ContentScale.Crop else ContentScale.Fit,
      shutter = { Box(Modifier.fillMaxSize().background(Color.Black)) },
    )

    if (playbackState == Player.STATE_BUFFERING) {
      CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
    }

    if (error != null) {
      Text(
        text = "영상을 재생할 수 없습니다: $error",
        modifier =
          Modifier.align(Alignment.Center)
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
            .padding(16.dp),
        color = MaterialTheme.colorScheme.onErrorContainer,
      )
    }

    if (controlsVisible || localControlsVisible) {
      VideoControls(
        title = document.name,
        isPlaying = isPlaying,
        position = position,
        duration = duration,
        fillScreen = fillScreen,
        onTogglePlayback = { if (player.isPlaying) player.pause() else player.play() },
        onSeekBack = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L)) },
        onSeekForward = {
          val target = player.currentPosition + 10_000
          player.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
        },
        onSeek = { player.seekTo(it) },
        onToggleFit = { fillScreen = !fillScreen },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}

@Composable
private fun VideoControls(
  title: String,
  isPlaying: Boolean,
  position: Long,
  duration: Long,
  fillScreen: Boolean,
  onTogglePlayback: () -> Unit,
  onSeekBack: () -> Unit,
  onSeekForward: () -> Unit,
  onSeek: (Long) -> Unit,
  onToggleFit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.72f)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(title, color = Color.White, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    Slider(
      value = position.coerceAtMost(duration.coerceAtLeast(1L)).toFloat(),
      onValueChange = { onSeek(it.toLong()) },
      valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
      modifier = Modifier.fillMaxWidth(),
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onSeekBack) {
        Icon(Icons.Outlined.Replay10, contentDescription = "10초 뒤로", tint = Color.White)
      }
      IconButton(onClick = onTogglePlayback, modifier = Modifier.size(56.dp)) {
        Icon(
          if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
          contentDescription = if (isPlaying) "일시정지" else "재생",
          modifier = Modifier.size(34.dp),
          tint = Color.White,
        )
      }
      IconButton(onClick = onSeekForward) {
        Icon(Icons.Outlined.Forward10, contentDescription = "10초 앞으로", tint = Color.White)
      }
      Text(
        "${formatDuration(position)} / ${formatDuration(duration)}",
        modifier = Modifier.padding(horizontal = 8.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
      )
      IconButton(onClick = onToggleFit) {
        Icon(
          if (fillScreen) Icons.Outlined.FitScreen else Icons.Outlined.Fullscreen,
          contentDescription = if (fillScreen) "화면에 맞춤" else "화면 채우기",
          tint = Color.White,
        )
      }
    }
  }
}

internal fun videoPositionFromState(serializedState: String?): Long {
  if (serializedState.isNullOrBlank()) return 0L
  if (!VIDEO_KIND_PATTERN.containsMatchIn(serializedState)) return 0L
  return VIDEO_POSITION_PATTERN.find(serializedState)
    ?.groupValues
    ?.getOrNull(1)
    ?.toLongOrNull()
    ?.coerceAtLeast(0L)
    ?: 0L
}

private val VIDEO_KIND_PATTERN = Regex("\\\"kind\\\"\\s*:\\s*\\\"video\\\"")
private val VIDEO_POSITION_PATTERN = Regex("\\\"positionMs\\\"\\s*:\\s*(-?\\d+)")

private fun formatDuration(milliseconds: Long): String {
  val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000
  val hours = totalSeconds / 3_600
  val minutes = totalSeconds % 3_600 / 60
  val seconds = totalSeconds % 60
  return if (hours > 0) {
    String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
  }
}
