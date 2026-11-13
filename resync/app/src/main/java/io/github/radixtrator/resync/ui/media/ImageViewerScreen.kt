package io.github.radixtrator.resync.ui.media

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import io.github.radixtrator.resync.data.MediaDownloads
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.DismissibleMedia
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Sync's image viewer, with its control bar (download, host and URL, resolution, file size, then Comments / Open in
 * browser / Copy / Share). Media › "Close images and videos by": a single tap closes it and swiping up slides the bar in from
 * the bottom (a downward fling closes it too, with "Swipe to close"); or swiping up or down closes it and a tap shows
 * and hides the bar.
 */
@Composable
fun ImageViewerScreen(urls: List<String>, start: Int, postId: String?, downloads: MediaDownloads, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { urls.size }
    // Zoomed pages own their drags (panning), so swipe-to-dismiss is off while zoomed. With the info
    // sheet open, a tap or Back closes the sheet instead.
    val zoomed = remember { mutableStateMapOf<Int, Boolean>() }
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    val download = rememberDownloader(downloads)
    val settings = LocalSettings.current
    val tapCloses = settings.tapToClose
    BackHandler(enabled = infoOpen) { infoOpen = false }
    DismissibleMedia(
        onDismiss = onClose,
        // Closing by tap, the open bar takes vertical drags; closing by swipe, a swipe still closes with it showing.
        enabled = zoomed[pager.currentPage] != true && (!tapCloses || !infoOpen),
        dismissOnDrag = !tapCloses || settings.swipeToClose,
        // Without onSwipeUp, a fling either way closes.
        onSwipeUp = if (tapCloses) ({ infoOpen = true }) else null,
        // The page it was opened from, blurred.
        backdrop = { BlurredScreenBackdrop() },
    ) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(state = pager, beyondViewportPageCount = 1, pageSpacing = 16.dp, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(
                    urls[page],
                    onTap = {
                        when {
                            !tapCloses -> infoOpen = !infoOpen
                            infoOpen -> infoOpen = false
                            else -> onClose()
                        }
                    },
                    onZoomChange = { zoomed[page] = it },
                )
            }
            if (urls.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),
                )
            }
            AnimatedVisibility(
                visible = infoOpen,
                enter = slideInVertically(Motion.enter()) { it },
                exit = slideOutVertically(Motion.exit()) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val url = urls[pager.currentPage]
                ImageInfoSheet(url, postId, downloads, onDownload = { download(url) }, onClose = { infoOpen = false })
            }
        }
    }
}

@Composable
private fun ImageInfoSheet(url: String, postId: String?, downloads: MediaDownloads, onDownload: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var details by remember(url) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(url) {
        val cached = cachedImageInfo(context, url)
        val bytes = cached?.second ?: downloads.contentLength(url)
        details = listOfNotNull(
            cached?.first?.let { "Image resolution: ${it.width}x${it.height}" },
            bytes?.let { "File size: " + String.format(Locale.US, "%.2fMB", it / 1_048_576.0) },
        )
    }
    Column(Modifier.fillMaxWidth().background(MediaPanelColor)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onDownload) { Icon(AppIcons.Download, "Download", tint = Color.White) }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
        MediaLinkInfo(url, details)
        MediaActionBar(url, postId)
    }
}

/** The original's resolution and file size from Coil's disk cache; the viewer itself decodes a smaller copy. */
private suspend fun cachedImageInfo(context: Context, url: String): Pair<IntSize, Long>? = withContext(Dispatchers.IO) {
    runCatching {
        SingletonImageLoader.get(context).diskCache?.openSnapshot(url)?.use { snapshot ->
            val file = snapshot.data.toFile()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth > 0) IntSize(bounds.outWidth, bounds.outHeight) to file.length() else null
        }
    }.getOrNull()
}

/** Pinch/double-tap zoom that leaves single-finger swipes to the pager while not zoomed. */
@Composable
private fun ZoomableImage(url: String, onTap: () -> Unit, onZoomChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val zoom = remember { ZoomState(onZoomChange) }
    // Original size, so deep zoom shows real pixels rather than a screen-sized downsample.
    val request = remember(url) { coil3.request.ImageRequest.Builder(context).data(url).size(coil3.size.Size.ORIGINAL).build() }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .zoomGestures(zoom, onTap)
            .zoomTransform(zoom),
    ) {
        val loadState by painter.state.collectAsState()
        when (loadState) {
            is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            is AsyncImagePainter.State.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Couldn't load image", color = Color.White) }
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
        }
    }
}
