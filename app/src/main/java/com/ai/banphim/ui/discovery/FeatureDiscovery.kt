package com.ai.banphim.ui.discovery

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ai.banphim.R
import com.ai.banphim.ui.components.StatusPill
import com.ai.banphim.ui.components.StatusTone
import com.ai.banphim.ui.theme.AppTheme
import com.ai.banphim.ui.theme.ElectricBlue500
import com.ai.banphim.ui.theme.Indigo500
import kotlin.math.roundToInt

/** Unique identifiers for real UI components spotlighted during feature discovery. */
enum class DiscoveryTargetId {
  HOME_SETUP,
  HOME_PLAYGROUND,
  SETTINGS_TRANSLATE_MODE,
  SETTINGS_AI_GATEWAY,
  TOPBAR_APPEARANCE,
  TYPING_METHOD,
  TYPING_PRIVACY,
  THEME_STUDIO,
}

/** Logical companion app sections tracked for first-visit contextual discovery. */
enum class DiscoveryArea(
  val id: String,
  @StringRes val labelRes: Int,
  val icon: ImageVector,
) {
  HOME("home", R.string.discovery_area_home, Icons.Filled.Home),
  SETTINGS("settings", R.string.discovery_area_settings, Icons.Filled.Settings),
  TYPING("typing", R.string.discovery_area_typing, Icons.Filled.Keyboard),
  THEME("theme", R.string.discovery_area_theme, Icons.Filled.Palette),
}

/** Definition of one interactive coach-mark step bound to a target UI card or button. */
data class FeatureTourStep(
  val targetId: DiscoveryTargetId,
  val area: DiscoveryArea,
  val icon: ImageVector,
  @StringRes val titleRes: Int,
  @StringRes val descRes: Int,
  val singleColumnItemIndex: Int,
  val twoColumnItemIndex: Int,
  val requiresScroll: Boolean = true,
)

/** Canonical ordered sequence of discovery steps across the entire companion app. */
object FeatureDiscoveryCatalog {
  val allSteps: List<FeatureTourStep> =
    listOf(
      FeatureTourStep(
        targetId = DiscoveryTargetId.HOME_SETUP,
        area = DiscoveryArea.HOME,
        icon = Icons.Filled.Keyboard,
        titleRes = R.string.discovery_step_home_setup_title,
        descRes = R.string.discovery_step_home_setup_desc,
        singleColumnItemIndex = 0,
        twoColumnItemIndex = 0,
      ),
      FeatureTourStep(
        targetId = DiscoveryTargetId.HOME_PLAYGROUND,
        area = DiscoveryArea.HOME,
        icon = Icons.Filled.PlayArrow,
        titleRes = R.string.discovery_step_home_playground_title,
        descRes = R.string.discovery_step_home_playground_desc,
        singleColumnItemIndex = 1,
        twoColumnItemIndex = 0,
      ),
      FeatureTourStep(
        targetId = DiscoveryTargetId.SETTINGS_TRANSLATE_MODE,
        area = DiscoveryArea.SETTINGS,
        icon = Icons.Filled.Translate,
        titleRes = R.string.discovery_step_settings_mode_title,
        descRes = R.string.discovery_step_settings_mode_desc,
        singleColumnItemIndex = 1,
        twoColumnItemIndex = 1,
      ),
      FeatureTourStep(
        targetId = DiscoveryTargetId.SETTINGS_AI_GATEWAY,
        area = DiscoveryArea.SETTINGS,
        icon = Icons.Filled.Psychology,
        titleRes = R.string.discovery_step_settings_gateway_title,
        descRes = R.string.discovery_step_settings_gateway_desc,
        singleColumnItemIndex = 2,
        twoColumnItemIndex = 1,
      ),
      FeatureTourStep(
        targetId = DiscoveryTargetId.TOPBAR_APPEARANCE,
        area = DiscoveryArea.SETTINGS,
        icon = Icons.Filled.BrightnessAuto,
        titleRes = R.string.discovery_step_topbar_appearance_title,
        descRes = R.string.discovery_step_topbar_appearance_desc,
        singleColumnItemIndex = 0,
        twoColumnItemIndex = 0,
        requiresScroll = false,
      ),
      FeatureTourStep(
        targetId = DiscoveryTargetId.TYPING_METHOD,
        area = DiscoveryArea.TYPING,
        icon = Icons.Filled.Keyboard,
        titleRes = R.string.discovery_step_typing_method_title,
        descRes = R.string.discovery_step_typing_method_desc,
        singleColumnItemIndex = 1,
        twoColumnItemIndex = 0,
      ),
      FeatureTourStep(
        targetId = DiscoveryTargetId.TYPING_PRIVACY,
        area = DiscoveryArea.TYPING,
        icon = Icons.Filled.Lock,
        titleRes = R.string.discovery_step_typing_privacy_title,
        descRes = R.string.discovery_step_typing_privacy_desc,
        singleColumnItemIndex = 2,
        twoColumnItemIndex = 0,
      ),
      FeatureTourStep(
        targetId = DiscoveryTargetId.THEME_STUDIO,
        area = DiscoveryArea.THEME,
        icon = Icons.Filled.Palette,
        titleRes = R.string.discovery_step_theme_studio_title,
        descRes = R.string.discovery_step_theme_studio_desc,
        singleColumnItemIndex = 0,
        twoColumnItemIndex = 0,
      ),
    )

  fun stepsForArea(area: DiscoveryArea): List<FeatureTourStep> =
    allSteps.filter { it.area == area }
}

/** Reactive coordinate registry tracking root-relative bounds of currently visible discovery targets. */
class DiscoveryTargetRegistry {
  private val rects = mutableStateMapOf<DiscoveryTargetId, Rect>()

  fun update(id: DiscoveryTargetId, bounds: Rect) {
    if (bounds.width > 0f && bounds.height > 0f) {
      rects[id] = bounds
    }
  }

  fun remove(id: DiscoveryTargetId) {
    rects.remove(id)
  }

  fun rectOf(id: DiscoveryTargetId): Rect? = rects[id]
}

/**
 * Registers the modified Composable's bounds in [registry] so the spotlight overlay can frame it.
 */
fun Modifier.discoveryTarget(
  id: DiscoveryTargetId,
  registry: DiscoveryTargetRegistry,
): Modifier =
  composed {
    DisposableEffect(id, registry) {
      onDispose { registry.remove(id) }
    }
    this.onGloballyPositioned { coords ->
      if (coords.isAttached) {
        registry.update(id, coords.boundsInRoot())
      }
    }
  }

/**
 * Interactive Material 3 spotlight coach-mark overlay drawing an animated cutout around the real
 * target UI component and positioning an adaptive guidance card above or below it.
 */
@Composable
fun FeatureDiscoveryOverlay(
  steps: List<FeatureTourStep>,
  currentStepIndex: Int,
  registry: DiscoveryTargetRegistry,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onSkip: () -> Unit,
) {
  val step = steps.getOrNull(currentStepIndex) ?: return
  BackHandler(onBack = onSkip)

  val ext = AppTheme.colors
  val density = LocalDensity.current
  val rawTargetRect = registry.rectOf(step.targetId)
  var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
  var cardHeightPx by remember { mutableStateOf(0) }

  val targetPaddingPx = with(density) { 8.dp.toPx() }
  val cardMarginPx = with(density) { 14.dp.toPx() }
  val horizontalPadPx = with(density) { 16.dp.toPx() }
  val cornerRadiusPx = with(density) { 20.dp.toPx() }

  val pulseTransition = rememberInfiniteTransition(label = "spotlightPulse")
  val pulseAlpha by
    pulseTransition.animateFloat(
      initialValue = 0.45f,
      targetValue = 0.95f,
      animationSpec = infiniteRepeatable(animation = tween(950), repeatMode = RepeatMode.Reverse),
      label = "pulseAlpha",
    )

  BoxWithConstraints(
    modifier =
      Modifier.fillMaxSize()
        .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
        .pointerInput(step.targetId) {
          detectTapGestures { /* Consume outside taps during active tour so user doesn't lose context */ }
        }
        .testTag("feature_discovery_overlay"),
  ) {
    val containerWidthPx = with(density) { maxWidth.toPx() }
    val containerHeightPx = with(density) { maxHeight.toPx() }
    val safeTopPx = WindowInsets.safeDrawing.getTop(density).toFloat() + with(density) { 8.dp.toPx() }
    val safeBottomPx =
      WindowInsets.safeDrawing.getBottom(density).toFloat() + with(density) { 12.dp.toPx() }

    val localTarget: Rect? =
      rawTargetRect?.let { r ->
        val translated =
          Rect(
            left = (r.left - overlayOrigin.x - targetPaddingPx).coerceIn(4f, containerWidthPx - 4f),
            top = (r.top - overlayOrigin.y - targetPaddingPx).coerceIn(4f, containerHeightPx - 4f),
            right = (r.right - overlayOrigin.x + targetPaddingPx).coerceIn(4f, containerWidthPx - 4f),
            bottom = (r.bottom - overlayOrigin.y + targetPaddingPx).coerceIn(4f, containerHeightPx - 4f),
          )
        if (translated.width > 12f && translated.height > 12f) translated else null
      }

    val animLeft by
      animateFloatAsState(
        targetValue = localTarget?.left ?: (containerWidthPx * 0.1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "spotLeft",
      )
    val animTop by
      animateFloatAsState(
        targetValue = localTarget?.top ?: (containerHeightPx * 0.35f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "spotTop",
      )
    val animRight by
      animateFloatAsState(
        targetValue = localTarget?.right ?: (containerWidthPx * 0.9f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "spotRight",
      )
    val animBottom by
      animateFloatAsState(
        targetValue = localTarget?.bottom ?: (containerHeightPx * 0.55f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "spotBottom",
      )

    // Dimmed Scrim + Offscreen BlendMode.Clear Cutout + Animated Indigo/Electric Blue Ring
    Canvas(
      modifier =
        Modifier.fillMaxSize().graphicsLayer {
          compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
      drawRect(color = Color.Black.copy(alpha = 0.74f))

      if (localTarget != null) {
        val spotW = (animRight - animLeft).coerceAtLeast(16f)
        val spotH = (animBottom - animTop).coerceAtLeast(16f)
        drawRoundRect(
          color = Color.Transparent,
          topLeft = Offset(animLeft, animTop),
          size = Size(spotW, spotH),
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
          blendMode = BlendMode.Clear,
        )
        drawRoundRect(
          brush = Brush.linearGradient(listOf(Indigo500, ElectricBlue500)),
          topLeft = Offset(animLeft, animTop),
          size = Size(spotW, spotH),
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
          style = Stroke(width = 3.dp.toPx()),
          alpha = pulseAlpha,
        )
      }
    }

    // Smart tooltip card placement above or below the spotlighted element
    val measuredCardH = if (cardHeightPx > 0) cardHeightPx.toFloat() else with(density) { 230.dp.toPx() }
    val targetTop = localTarget?.top ?: (containerHeightPx * 0.45f)
    val targetBottom = localTarget?.bottom ?: (containerHeightPx * 0.55f)
    val spaceBelow = (containerHeightPx - safeBottomPx) - targetBottom
    val spaceAbove = targetTop - safeTopPx

    val desiredY =
      when {
        localTarget == null -> (containerHeightPx - measuredCardH) / 2f
        spaceBelow >= measuredCardH + cardMarginPx || spaceBelow >= spaceAbove ->
          targetBottom + cardMarginPx
        else -> targetTop - measuredCardH - cardMarginPx
      }
    val maxValidY = (containerHeightPx - safeBottomPx - measuredCardH).coerceAtLeast(safeTopPx)
    val clampedY = desiredY.coerceIn(safeTopPx, maxValidY)

    val cardMaxWidthPx = with(density) { 460.dp.toPx() }
    val usableWidthPx = (containerWidthPx - horizontalPadPx * 2f).coerceAtMost(cardMaxWidthPx)
    val centeredX = ((containerWidthPx - usableWidthPx) / 2f).roundToInt()

    val isFirst = currentStepIndex == 0
    val isLast = currentStepIndex == steps.lastIndex

    Surface(
      color = ext.cardSurface,
      shape = RoundedCornerShape(22.dp),
      border =
        BorderStroke(
          width = 1.25.dp,
          brush = Brush.linearGradient(listOf(Indigo500.copy(alpha = 0.65f), ElectricBlue500.copy(alpha = 0.65f))),
        ),
      shadowElevation = if (ext.isAmoled) 0.dp else 10.dp,
      modifier =
        Modifier.offset { IntOffset(centeredX, clampedY.roundToInt()) }
          .width(with(density) { usableWidthPx.toDp() })
          .onGloballyPositioned { cardHeightPx = it.size.height }
          .testTag("discovery_tooltip_card"),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
          ) {
            StatusPill(
              text = stringResource(R.string.discovery_step_counter, currentStepIndex + 1, steps.size),
              tone = StatusTone.PRIMARY,
              leadingIcon = Icons.Filled.AutoAwesome,
            )
            Text(
              text = stringResource(step.area.labelRes),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          TextButton(
            onClick = onSkip,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("discovery_skip_btn"),
          ) {
            Text(
              text = stringResource(R.string.discovery_btn_skip),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        LinearProgressIndicator(
          progress = { (currentStepIndex + 1f) / steps.size.coerceAtLeast(1) },
          modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
          color = MaterialTheme.colorScheme.primary,
          trackColor = ext.cardSurfaceElevated,
          strokeCap = StrokeCap.Round,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = step.icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp),
            )
          }
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = stringResource(step.titleRes),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(step.descRes),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (!isFirst) {
            OutlinedButton(
              onClick = onPrevious,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.heightIn(min = 48.dp).testTag("discovery_prev_btn"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(stringResource(R.string.discovery_btn_back))
            }
          }
          Button(
            onClick = onNext,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("discovery_next_btn"),
          ) {
            Text(
              text =
                if (isLast) stringResource(R.string.discovery_btn_finish)
                else stringResource(R.string.discovery_btn_next),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
              imageVector =
                if (isLast) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(17.dp),
            )
          }
        }
      }
    }
  }
}

/**
 * Non-blocking contextual discovery banner displayed the first time a user explores a section
 * when the full guided tour is not currently active.
 */
@Composable
fun TabDiscoveryBanner(
  visible: Boolean,
  area: DiscoveryArea,
  onStartAreaGuide: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val ext = AppTheme.colors
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(animationSpec = tween(220)),
    exit = fadeOut(animationSpec = tween(160)),
    modifier = modifier,
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth().widthIn(max = 940.dp).clip(RoundedCornerShape(16.dp)),
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(Brush.linearGradient(listOf(ext.heroGradientStart, ext.heroGradientEnd)))
              .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
            modifier =
              Modifier.size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.Lightbulb,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
          }
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = stringResource(R.string.discovery_banner_title, stringResource(area.labelRes)),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = stringResource(R.string.discovery_banner_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          FilledTonalButton(
            onClick = onStartAreaGuide,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.heightIn(min = 40.dp).testTag("tab_discovery_start_btn"),
          ) {
            Text(
              text = stringResource(R.string.discovery_banner_start),
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("tab_discovery_dismiss_btn"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = stringResource(R.string.discovery_banner_dismiss),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

/**
 * Interactive Discovery Hub modal bottom sheet enabling users to track exploration progress,
 * replay the overview Onboarding slides, start a full interactive tour, or inspect any section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryHubBottomSheet(
  seenAreaIds: Set<String>,
  onReplayOnboarding: () -> Unit,
  onStartFullTour: () -> Unit,
  onStartAreaTour: (DiscoveryArea) -> Unit,
  onResetProgress: () -> Unit,
  onDismiss: () -> Unit,
) {
  val ext = AppTheme.colors
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val totalAreas = DiscoveryArea.entries.size
  val exploredCount = DiscoveryArea.entries.count { seenAreaIds.contains(it.id) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = ext.cardSurface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 22.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = stringResource(R.string.discovery_hub_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = stringResource(R.string.discovery_hub_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusPill(
          text = "$exploredCount/$totalAreas",
          tone = if (exploredCount == totalAreas) StatusTone.SUCCESS else StatusTone.PRIMARY,
          leadingIcon = if (exploredCount == totalAreas) Icons.Filled.CheckCircle else Icons.Filled.Explore,
        )
      }

      LinearProgressIndicator(
        progress = { exploredCount.toFloat() / totalAreas.coerceAtLeast(1) },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        color = if (exploredCount == totalAreas) ext.success else MaterialTheme.colorScheme.primary,
        trackColor = ext.cardSurfaceElevated,
        strokeCap = StrokeCap.Round,
      )
      Text(
        text = stringResource(R.string.discovery_hub_progress, exploredCount, totalAreas),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = {
            onDismiss()
            onReplayOnboarding()
          },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("hub_replay_onboarding_btn"),
        ) {
          Text(
            text = stringResource(R.string.discovery_hub_replay_onboarding),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Button(
          onClick = {
            onDismiss()
            onStartFullTour()
          },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("hub_start_full_tour_btn"),
        ) {
          Text(
            text = stringResource(R.string.discovery_hub_start_full_tour),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      HorizontalDivider(color = ext.subtleBorder)

      Text(
        text = stringResource(R.string.discovery_hub_areas_label),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DiscoveryArea.entries.forEach { area ->
          val seen = seenAreaIds.contains(area.id)
          Surface(
            color = ext.cardSurfaceElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ext.cardBorder),
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                  onDismiss()
                  onStartAreaTour(area)
                }
                .testTag("hub_area_${area.id}"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = area.icon,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
              }
              Text(
                text = stringResource(area.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
              )
              StatusPill(
                text =
                  if (seen) stringResource(R.string.discovery_hub_seen_badge)
                  else stringResource(R.string.discovery_hub_new_badge),
                tone = if (seen) StatusTone.SUCCESS else StatusTone.WARNING,
              )
              Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }

      if (exploredCount > 0) {
        TextButton(
          onClick = onResetProgress,
          modifier =
            Modifier.align(Alignment.CenterHorizontally)
              .minimumInteractiveComponentSize()
              .testTag("hub_reset_discovery_btn"),
        ) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.discovery_hub_reset),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
    }
  }
}
