package com.example.npc.ui.timeline.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import com.example.npc.feature.diagnostics.OrchestratorDiagnosticsScreen
import com.example.npc.feature.diagnostics.vm.DiagnosticsViewModel
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.npc.feature.editor.EditorViewModel
import com.example.npc.feature.editor.ui.TemplateEditorSheet
import com.example.npc.ui.timeline.model.CategoryFilter
import com.example.npc.ui.timeline.model.EventUiModel
import com.example.npc.ui.timeline.model.SourceFilter
import com.example.npc.ui.timeline.model.TimelineUiEffect
import com.example.npc.ui.timeline.ui.components.CategoryCorrectionDialog
import com.example.npc.ui.timeline.ui.components.CategoryFilterChips
import com.example.npc.ui.timeline.ui.components.DeleteConfirmationDialog
import com.example.npc.ui.timeline.ui.components.EmptyTimelineView
import com.example.npc.ui.timeline.ui.components.EventDetailsDialog
import com.example.npc.ui.timeline.ui.components.SourceHealthHeader
import com.example.npc.ui.timeline.ui.components.TimelineItemRow
import com.example.npc.ui.timeline.ui.components.TimelineSearchBar
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onNavigateToDetails: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onOpenDiagnostics: (() -> Unit)? = null,
    diagnosticsViewModel: DiagnosticsViewModel? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showInternalDiagnostics by remember { mutableStateOf(false) }

    if (showInternalDiagnostics) {
        val diagVm = remember {
            diagnosticsViewModel ?: DiagnosticsViewModel(
                templateBankManager = viewModel.templateBankManager
            )
        }
        OrchestratorDiagnosticsScreen(
            viewModel = diagVm,
            onBackClick = { showInternalDiagnostics = false },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 5 && total > 0
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is TimelineUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is TimelineUiEffect.ShareJsonExport -> {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, effect.json)
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Экспорт базы данных")
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareIntent)
                }
                is TimelineUiEffect.ShareJsonFile -> {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, effect.jsonContent)
                        putExtra(Intent.EXTRA_TITLE, effect.filename)
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Экспорт файла JSON")
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareIntent)
                }
                is TimelineUiEffect.CopyToClipboard -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText(effect.label, effect.text)
                    clipboard?.setPrimaryClip(clip)
                    snackbarHostState.showSnackbar("JSON скопирован в буфер обмена")
                }
            }
        }
    }

    if (uiState.isDeleteConfirmationVisible) {
        DeleteConfirmationDialog(
            onConfirm = { viewModel.onConfirmDeleteAll() },
            onDismiss = { viewModel.onDismissDeleteDialog() }
        )
    }

    uiState.eventForCategoryCorrection?.let { event ->
        CategoryCorrectionDialog(
            currentCategory = event.category,
            onCategorySelected = { newCat ->
                viewModel.onCategoryCorrected(event.id, newCat)
            },
            onDismissRequest = { viewModel.onDismissCategoryCorrection() }
        )
    }

    var editingEventForTemplate by remember { mutableStateOf<EventUiModel?>(null) }

    uiState.selectedEventForDetails?.let { event ->
        EventDetailsDialog(
            event = event,
            onCategoryCorrectionClick = {
                viewModel.onOpenCategoryCorrection(event)
            },
            onDismissRequest = { viewModel.onDismissEventDetails() },
            onCreateTemplateClick = {
                viewModel.onDismissEventDetails()
                editingEventForTemplate = event
            },
            onCopyJson = { json ->
                viewModel.onCopyPayloadJson(json)
            }
        )
    }

    val eventToEdit = editingEventForTemplate
    if (eventToEdit != null) {
        val editorViewModel = remember(eventToEdit.id) {
            EditorViewModel(
                eventId = eventToEdit.id,
                packageName = eventToEdit.packageName ?: "com.example.bank",
                rawText = eventToEdit.text,
                onSaveTemplate = { tmpl, state ->
                    viewModel.saveTemplate(tmpl, state)
                }
            )
        }
        TemplateEditorSheet(
            eventId = eventToEdit.id,
            onDismiss = { editingEventForTemplate = null },
            viewModel = editorViewModel
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Лента событий") },
                actions = {
                    IconButton(
                        onClick = {
                            if (onOpenDiagnostics != null) {
                                onOpenDiagnostics()
                            } else {
                                showInternalDiagnostics = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Диагностика конвейера"
                        )
                    }
                    IconButton(onClick = { viewModel.onExportJsonClicked() }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Экспорт базы"
                        )
                    }
                    IconButton(onClick = { viewModel.onDeleteAllClicked() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить все данные"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TimelineSearchBar(
                query = uiState.searchQuery,
                onQueryChange = { query -> viewModel.onSearchQueryChanged(query) },
                onClear = { viewModel.onSearchQueryChanged("") }
            )

            CategoryFilterChips(
                selectedCategory = uiState.selectedCategoryFilter,
                onCategorySelected = { filter -> viewModel.onCategoryFilterSelected(filter) }
            )

            SourceHealthHeader(
                healthList = uiState.sourceHealth,
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = { filter -> viewModel.onFilterSelected(filter) }
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.events.isEmpty()) {
                EmptyTimelineView(
                    searchQuery = uiState.searchQuery,
                    selectedFilter = if (uiState.selectedFilter != SourceFilter.ALL) uiState.selectedFilter.name else null,
                    onReset = {
                        viewModel.onSearchQueryChanged("")
                        viewModel.onFilterSelected(SourceFilter.ALL)
                        viewModel.onCategoryFilterSelected(CategoryFilter.ALL)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.events,
                        key = { it.id }
                    ) { event ->
                        TimelineItemRow(
                            event = event,
                            onClick = {
                                viewModel.onEventClicked(event)
                                onNavigateToDetails(event.id)
                            },
                            onCategoryClick = {
                                viewModel.onOpenCategoryCorrection(event)
                            }
                        )
                    }
                }
            }
        }
    }
}
