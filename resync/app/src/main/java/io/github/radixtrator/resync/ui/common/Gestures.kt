package io.github.radixtrator.resync.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class Axis { HORIZONTAL_RIGHT, VERTICAL }

/**
 * Watches single-finger drags in the Initial pass so it can claim them before children (pagers,
 * zoomable images, a Media3 PlayerView) do, but only once the drag is clearly along [axis].
 * Reports the drag delta and, on release, the velocity along that axis.
 */
private suspend fun PointerInputScope.claimDrags(
    axis: Axis,
    scope: CoroutineScope,
    onDelta: suspend (Float) -> Unit,
    onEnd: suspend (velocity: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // Tracked from the first touch, not just once claimed: a quick flick is over in a few events, too few to
        // measure its speed from what's left after the slop.
        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var total = Offset.Zero
        var claimed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) break // pinch: leave it to the child
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            total += delta
            tracker.addPosition(change.uptimeMillis, change.position)
            if (!claimed) {
                claimed = when (axis) {
                    Axis.VERTICAL -> abs(total.y) > viewConfiguration.touchSlop && abs(total.y) > abs(total.x) * 1.5f
                    Axis.HORIZONTAL_RIGHT -> total.x > viewConfiguration.touchSlop && total.x > abs(total.y) * 1.5f
                }
            }
            if (claimed) {
                change.consume()
                val d = if (axis == Axis.VERTICAL) delta.y else delta.x
                scope.launch { onDelta(d) }
            }
        }
        if (claimed) {
            val v = tracker.calculateVelocity()
            scope.launch { onEnd(if (axis == Axis.VERTICAL) v.y else v.x) }
        }
    }
}

/**
 * Fling media up or down to close it, like Sync's viewer. The backdrop fades as it moves. With
 * [onSwipeUp], only a downward fling closes it: swiping up calls that instead (Sync's info sheet) and
 * the media springs back. [dismissOnDrag] false (Sync's "Swipe to close" off) keeps the media from closing on a
 * fling; a swipe up still reaches [onSwipeUp].
 */
@Composable
fun DismissibleMedia(
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    dismissOnDrag: Boolean = true,
    onSwipeUp: (() -> Unit)? = null,
    /** Drawn behind the media, filling the screen; it stays put and fades with the black as the media moves. */
    backdrop: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var height by remember { mutableIntStateOf(1) }
    val swipeUp by rememberUpdatedState(onSwipeUp)
    // Read only from the draw and layer blocks below, so a drag redraws without recomposing the media.
    fun progress(): Float {
        val travel = if (swipeUp != null) offset.value.coerceAtLeast(0f) else abs(offset.value)
        return (travel / (height * 0.5f)).coerceIn(0f, 1f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height }
            .drawBehind { drawRect(Color.Black.copy(alpha = 1f - progress() * 0.85f)) }
            .pointerInput(enabled, dismissOnDrag, onSwipeUp != null) {
                if (!enabled || (!dismissOnDrag && onSwipeUp == null)) return@pointerInput
                claimDrags(
                    Axis.VERTICAL,
                    scope,
                    // Upward drags only hint at the sheet, so they move a fraction of the finger. Without
                    // dismissing, the media never moves down.
                    onDelta = {
                        val next = offset.value + if (swipeUp != null && offset.value + it < 0f) it * 0.3f else it
                        offset.snapTo(if (dismissOnDrag) next else next.coerceAtMost(0f))
                    },
                    onEnd = { velocity ->
                        val up = swipeUp
                        when {
                            up != null && offset.value < 0f -> {
                                if (offset.value < -height * 0.03f || velocity < -1500f) up()
                                offset.animateTo(0f, SettleSpec)
                            }
                            !dismissOnDrag -> offset.animateTo(0f, SettleSpec, initialVelocity = velocity)
                            abs(offset.value) > height * 0.18f || abs(velocity) > 2500f -> onDismiss()
                            else -> offset.animateTo(0f, SettleSpec, initialVelocity = velocity)
                        }
                    },
                )
            },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - progress() * 0.85f }) { backdrop() }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offset.value
                    val s = 1f - progress() * 0.15f
                    scaleX = s
                    scaleY = s
                },
        ) { content() }
    }
}

/**
 * Inside tablet mode's dual pane ([io.github.radixtrator.resync.ui.home.DualPane]), which has its own navigator for
 * the list pane: the screen beneath the pane, or null when there's none to reveal. Unset (the default) everywhere
 * else, where the nav host does the revealing.
 */
val LocalPreviousScreen = compositionLocalOf<PaneBelow?> { null }

/** The screen under a dual pane's page; [content] null means there's nothing to draw there. */
class PaneBelow(val content: (@Composable () -> Unit)?)

/** Set just before a dual pane's swipe-back pops, so the nav host skips its own pop animation (the drag played it). */
object SwipeBackState {
    @Volatile var popping = false
}

/**
 * Sync's "slide anywhere to go back": drag the page right like a card, revealing the previous screen.
 *
 * The drag is handed to the nav host as a back gesture, the same way a system edge swipe is: it slides the page
 * with its predictive back animation, showing the real screen beneath (its own scroll position and toolbar,
 * nothing redrawn) and pops it, or settles back, when the finger lets go.
 */
@Composable
fun SwipeBack(enabled: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    val pane = LocalPreviousScreen.current
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    when {
        !enabled -> content()
        pane != null || dispatcher == null -> PaneSwipeBack(pane?.content, onBack, content)
        else -> GestureSwipeBack(dispatcher, content)
    }
}

@Composable
private fun GestureSwipeBack(dispatcher: NavigationEventDispatcher, content: @Composable () -> Unit) {
    val input = remember(dispatcher) { DirectNavigationEventInput() }
    DisposableEffect(dispatcher, input) {
        dispatcher.addInput(input)
        onDispose { dispatcher.removeInput(input) }
    }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { width = it.width }
            .pointerInput(input) {
                var dragged = 0f
                var started = false
                claimDrags(
                    Axis.HORIZONTAL_RIGHT,
                    scope,
                    onDelta = {
                        if (!started) {
                            started = true
                            dragged = 0f
                            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
                        }
                        dragged = (dragged + it).coerceAtLeast(0f)
                        input.backProgressed(NavigationEvent(progress = (dragged / width).coerceIn(0f, 1f), swipeEdge = NavigationEvent.EDGE_LEFT))
                    },
                    onEnd = { velocity ->
                        if (started) {
                            started = false
                            if (dragged > width * 0.33f || velocity > 1800f) input.backCompleted() else input.backCancelled()
                        }
                    },
                )
            },
    ) { content() }
}

@Composable
private fun PaneSwipeBack(previous: (@Composable () -> Unit)?, onBack: () -> Unit, content: @Composable () -> Unit) {
    // Bounded so springing back with the finger's leftward momentum can't carry the page past its place.
    val offset = remember { Animatable(0f).apply { updateBounds(lowerBound = 0f) } }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    // Only whether a drag is under way is read in composition; how far it's got is read while drawing.
    val dragging by remember { derivedStateOf { offset.value > 0f } }
    fun progress() = (offset.value / width).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { width = it.width }
            .pointerInput(Unit) {
                claimDrags(
                    Axis.HORIZONTAL_RIGHT,
                    scope,
                    onDelta = { offset.snapTo((offset.value + it).coerceAtLeast(0f)) },
                    onEnd = { velocity ->
                        if (offset.value > width * 0.33f || velocity > 1800f) {
                            // Carry on at about the finger's speed: a fast fling finishes quickly, a slow drag glides.
                            val remaining = width - offset.value
                            val ms = (remaining / velocity.coerceAtLeast(1200f) * 1600f).toInt().coerceIn(90, 220)
                            offset.animateTo(width.toFloat(), tween(ms, easing = LinearOutSlowInEasing))
                            SwipeBackState.popping = true
                            onBack()
                        } else {
                            offset.animateTo(0f, SettleSpec, initialVelocity = velocity)
                        }
                    },
                )
            },
    ) {
        if (previous != null && dragging) {
            CompositionLocalProvider(LocalPreviousScreen provides PaneBelow(null)) { previous() }
            // A sibling of the screen above, so dimming redraws one rectangle rather than the screen under it.
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.3f * (1f - progress()))) })
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset.value
                    if (offset.value > 0f) shadowElevation = 24f
                }
                .background(MaterialTheme.colorScheme.background),
        ) { content() }
    }
}

/** Springing back after a drag that didn't go far enough: no bounce, quick, and it keeps the fling's momentum. */
private val SettleSpec = spring<Float>(stiffness = Spring.StiffnessMedium)
