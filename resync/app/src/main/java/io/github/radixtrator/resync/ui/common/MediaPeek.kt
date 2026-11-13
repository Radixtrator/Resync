package io.github.radixtrator.resync.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.theme.Motion

/** The picture held down in a feed, shown over it until the finger lifts. */
@Stable
class MediaPeek {
    var url by mutableStateOf<String?>(null)
        private set

    /** The last url shown, kept while the overlay fades out. */
    internal var lastUrl: String? = null
        private set

    fun show(url: String) {
        lastUrl = url
        this.url = url
    }

    fun hide() {
        url = null
    }
}

/** Set by feeds (subreddits, the front page); elsewhere null, and holding a picture does nothing. */
val LocalMediaPeek = staticCompositionLocalOf<MediaPeek?> { null }

/**
 * The picture a hold on this post's preview peeks at: an image, or a gallery's first. Not videos or links, and not
 * while the preview is blurred, where a stray hold would reveal it: unless it's blurred only for being NSFW and
 * Media › "Unblur NSFW previews" is on, which shows it unblurred. Spoilers stay hidden either way.
 */
@Composable
fun Post.peekUrl(): String? {
    val settings = LocalSettings.current
    if (!settings.holdToPreview) return null
    if (shouldBlur() && (!settings.peekUnblursNsfw || (spoiler && settings.blurSpoilers))) return null
    return when (val m = media) {
        is PostMedia.Image -> m.url
        is PostMedia.Gallery -> m.images.firstOrNull()?.url ?: m.preview?.url
        else -> null
    }
}

/**
 * Hides the peek when the finger lifts (or the gesture is cancelled), and keeps the feed from scrolling under it
 * while it shows. Watches on the Initial pass, so it sees the gesture whatever the clickable below does with it.
 */
internal fun Modifier.peekRelease(peek: MediaPeek): Modifier = pointerInput(peek) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (peek.url != null) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
        peek.hide()
    }
}

/**
 * A preview's click: [onClick] on a tap and, in a feed with a picture to show, a peek at it for as long as it's held.
 * Anywhere else, a plain click.
 */
@Composable
fun Modifier.peekableClick(post: Post, onClick: () -> Unit): Modifier {
    val peek = LocalMediaPeek.current
    val url = if (peek != null) post.peekUrl() else null
    return if (peek == null || url == null) {
        clickable(onClick = onClick)
    } else {
        peekRelease(peek).combinedClickable(onClick = onClick, onLongClick = { peek.show(url) })
    }
}

/** Provides a [MediaPeek] to [content] and draws the held picture over it. */
@Composable
fun MediaPeekHost(content: @Composable () -> Unit) {
    val peek = remember { MediaPeek() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalMediaPeek provides peek) { content() }
        AnimatedVisibility(
            visible = peek.url != null,
            enter = fadeIn(Motion.enter()) + scaleIn(Motion.enter(), initialScale = 0.92f),
            exit = fadeOut(Motion.exit()) + scaleOut(Motion.exit(), targetScale = 0.92f),
        ) {
            val url = peek.url ?: peek.lastUrl ?: return@AnimatedVisibility
            val context = LocalContext.current
            val request = remember(url) { ImageRequest.Builder(context).data(url).build() }
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).safeDrawingPadding().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
