package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountSummary
import com.example.data.MonthlyChartPoint
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.Formatters
import com.example.model.DashboardScopeFilter
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import com.example.model.UserProfile
import com.example.ui.components.DatePickerDialogModal
import com.example.ui.components.EmptyStateBanner
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.MonthlyIncomeExpenseChart
import com.example.ui.components.TransactionRowCard
import com.example.ui.theme.DueAmber
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald900
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.Mint300
import com.example.ui.theme.MonoAmountStyle
import com.example.ui.theme.TransferIndigo

@Composable
fun DashboardScreen(
  lang: AppLang,
  profile: UserProfile?,
  isAdmin: Boolean,
  scopeFilter: DashboardScopeFilter,
  summary: AccountSummary,
  customerCollection: Double,
  customerDue: Double,
  selectedDate: String,
  chartPoints: List<MonthlyChartPoint>,
  recentTransactions: List<TransactionRecord>,
  onScopeChange: (DashboardScopeFilter) -> Unit,
  onSelectedDateChange: (String) -> Unit,
  onLanguageToggle: () -> Unit,
  onSignOut: () -> Unit,
  onQuickAdd: (TransactionType) -> Unit,
  onTransactionClick: (TransactionRecord) -> Unit,
  onViewAllTransactions: () -> Unit,
) {
  var showDatePicker by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = AppStrings.appTitle(lang),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = profile?.displayName ?: "",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
              shape = RoundedCornerShape(50),
              color =
                if (isAdmin) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.tertiaryContainer,
            ) {
              Text(
                text =
                  if (isAdmin) AppStrings.roleAdmin(lang)
                  else AppStrings.roleEmployee(lang),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color =
                  if (isAdmin) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          FilledTonalButton(
            onClick = onLanguageToggle,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.testTag("dashboard_lang_toggle"),
          ) {
            Icon(Icons.Default.Language, contentDescription = "Language", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (lang == AppLang.EN) "বাংলা" else "EN")
          }
          IconButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("sign_out_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Logout,
              contentDescription = "Sign out",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    // Account Scope Selector (Personal & ISP strictly separated)
    if (isAdmin) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DashboardScopeFilter.entries.forEach { scope ->
            val label =
              when (scope) {
                DashboardScopeFilter.COMBINED -> AppStrings.scopeAll(lang)
                DashboardScopeFilter.PERSONAL -> AppStrings.scopePersonal(lang)
                DashboardScopeFilter.ISP_BUSINESS -> AppStrings.scopeIsp(lang)
              }
            FilterChip(
              selected = scopeFilter == scope,
              onClick = { onScopeChange(scope) },
              label = { Text(label) },
              modifier = Modifier.testTag("scope_chip_${scope.name}"),
            )
          }
        }
      }
    }

    // Featured Net Profit & Cash/Bank Hero Card
    item {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Emerald900, Emerald700)))
            .padding(18.dp)
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = AppStrings.netProfit(lang),
              style = MaterialTheme.typography.titleMedium,
              color = Mint300,
            )
            Surface(
              shape = RoundedCornerShape(50),
              color = Color.White.copy(alpha = 0.14f),
            ) {
              Text(
                text =
                  when {
                    !isAdmin -> AppStrings.scopeIsp(lang)
                    scopeFilter == DashboardScopeFilter.PERSONAL -> AppStrings.scopePersonal(lang)
                    scopeFilter == DashboardScopeFilter.ISP_BUSINESS -> AppStrings.scopeIsp(lang)
                    else -> AppStrings.scopeAll(lang)
                  },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = Formatters.formatTaka(summary.netProfit),
            style = MonoAmountStyle.copy(fontSize = 28.sp),
            color = Color.White,
          )
          Spacer(modifier = Modifier.height(14.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            HeroMiniStat(
              label = AppStrings.cashBalance(lang),
              value = Formatters.formatTaka(summary.cashBalance),
              modifier = Modifier.weight(1f),
            )
            HeroMiniStat(
              label = AppStrings.bankBalance(lang),
              value = Formatters.formatTaka(summary.bankBalance),
              modifier = Modifier.weight(1f),
            )
            HeroMiniStat(
              label = AppStrings.mobileBalance(lang),
              value = Formatters.formatTaka(summary.mobileBalance),
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }

    // Quick Action Buttons (+ Income, - Expense, Transfer)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = { onQuickAdd(TransactionType.INCOME) },
          colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
          modifier = Modifier.weight(1f).height(48.dp).testTag("quick_add_income"),
          contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(AppStrings.income(lang), maxLines = 1)
        }
        Button(
          onClick = { onQuickAdd(TransactionType.EXPENSE) },
          colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral),
          modifier = Modifier.weight(1f).height(48.dp).testTag("quick_add_expense"),
          contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
          Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(AppStrings.expense(lang), maxLines = 1)
        }
        if (isAdmin) {
          OutlinedButton(
            onClick = { onQuickAdd(TransactionType.TRANSFER) },
            modifier = Modifier.weight(1f).height(48.dp).testTag("quick_add_transfer"),
            contentPadding = PaddingValues(horizontal = 8.dp),
          ) {
            Icon(
              Icons.AutoMirrored.Filled.CompareArrows,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(AppStrings.addTransfer(lang), maxLines = 1)
          }
        }
      }
    }

    // 2-Column KPI Cards Grid
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          KpiMetricCard(
            title = AppStrings.totalIncome(lang),
            amount = summary.totalIncome,
            icon = Icons.Default.ArrowDownward,
            accentColor = IncomeGreen,
            modifier = Modifier.weight(1f),
          )
          KpiMetricCard(
            title = AppStrings.totalExpenses(lang),
            amount = summary.totalExpense,
            icon = Icons.Default.ArrowUpward,
            accentColor = ExpenseCoral,
            modifier = Modifier.weight(1f),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          KpiMetricCard(
            title = AppStrings.cashBalance(lang),
            amount = summary.cashBalance,
            icon = Icons.Default.Wallet,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
          )
          KpiMetricCard(
            title = AppStrings.bankBalance(lang),
            amount = summary.bankBalance,
            icon = Icons.Default.AccountBalance,
            accentColor = TransferIndigo,
            subtitle = "${AppStrings.mobileBalance(lang)}: ${Formatters.formatTaka(summary.mobileBalance)}",
            modifier = Modifier.weight(1f),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          KpiMetricCard(
            title = AppStrings.totalCustomerCollection(lang),
            amount = customerCollection,
            icon = Icons.Default.Payments,
            accentColor = IncomeGreen,
            modifier = Modifier.weight(1f),
          )
          KpiMetricCard(
            title = AppStrings.totalCustomerDue(lang),
            amount = customerDue,
            icon = Icons.Default.Schedule,
            accentColor = DueAmber,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    // Daily Opening and Closing Balance Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = AppStrings.dailyBalanceSummary(lang),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = { showDatePicker = true }) {
              Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date", modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(selectedDate)
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
              modifier = Modifier.weight(1f),
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = AppStrings.openingBalance(lang),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = Formatters.formatTaka(summary.dailyOpeningBalance),
                  style = MonoAmountStyle.copy(fontSize = 14.sp),
                )
              }
            }
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
              modifier = Modifier.weight(1f),
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = AppStrings.closingBalance(lang),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = Formatters.formatTaka(summary.dailyClosingBalance),
                  style = MonoAmountStyle.copy(fontSize = 14.sp),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    }

    // Monthly Income vs Expense Chart
    item {
      MonthlyIncomeExpenseChart(data = chartPoints, lang = lang)
    }

    // Recent Transactions Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = AppStrings.recentTransactions(lang),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onViewAllTransactions) {
          Text(AppStrings.tr(lang, "View All", "সব দেখুন"))
        }
      }
    }

    if (recentTransactions.isEmpty()) {
      item {
        EmptyStateBanner(
          title = AppStrings.tr(lang, "No transactions yet", "এখনও কোনো লেনদেন নেই"),
          subtitle =
            AppStrings.tr(
              lang,
              "Tap + Income or - Expense above to record your first entry.",
              "প্রথম হিসাব যোগ করতে উপরের + আয় বা - ব্যয় বোতামে ট্যাপ করুন।",
            ),
        )
      }
    } else {
      items(recentTransactions.take(6), key = { "${it.scope.name}_${it.id}" }) { tx ->
        TransactionRowCard(
          tx = tx,
          lang = lang,
          onClick = { onTransactionClick(tx) },
        )
      }
    }
  }

  if (showDatePicker) {
    DatePickerDialogModal(
      initialMillis = Formatters.parseDateMillis(selectedDate) ?: System.currentTimeMillis(),
      onDismiss = { showDatePicker = false },
      onDateSelected = { _, formatted ->
        onSelectedDateChange(formatted)
        showDatePicker = false
      },
    )
  }
}

@Composable
private fun HeroMiniStat(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color.White.copy(alpha = 0.12f),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = Mint300,
        maxLines = 1,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = value,
        style = MonoAmountStyle.copy(fontSize = 12.sp),
        color = Color.White,
        maxLines = 1,
      )
    }
  }
}
