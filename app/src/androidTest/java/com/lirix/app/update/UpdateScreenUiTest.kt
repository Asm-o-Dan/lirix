package com.lirix.app.update

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lirix.app.R
import com.lirix.app.ui.ForegroundComposeHostRule
import com.lirix.app.ui.theme.LirixTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateScreenUiTest {
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: org.junit.rules.TestRule = org.junit.rules.RuleChain
        .outerRule(ForegroundComposeHostRule()).around(ui)

    @Test fun updateStatesKeepActionsReachableAndLabelsUnclippedAtLargeFont() {
        val state = mutableStateOf(UpdateScreenState("1.5.1", R.string.update_current))
        var primaryClicks = 0
        var cancellations = 0
        var backs = 0
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                LirixTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        UpdateScreen(state.value, { backs++ }, { primaryClicks++ }, { cancellations++ })
                    }
                }
            }
        }
        fun labelFits(tag: String) {
            val node = ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            val results = mutableListOf<TextLayoutResult>()
            // Buttons merge their Text child; the Text layout action remains available in their semantics.
            assertTrue("No text layout for $tag", node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true)
            assertTrue(results.isNotEmpty())
            assertFalse("Clipped $tag: ${results.map { it.size to it.multiParagraph.height }}", results.any { it.hasVisualOverflow })
        }
        fun primaryLabelFits() {
            val action = ui.onNodeWithTag("update-primary").performScrollTo().assertIsDisplayed()
            val bounds = action.fetchSemanticsNode().boundsInRoot
            val density = ui.activity.resources.displayMetrics.density
            assertTrue("Action shorter than 48 dp", bounds.height >= 48 * density)
            val label = ui.onNode(hasText(ui.activity.getString(when (state.value.message) {
                R.string.update_available -> R.string.update_download
                R.string.update_failed -> R.string.update_retry
                R.string.update_signature -> R.string.update_check
                else -> R.string.update_check
            })) and hasAnyAncestor(hasTestTag("update-primary")), useUnmergedTree = true)
            val results = mutableListOf<TextLayoutResult>()
            assertTrue(label.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true)
            assertFalse("Clipped primary label", results.any { it.hasVisualOverflow })
        }
        ui.onNodeWithContentDescription(ui.activity.getString(R.string.action_back)).performClick()
        ui.runOnIdle { assertEquals(1, backs) }
        labelFits("update-installed")
        ui.onNodeWithTag("update-installed").assertTextEquals(ui.activity.getString(R.string.update_installed, "1.5.1"))
        labelFits("update-status")
        primaryLabelFits()
        ui.runOnIdle { state.value = state.value.copy(message = R.string.update_checking, busy = true) }
        ui.onNodeWithTag("update-primary").performScrollTo().assertIsNotEnabled()
        ui.runOnIdle {
            state.value = state.value.copy(message = R.string.update_available, busy = false, releaseVersion = "1.6.0")
        }
        labelFits("update-status"); primaryLabelFits()
        ui.onNodeWithTag("update-primary").performClick()
        ui.runOnIdle { assertEquals(1, primaryClicks); state.value = state.value.copy(message = R.string.update_downloading, busy = true, progress = .42f) }
        labelFits("update-status")
        ui.onNodeWithTag("update-progress").performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(.42f, 0f..1f)))
        ui.onNodeWithTag("update-primary").assertDoesNotExist()
        ui.onNodeWithTag("update-cancel").performScrollTo().assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(1, cancellations); state.value = state.value.copy(message = R.string.update_failed, busy = false, progress = null) }
        labelFits("update-status"); labelFits("update-detail"); primaryLabelFits()
        ui.onNodeWithTag("update-primary").performClick()
        ui.runOnIdle { assertEquals(2, primaryClicks); state.value = state.value.copy(message = R.string.update_signature) }
        labelFits("update-status"); labelFits("update-detail"); primaryLabelFits()
        ui.onNodeWithText(ui.activity.getString(R.string.update_install)).assertDoesNotExist()
    }
}
