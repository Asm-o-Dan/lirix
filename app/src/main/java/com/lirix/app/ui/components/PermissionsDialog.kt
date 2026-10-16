package com.lirix.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lirix.app.ingestion.NotificationListener
import com.lirix.app.ui.theme.LirixThemeTokens

/**
 * Types of system permissions required by Lirix.
 */
enum class LirixPermissionType {
    NOTIFICATION_LISTENER,
    SYSTEM_ALERT_WINDOW,
    ALL
}

/**
 * Modern permissions onboarding and management dialog adhering to SPEC-UI-MOBILE-2.0 and ADR-023.
 * Supports auto-checking permissions on ON_RESUME lifecycle event.
 */
@Composable
fun PermissionsDialog(
    isOpen: Boolean,
    permissionType: LirixPermissionType = LirixPermissionType.NOTIFICATION_LISTENER,
    onDismissRequest: () -> Unit,
    onAllGranted: () -> Unit = {}
) {
    if (!isOpen) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tokens = LirixThemeTokens.colors

    var isNotificationGranted by remember {
        mutableStateOf(NotificationListener.isPermissionGranted(context))
    }
    var isOverlayGranted by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true)
    }

    // Auto-check permissions upon returning from Android Settings (ON_RESUME)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val notif = NotificationListener.isPermissionGranted(context)
                val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                isNotificationGranted = notif
                isOverlayGranted = overlay

                val targetGranted = when (permissionType) {
                    LirixPermissionType.NOTIFICATION_LISTENER -> notif
                    LirixPermissionType.SYSTEM_ALERT_WINDOW -> overlay
                    LirixPermissionType.ALL -> notif && overlay
                }
                if (targetGranted) {
                    onAllGranted()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allSatisfied = when (permissionType) {
        LirixPermissionType.NOTIFICATION_LISTENER -> isNotificationGranted
        LirixPermissionType.SYSTEM_ALERT_WINDOW -> isOverlayGranted
        LirixPermissionType.ALL -> isNotificationGranted && isOverlayGranted
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(20.dp)))
                .border(
                    BorderStroke(1.dp, tokens.borderSubtle),
                    RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(20.dp))
                ),
            color = tokens.surfaceElevated,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(tokens.accentGlow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = tokens.accentPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Системные разрешения",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = tokens.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Для автоматического определения треков и отображения текста Lirix требуются следующие разрешения:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Permission item 1: Notification Listener
                if (permissionType == LirixPermissionType.NOTIFICATION_LISTENER || permissionType == LirixPermissionType.ALL) {
                    PermissionCard(
                        title = "Доступ к уведомлениям для чтения медиасессий",
                        description = "Позволяет Lirix узнавать текущий воспроизводимый трек из Spotify, YouTube Music, VK и других плееров. Личные сообщения не читаются.",
                        icon = Icons.Default.Notifications,
                        isGranted = isNotificationGranted,
                        onOpenSettings = {
                            try {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                }

                // Permission item 2: SYSTEM_ALERT_WINDOW (Overlay)
                if (permissionType == LirixPermissionType.SYSTEM_ALERT_WINDOW || permissionType == LirixPermissionType.ALL) {
                    if (permissionType == LirixPermissionType.ALL) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    PermissionCard(
                        title = "Поверх других приложений",
                        description = "Требуется для отображения плавающего виджета-суфлёра поверх активных плееров и окон.",
                        icon = Icons.Default.Layers,
                        isGranted = isOverlayGranted,
                        onOpenSettings = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                if (allSatisfied) {
                    Button(
                        onClick = {
                            onAllGranted()
                            onDismissRequest()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(tokens.pillCornerRadius.coerceAtLeast(12.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.accentPrimary,
                            contentColor = tokens.surfaceElevated
                        )
                    ) {
                        Text(
                            text = "Продолжить",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(tokens.pillCornerRadius.coerceAtLeast(12.dp)),
                        border = BorderStroke(1.dp, tokens.borderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = tokens.textSecondary
                        )
                    ) {
                        Text(
                            text = "Настроить позже",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onOpenSettings: () -> Unit
) {
    val tokens = LirixThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(14.dp)),
        color = tokens.surfacePrimary,
        border = BorderStroke(
            1.dp,
            if (isGranted) tokens.accentPrimary.copy(alpha = 0.4f) else tokens.borderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) tokens.accentGlow else tokens.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                        contentDescription = null,
                        tint = if (isGranted) tokens.accentPrimary else tokens.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isGranted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Предоставлено ✓",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = tokens.accentPrimary
                    )
                }
            } else {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(tokens.pillCornerRadius.coerceAtLeast(10.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.accentPrimary,
                        contentColor = tokens.surfaceElevated
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Открыть системные настройки",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
