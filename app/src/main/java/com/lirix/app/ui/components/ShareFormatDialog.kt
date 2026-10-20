package com.lirix.app.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lirix.app.feature.share.ShareCardFormat
import com.lirix.app.feature.share.ShareManager
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Preview and export share the same bitmap; export never regenerates a poster. */
@Composable
fun ShareFormatDialog(
    onDismissRequest: () -> Unit,
    onFormatSelected: suspend (ShareCardFormat) -> Bitmap
) {
    val colors = LirixThemeTokens.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(ShareCardFormat.SQUARE_1_1) }
    var rendered by remember { mutableStateOf<Pair<ShareCardFormat, Bitmap>?>(null) }
    val preview = rendered?.takeIf { it.first == format }?.second
    var generating by remember { mutableStateOf(true) }
    var exporting by remember { mutableStateOf(false) }
    var savingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = savingBitmap
        savingBitmap = null
        if (uri == null || bitmap == null) {
            exporting = false
            if (uri != null) error = "Предпросмотр был закрыт. Откройте постер и сохраните его ещё раз."
        } else {
            scope.launch {
                try {
                    ShareManager.saveBitmap(context, bitmap, uri)
                    saved = true
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = "Не удалось сохранить постер. Попробуйте другой файл."
                } finally {
                    exporting = false
                }
            }
        }
    }

    LaunchedEffect(format, retry) {
        generating = true
        rendered = null
        error = null
        saved = false
        try {
            rendered = format to onFormatSelected(format)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = "Не удалось создать постер. Попробуйте ещё раз."
        } finally {
            generating = false
        }
    }

    Dialog(
        onDismissRequest = { if (!exporting) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.9f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(colors.cardCornerRadius),
            color = colors.surfaceElevated
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Предпросмотр постера", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareCardFormat.entries.forEach { option ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = format == option,
                            onClick = { format = option },
                            enabled = !exporting,
                            label = { Text(if (option == ShareCardFormat.SQUARE_1_1) "Квадрат 1:1" else "Истории 9:16") },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = colors.textSecondary,
                                selectedLabelColor = colors.textPrimary,
                                selectedContainerColor = colors.surfaceCard
                            )
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = preview
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(), contentDescription = "Постер в формате ${format.label}",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                        )
                    } else if (generating) {
                        CircularProgressIndicator(color = colors.accentPrimary)
                    } else {
                        TextButton(onClick = { retry++ }) { Text("Повторить", color = colors.accentPrimary) }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (saved) Text("Постер сохранён", color = colors.accentPrimary, style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f), enabled = preview != null && !generating && !exporting,
                        onClick = {
                            error = null
                            saved = false
                            savingBitmap = preview
                            exporting = true
                            try {
                                saveLauncher.launch("lirix-poster-${System.currentTimeMillis()}.png")
                            } catch (failure: Exception) {
                                savingBitmap = null
                                exporting = false
                                error = "Не удалось открыть выбор файла"
                            }
                        }
                    ) { Text("Сохранить", color = colors.textPrimary) }
                    Button(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary, contentColor = colors.appBackground),
                        enabled = preview != null && !generating && !exporting,
                        onClick = {
                            val bitmap = preview ?: return@Button
                            exporting = true
                            error = null
                            scope.launch {
                                try {
                                    check(ShareManager.shareBitmap(context, bitmap, "Поделиться постером"))
                                } catch (failure: Exception) {
                                    if (failure is CancellationException) throw failure
                                    error = "Не удалось открыть меню отправки"
                                } finally {
                                    exporting = false
                                }
                            }
                        }
                    ) { Text("Отправить") }
                }
                TextButton(onClick = onDismissRequest, enabled = !exporting, modifier = Modifier.align(Alignment.End)) {
                    Text("Закрыть", color = colors.textSecondary)
                }
            }
        }
    }
}
