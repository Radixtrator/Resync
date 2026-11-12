@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.NsfwMode
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.data.ProfileItem
import io.github.radixtrator.resync.data.SortKind
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.PostActionsSheet
import io.github.radixtrator.resync.ui.common.postCallbacks
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.home.ExploreMode
import io.github.radixtrator.resync.ui.home.ExploreSheet
import io.github.radixtrator.resync.ui.media.rememberDownloader
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalFrontpageOrAllFeed
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.isFrontpageOrAll
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.SwipeModeRoute

/**
 * Sync's Swipe mode: one post per page, swipe horizontally for the next or previous one. Each page is a
 * [SwipePage] (header and title over centered media), and the bottom bar ([SwipeBottomBar]) acts on the post
 * on screen: vote, save, comments, download and post options. Pages near the end trigger the next load.
 * The toolbar's feed name (▾) opens Explore to switch feeds in place, and Sort re-sorts.
 */
@Composable
fun SwipeModeScreen(subreddit: String?, container: AppContainer) {
    CompositionLocalProvider(LocalFrontpageOrAllFeed provides isFrontpageOrAll(subreddit)) {
        SwipeModeScreenContent(subreddit, container)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeModeScreenContent(subreddit: String?, container: AppContainer) {
    val nav = LocalNavigator.current
    val settings = LocalSettings.current
    val vm = viewModel(key = "swipe:${subreddit.orEmpty()}") { FeedViewModel(container, subreddit) }
    val state by vm.state.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val callbacks = remember(vm) { vm.postCallbacks() }
    val filters by container.filters.filters.collectAsStateWithLifecycle()
    val icons by container.subredditIcons.collectAsStateWithLifecycle()
    val posts = remember(state.items, filters, settings.nsfw) {
        state.items.mapNotNull { (it as? ProfileItem.PostItem)?.post }
            .filterNot { filters.hides(it, subreddit) || (settings.nsfw == NsfwMode.HIDE && it.over18) }
    }
    val pager = rememberPagerState { posts.size }
    LaunchedEffect(pager, posts.size) {
        snapshotFlow { pager.currentPage }.collect { page -> if (page >= posts.size - 3) vm.loadMore() }
    }
    // History › "Mark posts as read on scroll": in Swipe mode, the pages swiped past count as read.
    if (settings.markReadOnScroll) {
        LaunchedEffect(pager, posts) {
            snapshotFlow { pager.currentPage }.collect { page -> posts.take(page).forEach { callbacks.onMarkRead(it) } }
        }
    }
    var exploreOpen by rememberSaveable { mutableStateOf(false) }
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    // The post whose options sheet is open, from the bottom bar's ⋮ or a long-press on a page's header.
    // Kept as the fullname so the sheet sees the post's latest state (saved, voted).
    var sheetFor by remember { mutableStateOf<String?>(null) }
    val download = rememberDownloader(container.downloads)
    val current = posts.getOrNull(pager.currentPage)

    SwipeBack(enabled = false, onBack = nav::pop) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {
                        // Sync: the feed name with ▾ over the sort; tapping it opens Explore to switch feeds.
                        Column(Modifier.clickable { exploreOpen = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Format.feedName(subreddit),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(Icons.Default.KeyboardArrowDown, "Switch feed", Modifier.size(20.dp))
                            }
                            Text(sort.label, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { nav.push(SearchRoute(subreddit = subreddit)) }) { Icon(Icons.Outlined.Search, "Search") }
                        IconButton(onClick = { sortOpen = true }) { Icon(AppIcons.Sort, "Sort") }
                    },
                )
            },
            bottomBar = {
                // Sync's bottom bar, for the post on screen. Download saves its image, every gallery image, or its video.
                current?.let { post ->
                    val urls = downloadUrls(post)
                    SwipeBottomBar(
                        post = post,
                        canDownload = urls.isNotEmpty(),
                        onVote = { callbacks.onVote(post, it) },
                        onToggleSave = { callbacks.onToggleSave(post) },
                        onComments = { nav.openPost(post) },
                        onDownload = { urls.forEach(download) },
                        onMore = { sheetFor = post.fullname },
                    )
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                when {
                    posts.isEmpty() && state.loading -> CenteredBox { CircularProgressIndicator() }
                    posts.isEmpty() -> CenteredBox {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error ?: "This sub has no posts", textAlign = TextAlign.Center)
                            TextButton(onClick = vm::retry) { Text("Retry") }
                        }
                    }
                    else -> HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { posts[it].fullname }) { page ->
                        val post = posts[page]
                        SwipePage(
                            post = post,
                            iconUrl = icons[post.subreddit.lowercase()],
                            onOpen = { nav.openPost(post) },
                            onMedia = { nav.openMedia(post) },
                            onMore = { sheetFor = post.fullname },
                        )
                    }
                }
            }
        }
    }

    sheetFor?.let { id -> posts.firstOrNull { it.fullname == id } }?.let { post ->
        PostActionsSheet(
            post = post,
            onDismiss = { sheetFor = null },
            onHide = callbacks.onHide?.let { hide -> { hide(post) } },
            onDelete = { callbacks.onDelete(post) },
            onMarkRead = { callbacks.onMarkRead(post) },
        )
    }
    if (sortOpen) {
        // The Comments sort is a comment stream, with no posts to page through here.
        SortSheet(current = sort, options = vm.sortOptions - SortKind.COMMENTS, onDismiss = { sortOpen = false }, onPick = vm::setSort)
    }
    if (exploreOpen) {
        ExploreSheet(
            container = container,
            mode = ExploreMode.BROWSE,
            onDismiss = { exploreOpen = false },
            onOpenFeed = { name ->
                exploreOpen = false
                // Swaps this page for the picked feed's, so back still leaves Swipe mode.
                if (!name.equals(subreddit, ignoreCase = true)) nav.replaceTop(SwipeModeRoute(name))
            },
        )
    }
}

/** What the download button saves for [post]: its image, every gallery image, or its video. */
private fun downloadUrls(post: Post): List<String> = when (val m = post.media) {
    is PostMedia.Image -> listOf(m.url)
    is PostMedia.Gallery -> m.images.map { it.url }
    is PostMedia.Video -> listOf(m.url)
    is PostMedia.Link, PostMedia.None -> emptyList()
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
