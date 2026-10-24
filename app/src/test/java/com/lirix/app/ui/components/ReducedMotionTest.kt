package com.lirix.app.ui.components

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReducedMotionTest {
    @Test fun readsCurrentSystemSettingInsteadOfRememberingFirstLaunchValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        val key = Settings.Global.ANIMATOR_DURATION_SCALE
        val previous = Settings.Global.getString(resolver, key)
        try {
            Settings.Global.putFloat(resolver, key, 1f)
            assertFalse(readReducedMotion(context))
            Settings.Global.putFloat(resolver, key, 0f)
            assertTrue(readReducedMotion(context))
            Settings.Global.putFloat(resolver, key, .5f)
            assertFalse(readReducedMotion(context))
        } finally { Settings.Global.putString(resolver, key, previous) }
    }
}
