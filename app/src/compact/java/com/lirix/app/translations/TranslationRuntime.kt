package com.lirix.app.translations
import android.content.Context
import androidx.compose.runtime.Composable
internal object TranslationRuntime {
    fun create(context: Context): MachineTranslationFallback = AndroidMachineTranslations(context, MlKitTranslationBackend())
    @Composable fun Attribution() { GoogleTranslateAttribution() }
}
