package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.AuthManager
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald900

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
  lang: AppLang,
  onLanguageChange: (AppLang) -> Unit,
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    AuthManager.attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(Emerald900, Emerald800, MaterialTheme.colorScheme.background)
          )
        )
        .safeDrawingPadding()
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 560.dp)
          .align(Alignment.Center)
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // Top language toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        Surface(
          shape = RoundedCornerShape(50),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Language,
              contentDescription = "Language",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            FilterChip(
              selected = lang == AppLang.EN,
              onClick = { onLanguageChange(AppLang.EN) },
              label = { Text("EN") },
              modifier = Modifier.testTag("auth_lang_en"),
            )
            FilterChip(
              selected = lang == AppLang.BN,
              onClick = { onLanguageChange(AppLang.BN) },
              label = { Text("বাংলা") },
              modifier = Modifier.testTag("auth_lang_bn"),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Hero illustration
      Image(
        painter = painterResource(id = R.drawable.img_auth_hero),
        contentDescription = "Plusnet Accounts illustration",
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.fillMaxWidth()
            .height(210.dp)
            .clip(MaterialTheme.shapes.large),
      )

      Spacer(modifier = Modifier.height(20.dp))

      Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(22.dp)) {
          Text(
            text = AppStrings.appTitle(lang),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text =
              AppStrings.tr(
                lang,
                "Personal finance & ISP business accounting with real-time cloud sync, customer billing, and team collections.",
                "ব্যক্তিগত ও আইএসপি ব্যবসার সম্পূর্ণ হিসাব ব্যবস্থাপনা, গ্রাহক বিলিং, টিম কালেকশন এবং ক্লাউড ব্যাকআপ।",
              ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.height(16.dp))

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FeaturePill(
              icon = Icons.Default.AccountBalance,
              text = AppStrings.tr(lang, "Personal & ISP Ledgers", "ব্যক্তিগত ও আইএসপি খাতা"),
            )
            FeaturePill(
              icon = Icons.Default.CloudDone,
              text = AppStrings.tr(lang, "Real-time Sync", "রিয়েল-টাইম সিঙ্ক"),
            )
            FeaturePill(
              icon = Icons.Default.Security,
              text = AppStrings.tr(lang, "Admin & Employee RBAC", "অ্যাডমিন ও কর্মচারী রোল"),
            )
          }

          if (!errorMsg.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = errorMsg!!,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(20.dp))

          Button(
            onClick = {
              errorMsg = null
              isLoading = true
              AuthManager.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isLoading = false
                  errorMsg = msg
                },
                scope = scope,
                onAuthCancelled = { isLoading = false },
              )
            },
            enabled = !isLoading,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            shape = MaterialTheme.shapes.medium,
            modifier =
              Modifier.fillMaxWidth()
                .height(52.dp)
                .testTag("google_sign_in_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp,
              )
            } else {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Login,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = AppStrings.tr(lang, "Sign in with Google", "গুগল দিয়ে সাইন ইন করুন"),
                style = MaterialTheme.typography.titleMedium,
              )
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Composable
private fun FeaturePill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
) {
  Surface(
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(15.dp),
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
  }
}
