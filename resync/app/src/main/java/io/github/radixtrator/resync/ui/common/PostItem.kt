@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.common

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import io.github.radixtrator.resync.data.PostMedia
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.radixtrator.resync.data.NsfwMode
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.SwipeAction
import io.github.radixtrator.resync.notify.Notifications
import io.github.radixtrator.resync.ui.feed.AddToMultiSheet
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalContainer
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.UserRoute

/** Things a list can do to a post; implemented by [ListingViewModel]. */
class PostCallbacks(
    val onVote: (Post, Int) -> Unit,
    val onToggleSave: (Post) -> Unit,
    val onHide: ((Post) -> Unit)?,
    val onDelete: (Post) -> Unit,
    val onMarkRead: (Post) -> Unit,
)

fun ListingViewModel<*>.postCallbacks(allowHide: Boolean = true) = PostCallbacks(
    onVote = ::vote,
    onToggleSave = ::toggleSave,
    onHide = if (allowHide) ::hide else null,
    onDelete = ::delete,
    onMarkRead = ::markRead,
)

/** Applies the NSFW "hide" setting to a list of posts. */
@Composable
fun <T> List<T>.visiblePosts(postOf: (T) -> Post?): List<T> {
    val hideNsfw = LocalSettings.current.nsfw == NsfwMode.HIDE
    return remember(this, hideNsfw) { if (hideNsfw) filterNot { postOf(it)?.over18 == true } else this }
}

/**
 * A post in [layout] (by default the user's global choice). Feeds lay items out in a grid that owns
 * the spacing; elsewhere ([standalone]) card layouts get their own margins.
 */
@Composable
fun PostListItem(
    post: Post,
    showSubreddit: Boolean,
    callbacks: PostCallbacks,
    layout: PostLayout = LocalSettings.current.layout,
    standalone: Boolean = true,
) {
    val nav = LocalNavigator.current
    var sheetOpen by remember { mutableStateOf(false) }
    val actions = remember(post, callbacks) {
        PostActions(
            onOpen = { nav.openPost(post) },
            onMedia = { nav.openMedia(post) },
            onMore = { sheetOpen = true },
            onVote = { callbacks.onVote(post, it) },
            onToggleSave = { callbacks.onToggleSave(post) },
            // Customize view's Hide button shows only where the list can hide.
            onHide = callbacks.onHide?.let { hide -> { hide(post) } },
        )
    }
    val settings = LocalSettings.current
    val margins = if (standalone) Modifier.padding(horizontal = layout.gutter, vertical = layout.spacing / 2) else Modifier
    // Hide only exists where the list can drop the post (not on profiles' own lists, for instance).
    val canHide = callbacks.onHide != null
    val stages = remember(settings.postSwipeStages, canHide) { settings.postSwipeStages.filter { it != SwipeAction.HIDE || canHide } }
    SwipeActions(
        stages = stages,
        enabled = settings.postSwipe,
        shape = layout.swipeShape,
        modifier = margins,
        onAction = { action ->
            when (action) {
                SwipeAction.UPVOTE -> callbacks.onVote(post, if (post.likes == true) 0 else 1)
                SwipeAction.DOWNVOTE -> callbacks.onVote(post, if (post.likes == false) 0 else -1)
                SwipeAction.SAVE -> callbacks.onToggleSave(post)
                SwipeAction.PROFILE -> nav.push(UserRoute(post.author))
                SwipeAction.SHARE -> nav.share("https://www.reddit.com${post.permalink}", post.title)
                SwipeAction.HIDE -> callbacks.onHide?.invoke(post)
                SwipeAction.MARK_READ -> callbacks.onMarkRead(post)
                SwipeAction.REPLY -> Unit
            }
        },
    ) { PostLayoutItem(layout, post, showSubreddit, actions) }
    if (sheetOpen) {
        PostActionsSheet(
            post = post,
            onDismiss = { sheetOpen = false },
            onHide = callbacks.onHide?.let { hide -> { hide(post) } },
            onDelete = { callbacks.onDelete(post) },
            onMarkRead = { callbacks.onMarkRead(post) },
        )
    }
}

/**
 * Sync's "Post options" sheet: the actions Resync has, in Sync's order and groups. [onMarkRead] is null
 * in the post header, where Sync leaves it out.
 */
@Composable
fun PostActionsSheet(
    post: Post,
    onDismiss: () -> Unit,
    onHide: (() -> Unit)?,
    onDelete: () -> Unit,
    onMarkRead: (() -> Unit)? = null,
) {
    val nav = LocalNavigator.current
    val clipboard = LocalClipboardManager.current
    val isOwn = LocalAccount.current?.equals(post.author, ignoreCase = true) == true
    val permalink = "https://www.reddit.com${post.permalink}"
    var copying by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var includeTitle by remember { mutableStateOf(false) }
    var filtering by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var addingToMulti by remember { mutableStateOf(false) }
    val container = LocalContainer.current
    val context = LocalContext.current
    val watchedPosts by container.watch.posts.collectAsStateWithLifecycle()
    val watching = post.id in watchedPosts
    val domain = shortDomain(post)

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun watch() {
        container.setWatchingPost(post, true)
        toast("Watching: you'll get a notification when new comments arrive")
        onDismiss()
    }

    // The sheet stays open while Android asks, so the answer still has somewhere to land.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) watch() else toast("Allow notifications to watch posts")
    }

    fun act(block: () -> Unit) {
        onDismiss()
        block()
    }

    fun copy(text: String) = act { clipboard.setText(AnnotatedString(text)) }

    fun filter(message: String, block: () -> Unit) = act {
        block()
        toast(message)
    }

    if (reporting) {
        ReportSheet(post.fullname, post.subreddit, comment = false, onDismiss = onDismiss)
        return
    }
    if (addingToMulti) {
        AddToMultiSheet(container, post.subreddit, onDismiss)
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { SheetHandle() }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            if (sharing) {
                // Sync's Share ⋮: what to share, optionally with the title in front.
                SheetTitle("Share", topPadding = 0.dp)
                fun shareUrl(url: String) = act { nav.share(if (includeTitle) "${post.title}\n$url" else url, post.title) }
                OptionRow("Include title", null, onClick = { includeTitle = !includeTitle }, trailing = {
                    androidx.compose.material3.Switch(checked = includeTitle, onCheckedChange = { includeTitle = it })
                })
                val preview = when (val m = post.media) {
                    is PostMedia.Image -> m.url
                    is PostMedia.Gallery -> m.images.firstOrNull()?.url
                    is PostMedia.Video -> m.url
                    else -> null
                }
                preview?.let { url -> OptionRow("Post preview", AppIcons.Image, onClick = { shareUrl(url) }) }
                OptionRow("Post comments", AppIcons.Text, onClick = { shareUrl(permalink) })
                if (!post.isSelf) OptionRow("Link", AppIcons.Link, onClick = { shareUrl(post.url) })
            } else if (copying) {
                SheetTitle("Copy", topPadding = 0.dp)
                OptionRow("Copy title", AppIcons.Text, onClick = { copy(post.title) })
                OptionRow("Copy permalink", AppIcons.Link, onClick = { copy(permalink) })
                if (!post.isSelf) OptionRow("Copy link", AppIcons.Link, onClick = { copy(post.url) })
                OptionRow("Copy username", Icons.Outlined.Person, onClick = { copy(post.author) })
            } else if (filtering) {
                SheetTitle("Filter", topPadding = 0.dp)
                OptionRow("Filter /r/${post.subreddit}", AppIcons.Compass, onClick = {
                    filter("Filtered r/${post.subreddit}") { container.filters.filterSubreddit(post.subreddit) }
                })
                OptionRow("Filter /u/${post.author}", Icons.Outlined.AccountCircle, onClick = {
                    filter("Filtered u/${post.author}") { container.filters.filterUser(post.author) }
                })
                post.flair?.takeIf { it.isNotBlank() }?.let { flair ->
                    OptionRow("Filter flair ($flair)", AppIcons.Tag, onClick = {
                        filter("Filtered flair $flair") { container.filters.filterFlair(flair) }
                    })
                }
                if (domain != null) {
                    OptionRow("Filter domain ($domain)", AppIcons.Link, onClick = {
                        filter("Filtered ${post.domain}") { container.filters.filterDomain(post.domain) }
                    })
                }
            } else {
                SheetTitle("Post options", topPadding = 0.dp)
                onHide?.let { hide -> OptionRow("Hide", Icons.Default.Close, onClick = { act(hide) }) }
                onMarkRead?.let { markRead -> OptionRow("Mark as read", Icons.Default.Check, onClick = { act(markRead) }) }
                OptionRow("Share", Icons.Outlined.Share, onClick = { act { nav.share(permalink, post.title) } }, trailing = {
                    IconButton(onClick = { sharing = true }) { Icon(Icons.Default.MoreVert, "Share as…") }
                })
                OptionRow(if (watching) "Stop watching" else "Watch", if (watching) Icons.Filled.Notifications else Icons.Outlined.Notifications, onClick = {
                    when {
                        watching -> {
                            container.setWatchingPost(post, false)
                            toast("Stopped watching")
                            onDismiss()
                        }
                        Notifications.canNotify(context) -> watch()
                        else -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                })
                if (!isOwn) OptionRow("Report", AppIcons.Report, onClick = { reporting = true })
                HorizontalDivider()
                OptionRow("/u/${post.author}", Icons.Outlined.AccountCircle, onClick = { act { nav.push(UserRoute(post.author)) } })
                OptionRow("/r/${post.subreddit}", AppIcons.Compass, onClick = { act { nav.push(FeedRoute(post.subreddit)) } })
                if (domain != null) OptionRow("Explore domain ($domain)", AppIcons.Compass, onClick = { act { nav.push(FeedRoute(domain = post.domain)) } })
                OptionRow("Open in browser", AppIcons.OpenExternal, onClick = { act { nav.openExternal(permalink) } })
                HorizontalDivider()
                OptionRow("Copy...", AppIcons.Copy, onClick = { copying = true })
                OptionRow("Filter...", AppIcons.Filter, onClick = { filtering = true })
                OptionRow("Add r/${post.subreddit} to multi...", Icons.Default.Add, onClick = { addingToMulti = true })
                if (isOwn && post.isSelf) {
                    OptionRow("Edit", Icons.Outlined.Edit, onClick = {
                        act { nav.push(ComposeRoute(ComposeMode.EDIT, target = post.fullname, quote = post.title, initial = post.selfText)) }
                    })
                }
                if (isOwn) OptionRow("Delete", Icons.Outlined.Delete, onClick = { confirmDelete = true })
            }
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            what = "post",
            onConfirm = {
                confirmDelete = false
                act(onDelete)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun ConfirmDeleteDialog(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $what?") },
        text = { Text("This can't be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
