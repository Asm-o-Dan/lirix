package com.lirix.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.R
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.theme.LirixThemeTokens

/**
 * Accessible Modal BottomSheet providing explicit touch alternatives
 * to swipe actions in LibraryScreen (SPEC-UI-MOBILE-2.0 / TASK-UI-MOB-04).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionBottomSheet(
    track: TrackEntity,
    onDismissRequest: () -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onHideTrack: (TrackEntity) -> Unit
) {
    val tokens = LirixThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val accentPrimary = tokens.accentPrimary

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = tokens.surfacePrimary,
        contentColor = tokens.textPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.borderSubtle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = 24.dp)
        ) {
            // Track Info Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtThumbnail(
                    artUri = track.albumArtUri,
                    size = 48.dp,
                    shape = RoundedCornerShape(6.dp),
                    contentDescription = track.title
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action 1: Toggle Favorite
            ActionRowItem(
                icon = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                iconTint = if (track.isFavorite) tokens.accentSecondary else accentPrimary,
                title = stringResource(if (track.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
                onClick = {
                    onToggleFavorite(track)
                    onDismissRequest()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action 2: Hide track from library ("Игнорировать")
            ActionRowItem(
                icon = Icons.Default.VisibilityOff,
                iconTint = if (tokens.isDark) Color(0xFFE57373) else Color(0xFFC85A5A),
                title = stringResource(R.string.action_hide_from_library),
                onClick = {
                    onHideTrack(track)
                    onDismissRequest()
                }
            )
        }
    }
}

@Composable
private fun ActionRowItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    onClick: () -> Unit
) {
    val tokens = LirixThemeTokens.colors
    Surface(
        onClick = onClick,
        color = tokens.surfaceCard,
        shape = RoundedCornerShape(tokens.cardCornerRadius.coerceAtLeast(10.dp)),
        border = BorderStroke(1.dp, tokens.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = tokens.textPrimary
            )
        }
    }
}
