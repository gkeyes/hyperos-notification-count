package dev.hyperos.notificationcount.settings

import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.delay

/**
 * Motion tokens of the design system. Springs everywhere a value follows the user; no logic
 * waits for an animation, and with the system animator scale at 0 every spec snaps.
 */
internal object Motion {
    /** Position and size. */
    const val SPATIAL_DAMPING = 0.86f
    const val SPATIAL_STIFFNESS = 380f

    /** Colour and opacity. */
    const val EFFECTS_DAMPING = 1f
    const val EFFECTS_STIFFNESS = 600f

    const val PRESS_IN_DAMPING = 1f
    const val PRESS_IN_STIFFNESS = 1500f
    const val PRESS_RELEASE_DAMPING = 0.62f
    const val PRESS_RELEASE_STIFFNESS = 420f

    /** Pressed scale for cards and buttons, and for full-width list rows. */
    const val PRESSED_SCALE = 0.965f
    const val PRESSED_SCALE_ROW = 0.985f

    /** First entry: first 8 blocks, 26ms apart, rising from +16dp. */
    const val STAGGER_STEP_MS = 26L
    const val STAGGER_MAX_ITEMS = 8
    val enterOffset = 16.dp

    fun <T> spatial(reduce: Boolean = false): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(SPATIAL_DAMPING, SPATIAL_STIFFNESS)

    fun <T> effects(reduce: Boolean = false): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(EFFECTS_DAMPING, EFFECTS_STIFFNESS)

    fun <T> pressIn(reduce: Boolean = false): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(PRESS_IN_DAMPING, PRESS_IN_STIFFNESS)

    fun <T> pressRelease(reduce: Boolean = false): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(PRESS_RELEASE_DAMPING, PRESS_RELEASE_STIFFNESS)

    fun spatialSize(reduce: Boolean = false): FiniteAnimationSpec<IntSize> =
        if (reduce) snap() else spring(SPATIAL_DAMPING, SPATIAL_STIFFNESS, IntSize.VisibilityThreshold)

    fun spatialOffset(reduce: Boolean = false): FiniteAnimationSpec<IntOffset> =
        if (reduce) snap() else spring(SPATIAL_DAMPING, SPATIAL_STIFFNESS, IntOffset.VisibilityThreshold)

    /** Text/content swap: fade-through with the effects spring. */
    fun fadeThrough(reduce: Boolean): ContentTransform =
        if (reduce) EnterTransition.None togetherWith ExitTransition.None
        else ContentTransform(
            targetContentEnter = fadeIn(effects()),
            initialContentExit = fadeOut(effects()),
            sizeTransform = SizeTransform(clip = false) { _, _ -> spatialSize() },
        )

    /** Page push: a sub-page enters from the end edge; going back reverses it. */
    fun pagePush(forward: Boolean, reduce: Boolean): ContentTransform =
        if (reduce) EnterTransition.None togetherWith ExitTransition.None
        else ContentTransform(
            targetContentEnter = slideInHorizontally(spatialOffset()) { if (forward) it / 4 else -it / 4 } +
                fadeIn(effects()),
            initialContentExit = slideOutHorizontally(spatialOffset()) { if (forward) -it / 4 else it / 4 } +
                fadeOut(effects()),
            sizeTransform = null,
        )

    /** Digit roll: up when the value grows, down when it shrinks. */
    fun numberRoll(up: Boolean, reduce: Boolean): ContentTransform =
        if (reduce) EnterTransition.None togetherWith ExitTransition.None
        else ContentTransform(
            targetContentEnter = slideInVertically(spatialOffset()) { if (up) it else -it } + fadeIn(effects()),
            initialContentExit = slideOutVertically(spatialOffset()) { if (up) -it else it } + fadeOut(effects()),
            sizeTransform = SizeTransform(clip = true),
        )
}

/** True when the user turned animations off (animator duration scale = 0). */
internal val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
internal fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

internal val reduceMotion: Boolean
    @Composable get() = LocalReduceMotion.current

/**
 * Press feedback: pressIn spring to [pressedScale] with a 6% state layer, pressRelease spring
 * (slight overshoot) back. Values are read in the draw phase only.
 */
@Composable
internal fun Modifier.pressFeedback(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = Motion.PRESSED_SCALE,
    stateLayer: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    return pressAnimation(pressed && enabled, pressedScale, stateLayer)
}

/**
 * Press feedback for library components that do not expose an interaction source (Miuix
 * preference rows): observes touches on the initial pass without consuming them.
 */
@Composable
internal fun Modifier.observedPressFeedback(
    enabled: Boolean,
    pressedScale: Float = Motion.PRESSED_SCALE_ROW,
): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val observer = Modifier.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed = true
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                pressed = false
            }
        }
    }
    // Miuix rows draw their own pressed highlight, so only the scale is added here.
    return this.then(observer).pressAnimation(pressed && enabled, pressedScale, stateLayer = false)
}

@Composable
private fun Modifier.pressAnimation(pressed: Boolean, pressedScale: Float, stateLayer: Boolean): Modifier {
    val reduce = reduceMotion
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pressed, reduce) {
        progress.animateTo(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = if (pressed) Motion.pressIn(reduce) else Motion.pressRelease(reduce),
        )
    }
    val layer: Color = Family.colors.stateLayer
    val scaled = Modifier.graphicsLayer {
        val scale = 1f - (1f - pressedScale) * progress.value
        scaleX = scale
        scaleY = scale
    }
    return if (!stateLayer) this.then(scaled) else this.then(scaled).drawWithContent {
        drawContent()
        val alpha = progress.value.coerceIn(0f, 1f)
        if (alpha > 0f) drawRect(layer.copy(alpha = layer.alpha * alpha))
    }
}

/** Screens whose first-entry stagger already played in this process. */
private val playedEntrances = mutableSetOf<String>()

/**
 * First-entry reveal: fade in and rise from +16dp with the spatial spring, staggered by
 * [index]. Plays once per screen per process, never on recreation; skipped when motion is off.
 */
@Composable
internal fun Modifier.staggeredEntrance(screen: String, index: Int): Modifier {
    val reduce = reduceMotion
    val firstEntry = remember { !reduce && screen !in playedEntrances }
    val progress = remember { Animatable(if (firstEntry) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (firstEntry && progress.value < 1f) {
            delay(Motion.STAGGER_STEP_MS * index.coerceAtMost(Motion.STAGGER_MAX_ITEMS - 1))
            progress.animateTo(1f, Motion.spatial())
        }
        playedEntrances += screen
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * Motion.enterOffset.toPx()
    }
}
