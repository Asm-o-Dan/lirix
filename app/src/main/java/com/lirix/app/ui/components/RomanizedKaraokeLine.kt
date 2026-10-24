package com.lirix.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.R
import com.lirix.app.feature.lyrics.romanization.*
import com.lirix.app.ui.theme.LirixThemeTokens

/** Presentation only. Partial readings never appear as unqualified green Romaji. */
@Composable
fun RomanizedKaraokeLine(
    originalText: String, displayMode: RomanizationDisplayMode, modifier: Modifier = Modifier,
    reading: ReadingResult? = null, fontFamily: FontFamily? = null,
    fontWeight: FontWeight = FontWeight.Normal, fontSize: TextUnit = 24.sp,
    lineHeight: TextUnit = (fontSize.value * 1.35f).sp,
    textColor: Color = LirixThemeTokens.colors.textPrimary, alpha: Float = 1f,
    rubyColor: Color = LirixThemeTokens.colors.accentSecondary.copy(alpha = 0.85f * alpha)
) {
    val enabled = displayMode != RomanizationDisplayMode.ORIGINAL_ONLY
    val valid = reading?.takeIf { it.originalText == originalText }
    val complete = enabled && valid?.status == ReadingStatus.COMPLETE
    val label = if (!enabled) null else when (valid?.status) {
        ReadingStatus.PARTIAL -> R.string.reading_partial
        ReadingStatus.NEEDS_LANGUAGE -> R.string.reading_needs_language
        ReadingStatus.UNAVAILABLE -> R.string.reading_unavailable
        null -> if (originalText.isNotBlank()) R.string.reading_loading else null
        else -> null
    }
    Column(modifier.fillMaxWidth()) {
        if (complete && displayMode == RomanizationDisplayMode.READING_ABOVE) {
            Text(valid?.readingText.orEmpty(), fontSize = (fontSize.value * 0.46f).sp,
                lineHeight = (fontSize.value * 0.60f).sp, color = rubyColor, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp))
        }
        if (label != null) Text(stringResource(label), color = LirixThemeTokens.colors.textSecondary.copy(alpha = alpha),
            fontSize = (fontSize.value * 0.46f).sp, lineHeight = (fontSize.value * 0.62f).sp)
        Text(if (complete && displayMode == RomanizationDisplayMode.ROMANIZED_ONLY) valid?.readingText.orEmpty() else originalText,
            fontFamily = fontFamily, fontWeight = fontWeight, fontSize = fontSize, lineHeight = lineHeight,
            color = textColor.copy(alpha = alpha), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun RomanizationModeDialog(
    currentMode: RomanizationDisplayMode, onModeSelected: (RomanizationDisplayMode) -> Unit,
    onDismissRequest: () -> Unit,
    currentLanguage: RomanizationLanguage = RomanizationLanguage.AUTO,
    onLanguageSelected: (RomanizationLanguage) -> Unit = {}
) {
    val tokens = LirixThemeTokens.colors
    AlertDialog(onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.romanization_dialog_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.romanization_dialog_desc), style = MaterialTheme.typography.bodySmall)
                Column(Modifier.selectableGroup()) {
                    listOf(RomanizationDisplayMode.ORIGINAL_ONLY to R.string.romanization_mode_original,
                        RomanizationDisplayMode.READING_ABOVE to R.string.romanization_mode_reading_above,
                        RomanizationDisplayMode.ROMANIZED_ONLY to R.string.romanization_mode_romanized_only).forEach { (mode, label) ->
                        ReadingChoice(stringResource(label), currentMode == mode) { onModeSelected(mode) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.reading_language_title), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    listOf(RomanizationLanguage.AUTO to R.string.reading_language_auto,
                        RomanizationLanguage.JAPANESE to R.string.reading_language_japanese,
                        RomanizationLanguage.CHINESE to R.string.reading_language_mandarin,
                        RomanizationLanguage.NONE to R.string.reading_language_original).forEach { (language, label) ->
                        ReadingChoice(stringResource(label), currentLanguage == language) { onLanguageSelected(language) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.action_done)) } },
        containerColor = tokens.surfaceElevated, textContentColor = tokens.textPrimary)
}

@Composable
private fun ReadingChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}
