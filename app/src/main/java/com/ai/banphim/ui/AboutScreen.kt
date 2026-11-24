package com.ai.banphim.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.banphim.BuildConfig
import com.ai.banphim.R
import com.ai.banphim.ui.components.HeroGradientCard
import com.ai.banphim.ui.components.PremiumCard
import com.ai.banphim.ui.components.SectionHeader
import com.ai.banphim.ui.components.StatusPill
import com.ai.banphim.ui.components.StatusTone
import com.ai.banphim.ui.theme.AppTheme
import com.ai.banphim.ui.theme.ElectricBlue600
import com.ai.banphim.ui.theme.Indigo500
import com.ai.banphim.ui.theme.MonoFontFamily

internal const val AUTHOR_CONTACT_EMAIL = "ngthan120601@gmail.com"

/**
 * Redesigned About screen aligned with the Modern Premium Indigo/Electric Blue Design System.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val ext = AppTheme.colors
  var privacyExpanded by rememberSaveable { mutableStateOf(false) }
  var termsExpanded by rememberSaveable { mutableStateOf(false) }
  var licensesExpanded by rememberSaveable { mutableStateOf(false) }

  Scaffold(
    containerColor = ext.canvasBackground,
    topBar = {
      Column {
        TopAppBar(
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = ext.cardSurface,
              titleContentColor = MaterialTheme.colorScheme.onSurface,
              navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
          title = {
            Text(
              text = stringResource(R.string.about_screen_title),
              style = MaterialTheme.typography.titleLarge,
            )
          },
          navigationIcon = {
            IconButton(
              onClick = onBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("about_back_btn"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.about_back_cd),
              )
            }
          },
        )
        HorizontalDivider(color = ext.subtleBorder, thickness = 1.dp)
      }
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .navigationBarsPadding(),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item { AboutAppIdentityCard() }
        item { AboutDeveloperCard() }
        item {
          PremiumCard(contentPadding = 6.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
              ExpandableAboutSection(
                icon = Icons.Default.PrivacyTip,
                title = stringResource(R.string.about_privacy_title),
                summary = stringResource(R.string.about_privacy_summary),
                body = stringResource(R.string.about_privacy_body),
                expanded = privacyExpanded,
                onToggle = { privacyExpanded = !privacyExpanded },
                testTag = "about_privacy_row",
              )
              HorizontalDivider(
                color = ext.subtleBorder,
                modifier = Modifier.padding(horizontal = 14.dp),
              )
              ExpandableAboutSection(
                icon = Icons.Default.Description,
                title = stringResource(R.string.about_terms_title),
                summary = stringResource(R.string.about_terms_summary),
                body = stringResource(R.string.about_terms_body),
                expanded = termsExpanded,
                onToggle = { termsExpanded = !termsExpanded },
                testTag = "about_terms_row",
              )
              HorizontalDivider(
                color = ext.subtleBorder,
                modifier = Modifier.padding(horizontal = 14.dp),
              )
              ExpandableAboutSection(
                icon = Icons.Default.Code,
                title = stringResource(R.string.about_oss_title),
                summary = stringResource(R.string.about_oss_summary),
                body = stringResource(R.string.about_oss_body),
                expanded = licensesExpanded,
                onToggle = { licensesExpanded = !licensesExpanded },
                testTag = "about_oss_row",
              )
            }
          }
        }
        item { AboutContactFeedbackCard() }
      }
    }
  }
}

@Composable
private fun AboutAppIdentityCard() {
  val ext = AppTheme.colors
  val badgeBrush = Brush.linearGradient(listOf(Indigo500, ElectricBlue600))

  HeroGradientCard {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(
        modifier =
          Modifier.size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(badgeBrush),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.Keyboard,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(30.dp),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = stringResource(R.string.about_app_name_label),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
          color = ext.cardSurface.copy(alpha = 0.72f),
          shape = CircleShape,
          border = BorderStroke(1.dp, ext.subtleBorder),
        ) {
          Text(
            text =
              stringResource(
                R.string.about_version_full,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
              ),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = MonoFontFamily,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }
    }

    Text(
      text = stringResource(R.string.about_keyboard_desc),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )

    HorizontalDivider(color = ext.subtleBorder)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      AboutCapabilityRow(icon = Icons.Default.Lock, text = stringResource(R.string.about_feature_offline))
      AboutCapabilityRow(icon = Icons.Default.Translate, text = stringResource(R.string.about_feature_ai))
      AboutCapabilityRow(icon = Icons.Default.Widgets, text = stringResource(R.string.about_feature_ux))
    }
  }
}

@Composable
private fun AboutCapabilityRow(icon: ImageVector, text: String) {
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp).padding(top = 2.dp),
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun AboutDeveloperCard() {
  PremiumCard {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.Person,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.about_developer_title),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = stringResource(R.string.about_author_name),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
    Text(
      text = stringResource(R.string.about_author_role),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ExpandableAboutSection(
  icon: ImageVector,
  title: String,
  summary: String,
  body: String,
  expanded: Boolean,
  onToggle: () -> Unit,
  testTag: String,
) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onToggle)
        .padding(horizontal = 12.dp, vertical = 12.dp)
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = if (expanded) 3 else 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    AnimatedVisibility(visible = expanded) {
      Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 46.dp, top = 8.dp, bottom = 4.dp),
      )
    }
  }
}

@Composable
private fun AboutContactFeedbackCard() {
  val context = LocalContext.current
  val ext = AppTheme.colors
  val appName = stringResource(R.string.app_name)
  var copiedNotice by rememberSaveable { mutableStateOf(false) }

  PremiumCard {
    SectionHeader(
      icon = Icons.Default.Feedback,
      title = stringResource(R.string.about_feedback_title),
      subtitle = stringResource(R.string.about_feedback_desc),
    )

    Surface(
      color = ext.cardSurfaceElevated,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, ext.cardBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          Icons.Default.Email,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.about_contact_email_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = AUTHOR_CONTACT_EMAIL,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Button(
        onClick = {
          val subject = Uri.encode("$appName Feedback (v${BuildConfig.VERSION_NAME})")
          val emailIntent =
            Intent(Intent.ACTION_SENDTO).apply {
              data = Uri.parse("mailto:$AUTHOR_CONTACT_EMAIL?subject=$subject")
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
          val launched = runCatching { context.startActivity(emailIntent) }.isSuccess
          if (!launched) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Author Email", AUTHOR_CONTACT_EMAIL))
            copiedNotice = true
          }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("about_send_feedback_btn"),
      ) {
        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.about_send_feedback), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      OutlinedButton(
        onClick = {
          val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
          cm?.setPrimaryClip(ClipData.newPlainText("Author Email", AUTHOR_CONTACT_EMAIL))
          copiedNotice = true
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("about_copy_email_btn"),
      ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.about_copy_email), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }

    if (copiedNotice) {
      StatusPill(
        text = stringResource(R.string.about_copied_email, AUTHOR_CONTACT_EMAIL),
        tone = StatusTone.SUCCESS,
      )
    }
  }
}
