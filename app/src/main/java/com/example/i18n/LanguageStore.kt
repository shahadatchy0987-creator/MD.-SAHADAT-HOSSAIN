package com.example.i18n

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "plusnet_settings")

enum class AppLang(val code: String, val label: String) {
  EN("en", "English"),
  BN("bn", "বাংলা"),
}

class LanguageStore(private val context: Context) {
  private val langKey = stringPreferencesKey("app_language")

  val languageFlow: Flow<AppLang> =
    context.settingsDataStore.data.map { prefs ->
      if (prefs[langKey] == AppLang.BN.code) AppLang.BN else AppLang.EN
    }

  suspend fun setLanguage(lang: AppLang) {
    context.settingsDataStore.edit { prefs -> prefs[langKey] = lang.code }
  }
}

object Formatters {
  private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
  private val monthFormat = SimpleDateFormat("MM-yyyy", Locale.US)
  private val amountFormat =
    DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).apply { isGroupingUsed = true }

  fun formatTaka(amount: Double): String {
    val prefix = if (amount < 0) "-৳ " else "৳ "
    return prefix + amountFormat.format(kotlin.math.abs(amount))
  }

  fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

  fun currentDateFormatted(): String = formatDate(System.currentTimeMillis())

  fun currentMonthFormatted(): String = monthFormat.format(Date(System.currentTimeMillis()))

  fun parseDateMillis(ddMmYyyy: String): Long? {
    return try {
      dateFormat.isLenient = false
      dateFormat.parse(ddMmYyyy)?.time
    } catch (_: Exception) {
      null
    }
  }
}
