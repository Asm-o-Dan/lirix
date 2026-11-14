package com.lirix.app.translations

import android.content.Context
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

internal object TranslationRuntime {
    fun create(context: Context): MachineTranslationFallback {
        val backend = OnnxMarianBackend(context)
        return AndroidMachineTranslations(context, backend, backend.cacheVersion,
            MachineTranslationEngineInfo.Private.copy(version = backend.cacheVersion))
    }
    @Composable fun Attribution() {
        val uri = LocalUriHandler.current
        TextButton(onClick = { runCatching { uri.openUri("https://opus.nlpl.eu/Opus-MT/") } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("OPUS-MT · Helsinki-NLP")
        }
    }
}
