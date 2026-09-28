package app.copyruler.ui.copy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.copyruler.R
import app.copyruler.ruler.Segment
import app.copyruler.ruler.SegmentUnit
import app.copyruler.ui.theme.RulerTheme

/** Where the current segment settles, as a fraction of the list height from the top. */
private const val FOCUS_LINE = 0.3f

private enum class SegmentStatus { DONE, CURRENT, PENDING }

/**
 * The source text as a list of segments. The current one sits under a highlighter
 * stroke about 30% down the screen; copied segments fade out, the rest stay dark.
 */
@Composable
fun TextRuler(
    segments: List<Segment>,
    index: Int,
    unit: SegmentUnit,
    fontSizeSp: Int,
    onSegmentClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = index)

    // Scroll whenever the position, the unit or the text changes. With the top content
    // padding below, "scroll to item" puts the item right on the focus line.
    LaunchedEffect(index, unit, segments) {
        if (segments.isNotEmpty()) listState.animateScrollToItem(index.coerceIn(0, segments.lastIndex))
    }

    BoxWithConstraints(modifier) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 20.dp,
                top = maxHeight * FOCUS_LINE,
                bottom = maxHeight * (1f - FOCUS_LINE),
            ),
        ) {
            itemsIndexed(segments, key = { _, segment -> segment.start }) { i, segment ->
                SegmentRow(
                    number = i + 1,
                    text = segment.text,
                    status = when {
                        i < index -> SegmentStatus.DONE
                        i == index -> SegmentStatus.CURRENT
                        else -> SegmentStatus.PENDING
                    },
                    fontSizeSp = fontSizeSp,
                    // In phrase mode, extra space marks where a new line of the source begins.
                    startsSourceLine = unit == SegmentUnit.PHRASE && i > 0 && segments[i - 1].line != segment.line,
                    onClick = { onSegmentClick(i) },
                )
            }
        }
    }
}

@Composable
private fun SegmentRow(
    number: Int,
    text: String,
    status: SegmentStatus,
    fontSizeSp: Int,
    startsSourceLine: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val ruler = RulerTheme.colors
    val textColor by animateColorAsState(
        targetValue = when (status) {
            SegmentStatus.DONE -> colors.onSurface.copy(alpha = 0.35f)
            SegmentStatus.CURRENT -> ruler.onHighlight
            SegmentStatus.PENDING -> colors.onSurface
        },
        label = "segmentText",
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = if (status == SegmentStatus.CURRENT) 1f else 0f,
        label = "segmentHighlight",
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val stateText = when (status) {
        SegmentStatus.DONE -> stringResource(R.string.segment_state_done)
        SegmentStatus.CURRENT -> stringResource(R.string.segment_state_current)
        SegmentStatus.PENDING -> null
    }
    val numberLabel = stringResource(R.string.segment_number, number)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, onClickLabel = numberLabel)
            .padding(top = if (startsSourceLine) 18.dp else 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) {
                if (stateText != null) stateDescription = stateText
            },
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant.copy(alpha = if (status == SegmentStatus.CURRENT) 1f else 0.55f),
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(36.dp)
                .alignByBaseline(),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.6f).sp,
            style = MaterialTheme.typography.bodyLarge,
            onTextLayout = { layout = it },
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
                .drawBehind {
                    val result = layout ?: return@drawBehind
                    if (highlightAlpha <= 0f) return@drawBehind
                    // One marker stroke per wrapped line, like a real highlighter pen.
                    val padX = 6.dp.toPx()
                    val radius = CornerRadius(6.dp.toPx())
                    val color = ruler.highlight.copy(alpha = ruler.highlight.alpha * highlightAlpha)
                    for (line in 0 until result.lineCount) {
                        val top = result.getLineTop(line)
                        val height = result.getLineBottom(line) - top
                        val left = result.getLineLeft(line) - padX
                        val right = result.getLineRight(line) + padX
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, top + height * 0.06f),
                            size = Size(right - left, height * 0.88f),
                            cornerRadius = radius,
                        )
                    }
                },
        )
    }
}
