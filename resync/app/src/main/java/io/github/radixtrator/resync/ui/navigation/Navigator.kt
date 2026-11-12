package io.github.radixtrator.resync.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostCache
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.data.RedditApi
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.ui.media.ScreenSnapshot

/**
 * Screen-to-screen navigation plus link handling. Provided as both LocalNavigator and LocalUriHandler,
 * so links tapped inside rendered markdown get the same in-app routing.
 */
class Navigator(
    private val context: Context,
    private val backStack: MutableList<NavKey>,
    private val settings: () -> Settings,
    /** This is [listPane]: the navigator the feed sees while a post is open beside it. */
    private val inListPane: Boolean = false,
) : UriHandler {

    /**
     * For the feed in Tablet mode's dual pane (ui/home/DualPane.kt), whose post sits on top of it in the
     * back stack. It acts as if that post weren't there: opening another post or feed replaces the open
     * post instead of stacking on it, back closes the feed (and its post), and [top] is the feed.
     * Other pages (media, profiles, search...) still open over both panes, keeping the post.
     */
    val listPane: Navigator by lazy { Navigator(context, backStack, settings, inListPane = true) }

    val canPop: Boolean get() = backStack.size > 1

    val top: NavKey get() = if (inListPane) backStack.lastOrNull { it !is PostRoute } ?: backStack.last() else backStack.last()

    fun push(route: NavKey) {
        if (inListPane && (route is PostRoute || route is FeedRoute)) closeDetailPane()
        // The image viewer sits on a blurred copy of the page it opens from.
        if (route is ImageRoute) ScreenSnapshot.capture()
        backStack.add(route)
    }

    fun pop() {
        if (inListPane) closeDetailPane()
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Pops while [predicate] holds for the top screen, never past the root. */
    fun popWhile(predicate: (NavKey) -> Boolean) {
        if (inListPane) closeDetailPane()
        while (backStack.size > 1 && predicate(backStack.last())) backStack.removeAt(backStack.lastIndex)
    }

    /** Swaps the current screen, e.g. the submit form for the post it created. */
    fun replaceTop(route: NavKey) {
        if (inListPane) closeDetailPane()
        backStack.add(route)
        backStack.removeAt(backStack.lastIndex - 1)
    }

    /** Closes the post(s) open in the dual pane's right side, back down to the feed under them. */
    private fun closeDetailPane() {
        while (backStack.size > 1 && backStack.last() is PostRoute) backStack.removeAt(backStack.lastIndex)
    }

    fun resetTo(route: NavKey) {
        backStack.add(route)
        backStack.subList(0, backStack.lastIndex).clear()
    }

    override fun openUri(uri: String) {
        RedditLinks.route(uri)?.let(::push) ?: openExternal(uri)
    }

    fun openExternal(uri: String) {
        try {
            if (settings().inAppBrowser && uri.startsWith("http")) {
                CustomTabsIntent.Builder().setShowTitle(true).build().apply {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }.launchUrl(context, uri.toUri())
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open $uri", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(url: String, title: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, url)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Sync's Random. Reddit dropped its random-community endpoint (Sync now errors), so this picks one you joined. */
    /** Opens a random community from all of Reddit (not just yours); [nsfw] picks an NSFW one. */
    suspend fun openRandomCommunity(api: RedditApi, nsfw: Boolean = false) {
        val pick = runCatching { api.randomCommunityPool(nsfw) }.getOrNull()?.randomOrNull()
        if (pick == null) {
            Toast.makeText(context, "Couldn't find a random community", Toast.LENGTH_SHORT).show()
        } else {
            push(FeedRoute(pick))
        }
    }

    /**
     * Opens [post]'s comment page. The post as shown here (with any vote or save just made) is remembered, so
     * the page can draw it straight away while the comments load.
     */
    fun openPost(post: Post) {
        PostCache.put(post)
        push(PostRoute(post.id))
    }

    /** What tapping a post's media does: viewer for images/galleries, player for video, else the link. */
    fun openMedia(post: Post) {
        when (val m = post.media) {
            is PostMedia.Image -> push(ImageRoute(listOf(m.url), postId = post.id))
            is PostMedia.Gallery -> push(ImageRoute(m.images.map { it.url }, postId = post.id))
            is PostMedia.Video -> push(VideoRoute(m.url, loop = m.loop, postId = post.id, fallback = m.fallback))
            is PostMedia.Link -> openUri(m.url)
            PostMedia.None -> openPost(post)
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator provided") }
val LocalSettings = compositionLocalOf { Settings() }

/** True inside the Frontpage, r/popular and r/all feeds, where "Blur NSFW only on Frontpage, Popular & All" still blurs. */
val LocalFrontpageOrAllFeed = compositionLocalOf { false }

/** Whether a feed of [subreddit] (null or "" is the front page) is the Frontpage, r/popular or r/all; [domain] feeds are none. */
fun isFrontpageOrAll(subreddit: String?, domain: String? = null): Boolean =
    domain == null && subreddit?.lowercase().let { it == null || it == "" || it == "popular" || it == "all" }

/**
 * Ids of posts the user has opened, for dimming them in feeds. It's the state itself rather than its value, so
 * marking a post read recomposes only rows that read it (through [rememberVisited]), not every row on screen.
 */
val LocalVisited = staticCompositionLocalOf<State<Set<String>>> { mutableStateOf(emptySet()) }

/** Whether [postId] has been opened; the caller recomposes only when that answer changes. */
@Composable
fun rememberVisited(postId: String): Boolean {
    val visited = LocalVisited.current
    return remember(postId, visited) { derivedStateOf { postId in visited.value } }.value
}

/** The app's container, for shared sheets that load or send things themselves (reporting, tagging). */
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }

/** Your tags for users (Sync's "Tag user"), by lowercase name. */
val LocalUserTags = compositionLocalOf<Map<String, String>> { emptyMap() }

/** ARGB tag tints by lowercase user name; see [LocalUserTags]. */
val LocalUserTagColors = compositionLocalOf<Map<String, Int>> { emptyMap() }

/** Active account name, or null when logged out. */
val LocalAccount = compositionLocalOf<String?> { null }
