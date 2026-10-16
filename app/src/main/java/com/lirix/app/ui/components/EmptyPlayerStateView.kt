package com.lirix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.LirixThemeTokens

enum class PlayerEmptyStateType {
    NO_ACTIVE_PLAYER,
    LYRICS_NOT_FOUND,
    UNSYNCED_LYRICS
}

/**
 * Clean bookish empty and error state component for NowPlayingScreen (SPEC-UI-MOBILE-2.0 / ADR-023).
 */
@Composable
fun EmptyPlayerStateView(
    stateType: PlayerEmptyStateType,
    modifier: Modifier = Modifier,
    trackTitle: String? = null,
    trackArtist: String? = null,
    onSearchManual: (() -> Unit)? = null,
    onConnectClick: (() -> Unit)? = null
) {
    val tokens = LirixThemeTokens.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            when (stateType) {
                PlayerEmptyStateType.NO_ACTIVE_PLAYER -> {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(tokens.surfaceCard)
                            .border(1.dp, tokens.borderSubtle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = tokens.textMuted,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Ничего не играет",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = tokens.textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Запустите трек в любом плеере Windows/Android",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    if (onConnectClick != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onConnectClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.accentPrimary,
                                contentColor = if (tokens.isDark) Color(0xFF101216) else Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DevicesOther,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Подключить ПК (Lirix Connect)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                PlayerEmptyStateType.LYRICS_NOT_FOUND -> {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(tokens.surfaceCard)
                            .border(1.dp, tokens.borderSubtle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = tokens.accentPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Не нашли текст для этого трека",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = tokens.textPrimary,
                        textAlign = TextAlign.Center
                    )

                    if (!trackTitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!trackArtist.isNullOrBlank()) "$trackTitle — $trackArtist" else trackTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Вы можете найти его в веб-поиске и привязать к песне в один клик",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    if (onSearchManual != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onSearchManual,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.surfaceElevated,
                                contentColor = tokens.accentPrimary
                            ),
                            shape = RoundedCornerShape(tokens.pillCornerRadius),
                            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Искать вручную",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                PlayerEmptyStateType.UNSYNCED_LYRICS -> {
                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(tokens.cardCornerRadius),
                        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Тайминги недоступны. Прокручивайте текст вручную",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
