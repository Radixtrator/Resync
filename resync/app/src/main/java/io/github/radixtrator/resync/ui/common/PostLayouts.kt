package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.data.ImageSource
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostColumns
import io.github.radixtrator.resync.data.columnsFor
import io.github.radixtrator.resync.data.isCoverScreen
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.data.ViewOptions
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalContainer
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalUserTagColors
import io.github.radixtrator.resync.ui.navigation.LocalFrontpageOrAllFeed
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.LocalUserTags
import io.github.radixtrator.resync.ui.navigation.rememberVisited
import io.github.radixtrator.resync.ui.navigation.ModRoute
import io.github.radixtrator.resync.ui.theme.ResyncColors
import kotlin.math.min
import kotlin.math.roundToInt

// Sync's six feed layouts (sync-reference/SYNC_PARITY_LOG.md §3.3). Each takes the post plus
// [PostActions]; spacing between items belongs to the list, see [gutter] and [spacing].

/**
 * What a feed item can do with its post. [onHide] is null where the list can't drop posts (the Hide button then
 * stays out); [onShare] and [onModTools] null mean the usual share sheet and Moderator screen.
 */
class PostActions(
    val onOpen: () -> Unit,
    val onMedia: () -> Unit,
    val onMore: () -> Unit,
    val onVote: (Int) -> Unit,
    val onToggleSave: () -> Unit,
    val onHide: (() -> Unit)? = null,
    val onShare: (() -> Unit)? = null,
    val onModTools: (() -> Unit)? = null,
)

/**
 * Side gutter and column gap: card layouts float on the window, row layouts run edge to edge, and so do Cards with
 * "Full width cards" on ([fullWidthCards]).
 */
val PostLayout.gutter: Dp
    @Composable get() = when (this) {
        PostLayout.LIST, PostLayout.COMPACT, PostLayout.SLIDES -> 0.dp
        PostLayout.CARD -> if (fullWidthCards()) 0.dp else 16.dp
        PostLayout.SMALLER_CARDS, PostLayout.SMALL_CARDS -> 16.dp
    }

/**
 * Gap between consecutive items. Rows draw their own dividers instead, as do Slides with the thick divider off
 * ([ViewOptions.thickDivider]); full-width Cards sit closer, like Slides.
 */
val PostLayout.spacing: Dp
    @Composable get() = when (this) {
        PostLayout.LIST, PostLayout.COMPACT -> 0.dp
        PostLayout.SLIDES -> if (LocalSettings.current.viewOptionsFor(this).thickDivider) 8.dp else 0.dp
        PostLayout.CARD -> if (fullWidthCards()) 8.dp else 16.dp
        PostLayout.SMALLER_CARDS, PostLayout.SMALL_CARDS -> 16.dp
    }

/** The item's outline, so the swipe panel behind a card has the card's corners. */
val PostLayout.swipeShape: Shape
    @Composable get() = when (this) {
        PostLayout.CARD -> if (fullWidthCards()) RectangleShape else RoundedCornerShape(8.dp)
        PostLayout.SMALL_CARDS -> RoundedCornerShape(8.dp)
        PostLayout.SMALLER_CARDS -> RoundedCornerShape(10.dp)
        PostLayout.LIST, PostLayout.COMPACT, PostLayout.SLIDES -> RectangleShape
    }

/**
 * Whether this is Cards with Customize view's "Full width cards" ([ViewOptions.cardFullWidth]) taking effect: Sync
 * offers it for a single column only, so it applies while the feed's [columns] (from the window width, as feeds
 * count them) come to one.
 */
@Composable
private fun PostLayout.fullWidthCards(): Boolean {
    if (this != PostLayout.CARD) return false
    val options = LocalSettings.current.viewOptionsFor(this)
    return options.cardFullWidth && feedColumns(options) == 1
}

/**
 * How many columns a feed of this layout has in the current window: the Customize view choice for this kind of
 * screen (a foldable's cover or main screen, [ViewOptions.columnsFor]), else as many as the width fits.
 */
@Composable
fun PostLayout.feedColumns(options: ViewOptions): Int {
    val config = LocalConfiguration.current
    return columns(config.screenWidthDp, options.columnsFor(isCoverScreen(config.smallestScreenWidthDp)))
}

/**
 * Cards and small cards tile into columns on wide screens, as on Sync's foldable cover screen. A Customize view
 * [override] other than Auto fixes the count instead, whatever the width.
 */
fun PostLayout.columns(widthDp: Int, override: PostColumns = PostColumns.AUTO): Int = override.count ?: when (this) {
    PostLayout.CARD, PostLayout.SMALL_CARDS -> when {
        widthDp >= 840 -> 3
        widthDp >= 480 -> 2
        else -> 1
    }
    else -> 1
}

/** [post] drawn in [layout], honoring that layout's Customize view options ([ViewOptions]) from [LocalSettings]. */
@Composable
fun PostLayoutItem(layout: PostLayout, post: Post, showSubreddit: Boolean, actions: PostActions) {
    val options = LocalSettings.current.viewOptionsFor(layout)
    when (layout) {
        PostLayout.LIST -> RowPost(post, showSubreddit, actions, options, flushThumbnail = true, inCard = false)
        PostLayout.COMPACT -> RowPost(post, showSubreddit, actions, options, flushThumbnail = false, inCard = false)
        PostLayout.SMALLER_CARDS -> SyncCard(RoundedCornerShape(10.dp)) {
            RowPost(post, showSubreddit, actions, options, flushThumbnail = false, inCard = true)
        }
        PostLayout.SMALL_CARDS -> SmallCardPost(post, showSubreddit, actions, options)
        PostLayout.CARD -> CardPost(post, showSubreddit, actions, options)
        PostLayout.SLIDES -> SlidePost(post, actions, options)
    }
}

@Composable
private fun SyncCard(shape: Shape, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

// --- Cards ---

/**
 * Sync's Cards. [options]: image above or below the title (`card_image_above`), full-height or cropped previews,
 * selftext previews, full width cards (square corners here; the list drops the gutter), the title, description and
 * selftext sizes, and the footer's Save / Hide / Share / Mod tools buttons.
 */
@Composable
private fun CardPost(post: Post, showSubreddit: Boolean, actions: PostActions, options: ViewOptions) {
    val previewMaxHeight = if (options.cardFullHeight) null else CroppedPreviewHeight
    // Rounded, or square with "Full width cards" on.
    SyncCard(PostLayout.CARD.swipeShape) {
        Column(Modifier.combinedClickable(onClick = actions.onOpen, onLongClick = actions.onMore)) {
            if (options.imageAboveTitle) CardPreview(post, onClick = actions.onMedia, maxHeight = previewMaxHeight)
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                PostTitle(post, CardTitle.scaledBy(options.titleSize.scale))
                Spacer(Modifier.height(4.dp))
                PostMeta(post, showSubreddit, maxLines = 3, style = CardMeta.scaledBy(options.descriptionSize.scale), showAuthor = feedAuthors)
                if (post.shownFlair() != null) {
                    Spacer(Modifier.height(8.dp))
                    FlairChip(post)
                }
                if (options.selftextPreviews) {
                    selfTextPreview(post)?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium.scaledBy(options.selftextSize.scale),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!options.imageAboveTitle) {
                CardPreview(post, onClick = actions.onMedia, modifier = Modifier.padding(top = 12.dp), maxHeight = previewMaxHeight)
            }
            StatsFooter(post, actions, PostButtons(options.cardSaveButton, options.hideButton, options.shareButton, options.modButton))
        }
    }
}

/**
 * Sync's Small cards. [options]: the thumbnail top-right, or top-left (`small_cards_thumbnail_align_left`), the title
 * and description sizes, and the footer's Save / Hide / Share / Mod tools buttons, as on Cards.
 */
@Composable
private fun SmallCardPost(post: Post, showSubreddit: Boolean, actions: PostActions, options: ViewOptions) {
    SyncCard(RoundedCornerShape(8.dp)) {
        Column(Modifier.combinedClickable(onClick = actions.onOpen, onLongClick = actions.onMore)) {
            Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                val hasThumbnail = post.thumbnail() != null
                if (hasThumbnail && options.thumbnailLeft) {
                    Thumbnail(post, 64.dp, RoundedCornerShape(8.dp), onClick = actions.onMedia)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    PostTitle(post, CardTitle.scaledBy(options.titleSize.scale), maxLines = 5)
                    Spacer(Modifier.height(4.dp))
                    PostMeta(post, showSubreddit, maxLines = 3, style = RowMeta.scaledBy(options.descriptionSize.scale), showAuthor = feedAuthors)
                    if (post.shownFlair() != null) {
                        Spacer(Modifier.height(8.dp))
                        FlairChip(post)
                    }
                }
                if (hasThumbnail && !options.thumbnailLeft) {
                    Spacer(Modifier.width(12.dp))
                    Thumbnail(post, 64.dp, RoundedCornerShape(8.dp), onClick = actions.onMedia)
                }
            }
            StatsFooter(post, actions, PostButtons(options.cardSaveButton, options.hideButton, options.shareButton, options.modButton))
        }
    }
}

/** "10.6k points / 154 comments" on the left, then any of [buttons] and ⋮ on the right: Sync's card footer. */
@Composable
private fun StatsFooter(post: Post, actions: PostActions, buttons: PostButtons = PostButtons.None) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PostStats(post, Modifier.weight(1f))
        PostButtonIcons(post, actions, buttons, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = actions.onMore) { Icon(Icons.Default.MoreVert, "Post options", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Which of Sync's optional post buttons (Customize view › Buttons) a button row shows. */
private class PostButtons(val save: Boolean, val hide: Boolean, val share: Boolean, val mod: Boolean) {
    companion object {
        val None = PostButtons(save = false, hide = false, share = false, mod = false)
    }
}

/**
 * The switched-on [buttons] in Sync's settings order: Save, Hide (only where the list can hide), Share, and Mod tools
 * (only in communities you moderate). Emitted straight into the caller's row.
 */
@Composable
private fun PostButtonIcons(post: Post, actions: PostActions, buttons: PostButtons, tint: Color) {
    val nav = LocalNavigator.current
    if (buttons.save) {
        IconButton(onClick = actions.onToggleSave) {
            Icon(
                if (post.saved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                if (post.saved) "Unsave" else "Save",
                tint = if (post.saved) ResyncColors.Saved else tint,
            )
        }
    }
    if (buttons.hide) {
        actions.onHide?.let { hide -> IconButton(onClick = hide) { Icon(Icons.Default.Close, "Hide", tint = tint) } }
    }
    if (buttons.share) {
        val share: () -> Unit = actions.onShare ?: { nav.share("https://www.reddit.com${post.permalink}", post.title) }
        IconButton(onClick = share) { Icon(Icons.Outlined.Share, "Share", tint = tint) }
    }
    if (buttons.mod) {
        val moderated by LocalContainer.current.moderated.collectAsStateWithLifecycle()
        if (moderated.any { it.equals(post.subreddit, ignoreCase = true) }) {
            val modTools: () -> Unit = actions.onModTools ?: { nav.push(ModRoute(post.subreddit)) }
            IconButton(onClick = modTools) { Icon(AppIcons.Shield, "Mod tools", tint = tint) }
        }
    }
}

// --- List, compact and smaller cards ---

/**
 * Sync's List ([flushThumbnail]: square thumbnail against the left edge) and Compact (inset rounded
 * thumbnail) rows; Smaller cards wraps the compact row in a card. ⌄ expands the media or text inline.
 * [options]: thumbnails (left or right), the type indicator, a one-line title and the title and description sizes, as
 * Sync's List/Compact/Smaller cards screens offer.
 */
@Composable
private fun RowPost(post: Post, showSubreddit: Boolean, actions: PostActions, options: ViewOptions, flushThumbnail: Boolean, inCard: Boolean) {
    var expanded by rememberSaveable(post.fullname) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val short = LocalSettings.current.shortScores
    Column(
        Modifier
            // Opaque so a swipe's action panel shows only where the row has slid away.
            .background(if (inCard) Color.Transparent else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = actions.onOpen, onLongClick = actions.onMore),
    ) {
        val right = options.thumbnailRight
        val thumbnail = @Composable {
            if (flushThumbnail) {
                Thumbnail(post, 72.dp, RectangleShape, onClick = actions.onMedia)
            } else {
                val inset = if (right) Modifier.padding(end = 8.dp, top = 8.dp) else Modifier.padding(start = 8.dp, top = 8.dp)
                Thumbnail(post, 64.dp, RoundedCornerShape(8.dp), onClick = actions.onMedia, modifier = inset)
            }
        }
        val text = @Composable {
            Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 10.dp, end = 4.dp)) {
                PostTitle(post, RowTitle.scaledBy(options.titleSize.scale), maxLines = if (options.singleLineTitle) 1 else 4)
                val description = RowMeta.scaledBy(options.descriptionSize.scale)
                PostMeta(post, showSubreddit, maxLines = 1, style = description, showAuthor = feedAuthors)
                Text(
                    "${points(post.score, short)} • ${comments(post.numComments, short)}",
                    style = description,
                    color = muted,
                    maxLines = 1,
                )
                post.shownFlair()?.let {
                    Text(it, style = if (flushThumbnail) RowMeta else RowTitle, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val expand = @Composable {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(40.dp)) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand", tint = muted)
                }
            }
        val type = @Composable { Icon(typeIcon(post), null, tint = muted, modifier = Modifier.padding(8.dp).size(20.dp)) }
        val side = @Composable {
            if (flushThumbnail) {
                Row(Modifier.fillMaxHeight().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    expand()
                    if (options.typeIndicators) type()
                }
            } else {
                Column(Modifier.fillMaxHeight().padding(end = 4.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                    // Without the type icon, ⌄ still sits at the bottom.
                    if (options.typeIndicators) type() else Spacer(Modifier.weight(1f))
                    expand()
                }
            }
        }
        val none = @Composable {}
        RowPostLayout(
            leading = if (options.thumbnails && !right) thumbnail else none,
            text = text,
            side = side,
            // "Align thumbnail right": past ⌄ and the type icon, against the row's end.
            trailing = if (options.thumbnails && right) thumbnail else none,
        )
        if (expanded) {
            if (post.thumbnail() != null && !post.isSelf) {
                CardPreview(post, onClick = actions.onMedia, modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).clip(RoundedCornerShape(8.dp)))
            } else if (post.selfText.isNotBlank()) {
                RedditBodyText(
                    post.selfTextHtml?.let { rememberRedditHtml(it) } ?: AnnotatedString(post.selfText),
                    MaterialTheme.typography.bodyMedium,
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
        if (!inCard) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * [RowPost]'s row: [text] takes the width the others leave, and [side] (⌄ and the type icon) stretches to the
 * row's height so ⌄ sits at the bottom. That's what IntrinsicSize.Min did, but it measured the title and meta text
 * twice per row; here only [side]'s small intrinsics are asked for.
 */
@Composable
private fun RowPostLayout(
    leading: @Composable () -> Unit,
    text: @Composable () -> Unit,
    side: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Layout(contents = listOf(leading, text, side, trailing), modifier = Modifier.fillMaxWidth()) { (lead, main, aside, trail), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val leadP = lead.firstOrNull()?.measure(loose)
        val trailP = trail.firstOrNull()?.measure(loose)
        val sideM = aside.first()
        val sideWidth = sideM.maxIntrinsicWidth(Constraints.Infinity)
        val textWidth = (width - (leadP?.width ?: 0) - (trailP?.width ?: 0) - sideWidth).coerceAtLeast(0)
        val textP = main.first().measure(Constraints.fixedWidth(textWidth))
        val height = maxOf(textP.height, leadP?.height ?: 0, trailP?.height ?: 0, sideM.minIntrinsicHeight(sideWidth))
        val sideP = sideM.measure(Constraints.fixed(sideWidth, height))
        layout(width, height) {
            var x = 0
            leadP?.let { it.place(x, 0); x += it.width }
            textP.place(x, 0); x += textP.width
            sideP.place(x, 0); x += sideP.width
            trailP?.place(x, 0)
        }
    }
}

// --- Slides ---

/**
 * Sync's Slides: a full-width post with the sub on top, big media, and vote/comment pills. [options]: image and
 * selftext previews and the image's full height / full width / place before the title, the subreddit icon, the
 * border around selftext, the thick divider (off: a hairline here, and no gap in the list), the title, description
 * and selftext sizes, and the button row (Save / Hide / Share / Mod tools, voting on the right, or no buttons at all).
 */
@Composable
private fun SlidePost(post: Post, actions: PostActions, options: ViewOptions) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // "Preview before title": the media opens the slide, flush with its top edge when it also runs full width.
    val previewFirst = options.imagePreviews && options.slideImageTop && post.hasCardPreview()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = actions.onOpen, onLongClick = actions.onMore)
            .padding(top = if (previewFirst && options.slideFullWidth) 0.dp else 16.dp),
    ) {
        if (previewFirst) SlidePreview(post, actions, options, top = 0.dp)
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = if (previewFirst) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (options.subredditIcons) {
                LetterAvatar(post.subreddit, 32.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                val description = CardMeta.scaledBy(options.descriptionSize.scale)
                Text(
                    buildAnnotatedString {
                        append("r/")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(post.subreddit) }
                    },
                    style = description,
                    color = muted,
                )
                PostMeta(post, showSubreddit = false, maxLines = 1, style = description, showAuthor = feedAuthors)
            }
        }
        PostTitle(
            post,
            CardTitle.copy(fontSize = 15.sp).scaledBy(options.titleSize.scale),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        )
        if (post.shownFlair() != null) FlairChip(post, Modifier.padding(start = 16.dp, top = 8.dp))
        if (options.imagePreviews && !options.slideImageTop) SlidePreview(post, actions, options, top = 12.dp)
        val selftext = if (options.selftextPreviews) selfTextPreview(post) else null
        selftext?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium.scaledBy(options.selftextSize.scale),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .fillMaxWidth()
                    .then(
                        if (options.selftextBorder) {
                            Modifier
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        } else Modifier,
                    ),
            )
        }
        if (!options.hideAllButtons) {
            val buttons = PostButtons(options.saveButton, options.hideButton, options.shareButton, options.modButton)
            val onSurface = MaterialTheme.colorScheme.onSurface
            // "Right aligned voting" mirrors the row: icons and ⋮ on the left, the comment and vote pills on the right.
            val voteRight = options.voteRight
            Row(
                Modifier.fillMaxWidth().padding(start = if (voteRight) 4.dp else 16.dp, end = if (voteRight) 16.dp else 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (voteRight) {
                    PostButtonIcons(post, actions, buttons, tint = onSurface)
                    IconButton(onClick = actions.onMore) { Icon(Icons.Default.MoreVert, "Post options") }
                    Spacer(Modifier.weight(1f))
                    CommentsPill(post.numComments, actions.onOpen)
                    Spacer(Modifier.width(8.dp))
                    VotePill(post.likes, post.score, actions.onVote)
                } else {
                    VotePill(post.likes, post.score, actions.onVote)
                    Spacer(Modifier.width(8.dp))
                    CommentsPill(post.numComments, actions.onOpen)
                    Spacer(Modifier.weight(1f))
                    PostButtonIcons(post, actions, buttons, tint = onSurface)
                    IconButton(onClick = actions.onMore) { Icon(Icons.Default.MoreVert, "Post options") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Without the thick divider the list leaves no gap between slides, so each ends on a hairline.
        if (!options.thickDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * A slide's big preview, [top] below what comes before it: inset with rounded corners, or edge to edge with "Preview
 * full width"; cropped to a band unless "Preview full height" is on.
 */
@Composable
private fun SlidePreview(post: Post, actions: PostActions, options: ViewOptions, top: Dp) {
    CardPreview(
        post,
        onClick = actions.onMedia,
        modifier = if (options.slideFullWidth) {
            Modifier.padding(top = top)
        } else {
            Modifier.padding(start = 16.dp, end = 16.dp, top = top).clip(RoundedCornerShape(12.dp))
        },
        height = if (options.slideFullHeight) null else CroppedPreviewHeight,
    )
}

/** The comment count pill beside Slides' vote pill; opens the comments. */
@Composable
private fun CommentsPill(count: Int, onOpen: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Comment, "Comments", modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(Format.count(count, LocalSettings.current.shortScores), style = MaterialTheme.typography.labelLarge)
    }
}

/** ▲ score ▼ in one rounded pill, tinted orange when upvoted. */
@Composable
private fun VotePill(likes: Boolean?, score: Int, onVote: (Int) -> Unit) {
    val tint = when (likes) {
        true -> ResyncColors.Upvote
        false -> ResyncColors.Downvote
        null -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (likes == null) MaterialTheme.colorScheme.surfaceContainerHighest else tint.copy(alpha = 0.25f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onVote(if (likes == true) 0 else 1) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, "Upvote", tint = tint)
        }
        Text(Format.count(score, LocalSettings.current.shortScores), style = MaterialTheme.typography.labelLarge, color = tint)
        IconButton(onClick = { onVote(if (likes == false) 0 else -1) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, "Downvote", tint = tint)
        }
    }
}

// --- Shared pieces ---

// Feed text, measured from Sync's screenshots: cards read a size up from rows, and lines sit 1.2em apart
// like Sync's Roboto (its titles are Product Sans, a little taller).
private val CardTitle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp)
private val CardMeta: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 1.2.em)
private val RowTitle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 1.2.em)
private val RowMeta: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 1.2.em)

/** Customize view's title size ([ViewOptions.titleSize]): the size times [scale]; em line heights follow on their own. */
private fun TextStyle.scaledBy(scale: Float): TextStyle =
    if (scale == 1f) this else copy(fontSize = fontSize * scale, lineHeight = if (lineHeight.isSp) lineHeight * scale else lineHeight)

/** The band Slides crop previews to, and that Cards with "Full height" off crop tall previews to. */
private val CroppedPreviewHeight = 220.dp

/** The title, dimmed once read (in feeds), with Sync's pin and lock marks inline. */
@Composable
fun PostTitle(
    post: Post,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    dimWhenRead: Boolean = true,
) {
    val read = dimWhenRead && rememberVisited(post.id)
    val accent = MaterialTheme.colorScheme.primary
    // Remembered, with the marks map below, so Text can skip a row whose title hasn't changed.
    val text = remember(post.title, post.stickied, post.locked) { buildAnnotatedString {
        append(post.title)
        if (post.stickied) {
            append(" ")
            appendInlineContent("pin", "[pinned]")
        }
        if (post.locked) {
            append(" ")
            appendInlineContent("lock", "[locked]")
        }
    } }
    val marks = remember(accent) { mapOf(
        "pin" to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(AppIcons.Pin, "Pinned", tint = accent)
        },
        "lock" to InlineTextContent(Placeholder(0.9.em, 0.9.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Icons.Default.Lock, "Locked", tint = accent)
        },
    ) }
    Text(
        text,
        style = style,
        color = if (read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = marks,
        modifier = modifier,
    )
}

/**
 * "worldnews • author 🛡 [flair] • 4h* (last edited 1h) • thehill", with the sub in the accent color and
 * Sync's shield after moderators and admins.
 */
@Composable
fun PostMeta(
    post: Post,
    showSubreddit: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    style: TextStyle = CardMeta,
    suffix: String? = null,
    showAuthor: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    val flairBg = MaterialTheme.colorScheme.surfaceVariant
    val tag = LocalUserTags.current[post.author.lowercase()]
    val tagStyle = userTagStyle(post.author)
    val me = LocalAccount.current
    val text = remember(post, showSubreddit, showAuthor, suffix, me, tag, tagStyle, accent, flairBg) { buildAnnotatedString {
        if (showSubreddit) {
            withStyle(SpanStyle(color = accent)) { append(post.subreddit) }
        }
        if (showAuthor) {
            if (showSubreddit) append(" • ")
            append(post.author)
            // Sync marks your own posts with a person after your name.
            if (post.author.equals(me, ignoreCase = true)) {
                append(" ")
                appendInlineContent("you", "[you]")
            }
            tag?.let {
                append(" ")
                withStyle(tagStyle) { append(" $it ") }
            }
            post.distinguished?.takeIf { it in DISTINGUISHED }?.let {
                append(" ")
                appendInlineContent(it, "[$it]")
            }
            post.authorFlair?.let {
                append(" ")
                withStyle(SpanStyle(background = flairBg)) { append(" $it ") }
            }
        }
        if (showSubreddit || showAuthor) append(" • ")
        append(Format.timeAgo(post.createdUtc))
        post.editedUtc?.let { append("* (last edited ${Format.timeAgo(it)})") }
        shortDomain(post)?.let { append(" • $it") }
        if (post.over18) withStyle(SpanStyle(color = ResyncColors.Nsfw, fontWeight = FontWeight.Bold)) { append(" • NSFW") }
        if (post.spoiler) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(" • Spoiler") }
        suffix?.let { append(" • $it") }
    } }
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = distinguishedMarks(),
        modifier = modifier,
    )
}

private val DISTINGUISHED = setOf("moderator", "admin")

/** Your tag for a user (Sync's "Tag user"): a tinted label after their name. */
@Composable
fun userTagStyle(user: String? = null): SpanStyle {
    val custom = user?.let { LocalUserTagColors.current[it.lowercase()] }
    return if (custom == null) {
        SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, color = MaterialTheme.colorScheme.onTertiaryContainer)
    } else {
        // Dark text on light tints, light text on dark ones.
        SpanStyle(background = Color(custom), color = if (Color(custom).luminance() > 0.5f) Color.Black else Color.White)
    }
}

/** Inline content for [DISTINGUISHED] ids: Sync's shield, in the accent color (red for admins). */
@Composable
fun distinguishedMarks(): Map<String, InlineTextContent> {
    val accent = MaterialTheme.colorScheme.primary
    val you = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(accent, you) { mapOf(
        "moderator" to InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(AppIcons.Shield, "Moderator", tint = accent)
        },
        "admin" to InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(AppIcons.Shield, "Admin", tint = ResyncColors.Nsfw)
        },
        "you" to InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Icons.Outlined.Person, "You", tint = you)
        },
    ) }
}

/** Sync drops ".com" ("thehill", "reddit") and keeps other hosts ("i.redd.it"); self posts show none. */
internal fun shortDomain(post: Post): String? = when {
    post.isSelf || post.domain.isEmpty() || post.domain.startsWith("self.") -> null
    else -> post.domain.removeSuffix(".com")
}

/** Post options › "Show post author", for feed items' [PostMeta]; off leaves the name and its marks out. */
private val feedAuthors: Boolean
    @Composable get() = LocalSettings.current.showPostAuthor

/** The post's flair, unless Post options › Flair hides flair in feeds. */
@Composable
private fun Post.shownFlair(): String? = flair?.takeIf { LocalSettings.current.showPostFlair }

/** A rounded flair chip, tinted with the subreddit's flair color when it has one. */
@Composable
fun FlairChip(post: Post, modifier: Modifier = Modifier) {
    FlairChip(post.flair ?: return, post.flairColor, modifier)
}

/** A flair chip for [text], tinted with [color] (ARGB) when the community set one. */
@Composable
fun FlairChip(text: String, color: Int?, modifier: Modifier = Modifier) {
    // Sync mutes flair colors toward the card so bright subreddit colors don't shout.
    val bg = color?.let { Color(it).copy(alpha = 0.45f) } ?: MaterialTheme.colorScheme.surfaceVariant
    Text(
        text,
        style = CardMeta,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun PostStats(post: Post, modifier: Modifier = Modifier, style: TextStyle = CardMeta) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val pointsColor = when (post.likes) {
        true -> ResyncColors.Upvote
        false -> ResyncColors.Downvote
        null -> muted
    }
    val short = LocalSettings.current.shortScores
    Column(modifier) {
        Text(points(post.score, short), style = style, color = pointsColor)
        Text(comments(post.numComments, short), style = style, color = muted)
    }
}

/** [short]: Post options › "Display short scores" (10.6k, else 10,600). */
private fun points(score: Int, short: Boolean) = if (score == 1) "1 point" else "${Format.count(score, short)} points"
private fun comments(n: Int, short: Boolean) = if (n == 1) "1 comment" else "${Format.count(n, short)} comments"

/** Whether [CardPreview] has a big image to show (links without one fall back to a link row). */
fun Post.hasCardPreview(): Boolean = media.cardImage() != null

private fun PostMedia.cardImage(): ImageSource? = when (this) {
    is PostMedia.Image -> preview ?: ImageSource(url, 0, 0)
    is PostMedia.Gallery -> preview
    is PostMedia.Video -> preview
    // Link thumbnails come from the preview when Reddit has one; tiny legacy thumbnails stay in rows.
    is PostMedia.Link -> thumbnail?.takeIf { it.width == 0 || it.width >= 320 }
    PostMedia.None -> null
}

/**
 * What a feed of [layout] in [columns] will load for a post: the [CardPreview] or [Thumbnail] image, at the rendition and
 * size those ask for, so [PreloadImages] can have it ready before it scrolls into view. Empty under data saving.
 */
@Composable
fun rememberPostPreloads(layout: PostLayout, columns: Int): (Post) -> List<ImagePreload> {
    val settings = LocalSettings.current
    val frontpageOrAll = LocalFrontpageOrAllFeed.current
    val saving = dataSavingActive()
    val columnWidth = LocalWindowInfo.current.containerSize.width / columns.coerceAtLeast(1)
    val density = LocalDensity.current
    return remember(layout, settings, frontpageOrAll, saving, columnWidth, density) {
        val options = settings.viewOptionsFor(layout)
        val thumbnailSize = when (layout) {
            PostLayout.LIST -> if (options.thumbnails) 72.dp else null
            PostLayout.COMPACT, PostLayout.SMALLER_CARDS -> if (options.thumbnails) 64.dp else null
            PostLayout.SMALL_CARDS -> 64.dp
            PostLayout.CARD, PostLayout.SLIDES -> null
        }
        val cards = layout == PostLayout.CARD || (layout == PostLayout.SLIDES && options.imagePreviews)
        val thumbnailPx = thumbnailSize?.let { with(density) { it.roundToPx() } }
        val preloads: (Post) -> List<ImagePreload> = { post ->
            val decode = !post.shouldBlur(settings, frontpageOrAll)
            if (saving) emptyList() else when {
                cards -> post.media.cardImage()?.let { image ->
                    val ratio = image.aspectRatio.coerceIn(0.5f, 2.2f)
                    listOf(ImagePreload(image.urlFor(columnWidth), columnWidth, (columnWidth / ratio).roundToInt(), decode))
                }
                thumbnailPx != null -> post.thumbnail()?.let { listOf(ImagePreload(it.urlFor(thumbnailPx), thumbnailPx, thumbnailPx, decode)) }
                else -> null
            }.orEmpty()
        }
        preloads
    }
}

/**
 * Full-width media for cards and slides, at the image's own aspect (Sync's "Preview full height"),
 * with a centered badge for galleries and video and, on links, the domain/path band along the bottom.
 */
@Composable
fun CardPreview(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp? = null, maxHeight: Dp? = null) {
    val image = post.media.cardImage() ?: return
    val blur = post.shouldBlur()
    val saving = dataSavingActive()
    val ratio = image.aspectRatio.coerceIn(0.5f, 2.2f)
    // About a column's width: a three-column grid of cards needn't fetch 1080px previews.
    val columnWidth = LocalWindowInfo.current.containerSize.width / LocalFeedColumns.current
    Box(
        modifier
            .fillMaxWidth()
            .then(
                when {
                    height != null -> Modifier.height(height)
                    // Its natural height up to [maxHeight], cropped beyond that.
                    maxHeight != null -> Modifier.layout { measurable, constraints ->
                        val width = constraints.maxWidth
                        val cropped = min((width / ratio).roundToInt(), maxHeight.roundToPx())
                        val placeable = measurable.measure(Constraints.fixed(width, cropped))
                        layout(width, cropped) { placeable.place(0, 0) }
                    }
                    else -> Modifier.aspectRatio(ratio)
                },
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Holding it peeks at the picture, in feeds.
            .peekableClick(post, onClick),
    ) {
        // Data saving leaves the preview unloaded: the empty frame keeps its size, with the post's type in it.
        if (!saving) {
            AsyncImage(
                model = rememberPreviewRequest(image.urlFor(columnWidth), blurWidth = if (blur) CARD_BLUR_WIDTH else null),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        when (post.media) {
            is PostMedia.Gallery -> CenterBadge(AppIcons.Gallery, "Gallery", Modifier.align(Alignment.Center))
            is PostMedia.Video -> CenterBadge(AppIcons.PlayCircle, "Video", Modifier.align(Alignment.Center))
            else -> if (saving && !blur) {
                Icon(typeIcon(post), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).size(32.dp))
            }
        }
        if (post.media is PostMedia.Link) UrlBand(post.domain, post.url, Modifier.align(Alignment.BottomCenter))
        if (blur) {
            Text(
                if (post.over18) "NSFW" else "Spoiler",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (post.over18) ResyncColors.Nsfw else Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CenterBadge(icon: ImageVector, description: String, modifier: Modifier) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.4f),
        contentColor = Color.White,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)),
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, modifier = Modifier.size(24.dp)) }
    }
}

/** "thehill.com/policy/international/…" over the bottom of a link preview. */
@Composable
private fun UrlBand(domain: String, url: String, modifier: Modifier) {
    val path = url.substringAfter("://", url).substringAfter('/', "").let { if (it.isEmpty()) "" else "/$it" }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(domain) }
            append(path)
        },
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** How far blurred previews are shrunk ([rememberPreviewRequest]): a heavy blur on cards, a lighter one on thumbnails. */
const val CARD_BLUR_WIDTH = 24
private const val THUMBNAIL_BLUR_WIDTH = 6

/** A square or rounded thumbnail; posts without an image get their content-type icon. */
@Composable
private fun Thumbnail(post: Post, size: Dp, shape: Shape, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Data saving shows the type icon in place of the image.
    val saving = dataSavingActive()
    val thumb = post.thumbnail()?.takeUnless { saving }
    val widthPx = with(LocalDensity.current) { size.roundToPx() }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Holding it peeks at the picture, in feeds.
            .peekableClick(post, onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            AsyncImage(
                model = rememberPreviewRequest(thumb.urlFor(widthPx), blurWidth = if (post.shouldBlur()) THUMBNAIL_BLUR_WIDTH else null),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(typeIcon(post), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun typeIcon(post: Post): ImageVector = when (post.media) {
    is PostMedia.Image -> AppIcons.Image
    is PostMedia.Gallery -> AppIcons.Gallery
    is PostMedia.Video -> AppIcons.PlayCircle
    is PostMedia.Link -> AppIcons.Link
    PostMedia.None -> AppIcons.Text
}

/** A round initial standing in for a subreddit icon (listings don't carry one). */
@Composable
private fun LetterAvatar(name: String, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Sync's feed preview of a text post: its first paragraph as plain text, then "…" on a line of its own if there's more. */
@Composable
private fun selfTextPreview(post: Post): String? {
    if (!post.isSelf) return null
    return remember(post.selfTextHtml, post.selfText) {
        firstParagraph(post.selfTextHtml?.let { AnnotatedString.fromHtml(it).text } ?: post.selfText)
    }
}
