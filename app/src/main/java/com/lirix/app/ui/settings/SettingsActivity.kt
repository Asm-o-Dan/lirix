package com.lirix.app.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lirix.app.storage.ThemePreferencesRepository
import com.lirix.app.ui.i18n.LocalizedAppContent
import com.lirix.app.ui.theme.LirixTheme

/** A separate settings host leaves the current player and its editing/playback state underneath. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LocalizedAppContent {
                val theme by ThemePreferencesRepository.getInstance(applicationContext).selectedTheme.collectAsState()
                SideEffect {
                    val bars = if (theme.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                }
                LirixTheme(themeType = theme) {
                    SettingsContent(onBack = { onBackPressedDispatcher.onBackPressed() })
                }
            }
        }
    }

    companion object {
        fun open(context: Context) = context.startActivity(Intent(context, SettingsActivity::class.java))
    }
}
