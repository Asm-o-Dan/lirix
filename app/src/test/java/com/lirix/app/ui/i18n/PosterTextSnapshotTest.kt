package com.lirix.app.ui.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.lirix.app.R
import com.lirix.app.feature.share.PosterTextSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PosterTextSnapshotTest {
    @Test fun capturedPosterLanguageIsStableWhenUiLanguageChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val english = localizedContext(context, Configuration(context.resources.configuration), Locale.ENGLISH)
        val snapshot = PosterTextSnapshot.from(english)
        assertEquals("Now Playing", english.getString(R.string.tab_now_playing))
        LirixStrings.currentLanguage = InterfaceLanguage.RU
        try {
            assertEquals("Lirix · Now playing", snapshot.text("Lirix · Сейчас играет"))
            assertEquals("21 tracks", snapshot.quantity(21, "трек", "трека", "треков"))
            assertEquals("1 track", snapshot.quantity(1, "трек", "трека", "треков"))
            assertEquals("11 tracks", snapshot.quantity(11, "трек", "трека", "треков"))
            val userText = "Трек 世界 {1}"
            assertEquals("🔥 ON REPEAT: $userText — Artist", snapshot.text("🔥 ОДЕРЖИМОСТЬ: {0} — {1}", userText, "Artist"))
        } finally { LirixStrings.currentLanguage = InterfaceLanguage.EN }
    }
    @Test fun russianQuantityAndLocalizedCaptionsRemainAvailable() {
        val snapshot = PosterTextSnapshot(InterfaceLanguage.RU)
        assertEquals("21 трек", snapshot.quantity(21, "трек", "трека", "треков"))
        assertEquals("22 трека", snapshot.quantity(22, "трек", "трека", "треков"))
        assertEquals("11 треков", snapshot.quantity(11, "трек", "трека", "треков"))
        assertEquals("Lirix · Сейчас играет", snapshot.text("Lirix · Сейчас играет"))
    }
}
