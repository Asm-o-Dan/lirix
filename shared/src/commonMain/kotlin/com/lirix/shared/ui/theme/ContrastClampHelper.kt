package com.lirix.shared.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Package alias for [com.lirix.app.ui.theme.ContrastClampHelper].
 */
typealias ContrastClampHelper = com.lirix.app.ui.theme.ContrastClampHelper

fun clampAccentContrast(color: Color, isDark: Boolean): Color {
    return com.lirix.app.ui.theme.ContrastClampHelper.clampAccentContrast(color, isDark)
}
