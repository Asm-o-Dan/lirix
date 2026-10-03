package com.example.npc.ui.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine
import com.example.npc.ui.timeline.ui.theme.CategoryColors

@Composable
fun CategoryBadge(
    category: Category,
    engine: Engine = Engine.NONE,
    confidence: Double = 0.0,
    isUserCorrected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val style = CategoryColors.forCategory(category)
    val shape = RoundedCornerShape(8.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .border(width = 0.75.dp, color = style.borderColor, shape = shape)
            .background(color = style.containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (isUserCorrected) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Ручная правка",
                tint = style.primaryColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        } else if (engine == Engine.PROTOTYPE) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Шаблон",
                tint = style.primaryColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = style.primaryColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Text(
            text = style.label,
            style = MaterialTheme.typography.labelSmall,
            color = style.onContainerColor
        )

        if (confidence > 0.0) {
            Spacer(modifier = Modifier.width(4.dp))
            val percent = (confidence * 100).toInt()
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall,
                color = style.onContainerColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun CategoryBadge(
    category: Category,
    confidence: Float,
    isPrototype: Boolean,
    isUserCorrected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = CategoryBadge(
    category = category,
    engine = if (isPrototype) Engine.PROTOTYPE else Engine.NONE,
    confidence = confidence.toDouble(),
    isUserCorrected = isUserCorrected,
    onClick = onClick,
    modifier = modifier
)
