package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark"), BLACK("Black (AMOLED)") }

/** Sync's six feed layouts, in its Change view order. Names are persisted, so CARD/COMPACT keep theirs. */
enum class PostLayout(val label: String) {
    LIST("List"),
    COMPACT("Compact"),
    SMALLER_CARDS("Smaller cards"),
    SMALL_CARDS("Small cards"),
    CARD("Cards"),
    SLIDES("Slides"),
}

/** Sync's "Base font size": scales every sp in the app. SMALLER is Sync's default on phones. */
enum class BaseFontSize(val label: String, val scale: Float) {
    SMALLEST("Smallest", 0.8f),
    SMALLER("Smaller", 0.9f),
    NORMAL("Normal", 1.0f),
    LARGER("Larger", 1.1f),
    LARGEST("Largest", 1.25f),
}

/** Typeface choices. Roboto is bundled (Sync's default); Product Sans can't be, so the rest are the system's families. */
enum class Typeface(val label: String) {
    ROBOTO("Roboto"),
    SANS("Sans serif (system)"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
}
/**
 * Sync's per-view-type display options (Settings > View type, sync-reference/settings-catalogue.md "View type").
 * Each layout reads only the options its Sync screen offers; the rest keep their defaults. Every default is how
 * Resync's layouts looked before the option existed, which is mostly Sync's default too; the fields whose default
 * differs from Sync's say so. Sync shares a few keys between List, Compact and Smaller cards (`type_indicators`,
 * `compact_single_lines`, `thumbnail_align`); here every layout has its own.
 */
@Serializable
data class ViewOptions(
    /** Slides: `slide_image_previews`, the big image/video/link preview. */
    val imagePreviews: Boolean = true,
    /** Slides and Cards: `slide_selftext_previews` / `card_selftext_previews`, a text post's first paragraph. */
    val selftextPreviews: Boolean = true,
    /** Slides: `slide_subreddit_thumbnail`, the community icon before "r/sub". */
    val subredditIcons: Boolean = true,
    /** Slides: `slide_selftext_border`, the outline around the text preview. */
    val selftextBorder: Boolean = true,
    /** Cards: `card_image_above`; off puts the preview under the title and meta. */
    val imageAboveTitle: Boolean = true,
    /** Small cards: `small_cards_thumbnail_align_left`; off keeps the thumbnail top-right. */
    val thumbnailLeft: Boolean = false,
    /** List and Compact: `compact_thumbnails`. */
    val thumbnails: Boolean = true,
    /** List, Compact and Smaller cards: `type_indicators`, the link/image/gallery/play icon on the right. */
    val typeIndicators: Boolean = true,
    /** List, Compact and Smaller cards: `compact_single_lines`, "Limit title to a single line". */
    val singleLineTitle: Boolean = false,
    /** List, Compact and Smaller cards: `thumbnail_align`, "Align thumbnail right"; the thumbnail goes to the row's end. */
    val thumbnailRight: Boolean = false,
    /**
     * Slides: `slide_image_full_height`, "Preview full height": the preview at the image's own aspect. Sync's default
     * is on; off keeps Resync's fixed 220 dp band.
     */
    val slideFullHeight: Boolean = false,
    /** Slides: `slide_full_width`, "Preview full width": the preview runs edge to edge, without margins or corners. */
    val slideFullWidth: Boolean = false,
    /** Slides: `slide_image_top`, "Preview before title": the preview opens the slide, above the subreddit and title. */
    val slideImageTop: Boolean = false,
    /**
     * Slides: `slide_thick_divider`, "Show a thick divider between posts": a band of the window's background between
     * slides. Off leaves a hairline instead.
     */
    val thickDivider: Boolean = true,
    /**
     * Cards: `card_full_width`, "Full width cards": no side gutter and square corners. As in Sync, it applies only
     * while the cards fall in a single column.
     */
    val cardFullWidth: Boolean = false,
    /**
     * Cards: `card_image_full_height`, "Full height". Sync's default is off; on keeps Resync's previews at the image's
     * own aspect, and off crops tall images to a 220 dp band.
     */
    val cardFullHeight: Boolean = true,
    /** Slides: `slide_buttons_save`, the bookmark button. */
    val saveButton: Boolean = true,
    /**
     * Cards and Small cards: `card_buttons_save` / `small_card_buttons_save`. Sync's default is on; off keeps Resync's
     * card footer (points, comments, ⋮).
     */
    val cardSaveButton: Boolean = false,
    /**
     * Slides, Cards and Small cards: `slide_buttons_hide` / `card_buttons_hide` / `small_card_buttons_hide`, where the
     * list can hide posts.
     */
    val hideButton: Boolean = false,
    /**
     * Slides, Cards and Small cards: `slide_buttons_share` / `card_buttons_share` / `small_card_buttons_share`, sharing
     * the comments link.
     */
    val shareButton: Boolean = false,
    /**
     * Slides, Cards and Small cards: `slide_buttons_mod` / `card_buttons_mod` / `small_card_buttons_mod`, opening the
     * community's Moderator screen; shown only in communities you moderate. Sync's default is on; off keeps Resync's
     * button row as it was.
     */
    val modButton: Boolean = false,
    /** Slides: `slide_button_vote_right`, "Right aligned voting": the vote and comment pills move to the row's end. */
    val voteRight: Boolean = false,
    /** Slides: `slide_hide_all_buttons`, "Hide all buttons": no button row at all (⋮ stays on long press). */
    val hideAllButtons: Boolean = false,
    /**
     * Cards and Small cards: how many columns on a foldable's main (inner) screen, a tablet, or any window at least
     * [COVER_SCREEN_MAX_SW_DP] dp in its smallest width. [PostColumns.AUTO] keeps Resync's width-based tiling.
     * (The name predates [coverColumns], so settings saved with one override keep it for the big screen.)
     */
    val columns: PostColumns = PostColumns.AUTO,
    /** Cards and Small cards: the same for a foldable's cover screen, or any phone-sized window. */
    val coverColumns: PostColumns = PostColumns.AUTO,
    /**
     * Every layout: the size half of Sync's "Title font" picker (`slide_title`, `card_title`, `small_card_title`,
     * `smaller_card_title`, `compact_title`, `list_title`), reduced to three steps around the layout's own size.
     */
    val titleSize: TitleSize = TitleSize.NORMAL,
    /**
     * Every layout: the size half of Sync's "Description font" (`slide_description`, `card_description`,
     * `small_card_description`, `smaller_card_description`, `compact_description`, `list_description`), the meta line
     * under the title (and the points line in List, Compact and Smaller cards).
     */
    val descriptionSize: TitleSize = TitleSize.NORMAL,
    /** Slides and Cards: the size half of Sync's "Selftext font" (`slide_selftext`, `card_selftext`), the text preview. */
    val selftextSize: TitleSize = TitleSize.NORMAL,
)

/** A [ViewOptions.columns] choice; [count] null means Resync picks from the window width. */
enum class PostColumns(val label: String, val count: Int?) {
    AUTO("Auto", null),
    ONE("1", 1),
    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
}

/**
 * Below this smallest width (dp) a window counts as a cover screen: phones and foldables' outer displays sit around
 * 320-420 dp, while unfolded inner screens and tablets are 600 dp or more whichever way they're held.
 */
const val COVER_SCREEN_MAX_SW_DP = 600

/** Whether a window whose smallest width is [smallestWidthDp] is a cover (phone-sized) screen. */
fun isCoverScreen(smallestWidthDp: Int): Boolean = smallestWidthDp < COVER_SCREEN_MAX_SW_DP

/** The [ViewOptions.columns] or [ViewOptions.coverColumns] choice for a cover screen or not. */
fun ViewOptions.columnsFor(coverScreen: Boolean): PostColumns = if (coverScreen) coverColumns else columns

/**
 * A Customize view font size ([ViewOptions.titleSize], [ViewOptions.descriptionSize], [ViewOptions.selftextSize]):
 * [scale] multiplies the layout's own size for that text. Named for the first of them; names are persisted.
 */
enum class TitleSize(val label: String, val scale: Float) {
    SMALL("Small", 0.875f),
    NORMAL("Normal", 1f),
    LARGE("Large", 1.15f),
}

enum class NsfwMode(val label: String) { HIDE("Hide NSFW posts"), BLUR("Blur previews"), SHOW("Show") }

/**
 * Sync's "Periodically check for new messages". [minutes] null means no repeating check. WorkManager can't
 * repeat faster than every 15 minutes, so Sync's "Every 10 minutes" is 15 here.
 */
enum class MessageCheck(val label: String, val minutes: Long?) {
    OFF("Off", null),
    MINUTES_15("Every 15 minutes", 15),
    MINUTES_30("Every 30 minutes", 30),
    HOUR("Every 60 minutes", 60),
    HOURS_3("Every 3 hours", 180),
    HOURS_6("Every 6 hours", 360),
    HOURS_12("Every 12 hours", 720),
    HOURS_24("Every 24 hours", 1440),
    ON_LAUNCH("On launch", null),
}

/** Sync's "HD Reddit video quality": when v.redd.it videos open in HD rather than SD (the player's HD button switches). */
enum class VideoQuality(val label: String) { ALWAYS("Always"), WIFI("WiFi only"), NEVER("Never") }

/**
 * Sync's "Data saving mode" (Data options): while it applies, feeds leave thumbnails and card previews unloaded
 * and Reddit videos open in SD. [MOBILE] is Sync's "Automagic": any metered connection.
 */
enum class DataSaving(val label: String) { NEVER("Never"), MOBILE("On mobile data"), ALWAYS("Always") }

/** Sync's "Initial comment count": Reddit's `limit` for a post's first page of comments (500 is Reddit's ceiling). */
enum class CommentCount(val label: String, val limit: Int) {
    C50("50", 50),
    C75("75", 75),
    C100("100", 100),
    C200("200", 200),
    ALL("All (up to 500)", 500),
}

/** Sync's "Live comments refresh rate" (`live_comments_refresh`): how often sports mode reloads the comments. */
enum class LiveRefresh(val label: String, val seconds: Int) {
    S10("Every 10 seconds", 10),
    S15("Every 15 seconds", 15),
    S30("Every 30 seconds", 30),
    S60("Every 60 seconds", 60),
}

/** Sync's "Post media preview size" for the image or video above a post's comments. */
enum class MediaPreviewSize(val label: String) { CROPPED("Cropped height"), FULL("Full height") }

/** What a swipe stage does. Sync's post and comment swipe lists, merged; [posts]/[comments] say where each applies. */
enum class SwipeAction(val label: String, val posts: Boolean, val comments: Boolean) {
    UPVOTE("Upvote", posts = true, comments = true),
    DOWNVOTE("Downvote", posts = true, comments = true),
    SAVE("Save", posts = true, comments = true),
    PROFILE("Profile", posts = true, comments = true),
    SHARE("Share", posts = true, comments = true),
    HIDE("Hide", posts = true, comments = false),
    MARK_READ("Mark as read", posts = true, comments = false),
    REPLY("Reply", posts = false, comments = true),
}

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** A hand-picked accent (ARGB) the whole palette is tinted from, instead of Sync's blue. Ignored with Material You colors. */
    val seedColor: Int? = null,
    /** Sync's "extra colorful" tweak: backgrounds and cards take more of the accent's hue. */
    val extraColorful: Boolean = false,
    /**
     * Theme management › "Color intensity" (`monet_color_intensity`, 0–2 in 0.2 steps): scales the hand-picked
     * accent's saturation, so 0 is grey and 2 twice as vivid. Only applies with a [seedColor] (Sync's Manual mode).
     */
    val colorIntensity: Float = 1.0f,
    /** Sync's "Dark overlay" (`dark_overlay`): a black layer over the whole app, a night-reading dimmer. */
    val darkOverlay: Boolean = false,
    /** How dark [darkOverlay] makes the app: the black layer's alpha, [DARK_OVERLAY_MIN]–[DARK_OVERLAY_MAX]. */
    val darkOverlayLevel: Float = 0.4f,
    val layout: PostLayout = PostLayout.CARD,
    val nsfw: NsfwMode = NsfwMode.BLUR,
    val blurSpoilers: Boolean = true,
    /**
     * With [nsfw] on [NsfwMode.BLUR], blur NSFW previews only in the Frontpage, r/popular and r/all feeds; communities,
     * profiles, search and the rest show them as is.
     */
    val blurNsfwOnlyFrontpageAndAll: Boolean = false,
    /** Sync's "Hide read": posts you had already opened when a feed loaded are left out of it. */
    val hideRead: Boolean = false,
    val commentSort: CommentSort = CommentSort.BEST,
    val postMediaPreview: MediaPreviewSize = MediaPreviewSize.CROPPED,
    val swipeBack: Boolean = true,
    /** Sync's Tablet mode: on wide screens, a navigation rail replaces the bottom bar. */
    val tabletMode: Boolean = false,
    /**
     * Tablet › "Navigation on the right": on wide screens (a foldable's inner screen, a tablet) the Posts / Explore /
     * Inbox bar becomes a rail down the right edge, within reach of the thumb. Works with or without Tablet mode.
     */
    val navRailRight: Boolean = false,
    /** Tablet › "Dual pane (landscape)" (`tablet_dual_pane_landscape`): a post opens beside its feed in landscape. */
    val dualPaneLandscape: Boolean = true,
    /** Tablet › "Dual pane (portrait)" (`tablet_dual_pane_portrait`): the same in portrait, on wide enough windows. */
    val dualPanePortrait: Boolean = false,
    /**
     * Tablet › "Tabbed comments" (`tablet_tabs`): with Tablet mode on, a post page splits into Post and Comments tabs
     * instead of one scroll. Sync's default is on; off keeps Resync's single scroll.
     */
    val tabbedComments: Boolean = false,
    /**
     * General › "Confirm exit" (`exit_preference`): back on the last page asks before closing the app. Sync's default
     * is on; off keeps Resync's old behavior.
     */
    val confirmExit: Boolean = false,
    /** Theme management's saved themes, in the order they were saved. */
    val savedThemes: List<SavedTheme> = emptyList(),
    /**
     * Sync's "Use drawer navigation" (`use_drawer`): home pages lose the bottom bar and the toolbar avatar
     * opens a side drawer instead. Tablet mode's rail still wins on wide screens.
     */
    val drawerNavigation: Boolean = false,
    val baseFontSize: BaseFontSize = BaseFontSize.SMALLER,
    val typeface: Typeface = Typeface.ROBOTO,
    /** Sync's "Action visibility": labels of More actions buttons switched off. */
    val hiddenActions: Set<String> = emptySet(),
    val inAppBrowser: Boolean = true,
    val muteVideos: Boolean = false,
    val hdVideo: VideoQuality = VideoQuality.NEVER,
    /** Sync's comment navigation bar: previous/next top-level comment buttons. Off by default, as in Sync. */
    val commentNavigation: Boolean = false,
    /** Reopening a post returns to where you were, with the same threads folded. */
    val rememberCommentPosition: Boolean = true,
    /** Footnote numbers on off-Reddit links, with a card per link under the text. */
    val linkCards: Boolean = true,
    /** GIFs in comments (Giphy picks, uploads, links to GIFs) play under the comment's text. */
    val commentGifs: Boolean = true,
    /** Sync's "Show profile pictures": authors' avatars in comment headers instead of their initial. */
    val profilePictures: Boolean = true,
    val messageCheck: MessageCheck = MessageCheck.HOUR,
    /** Sync's extended swipe: drag left, and each further step arms the next of these three. */
    val postSwipe: Boolean = true,
    val postSwipeActions: List<SwipeAction> = DEFAULT_POST_SWIPES,
    val commentSwipe: Boolean = true,
    val commentSwipeActions: List<SwipeAction> = DEFAULT_COMMENT_SWIPES,
    /** Post options › Flair: flair chips on posts in feeds (Sync's "Hide post flair" is this off). */
    val showPostFlair: Boolean = true,
    /** Post options › "Show post author" in feed items' meta line. */
    val showPostAuthor: Boolean = true,
    /** Post options › "Display short scores": 10.6k rather than 10,600 in feeds. */
    val shortScores: Boolean = true,
    /** History › "Mark posts as read on scroll": posts scrolled past in a feed count as read. */
    val markReadOnScroll: Boolean = false,
    /** Comments › "Hide AutoMod comments", replies included. */
    val hideAutoMod: Boolean = false,
    /** Comments › "Show user flair" in comment headers. */
    val commentFlair: Boolean = true,
    /** Comment options › "Collapse all threads by default" on a post's first open. */
    val collapseAllByDefault: Boolean = false,
    /** Comment options › "Initial comment count". 200 is what Resync always asked for (Sync's default is 75). */
    val commentCount: CommentCount = CommentCount.C200,
    /** Comments › "Live comments refresh rate": sports mode's reload interval. 15 s is Sync's default. */
    val liveRefresh: LiveRefresh = LiveRefresh.S15,
    /** The comment reader's ⋮ › "Read replies": read every loaded comment, not just the top-level ones. */
    val readerReplies: Boolean = false,
    /** The comment reader's ⋮ speed: TextToSpeech's speech rate, 1.0 being the engine's normal pace. */
    val readerSpeechRate: Float = 1.0f,
    /**
     * Media › "Close images by": a single tap (true; swiping up then slides in the control bar) or swiping up or
     * down (false; a tap then shows and hides the bar).
     */
    val tapToClose: Boolean = true,
    /** Media › "Swipe to close": videos with a fling up or down, and images that close by a tap with a downward one. */
    val swipeToClose: Boolean = true,
    /** Media › Video / GIF playback: "Display seek bar". */
    val videoSeekBar: Boolean = true,
    /** Media › Video / GIF playback: "Display speed controls" (in the player's ⋮ panel). */
    val videoSpeedControls: Boolean = true,
    /** Data options › "Data saving mode". Never is how Resync always behaved (Sync's default is Automagic). */
    val dataSaving: DataSaving = DataSaving.NEVER,
    /** Per-feed overrides of [layout], keyed by [feedKey], like Sync's per-subreddit view. */
    val feedLayouts: Map<String, PostLayout> = emptyMap(),
    /** Sync's Customize view choices, per layout; a layout missing here uses the [ViewOptions] defaults. */
    val viewOptions: Map<PostLayout, ViewOptions> = emptyMap(),
    /** Sorts saved from the sort sheet ("Always use Hot here?"), keyed by [feedKey]; other feeds open on Reddit's default. */
    val feedSorts: Map<String, Sort> = emptyMap(),
) {
    fun layoutFor(subreddit: String?): PostLayout = feedLayouts[feedKey(subreddit)] ?: layout

    fun sortFor(subreddit: String?): Sort? = feedSorts[feedKey(subreddit)]

    fun viewOptionsFor(layout: PostLayout): ViewOptions = viewOptions[layout] ?: ViewOptions()

    /**
     * Whether Tablet mode's dual pane shows in a window [widthDp] wide, held [landscape] or not: Sync's per-orientation
     * "Dual pane" switches, as long as the window fits two panes ([DUAL_PANE_MIN_WIDTH_DP]).
     */
    fun dualPane(landscape: Boolean, widthDp: Int): Boolean =
        tabletMode && (if (landscape) dualPaneLandscape else dualPanePortrait) && widthDp >= DUAL_PANE_MIN_WIDTH_DP

    /** The three post swipe stages, repairing a stored list that isn't three valid entries. */
    val postSwipeStages: List<SwipeAction>
        get() = postSwipeActions.takeIf { l -> l.size == 3 && l.all { it.posts } } ?: DEFAULT_POST_SWIPES

    val commentSwipeStages: List<SwipeAction>
        get() = commentSwipeActions.takeIf { l -> l.size == 3 && l.all { it.comments } } ?: DEFAULT_COMMENT_SWIPES

    companion object {
        // Sync's defaults: posts upvote, downvote, hide; comments upvote, save, reply.
        val DEFAULT_POST_SWIPES = listOf(SwipeAction.UPVOTE, SwipeAction.DOWNVOTE, SwipeAction.HIDE)
        val DEFAULT_COMMENT_SWIPES = listOf(SwipeAction.UPVOTE, SwipeAction.SAVE, SwipeAction.REPLY)

        /** Front page is ""; subreddits and multis by lowercase name. */
        fun feedKey(subreddit: String?): String = subreddit?.lowercase().orEmpty()

        /**
         * The narrowest window (dp) that gets the dual pane: two 360 dp panes, the feed's minimum and a phone-sized
         * post. Most portrait tablets (600-839 dp) clear it, for "Dual pane (portrait)"; phones only do held sideways.
         */
        const val DUAL_PANE_MIN_WIDTH_DP = 720

        const val DARK_OVERLAY_MIN = 0.1f
        const val DARK_OVERLAY_MAX = 0.7f

        /** Sync's Color intensity slider range. */
        const val COLOR_INTENSITY_MAX = 2.0f
    }
}

/** Persists [Settings] as one JSON blob; unknown/removed fields are ignored on load. */
/** The saved base font scale, read before the container exists (MainActivity applies it in attachBaseContext). */
fun savedBaseFontScale(prefs: SharedPreferences): Float =
    prefs.getString("settings.v1", null)
        ?.let { runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(Settings.serializer(), it).baseFontSize.scale }.getOrNull() }
        ?: BaseFontSize.SMALLER.scale

class SettingsStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        _settings.value = next
        prefs.edit { putString(KEY, json.encodeToString(Settings.serializer(), next)) }
    }

    private fun load(): Settings = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(Settings.serializer(), it) }.getOrNull() }
        ?: Settings()

    private companion object {
        const val KEY = "settings.v1"
    }
}
