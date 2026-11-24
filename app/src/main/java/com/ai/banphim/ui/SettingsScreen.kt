package com.ai.banphim.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.ai.banphim.ui.screens.CustomizerState
import com.ai.banphim.ui.screens.ThemeCustomizerDialog
import com.ai.banphim.ui.screens.themeStudioTabContent
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.banphim.R
import com.ai.banphim.data.KeyboardThemePreset
import com.ai.banphim.ui.components.AppearanceModeBottomSheet
import com.ai.banphim.ui.discovery.DiscoveryArea
import com.ai.banphim.ui.discovery.DiscoveryHubBottomSheet
import com.ai.banphim.ui.discovery.DiscoveryTargetId
import com.ai.banphim.ui.discovery.DiscoveryTargetRegistry
import com.ai.banphim.ui.discovery.FeatureDiscoveryCatalog
import com.ai.banphim.ui.discovery.FeatureDiscoveryOverlay
import com.ai.banphim.ui.discovery.FeatureTourStep
import com.ai.banphim.ui.discovery.TabDiscoveryBanner
import com.ai.banphim.ui.discovery.discoveryTarget
import com.ai.banphim.ui.onboarding.OnboardingScreen
import com.ai.banphim.ui.screens.AboutEntryCard
import com.ai.banphim.ui.screens.AiGatewayCard
import com.ai.banphim.ui.screens.AppLanguageCard
import com.ai.banphim.ui.screens.ClipboardManagerCard
import com.ai.banphim.ui.screens.ImeSetupHeroCard
import com.ai.banphim.ui.screens.InputMethodSelectionCard
import com.ai.banphim.ui.screens.KeyboardLanguagesCard
import com.ai.banphim.ui.screens.OptionalByokCard
import com.ai.banphim.ui.screens.PersonalizedLearningCard
import com.ai.banphim.ui.screens.PlaygroundStudioCard
import com.ai.banphim.ui.screens.StatusOverviewCard
import com.ai.banphim.ui.screens.ThemePresetStudioItem
import com.ai.banphim.ui.screens.ThemeStudioHeaderCard
import com.ai.banphim.ui.screens.TranslationModeCard
import com.ai.banphim.ui.screens.TypingBehaviorCard
import com.ai.banphim.ui.theme.AppTheme
import com.ai.banphim.ui.theme.ElectricBlue500
import com.ai.banphim.ui.theme.Indigo600
import kotlinx.coroutines.delay

/** Top-level destinations in the companion Settings app, defaulting to [SETTINGS] on launch. */
private enum class SettingsTab(
  @StringRes val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  SETTINGS(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
  HOME(R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
  TYPING(R.string.tab_typing, Icons.Filled.Keyboard, Icons.Outlined.Keyboard),
  THEME(R.string.tab_theme, Icons.Filled.Palette, Icons.Outlined.Palette),
}

private fun SettingsTab.toDiscoveryArea(): DiscoveryArea =
  when (this) {
    SettingsTab.SETTINGS -> DiscoveryArea.SETTINGS
    SettingsTab.HOME -> DiscoveryArea.HOME
    SettingsTab.TYPING -> DiscoveryArea.TYPING
    SettingsTab.THEME -> DiscoveryArea.THEME
  }

private fun DiscoveryArea.toSettingsTab(): SettingsTab =
  when (this) {
    DiscoveryArea.HOME -> SettingsTab.HOME
    DiscoveryArea.SETTINGS -> SettingsTab.SETTINGS
    DiscoveryArea.TYPING -> SettingsTab.TYPING
    DiscoveryArea.THEME -> SettingsTab.THEME
  }

/**
 * Adaptive Material 3 Settings, Playground, Clipboard Pin Manager, and Theme hub
 * integrated with first-launch Onboarding and interactive Feature Discovery coach-marks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
  val settingsLoaded by viewModel.settingsLoaded.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val imeStatus by viewModel.imeStatus.collectAsStateWithLifecycle()
  val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
  val clips by viewModel.clips.collectAsStateWithLifecycle()
  val personalLexiconStats by viewModel.personalLexiconStats.collectAsStateWithLifecycle()

  val ext = AppTheme.colors
  val context = LocalContext.current

  var customizerState by remember { mutableStateOf<CustomizerState?>(null) }
  val photoLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }
      customizerState = CustomizerState("PHOTO", uri.toString(), 0.7f)
    }
  }

  var activeTab by rememberSaveable { mutableStateOf(SettingsTab.SETTINGS) }
  var showAboutScreen by rememberSaveable { mutableStateOf(false) }
  var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
  var showDiscoveryHubSheet by rememberSaveable { mutableStateOf(false) }
  var showOnboardingReplay by rememberSaveable { mutableStateOf(false) }
  var playgroundText by rememberSaveable { mutableStateOf("") }

  var activeTourKey by rememberSaveable { mutableStateOf<String?>(null) }
  var activeTourStepIndex by rememberSaveable { mutableIntStateOf(0) }
  val activeTourSteps: List<FeatureTourStep> =
    remember(activeTourKey) {
      when {
        activeTourKey == "full" -> FeatureDiscoveryCatalog.allSteps
        activeTourKey?.startsWith("area:") == true -> {
          val id = activeTourKey!!.removePrefix("area:")
          val area = DiscoveryArea.entries.firstOrNull { it.id == id }
          if (area != null) FeatureDiscoveryCatalog.stepsForArea(area) else emptyList()
        }
        else -> emptyList()
      }
    }
  val targetRegistry = remember { DiscoveryTargetRegistry() }
  val settingsListState = rememberLazyListState()
  val homeListState = rememberLazyListState()
  val typingListState = rememberLazyListState()
  val themeListState = rememberLazyListState()
  val listStates =
    remember(settingsListState, homeListState, typingListState, themeListState) {
      mapOf(
        SettingsTab.SETTINGS to settingsListState,
        SettingsTab.HOME to homeListState,
        SettingsTab.TYPING to typingListState,
        SettingsTab.THEME to themeListState,
      )
    }

  val appearanceIcon =
    when (settings.themePreset) {
      KeyboardThemePreset.DYNAMIC -> Icons.Default.BrightnessAuto
      KeyboardThemePreset.LIGHT -> Icons.Default.LightMode
      KeyboardThemePreset.DARK -> Icons.Default.DarkMode
      KeyboardThemePreset.AMOLED -> Icons.Default.NightlightRound
      KeyboardThemePreset.OCEAN -> Icons.Default.WaterDrop
    }

  if (!settingsLoaded) {
    Box(modifier = Modifier.fillMaxSize().background(ext.canvasBackground))
  } else if (!settings.hasCompletedOnboarding || showOnboardingReplay) {
    OnboardingScreen(
      isReplay = settings.hasCompletedOnboarding,
      onStartGuidedDiscovery = {
        viewModel.setOnboardingCompleted(true)
        showOnboardingReplay = false
        activeTourKey = "full"
        activeTourStepIndex = 0
      },
      onSkipToApp = {
        viewModel.setOnboardingCompleted(true)
        showOnboardingReplay = false
      },
    )
  } else if (showAboutScreen) {
    AboutScreen(onBack = { showAboutScreen = false })
  } else {

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize().background(ext.canvasBackground),
  ) {
    val useRail = maxWidth >= 600.dp
    val compactHeight = maxHeight < 500.dp
    val useTwoColumns = maxWidth >= 840.dp

    val activeStep = activeTourSteps.getOrNull(activeTourStepIndex)
    LaunchedEffect(activeTourSteps, activeTourStepIndex, useTwoColumns) {
      if (activeStep != null) {
        val targetTab = activeStep.area.toSettingsTab()
        val tabChanged = activeTab != targetTab
        if (tabChanged) {
          activeTab = targetTab
        }
        viewModel.markDiscoveryAreaSeen(activeStep.area.id)
        if (tabChanged) {
          delay(140)
        }
        if (activeStep.requiresScroll) {
          val targetIdx =
            if (useTwoColumns) activeStep.twoColumnItemIndex else activeStep.singleColumnItemIndex
          listStates.getValue(targetTab).animateScrollToItem(targetIdx)
        }
      }
    }

    Row(modifier = Modifier.fillMaxSize()) {
      if (useRail) {
        NavigationRail(
          containerColor = ext.cardSurface,
          contentColor = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
          Spacer(Modifier.height(12.dp))
          SettingsTab.entries.forEach { tab ->
            val selected = activeTab == tab
            val label = stringResource(tab.labelRes)
            NavigationRailItem(
              selected = selected,
              onClick = { activeTab = tab },
              icon = {
                Icon(
                  imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                  contentDescription = label,
                )
              },
              label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
              colors =
                NavigationRailItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.primary,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
            )
          }
        }
        VerticalDivider(thickness = 1.dp, color = ext.subtleBorder)
      }

      Scaffold(
        modifier = Modifier.weight(1f),
        containerColor = ext.canvasBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          Column(modifier = Modifier.fillMaxWidth().background(ext.cardSurface)) {
            TopAppBar(
              colors =
                TopAppBarDefaults.topAppBarColors(
                  containerColor = ext.cardSurface,
                  titleContentColor = MaterialTheme.colorScheme.onSurface,
                  actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              title = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Box(
                    modifier =
                      Modifier.size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(Indigo600, ElectricBlue500))),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Keyboard,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onPrimary,
                      modifier = Modifier.size(19.dp),
                    )
                  }
                  Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                      text = stringResource(R.string.app_name),
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    if (!compactHeight) {
                      Text(
                        text = stringResource(R.string.app_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                }
              },
              actions = {
                IconButton(
                  onClick = { showDiscoveryHubSheet = true },
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("top_bar_guide_btn"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = stringResource(R.string.discovery_hub_cd),
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
                IconButton(
                  onClick = { showAppearanceSheet = true },
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .discoveryTarget(DiscoveryTargetId.TOPBAR_APPEARANCE, targetRegistry)
                      .testTag("top_bar_appearance_btn"),
                ) {
                  Icon(
                    imageVector = appearanceIcon,
                    contentDescription = stringResource(R.string.appearance_mode_cd),
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
                IconButton(
                  onClick = { showAboutScreen = true },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("top_bar_about_btn"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.about_screen_title),
                  )
                }
              },
            )
            HorizontalDivider(thickness = 1.dp, color = ext.subtleBorder)
          }
        },
        bottomBar = {
          if (!useRail) {
            Column(modifier = Modifier.fillMaxWidth().background(ext.cardSurface)) {
              HorizontalDivider(thickness = 1.dp, color = ext.subtleBorder)
              NavigationBar(
                containerColor = ext.cardSurface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding(),
              ) {
                SettingsTab.entries.forEach { tab ->
                  val selected = activeTab == tab
                  val label = stringResource(tab.labelRes)
                  NavigationBarItem(
                    selected = selected,
                    onClick = { activeTab = tab },
                    icon = {
                      Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = label,
                      )
                    },
                    label = {
                      Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    },
                    colors =
                      NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      ),
                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                  )
                }
              }
            }
          }
        },
      ) { innerPadding ->
        Box(
          modifier =
            Modifier.fillMaxSize()
              .padding(innerPadding)
              .let { m -> if (useRail) m.navigationBarsPadding() else m },
          contentAlignment = Alignment.TopCenter,
        ) {
          AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
              fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
            },
            label = "settingsTabTransition",
          ) { tab ->
            val currentArea = tab.toDiscoveryArea()
            val showBanner =
              activeTourSteps.isEmpty() &&
                !settings.discoveryTipsDismissed &&
                !settings.seenDiscoveryAreas.contains(currentArea.id)

            Column(
              modifier = Modifier.fillMaxSize(),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              TabDiscoveryBanner(
                visible = showBanner,
                area = currentArea,
                onStartAreaGuide = {
                  viewModel.markDiscoveryAreaSeen(currentArea.id)
                  activeTourKey = "area:${currentArea.id}"
                  activeTourStepIndex = 0
                },
                onDismiss = { viewModel.markDiscoveryAreaSeen(currentArea.id) },
              )

              LazyColumn(
                state = listStates.getValue(tab),
                modifier =
                  Modifier.weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = if (useTwoColumns) 940.dp else 660.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                when (tab) {
                  SettingsTab.HOME -> {
                    if (useTwoColumns) {
                      item {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.spacedBy(14.dp),
                          verticalAlignment = Alignment.Top,
                        ) {
                          Box(
                            modifier =
                              Modifier.weight(1f)
                                .discoveryTarget(DiscoveryTargetId.HOME_SETUP, targetRegistry),
                          ) {
                            ImeSetupHeroCard(
                              imeStatus = imeStatus,
                              onRefresh = { viewModel.refreshImeStatus() },
                            )
                          }
                          Box(
                            modifier =
                              Modifier.weight(1f)
                                .discoveryTarget(DiscoveryTargetId.HOME_PLAYGROUND, targetRegistry),
                          ) {
                            PlaygroundStudioCard(
                              text = playgroundText,
                              onTextChange = { playgroundText = it },
                              inputMode = settings.inputMethodMode,
                            )
                          }
                        }
                      }
                    } else {
                      item {
                        Box(
                          modifier =
                            Modifier.fillMaxWidth()
                              .discoveryTarget(DiscoveryTargetId.HOME_SETUP, targetRegistry),
                        ) {
                          ImeSetupHeroCard(
                            imeStatus = imeStatus,
                            onRefresh = { viewModel.refreshImeStatus() },
                          )
                        }
                      }
                      item {
                        Box(
                          modifier =
                            Modifier.fillMaxWidth()
                              .discoveryTarget(DiscoveryTargetId.HOME_PLAYGROUND, targetRegistry),
                        ) {
                          PlaygroundStudioCard(
                            text = playgroundText,
                            onTextChange = { playgroundText = it },
                            inputMode = settings.inputMethodMode,
                          )
                        }
                      }
                    }
                  item {
                    StatusOverviewCard(
                      settings = settings,
                      clipCount = clips.size,
                      freeModelCount = aiStatus.catalog.models.size,
                      onClearUnpinned = { viewModel.clearUnpinnedClips() },
                    )
                  }
                  item {
                    ClipboardManagerCard(
                      clips = clips,
                      onAddPinned = { viewModel.addPinnedClip(it) },
                      onTogglePin = { viewModel.togglePinClip(it) },
                      onDeleteClip = { viewModel.deleteClip(it) },
                      onClearUnpinned = { viewModel.clearUnpinnedClips() },
                    )
                  }
                }

                SettingsTab.TYPING -> {
                  if (useTwoColumns) {
                    item {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top,
                      ) {
                        Column(
                          modifier = Modifier.weight(1f),
                          verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                          KeyboardLanguagesCard(
                            activeLanguage = settings.activeLanguage,
                            enabledLanguages = settings.enabledLanguages,
                            onSelectActive = { viewModel.updateActiveLanguage(it) },
                            onToggleEnabled = { viewModel.toggleLanguageEnabled(it) },
                          )
                          Box(
                            modifier =
                              Modifier.fillMaxWidth()
                                .discoveryTarget(DiscoveryTargetId.TYPING_PRIVACY, targetRegistry),
                          ) {
                            PersonalizedLearningCard(
                              personalizedEnabled = settings.personalizedSuggestionsEnabled,
                              stats = personalLexiconStats,
                              onTogglePersonalized = { viewModel.updatePersonalizedSuggestions(it) },
                              onClearLearnedWords = { viewModel.clearPersonalLexicon() },
                            )
                          }
                        }
                        Column(
                          modifier = Modifier.weight(1f),
                          verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                          Box(
                            modifier =
                              Modifier.fillMaxWidth()
                                .discoveryTarget(DiscoveryTargetId.TYPING_METHOD, targetRegistry),
                          ) {
                            InputMethodSelectionCard(
                              selectedMode = settings.inputMethodMode,
                              toneStyle = settings.tonePlacementStyle,
                              onSelectMode = { viewModel.updateInputMethodMode(it) },
                              onSelectToneStyle = { viewModel.updateTonePlacementStyle(it) },
                            )
                          }
                          TypingBehaviorCard(
                            autoCorrect = settings.autoCorrectionEnabled,
                            showSuggestions = settings.showSuggestions,
                            showNumberRow = settings.showNumberRow,
                            floatingMode = settings.floatingMode,
                            haptic = settings.hapticFeedbackEnabled,
                            sound = settings.soundFeedbackEnabled,
                            heightScale = settings.keyboardHeightScale,
                            onAutoCorrectChange = { viewModel.updateAutoCorrect(it) },
                            onShowSuggestionsChange = { viewModel.updateShowSuggestions(it) },
                            onShowNumberRowChange = { viewModel.updateShowNumberRow(it) },
                            onFloatingModeChange = { viewModel.updateFloatingMode(it) },
                            onHapticChange = { viewModel.updateHaptic(it) },
                            onSoundChange = { viewModel.updateSound(it) },
                            onHeightScaleChange = { viewModel.updateHeightScale(it) },
                          )
                        }
                      }
                    }
                  } else {
                    item {
                      KeyboardLanguagesCard(
                        activeLanguage = settings.activeLanguage,
                        enabledLanguages = settings.enabledLanguages,
                        onSelectActive = { viewModel.updateActiveLanguage(it) },
                        onToggleEnabled = { viewModel.toggleLanguageEnabled(it) },
                      )
                    }
                    item {
                      Box(
                        modifier =
                          Modifier.fillMaxWidth()
                            .discoveryTarget(DiscoveryTargetId.TYPING_METHOD, targetRegistry),
                      ) {
                        InputMethodSelectionCard(
                          selectedMode = settings.inputMethodMode,
                          toneStyle = settings.tonePlacementStyle,
                          onSelectMode = { viewModel.updateInputMethodMode(it) },
                          onSelectToneStyle = { viewModel.updateTonePlacementStyle(it) },
                        )
                      }
                    }
                    item {
                      Box(
                        modifier =
                          Modifier.fillMaxWidth()
                            .discoveryTarget(DiscoveryTargetId.TYPING_PRIVACY, targetRegistry),
                      ) {
                        PersonalizedLearningCard(
                          personalizedEnabled = settings.personalizedSuggestionsEnabled,
                          stats = personalLexiconStats,
                          onTogglePersonalized = { viewModel.updatePersonalizedSuggestions(it) },
                          onClearLearnedWords = { viewModel.clearPersonalLexicon() },
                        )
                      }
                    }
                    item {
                      TypingBehaviorCard(
                        autoCorrect = settings.autoCorrectionEnabled,
                        showSuggestions = settings.showSuggestions,
                        showNumberRow = settings.showNumberRow,
                        floatingMode = settings.floatingMode,
                        haptic = settings.hapticFeedbackEnabled,
                        sound = settings.soundFeedbackEnabled,
                        heightScale = settings.keyboardHeightScale,
                        onAutoCorrectChange = { viewModel.updateAutoCorrect(it) },
                        onShowSuggestionsChange = { viewModel.updateShowSuggestions(it) },
                        onShowNumberRowChange = { viewModel.updateShowNumberRow(it) },
                        onFloatingModeChange = { viewModel.updateFloatingMode(it) },
                        onHapticChange = { viewModel.updateHaptic(it) },
                        onSoundChange = { viewModel.updateSound(it) },
                        onHeightScaleChange = { viewModel.updateHeightScale(it) },
                      )
                    }
                  }
                }

                SettingsTab.THEME -> {
                  themeStudioTabContent(
                    settings = settings,
                    viewModel = viewModel,
                    useTwoColumns = useTwoColumns,
                    targetRegistry = targetRegistry,
                    onOpenCustomizer = { customizerState = it },
                    onLaunchPhotoPicker = {
                      photoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    },
                  )
                }

                SettingsTab.SETTINGS -> {
                  item {
                    AppLanguageCard(
                      selectedCode = settings.appLanguageCode,
                      onSelectCode = { viewModel.updateAppLanguage(it) },
                    )
                  }
                  if (useTwoColumns) {
                    item {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top,
                      ) {
                        Column(
                          modifier = Modifier.weight(1f),
                          verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                          Box(
                            modifier =
                              Modifier.fillMaxWidth()
                                .discoveryTarget(
                                  DiscoveryTargetId.SETTINGS_TRANSLATE_MODE,
                                  targetRegistry,
                                ),
                          ) {
                            TranslationModeCard(
                              selectedMode = settings.translationMode,
                              aiStatus = aiStatus,
                              onSelectMode = { viewModel.updateTranslationMode(it) },
                              onDownloadPack = { viewModel.downloadOfflinePack(it) },
                              onDeletePack = { viewModel.deleteOfflinePack(it) },
                            )
                          }
                          OptionalByokCard(
                            currentKey = settings.optionalGeminiApiKey,
                            onSaveKey = { viewModel.updateOptionalGeminiKey(it) },
                          )
                        }
                        Column(
                          modifier = Modifier.weight(1f),
                          verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                          Box(
                            modifier =
                              Modifier.fillMaxWidth()
                                .discoveryTarget(
                                  DiscoveryTargetId.SETTINGS_AI_GATEWAY,
                                  targetRegistry,
                                ),
                          ) {
                            AiGatewayCard(
                              aiStatus = aiStatus,
                              liveTranslateEnabled = settings.liveTranslateAsYouType,
                              preferredModelId = settings.preferredAiModelId,
                              autoSwitchOnLimit = settings.autoSwitchAiModel,
                              sourceCode = settings.defaultSourceLang,
                              targetCode = settings.defaultTargetLang,
                              onLiveTranslateChange = { viewModel.updateLiveTranslate(it) },
                              onSelectModel = { viewModel.updatePreferredAiModel(it) },
                              onAutoSwitchChange = { viewModel.updateAutoSwitchAiModel(it) },
                              onSourceSelected = {
                                viewModel.updateLanguages(it, settings.defaultTargetLang)
                              },
                              onTargetSelected = {
                                viewModel.updateLanguages(settings.defaultSourceLang, it)
                              },
                              onSwapLanguages = {
                                viewModel.updateLanguages(
                                  settings.defaultTargetLang,
                                  settings.defaultSourceLang,
                                )
                              },
                              onRefreshCatalog = { viewModel.refreshCatalog() },
                              onRunQuickTest = { viewModel.runQuickTranslationTest(it) },
                            )
                          }
                          AboutEntryCard(onOpenAbout = { showAboutScreen = true })
                        }
                      }
                    }
                  } else {
                    item {
                      Box(
                        modifier =
                          Modifier.fillMaxWidth()
                            .discoveryTarget(
                              DiscoveryTargetId.SETTINGS_TRANSLATE_MODE,
                              targetRegistry,
                            ),
                      ) {
                        TranslationModeCard(
                          selectedMode = settings.translationMode,
                          aiStatus = aiStatus,
                          onSelectMode = { viewModel.updateTranslationMode(it) },
                          onDownloadPack = { viewModel.downloadOfflinePack(it) },
                          onDeletePack = { viewModel.deleteOfflinePack(it) },
                        )
                      }
                    }
                    item {
                      Box(
                        modifier =
                          Modifier.fillMaxWidth()
                            .discoveryTarget(
                              DiscoveryTargetId.SETTINGS_AI_GATEWAY,
                              targetRegistry,
                            ),
                      ) {
                        AiGatewayCard(
                          aiStatus = aiStatus,
                          liveTranslateEnabled = settings.liveTranslateAsYouType,
                          preferredModelId = settings.preferredAiModelId,
                          autoSwitchOnLimit = settings.autoSwitchAiModel,
                          sourceCode = settings.defaultSourceLang,
                          targetCode = settings.defaultTargetLang,
                          onLiveTranslateChange = { viewModel.updateLiveTranslate(it) },
                          onSelectModel = { viewModel.updatePreferredAiModel(it) },
                          onAutoSwitchChange = { viewModel.updateAutoSwitchAiModel(it) },
                          onSourceSelected = {
                            viewModel.updateLanguages(it, settings.defaultTargetLang)
                          },
                          onTargetSelected = {
                            viewModel.updateLanguages(settings.defaultSourceLang, it)
                          },
                          onSwapLanguages = {
                            viewModel.updateLanguages(
                              settings.defaultTargetLang,
                              settings.defaultSourceLang,
                            )
                          },
                          onRefreshCatalog = { viewModel.refreshCatalog() },
                          onRunQuickTest = { viewModel.runQuickTranslationTest(it) },
                        )
                      }
                    }
                    item {
                      OptionalByokCard(
                        currentKey = settings.optionalGeminiApiKey,
                        onSaveKey = { viewModel.updateOptionalGeminiKey(it) },
                      )
                    }
                    item {
                      AboutEntryCard(onOpenAbout = { showAboutScreen = true })
                    }
                  }
                }
              }
            }
            }
          }
        }
      }
    }

    if (activeTourSteps.isNotEmpty()) {
      FeatureDiscoveryOverlay(
        steps = activeTourSteps,
        currentStepIndex = activeTourStepIndex,
        registry = targetRegistry,
        onNext = {
          if (activeTourStepIndex < activeTourSteps.lastIndex) {
            activeTourStepIndex++
          } else {
            activeTourKey = null
            activeTourStepIndex = 0
          }
        },
        onPrevious = {
          if (activeTourStepIndex > 0) {
            activeTourStepIndex--
          }
        },
        onSkip = {
          activeTourKey = null
          activeTourStepIndex = 0
        },
      )
    }
  }

  val activeCustomizer = customizerState
  if (activeCustomizer != null) {
    ThemeCustomizerDialog(
      initialType = activeCustomizer.type,
      initialValue = activeCustomizer.value,
      initialOpacity = activeCustomizer.opacity,
      onDismiss = { customizerState = null },
      onApply = { type, value, opacity ->
        viewModel.applyCustomTheme(type, value, opacity)
        customizerState = null
      },
    )
  }

  if (showAppearanceSheet) {
    AppearanceModeBottomSheet(
      selectedPreset = settings.themePreset,
      onSelectPreset = { viewModel.updateThemePreset(it) },
      onDismiss = { showAppearanceSheet = false },
    )
  }

  if (showDiscoveryHubSheet) {
    DiscoveryHubBottomSheet(
      seenAreaIds = settings.seenDiscoveryAreas,
      onReplayOnboarding = { showOnboardingReplay = true },
      onStartFullTour = {
        activeTourKey = "full"
        activeTourStepIndex = 0
      },
      onStartAreaTour = { area ->
        viewModel.markDiscoveryAreaSeen(area.id)
        activeTourKey = "area:${area.id}"
        activeTourStepIndex = 0
      },
      onResetProgress = { viewModel.resetFeatureDiscovery() },
      onDismiss = { showDiscoveryHubSheet = false },
    )
  }
  }
}
