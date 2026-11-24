package com.ai.banphim.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.banphim.R
import com.ai.banphim.ui.components.StatusPill
import com.ai.banphim.ui.components.StatusTone
import com.ai.banphim.ui.theme.AppTheme
import com.ai.banphim.ui.theme.ElectricBlue500
import com.ai.banphim.ui.theme.Indigo500
import kotlinx.coroutines.launch

private data class OnboardingSlideSpec(
  @DrawableRes val imageRes: Int,
  val badgeIcon: ImageVector,
  @StringRes val badgeRes: Int,
  @StringRes val titleRes: Int,
  @StringRes val descRes: Int,
  val chipResIds: List<Int>,
)

/**
 * Full-screen Material 3 overview onboarding introducing core keyboard, hybrid/offline
 * translation, and hardware-encrypted privacy features before launching interactive feature discovery.
 */
@Composable
fun OnboardingScreen(
  isReplay: Boolean,
  onStartGuidedDiscovery: () -> Unit,
  onSkipToApp: () -> Unit,
) {
  val ext = AppTheme.colors
  val scope = rememberCoroutineScope()
  val slides = remember {
    listOf(
      OnboardingSlideSpec(
        imageRes = R.drawable.img_onboarding_keyboard,
        badgeIcon = Icons.Filled.Keyboard,
        badgeRes = R.string.onboarding_slide1_badge,
        titleRes = R.string.onboarding_slide1_title,
        descRes = R.string.onboarding_slide1_desc,
        chipResIds = listOf(
          R.string.onboarding_slide1_chip1,
          R.string.onboarding_slide1_chip2,
          R.string.onboarding_slide1_chip3,
        ),
      ),
      OnboardingSlideSpec(
        imageRes = R.drawable.img_onboarding_translate,
        badgeIcon = Icons.Filled.Translate,
        badgeRes = R.string.onboarding_slide2_badge,
        titleRes = R.string.onboarding_slide2_title,
        descRes = R.string.onboarding_slide2_desc,
        chipResIds = listOf(
          R.string.onboarding_slide2_chip1,
          R.string.onboarding_slide2_chip2,
          R.string.onboarding_slide2_chip3,
        ),
      ),
      OnboardingSlideSpec(
        imageRes = R.drawable.img_onboarding_privacy,
        badgeIcon = Icons.Filled.Lock,
        badgeRes = R.string.onboarding_slide3_badge,
        titleRes = R.string.onboarding_slide3_title,
        descRes = R.string.onboarding_slide3_desc,
        chipResIds = listOf(
          R.string.onboarding_slide3_chip1,
          R.string.onboarding_slide3_chip2,
          R.string.onboarding_slide3_chip3,
        ),
      ),
    )
  }

  val pagerState = rememberPagerState(pageCount = { slides.size })
  val currentPage = pagerState.currentPage
  val isLastPage = currentPage == slides.lastIndex

  BackHandler(enabled = currentPage > 0 || isReplay) {
    if (currentPage > 0) {
      scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
    } else if (isReplay) {
      onSkipToApp()
    }
  }

  val bgGlowPrimary = MaterialTheme.colorScheme.primary.copy(alpha = if (ext.isAmoled) 0.14f else 0.10f)
  val bgGlowSecondary = ext.electricBlue.copy(alpha = if (ext.isAmoled) 0.12f else 0.09f)

  Surface(
    color = ext.canvasBackground,
    modifier =
      Modifier.fillMaxSize().drawBehind {
        drawCircle(
          brush =
            Brush.radialGradient(
              colors = listOf(bgGlowPrimary, Color.Transparent),
              center = Offset(size.width * 0.2f, size.height * 0.16f),
              radius = size.maxDimension * 0.55f,
            )
        )
        drawCircle(
          brush =
            Brush.radialGradient(
              colors = listOf(bgGlowSecondary, Color.Transparent),
              center = Offset(size.width * 0.85f, size.height * 0.78f),
              radius = size.maxDimension * 0.50f,
            )
        )
      },
  ) {
    BoxWithConstraints(
      modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
      contentAlignment = Alignment.Center,
    ) {
      val wideLayout = maxWidth >= 660.dp || maxHeight < 520.dp

      Column(
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = if (wideLayout) 920.dp else 560.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Top Header Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          StatusPill(
            text = stringResource(R.string.onboarding_step_badge, currentPage + 1, slides.size),
            tone = StatusTone.PRIMARY,
            leadingIcon = Icons.Filled.AutoAwesome,
          )
          TextButton(
            onClick = onSkipToApp,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_skip_btn"),
          ) {
            Text(
              text = stringResource(R.string.onboarding_skip),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Slide Content Pager
        HorizontalPager(
          state = pagerState,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { pageIdx ->
          val slide = slides[pageIdx]
          if (wideLayout) {
            Row(
              modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(24.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OnboardingIllustrationFrame(
                imageRes = slide.imageRes,
                contentDesc = stringResource(slide.titleRes),
                modifier = Modifier.weight(1f).fillMaxHeight(0.88f),
              )
              Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
              ) {
                OnboardingSlideCopy(slide = slide, centered = false)
              }
            }
          } else {
            Column(
              modifier =
                Modifier.fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(vertical = 8.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              OnboardingIllustrationFrame(
                imageRes = slide.imageRes,
                contentDesc = stringResource(slide.titleRes),
                modifier =
                  Modifier.fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .aspectRatio(4f / 3f),
              )
              Spacer(Modifier.height(22.dp))
              OnboardingSlideCopy(slide = slide, centered = true)
            }
          }
        }

        Spacer(Modifier.height(10.dp))

        // Animated Pager Indicators
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          repeat(slides.size) { idx ->
            val selected = idx == currentPage
            val width by
              animateDpAsState(
                targetValue = if (selected) 28.dp else 8.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "onboardingIndicatorWidth",
              )
            val color by
              animateColorAsState(
                targetValue =
                  if (selected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant,
                label = "onboardingIndicatorColor",
              )
            Box(
              modifier =
                Modifier.height(8.dp)
                  .width(width)
                  .clip(CircleShape)
                  .background(color)
            )
          }
        }

        Spacer(Modifier.height(16.dp))

        // Bottom Navigation & Discovery Launch Actions
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (currentPage > 0) {
            IconButton(
              onClick = { scope.launch { pagerState.animateScrollToPage(currentPage - 1) } },
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(14.dp))
                  .background(ext.cardSurfaceElevated)
                  .testTag("onboarding_back_btn"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.onboarding_back),
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }
          }

          Button(
            onClick = {
              if (isLastPage) {
                onStartGuidedDiscovery()
              } else {
                scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
              }
            },
            shape = RoundedCornerShape(16.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier =
              Modifier.weight(1f)
                .heightIn(min = 52.dp)
                .testTag(if (isLastPage) "onboarding_start_discovery_btn" else "onboarding_next_btn"),
          ) {
            Icon(
              imageVector =
                if (isLastPage) Icons.Filled.Explore else Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text =
                if (isLastPage) stringResource(R.string.onboarding_start_discovery)
                else stringResource(R.string.onboarding_next),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        if (isLastPage) {
          TextButton(
            onClick = onSkipToApp,
            modifier =
              Modifier.padding(top = 2.dp)
                .minimumInteractiveComponentSize()
                .testTag("onboarding_enter_app_btn"),
          ) {
            Text(
              text = stringResource(R.string.onboarding_enter_app),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          Spacer(Modifier.height(6.dp))
        }
      }
    }
  }
}

@Composable
private fun OnboardingIllustrationFrame(
  @DrawableRes imageRes: Int,
  contentDesc: String,
  modifier: Modifier = Modifier,
) {
  val ext = AppTheme.colors
  val frameShape = RoundedCornerShape(24.dp)

  Surface(
    shape = frameShape,
    color = ext.cardSurface,
    border =
      BorderStroke(
        width = 1.25.dp,
        brush = Brush.linearGradient(listOf(Indigo500.copy(alpha = 0.45f), ElectricBlue500.copy(alpha = 0.45f))),
      ),
    shadowElevation = if (ext.isAmoled) 0.dp else 4.dp,
    modifier = modifier.clip(frameShape),
  ) {
    Image(
      painter = painterResource(id = imageRes),
      contentDescription = contentDesc,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingSlideCopy(
  slide: OnboardingSlideSpec,
  centered: Boolean,
) {
  val ext = AppTheme.colors
  val horizontalAlign = if (centered) Alignment.CenterHorizontally else Alignment.Start
  val textAlign = if (centered) TextAlign.Center else TextAlign.Start

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = horizontalAlign,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    StatusPill(
      text = stringResource(slide.badgeRes),
      tone = StatusTone.PRIMARY,
      leadingIcon = slide.badgeIcon,
    )
    Text(
      text = stringResource(slide.titleRes),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = textAlign,
    )
    Text(
      text = stringResource(slide.descRes),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = textAlign,
    )
    FlowRow(
      horizontalArrangement =
        Arrangement.spacedBy(8.dp, alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
      slide.chipResIds.forEach { chipRes ->
        Surface(
          color = ext.cardSurfaceElevated,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, ext.subtleBorder),
        ) {
          Text(
            text = stringResource(chipRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }
    }
  }
}
