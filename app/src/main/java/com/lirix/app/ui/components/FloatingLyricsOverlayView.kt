package com.lirix.app.ui.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lirix.app.MainActivity
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.service.FloatingLyricsState
import com.lirix.app.service.FloatingSnapCalculator
import com.lirix.app.ui.theme.AppColors
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Custom lifecycle owner required to host Jetpack Compose in a WindowManager view from a Service.
 */
private class OverlayLifecycleOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

/**
 * System overlay view providing a floating Dynamic Island pill and expandable mini-player.
 * Features kinetic touch tracking and magnetic edge snapping.
 * Spec: TASK-FLT-02 (.sdd/tasks/TASK-FLT-02.md)
 */
@SuppressLint("ViewConstructor")
class FloatingLyricsOverlayView(
    context: Context,
    private val windowManager: WindowManager,
    val layoutParams: WindowManager.LayoutParams,
    private val stateFlow: StateFlow<FloatingLyricsState>,
    private val onToggleExpand: () -> Unit,
    private val onCloseClick: () -> Unit
) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0
    private var initialY = 0
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isDragging = false
    private var activeAnimator: ValueAnimator? = null

    private val lifecycleOwner = OverlayLifecycleOwner()
    private val composeView: ComposeView

    init {
        lifecycleOwner.onCreate()
        lifecycleOwner.onStart()

        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        setViewTreeViewModelStoreOwner(lifecycleOwner)

        composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                val state by stateFlow.collectAsState()
                FloatingLyricsOverlayContent(
                    state = state,
                    onToggleExpand = onToggleExpand,
                    onCloseClick = onCloseClick,
                    onOpenAppClick = { openApp() },
                    onPlayPauseClick = { MediaSessionCollector.togglePlayPause() },
                    onSeekRelative = { deltaMs -> MediaSessionCollector.seekRelative(deltaMs) }
                )
            }
        }

        addView(composeView)
    }

    private fun openApp() {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch MainActivity from overlay")
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeAnimator?.cancel()
                initialX = layoutParams.x
                initialY = layoutParams.y
                touchDownX = ev.rawX
                touchDownY = ev.rawY
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - touchDownX
                val dy = ev.rawY - touchDownY
                if (!isDragging && FloatingSnapCalculator.isDragGesture(dx, dy, touchSlop.toFloat())) {
                    isDragging = true
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return isDragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeAnimator?.cancel()
                initialX = layoutParams.x
                initialY = layoutParams.y
                touchDownX = event.rawX
                touchDownY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchDownX
                val dy = event.rawY - touchDownY
                if (!isDragging && FloatingSnapCalculator.isDragGesture(dx, dy, touchSlop.toFloat())) {
                    isDragging = true
                }
                if (isDragging) {
                    val screenWidth = resources.displayMetrics.widthPixels
                    val screenHeight = resources.displayMetrics.heightPixels
                    val newX = initialX + dx.toInt()
                    val newY = initialY + dy.toInt()

                    layoutParams.x = FloatingSnapCalculator.clampX(newX, width, screenWidth)
                    layoutParams.y = FloatingSnapCalculator.clampY(newY, height, screenHeight)
                    try {
                        windowManager.updateViewLayout(this, layoutParams)
                    } catch (e: Exception) {
                        Timber.w(e, "Error updating overlay layout during drag")
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    animateSnapToEdge()
                    return true
                } else {
                    // Click on container
                    if (!stateFlow.value.isExpanded) {
                        onToggleExpand()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                animateSnapToEdge()
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateSnapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val marginPx = (FloatingSnapCalculator.DEFAULT_EDGE_MARGIN_DP * resources.displayMetrics.density).toInt()
        val targetX = FloatingSnapCalculator.calculateSnapTargetX(
            currentX = layoutParams.x,
            viewWidth = if (width > 0) width else 400,
            screenWidth = screenWidth,
            marginPx = marginPx
        )

        activeAnimator = ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 240L
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { animator ->
                layoutParams.x = animator.animatedValue as Int
                try {
                    windowManager.updateViewLayout(this@FloatingLyricsOverlayView, layoutParams)
                } catch (e: Exception) {
                    // Ignored if detached
                }
            }
            start()
        }
    }

    fun onDestroy() {
        activeAnimator?.cancel()
        lifecycleOwner.onDestroy()
        removeAllViews()
    }
}

/**
 * Top-level composable content for the floating overlay.
 */
@Composable
fun FloatingLyricsOverlayContent(
    state: FloatingLyricsState,
    onToggleExpand: () -> Unit,
    onCloseClick: () -> Unit,
    onOpenAppClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeekRelative: (Long) -> Unit
) {
    MaterialTheme {
        AnimatedContent(
            targetState = state.isExpanded,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "OverlayExpandTransition"
        ) { isExpanded ->
            if (isExpanded) {
                FloatingExpandedCard(
                    state = state,
                    onCollapseClick = onToggleExpand,
                    onCloseClick = onCloseClick,
                    onOpenAppClick = onOpenAppClick,
                    onPlayPauseClick = onPlayPauseClick,
                    onSeekRelative = onSeekRelative
                )
            } else {
                FloatingCollapsedPill(
                    state = state,
                    onClick = onToggleExpand
                )
            }
        }
    }
}

/**
 * Collapsed Dynamic Island pill.
 */
@Composable
fun FloatingCollapsedPill(
    state: FloatingLyricsState,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .widthIn(min = 160.dp, max = 330.dp)
            .heightIn(min = 42.dp, max = 64.dp)
            .wrapContentHeight(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xEE090A0F),
        border = BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(
                listOf(
                    AppColors.HyperViolet.copy(alpha = 0.8f),
                    AppColors.CyberCyan.copy(alpha = 0.8f)
                )
            )
        ),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pulse / Thumbnail
            AlbumArtThumbnail(
                artUri = state.albumArtUri,
                size = 28.dp,
                shape = CircleShape,
                borderColor = AppColors.HyperViolet
            )

            // Centered active lyric or track info
            val displayText = when {
                state.activeLineText.isNotBlank() -> state.activeLineText
                state.currentChord != null -> "Аккорд: ${state.currentChord}"
                state.title.isNotBlank() -> "${state.artist.ifBlank { "Lirix" }} — ${state.title}"
                else -> "🎵 Lirix Караоке"
            }

            Text(
                text = displayText,
                color = if (state.activeLineText.isNotBlank()) AppColors.CyberCyan else AppColors.TextPrimary,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                softWrap = true,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Развернуть",
                tint = AppColors.TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Expanded mini-player card (320dp) with lyrics, chords and transport controls.
 */
@Composable
fun FloatingExpandedCard(
    state: FloatingLyricsState,
    onCollapseClick: () -> Unit,
    onCloseClick: () -> Unit,
    onOpenAppClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeekRelative: (Long) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(320.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF70C0D14),
        border = BorderStroke(1.2.dp, AppColors.BorderSubtle),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Drag Handle
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppColors.BorderSubtle)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Header: Album Art, Title, Artist, Collapse, Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AlbumArtThumbnail(
                    artUri = state.albumArtUri,
                    size = 38.dp,
                    shape = RoundedCornerShape(8.dp),
                    borderColor = AppColors.HyperViolet
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title.ifBlank { "Lirix Player" },
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.artist.ifBlank { "Ожидание трека..." },
                        color = AppColors.HyperViolet,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Collapse button
                IconButton(
                    onClick = onCollapseClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Свернуть",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Close button
                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть оверлей",
                        tint = AppColors.ExpenseRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Middle section: 3-line karaoke & chords
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.SurfaceLevel1)
                    .border(1.dp, AppColors.BorderSubtle.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.hasLyrics && !state.isSynced) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📜 Текст без таймкодов",
                            color = AppColors.HyperViolet,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Этот текст без таймкодов. Откройте Lirix для чтения полного текста",
                            color = AppColors.TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        OutlinedButton(
                            onClick = onOpenAppClick,
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(17.dp),
                            border = BorderStroke(1.dp, AppColors.HyperViolet),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Открыть в Lirix",
                                color = AppColors.HyperViolet,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Previous line (dimmed)
                        state.previousLineText?.let { prev ->
                            Text(
                                text = prev,
                                color = AppColors.TextTertiary.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Active line (highlighted)
                        val mainText = when {
                            state.activeLineText.isNotBlank() -> state.activeLineText
                            state.currentChord != null -> "Аккорд: ${state.currentChord}"
                            state.hasLyrics -> "♪ ♪ ♪"
                            else -> "Текст песни не найден или загружается..."
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.HyperViolet.copy(alpha = 0.15f))
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mainText,
                                color = AppColors.CyberCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Next line (dimmed)
                        state.nextLineText?.let { next ->
                            Text(
                                text = next,
                                color = AppColors.TextTertiary.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Transport Bar: [-10s] [Play/Pause] [+10s] [Open Lirix]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Seek -10s
                IconButton(
                    onClick = { onSeekRelative(-10000L) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AppColors.SurfaceLevel1)
                        .border(1.dp, AppColors.BorderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Назад на 10 сек",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Play / Pause
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AppColors.HyperViolet, AppColors.CyberCyan)
                            )
                        )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Пауза" else "Воспроизведение",
                        tint = AppColors.AmoledBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Seek +10s
                IconButton(
                    onClick = { onSeekRelative(10000L) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AppColors.SurfaceLevel1)
                        .border(1.dp, AppColors.BorderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Вперед на 10 сек",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Open App
                IconButton(
                    onClick = onOpenAppClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AppColors.SurfaceLevel1)
                        .border(1.dp, AppColors.BorderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Открыть Lirix",
                        tint = AppColors.HyperViolet,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
