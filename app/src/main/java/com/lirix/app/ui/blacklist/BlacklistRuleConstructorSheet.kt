package com.lirix.app.ui.blacklist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleType
import com.lirix.app.ui.theme.LirixThemeTokens

internal val PACKAGE_FRIENDLY_NAMES = mapOf(
    "com.spotify.music" to "Spotify",
    "com.vkontakte.android" to "VK Музыка",
    "ru.yandex.music" to "Яндекс Музыка",
    "org.telegram.messenger" to "Telegram",
    "com.google.android.apps.youtube.music" to "YouTube Music"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistRuleConstructorSheet(
    isOpen: Boolean,
    draftType: BlacklistRuleType,
    draftPattern: String,
    draftMatchMode: BlacklistMatchMode,
    previewCount: Int,
    previewMatchingTracks: List<TrackEntity> = emptyList(),
    isCalculatingPreview: Boolean,
    suggestions: List<String> = emptyList(),
    onTypeChange: (BlacklistRuleType) -> Unit,
    onPatternChange: (String) -> Unit,
    onMatchModeChange: (BlacklistMatchMode) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    if (!isOpen) return

    val tokens = LirixThemeTokens.colors
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 100% theme adaptive warning / danger colors
    val dangerColor = if (tokens.isDark) Color(0xFFE57373) else Color(0xFFC85A5A)
    val dangerBg = if (tokens.isDark) Color(0x22E57373) else Color(0xFFFDE8E8)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.surfaceElevated,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(tokens.borderSubtle)
            )
        },
        shape = RoundedCornerShape(
            topStart = tokens.cardCornerRadius.coerceAtLeast(16.dp),
            topEnd = tokens.cardCornerRadius.coerceAtLeast(16.dp)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // 1. Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Новое правило игнорирования",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = tokens.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Игнорируемые треки не сохраняются и стираются из базы",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp) // Touch target >= 48dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = tokens.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Criteria Selector (Title, Artist, Package)
            Text(
                text = "1. Что игнорируем? (критерий)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = tokens.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CriterionChip(
                    title = "По названию",
                    icon = Icons.Default.MusicNote,
                    selected = draftType == BlacklistRuleType.TITLE,
                    onClick = { onTypeChange(BlacklistRuleType.TITLE) },
                    modifier = Modifier.weight(1f)
                )
                CriterionChip(
                    title = "По артисту",
                    icon = Icons.Default.Person,
                    selected = draftType == BlacklistRuleType.ARTIST,
                    onClick = { onTypeChange(BlacklistRuleType.ARTIST) },
                    modifier = Modifier.weight(1f)
                )
                CriterionChip(
                    title = "Приложение",
                    icon = Icons.Default.Block,
                    selected = draftType == BlacklistRuleType.PACKAGE_NAME,
                    onClick = { onTypeChange(BlacklistRuleType.PACKAGE_NAME) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Pattern Input
            Text(
                text = "2. ЗНАЧЕНИЕ ДЛЯ СОПОСТАВЛЕНИЯ",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = tokens.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val placeholderText = when (draftType) {
                BlacklistRuleType.TITLE -> "Например: «Реклама», «Voice note», «Голосовое»"
                BlacklistRuleType.ARTIST -> "Например: «Нежелательный исполнитель»"
                BlacklistRuleType.PACKAGE_NAME -> "Например: «org.telegram.messenger»"
            }

            OutlinedTextField(
                value = draftPattern,
                onValueChange = onPatternChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = placeholderText,
                        color = tokens.textMuted,
                        fontSize = 14.sp
                    )
                },
                singleLine = true,
                trailingIcon = {
                    if (draftPattern.isNotEmpty()) {
                        IconButton(
                            onClick = { onPatternChange("") },
                            modifier = Modifier.size(48.dp) // Touch target >= 48dp
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Очистить",
                                tint = tokens.textSecondary
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(10.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = tokens.textPrimary,
                    unfocusedTextColor = tokens.textPrimary,
                    focusedBorderColor = tokens.accentPrimary,
                    unfocusedBorderColor = tokens.borderSubtle,
                    cursorColor = tokens.accentPrimary,
                    focusedContainerColor = tokens.surfacePrimary,
                    unfocusedContainerColor = tokens.surfacePrimary
                )
            )

            // 4. Quick suggestions ribbon from Room database OR Matching tracks preview
            if (draftPattern.isBlank()) {
                if (suggestions.isNotEmpty()) {
                    val suggestionsTitle = when (draftType) {
                        BlacklistRuleType.TITLE -> "Треки из вашей фонотеки:"
                        BlacklistRuleType.ARTIST -> "Исполнители из вашей фонотеки:"
                        BlacklistRuleType.PACKAGE_NAME -> "Приложения из истории фонотеки:"
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = suggestionsTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { item ->
                            val isSelected = draftPattern.trim() == item.trim()
                            val displayText = if (draftType == BlacklistRuleType.PACKAGE_NAME) {
                                try {
                                    val appInfo = context.packageManager.getApplicationInfo(item, 0)
                                    context.packageManager.getApplicationLabel(appInfo).toString()
                                } catch (_: Exception) {
                                    PACKAGE_FRIENDLY_NAMES[item] ?: item
                                }
                            } else {
                                item
                            }
                            Surface(
                                onClick = { onPatternChange(item) },
                                shape = RoundedCornerShape(tokens.pillCornerRadius.coerceAtLeast(8.dp)),
                                color = if (isSelected) tokens.accentGlow else tokens.surfacePrimary,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) tokens.accentPrimary else tokens.borderSubtle
                                ),
                                modifier = Modifier.heightIn(min = 48.dp) // Touch target >= 48dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = displayText,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) tokens.accentPrimary else tokens.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Будет удалено из фонотеки ($previewCount):",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = if (previewCount > 0) dangerColor else tokens.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (previewMatchingTracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        previewMatchingTracks.forEach { track ->
                            Surface(
                                shape = RoundedCornerShape(tokens.pillCornerRadius.coerceAtLeast(8.dp)),
                                color = dangerBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, dangerColor),
                                modifier = Modifier.heightIn(min = 48.dp) // Touch target >= 48dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "❌ ${track.title} • ${track.artist}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = dangerColor
                                    )
                                }
                            }
                        }
                    }
                } else if (!isCalculatingPreview) {
                    Surface(
                        shape = RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(8.dp)),
                        color = tokens.surfacePrimary,
                        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✓ Ни один трек в фонотеке не совпадает с этим правилом",
                                fontSize = 13.sp,
                                color = tokens.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Match Mode Selector
            Text(
                text = "3. РЕЖИМ СРАВНЕНИЯ",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = tokens.textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(10.dp)))
                    .background(tokens.surfacePrimary)
                    .border(1.dp, tokens.borderSubtle, RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(10.dp)))
            ) {
                MatchModeRow(
                    title = "Содержит подстроку",
                    subtitle = "Поиск фрагмента без учета регистра (рекомендуется)",
                    selected = draftMatchMode == BlacklistMatchMode.CONTAINS_IGNORE_CASE,
                    onClick = { onMatchModeChange(BlacklistMatchMode.CONTAINS_IGNORE_CASE) }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(tokens.borderSubtle)
                )
                MatchModeRow(
                    title = "Точное совпадение",
                    subtitle = "Полное совпадение всей строки целиком",
                    selected = draftMatchMode == BlacklistMatchMode.EXACT,
                    onClick = { onMatchModeChange(BlacklistMatchMode.EXACT) }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(tokens.borderSubtle)
                )
                MatchModeRow(
                    title = "Регулярное выражение (Regex)",
                    subtitle = "Для гибких сложных шаблонов (например ^(ad|reklama))",
                    selected = draftMatchMode == BlacklistMatchMode.REGEX,
                    onClick = { onMatchModeChange(BlacklistMatchMode.REGEX) }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 6. Reactive Purge Preview Banner
            if (isCalculatingPreview) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(10.dp)))
                        .background(tokens.surfacePrimary)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = tokens.accentPrimary
                    )
                    Text(
                        text = "Проверка совпадений в медиатеке...",
                        fontSize = 13.sp,
                        color = tokens.textSecondary
                    )
                }
            } else if (draftPattern.isNotBlank()) {
                if (previewCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(10.dp)))
                            .background(dangerBg)
                            .border(1.2.dp, dangerColor, RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(10.dp)))
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Предупреждение",
                            tint = dangerColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "⚠️ В текущей медиатеке найдено: $previewCount треков.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = dangerColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Они будут БЕЗВОЗВРАТНО УДАЛЕНЫ при подтверждении!",
                                style = MaterialTheme.typography.bodySmall,
                                color = dangerColor
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(10.dp)))
                            .background(tokens.surfacePrimary)
                            .border(1.dp, tokens.borderSubtle, RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(10.dp)))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🛡",
                            fontSize = 18.sp
                        )
                        Text(
                            text = "В текущей медиатеке совпадений нет. Будущие записи будут игнорироваться.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 7. Action Button
            val isEnabled = draftPattern.isNotBlank()
            val confirmButtonText = when {
                previewCount > 0 -> "Исключить и удалить $previewCount треков"
                else -> "Создать правило игнорирования"
            }

            Button(
                onClick = onConfirm,
                enabled = isEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp), // Touch target >= 52dp
                shape = RoundedCornerShape(tokens.pillCornerRadius.coerceAtLeast(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (previewCount > 0) dangerColor else tokens.accentPrimary,
                    contentColor = tokens.surfaceElevated,
                    disabledContainerColor = tokens.surfacePrimary,
                    disabledContentColor = tokens.textMuted
                )
            ) {
                Text(
                    text = confirmButtonText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp), // Touch target >= 48dp
                colors = ButtonDefaults.textButtonColors(
                    contentColor = tokens.textSecondary
                )
            ) {
                Text("Отмена", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CriterionChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LirixThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp), // Touch target >= 48dp
        shape = RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(8.dp)),
        color = if (selected) tokens.accentGlow else tokens.surfacePrimary,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) tokens.accentPrimary else tokens.borderSubtle
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) tokens.accentPrimary else tokens.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) tokens.accentPrimary else tokens.textPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MatchModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tokens = LirixThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp) // Touch target >= 48dp
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // Handled by row clickable
            colors = RadioButtonDefaults.colors(
                selectedColor = tokens.accentPrimary,
                unselectedColor = tokens.borderSubtle
            ),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = tokens.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = tokens.textSecondary
            )
        }
    }
}
