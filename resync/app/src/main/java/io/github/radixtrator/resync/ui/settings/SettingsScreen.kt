@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import io.github.radixtrator.resync.ui.theme.THEME_SEEDS
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings as SettingsGlyph
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.BuildConfig
import io.github.radixtrator.resync.data.Backup
import io.github.radixtrator.resync.data.BackupDocument
import io.github.radixtrator.resync.data.BackupFormatException
import io.github.radixtrator.resync.data.BackupPart
import io.github.radixtrator.resync.data.BaseFontSize
import io.github.radixtrator.resync.data.CommentCount
import io.github.radixtrator.resync.data.CommentSort
import io.github.radixtrator.resync.data.DataSaving
import io.github.radixtrator.resync.data.FilterType
import io.github.radixtrator.resync.data.Typeface
import io.github.radixtrator.resync.data.LiveRefresh
import io.github.radixtrator.resync.data.MediaPreviewSize
import io.github.radixtrator.resync.data.MessageCheck
import io.github.radixtrator.resync.data.NsfwMode
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.SwipeAction
import io.github.radixtrator.resync.data.ThemeMode
import io.github.radixtrator.resync.data.VideoQuality
import io.github.radixtrator.resync.notify.InboxWorker
import io.github.radixtrator.resync.notify.Notifications
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.navigation.CustomizeViewRoute
import io.github.radixtrator.resync.ui.navigation.FilterEditorRoute
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.SetupRoute
import io.github.radixtrator.resync.ui.navigation.SettingsRoute
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.navigation.ThemeManagementRoute
import io.github.radixtrator.resync.watch.WatchWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sync's settings categories, grouped as on its root screen. Each opens its own page; [summary] is the root
 * screen's line under the title.
 */
enum class SettingsCategory(val group: String, val title: String, val summary: String) {
    GENERAL("Appearance", "General", "Navigation, gestures, fonts & quick actions"),
    TABLET("Appearance", "Tablet", "Navigation rail, dual pane & tabbed comments"),
    THEME("Appearance", "Theme management", "Colors, saved themes & sharing"),
    DARK_MODE("Appearance", "Dark mode", "Light, dark or auto, and the dark overlay"),
    VIEW_TYPE("Appearance", "View type", "Customize how posts look"),
    COMMENTS("Appearance", "Comments", "Customize how comments look"),
    POSTS("Content", "Post options", "NSFW, flair, scores, swipe actions & more"),
    COMMENT_OPTIONS("Content", "Comment options", "Sorting, navigation, swipe actions & more"),
    MESSAGING("Content", "Messaging", "Message checks, watched communities & posts"),
    MEDIA("Content", "Media", "Closing the viewer & video playback"),
    LINKS("Content", "Link handling", "In-app browser & link cards"),
    HISTORY("Content", "History", "Marking as read & hiding read posts"),
    DATA("General", "Data options", "Data saving & video quality"),
    FILTERS("General", "Filters", "Post filters"),
    BACKUP("General", "Backup", "Backup & restore"),
    OTHER("Other", "Everything else", "Reddit API setup & app info"),
}

/** The glyph beside each category on the root screen. */
private val SettingsCategory.icon: ImageVector
    get() = when (this) {
        SettingsCategory.GENERAL -> Icons.Outlined.SettingsGlyph
        SettingsCategory.TABLET -> AppIcons.Tablet
        SettingsCategory.THEME -> AppIcons.Palette
        SettingsCategory.DARK_MODE -> AppIcons.Moon
        SettingsCategory.VIEW_TYPE -> AppIcons.ViewStack
        SettingsCategory.COMMENTS -> AppIcons.Comment
        SettingsCategory.POSTS -> AppIcons.Text
        SettingsCategory.COMMENT_OPTIONS -> AppIcons.Reply
        SettingsCategory.MESSAGING -> Icons.Outlined.Email
        SettingsCategory.MEDIA -> AppIcons.Image
        SettingsCategory.LINKS -> AppIcons.Link
        SettingsCategory.HISTORY -> AppIcons.Clock
        SettingsCategory.DATA -> AppIcons.DataTransfer
        SettingsCategory.FILTERS -> AppIcons.Filter
        SettingsCategory.BACKUP -> AppIcons.Backup
        SettingsCategory.OTHER -> Icons.Outlined.Info
    }

/** Sync's settings: the categories at [category] null, else that category's page. */
@Composable
fun SettingsScreen(container: AppContainer, category: SettingsCategory? = null) {
    val nav = LocalNavigator.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()

    SwipeBack(enabled = settings.swipeBack, onBack = nav::pop) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = { Text(category?.title ?: "Settings") },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                if (category == null) {
                    SettingsCategory.entries.groupBy { it.group }.entries.forEachIndexed { i, (group, entries) ->
                        Section(group, divider = i > 0)
                        entries.forEach { entry ->
                            ListItem(
                                headlineContent = { Text(entry.title) },
                                supportingContent = { Text(entry.summary) },
                                leadingContent = { Icon(entry.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.clickable { nav.push(SettingsRoute(entry)) },
                            )
                        }
                    }
                } else {
                    CategoryRows(container, category, settings)
                }
            }
        }
    }
}

@Composable
private fun CategoryRows(container: AppContainer, category: SettingsCategory, settings: Settings) {
    val nav = LocalNavigator.current
    val update = container.settings::update
    val context = LocalContext.current
    when (category) {
        SettingsCategory.GENERAL -> {
            Section("Navigation", divider = false)
            Toggle("Drawer navigation", "A side drawer, opened from your avatar, replaces the bottom bar", settings.drawerNavigation) { v -> update { it.copy(drawerNavigation = v) } }
            Toggle("Confirm exit", "Ask before back closes the app", settings.confirmExit) { v -> update { it.copy(confirmExit = v) } }
            Section("Gestures")
            Toggle("Slide to go back", "Drag a page to the right to close it", settings.swipeBack) { v -> update { it.copy(swipeBack = v) } }
            Section("Fonts")
            Choice("Typeface", Typeface.entries, settings.typeface, Typeface::label) { v -> update { it.copy(typeface = v) } }
            Choice("Base font size", BaseFontSize.entries, settings.baseFontSize, BaseFontSize::label) { v ->
                update { it.copy(baseFontSize = v) }
                // The scale lives in the activity's configuration, which only a restart rebuilds.
                (context as? android.app.Activity)?.recreate()
            }
        }
        SettingsCategory.TABLET -> {
            Toggle("Tablet mode", "On wide screens, a navigation rail replaces the bottom bar", settings.tabletMode) { v -> update { it.copy(tabletMode = v) } }
            Toggle(
                "Navigation on the right",
                "On wide screens, like a foldable's inner screen, move the bottom bar to a rail on the right edge for one-handed use",
                settings.navRailRight,
            ) { v -> update { it.copy(navRailRight = v) } }
            // Sync's Tablet › Options: the dual pane per orientation, on windows wide enough for two panes.
            if (settings.tabletMode) {
                Toggle("Dual pane (landscape)", "Show comments next to posts in landscape", settings.dualPaneLandscape) { v -> update { it.copy(dualPaneLandscape = v) } }
                Toggle("Dual pane (portrait)", "The same in portrait, on wide screens", settings.dualPanePortrait) { v -> update { it.copy(dualPanePortrait = v) } }
                Toggle("Tabbed comments", "Show a post and its comments as tabs", settings.tabbedComments) { v -> update { it.copy(tabbedComments = v) } }
            }
        }
        SettingsCategory.THEME -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Toggle("Material You colors", "Use your wallpaper's colors", settings.dynamicColor) { v -> update { it.copy(dynamicColor = v) } }
            }
            if (!(settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) {
                Text("Theme color", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf<Int?>(null) + THEME_SEEDS).forEach { seed ->
                        val selected = settings.seedColor == seed
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(seed?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant)
                                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { update { it.copy(seedColor = seed) } },
                        )
                    }
                }
                if (settings.seedColor != null) {
                    Toggle("Extra colorful", "Tint backgrounds and cards with the accent too", settings.extraColorful) { v -> update { it.copy(extraColorful = v) } }
                }
            }
            ListItem(
                headlineContent = { Text("Saved themes") },
                supportingContent = { Text("Preview, save, share and load themes") },
                modifier = Modifier.clickable { nav.push(ThemeManagementRoute) },
            )
        }
        SettingsCategory.DARK_MODE -> {
            Choice("Theme", ThemeMode.entries, settings.theme, ThemeMode::label) { v -> update { it.copy(theme = v) } }
            DarkOverlayRows(settings, update)
        }
        SettingsCategory.VIEW_TYPE -> {
            Choice("Default post layout", PostLayout.entries, settings.layout, PostLayout::label) { v -> update { it.copy(layout = v) } }
            ListItem(
                headlineContent = { Text("Customize view") },
                supportingContent = { Text("Each layout's previews, sizes and buttons") },
                modifier = Modifier.clickable { nav.push(CustomizeViewRoute(settings.layout)) },
            )
            Choice("Post media preview size", MediaPreviewSize.entries, settings.postMediaPreview, MediaPreviewSize::label) { v -> update { it.copy(postMediaPreview = v) } }
        }
        SettingsCategory.COMMENTS -> {
            Toggle("Show user flair", null, settings.commentFlair) { v -> update { it.copy(commentFlair = v) } }
            Toggle("Show profile pictures", "Authors' avatars in comment headers", settings.profilePictures) { v -> update { it.copy(profilePictures = v) } }
            Toggle("Hide AutoMod comments", "And the replies to them", settings.hideAutoMod) { v -> update { it.copy(hideAutoMod = v) } }
            Choice("Live comments refresh rate", LiveRefresh.entries, settings.liveRefresh, LiveRefresh::label) { v -> update { it.copy(liveRefresh = v) } }
        }
        SettingsCategory.POSTS -> {
            Choice("NSFW posts", NsfwMode.entries, settings.nsfw, NsfwMode::label) { v -> update { it.copy(nsfw = v) } }
            if (settings.nsfw == NsfwMode.BLUR) {
                Toggle("Blur NSFW only on Frontpage, Popular & All", "Show NSFW previews unblurred everywhere else", settings.blurNsfwOnlyFrontpageAndAll) { v ->
                    update { it.copy(blurNsfwOnlyFrontpageAndAll = v) }
                }
            }
            Toggle("Blur spoiler previews", null, settings.blurSpoilers) { v -> update { it.copy(blurSpoilers = v) } }
            Toggle("Show post flair", "Flair chips on posts in feeds", settings.showPostFlair) { v -> update { it.copy(showPostFlair = v) } }
            Toggle("Show post author", "In feeds", settings.showPostAuthor) { v -> update { it.copy(showPostAuthor = v) } }
            Toggle("Display short scores", "10.6k instead of 10,600", settings.shortScores) { v -> update { it.copy(shortScores = v) } }
            Section("Swipe actions")
            Toggle("Swipe posts", "Drag a post left; drag further for the next action", settings.postSwipe) { v -> update { it.copy(postSwipe = v) } }
            if (settings.postSwipe) {
                SwipeStageChoices(settings.postSwipeStages, SwipeAction.entries.filter { it.posts }) { stages -> update { it.copy(postSwipeActions = stages) } }
            }
        }
        SettingsCategory.COMMENT_OPTIONS -> {
            Choice("Default comment sort", CommentSort.entries, settings.commentSort, CommentSort::label) { v -> update { it.copy(commentSort = v) } }
            Toggle("Collapse all threads by default", "The first time you open a post", settings.collapseAllByDefault) { v -> update { it.copy(collapseAllByDefault = v) } }
            Choice("Initial comment count", CommentCount.entries, settings.commentCount, CommentCount::label) { v -> update { it.copy(commentCount = v) } }
            Toggle("Comment navigation buttons", "Jump between top-level comments", settings.commentNavigation) { v -> update { it.copy(commentNavigation = v) } }
            Toggle("Remember comment position", "Reopen posts where you left off, threads folded as you left them", settings.rememberCommentPosition) { v -> update { it.copy(rememberCommentPosition = v) } }
            Section("Swipe actions")
            Toggle("Swipe comments", "Drag a comment left; drag further for the next action", settings.commentSwipe) { v -> update { it.copy(commentSwipe = v) } }
            if (settings.commentSwipe) {
                SwipeStageChoices(settings.commentSwipeStages, SwipeAction.entries.filter { it.comments }) { stages -> update { it.copy(commentSwipeActions = stages) } }
            }
        }
        SettingsCategory.MESSAGING -> MessagingRows(container, settings)
        SettingsCategory.MEDIA -> {
            Choice("Close images by", listOf(true, false), settings.tapToClose, { if (it) "Single tap" else "Swiping up or down" }) { v -> update { it.copy(tapToClose = v) } }
            Toggle(
                "Swipe to close",
                if (settings.tapToClose) "Fling videos up or down, and images down, to close them" else "Fling videos up or down to close them",
                settings.swipeToClose,
            ) { v -> update { it.copy(swipeToClose = v) } }
            Toggle("Hold to preview", "Hold a picture in a feed to see it full screen until you let go", settings.holdToPreview) { v -> update { it.copy(holdToPreview = v) } }
            Toggle("Start videos muted", null, settings.muteVideos) { v -> update { it.copy(muteVideos = v) } }
            Toggle("Display seek bar", null, settings.videoSeekBar) { v -> update { it.copy(videoSeekBar = v) } }
            Toggle("Display speed controls", null, settings.videoSpeedControls) { v -> update { it.copy(videoSpeedControls = v) } }
        }
        SettingsCategory.LINKS -> {
            Toggle("Open links in the app", "Custom Tabs instead of your browser app", settings.inAppBrowser) { v -> update { it.copy(inAppBrowser = v) } }
            Toggle("Link cards", "Number links and list them under posts and comments", settings.linkCards) { v -> update { it.copy(linkCards = v) } }
            Toggle("GIF previews in comments", "Play GIFs under the comments that post or link them", settings.commentGifs) { v -> update { it.copy(commentGifs = v) } }
        }
        SettingsCategory.HISTORY -> {
            Toggle("Mark posts as read on scroll", "Posts you scroll past in a feed count as read", settings.markReadOnScroll) { v -> update { it.copy(markReadOnScroll = v) } }
            Toggle("Hide read posts", "Leave out posts you have already opened when a feed loads", settings.hideRead) { v -> update { it.copy(hideRead = v) } }
        }
        SettingsCategory.DATA -> {
            Choice("Data saving mode", DataSaving.entries, settings.dataSaving, DataSaving::label) { v -> update { it.copy(dataSaving = v) } }
            Text(
                "Data saving leaves feed thumbnails and previews unloaded and opens Reddit videos in SD",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Choice("HD Reddit video quality", VideoQuality.entries, settings.hdVideo, VideoQuality::label) { v -> update { it.copy(hdVideo = v) } }
        }
        SettingsCategory.FILTERS -> {
            // Sync's Post filters: a row per list, each opening its editor. Post ⋮ → Filter… adds to them too.
            val filters by container.filters.filters.collectAsStateWithLifecycle()
            FilterType.entries.forEach { type ->
                val count = type.of(filters).size
                ListItem(
                    headlineContent = { Text(type.title) },
                    supportingContent = { Text(if (count == 0) type.summary else "${type.summary} · $count") },
                    modifier = Modifier.clickable { nav.push(FilterEditorRoute(type)) },
                )
            }
        }
        SettingsCategory.BACKUP -> BackupSection(container)
        SettingsCategory.OTHER -> {
            ListItem(
                headlineContent = { Text("Reddit API") },
                supportingContent = { Text("Client ID, redirect URI, user agent") },
                modifier = Modifier.clickable { nav.push(SetupRoute) },
            )
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
        }
    }
}

/** Sync's Messaging: the message check, and the communities and posts being watched for new posts and comments. */
@Composable
private fun MessagingRows(container: AppContainer, settings: Settings) {
    val update = container.settings::update
    val watched by container.watch.watched.collectAsStateWithLifecycle()
    val watchedPosts by container.watch.posts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Allow notifications to hear about new messages", Toast.LENGTH_SHORT).show()
    }
    Choice("Periodically check for new messages", MessageCheck.entries, settings.messageCheck, MessageCheck::label) { v ->
        update { it.copy(messageCheck = v) }
        InboxWorker.schedule(context, v)
        if (v != MessageCheck.OFF && !Notifications.canNotify(context)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (settings.messageCheck != MessageCheck.OFF) {
        ListItem(
            headlineContent = { Text("Check now") },
            supportingContent = { Text("Look for new messages without waiting for the next check") },
            modifier = Modifier.clickable { InboxWorker.checkNow(context) },
        )
    }

    Section("Watched communities")
    if (watched.isEmpty()) {
        ListItem(
            headlineContent = { Text("None yet") },
            supportingContent = { Text("Tap Watch on a community to get a notification for each new post") },
        )
    }
    if (watched.isNotEmpty() || watchedPosts.isNotEmpty()) {
        ListItem(
            headlineContent = { Text("Check now") },
            supportingContent = { Text("Look for new posts and comments without waiting for the hourly check") },
            modifier = Modifier.clickable { WatchWorker.checkNow(context) },
        )
    }
    watched.values.sortedBy { it.name.lowercase() }.forEach { community ->
        ListItem(
            headlineContent = { Text("r/${community.name}") },
            supportingContent = { Text("Checked every hour") },
            trailingContent = {
                IconButton(onClick = { container.setWatching(community.name, false) }) {
                    Icon(Icons.Default.Close, "Stop watching r/${community.name}")
                }
            },
        )
    }
    if (watchedPosts.isNotEmpty()) {
        Section("Watched posts")
        watchedPosts.values.sortedBy { it.title.lowercase() }.forEach { post ->
            ListItem(
                headlineContent = { Text(post.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("r/${post.subreddit} · new comments are checked every hour") },
                trailingContent = {
                    IconButton(onClick = { container.unwatchPost(post.id) }) { Icon(Icons.Default.Close, "Stop watching") }
                },
            )
        }
    }
}

/** Relaunches the app in a fresh process, so every store reloads from preferences. */
internal fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
    launch?.component?.let { context.startActivity(Intent.makeRestartActivityTask(it)) }
    Runtime.getRuntime().exit(0)
}

/** Sync's "First / Second / Third swipe action" rows. */
@Composable
private fun SwipeStageChoices(stages: List<SwipeAction>, options: List<SwipeAction>, onChange: (List<SwipeAction>) -> Unit) {
    listOf("First", "Second", "Third").forEachIndexed { i, name ->
        Choice("$name swipe action", options, stages[i], SwipeAction::label) { v ->
            onChange(stages.toMutableList().also { it[i] = v })
        }
    }
}

/** A settings group heading in the accent color, under a divider ([divider] false at the top of a page). */
@Composable
internal fun Section(title: String, divider: Boolean = true) {
    if (divider) HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** A switch row; tapping anywhere on it flips the switch. */
@Composable
internal fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

/** A row showing [selected]; tapping it opens a dialog of radio [options]. */
@Composable
internal fun <T> Choice(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(label(selected)) },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = option == selected, role = Role.RadioButton) {
                                    open = false
                                    onPick(option)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == selected, onClick = null)
                            Text(label(option), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}
