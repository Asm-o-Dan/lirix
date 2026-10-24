package com.lirix.app.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lirix.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lirix.app.feature.share.ShareCardFormat
import com.lirix.app.feature.share.ShareManager
import com.lirix.app.feature.share.animation.AnimatedPosterPipeline
import com.lirix.app.feature.share.animation.AnimationSpec
import com.lirix.app.feature.share.animation.PosterAnimator
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dialog for selecting poster export format (Square 1:1 or Stories 9:16)
 * and toggling between static PNG poster and animated MP4 video (SPEC-ANIM-POSTER-01 §5 / TASK-ANIM-007).
 */
@Composable
fun ShareFormatDialog(
    onDismissRequest: () -> Unit,
    animatorProvider: (() -> PosterAnimator)? = null,
    onFormatSelected: suspend (ShareCardFormat) -> Bitmap
) {
    val colors = LirixThemeTokens.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var format by remember { mutableStateOf(ShareCardFormat.SQUARE_1_1) }
    var isAnimated by remember { mutableStateOf(false) }

    var rendered by remember { mutableStateOf<Pair<ShareCardFormat, Bitmap>?>(null) }
    val preview = rendered?.takeIf { it.first == format }?.second
    var generating by remember { mutableStateOf(true) }
    var exporting by remember { mutableStateOf(false) }
    var encodingProgress by remember { mutableFloatStateOf(0f) }

    var savingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var savingVideoFile by remember { mutableStateOf<File?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    val saveImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = savingBitmap
        savingBitmap = null
        if (uri == null || bitmap == null) {
            exporting = false
            if (uri != null) error = context.getString(R.string.share_preview_closed_error)
        } else {
            scope.launch {
                try {
                    ShareManager.saveBitmap(context, bitmap, uri)
                    saved = true
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = context.getString(R.string.share_save_failed_error)
                } finally {
                    exporting = false
                }
            }
        }
    }

    val saveVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val videoFile = savingVideoFile
        savingVideoFile = null
        if (uri == null || videoFile == null) {
            exporting = false
            if (uri != null) error = context.getString(R.string.share_video_not_found_error)
        } else {
            scope.launch {
                try {
                    ShareManager.saveVideo(context, videoFile, uri)
                    saved = true
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = context.getString(R.string.share_save_video_failed_error)
                } finally {
                    exporting = false
                }
            }
        }
    }

    LaunchedEffect(format, retry, isAnimated) {
        if (!isAnimated) {
            generating = true
            rendered = null
            error = null
            saved = false
            try {
                rendered = format to onFormatSelected(format)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = context.getString(R.string.share_generate_failed_error)
            } finally {
                generating = false
            }
        } else {
            generating = false
            error = null
            saved = false
        }
    }

    Dialog(
        onDismissRequest = { if (!exporting) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.9f),
            shape = RoundedCornerShape(colors.cardCornerRadius),
            color = colors.surfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(if (isAnimated) R.string.share_title_animated else R.string.share_poster_preview_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )

                // Toggle: Static / Animation
                if (animatorProvider != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = !isAnimated,
                            onClick = { if (!exporting) isAnimated = false },
                            enabled = !exporting,
                            label = { Text(stringResource(R.string.share_tab_static)) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = colors.textSecondary,
                                selectedLabelColor = colors.textPrimary,
                                selectedContainerColor = colors.surfaceCard
                            )
                        )
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = isAnimated,
                            onClick = { if (!exporting) isAnimated = true },
                            enabled = !exporting,
                            label = { Text(stringResource(R.string.share_tab_animated)) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = colors.textSecondary,
                                selectedLabelColor = colors.textPrimary,
                                selectedContainerColor = colors.surfaceCard
                            )
                        )
                    }
                }

                // Format selection: Square 1:1 vs Stories 9:16
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareCardFormat.entries.forEach { option ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = format == option,
                            onClick = { format = option },
                            enabled = !exporting,
                            label = { Text(stringResource(if (option == ShareCardFormat.SQUARE_1_1) R.string.share_format_square else R.string.share_format_stories)) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = colors.textSecondary,
                                selectedLabelColor = colors.textPrimary,
                                selectedContainerColor = colors.surfaceCard
                            )
                        )
                    }
                }

                // Preview box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAnimated && animatorProvider != null) {
                        val transition = rememberInfiniteTransition(label = "PosterAnimationPreview")
                        val animProgress by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(4000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "t"
                        )
                        val animator = remember(animatorProvider) { animatorProvider() }

                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawIntoCanvas { composeCanvas ->
                                val nativeCanvas = composeCanvas.nativeCanvas
                                val targetW = if (format == ShareCardFormat.SQUARE_1_1) 1080 else 1080
                                val targetH = if (format == ShareCardFormat.SQUARE_1_1) 1080 else 1920
                                val scale = minOf(size.width / targetW, size.height / targetH)
                                nativeCanvas.save()
                                val leftOffset = (size.width - targetW * scale) / 2f
                                val topOffset = (size.height - targetH * scale) / 2f
                                nativeCanvas.translate(leftOffset, topOffset)
                                nativeCanvas.scale(scale, scale)
                                animator.renderFrame(nativeCanvas, animProgress, targetW, targetH)
                                nativeCanvas.restore()
                            }
                        }
                    } else {
                        val bitmap = preview
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.share_format_poster_cd, format.label),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else if (generating) {
                            CircularProgressIndicator(color = colors.accentPrimary)
                        } else {
                            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.share_action_retry), color = colors.accentPrimary) }
                        }
                    }
                }

                // Encoding progress bar
                if (exporting && isAnimated) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { encodingProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.accentPrimary,
                            trackColor = colors.surfaceCard
                        )
                        Text(
                            text = stringResource(R.string.share_video_encoding_progress, (encodingProgress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (saved) Text(stringResource(if (isAnimated) R.string.share_video_saved else R.string.share_poster_saved), color = colors.accentPrimary, style = MaterialTheme.typography.bodyMedium)

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = (!isAnimated && preview != null && !generating && !exporting) ||
                                (isAnimated && animatorProvider != null && !exporting),
                        onClick = {
                            error = null
                            saved = false
                            if (isAnimated && animatorProvider != null) {
                                exporting = true
                                encodingProgress = 0f
                                scope.launch {
                                    try {
                                        val animator = animatorProvider()
                                        val spec = if (format == ShareCardFormat.SQUARE_1_1) {
                                            AnimationSpec.SQUARE
                                        } else {
                                            AnimationSpec.STORIES
                                        }
                                        val cacheVideoFile = File(context.cacheDir, "posters/poster_${System.currentTimeMillis()}.mp4")
                                        val generated = AnimatedPosterPipeline.generate(
                                            context = context,
                                            animator = animator,
                                            spec = spec,
                                            outputFile = cacheVideoFile,
                                            onProgress = { p -> encodingProgress = p }
                                        ).getOrThrow()

                                        savingVideoFile = generated
                                        withContext(Dispatchers.Main) {
                                            saveVideoLauncher.launch("lirix-animated-${System.currentTimeMillis()}.mp4")
                                        }
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        exporting = false
                                        error = context.getString(R.string.share_video_gen_failed_error)
                                    }
                                }
                            } else {
                                savingBitmap = preview
                                exporting = true
                                try {
                                    saveImageLauncher.launch("lirix-poster-${System.currentTimeMillis()}.png")
                                } catch (failure: Exception) {
                                    savingBitmap = null
                                    exporting = false
                                    error = context.getString(R.string.share_file_picker_error)
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.share_action_save), color = colors.textPrimary) }

                    Button(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary, contentColor = colors.appBackground),
                        enabled = (!isAnimated && preview != null && !generating && !exporting) ||
                                (isAnimated && animatorProvider != null && !exporting),
                        onClick = {
                            exporting = true
                            error = null
                            if (isAnimated && animatorProvider != null) {
                                encodingProgress = 0f
                                scope.launch {
                                    try {
                                        val animator = animatorProvider()
                                        val spec = if (format == ShareCardFormat.SQUARE_1_1) {
                                            AnimationSpec.SQUARE
                                        } else {
                                            AnimationSpec.STORIES
                                        }
                                        val sharesDir = File(context.cacheDir, "shares").apply { mkdirs() }
                                        val cacheVideoFile = File(sharesDir, "animated_share_${System.currentTimeMillis()}.mp4")
                                        val generated = AnimatedPosterPipeline.generate(
                                            context = context,
                                            animator = animator,
                                            spec = spec,
                                            outputFile = cacheVideoFile,
                                            onProgress = { p -> encodingProgress = p }
                                        ).getOrThrow()

                                        check(ShareManager.shareAnimatedPoster(context, generated, context.getString(R.string.share_intent_chooser_title)))
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        error = context.getString(R.string.share_video_send_failed_error)
                                    } finally {
                                        exporting = false
                                    }
                                }
                            } else {
                                val bitmap = preview ?: return@Button
                                scope.launch {
                                    try {
                                        check(ShareManager.shareBitmap(context, bitmap, context.getString(R.string.share_intent_chooser_title)))
                                    } catch (failure: Exception) {
                                        if (failure is CancellationException) throw failure
                                        error = context.getString(R.string.share_menu_failed_error)
                                    } finally {
                                        exporting = false
                                    }
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.share_action_send)) }
                }

                TextButton(onClick = onDismissRequest, enabled = !exporting, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_close), color = colors.textSecondary)
                }
            }
        }
    }
}
