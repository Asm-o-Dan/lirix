package com.example.npc.ui.timeline.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyTimelineView(
    searchQuery: String = "",
    selectedFilter: String? = null,
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSearching = searchQuery.isNotBlank() || selectedFilter != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSearching) Icons.Default.SearchOff else Icons.Default.Inbox,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSearching) "Ничего не найдено" else "Лента событий пуста",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isSearching) {
                "Попробуйте изменить поисковый запрос или сбросить активные фильтры"
            } else {
                "Ожидание входящих уведомлений, SMS-сообщений или обновлений медиа-сессий"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )

        if (isSearching) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onReset) {
                Text(text = "Сбросить фильтры")
            }
        }
    }
}
