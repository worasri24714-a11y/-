package app.copyruler.ui.editor

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.copyruler.R
import app.copyruler.ui.theme.RulerTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    val clipboardEmpty = stringResource(R.string.editor_clipboard_empty)
    val untitled = stringResource(R.string.editor_untitled)

    val requestBack: () -> Unit = {
        if (viewModel.hasChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = viewModel.hasChanges) { showDiscardDialog = true }

    val saved by rememberUpdatedState(onSaved)
    val savedId = viewModel.savedDocumentId
    LaunchedEffect(savedId) {
        if (savedId != null) {
            viewModel.onSaveHandled()
            saved(savedId)
        }
    }

    val paste: () -> Unit = {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
        val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (pasted.isNullOrEmpty()) {
            scope.launch { snackbar.showSnackbar(clipboardEmpty) }
        } else {
            viewModel.appendPasted(pasted)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.isNew) R.string.editor_new_title else R.string.editor_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.editor_name_label)) },
                singleLine = true,
                enabled = !viewModel.loading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                AssistChip(
                    onClick = paste,
                    label = { Text(stringResource(R.string.editor_paste)) },
                    leadingIcon = {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
                    },
                    enabled = !viewModel.loading,
                )
                if (viewModel.text.isNotEmpty()) {
                    AssistChip(
                        onClick = { viewModel.onTextChange("") },
                        label = { Text(stringResource(R.string.editor_clear)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Clear, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
                        },
                    )
                }
            }
            OutlinedTextField(
                value = viewModel.text,
                onValueChange = viewModel::onTextChange,
                placeholder = { Text(stringResource(R.string.editor_text_placeholder)) },
                enabled = !viewModel.loading,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = RulerTheme.colors.card,
                    unfocusedContainerColor = RulerTheme.colors.card,
                    disabledContainerColor = RulerTheme.colors.card,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Text(
                text = stringResource(R.string.editor_counts, counts.lines, counts.phrases),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(
                onClick = { viewModel.save(untitled) },
                enabled = viewModel.text.isNotBlank() && !viewModel.saving && !viewModel.loading,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RulerTheme.colors.highlight,
                    contentColor = RulerTheme.colors.onHighlight,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) {
                Text(
                    text = stringResource(if (viewModel.isNew) R.string.editor_start else R.string.editor_save_continue),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_keep_editing))
                }
            },
        )
    }
}
