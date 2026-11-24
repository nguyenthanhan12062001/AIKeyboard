package com.ai.banphim

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.banphim.data.AppLocaleManager
import com.ai.banphim.ui.ProvideAppLocale
import com.ai.banphim.ui.SettingsScreen
import com.ai.banphim.ui.SettingsViewModel
import com.ai.banphim.ui.theme.AIBanPhimTheme

/**
 * Entry activity hosting the Material 3 companion Settings, Home/Playground,
 * Typing, Theme, and About experience with live per-app locale switching.
 */
class MainActivity : ComponentActivity() {
  private val settingsViewModel: SettingsViewModel by viewModels()

  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(newBase)
    val savedTag = AppLocaleManager.getSavedSyncTag(newBase)
    if (savedTag.isNotEmpty()) {
      runCatching {
        applyOverrideConfiguration(
          AppLocaleManager.buildLocalizedConfiguration(newBase.resources.configuration, savedTag)
        )
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
      ProvideAppLocale(localeTag = settings.appLanguageCode) {
        AIBanPhimTheme(themePreset = settings.themePreset) {
          SettingsScreen(viewModel = settingsViewModel)
        }
      }
    }
  }

  /** Refreshes Android IME activation status whenever the user returns from system settings. */
  override fun onResume() {
    super.onResume()
    settingsViewModel.refreshImeStatus()
  }
}
