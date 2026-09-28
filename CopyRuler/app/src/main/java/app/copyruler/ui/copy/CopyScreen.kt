package app.copyruler.ui.copy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.copyruler.R
import app.copyruler.data.AppSettings
import app.copyruler.ruler.ImageBand
import app.copyruler.ruler.SegmentUnit
import app.copyruler.ui.common.KeepScreenOn
import app.copyruler.ui.common.VolumeKeysEffect
import app.copyruler.ui.common.rememberHaptics
import app.copyruler.ui.theme.RulerTheme
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyScreen(
    viewModel: CopyViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
    onEditText: () -> Unit,
    onOpenDocument: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics(settings.haptics)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val viewerState = rememberImageViewerState()

    // Font size follows the slider live and is saved when the slider is released.
    var fontSize by remember(settings.fontSizeSp) { mutableIntStateOf(settings.fontSizeSp) }
    // null = default: open for photos (the band height needs tuning), closed for text.
    var adjustOpen by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val showAdjust = adjustOpen ?: (state is CopyUiState.Image)

    val messages = EdgeMessages(
        endText = stringResource(R.string.copy_end_text),
        startText = stringResource(R.string.copy_start_text),
        endImage = stringResource(R.string.copy_end_image),
        startImage = stringResource(R.string.copy_start_image),
    )
    val announce: (String) -> Unit = { message ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }
    val onMove: (Boolean) -> Unit = { forward ->
        val result = if (forward) viewModel.next() else viewModel.previous()
        when (result) {
            MoveResult.Moved -> haptics.tick()
            is MoveResult.BandMoved -> {
                haptics.tick()
                viewerState.revealBand(result.top)
            }
            MoveResult.AtEdge -> {
                haptics.edge()
                announce(messages.forEdge(forward, isImage = state is CopyUiState.Image))
            }
            MoveResult.Ignored -> Unit
        }
    }
    val onNext: () -> Unit = { onMove(true) }
    val onPrevious: () -> Unit = { onMove(false) }

    KeepScreenOn()
    VolumeKeysEffect(enabled = settings.volumeKeys, onNext = onNext, onPrevious = onPrevious)

    val openDocument by rememberUpdatedState(onOpenDocument)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CopyEvent.OpenDocument -> openDocument(event.id)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when (val s = state) {
                        is CopyUiState.Text -> s.title
                        is CopyUiState.Image -> s.title
                        else -> ""
                    }
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state is CopyUiState.Text) {
                        IconButton(onClick = onEditText) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit_text))
                        }
                    }
                    if (state is CopyUiState.Image && viewModel.ocrAvailable) {
                        IconButton(onClick = viewModel::recognizeText) {
                            Icon(Icons.Outlined.DocumentScanner, contentDescription = stringResource(R.string.action_ocr))
                        }
                    }
                    if (state is CopyUiState.Text || state is CopyUiState.Image) {
                        IconToggleButton(checked = showAdjust, onCheckedChange = { adjustOpen = it }) {
                            Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.action_adjust))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (state is CopyUiState.Text || state is CopyUiState.Image) {
                RulerControls(onPrevious = onPrevious, onNext = onNext, showVolumeHint = settings.volumeKeys)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                CopyUiState.Loading -> Unit
                CopyUiState.Missing -> CenteredMessage(stringResource(R.string.copy_missing))
                is CopyUiState.Text -> {
                    val unitLabel = stringResource(
                        if (s.unit == SegmentUnit.LINE) R.string.copy_unit_line_short else R.string.copy_unit_phrase_short,
                    )
                    val counter = if (s.segments.isEmpty()) {
                        "0 / 0"
                    } else {
                        stringResource(R.string.copy_counter, s.index + 1, s.segments.size)
                    }
                    ProgressHeader(label = "$unitLabel $counter", progress = s.progress)
                    AnimatedVisibility(visible = showAdjust) {
                        TextAdjustPanel(
                            unit = s.unit,
                            onUnitChange = viewModel::setUnit,
                            fontSize = fontSize,
                            onFontSizeChange = { fontSize = it },
                            onFontSizeChangeFinished = { viewModel.setFontSize(fontSize) },
                        )
                    }
                    if (s.segments.isEmpty()) {
                        CenteredMessage(stringResource(R.string.copy_empty_text))
                    } else {
                        TextRuler(
                            segments = s.segments,
                            index = s.index,
                            unit = s.unit,
                            fontSizeSp = fontSize,
                            onSegmentClick = { index -> if (viewModel.jumpTo(index)) haptics.tick() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is CopyUiState.Image -> {
                    val label = if (s.snapping) {
                        stringResource(R.string.copy_unit_line_short) + " " +
                            stringResource(R.string.copy_counter, s.lineIndex + 1, s.lineCount)
                    } else {
                        stringResource(R.string.copy_band_position)
                    }
                    ProgressHeader(label = label, progress = s.progress)
                    AnimatedVisibility(visible = showAdjust) {
                        BandAdjustPanel(
                            state = s,
                            onSnapChange = viewModel::setSnapToLines,
                            onHeightChange = viewModel::setBandHeight,
                            onHeightChangeFinished = viewModel::commitBand,
                        )
                    }
                    val bitmap = s.bitmap
                    when {
                        bitmap != null -> ImageRuler(
                            bitmap = bitmap,
                            bandTop = s.bandTop,
                            bandHeight = s.bandHeight,
                            viewerState = viewerState,
                            onTap = { fraction ->
                                viewModel.placeBandAt(fraction)
                                haptics.tick()
                            },
                            onBandDrag = viewModel::dragBandTo,
                            onBandDragEnd = viewModel::finishDrag,
                            description = stringResource(R.string.band_description, (s.progress * 100).roundToInt()),
                            modifier = Modifier.weight(1f),
                        )
                        s.loadFailed -> CenteredMessage(stringResource(R.string.copy_image_failed))
                        else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    val imageTitle = (state as? CopyUiState.Image)?.title.orEmpty()
    val ocrDocumentTitle = stringResource(R.string.ocr_doc_title, imageTitle)
    OcrDialogs(
        state = ocrState,
        onDismiss = viewModel::dismissOcr,
        onSendToText = { text -> viewModel.sendRecognizedText(ocrDocumentTitle, text) },
    )
}

private class EdgeMessages(
    val endText: String,
    val startText: String,
    val endImage: String,
    val startImage: String,
) {
    fun forEdge(forward: Boolean, isImage: Boolean): String = when {
        forward && isImage -> endImage
        forward -> endText
        isImage -> startImage
        else -> startText
    }
}

@Composable
private fun ProgressHeader(label: String, progress: Float) {
    val animated by animateFloatAsState(targetValue = progress, label = "progress")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.percent, (progress * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun AdjustPanel(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextAdjustPanel(
    unit: SegmentUnit,
    onUnitChange: (SegmentUnit) -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    onFontSizeChangeFinished: () -> Unit,
) {
    AdjustPanel {
        Text(stringResource(R.string.unit_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        val options = listOf(SegmentUnit.LINE to R.string.unit_line, SegmentUnit.PHRASE to R.string.unit_phrase)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = unit == value,
                    onClick = { onUnitChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                ) { Text(stringResource(label)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.font_size_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.font_size_value, fontSize), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = fontSize.toFloat(),
            onValueChange = { onFontSizeChange(it.roundToInt()) },
            onValueChangeFinished = onFontSizeChangeFinished,
            valueRange = AppSettings.MIN_FONT_SIZE.toFloat()..AppSettings.MAX_FONT_SIZE.toFloat(),
            steps = AppSettings.MAX_FONT_SIZE - AppSettings.MIN_FONT_SIZE - 1,
        )
    }
}

@Composable
private fun BandAdjustPanel(
    state: CopyUiState.Image,
    onSnapChange: (Boolean) -> Unit,
    onHeightChange: (Float) -> Unit,
    onHeightChangeFinished: () -> Unit,
) {
    val height = state.bandHeight
    AdjustPanel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(
                value = state.snapToLines,
                role = Role.Switch,
                onValueChange = onSnapChange,
            ),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.snap_lines_label), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = when {
                        !state.snapToLines -> stringResource(R.string.snap_lines_off)
                        state.bitmap == null -> ""
                        state.snapping -> stringResource(R.string.snap_lines_found, state.lineCount)
                        else -> stringResource(R.string.snap_lines_none)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = state.snapToLines, onCheckedChange = null)
        }
        if (state.snapping) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.band_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.band_height_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.band_height_value, String.format(Locale.ROOT, "%.1f", height * 100)),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Slider(
                value = height,
                onValueChange = onHeightChange,
                onValueChangeFinished = onHeightChangeFinished,
                valueRange = ImageBand.MIN_HEIGHT..ImageBand.MAX_HEIGHT,
            )
            Text(
                text = stringResource(R.string.band_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The two big buttons; sized to be hit without looking while writing. */
@Composable
private fun RulerControls(onPrevious: () -> Unit, onNext: () -> Unit, showVolumeHint: Boolean) {
    val ruler = RulerTheme.colors
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (showVolumeHint) {
                Text(
                    text = stringResource(R.string.copy_volume_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onPrevious,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp),
                ) {
                    Text(
                        stringResource(R.string.action_previous),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ruler.highlight, contentColor = ruler.onHighlight),
                    border = BorderStroke(1.dp, ruler.onHighlight.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .weight(2.4f)
                        .heightIn(min = 72.dp),
                ) {
                    Text(
                        stringResource(R.string.action_next),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OcrDialogs(state: OcrState, onDismiss: () -> Unit, onSendToText: (String) -> Unit) {
    when (state) {
        OcrState.Idle -> Unit
        OcrState.Running -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.action_ocr)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.ocr_running))
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
        is OcrState.Done -> if (state.text.isBlank()) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.ocr_nothing_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.ocr_nothing_body))
                        Spacer(Modifier.height(12.dp))
                        OcrLimitNote()
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
            )
        } else {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.ocr_result_title)) },
                text = {
                    Column {
                        OcrLimitNote()
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = RulerTheme.colors.card,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = state.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onSendToText(state.text) }) { Text(stringResource(R.string.ocr_send_to_text)) }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
        OcrState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.ocr_failed_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.ocr_failed_body))
                    Spacer(Modifier.height(12.dp))
                    OcrLimitNote()
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/** States plainly that the on-device reader cannot read Thai. */
@Composable
private fun OcrLimitNote() {
    Surface(
        color = RulerTheme.colors.highlight.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.ocr_limit_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}
