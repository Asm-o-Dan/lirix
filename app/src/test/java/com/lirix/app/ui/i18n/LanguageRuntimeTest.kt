package com.lirix.app.ui.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.lirix.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Regression contract: enum parsing alone did not exercise actual resources or persistence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 32, 33, 34])
class LanguageRuntimeTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun preferences(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private class Platform(var code: String = "") : AppLocalePlatform {
        val writes = mutableListOf<String>()
        override fun readLanguageCode() = code
        override fun writeLanguageCode(value: String) { code = value; writes += value }
    }
    private fun resources(controller: AndroidLanguageController, system: List<String> = listOf("en-US")) =
        localizedContext(context, Configuration(context.resources.configuration), resolveAppLocale(controller.language.value, system)).resources

    @Test fun explicitChoiceChangesRealResourcesAndSurvivesNewController() {
        val prefs = preferences("locale-test-persist")
        val controller = AndroidLanguageController(prefs)
        controller.setLanguage(AppLanguage.ENGLISH)
        assertEquals("Now Playing", resources(controller).getString(R.string.tab_now_playing))
        assertEquals("Could not load lyrics: offline", resources(controller).getString(R.string.viewer_load_error, "offline"))
        val restarted = AndroidLanguageController(prefs)
        assertEquals(AppLanguage.ENGLISH, restarted.language.value)
        assertEquals("Now Playing", resources(restarted, listOf("ru-RU")).getString(R.string.tab_now_playing))
        controller.setLanguage(AppLanguage.RUSSIAN)
        assertEquals("Сейчас играет", resources(controller).getString(R.string.tab_now_playing))
        assertEquals("Ошибка загрузки: offline", resources(controller).getString(R.string.viewer_load_error, "offline"))
    }

    @Test fun systemChoiceFollowsSystemWhileExplicitLanguageStaysFixed() {
        assertEquals("ru", resolveAppLocale(AppLanguage.SYSTEM, listOf("ru-RU")).language)
        assertEquals("en", resolveAppLocale(AppLanguage.SYSTEM, listOf("en-US")).language)
        assertEquals("en", resolveAppLocale(AppLanguage.SYSTEM, listOf("fr", "de")).language)
        assertEquals("ru", resolveAppLocale(AppLanguage.RUSSIAN, listOf("en-US")).language)
        assertEquals("en", resolveAppLocale(AppLanguage.ENGLISH, listOf("ru-RU")).language)
    }

    @Test fun frameworkLocaleWinsAfterMigrationIncludingExternalSystemReset() {
        val prefs = preferences("locale-test-platform")
        val platform = Platform("en")
        val controller = AndroidLanguageController(prefs, platform = platform)
        assertEquals(AppLanguage.ENGLISH, controller.language.value)
        platform.code = "ru"
        controller.refresh()
        assertEquals(AppLanguage.RUSSIAN, controller.language.value)
        assertEquals("Сейчас играет", resources(controller).getString(R.string.tab_now_playing))
        platform.code = ""
        controller.refresh()
        assertEquals(AppLanguage.SYSTEM, controller.language.value)
        assertEquals(AppLanguage.SYSTEM, AndroidLanguageController(prefs, platform = platform).language.value)
        assertEquals("Now Playing", resources(controller, listOf("en-US")).getString(R.string.tab_now_playing))
        assertEquals("Сейчас играет", resources(controller, listOf("ru-RU")).getString(R.string.tab_now_playing))
        assertTrue(platform.writes.isEmpty())
    }

    @Test fun systemResourceChangesNeverRewriteExplicitChoiceOrUserContent() {
        val original = "日本語 lyric / заметка" // User data must never become a translated UI resource.
        val prefs = preferences("locale-test-content").apply { edit().putString("user_note", original).commit() }
        val controller = AndroidLanguageController(prefs)
        assertEquals("Сейчас играет", resources(controller, listOf("ru-RU")).getString(R.string.tab_now_playing))
        assertEquals("Now Playing", resources(controller, listOf("en-US")).getString(R.string.tab_now_playing))
        controller.setLanguage(AppLanguage.RUSSIAN)
        assertEquals("Сейчас играет", resources(controller, listOf("en-US")).getString(R.string.tab_now_playing))
        assertEquals(original, prefs.getString("user_note", null))
        assertEquals(AppLanguage.RUSSIAN, AndroidLanguageController(prefs).language.value)
    }

    @Test fun legacyPreferenceMigratesOnceAndDoesNotOverrideLaterSystemChoice() {
        val prefs = preferences("locale-test-current")
        val legacy = preferences("locale-test-legacy").apply { edit().putString("pref_app_language", "ru").commit() }
        val platform = Platform()
        assertEquals(AppLanguage.RUSSIAN, AndroidLanguageController(prefs, legacy, platform).language.value)
        assertEquals(listOf("ru"), platform.writes)
        platform.code = ""
        assertEquals(AppLanguage.SYSTEM, AndroidLanguageController(prefs, legacy, platform).language.value)
        assertEquals(listOf("ru"), platform.writes)
    }

    @Test fun invalidPreferenceFallsBackAndRepeatedChoiceDoesNotRewriteFramework() {
        val prefs = preferences("locale-test-invalid").apply { edit().putString("pref_app_language", "bogus").commit() }
        val platform = Platform()
        val controller = AndroidLanguageController(prefs, platform = platform)
        assertEquals(AppLanguage.SYSTEM, controller.language.value)
        controller.setLanguage(AppLanguage.ENGLISH)
        controller.setLanguage(AppLanguage.ENGLISH)
        assertEquals(listOf("en"), platform.writes)
        controller.setLanguage(AppLanguage.SYSTEM)
        assertEquals(listOf("en", ""), platform.writes)
    }

    @Test fun localizedConfigurationPreservesDeviceSettingsAndActivityOwnership() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val configuration = Configuration(activity.resources.configuration).apply {
            fontScale = 1.4f; orientation = Configuration.ORIENTATION_LANDSCAPE; densityDpi = 320
            setLocales(LocaleList(Locale.ENGLISH))
        }
        val localized = localizedContext(activity, configuration, Locale("ru"))
        assertEquals(1.4f, localized.resources.configuration.fontScale, 0.001f)
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, localized.resources.configuration.orientation)
        assertEquals(320, localized.resources.configuration.densityDpi)
        assertSame(activity, ContextThemeWrapper(localized, 0).findHostActivity())
        assertEquals("ru", localized.resources.configuration.locales[0].language)
    }
}
