package com.lirix.app.ui

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Foreground the debug-only empty host through the authorized shell before ActivityScenario.
 * Some OEMs abort launches from an instrumented background process even with overlay permission.
 * The Compose rule then owns a fresh activity and verifies the real UI normally.
 */
class ForegroundComposeHostRule : ExternalResource() {
    override fun before() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val component = "${instrumentation.targetContext.packageName}/androidx.activity.ComponentActivity"
        val descriptor = instrumentation.uiAutomation.executeShellCommand("am start -W -n $component")
        val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        check(!output.contains("Error:") && !output.contains("Exception")) { "Test host could not start: $output" }
    }
}
