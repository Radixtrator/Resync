package io.github.radixtrator.resync.ui.common

import android.util.LruCache
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

private const val SPOILER_URL = "resync-spoiler:reveal"
private val PARAGRAPH_BREAK = Regex("""</p>\s*<p>""")
private val HORIZONTAL_RULE = Regex("""<hr\s*/?>""")
private val HEADING_END = Regex("""</h([1-6])>\s*(?=<)""")
// A list with no list inside it, so nested lists can be rewritten from the inside out.
private val INNER_LIST = Regex("""<(ol|ul)(?:\s+start="(\d+)")?[^>]*>((?:(?!<[ou]l[\s>]).)*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
private val LIST_ITEM = Regex("""<li>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
private val LEADING_BREAKS = Regex("""^(\s*<br>\s*)+""")
private val SPACED_BREAK = Regex("""\s*<br>\s*""")
private val SPOILER = Regex("""<span class="md-spoiler-text">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)

/**
 * Stands in for a horizontal rule (`---`) as its own paragraph. It's invisible, so text that doesn't go
 * through [RedditBodyText] just shows a blank line there.
 */
const val RULE = '\u2063'
private val RELATIVE_LINK = Regex("""<a href="(/[^"]*)"([^>]*)>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
private val RULE_GAP = Regex("<p>$RULE</p><br>(?=<p>$RULE</p>)")
private val TAG = Regex("<[^>]*>")

/**
 * Renders the HTML Reddit generates from markdown (body_html / selftext_html).
 * Links go through LocalUriHandler (the Navigator), so reddit URLs open in-app.
 * Spoilers (`>!text!<`) show as a tappable "[spoiler]" until revealed.
 */
@Composable
fun rememberRedditHtml(html: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val uriHandler = LocalUriHandler.current
    var revealed by remember(html) { mutableStateOf(false) }
    return remember(html, linkColor, revealed) {
        redditHtml(html, linkColor, revealed) { url ->
            if (url == SPOILER_URL) revealed = true else uriHandler.openUri(url)
        }
    }
}

fun redditHtml(
    html: String,
    linkColor: Color,
    revealSpoilers: Boolean = true,
    onLink: ((String) -> Unit)? = null,
): AnnotatedString {
    val listener = onLink?.let { handler ->
        LinkInteractionListener { link -> (link as? LinkAnnotation.Url)?.url?.let(handler) }
    }
    val parsed = AnnotatedString.fromHtml(
        cachedMarkup(html, revealSpoilers),
        // Sync's "Show larger links": links are a quarter bigger than the text around them.
        linkStyles = TextLinkStyles(SpanStyle(color = linkColor, fontSize = 1.25.em)),
        linkInteractionListener = listener,
    )
    return parsed.subSequence(0, parsed.text.trimEnd().length)
}

/**
 * Comment rows are recomposed from scratch whenever they scroll back in, so [redditMarkup]'s regex passes are
 * kept for recently shown bodies instead of being redone each time.
 */
private val markupCache = LruCache<String, String>(300)

private fun cachedMarkup(html: String, revealSpoilers: Boolean): String {
    val key = if (revealSpoilers) "1$html" else "0$html"
    return markupCache[key] ?: redditMarkup(html, revealSpoilers).also { markupCache.put(key, it) }
}

/** Reddit's HTML rewritten for fromHtml, which knows nothing of rules, spoilers or site-relative links. */
fun redditMarkup(html: String, revealSpoilers: Boolean): String {
    // Reddit emits site-relative links (/r/foo, /u/bar) that no URI handler can open as-is.
    // Sync spells out where such a link goes, "(/wiki/)", unless the text already is the path (/r/foo, u/bar).
    var source = RELATIVE_LINK.replace(html) { m ->
        val path = m.groupValues[1]
        val label = m.groupValues[3].replace(TAG, "").trim()
        val spelled = label.startsWith("/") || label.startsWith("r/") || label.startsWith("u/") || label.isEmpty()
        val link = "<a href=\"https://www.reddit.com$path\"${m.groupValues[2]}>${m.groupValues[3]}</a>"
        if (spelled || path.startsWith("//")) link else "$link ($path)"
    }.replace("href=\"/", "href=\"https://www.reddit.com/")
    source = HORIZONTAL_RULE.replace(source, "<p>$RULE</p>")
    source = flattenLists(source)
    // fromHtml runs paragraphs together, and headings into what follows; Reddit (and Sync) leave a blank
    // line after each.
    source = PARAGRAPH_BREAK.replace(source, "</p><br><p>")
    source = HEADING_END.replace(source, "</h$1><br>")
    // Rules in a row sit a line apart in Sync, not a paragraph apart.
    source = RULE_GAP.replace(source, "<p>$RULE</p>")
    return SPOILER.replace(source) { m ->
        if (revealSpoilers) "<i>${m.groupValues[1]}</i>" else "<a href=\"$SPOILER_URL\">[spoiler]</a>"
    }
}

/**
 * fromHtml can't number ordered lists, and spaces items a paragraph apart. Sync writes "1. item" (or
 * "• item") a line apart, so each list becomes such lines in one paragraph, innermost lists first.
 */
private fun flattenLists(html: String): String {
    var source = html
    while (true) {
        val list = INNER_LIST.find(source) ?: return source
        val ordered = list.groupValues[1] == "ol"
        var number = list.groupValues[2].toIntOrNull() ?: 1
        val lines = LIST_ITEM.findAll(list.groupValues[3]).map { item ->
            // Paragraphs inside an item (loose lists, or a nested list already rewritten) become line breaks.
            val content = LEADING_BREAKS.replace(SPACED_BREAK.replace(item.groupValues[1].replace("<p>", "<br>").replace("</p>", ""), "<br>").trim(), "")
            (if (ordered) "${number++}. " else "• ") + content
        }
        source = source.replaceRange(list.range, "<p>" + lines.joinToString("<br>") + "</p>")
    }
}

/** [Text] that draws each [RULE] as a line across its full width, like Sync's horizontal rules. */
@Composable
fun RedditBodyText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    if (RULE !in text.text) {
        Text(text, modifier, style = style, maxLines = maxLines, overflow = overflow, onTextLayout = { onLayout?.invoke(it) })
        return
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color = MaterialTheme.colorScheme.onSurface
    Text(
        text,
        modifier.fillMaxWidth().drawBehind {
            val result = layout ?: return@drawBehind
            // With maxLines, rules past the cut would otherwise land on the last visible line.
            val shown = result.getLineEnd(result.lineCount - 1, visibleEnd = true)
            text.text.forEachIndexed { i, c ->
                if (c != RULE || i >= shown) return@forEachIndexed
                val line = result.getLineForOffset(i)
                val y = (result.getLineTop(line) + result.getLineBottom(line)) / 2
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.3.dp.toPx())
            }
        },
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = {
            layout = it
            onLayout?.invoke(it)
        },
    )
}

/** Rendered text plus the links Sync would footnote, in order: link n is marked ⁿ in [text]. */
data class RedditText(val text: AnnotatedString, val links: List<String>)

/**
 * [rememberRedditHtml] with Sync's link footnotes: every link that leaves Reddit gets a superscript
 * number after it, and comes back in [RedditText.links] for [LinkCards] under the text.
 */
@Composable
fun rememberRedditText(html: String, footnotes: Boolean = true): RedditText {
    // A spoiler's reveal is state of this row, so it can't be shared through the cache.
    if (SPOILER_CLASS in html) {
        val text = rememberRedditHtml(html)
        return remember(text, footnotes) { if (footnotes) footnoteLinks(text) else RedditText(text, emptyList()) }
    }
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(html, linkColor, footnotes) { redditText(html, linkColor, footnotes) }
}

private const val SPOILER_CLASS = "md-spoiler-text"

private data class TextKey(val html: String, val linkColor: Color, val footnotes: Boolean)

/**
 * Finished comment and post bodies. Building one (fromHtml, footnotes) is the costly part of a row, so comment
 * pages build the first screenful off the main thread with [prepareRedditText] before showing it, and rows that
 * scroll back in reuse what was built.
 */
private val textCache = LruCache<TextKey, RedditText>(600)

/**
 * [rememberRedditText]'s result outside composition, safe on any thread. Links carry no listener of their own,
 * so a tap goes to the LocalUriHandler (the Navigator) as before. Not for HTML with spoilers.
 */
fun redditText(html: String, linkColor: Color, footnotes: Boolean): RedditText {
    val key = TextKey(html, linkColor, footnotes)
    textCache[key]?.let { return it }
    val text = redditHtml(html, linkColor)
    val built = if (footnotes) footnoteLinks(text) else RedditText(text, emptyList())
    textCache.put(key, built)
    return built
}

/** Builds [html]'s body into the cache ahead of time, unless [rememberRedditText] would build it per row anyway. */
fun prepareRedditText(html: String, linkColor: Color, footnotes: Boolean) {
    if (html.isNotBlank() && SPOILER_CLASS !in html) redditText(html, linkColor, footnotes)
}

/** Numbers the off-Reddit links in [text]; repeats of a URL reuse its number. */
fun footnoteLinks(text: AnnotatedString): RedditText {
    val links = mutableListOf<String>()
    val marks = text.getLinkAnnotations(0, text.length)
        .mapNotNull { range ->
            val url = (range.item as? LinkAnnotation.Url)?.url ?: return@mapNotNull null
            if (!isFootnoted(url)) return@mapNotNull null
            val n = links.indexOf(url).takeIf { it >= 0 } ?: links.size.also { links += url }
            range.end to n + 1
        }
        .sortedBy { it.first }
    if (marks.isEmpty()) return RedditText(text, emptyList())
    val numbered = AnnotatedString.Builder().apply {
        var from = 0
        for ((end, n) in marks) {
            append(text.subSequence(from, end))
            append(superscript(n))
            from = end
        }
        append(text.subSequence(from, text.length))
    }.toAnnotatedString()
    return RedditText(numbered, links)
}

/** Reddit's own pages (users, subs, threads) read fine inline; everything else gets a footnote. */
private fun isFootnoted(url: String): Boolean {
    if (url == SPOILER_URL || !url.startsWith("http")) return false
    val host = url.substringAfter("://").substringBefore('/').substringBefore('?').lowercase()
    return !(host == "reddit.com" || host.endsWith(".reddit.com"))
}

private fun superscript(n: Int): String = n.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")

/** True if the HTML has spoiler markup; used by tests and to decide on reveal affordances. */
fun hasSpoiler(html: String) = SPOILER.containsMatchIn(html)

/** The first non-blank line of [text], with "\n…" when more follows (Sync's selftext previews); null if blank. */
fun firstParagraph(text: String): String? {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val first = lines.firstOrNull() ?: return null
    return if (lines.size > 1) "$first\n…" else first
}
