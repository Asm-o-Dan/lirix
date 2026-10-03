package com.example.npc.ui.timeline.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.npc.ui.timeline.model.SourceFilter
import com.example.npc.ui.timeline.model.SourceHealthUiModel

@Composable
fun SourceHealthHeader(
    healthList: List<SourceHealthUiModel>,
    selectedFilter: SourceFilter,
    onFilterSelected: (SourceFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == SourceFilter.ALL,
            onClick = { onFilterSelected(SourceFilter.ALL) },
            label = { Text("Все") }
        )

        healthList.forEach { health ->
            val filterForHealth = when (health.sourceId.lowercase()) {
                "notification" -> SourceFilter.NOTIFICATION
                "sms" -> SourceFilter.SMS
                "media" -> SourceFilter.MEDIA
                else -> null
            }

            val isSelected = filterForHealth != null && selectedFilter == filterForHealth

            SourceHealthBadge(
                health = health,
                isSelected = isSelected,
                onClick = {
                    if (filterForHealth != null) {
                        if (isSelected) {
                            onFilterSelected(SourceFilter.ALL)
                        } else {
                            onFilterSelected(filterForHealth)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SourceHealthHeader(
    healthList: List<SourceHealthUiModel>,
    selectedFilter: String?,
    onFilterSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentFilter = when (selectedFilter?.lowercase()) {
        "notification" -> SourceFilter.NOTIFICATION
        "sms" -> SourceFilter.SMS
        "media" -> SourceFilter.MEDIA
        else -> SourceFilter.ALL
    }

    SourceHealthHeader(
        healthList = healthList,
        selectedFilter = currentFilter,
        onFilterSelected = { filter ->
            when (filter) {
                SourceFilter.ALL -> onFilterSelect(null)
                SourceFilter.NOTIFICATION -> onFilterSelect("notification")
                SourceFilter.SMS -> onFilterSelect("sms")
                SourceFilter.MEDIA -> onFilterSelect("media")
            }
        },
        modifier = modifier
    )
}
