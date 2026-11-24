package com.ai.banphim

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ai.banphim.data.AppLocaleManager
import com.ai.banphim.ime.BanPhimImeService
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

  @get:Rule
  val composeTestRule = createEmptyComposeRule()

  @After
  fun cleanLocalePrefs() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    AppLocaleManager.saveSyncTag(ctx, "")
  }

  @Test
  fun testMainActivityLaunchAndSkipOnboarding() {
    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.waitUntil(5_000L) {
        composeTestRule.onAllNodesWithTag("onboarding_skip_btn").fetchSemanticsNodes().isNotEmpty() ||
          composeTestRule.onAllNodesWithTag("nav_tab_home").fetchSemanticsNodes().isNotEmpty()
      }
      if (composeTestRule.onAllNodesWithTag("onboarding_skip_btn").fetchSemanticsNodes().isNotEmpty()) {
        composeTestRule.onNodeWithTag("onboarding_skip_btn").performClick()
      }

      composeTestRule.waitUntil(5_000L) {
        composeTestRule.onAllNodesWithTag("nav_tab_home").fetchSemanticsNodes().isNotEmpty()
      }

      composeTestRule.onNodeWithTag("nav_tab_home").performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("nav_tab_typing").performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("nav_tab_theme").performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("top_bar_about_btn").performClick()
      composeTestRule.waitForIdle()
    }
  }

  @Test
  fun testMainActivityLaunchWithSavedLocaleOverride() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    AppLocaleManager.saveSyncTag(ctx, "vi")

    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.waitUntil(5_000L) {
        composeTestRule.onAllNodesWithTag("onboarding_skip_btn").fetchSemanticsNodes().isNotEmpty() ||
          composeTestRule.onAllNodesWithTag("nav_tab_settings").fetchSemanticsNodes().isNotEmpty()
      }
      if (composeTestRule.onAllNodesWithTag("onboarding_skip_btn").fetchSemanticsNodes().isNotEmpty()) {
        composeTestRule.onNodeWithTag("onboarding_skip_btn").performClick()
        composeTestRule.waitUntil(5_000L) {
          composeTestRule.onAllNodesWithTag("nav_tab_settings").fetchSemanticsNodes().isNotEmpty()
        }
      }
      composeTestRule.onNodeWithTag("nav_tab_settings").assertExists()
    }
  }

  @Test
  fun testBanPhimImeServiceLifecycleTransitions() {
    val controller = Robolectric.buildService(BanPhimImeService::class.java).create()
    val service = controller.get()
    val inputView = service.onCreateInputView()
    assertNotNull(inputView)

    val info = EditorInfo()
    service.onStartInputView(info, false)
    service.onStartInputView(info, true)
    service.onFinishInputView(false)
    service.onWindowHidden()
    service.onStartInputView(info, false)
    controller.destroy()
  }
}
