@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import io.github.radixtrator.resync.data.ExploreSection
import io.github.radixtrator.resync.ui.common.Format
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.BuildConfig
import io.github.radixtrator.resync.data.ThemeMode
import io.github.radixtrator.resync.data.UserWhere
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.QuickAction
import io.github.radixtrator.resync.ui.common.QuickActionGrid
import io.github.radixtrator.resync.ui.common.SheetHandle
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.common.TopSheet
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.PostRoute
import io.github.radixtrator.resync.ui.post.RecentsSheet
import io.github.radixtrator.resync.data.NsfwMode
import io.github.radixtrator.resync.data.DataSaving
import androidx.compose.ui.graphics.vector.path
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import io.github.radixtrator.resync.ui.navigation.InboxRoute
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.Check
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LoginRoute
import io.github.radixtrator.resync.ui.navigation.Navigator
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.SettingsRoute
import io.github.radixtrator.resync.ui.settings.SettingsCategory
import io.github.radixtrator.resync.ui.navigation.SwipeModeRoute
import io.github.radixtrator.resync.ui.navigation.SubmitRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute
import io.github.radixtrator.resync.ui.theme.LocalDarkTheme

/** Sync's bottom-navigation tabs. Explore isn't a page: it opens [ExploreSheet] over the current one. */
enum class HomeTab { POSTS, INBOX }

private val NavBarHeight = 80.dp

/** Material's navigation rail width; the page and the FAB keep clear of it. */
private val RailWidth = 80.dp

private val FabSize = 56.dp

// Sync's nav labels are larger than M3's 12sp.
private val NavLabel @Composable get() = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)

/**
 * The frame shared by Sync-style top-level pages (feeds, inbox): a toolbar that hides on scroll with
 * the account avatar (or a back arrow) and a ⋮ that drops the More actions sheet; on [home] pages the
 * bottom navigation; and the ⋯ FAB (tap: More actions, long-press: jump to a subreddit).
 *
 * [content] gets the padding its list should use so items clear the toolbar, nav bar and FAB.
 */
@Composable
fun ShellScaffold(
    container: AppContainer,
    home: Boolean,
    tab: HomeTab,
    smallTitle: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    snackbar: SnackbarHostState,
    quickActions: List<QuickAction>,
    onReselectTab: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    /** Sync's toolbar search opens Explore in search mode; inside a community it can search just that one. */
    showSearch: Boolean = false,
    searchSubreddit: String? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val nav = LocalNavigator.current
    val inboxCount by container.inboxCount.collectAsStateWithLifecycle()
    var accountOpen by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var fabMoreOpen by rememberSaveable { mutableStateOf(false) }
    var explore by rememberSaveable { mutableStateOf<ExploreMode?>(null) }
    var recentsOpen by rememberSaveable { mutableStateOf(false) }
    // Explore and Recently viewed open sheets this shell owns.
    val moreActions = quickActions.map { action ->
        when (action.label) {
            ACTION_EXPLORE -> QuickAction(action.label, action.icon, action.active) { explore = ExploreMode.BROWSE }
            ACTION_RECENTLY_VIEWED -> QuickAction(action.label, action.icon, action.active) { recentsOpen = true }
            else -> action
        }
    }
    // The nav bar and FAB slide away with the toolbar, as Sync's autohide does. Its collapsedFraction is read only
    // inside graphicsLayer, so scrolling redraws them without recomposing the page.
    val toolbar = scrollBehavior.state
    // Tablet mode: on a wide screen the Posts / Explore / Inbox bar becomes a rail down the left edge, or the right
    // one with "Navigation on the right" (which brings the rail on its own, Tablet mode or not).
    val settings = LocalSettings.current
    val rail = home && (settings.tabletMode || settings.navRailRight) && LocalConfiguration.current.screenWidthDp >= 600
    val railRight = rail && settings.navRailRight
    // Drawer navigation: no bottom bar; the avatar opens a side drawer. The rail wins on wide screens.
    val drawer = home && !rail && settings.drawerNavigation
    val bar = home && !rail && !drawer
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawer && drawerState.isOpen) { scope.launch { drawerState.close() } }

    /** Closes the drawer, then runs [action]: animated for sheets over this page, at once before leaving it. */
    fun fromDrawer(leaves: Boolean, action: () -> Unit) {
        if (leaves) {
            // A page left with its drawer open would come back with it open (the drawer state is saved).
            scope.launch {
                drawerState.snapTo(DrawerValue.Closed)
                action()
            }
        } else {
            scope.launch { drawerState.close() }
            action()
        }
    }

    val page: @Composable () -> Unit = { Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = if (bar) NavBarHeight + navInset else navInset)) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    if (home) {
                        AccountButton(container, onClick = {
                            if (drawer) {
                                scope.launch { drawerState.open() }
                            } else {
                                accountOpen = true
                            }
                        })
                    } else {
                        IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                title = {
                    if (smallTitle != null) Text(smallTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    if (showSearch) IconButton(onClick = { explore = ExploreMode.SEARCH }) { Icon(Icons.Outlined.Search, "Search") }
                    actions()
                    IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, "More actions") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(start = if (rail && !railRight) RailWidth else 0.dp, end = if (railRight) RailWidth else 0.dp)) {
            content(
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    // Room for the last item to scroll clear of the nav bar and the FAB.
                    bottom = (if (bar) NavBarHeight else 0.dp) + navInset + FabSize + 24.dp,
                ),
            )
            }
            MoreFab(
                onClick = { fabMoreOpen = true },
                onLongClick = { explore = ExploreMode.SEARCH },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // Clear of a right-hand rail.
                    .padding(end = 24.dp + if (railRight) RailWidth else 0.dp, bottom = 16.dp + navInset + if (bar) NavBarHeight else 0.dp)
                    .graphicsLayer {
                        val travel = 16.dp.toPx() + FabSize.toPx() + if (bar) NavBarHeight.toPx() + navInset.toPx() else navInset.toPx()
                        translationY = toolbar.collapsedFraction * travel
                    },
            )
            if (rail) {
                androidx.compose.material3.NavigationRail(
                    modifier = Modifier.align(if (railRight) Alignment.CenterEnd else Alignment.CenterStart).fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.NavigationRailItem(
                        selected = tab == HomeTab.POSTS, onClick = { if (tab == HomeTab.POSTS) onReselectTab() else nav.popWhile { it is InboxRoute } },
                        icon = { Icon(if (tab == HomeTab.POSTS) Icons.Filled.Home else Icons.Outlined.Home, null) }, label = { Text("Posts", style = NavLabel) },
                    )
                    androidx.compose.material3.NavigationRailItem(
                        selected = false, onClick = { explore = ExploreMode.BROWSE },
                        icon = { Icon(AppIcons.Compass, null) }, label = { Text("Explore", style = NavLabel) },
                    )
                    androidx.compose.material3.NavigationRailItem(
                        selected = tab == HomeTab.INBOX, onClick = { if (tab == HomeTab.INBOX) onReselectTab() else nav.push(InboxRoute(home = true)) },
                        icon = {
                            BadgedBox(badge = { if (inboxCount > 0) Badge { Text("$inboxCount") } }) {
                                Icon(if (tab == HomeTab.INBOX) Icons.Filled.Email else Icons.Outlined.Email, null)
                            }
                        },
                        label = { Text("Inbox", style = NavLabel) },
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
            if (bar) {
                HomeNavigationBar(
                    tab = tab,
                    inboxCount = inboxCount,
                    onPosts = { if (tab == HomeTab.POSTS) onReselectTab() else nav.popWhile { it is InboxRoute } },
                    onExplore = { explore = ExploreMode.BROWSE },
                    onInbox = { if (tab == HomeTab.INBOX) onReselectTab() else nav.push(InboxRoute(home = true)) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { translationY = toolbar.collapsedFraction * size.height },
                )
            }
        }
    } }

    if (drawer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // Swipe only to close: an open-swipe would fight the feed's swipe-back and post swipes.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                HomeDrawer(
                    container = container,
                    tab = tab,
                    inboxCount = inboxCount,
                    onAccount = { fromDrawer(leaves = false) { accountOpen = true } },
                    onPosts = {
                        if (tab == HomeTab.POSTS) fromDrawer(leaves = false, onReselectTab)
                        else fromDrawer(leaves = true) { nav.popWhile { it is InboxRoute } }
                    },
                    onExplore = { fromDrawer(leaves = false) { explore = ExploreMode.BROWSE } },
                    onInbox = {
                        if (tab == HomeTab.INBOX) fromDrawer(leaves = false, onReselectTab)
                        else fromDrawer(leaves = true) { nav.push(InboxRoute(home = true)) }
                    },
                    onOpenFeed = { name -> fromDrawer(leaves = true) { openFeedFromExplore(nav, name) } },
                    onSettings = { fromDrawer(leaves = true) { nav.push(SettingsRoute()) } },
                )
            },
        ) { page() }
    } else {
        page()
    }

    if (accountOpen) AccountCard(container, onDismiss = { accountOpen = false })
    if (moreOpen) {
        TopSheet(onDismiss = { moreOpen = false }) {
            MoreActionsTitle(container)
            QuickActionGrid(moreActions, onPicked = { moreOpen = false })
        }
    }
    if (fabMoreOpen) {
        ModalBottomSheet(onDismissRequest = { fabMoreOpen = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { SheetHandle() }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                MoreActionsTitle(container, topPadding = 0.dp)
                QuickActionGrid(moreActions, onPicked = { fabMoreOpen = false })
            }
        }
    }
    if (recentsOpen) {
        val recents by container.recentPosts.posts.collectAsStateWithLifecycle()
        RecentsSheet(
            posts = recents,
            onDismiss = { recentsOpen = false },
            onPick = {
                recentsOpen = false
                nav.push(PostRoute(it.id))
            },
        )
    }
    explore?.let { mode ->
        ExploreSheet(
            container = container,
            mode = mode,
            subreddit = searchSubreddit,
            onDismiss = { explore = null },
            onOpenFeed = { name ->
                explore = null
                openFeedFromExplore(nav, name)
            },
        )
    }
}

/**
 * Explore swaps the Posts tab's feed, keeping back history, like Sync; the front page resets to the root.
 * From a non-home page (a pushed feed or the inbox) the pick still lands on the Posts tab.
 */
private fun openFeedFromExplore(nav: Navigator, name: String?) {
    val current = nav.top
    if (current is FeedRoute && current.home && current.subreddit.equals(name, ignoreCase = true)) return
    if (name == null) nav.resetTo(FeedRoute(home = true)) else nav.push(FeedRoute(name, home = true))
}

/** More actions that open sheets [ShellScaffold] owns; it binds them, since the caller builds the list before the shell exists. */
private const val ACTION_EXPLORE = "Explore"
private const val ACTION_RECENTLY_VIEWED = "Recently viewed"

/**
 * The More actions grid, in Sync's default order (Search, Profile, Hide read, About, Submit, Random,
 * Dark mode, Settings, Swipe mode, Change view, Refresh, Saved), then the optional ones (Dark overlay,
 * Data saving, Explore, ...) alphabetically
 * as Sync's Action visibility lists them. [onAbout], [onChangeView] and [onScrollToTop] only exist on some pages.
 */
@Composable
fun quickActions(
    container: AppContainer,
    onRefresh: () -> Unit,
    subreddit: String? = null,
    onAbout: (() -> Unit)? = null,
    onChangeView: (() -> Unit)? = null,
    onScrollToTop: (() -> Unit)? = null,
): List<QuickAction> {
    val nav = LocalNavigator.current
    val me = LocalAccount.current
    val dark = LocalDarkTheme.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by container.accounts.collectAsStateWithLifecycle()
    // Toggle account steps to the next saved account (from logged out: the first one).
    val nextAccount = when {
        me == null -> accounts.firstOrNull()
        accounts.size > 1 -> accounts[(accounts.indexOf(me) + 1).mod(accounts.size)]
        else -> null
    }
    return buildList {
        add(QuickAction("Search", Icons.Outlined.Search) { nav.push(SearchRoute(subreddit = subreddit)) })
        if (me != null) add(QuickAction("Profile", Icons.Outlined.Person) { nav.push(UserRoute(me)) })
        add(QuickAction("Hide read", Icons.Default.Check, active = settings.hideRead) {
            container.settings.update { it.copy(hideRead = !it.hideRead) }
        })
        onAbout?.let { add(QuickAction("About", Icons.Outlined.Info, onClick = it)) }
        add(QuickAction("Submit", Icons.Outlined.Create) { nav.push(SubmitRoute(subreddit)) })
        add(QuickAction("Random", AppIcons.Dice) { scope.launch { nav.openRandomCommunity(container.api) } })
        add(QuickAction("Dark mode", AppIcons.Moon, active = dark) {
            container.settings.update { it.copy(theme = if (dark) ThemeMode.LIGHT else ThemeMode.DARK) }
        })
        add(QuickAction("Settings", Icons.Outlined.Settings) { nav.push(SettingsRoute()) })
        add(QuickAction("Swipe mode", AppIcons.Gallery) { nav.push(SwipeModeRoute(subreddit)) })
        onChangeView?.let { add(QuickAction("Change view", AppIcons.ViewStack, onClick = it)) }
        add(QuickAction("Refresh", Icons.Outlined.Refresh, onClick = onRefresh))
        if (me != null) add(QuickAction("Saved", AppIcons.Bookmark) { nav.push(UserRoute(me, UserWhere.SAVED)) })
        add(QuickAction("Dark overlay", AppIcons.Contrast, active = settings.darkOverlay) {
            container.settings.update { it.copy(darkOverlay = !it.darkOverlay) }
        })
        // Flips between Always and Never; "On mobile data" counts as on, so a tap turns it off.
        add(QuickAction("Data saving", AppIcons.DataSaver, active = settings.dataSaving != DataSaving.NEVER) {
            container.settings.update {
                it.copy(dataSaving = if (it.dataSaving == DataSaving.NEVER) DataSaving.ALWAYS else DataSaving.NEVER)
            }
        })
        add(QuickAction(ACTION_EXPLORE, AppIcons.Compass) {})
        add(QuickAction("Filters", AppIcons.Filter) { nav.push(SettingsRoute(SettingsCategory.FILTERS)) })
        if (me != null) add(QuickAction("Inbox", Icons.Outlined.Email) { nav.push(InboxRoute()) })
        // A random NSFW community from all of Reddit (needs an account that allows NSFW).
        if (me != null && settings.nsfw != NsfwMode.HIDE) {
            add(QuickAction("Random nsfw", AppIcons.Dice) { scope.launch { nav.openRandomCommunity(container.api, nsfw = true) } })
        }
        add(QuickAction(ACTION_RECENTLY_VIEWED, AppIcons.Clock) {})
        onScrollToTop?.let { add(QuickAction("Scroll to top", AppIcons.ArrowUp, onClick = it)) }
        nextAccount?.let { next ->
            add(QuickAction("Toggle account", Icons.Outlined.AccountCircle) {
                container.switchAccount(next)
                Toast.makeText(context, "Switched to u/$next", Toast.LENGTH_SHORT).show()
            })
        }
        add(QuickAction("Watched", Icons.Outlined.Notifications) { nav.push(SettingsRoute(SettingsCategory.MESSAGING)) })
    }.filterNot { it.label in settings.hiddenActions }
}

/**
 * The More actions sheets' title, with Sync's ⋮ in the corner. Its menu is a small tree: "Action visibility ▸" opens a
 * checklist of every action (ticking one shows it in the grid at once, the menu stays open), with a back row to the
 * top level, where "Show all actions" undoes every hide.
 */
@Composable
private fun MoreActionsTitle(container: AppContainer, topPadding: Dp = 16.dp) {
    val settings = LocalSettings.current
    var menuOpen by remember { mutableStateOf(false) }
    var inVisibility by remember { mutableStateOf(false) }
    fun close() {
        menuOpen = false
        inVisibility = false
    }
    Box(Modifier.fillMaxWidth()) {
        SheetTitle("More actions", topPadding = topPadding)
        Box(Modifier.align(Alignment.TopEnd).padding(top = (topPadding - 12.dp).coerceAtLeast(0.dp), end = 4.dp)) {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More actions options") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = ::close) {
                if (!inVisibility) {
                    DropdownMenuItem(
                        text = { Text("Action visibility") },
                        trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        onClick = { inVisibility = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Show all actions") },
                        enabled = settings.hiddenActions.isNotEmpty(),
                        onClick = {
                            container.settings.update { it.copy(hiddenActions = emptySet()) }
                            close()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Action visibility", style = MaterialTheme.typography.titleSmall) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") },
                        onClick = { inVisibility = false },
                    )
                    HorizontalDivider()
                    QUICK_ACTION_LABELS.sortedBy { it.lowercase() }.forEach { label ->
                        val shown = label !in settings.hiddenActions
                        val toggle = {
                            container.settings.update {
                                it.copy(hiddenActions = if (shown) it.hiddenActions + label else it.hiddenActions - label)
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(label) },
                            trailingIcon = { Checkbox(checked = shown, onCheckedChange = { toggle() }) },
                            onClick = toggle,
                        )
                    }
                }
            }
        }
    }
}

/** Every More actions button that the sheets' ⋮ › Action visibility can switch off, in grid order. */
val QUICK_ACTION_LABELS = listOf(
    "Search", "Profile", "Hide read", "About", "Submit", "Random", "Dark mode", "Settings",
    "Swipe mode", "Change view", "Refresh", "Saved", "Dark overlay", "Data saving", ACTION_EXPLORE, "Filters", "Inbox", "Random nsfw",
    ACTION_RECENTLY_VIEWED, "Scroll to top", "Toggle account", "Watched",
)

@Composable
private fun HomeNavigationBar(
    tab: HomeTab,
    inboxCount: Int,
    onPosts: () -> Unit,
    onExplore: () -> Unit,
    onInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sync keeps unselected items at full contrast; only the pill marks the current tab.
    val colors = NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurface,
        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
    )
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        NavItem("Posts", if (tab == HomeTab.POSTS) Icons.Filled.Home else Icons.Outlined.Home, tab == HomeTab.POSTS, onPosts, colors)
        NavItem("Explore", AppIcons.Compass, false, onExplore, colors)
        NavigationBarItem(
            selected = tab == HomeTab.INBOX,
            onClick = onInbox,
            colors = colors,
            label = { Text("Inbox", style = NavLabel) },
            icon = {
                BadgedBox(badge = { if (inboxCount > 0) Badge { Text("$inboxCount") } }) {
                    Icon(if (tab == HomeTab.INBOX) Icons.Filled.Email else Icons.Outlined.Email, null)
                }
            },
        )
    }
}

/**
 * Sync's drawer navigation (Settings "Use drawer navigation"): your account, Posts / Inbox / Explore,
 * then your favorites and subscriptions (Explore's lists, same "Duplicate favorites" and hidden-section
 * choices), then Settings. The header opens the account card.
 */
@Composable
private fun HomeDrawer(
    container: AppContainer,
    tab: HomeTab,
    inboxCount: Int,
    onAccount: () -> Unit,
    onPosts: () -> Unit,
    onExplore: () -> Unit,
    onInbox: () -> Unit,
    onOpenFeed: (String?) -> Unit,
    onSettings: () -> Unit,
) {
    val me by container.username.collectAsStateWithLifecycle()
    val avatar by container.avatar.collectAsStateWithLifecycle()
    val explore by container.explore.state.collectAsStateWithLifecycle()
    val subscriptions by container.subscriptions.collectAsStateWithLifecycle()
    val icons by container.subredditIcons.collectAsStateWithLifecycle()
    val favorites = if (ExploreSection.FAVORITES in explore.hidden) emptyList() else explore.favorites
    val subscribed = if (ExploreSection.SUBSCRIPTIONS in explore.hidden) {
        emptyList()
    } else {
        (if (explore.duplicateFavorites) subscriptions else subscriptions.filterNot(explore::isFavorite))
            .filterNot { it.startsWith("u_", ignoreCase = true) }
    }
    val itemPadding = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)

    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "account") { Column {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onAccount).padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(avatar, size = 48)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        me ?: "Anonymous",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.KeyboardArrowDown, "Accounts")
                }
                HorizontalDivider(Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            } }
            item(key = "posts") {
                NavigationDrawerItem(
                    label = { Text("Posts") },
                    icon = { Icon(if (tab == HomeTab.POSTS) Icons.Filled.Home else Icons.Outlined.Home, null) },
                    selected = tab == HomeTab.POSTS,
                    onClick = onPosts,
                    modifier = itemPadding,
                )
            }
            item(key = "inbox") {
                NavigationDrawerItem(
                    label = { Text("Inbox") },
                    icon = { Icon(if (tab == HomeTab.INBOX) Icons.Filled.Email else Icons.Outlined.Email, null) },
                    badge = { if (inboxCount > 0) Badge { Text("$inboxCount") } },
                    selected = tab == HomeTab.INBOX,
                    onClick = onInbox,
                    modifier = itemPadding,
                )
            }
            item(key = "explore") {
                NavigationDrawerItem(
                    label = { Text("Explore") },
                    icon = { Icon(AppIcons.Compass, null) },
                    selected = false,
                    onClick = onExplore,
                    modifier = itemPadding,
                )
            }
            drawerFeeds(ExploreSection.FAVORITES.header, favorites, icons, itemPadding, onOpenFeed)
            drawerFeeds(ExploreSection.SUBSCRIPTIONS.header, subscribed, icons, itemPadding, onOpenFeed)
            item(key = "settings") { Column {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Outlined.Settings, null) },
                    selected = false,
                    onClick = onSettings,
                    modifier = itemPadding,
                )
                Spacer(Modifier.height(8.dp))
            } }
        }
    }
}

/** A drawer section: a small header, then a row per feed that opens it on the Posts tab. */
private fun LazyListScope.drawerFeeds(
    title: String,
    names: List<String>,
    icons: Map<String, String>,
    itemPadding: Modifier,
    onOpenFeed: (String?) -> Unit,
) {
    if (names.isEmpty()) return
    item(key = "header:$title") { Column {
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
        )
    } }
    items(names, key = { "$title:$it" }) { name ->
        NavigationDrawerItem(
            label = { Text(Format.feedName(name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            icon = { FeedIcon(name, icons[name.lowercase()]) },
            selected = false,
            onClick = { onOpenFeed(name.ifEmpty { null }) },
            modifier = itemPadding,
        )
    }
}

@Composable
private fun RowScope.NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    colors: androidx.compose.material3.NavigationBarItemColors,
) {
    NavigationBarItem(selected = selected, onClick = onClick, colors = colors, label = { Text(label, style = NavLabel) }, icon = { Icon(icon, null) })
}

/** Sync's FAB: a rounded square with ⋯. */
@Composable
private fun MoreFab(onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 3.dp,
        modifier = modifier.size(FabSize),
    ) {
        Box(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MoreVert, "More actions", modifier = Modifier.rotate(90f))
        }
    }
}

@Composable
private fun AccountButton(container: AppContainer, onClick: () -> Unit) {
    val avatar by container.avatar.collectAsStateWithLifecycle()
    IconButton(onClick = onClick) { Avatar(avatar, size = 30) }
}

@Composable
private fun Avatar(url: String?, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = "Account", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.Outlined.AccountCircle, "Account", modifier = Modifier.size((size * 0.8f).dp))
        }
    }
}

/** The account switcher Sync shows under the avatar: accounts, add account, then profile and settings. */
@Composable
private fun AccountCard(container: AppContainer, onDismiss: () -> Unit) {
    val nav = LocalNavigator.current
    val me by container.username.collectAsStateWithLifecycle()
    val accounts by container.accounts.collectAsStateWithLifecycle()
    val avatar by container.avatar.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    fun go(action: () -> Unit) {
        onDismiss()
        action()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss, indication = null, interactionSource = null)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 56.dp)
                    .fillMaxWidth()
                    .clickable(onClick = {}, indication = null, interactionSource = null),
            ) {
                Column {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(avatar, size = 48)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            me ?: "Anonymous",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (me != null) IconButton(onClick = { confirmLogout = true }) { Icon(AppIcons.RemoveCircle, "Log out") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    accounts.filter { it != me }.forEach { name ->
                        CardRow(name, Icons.Outlined.AccountCircle) { go { container.switchAccount(name) } }
                    }
                    if (me != null) CardRow("Anonymous", Icons.Outlined.AccountCircle) { go { container.switchAccount(null) } }
                    CardRow("Add account", AppIcons.PersonAdd) { go { nav.push(LoginRoute) } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    me?.let { name -> CardRow("Profile", Icons.Outlined.Person) { go { nav.push(UserRoute(name)) } } }
                    CardRow("Settings", Icons.Outlined.Settings) { go { nav.push(SettingsRoute()) } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "Resync ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out u/$me?") },
            text = { Text("Resync forgets this account on this device.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; go(container::logout) }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CardRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
