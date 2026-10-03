package com.example.npc.ui.timeline.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.npc.ui.timeline.model.CategoryFilter
import com.example.npc.ui.timeline.ui.theme.CategoryColors

@Composable
fun CategoryFilterChips(
    selectedCategory: CategoryFilter,
    onCategorySelected: (CategoryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        CategoryFilter.ALL,
        CategoryFilter.FINANCE,
        CategoryFilter.COMMUNICATION,
        CategoryFilter.MUSIC,
        CategoryFilter.SERVICES
    )

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            val isSelected = filter == selectedCategory
            val chipLabel = when (filter) {
                CategoryFilter.ALL -> "Все"
                CategoryFilter.FINANCE -> "Финансы"
                CategoryFilter.COMMUNICATION -> "Связь"
                CategoryFilter.MUSIC -> "Музыка"
                CategoryFilter.SERVICES -> "Сервисы"
                else -> filter.name
            }

            val categoryStyle = filter.category?.let { CategoryColors.forCategory(it) }

            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(filter) },
                label = { Text(text = chipLabel) },
                colors = if (categoryStyle != null && isSelected) {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = categoryStyle.containerColor,
                        selectedLabelColor = categoryStyle.onContainerColor
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                }
            )
        }
    }
}
