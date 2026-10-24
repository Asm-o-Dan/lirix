package com.lirix.app.ui.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

internal fun resolveAppLocale(language: AppLanguage, systemLanguages: List<String>): Locale = when (language) {
    AppLanguage.RUSSIAN -> Locale("ru")
    AppLanguage.ENGLISH -> Locale.ENGLISH
    AppLanguage.SYSTEM -> systemLanguages.asSequence().map { Locale.forLanguageTag(it).language }
        .firstOrNull { it == "ru" || it == "en" }?.let(::Locale) ?: Locale.ENGLISH
}

internal fun localizedContext(context: Context, configuration: Configuration, locale: Locale): Context =
    ContextThemeWrapper(context, 0).apply {
        applyOverrideConfiguration(Configuration(configuration).apply { setLocales(LocaleList(locale)) })
        // A wrapper with themeResId=0 otherwise starts from a platform default theme.
        // Copy the host theme after applying the override and before exposing this context.
        theme.setTo(context.theme)
    }

/** Keep Activity capabilities when LocalContext supplies a locale-aware resource wrapper. */
fun Context.findHostActivity(): Activity? {
    var current: Context = this
    val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Context, Boolean>())
    while (visited.add(current)) {
        if (current is Activity) return current
        current = (current as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}

@Composable
fun LocalizedAppContent(content: @Composable () -> Unit) {
    val host = LocalContext.current
    val configuration = LocalConfiguration.current
    val language by LanguageManager.language(host).collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(host, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) LanguageManager.refresh(host) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val locales = if (Build.VERSION.SDK_INT >= 33) host.getSystemService(LocaleManager::class.java).systemLocales
        else Resources.getSystem().configuration.locales
    val systemTags = (0 until locales.size()).map { locales[it].toLanguageTag() }
    val effective = resolveAppLocale(language, systemTags)
    SideEffect {
        com.lirix.app.ui.i18n.LirixStrings.currentLanguage = if (effective.language == "ru")
            com.lirix.app.ui.i18n.InterfaceLanguage.RU else com.lirix.app.ui.i18n.InterfaceLanguage.EN
    }
    val localized = remember(host, configuration, effective) { localizedContext(host, configuration, effective) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalLayoutDirection provides LayoutDirection.Ltr,
        content = content
    )
}
