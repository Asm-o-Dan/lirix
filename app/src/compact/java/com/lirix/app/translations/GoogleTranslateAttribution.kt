package com.lirix.app.translations

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lirix.app.R

/** Official unmodified artwork, on white so all brand elements stay visible in every theme. */
@Composable
internal fun GoogleTranslateAttribution() {
    val uri = LocalUriHandler.current
    Surface(color = Color.White, modifier = Modifier.heightIn(min = 48.dp)
        .clickable { runCatching { uri.openUri("https://translate.google.com") } }) {
        Image(painterResource(R.drawable.google_translate_attribution), "powered by Google Translate",
            modifier = Modifier.padding(8.dp))
    }
}
