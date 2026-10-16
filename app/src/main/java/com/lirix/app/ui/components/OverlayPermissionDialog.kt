package com.lirix.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.LirixThemeTokens

/**
 * Informative Material 3 dialog for SYSTEM_ALERT_WINDOW permission request.
 * Explains clearly why overlay permission is needed and directs user to system settings.
 * Spec: TASK-BUG-FLT-04 (.sdd/tasks/TASK-BUG-FLT-04.md)
 */
@Composable
fun OverlayPermissionDialog(
    onDismissRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LirixThemeTokens.colors

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.PictureInPictureAlt,
                contentDescription = null,
                tint = tokens.accentPrimary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Отображение поверх других приложений",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary
            )
        },
        text = {
            Text(
                text = "Для показа бегущих строк караоке поверх плееров и других приложений требуется предоставить системное разрешение.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Настройки системы",
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.appBackground
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    text = "Отмена",
                    color = tokens.textSecondary
                )
            }
        },
        containerColor = tokens.surfaceElevated,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.border(1.dp, tokens.borderSubtle, RoundedCornerShape(20.dp))
    )
}
