package com.lirix.app.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.lirix.app.analytics.*
import com.lirix.app.desktop.share.*
import com.lirix.app.desktop.system.DesktopFiles
import com.lirix.app.ui.theme.LirixColors
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

private data class WrappedPreviewRequest(val stats: WrappedStats, val period: AnalyticsTimeframe,
    val colors: LirixColors, val format: PosterFormat, val section: PosterSection)

@Composable
internal fun DesktopWrappedPosterDialog(stats: WrappedStats, period: AnalyticsTimeframe, colors: LirixColors,
    onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    DesktopFeatureDialog("Постер итогов", { if (!busy) onDismiss() }) {
        DesktopWrappedPosterContent(stats, period, colors, busy, { busy = it }, onDismiss, onMessage)
    }
}

@Composable
internal fun ColumnScope.DesktopWrappedPosterContent(stats: WrappedStats, period: AnalyticsTimeframe, colors: LirixColors,
    busy: Boolean, onBusy: (Boolean) -> Unit, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    var format by remember { mutableStateOf(PosterFormat.STORIES) }
    var section by remember { mutableStateOf(PosterSection.FULL) }
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf<Pair<WrappedPreviewRequest, BufferedImage>?>(null) }
    val request = remember(stats, period, colors, format, section) { WrappedPreviewRequest(stats, period, colors, format, section) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(request) {
        ready = null; error = null
        try {
            val rendered = withContext(Dispatchers.Default) { DesktopPosterRenderer.wrapped(stats, period, colors, format, section) }
            ensureActive()
            ready = request to rendered
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "Не удалось создать предпросмотр: ${e.message}" }
    }
    // A stale preview can never be exported after format, data or theme changes.
    val image = ready?.takeIf { it.first == request }?.second
    val bitmap = remember(image) { image?.toComposeImageBitmap() }
    Text("${if (period == AnalyticsTimeframe.MONTH) "Последние 30 дней" else period.labelRu} · предпросмотр", color = colors.textPrimary)
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        ChoiceDropdown("Формат", format.label, PosterFormat.entries.map { it.label }, !busy) { label -> format = PosterFormat.entries.first { it.label == label } }
        ChoiceDropdown("Содержимое", section.label, PosterSection.entries.map { it.label }, !busy) { label -> section = PosterSection.entries.first { it.label == label } }
    }
    if (bitmap != null) Image(bitmap, "Предпросмотр итогов: ${format.label}, ${section.label}",
        modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
    else Box(Modifier.weight(1f).fillMaxWidth()) { if (error == null) CircularProgressIndicator() }
    error?.let { Text(it, color = colors.accentPrimary) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onDismiss, enabled = !busy) { Text("Закрыть") }
        Button(enabled = !busy && image != null, onClick = {
            val snapshot = image ?: return@Button
            if (busy) return@Button
            onBusy(true)
            scope.launch {
                error = null
                try {
                    val destination = withContext(Dispatchers.IO) { DesktopFiles.choose("Сохранить постер итогов", "png", true, "lirix-wrapped.png") } ?: return@launch
                    withContext(Dispatchers.IO) { check(ImageIO.write(snapshot, "png", destination)) { "PNG не сохранён" } }
                    onMessage("Постер сохранён: ${destination.name}")
                    onDismiss()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = "Ошибка сохранения: ${e.message}" }
                finally { onBusy(false) }
            }
        }) { Text(if (busy) "Сохранение…" else "Сохранить PNG") }
    }
}
