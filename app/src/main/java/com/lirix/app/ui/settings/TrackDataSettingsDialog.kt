package com.lirix.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lirix.app.R
import com.lirix.app.feature.metadata.TrackMetadataSettings
import com.lirix.app.metadata.TrackMetadataResolver
import com.lirix.app.storage.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Consent for catalogue lookups, the optional personal Last.fm key and removal of loaded data. */
@Composable
fun TrackDataSettingsDialog(settings: TrackMetadataSettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by settings.enabled.collectAsState()
    var key by rememberSaveable { mutableStateOf(settings.lastFmKey) }
    var cleared by remember { mutableStateOf(false) }
    val keyValid = key.isBlank() || TrackMetadataResolver.lastFmKeyPattern.matches(key.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.track_data_title)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = { settings.setEnabled(it) })
                    .testTag("track-data-switch"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.track_data_enable), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = enabled, onCheckedChange = null)
                }
                Text(stringResource(R.string.track_data_explanation), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.track_data_limits), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = key, onValueChange = { key = it.take(40) }, singleLine = true, isError = !keyValid,
                    modifier = Modifier.fillMaxWidth().testTag("track-data-lastfm"),
                    label = { Text(stringResource(R.string.track_data_lastfm_key)) },
                    supportingText = { Text(stringResource(if (keyValid) R.string.track_data_lastfm_hint else R.string.track_data_lastfm_invalid)) })
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { AppDatabase.getInstance(context.applicationContext).trackMetadataDao().clear() }
                        cleared = true
                    }
                }) { Text(stringResource(if (cleared) R.string.track_data_cleared else R.string.track_data_clear)) }
            }
        },
        confirmButton = {
            TextButton(enabled = keyValid, onClick = { settings.lastFmKey = key; onDismiss() }) { Text(stringResource(R.string.action_done)) }
        }
    )
}
