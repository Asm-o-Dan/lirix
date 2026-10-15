package com.lirix.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.LirixThemeTokens

/**
 * Clean track metadata header displaying title, artist, album, and app badge.
 * Spec: TASK-KMP-09 (.sdd/tasks/TASK-KMP-09.md)
 */
@Composable
fun TrackMetadataHeader(
    title: String,
    artist: String,
    album: String? = null,
    packageName: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App / Source Badge (e.g. "Spotify.exe", "YandexMusic.exe")
        if (!packageName.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(colors.pillCornerRadius))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = packageName,
                    color = colors.accentPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Track Title
        Text(
            text = if (title.isNotBlank()) title else "Нет активного трека",
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Artist Name
        Text(
            text = if (artist.isNotBlank()) artist else "Ожидание медиа-сессии",
            color = colors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // Album Name
        if (!album.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = album,
                color = colors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
