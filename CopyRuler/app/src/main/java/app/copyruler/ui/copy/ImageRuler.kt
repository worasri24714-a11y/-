package app.copyruler.ui.copy

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.copyruler.ui.theme.RulerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Zoom and scroll state of the photo. The photo is laid out to fill the width at zoom 1;
 * offsets are in pixels and always clamped so the photo never leaves the screen.
 */
@Stable
class ImageViewerState internal constructor(
    private val scope: CoroutineScope,
    private val density: Density,
) {
    var zoom by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    private var viewport by mutableStateOf(IntSize.Zero)
    private var imageSize by mutableStateOf(IntSize.Zero)
    private var initialised = false
    private var animation: Job? = null

    val contentWidth: Float get() = viewport.width * zoom

    val contentHeight: Float
        get() = if (imageSize.width == 0) 0f else viewport.width * zoom * imageSize.height / imageSize.width

    fun setViewport(size: IntSize, bandTop: Float) {
        if (size == viewport) return
        viewport = size
        clampOffsets()
        initialiseIfReady(bandTop)
    }

    fun setImageSize(size: IntSize, bandTop: Float) {
        if (size == imageSize) return
        imageSize = size
        clampOffsets()
        initialiseIfReady(bandTop)
    }

    /** Scrolls so the band sits on the focus line, the same spot text mode uses. */
    fun revealBand(bandTop: Float, animated: Boolean = true) {
        val height = contentHeight
        if (height <= 0f || viewport.height == 0) return
        val target = clampY(viewport.height * REVEAL_LINE - bandTop * height)
        stopAnimation()
        if (!animated) {
            offsetY = target
            return
        }
        val start = offsetY
        animation = scope.launch {
            animate(start, target, animationSpec = tween(350, easing = FastOutSlowInEasing)) { value, _ ->
                offsetY = value
            }
        }
    }

    fun stopAnimation() {
        animation?.cancel()
        animation = null
    }

    /** Applies one frame of a pan/pinch gesture, zooming around [centroid]. */
    fun transform(centroid: Offset, pan: Offset, zoomChange: Float) {
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val factor = newZoom / zoom
        zoom = newZoom
        offsetX = clampX(centroid.x - (centroid.x - offsetX) * factor + pan.x)
        offsetY = clampY(centroid.y - (centroid.y - offsetY) * factor + pan.y)
    }

    fun fling(velocity: Velocity) {
        stopAnimation()
        val decay = splineBasedDecay<Float>(density)
        animation = scope.launch {
            launch {
                AnimationState(initialValue = offsetY, initialVelocity = velocity.y).animateDecay(decay) {
                    val clamped = clampY(value)
                    offsetY = clamped
                    if (clamped != value) cancelAnimation()
                }
            }
            launch {
                AnimationState(initialValue = offsetX, initialVelocity = velocity.x).animateDecay(decay) {
                    val clamped = clampX(value)
                    offsetX = clamped
                    if (clamped != value) cancelAnimation()
                }
            }
        }
    }

    /** Screen y of the band's top edge. */
    fun bandTopY(bandTop: Float): Float = offsetY + bandTop * contentHeight

    /** Whether a touch at screen [y] grabs the band; thin bands get a finger-sized hit area. */
    fun isOnBand(y: Float, bandTop: Float, bandHeight: Float, minHitHeight: Float): Boolean {
        if (contentHeight <= 0f) return false
        val top = bandTopY(bandTop)
        val bottom = top + bandHeight * contentHeight
        val centre = (top + bottom) / 2f
        val half = max((bottom - top) / 2f, minHitHeight / 2f)
        return y in (centre - half)..(centre + half)
    }

    /** Band top after dragging it [dragY] pixels from where it started. */
    fun bandTopAfterDrag(startTop: Float, dragY: Float): Float =
        if (contentHeight <= 0f) startTop else startTop + dragY / contentHeight

    /** The spot under screen [y] as a fraction of the image height, or null if off the image. */
    fun fractionAt(y: Float): Float? {
        if (contentHeight <= 0f) return null
        return ((y - offsetY) / contentHeight).takeIf { it in 0f..1f }
    }

    private fun initialiseIfReady(bandTop: Float) {
        if (initialised || viewport == IntSize.Zero || imageSize == IntSize.Zero) return
        initialised = true
        revealBand(bandTop, animated = false)
    }

    private fun clampOffsets() {
        offsetX = clampX(offsetX)
        offsetY = clampY(offsetY)
    }

    private fun clampX(x: Float): Float = x.coerceIn(min(0f, viewport.width - contentWidth), 0f)

    private fun clampY(y: Float): Float = y.coerceIn(min(0f, viewport.height - contentHeight), 0f)

    private companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
        const val REVEAL_LINE = 0.3f
    }
}

@Composable
fun rememberImageViewerState(): ImageViewerState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(scope, density) { ImageViewerState(scope, density) }
}

/**
 * The photo with a highlighter band across it. Tap to move the band, drag the band
 * itself, drag elsewhere to scroll and pinch to zoom.
 */
@Composable
fun ImageRuler(
    bitmap: ImageBitmap,
    bandTop: Float,
    bandHeight: Float,
    viewerState: ImageViewerState,
    onTap: (Float) -> Unit,
    onBandDrag: (Float) -> Unit,
    onBandDragEnd: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = RulerTheme.colors
    val currentTop by rememberUpdatedState(bandTop)
    val currentHeight by rememberUpdatedState(bandHeight)
    val tap by rememberUpdatedState(onTap)
    val drag by rememberUpdatedState(onBandDrag)
    val dragEnd by rememberUpdatedState(onBandDragEnd)

    LaunchedEffect(bitmap) {
        viewerState.setImageSize(IntSize(bitmap.width, bitmap.height), currentTop)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(colors.photoBackdrop)
            .onSizeChanged { viewerState.setViewport(it, currentTop) }
            .pointerInput(viewerState) {
                detectRulerGestures(
                    state = viewerState,
                    bandTop = { currentTop },
                    bandHeight = { currentHeight },
                    onTap = { tap(it) },
                    onBandDrag = { drag(it) },
                    onBandDragEnd = { dragEnd() },
                )
            }
            .semantics { contentDescription = description },
    ) {
        val contentHeight = viewerState.contentHeight
        if (contentHeight <= 0f) return@Canvas
        drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(viewerState.offsetX.roundToInt(), viewerState.offsetY.roundToInt()),
            dstSize = IntSize(viewerState.contentWidth.roundToInt(), contentHeight.roundToInt()),
            filterQuality = FilterQuality.High,
        )

        val top = viewerState.bandTopY(bandTop)
        val bottom = top + bandHeight * contentHeight

        // Dim everything except the band.
        if (top > 0f) {
            drawRect(colors.photoDim, topLeft = Offset.Zero, size = Size(size.width, min(top, size.height)))
        }
        if (bottom < size.height) {
            val from = max(bottom, 0f)
            drawRect(colors.photoDim, topLeft = Offset(0f, from), size = Size(size.width, size.height - from))
        }

        // Yellow tint that keeps the ink dark, like a highlighter on paper.
        drawRect(
            color = colors.highlight.copy(alpha = 0.35f),
            topLeft = Offset(0f, top),
            size = Size(size.width, bottom - top),
            blendMode = BlendMode.Multiply,
        )
        val edge = 2.dp.toPx()
        drawRect(colors.highlight, topLeft = Offset(0f, top - edge), size = Size(size.width, edge))
        drawRect(colors.highlight, topLeft = Offset(0f, bottom), size = Size(size.width, edge))

        // Grip on the right edge hints that the band can be dragged.
        val gripHeight = ((bottom - top) * 0.7f).coerceIn(12.dp.toPx(), 36.dp.toPx())
        val gripWidth = 6.dp.toPx()
        drawRoundRect(
            color = colors.highlight,
            topLeft = Offset(size.width - gripWidth - 8.dp.toPx(), (top + bottom - gripHeight) / 2f),
            size = Size(gripWidth, gripHeight),
            cornerRadius = CornerRadius(gripWidth / 2f),
        )
    }
}

/**
 * One gesture handler for everything on the photo, so taps, band drags, scrolling and
 * pinch-zoom never fight each other:
 * - touch on the band + drag → move the band
 * - touch elsewhere + drag → scroll (with fling); two fingers → zoom
 * - tap without moving → move the band to that spot
 */
private suspend fun PointerInputScope.detectRulerGestures(
    state: ImageViewerState,
    bandTop: () -> Float,
    bandHeight: () -> Float,
    onTap: (Float) -> Unit,
    onBandDrag: (Float) -> Unit,
    onBandDragEnd: () -> Unit,
) {
    val touchSlop = viewConfiguration.touchSlop
    val minBandHitHeight = 48.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        state.stopAnimation()
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        val grabbedBand = state.isOnBand(down.position.y, bandTop(), bandHeight(), minBandHitHeight)
        val dragStartTop = bandTop()
        var dragY = 0f
        var bandDragged = false
        var pastSlop = false
        var multiTouch = false
        var cancelled = false
        var panSoFar = Offset.Zero
        var zoomSoFar = 1f

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) {
                cancelled = true
                break
            }
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) break
            if (pressed > 1) multiTouch = true

            val zoomChange = event.calculateZoom()
            val pan = event.calculatePan()
            if (!pastSlop) {
                zoomSoFar *= zoomChange
                panSoFar += pan
                val zoomMotion = abs(1f - zoomSoFar) * event.calculateCentroidSize(useCurrent = false)
                pastSlop = zoomMotion > touchSlop || panSoFar.getDistance() > touchSlop
                // Keep the band under the finger: count the distance moved before the slop was passed.
                if (pastSlop) dragY = panSoFar.y - pan.y
            }
            if (pastSlop) {
                if (grabbedBand && !multiTouch) {
                    event.changes.firstOrNull { it.id == down.id }?.let { primary ->
                        dragY += primary.positionChange().y
                        bandDragged = true
                        onBandDrag(state.bandTopAfterDrag(dragStartTop, dragY))
                    }
                } else {
                    state.transform(event.calculateCentroid(useCurrent = false), pan, zoomChange)
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
            event.changes.firstOrNull { it.id == down.id && it.pressed }?.let {
                velocityTracker.addPosition(it.uptimeMillis, it.position)
            }
        }

        if (bandDragged) onBandDragEnd()
        if (cancelled) return@awaitEachGesture
        when {
            !pastSlop && !multiTouch -> state.fractionAt(down.position.y)?.let(onTap)
            pastSlop && !grabbedBand && !multiTouch -> state.fling(velocityTracker.calculateVelocity())
        }
    }
}
