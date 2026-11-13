@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import io.github.radixtrator.resync.ui.common.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.ProfileItem
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.Sort
import io.github.radixtrator.resync.data.SortKind
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.data.TimeRange
import io.github.radixtrator.resync.data.UserComment
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.HeaderChip
import io.github.radixtrator.resync.ui.common.LargeHeader
import io.github.radixtrator.resync.ui.common.MessageEffect
import io.github.radixtrator.resync.ui.common.OptionRow
import io.github.radixtrator.resync.ui.common.PagedGrid
import io.github.radixtrator.resync.ui.common.rememberPostPreloads
import io.github.radixtrator.resync.ui.common.PostListItem
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.SheetHandle
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.TopSheet
import io.github.radixtrator.resync.ui.common.feedColumns
import io.github.radixtrator.resync.ui.common.distinguishedMarks
import io.github.radixtrator.resync.ui.common.gutter
import io.github.radixtrator.resync.ui.common.postCallbacks
import io.github.radixtrator.resync.ui.common.rememberRedditHtml
import io.github.radixtrator.resync.ui.common.spacing
import io.github.radixtrator.resync.ui.common.visiblePosts
import io.github.radixtrator.resync.ui.home.HomeTab
import io.github.radixtrator.resync.ui.home.ShellScaffold
import io.github.radixtrator.resync.ui.home.quickActions
import io.github.radixtrator.resync.ui.navigation.CustomizeViewRoute
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalFrontpageOrAllFeed
import io.github.radixtrator.resync.ui.common.MediaPeekHost
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.isFrontpageOrAll
import io.github.radixtrator.resync.ui.navigation.PostRoute
import io.github.radixtrator.resync.ui.theme.ResyncColors
import kotlinx.coroutines.launch

@Composable
fun FeedScreen(route: FeedRoute, container: AppContainer) {
    CompositionLocalProvider(LocalFrontpageOrAllFeed provides isFrontpageOrAll(route.subreddit, route.domain)) {
        // Holding a post's picture previews it until the finger lifts.
        MediaPeekHost { FeedScreenContent(route, container) }
    }
}

@Composable
private fun FeedScreenContent(route: FeedRoute, container: AppContainer) {
    val subreddit = route.subreddit
    val nav = LocalNavigator.current
    val vm = viewModel { FeedViewModel(container, subreddit, route.domain) }
    val state by vm.state.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val about by vm.about.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val gridState = rememberLazyStaggeredGridState(vm.scroll)
    val settings = LocalSettings.current
    // Each feed keeps its own view; a domain's is kept apart from the front page's.
    val layoutFeed = route.domain?.let { "domain:$it" } ?: subreddit
    val layout = settings.layoutFor(layoutFeed)
    // Customize view's Columns choice for this layout, else as many as the width fits.
    val columns = layout.feedColumns(settings.viewOptionsFor(layout))
    val postPreloads = rememberPostPreloads(layout, columns)
    val scope = rememberCoroutineScope()
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    var viewOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    // Wiki, Flairs, Rules or Moderators, from a header chip or the About ⋮.
    var communitySheet by rememberSaveable { mutableStateOf<AboutAction?>(null) }
    val explore by container.explore.state.collectAsStateWithLifecycle()
    val watched by container.watch.watched.collectAsStateWithLifecycle()
    val watching = vm.isCommunity && subreddit?.lowercase() in watched
    val community = about?.name ?: subreddit
    val toggleWatch = rememberWatchToggle(container) { message -> scope.launch { snackbar.showSnackbar(message) } }
    val callbacks = remember(vm) { vm.postCallbacks() }
    val filters by container.filters.filters.collectAsStateWithLifecycle()
    // Read posts are judged as of when the feed opened, so a post doesn't vanish the moment you come back from it.
    val readAtOpen = remember(vm) { container.visited.ids.value }
    val hideRead = settings.hideRead
    val posts = state.items.visiblePosts { (it as? ProfileItem.PostItem)?.post }.let { items ->
        remember(items, filters, hideRead) {
            if (filters.isEmpty && !hideRead) {
                items
            } else {
                items.filterNot {
                    (it as? ProfileItem.PostItem)?.post?.let { post -> filters.hides(post, subreddit) || (hideRead && post.id in readAtOpen) } == true
                }
            }
        }
    }
    // History › "Mark posts as read on scroll": posts above the first visible item (after the header, grid item 0)
    // count as read. Hide read still judges by what was read when the feed opened, so nothing jumps.
    if (settings.markReadOnScroll) {
        LaunchedEffect(gridState, posts) {
            snapshotFlow { gridState.firstVisibleItemIndex }.collect { first ->
                container.visited.markAll(posts.take((first - 1).coerceAtLeast(0)).mapNotNull { (it as? ProfileItem.PostItem)?.post?.id })
            }
        }
    }
    val title = route.domain ?: Format.feedName(subreddit)
    // The toolbar shows the title only once the big header has scrolled away.
    val headerGone by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    val openAbout = {
        vm.loadAbout()
        aboutOpen = true
    }

    MessageEffect(state.message, snackbar, vm::messageShown)

    SwipeBack(enabled = !route.home && LocalSettings.current.swipeBack, onBack = nav::pop) {
        ShellScaffold(
            container = container,
            home = route.home,
            tab = HomeTab.POSTS,
            smallTitle = title.takeIf { headerGone },
            scrollBehavior = scrollBehavior,
            snackbar = snackbar,
            quickActions = quickActions(
                container,
                onRefresh = vm::refresh,
                subreddit = subreddit.takeIf { vm.isCommunity },
                onAbout = if (vm.isCommunity) openAbout else null,
                onChangeView = { viewOpen = true },
                onScrollToTop = { scope.launch { gridState.scrollToItem(0) } },
            ),
            onReselectTab = { scope.launch { gridState.animateScrollToItem(0) } },
            actions = { IconButton(onClick = { sortOpen = true }) { Icon(AppIcons.Sort, "Sort") } },
            showSearch = true,
            searchSubreddit = subreddit.takeIf { vm.isCommunity },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(),
            ) {
                PagedGrid(
                    state = remember(state, posts) { state.copy(items = posts) },
                    key = { it.fullname },
                    onLoadMore = vm::loadMore,
                    onRetry = vm::retry,
                    columns = columns,
                    gutter = layout.gutter,
                    spacing = layout.spacing,
                    gridState = gridState,
                    bottomPadding = padding.calculateBottomPadding(),
                    preloadsOf = { (it as? ProfileItem.PostItem)?.post?.let(postPreloads).orEmpty() },
                    emptyText = if (sort.kind == SortKind.COMMENTS) "No comments here" else "No posts here",
                    header = {
                        FeedHeader(
                            title = title,
                            about = about.takeIf { vm.isCommunity },
                            sort = sort,
                            isCommunity = vm.isCommunity,
                            onSort = { sortOpen = true },
                            onChangeView = { viewOpen = true },
                            onJoin = vm::toggleSubscribe,
                            onAbout = openAbout,
                            onWiki = { communitySheet = AboutAction.WIKI },
                            onFlairs = { communitySheet = AboutAction.FLAIRS },
                            watching = watching,
                            onWatch = { community?.let(toggleWatch) },
                            // Sync offers Random on the front page.
                            onRandom = if (subreddit == null && route.domain == null) {
                                { scope.launch { nav.openRandomCommunity(container.api) } }
                            } else null,
                        )
                    },
                ) { item ->
                    when (item) {
                        is ProfileItem.PostItem ->
                            PostListItem(item.post, showSubreddit = !vm.isCommunity, callbacks = callbacks, layout = layout, standalone = false)
                        is ProfileItem.CommentItem -> CommentCard(item.comment) {
                            nav.push(PostRoute(item.comment.postId, item.comment.id, context = 3))
                        }
                    }
                }
            }
        }
    }

    // A sort other than the one this feed opens on asks whether to keep it ("Always use Hot for Frontpage?").
    var offerToSave by remember { mutableStateOf<Sort?>(null) }
    if (sortOpen) {
        SortSheet(current = sort, options = vm.sortOptions, onDismiss = { sortOpen = false }, onPick = { picked ->
            vm.setSort(picked)
            if (picked != (settings.sortFor(vm.feedKey) ?: vm.openingSort)) offerToSave = picked
        })
    }
    offerToSave?.let { picked ->
        AlertDialog(
            onDismissRequest = { offerToSave = null },
            title = { Text("Always use ${picked.label}?") },
            text = { Text("Open $title sorted by ${picked.label} from now on.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveSort(picked)
                    offerToSave = null
                }) { Text("Always") }
            },
            dismissButton = { TextButton(onClick = { offerToSave = null }) { Text("Just this time") } },
        )
    }
    if (viewOpen) ChangeViewSheet(container, layoutFeed, current = layout, onDismiss = { viewOpen = false })
    if (aboutOpen) {
        SubredditInfoSheet(
            about = about,
            watching = watching,
            favorite = community != null && explore.isFavorite(community),
            onDismiss = { aboutOpen = false },
            onToggleSubscribe = vm::toggleSubscribe,
            onToggleWatch = { community?.let(toggleWatch) },
            onToggleFavorite = { community?.let(container.explore::toggleFavorite) },
            onAction = { action ->
                aboutOpen = false
                if (community != null && !nav.runAboutAction(action, community)) communitySheet = action
            },
        )
    }
    val sheet = communitySheet
    if (sheet != null && community != null) {
        CommunityActionSheet(container, community, sheet, onDismiss = { communitySheet = null }, onNavigate = nav::push)
    }
}

@Composable
private fun FeedHeader(
    title: String,
    about: Subreddit?,
    sort: Sort,
    isCommunity: Boolean,
    onSort: () -> Unit,
    onChangeView: () -> Unit,
    onJoin: () -> Unit,
    onAbout: () -> Unit,
    onWiki: () -> Unit,
    onFlairs: () -> Unit,
    watching: Boolean,
    onWatch: () -> Unit,
    onRandom: (() -> Unit)?,
) {
    LargeHeader(
        title = title,
        leading = if (isCommunity) {
            { CommunityIcon(about, title) }
        } else null,
        overline = about?.let { sub ->
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(Format.members(sub.subscribers), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    ) {
        HeaderChip(sort.label, sortIcon(sort.kind), onSort, dropdown = true)
        if (isCommunity) {
            val joined = about?.isSubscribed == true
            HeaderChip(if (joined) "Joined" else "Join", if (joined) Icons.Outlined.Check else AppIcons.PlaylistAdd, onJoin)
            HeaderChip("About", Icons.Outlined.Info, onAbout)
            HeaderChip("Wiki", AppIcons.Book, onWiki)
            HeaderChip("Flairs", AppIcons.Tag, onFlairs, dropdown = true)
            HeaderChip(
                if (watching) "Watching" else "Watch",
                if (watching) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                onWatch,
            )
        }
        HeaderChip("Change view", AppIcons.ViewStack, onChangeView)
        onRandom?.let { HeaderChip("Random", AppIcons.Dice, it) }
    }
}

@Composable
private fun CommunityIcon(about: Subreddit?, name: String) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        about?.iconUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

private fun sortIcon(kind: SortKind): ImageVector = when (kind) {
    SortKind.BEST -> Icons.Outlined.Star
    SortKind.HOT -> AppIcons.Flame
    SortKind.NEW -> AppIcons.Burst
    SortKind.RISING -> AppIcons.TrendingUp
    SortKind.TOP -> AppIcons.Bars
    SortKind.CONTROVERSIAL -> AppIcons.TrendingDown
    SortKind.COMMENTS -> AppIcons.Comment
}

/**
 * A comment outside its thread (Sync's "Comments" sort, profiles): its post's title in italics, then
 * "author [flair] 5 points • 2h • sub" with the author in the accent color and the score in your vote's
 * color, then the comment itself. Tapping opens it in its thread.
 */
@Composable
fun CommentCard(comment: UserComment, onOpen: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val flairBg = MaterialTheme.colorScheme.surfaceVariant
    val meta = buildAnnotatedString {
        withStyle(SpanStyle(color = accent)) { append(comment.author) }
        comment.distinguished?.takeIf { it == "moderator" || it == "admin" }?.let {
            append(" ")
            appendInlineContent(it, "[$it]")
        }
        comment.authorFlair?.let {
            append(" ")
            withStyle(SpanStyle(background = flairBg)) { append(" $it ") }
        }
        append(" ")
        val scoreColor = when (comment.likes) {
            true -> ResyncColors.Upvote
            false -> ResyncColors.Downvote
            null -> Color.Unspecified
        }
        withStyle(SpanStyle(color = scoreColor)) {
            append(
                when {
                    comment.scoreHidden -> "[score hidden]"
                    comment.score == 1 -> "1 point"
                    else -> "${Format.count(comment.score)} points"
                },
            )
        }
        append(" • ${Format.timeAgo(comment.createdUtc)} • ${comment.subreddit}")
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.combinedClickable(onClick = onOpen).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(comment.postTitle, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = muted, inlineContent = distinguishedMarks())
            RedditBodyText(rememberRedditHtml(comment.bodyHtml), MaterialTheme.typography.bodyMedium.copy(lineHeight = TextUnit.Unspecified))
        }
    }
}

/**
 * Sync's sort drop-down. Top and Controversial open their time ranges in place, with a "..." row
 * to step back out, as Sync does.
 */
@Composable
internal fun SortSheet(current: Sort, options: List<SortKind>, onDismiss: () -> Unit, onPick: (Sort) -> Unit) {
    var timedKind by remember { mutableStateOf<SortKind?>(null) }
    fun pick(sort: Sort) {
        onDismiss()
        onPick(sort)
    }
    TopSheet(onDismiss = onDismiss) {
        val kind = timedKind
        if (kind == null) {
            SheetTitle("Subreddit sort")
            options.forEach { option ->
                OptionRow(
                    label = option.label,
                    icon = sortIcon(option),
                    selected = option == current.kind,
                    onClick = { if (option.timed) timedKind = option else pick(Sort(option)) },
                    trailing = if (option.timed) {
                        { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.padding(end = 8.dp)) }
                    } else null,
                )
            }
        } else {
            SheetTitle(kind.label)
            OptionRow(label = "...", icon = null, onClick = { timedKind = null })
            TimeRange.entries.forEach { range ->
                OptionRow(
                    label = range.label,
                    icon = null,
                    selected = current.kind == kind && current.time == range,
                    onClick = { pick(Sort(kind, range)) },
                )
            }
        }
    }
}

/**
 * Sync's Change view: picks this feed's layout (other feeds keep theirs). Cancel | Customize view sit under the
 * rows; Customize view opens the View type screen on the current layout.
 */
@Composable
private fun ChangeViewSheet(container: AppContainer, subreddit: String?, current: PostLayout, onDismiss: () -> Unit) {
    val nav = LocalNavigator.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { SheetHandle() }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle("Change view", topPadding = 0.dp)
            PostLayout.entries.forEach { layout ->
                OptionRow(
                    label = layout.label,
                    icon = null,
                    onClick = {
                        container.settings.update { it.copy(feedLayouts = it.feedLayouts + (Settings.feedKey(subreddit) to layout)) }
                        onDismiss()
                    },
                    radio = layout == current,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        nav.push(CustomizeViewRoute(current))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Customize view") }
            }
        }
    }
}
