package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.data.CommentGif

/** A comment body with its GIFs taken out of the text, and the GIFs to show under it. */
data class GifBody(val html: String, val gifs: List<CommentGif>)

private val ANCHOR = Regex("""<a\s+href="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
private val IMG = Regex("""<img\b[^>]*>""")
private val TAG = Regex("<[^>]*>")
private val EMPTY_PARAGRAPH = Regex("""<p>\s*</p>""")

/**
 * Splits [html] into text and GIF previews. The comment's own GIFs ([gifs], from media_metadata) come out of the
 * text entirely, since Reddit only leaves a link or an image tag there. A bare link to a GIF (the link's text is
 * its URL) is swapped for a preview as well; a GIF link with words of its own keeps them and also gets a preview.
 */
fun gifBody(html: String, gifs: List<CommentGif>): GifBody {
    val previews = gifs.toMutableList()
    val text = ANCHOR.replace(html) { m ->
        val href = m.groupValues[1].replace("&amp;", "&")
        val inner = m.groupValues[2]
        val label = inner.replace(TAG, "").replace("&amp;", "&").trim()
        val own = gifs.any { g -> href == g.page || href == g.url || g.id in href || g.id == label }
        if (own) return@replace ""
        val gif = gifUrl(href) ?: return@replace m.value
        if (previews.none { it.url == gif }) previews += CommentGif(id = gif, url = gif)
        if (label == href || label.isEmpty()) "" else m.value
    }
    // An upload can also sit in the text as a bare image tag.
    val cleaned = IMG.replace(text) { m -> if (gifs.any { g -> g.url in m.value.replace("&amp;", "&") || g.id in m.value }) "" else m.value }
    return GifBody(EMPTY_PARAGRAPH.replace(cleaned, ""), previews)
}

private val gifBodies = android.util.LruCache<Pair<String, List<CommentGif>>, GifBody>(600)

/** [gifBody], kept for bodies seen recently: rows scrolling back in, and ones a comment page prepared ahead. */
fun cachedGifBody(html: String, gifs: List<CommentGif>): GifBody {
    val key = html to gifs
    return gifBodies[key] ?: gifBody(html, gifs).also { gifBodies.put(key, it) }
}

/** The animated image a link points at, if it's a GIF: direct .gif links, Giphy pages and imgur .gifv. */
fun gifUrl(link: String): String? {
    if (!link.startsWith("http")) return null
    val host = link.substringAfter("://").substringBefore('/').substringBefore('?').lowercase()
    val path = link.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
    return when {
        // giphy.com/gifs/some-words-ID, and media.giphy.com/media/ID/giphy.gif
        host == "giphy.com" || host == "www.giphy.com" ->
            path.removePrefix("gifs/").takeIf { path.startsWith("gifs/") }?.trimEnd('/')?.substringAfterLast('-')
                ?.takeIf { it.isNotEmpty() }?.let { "https://i.giphy.com/$it.gif" }
        host.endsWith("giphy.com") && path.startsWith("media/") ->
            path.split('/').getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { "https://i.giphy.com/$it.gif" }
        host.endsWith("imgur.com") && path.endsWith(".gifv", ignoreCase = true) ->
            "https://i.imgur.com/${path.substringAfterLast('/').dropLast(5)}.gif"
        // preview.redd.it serves a still frame unless asked otherwise; i.redd.it has the GIF itself.
        host == "preview.redd.it" && path.endsWith(".gif", ignoreCase = true) -> "https://i.redd.it/${path.substringAfterLast('/')}"
        path.endsWith(".gif", ignoreCase = true) -> link
        else -> null
    }
}

/** A comment's GIFs, playing, each at most 280dp either way; tapping one opens it in the image viewer. */
@Composable
fun CommentGifs(gifs: List<CommentGif>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (gifs.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        gifs.forEachIndexed { i, gif ->
            // Giphy pages and links don't say how big the GIF is; it's square until it loads.
            var ratio by remember(gif.url) {
                mutableFloatStateOf(if (gif.width > 0 && gif.height > 0) gif.width.toFloat() / gif.height else 1f)
            }
            AsyncImage(
                model = gif.url,
                contentDescription = "GIF",
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    val image = state.result.image
                    if (image.width > 0 && image.height > 0) ratio = image.width.toFloat() / image.height
                },
                modifier = Modifier
                    .sizeIn(maxWidth = 280.dp, maxHeight = 280.dp)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpen(i) },
            )
        }
    }
}
