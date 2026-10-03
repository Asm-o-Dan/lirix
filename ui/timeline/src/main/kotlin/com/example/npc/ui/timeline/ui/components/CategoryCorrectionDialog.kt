package com.example.npc.ui.timeline.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.npc.core.model.classify.Category
import com.example.npc.ui.timeline.model.EventUiModel
import com.example.npc.ui.timeline.ui.theme.CategoryColors

@Composable
fun CategoryCorrectionDialog(
    currentCategory: Category,
    onCategorySelected: (Category) -> Unit,
    onDismissRequest: () -> Unit
) {
    val categories = listOf(
        Category.FINANCE,
        Category.COMMUNICATION,
        Category.MUSIC,
        Category.SERVICES,
        Category.ADVERTISEMENT,
        Category.OTHER,
        Category.UNCLASSIFIED
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Коррекция категории",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Выберите правильную категорию для уведомления:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                categories.forEach { category ->
                    val isSelected = category == currentCategory
                    val style = CategoryColors.forCategory(category)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCategorySelected(category)
                                onDismissRequest()
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onCategorySelected(category)
                                onDismissRequest()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) style.primaryColor else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun CategoryCorrectionDialog(
    event: EventUiModel,
    onDismiss: () -> Unit,
    onCategorySelected: (Category) -> Unit
) = CategoryCorrectionDialog(
    currentCategory = event.category,
    onCategorySelected = onCategorySelected,
    onDismissRequest = onDismiss
)
