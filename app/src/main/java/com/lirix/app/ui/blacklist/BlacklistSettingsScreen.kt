package com.lirix.app.ui.blacklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistSettingsScreen(
    onBack: () -> Unit,
    db: AppDatabase = AppDatabase.getInstance(LocalContext.current),
    viewModel: BlacklistViewModel = remember(db) { BlacklistViewModel(db) }
) {
    val tokens = LirixThemeTokens.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    val accessibilityManager = remember {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    val snackbarTimeoutMs = remember(accessibilityManager) {
        val isA11yActive = accessibilityManager?.isEnabled == true &&
            (accessibilityManager.isTouchExplorationEnabled || accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty())
        if (isA11yActive) 8000L else 6000L
    }

    // Handle snackbar messages with optional Undo
    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            if (uiState.showUndoForPurge) {
                val job = launch {
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = "Отменить",
                        duration = SnackbarDuration.Indefinite
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoLastPurge()
                    }
                }
                delay(snackbarTimeoutMs)
                job.cancel()
                viewModel.clearSnackbar()
            } else {
                snackbarHostState.showSnackbar(message)
                viewModel.clearSnackbar()
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.appBackground),
        containerColor = tokens.appBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.surfacePrimary)
                    .border(
                        width = 1.dp,
                        color = tokens.borderSubtle,
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp) // Touch target >= 48dp
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = tokens.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Чёрный список",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = tokens.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Правила игнорирования и авто-очистка",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = tokens.surfacePrimary,
                border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = { viewModel.openConstructor() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp), // Touch target >= 52dp
                        shape = RoundedCornerShape(tokens.pillCornerRadius.coerceAtLeast(12.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.accentPrimary,
                            contentColor = tokens.surfaceElevated
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Создать правило игнорирования",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Dashboard Metrics Banner
            DashboardBanner(
                activeRulesCount = uiState.activeRulesCount,
                totalRulesCount = uiState.rules.size
            )

            // Filter Tabs Row
            FilterTabsRow(
                activeTab = uiState.activeFilterTab,
                rules = uiState.rules,
                onTabSelected = { viewModel.setActiveFilterTab(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content: Loading, Empty State, or List
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = tokens.accentPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                uiState.filteredRules.isEmpty() -> {
                    EmptyStateView(
                        isFiltered = uiState.rules.isNotEmpty(),
                        activeTab = uiState.activeFilterTab
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = uiState.filteredRules,
                            key = { it.id }
                        ) { rule ->
                            BlacklistRuleCard(
                                rule = rule,
                                onToggle = { viewModel.toggleRule(rule) },
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deleteRule(rule.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal BottomSheet Rule Constructor
    BlacklistRuleConstructorSheet(
        isOpen = uiState.isConstructorOpen,
        draftType = uiState.draftType,
        draftPattern = uiState.draftPattern,
        draftMatchMode = uiState.draftMatchMode,
        previewCount = uiState.draftPurgePreviewCount,
        previewMatchingTracks = uiState.previewMatchingTracks,
        isCalculatingPreview = uiState.isCalculatingPreview,
        suggestions = uiState.dbSuggestions,
        onTypeChange = { viewModel.setDraftType(it) },
        onPatternChange = { viewModel.setDraftPattern(it) },
        onMatchModeChange = { viewModel.setDraftMatchMode(it) },
        onConfirm = { viewModel.saveDraftAndPurge() },
        onDismiss = { viewModel.closeConstructor() }
    )
}

@Composable
private fun DashboardBanner(
    activeRulesCount: Int,
    totalRulesCount: Int
) {
    val tokens = LirixThemeTokens.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(12.dp)),
        colors = CardDefaults.cardColors(containerColor = tokens.surfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = tokens.accentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Активные правила: $activeRulesCount",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = tokens.accentPrimary
                    )
                }

                Surface(
                    color = tokens.surfacePrimary,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
                ) {
                    Text(
                        text = "Всего правил: $totalRulesCount",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Блокировка сохранения треков из выбранных приложений и по ключевым словам в медиасессиях.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun FilterTabsRow(
    activeTab: BlacklistTab,
    rules: List<BlacklistRuleEntity>,
    onTabSelected: (BlacklistTab) -> Unit
) {
    val tokens = LirixThemeTokens.colors
    val titlesCount = remember(rules) { rules.count { it.ruleType == BlacklistRuleType.TITLE } }
    val artistsCount = remember(rules) { rules.count { it.ruleType == BlacklistRuleType.ARTIST } }
    val packagesCount = remember(rules) { rules.count { it.ruleType == BlacklistRuleType.PACKAGE_NAME } }

    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            TabChip(
                label = "Все (${rules.size})",
                selected = activeTab == BlacklistTab.ALL,
                onClick = { onTabSelected(BlacklistTab.ALL) }
            )
        }
        item {
            TabChip(
                label = "Треки ($titlesCount)",
                selected = activeTab == BlacklistTab.TITLES,
                onClick = { onTabSelected(BlacklistTab.TITLES) }
            )
        }
        item {
            TabChip(
                label = "Артисты ($artistsCount)",
                selected = activeTab == BlacklistTab.ARTISTS,
                onClick = { onTabSelected(BlacklistTab.ARTISTS) }
            )
        }
        item {
            TabChip(
                label = "Приложения ($packagesCount)",
                selected = activeTab == BlacklistTab.PACKAGES,
                onClick = { onTabSelected(BlacklistTab.PACKAGES) }
            )
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tokens = LirixThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp), // Touch target >= 48dp
        shape = RoundedCornerShape(tokens.pillCornerRadius.coerceAtLeast(8.dp)),
        color = if (selected) tokens.surfaceCard else tokens.surfacePrimary,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) tokens.accentPrimary else tokens.borderSubtle
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) tokens.accentPrimary else tokens.textSecondary
            )
        }
    }
}

@Composable
private fun BlacklistRuleCard(
    rule: BlacklistRuleEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LirixThemeTokens.colors
    val context = LocalContext.current

    // Resolve human app name using PackageManager
    val (displayPattern, subPattern) = remember(rule.pattern, rule.ruleType) {
        if (rule.ruleType == BlacklistRuleType.PACKAGE_NAME) {
            val trimmed = rule.pattern.trim()
            try {
                val pm = context.packageManager
                val appInfo = try {
                    pm.getApplicationInfo(trimmed, 0)
                } catch (_: Exception) {
                    val allApps = pm.getInstalledApplications(0)
                    allApps.firstOrNull {
                        it.packageName.equals(trimmed, ignoreCase = true) ||
                        pm.getApplicationLabel(it).toString().equals(trimmed, ignoreCase = true) ||
                        it.packageName.contains(trimmed, ignoreCase = true)
                    }
                }
                if (appInfo != null) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    Pair(label, if (appInfo.packageName != label) appInfo.packageName else null)
                } else {
                    val friendly = PACKAGE_FRIENDLY_NAMES[trimmed]
                        ?: PACKAGE_FRIENDLY_NAMES.entries.firstOrNull { it.key.contains(trimmed, ignoreCase = true) }?.value
                    if (friendly != null) Pair(friendly, trimmed) else Pair(trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, null)
                }
            } catch (_: Exception) {
                val friendly = PACKAGE_FRIENDLY_NAMES[trimmed]
                if (friendly != null) Pair(friendly, trimmed) else Pair(trimmed, null)
            }
        } else {
            Pair(rule.pattern, null)
        }
    }

    // Friendly rule title (no "Подстрока" jargon)
    val ruleTitle = when (rule.ruleType) {
        BlacklistRuleType.PACKAGE_NAME -> "Приложение: $displayPattern"
        BlacklistRuleType.TITLE -> when (rule.matchMode) {
            BlacklistMatchMode.CONTAINS_IGNORE_CASE -> "Название содержит: $displayPattern"
            BlacklistMatchMode.EXACT -> "Название совпадает: $displayPattern"
            BlacklistMatchMode.REGEX -> "Название по шаблону: $displayPattern"
        }
        BlacklistRuleType.ARTIST -> when (rule.matchMode) {
            BlacklistMatchMode.CONTAINS_IGNORE_CASE -> "Исполнитель содержит: $displayPattern"
            BlacklistMatchMode.EXACT -> "Исполнитель совпадает: $displayPattern"
            BlacklistMatchMode.REGEX -> "Исполнитель по шаблону: $displayPattern"
        }
    }

    // Type badge tokens
    val (badgeText, badgeBg, badgeTextColor) = when (rule.ruleType) {
        BlacklistRuleType.TITLE -> Triple("Трек", tokens.accentPrimary.copy(alpha = 0.15f), tokens.accentPrimary)
        BlacklistRuleType.ARTIST -> Triple("Артист", tokens.accentSecondary.copy(alpha = 0.15f), tokens.accentSecondary)
        BlacklistRuleType.PACKAGE_NAME -> Triple("Приложение", tokens.accentGlow.copy(alpha = 0.25f), tokens.accentPrimary)
    }

    val statusDesc = if (rule.isEnabled) "Активно" else "На паузе"

    val dangerColor = if (tokens.isDark) Color(0xFFE57373) else Color(0xFFC85A5A)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) tokens.surfaceCard else tokens.surfacePrimary.copy(alpha = 0.6f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (rule.isEnabled) tokens.borderSubtle else tokens.borderSubtle.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = statusDesc,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (rule.isEnabled) tokens.accentSecondary else tokens.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = ruleTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (rule.isEnabled) tokens.textPrimary else tokens.textMuted
                )

                if (subPattern != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subPattern,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = tokens.textMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = tokens.surfaceElevated,
                        checkedTrackColor = tokens.accentPrimary,
                        uncheckedThumbColor = tokens.textMuted,
                        uncheckedTrackColor = tokens.surfacePrimary
                    )
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp) // Touch target >= 48dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить правило",
                        tint = dangerColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    isFiltered: Boolean,
    activeTab: BlacklistTab
) {
    val tokens = LirixThemeTokens.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = tokens.borderSubtle,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isFiltered) "Нет правил в этой категории" else "Медиатека не фильтруется",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = tokens.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isFiltered) {
                    "Переключитесь на вкладку «Все» или создайте новое правило."
                } else {
                    "Создайте первое правило, чтобы заблокировать рекламу, голосовые сообщения из мессенджеров или нежелательных артистов."
                },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
