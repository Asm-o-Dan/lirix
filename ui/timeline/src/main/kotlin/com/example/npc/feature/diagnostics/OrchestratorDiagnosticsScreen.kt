package com.example.npc.feature.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.npc.core.storage.bank.TemplateBankManager
import com.example.npc.core.storage.dao.DynamicTemplateDao
import com.example.npc.feature.diagnostics.vm.DiagnosticsViewModel

@Composable
fun OrchestratorDiagnosticsScreen(
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onOpenTemplateInEditor: ((templateId: String) -> Unit)? = null
) {
    val viewModel = remember { DiagnosticsViewModel() }
    com.example.npc.feature.diagnostics.ui.OrchestratorDiagnosticsScreen(
        viewModel = viewModel,
        modifier = modifier,
        onBackClick = onBackClick,
        onOpenTemplateInEditor = onOpenTemplateInEditor
    )
}

@Composable
fun OrchestratorDiagnosticsScreen(
    templateBankManager: TemplateBankManager?,
    dynamicTemplateDao: DynamicTemplateDao?,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onOpenTemplateInEditor: ((templateId: String) -> Unit)? = null
) {
    val viewModel = remember(templateBankManager, dynamicTemplateDao) {
        DiagnosticsViewModel(
            templateBankManager = templateBankManager,
            dynamicTemplateDao = dynamicTemplateDao
        )
    }
    com.example.npc.feature.diagnostics.ui.OrchestratorDiagnosticsScreen(
        viewModel = viewModel,
        modifier = modifier,
        onBackClick = onBackClick,
        onOpenTemplateInEditor = onOpenTemplateInEditor
    )
}

@Composable
fun OrchestratorDiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onOpenTemplateInEditor: ((templateId: String) -> Unit)? = null
) {
    com.example.npc.feature.diagnostics.ui.OrchestratorDiagnosticsScreen(
        viewModel = viewModel,
        modifier = modifier,
        onBackClick = onBackClick,
        onOpenTemplateInEditor = onOpenTemplateInEditor
    )
}
