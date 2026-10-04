package com.eventengine.app.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventengine.app.feature.lyrics.ScraperRuleBundle
import com.eventengine.app.feature.lyrics.ScraperRuleBundleCodec
import com.eventengine.app.storage.AppDatabase
import com.eventengine.app.storage.CustomLyricsRuleEntity
import com.eventengine.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog for exporting and sharing a custom lyrics scraper rule.
 * Spec: TASK-RUL-02 / .sdd/intake/TRACK_D_SCRAPER_COMMUNITY.md
 */
@Composable
fun ExportRuleDialog(
    rule: CustomLyricsRuleEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val jsonBundle = remember(rule) {
        ScraperRuleBundleCodec.serialize(rule, pretty = true)
    }

    val bundlePreview = remember(jsonBundle) {
        ScraperRuleBundleCodec.deserializeBundle(jsonBundle).getOrNull()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = AppColors.SurfaceLevel1,
            border = BorderStroke(1.dp, AppColors.HyperViolet.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.HyperViolet.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AppColors.HyperViolet.copy(alpha = 0.4f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = AppColors.HyperViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Экспорт правила",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AppColors.TextPrimary
                            )
                            Text(
                                text = rule.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.CyberCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = AppColors.TextTertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metadata Chip Card
                Surface(
                    color = AppColors.SurfaceLevel2,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Целевой домен:",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextSecondary
                            )
                            Text(
                                text = rule.domain,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                color = AppColors.ElectricMint
                            )
                        }

                        if (bundlePreview != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Селектор:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.TextSecondary
                                )
                                Text(
                                    text = bundlePreview.contentSelector,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = AppColors.CyberCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Подпись SHA-256:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.TextSecondary
                                )
                                Text(
                                    text = "${bundlePreview.checksum.take(12)}... (v${bundlePreview.schemaVersion})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = AppColors.AmberGold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ПАКЕТ LIRIX JSON (v1):",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AppColors.TextTertiary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Monospace Code Block
                Surface(
                    color = AppColors.SurfaceLevel3,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = jsonBundle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = AppColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            clipboardManager.setText(AnnotatedString(jsonBundle))
                            Toast.makeText(context, "JSON правила скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppColors.HyperViolet.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.HyperViolet),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Копировать", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, jsonBundle)
                                putExtra(Intent.EXTRA_SUBJECT, "Lirix Scraper Rule: ${rule.name}")
                                type = "text/plain"
                            }
                            val chooser = Intent.createChooser(sendIntent, "Поделиться правилом ${rule.domain}")
                            context.startActivity(chooser)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.HyperViolet),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = AppColors.AmoledBlack, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Поделиться", color = AppColors.AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Dialog for importing a custom scraper rule from JSON bundle with live preview and validation.
 * Spec: TASK-RUL-02 / .sdd/intake/TRACK_D_SCRAPER_COMMUNITY.md
 */
@Composable
fun ImportRuleDialog(
    onDismiss: () -> Unit,
    onRuleImported: (CustomLyricsRuleEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    var jsonInput by remember { mutableStateOf("") }
    var existingRulesForDomainCount by remember { mutableStateOf(0) }

    val parseResult = remember(jsonInput) {
        if (jsonInput.isBlank()) null
        else ScraperRuleBundleCodec.deserializeBundle(jsonInput)
    }

    val validBundle: ScraperRuleBundle? = parseResult?.getOrNull()
    val parseError: Throwable? = parseResult?.exceptionOrNull()

    // Check if domain already exists in DB
    LaunchedEffect(validBundle?.domain) {
        val domain = validBundle?.domain
        if (domain != null && domain.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val existing = db.lyricsDao().getRulesForDomain(domain)
                existingRulesForDomainCount = existing.size
            }
        } else {
            existingRulesForDomainCount = 0
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = AppColors.SurfaceLevel1,
            border = BorderStroke(1.dp, AppColors.CyberCyan.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.CyberCyan.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AppColors.CyberCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = AppColors.CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Импорт правила",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AppColors.TextPrimary
                            )
                            Text(
                                text = "Вставка LirixScraperRuleBundle JSON",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = AppColors.TextTertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Paste from clipboard helper button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ВСТАВЬТЕ JSON ПРАВИЛА:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AppColors.TextTertiary
                    )

                    TextButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                jsonInput = clipText
                                Toast.makeText(context, "Вставлено из буфера", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, tint = AppColors.CyberCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Из буфера", color = AppColors.CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Input TextField
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    placeholder = {
                        Text(
                            text = "{\"schemaVersion\": 1, \"domain\": \"example.com\", ...}",
                            color = AppColors.TextDisabled,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppColors.SurfaceLevel2,
                        unfocusedContainerColor = AppColors.SurfaceLevel2,
                        focusedBorderColor = AppColors.CyberCyan,
                        unfocusedBorderColor = AppColors.BorderSubtle,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Live Validation Preview
                if (validBundle != null) {
                    Surface(
                        color = AppColors.SurfaceLevel2,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AppColors.ElectricMint.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AppColors.ElectricMint,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Пакет v${validBundle.schemaVersion} валиден • Подпись подтверждена",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = AppColors.ElectricMint
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Название: ${validBundle.name}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = AppColors.TextPrimary
                            )
                            Text(
                                text = "Домен: ${validBundle.domain}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = AppColors.CyberCyan
                            )
                            Text(
                                text = "Селектор: ${validBundle.contentSelector}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = AppColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Автор: ${validBundle.author} • Версия: v${validBundle.version}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextTertiary
                            )

                            if (existingRulesForDomainCount > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = AppColors.AmberGold, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "Правило для ${validBundle.domain} уже существует и будет обновлено",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppColors.AmberGold
                                    )
                                }
                            }
                        }
                    }
                } else if (parseError != null) {
                    Surface(
                        color = AppColors.SurfaceLevel2,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AppColors.ExpenseRed.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AppColors.ExpenseRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = parseError.message ?: "Ошибка валидации JSON",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.ExpenseRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextSecondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отмена", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val entityResult = ScraperRuleBundleCodec.deserializeAndValidate(jsonInput)
                            val entity = entityResult.getOrNull()
                            if (entity != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch(Dispatchers.IO) {
                                    db.lyricsDao().saveRule(entity)
                                }
                                onRuleImported(entity)
                                Toast.makeText(context, "Правило для ${entity.domain} сохранено!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Ошибка валидации правила", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = validBundle != null,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.CyberCyan,
                            disabledContainerColor = AppColors.SurfaceLevel3
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = if (validBundle != null) AppColors.AmoledBlack else AppColors.TextDisabled, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Импортировать",
                            color = if (validBundle != null) AppColors.AmoledBlack else AppColors.TextDisabled,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
