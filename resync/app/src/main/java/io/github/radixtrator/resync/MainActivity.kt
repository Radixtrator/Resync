package io.github.radixtrator.resync

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import io.github.radixtrator.resync.ui.common.SwipeBackState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.radixtrator.resync.notify.Notifications
import io.github.radixtrator.resync.ui.compose.ComposeScreen
import io.github.radixtrator.resync.ui.compose.SubmitScreen
import io.github.radixtrator.resync.ui.feed.FeedScreen
import io.github.radixtrator.resync.ui.home.DualPaneDetail
import io.github.radixtrator.resync.ui.home.DualPaneList
import io.github.radixtrator.resync.ui.home.rememberDualPaneSceneStrategies
import io.github.radixtrator.resync.ui.inbox.InboxScreen
import io.github.radixtrator.resync.ui.login.LoginScreen
import io.github.radixtrator.resync.ui.media.ImageViewerScreen
import io.github.radixtrator.resync.ui.media.ScreenSnapshotHost
import io.github.radixtrator.resync.ui.media.VideoPlayerScreen
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.CustomizeViewRoute
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.FilterEditorRoute
import io.github.radixtrator.resync.ui.navigation.ImageRoute
import io.github.radixtrator.resync.ui.navigation.InboxRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalContainer
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.LocalUserTagColors
import io.github.radixtrator.resync.ui.navigation.LocalUserTags
import io.github.radixtrator.resync.ui.navigation.LocalVisited
import io.github.radixtrator.resync.ui.navigation.LoginRoute
import io.github.radixtrator.resync.ui.navigation.Navigator
import io.github.radixtrator.resync.ui.navigation.PostRoute
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.SettingsRoute
import io.github.radixtrator.resync.ui.navigation.ThemeManagementRoute
import io.github.radixtrator.resync.ui.navigation.SwipeModeRoute
import io.github.radixtrator.resync.ui.navigation.ModRoute
import io.github.radixtrator.resync.ui.feed.ModScreen
import io.github.radixtrator.resync.ui.mod.ModMailConversationScreen
import io.github.radixtrator.resync.ui.mod.ModMailScreen
import io.github.radixtrator.resync.ui.navigation.ModMailConversationRoute
import io.github.radixtrator.resync.ui.navigation.ModMailRoute
import io.github.radixtrator.resync.ui.feed.SwipeModeScreen
import io.github.radixtrator.resync.ui.navigation.SetupRoute
import io.github.radixtrator.resync.ui.navigation.SubmitRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute
import io.github.radixtrator.resync.ui.navigation.VideoRoute
import io.github.radixtrator.resync.ui.post.PostScreen
import io.github.radixtrator.resync.ui.profile.ProfileScreen
import io.github.radixtrator.resync.ui.search.SearchScreen
import io.github.radixtrator.resync.ui.settings.CustomizeViewScreen
import io.github.radixtrator.resync.ui.settings.FilterEditorScreen
import io.github.radixtrator.resync.ui.settings.SettingsScreen
import io.github.radixtrator.resync.ui.settings.ThemeManagementScreen
import io.github.radixtrator.resync.ui.setup.SetupScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.savedBaseFontScale
import io.github.radixtrator.resync.ui.theme.LocalBaseFontScale
import io.github.radixtrator.resync.ui.theme.FullSizeText
import io.github.radixtrator.resync.ui.theme.Motion
import io.github.radixtrator.resync.ui.theme.ResyncTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // Only fontScale is set, so every other setting still follows the device.
        applyOverrideConfiguration(Configuration().apply { fontScale = newBase.resources.configuration.fontScale * savedBaseFontScale(newBase.getSharedPreferences("resync", Context.MODE_PRIVATE)) })
    }

    /** A page to open, from a tapped notification (a watched community's post, an inbox message). */
    private var openRoute by mutableStateOf<NavKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRoute = Notifications.routeFrom(intent)
        val container = (application as ResyncApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalBaseFontScale provides settings.baseFontSize.scale) {
                ResyncTheme(settings) {
                    // Sync's "Dark overlay": a black layer drawn over everything after it, so it dims without
                    // taking touches. Dialogs and sheets are their own windows and stay undimmed.
                    Box(
                        Modifier.fillMaxSize().drawWithContent {
                            drawContent()
                            if (settings.darkOverlay) {
                                val alpha = settings.darkOverlayLevel.coerceIn(Settings.DARK_OVERLAY_MIN, Settings.DARK_OVERLAY_MAX)
                                drawRect(Color.Black.copy(alpha = alpha))
                            }
                        },
                    ) {
                        ResyncNavHost(container, openRoute, onRouteOpened = { openRoute = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Notifications.routeFrom(intent)?.let { openRoute = it }
    }
}

@Composable
private fun ResyncNavHost(container: AppContainer, openRoute: NavKey?, onRouteOpened: () -> Unit) {
    val backStack = rememberNavBackStack(if (container.isConfigured) homeRoute(container) else SetupRoute)
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val username by container.username.collectAsStateWithLifecycle()
    val visited = container.visited.ids.collectAsStateWithLifecycle()
    val tags by container.tags.tags.collectAsStateWithLifecycle()
    val tagColors by container.tags.colors.collectAsStateWithLifecycle()
    val currentSettings by rememberUpdatedState(settings)
    val navigator = remember(backStack) { Navigator(context, backStack, { currentSettings }) }
    LaunchedEffect(openRoute) {
        if (openRoute != null && container.isConfigured) navigator.push(openRoute)
        if (openRoute != null) onRouteOpened()
    }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalUriHandler provides navigator,
        LocalSettings provides settings,
        LocalAccount provides username,
        LocalVisited provides visited,
        LocalUserTags provides tags,
        LocalUserTagColors provides tagColors,
        LocalContainer provides container,
    ) {
        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<SetupRoute> {
                    SetupScreen(
                        container = container,
                        canGoBack = navigator.canPop,
                        onBack = navigator::pop,
                        onDone = { navigator.resetTo(homeRoute(container)) },
                    )
                }
                entry<LoginRoute> { LoginScreen(container = container, onFinished = navigator::pop) }
                // Tablet mode's dual pane puts a post beside the feed it was opened from (ui/home/DualPane.kt).
                entry<FeedRoute>(metadata = DualPaneList) { route -> FeedScreen(route, container) }
                entry<PostRoute>(metadata = DualPaneDetail) { route -> PostScreen(route, container) }
                entry<ModRoute> { route -> ModScreen(route.subreddit, container) }
                entry<ModMailRoute> { route -> ModMailScreen(route.subreddit, container) }
                entry<ModMailConversationRoute> { route -> ModMailConversationScreen(route.id, container) }
                entry<SwipeModeRoute> { route -> SwipeModeScreen(route.subreddit, container) }
                entry<ImageRoute>(metadata = MediaFade) { route -> ImageViewerScreen(route.urls, route.start, route.postId, container.downloads, onClose = navigator::pop) }
                entry<VideoRoute>(metadata = MediaFade) { route -> VideoPlayerScreen(route.url, route.loop, route.postId, container.downloads, onClose = navigator::pop, fallback = route.fallback) }
                entry<UserRoute> { route -> ProfileScreen(route.name, route.tab, container) }
                entry<InboxRoute> { route -> InboxScreen(route, container) }
                entry<SearchRoute> { route -> SearchScreen(route.query, route.subreddit, container, communitiesTab = route.communities) }
                entry<SubmitRoute> { route -> SubmitScreen(route.subreddit, container) }
                entry<ComposeRoute> { route -> ComposeScreen(route, container) }
                // Sync's settings don't shrink with its base font size.
                entry<SettingsRoute> { route -> FullSizeText { SettingsScreen(container, route.category) } }
                entry<ThemeManagementRoute> { FullSizeText { ThemeManagementScreen(container) } }
                // Its preview stays at feed size; the screen sizes its own option rows like the settings.
                entry<CustomizeViewRoute> { route -> CustomizeViewScreen(route.layout, container) }
                entry<FilterEditorRoute> { route -> FullSizeText { FilterEditorScreen(route.type, container) } }
            },
        )
        LaunchedEffect(backStack.size) { SwipeBackState.popping = false }
        // Sync's "Confirm exit": back on the last page asks first. Only enabled with one page left, when NavDisplay's
        // own back handling has nothing to pop (and SwipeBack, which only pops, isn't on root pages). It's composed
        // before NavDisplay, so handlers inside pages (the drawer, sheets, viewers' panels) register later and
        // still get back first.
        var confirmingExit by remember { mutableStateOf(false) }
        BackHandler(enabled = settings.confirmExit && backStack.size == 1) { confirmingExit = true }
        if (confirmingExit) {
            // "Don't ask again" turns General › "Confirm exit" off, on exiting.
            var neverAsk by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { confirmingExit = false },
                title = { Text("Exit Resync?") },
                text = {
                    Row(
                        Modifier.clickable { neverAsk = !neverAsk },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = neverAsk, onCheckedChange = { neverAsk = it })
                        Text("Don't ask again")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmingExit = false
                        if (neverAsk) container.settings.update { it.copy(confirmExit = false) }
                        (context as? Activity)?.finish()
                    }) { Text("Exit") }
                },
                dismissButton = { TextButton(onClick = { confirmingExit = false }) { Text("Cancel") } },
            )
        }
        val sceneStrategies = rememberDualPaneSceneStrategies()
        ScreenSnapshotHost {
        NavDisplay(
            entries = entries,
            sceneStrategies = sceneStrategies,
            onBack = navigator::pop,
            transitionSpec = { cardPush() },
            // A dual pane's own swipe-back has already slid the page away (ui/common/Gestures.kt).
            popTransitionSpec = { if (SwipeBackState.popping) noTransition() else cardPop() },
            // Slide-to-go-back and the system's edge swipe: linear, so the page stays under the finger.
            predictivePopTransitionSpec = { cardPop(GestureSpec) },
        )
        }
    }
}

/** Media viewers fade in and out; they have their own drag-to-dismiss. */
private val MediaFade = NavDisplay.transitionSpec { ContentTransform(fadeIn(Motion.enter()), fadeOut(Motion.exit())) } +
    NavDisplay.popTransitionSpec { ContentTransform(fadeIn(Motion.enter()), fadeOut(Motion.exit()), targetContentZIndex = -1f) } +
    NavDisplay.predictivePopTransitionSpec { ContentTransform(fadeIn(Motion.enter()), fadeOut(Motion.exit()), targetContentZIndex = -1f) }

private val CardSpec = Motion.page<IntOffset>()
private val GestureSpec = tween<IntOffset>(Motion.PAGE, easing = LinearEasing)

/** Sync's card push: the new screen slides in over the old one, which stays where it is underneath. */
private fun cardPush() = ContentTransform(
    targetContentEnter = slideInHorizontally(CardSpec) { it },
    // A zero-length slide, timed like the push, keeps the old screen drawn until the new one has covered it.
    initialContentExit = slideOutHorizontally(CardSpec) { 0 },
)

/** The reverse: the top card slides off to the right, over the screen under it, which doesn't move. */
private fun cardPop(spec: FiniteAnimationSpec<IntOffset> = CardSpec) = ContentTransform(
    targetContentEnter = EnterTransition.None,
    initialContentExit = slideOutHorizontally(spec) { it },
    targetContentZIndex = -1f,
)

private fun noTransition() = ContentTransform(EnterTransition.None, ExitTransition.None, targetContentZIndex = -1f)

/** The Posts tab at launch: Explore's default feed (Sync's "Set as default"), else the front page. */
private fun homeRoute(container: AppContainer) = FeedRoute(container.explore.state.value.defaultFeed, home = true)
