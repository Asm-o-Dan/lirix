package com.example.npc.app.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

object HyperOsIntegration {

    fun isHyperOsOrMiui(): Boolean {
        val manufacturer = Build.MANUFACTURER
        if (manufacturer.equals("Xiaomi", ignoreCase = true) ||
            manufacturer.equals("Redmi", ignoreCase = true) ||
            manufacturer.equals("POCO", ignoreCase = true)
        ) {
            return true
        }

        val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
        if (!miuiVersion.isNullOrBlank()) return true

        val hyperOsVersion = getSystemProperty("ro.mi.os.version.name")
        if (!hyperOsVersion.isNullOrBlank()) return true

        return false
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            get.invoke(c, key) as? String
        } catch (_: Exception) {
            null
        }
    }

    fun getAutostartIntent(context: Context): Intent {
        val candidates = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings"
                )
            )
        )

        for (candidate in candidates) {
            val resolved = context.packageManager.resolveActivity(
                candidate,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            if (resolved != null) {
                return candidate.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
