package com.lirix.app.update

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.R
import kotlin.math.roundToInt

/** Presentation of the real activity state; no network, installation or release policy lives here. */
data class UpdateScreenState(
    val installedVersion: String,
    @StringRes val message: Int,
    val releaseVersion: String? = null,
    val busy: Boolean = false,
    val progress: Float? = null,
    val downloadReady: Boolean = false
)

@Composable
fun UpdateScreen(state: UpdateScreenState, onBack: () -> Unit, onPrimary: () -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background, contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(stringResource(R.string.update_title), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge)
            }
        }) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets),
            contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val statusIcon = when (state.message) {
                        R.string.update_current, R.string.update_ready -> Icons.Default.Check
                        R.string.update_available, R.string.update_downloading -> Icons.Default.Download
                        R.string.update_failed, R.string.update_signature -> Icons.Default.ErrorOutline
                        R.string.update_permission -> Icons.Default.Lock
                        else -> Icons.Default.Sync
                    }
                    Box(Modifier.size(72.dp).background(colors.primary.copy(alpha = .12f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(statusIcon, contentDescription = null, modifier = Modifier.size(48.dp), tint = colors.primary)
                    }
                    Text(stringResource(state.message), modifier = Modifier.fillMaxWidth().testTag("update-status")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(stringResource(R.string.update_installed, state.installedVersion),
                        modifier = Modifier.fillMaxWidth().testTag("update-installed"),
                        style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    state.releaseVersion?.let {
                        Text(stringResource(R.string.update_release_version, it), modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    val detail = when (state.message) {
                        R.string.update_current -> R.string.update_current_detail
                        R.string.update_failed -> R.string.update_failed_detail
                        R.string.update_signature -> R.string.update_signature_detail
                        R.string.update_permission -> R.string.update_permission_detail
                        R.string.update_ready -> R.string.update_ready_detail
                        else -> null
                    }
                    detail?.let { Text(stringResource(it), modifier = Modifier.fillMaxWidth().testTag("update-detail"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
                        color = colors.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    if (state.busy && state.progress != null) {
                        val amount = state.progress.coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { amount },
                            modifier = Modifier.fillMaxWidth().testTag("update-progress"))
                        Text(stringResource(R.string.update_download_percentage, (amount * 100).roundToInt()),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(top = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.busy && state.progress != null) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("update-cancel"), contentPadding = PaddingValues(16.dp)) {
                            Text(stringResource(R.string.update_cancel), modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    } else {
                        val action = when {
                            state.busy -> R.string.update_checking
                            state.message == R.string.update_signature -> R.string.update_check
                            state.message == R.string.update_failed -> R.string.update_retry
                            state.downloadReady -> R.string.update_install
                            state.releaseVersion != null -> R.string.update_download
                            else -> R.string.update_check
                        }
                        Button(onClick = onPrimary, enabled = !state.busy,
                            colors = ButtonDefaults.buttonColors(
                                contentColor = updateButtonContentColor(colors.primary, colors.onPrimary)),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("update-primary"),
                            contentPadding = PaddingValues(16.dp)) {
                            Text(stringResource(action), modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** Keep palette intent when readable; choose the stronger ink only for this action if necessary. */
private fun updateButtonContentColor(background: Color, preferred: Color): Color {
    fun contrast(foreground: Color): Float {
        val a = foreground.luminance()
        val b = background.luminance()
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
    }
    if (contrast(preferred) >= 4.5f) return preferred
    return if (contrast(Color.Black) >= contrast(Color.White)) Color.Black else Color.White
}
