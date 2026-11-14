package com.lirix.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.AppTab
import com.lirix.app.ui.theme.LirixThemeTokens

/**
 * Capsule bottom navigation component (SPEC-UI-MOBILE-2.0 / SPEC-I18N-01).
 * Supports localized tabs: Now Playing, Library, Wrapped.
 */
@Composable
fun NavigationCapsule(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val lirixColors = LirixThemeTokens.colors
    val haptic = LocalHapticFeedback.current
    val capsuleRadius = lirixColors.pillCornerRadius.coerceAtLeast(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Fixed width for every tab: the capsule neither resizes nor moves when the selection changes.
        Row(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(capsuleRadius))
                .background(lirixColors.surfacePrimary.copy(alpha = 0.94f))
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.horizontalGradient(
                            listOf(
                                lirixColors.accentPrimary.copy(alpha = 0.8f),
                                lirixColors.accentSecondary.copy(alpha = 0.8f)
                            )
                        )
                    ),
                    RoundedCornerShape(capsuleRadius)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                val tabRadius = (capsuleRadius - 6.dp).coerceAtLeast(10.dp)
                val bgModifier = if (isSelected) {
                    Modifier
                        .clip(RoundedCornerShape(tabRadius))
                        .background(lirixColors.surfaceCard)
                        .border(1.dp, lirixColors.accentSecondary.copy(alpha = 0.4f), RoundedCornerShape(tabRadius))
                } else {
                    Modifier.clip(RoundedCornerShape(tabRadius))
                }
                val tabTitle = stringResource(tab.titleRes)
                // The selected slot is wider for its label; the slots always sum to the same width.
                val slotWeight by animateFloatAsState(if (isSelected) 1.6f else 1f, label = "NavigationSlotWeight")

                Row(
                    modifier = Modifier
                        .weight(slotWeight)
                        .then(bgModifier)
                        .semantics { selected = isSelected }
                        .clickable(role = Role.Tab) {
                            if (currentTab != tab) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTabSelected(tab)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tabTitle,
                        tint = if (isSelected) lirixColors.accentSecondary else lirixColors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tabTitle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = lirixColors.textPrimary,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
