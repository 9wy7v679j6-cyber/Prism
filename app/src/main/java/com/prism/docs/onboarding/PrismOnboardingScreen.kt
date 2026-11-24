package com.prism.docs.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.prism.docs.core.data.model.StorageAccessLevel
import com.prism.docs.core.data.model.StorageAccessState
import com.prism.docs.core.ui.R as CoreUiR
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.PrismColors
import dev.chrisbanes.haze.hazeSource

@Composable
fun PrismOnboardingScreen(
  storageAccessState: StorageAccessState,
  onGrantAllFilesAccess: () -> Unit,
  onPickSafFolder: () -> Unit,
  onContinueToApp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = LocalHazeState.current
  val colorScheme = MaterialTheme.colorScheme
  val hasAnyAccess = storageAccessState.level != StorageAccessLevel.LIMITED_NONE

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      contentPadding = PaddingValues(top = 16.dp, bottom = 180.dp, start = 18.dp, end = 18.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        Modifier.fillMaxSize()
          .statusBarsPadding()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
          .testTag("onboarding_scroll_list"),
    ) {
      item {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .widthIn(max = 620.dp)
              .height(216.dp)
              .clip(RoundedCornerShape(30.dp))
        ) {
          Image(
            painter = painterResource(id = CoreUiR.drawable.img_prism_onboarding_hero),
            contentDescription = "Prism crystalline glass architectural illustration",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(
            modifier =
              Modifier.fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors =
                      listOf(
                        Color.Transparent,
                        Color(0xFF070B14).copy(alpha = 0.72f),
                      )
                  )
                )
          )
          Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "PRISM DOCUMENT STUDIO",
              style = MaterialTheme.typography.labelSmall,
              color = PrismColors.IceCyan,
            )
            Text(
              text = "Every document.\nPrivate & instant.",
              style = MaterialTheme.typography.headlineLarge,
              color = Color.White,
            )
          }
        }
      }

      item {
        Column(
          modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OnboardingPillarCard(
            icon = Icons.Rounded.Lock,
            accent = PrismColors.EmeraldSheet,
            title = "100% On-Device Privacy",
            body =
              "No cloud uploads, no tracking, and no network permission. Your files, OCR text, and PDF edits never leave your Android device.",
          )
          OnboardingPillarCard(
            icon = Icons.Rounded.PictureAsPdf,
            accent = PrismColors.CoralPdf,
            title = "Universal Viewer & PDF Toolkit",
            body =
              "Open PDFs, Office documents, images, code, archives, and media — plus merge, split, compress, sign, scan, and OCR completely offline.",
          )
          OnboardingPillarCard(
            icon = Icons.Rounded.Shield,
            accent = PrismColors.Sapphire,
            title = "Why Prism Asks for Storage Access",
            body =
              "To display every document across Downloads, Documents, and SD storage in one unified library, Prism requests All Files Access. If you prefer, choose individual folders via Android's system picker instead.",
          )
        }
      }

      if (hasAnyAccess) {
        item {
          GlassSurface(
            cornerRadius = 22.dp,
            elevation = GlassElevation.SUBTLE,
            accentTint = PrismColors.EmeraldSheet,
            modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = PrismColors.EmeraldSheet,
              )
              Column {
                Text(
                  text =
                    if (storageAccessState.canBrowseFullDevice) {
                      "All Files Access Granted"
                    } else {
                      "${storageAccessState.safFolders.size} SAF Folder(s) Connected"
                    },
                  style = MaterialTheme.typography.titleMedium,
                  color = colorScheme.onSurface,
                )
                Text(
                  text = "You're ready to open your Prism workspace.",
                  style = MaterialTheme.typography.bodySmall,
                  color = colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }

    GlassSurface(
      cornerRadius = 30.dp,
      elevation = GlassElevation.OVERLAY,
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .widthIn(max = 620.dp)
          .navigationBarsPadding()
          .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (hasAnyAccess) {
          Button(
            onClick = onContinueToApp,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_enter_app_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("Enter Prism")
          }
        } else {
          Button(
            onClick = onGrantAllFilesAccess,
            modifier =
              Modifier.fillMaxWidth()
                .height(52.dp)
                .testTag("onboarding_grant_all_files_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Shield,
              contentDescription = null,
              modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("Grant All Files Access")
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onPickSafFolder,
              modifier =
                Modifier.weight(1f)
                  .height(48.dp)
                  .testTag("onboarding_choose_saf_folder_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.size(6.dp))
              Text("Pick Folder")
            }
            TextButton(
              onClick = onContinueToApp,
              modifier = Modifier.height(48.dp).testTag("onboarding_skip_limited_button"),
            ) {
              Text("Not now")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OnboardingPillarCard(
  icon: ImageVector,
  accent: Color,
  title: String,
  body: String,
) {
  val colorScheme = MaterialTheme.colorScheme
  GlassSurface(
    cornerRadius = 22.dp,
    elevation = GlassElevation.SUBTLE,
    accentTint = accent,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Box(
        modifier =
          Modifier.size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent)
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = colorScheme.onSurface,
        )
        Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
