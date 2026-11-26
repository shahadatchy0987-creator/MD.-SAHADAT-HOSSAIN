package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.Formatters
import com.example.model.AccountCategories
import com.example.model.AccountScope
import com.example.model.AuditLog
import com.example.model.DashboardScopeFilter
import com.example.model.PaymentMethod
import com.example.model.SearchHistoryTerm
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import com.example.ui.components.DatePickerDialogModal
import com.example.ui.components.EmptyStateBanner
import com.example.ui.components.TransactionRowCard
import com.example.ui.theme.MonoAmountStyle
import com.example.viewmodel.LogFilterState

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
  lang: AppLang,
  isAdmin: Boolean,
  filter: LogFilterState,
  transactions: List<TransactionRecord>,
  frequentTerms: List<SearchHistoryTerm>,
  recentTerms: List<SearchHistoryTerm>,
  auditLogs: List<AuditLog>,
  onQueryChange: (String) -> Unit,
  onSubmitSearch: (String) -> Unit,
  onDeleteSearchTerm: (String) -> Unit,
  onClearSearchHistory: () -> Unit,
  onUpdateFilter: ((LogFilterState) -> LogFilterState) -> Unit,
  onAddTransaction: () -> Unit,
  onEditTransaction: (TransactionRecord) -> Unit,
  onDeleteTransaction: (TransactionRecord) -> Unit,
) {
  val keyboard = LocalSoftwareKeyboardController.current
  var showDatePicker by remember { mutableStateOf(false) }
  var categoryExpanded by remember { mutableStateOf(false) }
  var selectedTx by remember { mutableStateOf<TransactionRecord?>(null) }
  var pendingDeleteTx by remember { mutableStateOf<TransactionRecord?>(null) }

  val allCategories = remember {
    (AccountCategories.personalIncome +
      AccountCategories.personalExpense +
      AccountCategories.ispIncome +
      AccountCategories.ispExpense)
      .distinct()
  }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = onAddTransaction,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("logs_fab_add"),
      ) {
        Icon(Icons.Default.Add, contentDescription = AppStrings.addIncome(lang))
      }
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 88.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text(
          text = AppStrings.tr(lang, "Transaction Logs", "লেনদেনের তালিকা"),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
        )
      }

      // Search Input with IME Search recording
      item {
        OutlinedTextField(
          value = filter.query,
          onValueChange = onQueryChange,
          placeholder = { Text(AppStrings.searchTransactions(lang)) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
          trailingIcon = {
            Row {
              if (filter.query.isNotBlank()) {
                IconButton(
                  onClick = { onSubmitSearch(filter.query); keyboard?.hide() },
                  modifier = Modifier.testTag("search_submit_btn"),
                ) {
                  Icon(Icons.Default.Search, contentDescription = "Submit search")
                }
                IconButton(
                  onClick = { onQueryChange("") },
                  modifier = Modifier.testTag("search_clear_btn"),
                ) {
                  Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
              }
            }
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions =
            KeyboardActions(
              onSearch = {
                onSubmitSearch(filter.query)
                keyboard?.hide()
              }
            ),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().testTag("tx_search_input"),
        )
      }

      // Search History Section (Most Frequent & Recent Search Terms)
      if (frequentTerms.isNotEmpty() || recentTerms.isNotEmpty()) {
        item {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().testTag("search_history_panel"),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = AppStrings.frequentSearches(lang),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                  )
                }
                TextButton(
                  onClick = onClearSearchHistory,
                  modifier = Modifier.testTag("clear_search_history_btn"),
                ) {
                  Text(AppStrings.clearHistory(lang))
                }
              }

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                frequentTerms.forEach { termItem ->
                  AssistChip(
                    onClick = {
                      onSubmitSearch(termItem.term)
                      keyboard?.hide()
                    },
                    label = {
                      Text("${termItem.term} (${termItem.searchCount})")
                    },
                    leadingIcon = {
                      Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                      )
                    },
                    modifier = Modifier.testTag("frequent_term_${termItem.id}"),
                  )
                }
              }

              if (recentTerms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = AppStrings.recentSearches(lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  recentTerms.forEach { termItem ->
                    InputChip(
                      selected = filter.query.equals(termItem.term, ignoreCase = true),
                      onClick = {
                        onSubmitSearch(termItem.term)
                        keyboard?.hide()
                      },
                      label = { Text(termItem.term) },
                      trailingIcon = {
                        IconButton(
                          onClick = { onDeleteSearchTerm(termItem.id) },
                          modifier = Modifier.size(18.dp),
                        ) {
                          Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove search term",
                            modifier = Modifier.size(14.dp),
                          )
                        }
                      },
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Filter Strip (Account Scope, Type, Method, Date, Category)
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (isAdmin) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              DashboardScopeFilter.entries.forEach { sc ->
                FilterChip(
                  selected = filter.scopeFilter == sc,
                  onClick = { onUpdateFilter { it.copy(scopeFilter = sc) } },
                  label = {
                    Text(
                      when (sc) {
                        DashboardScopeFilter.COMBINED -> AppStrings.scopeAll(lang)
                        DashboardScopeFilter.PERSONAL -> AppStrings.scopePersonal(lang)
                        DashboardScopeFilter.ISP_BUSINESS -> AppStrings.scopeIsp(lang)
                      }
                    )
                  },
                )
              }
            }
          }

          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = filter.txType == null,
              onClick = { onUpdateFilter { it.copy(txType = null) } },
              label = { Text(AppStrings.tr(lang, "All Types", "সব ধরন")) },
            )
            FilterChip(
              selected = filter.txType == TransactionType.INCOME,
              onClick = { onUpdateFilter { it.copy(txType = TransactionType.INCOME) } },
              label = { Text(AppStrings.income(lang)) },
            )
            FilterChip(
              selected = filter.txType == TransactionType.EXPENSE,
              onClick = { onUpdateFilter { it.copy(txType = TransactionType.EXPENSE) } },
              label = { Text(AppStrings.expense(lang)) },
            )
            FilterChip(
              selected = filter.txType == TransactionType.TRANSFER,
              onClick = { onUpdateFilter { it.copy(txType = TransactionType.TRANSFER) } },
              label = { Text(AppStrings.addTransfer(lang)) },
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedButton(
              onClick = { showDatePicker = true },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                if (filter.dateFilter.isBlank()) AppStrings.tr(lang, "Filter Date", "তারিখ ফিল্টার")
                else filter.dateFilter
              )
            }
            AnimatedVisibility(visible = filter.dateFilter.isNotBlank()) {
              IconButton(onClick = { onUpdateFilter { it.copy(dateFilter = "") } }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear date filter")
              }
            }

            ExposedDropdownMenuBox(
              expanded = categoryExpanded,
              onExpandedChange = { categoryExpanded = !categoryExpanded },
              modifier = Modifier.weight(1f),
            ) {
              OutlinedTextField(
                value =
                  filter.category?.let { AppStrings.categoryLabel(lang, it) }
                    ?: AppStrings.tr(lang, "All Categories", "সব বিভাগ"),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                  ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
              )
              ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false },
              ) {
                DropdownMenuItem(
                  text = { Text(AppStrings.tr(lang, "All Categories", "সব বিভাগ")) },
                  onClick = {
                    onUpdateFilter { it.copy(category = null) }
                    categoryExpanded = false
                  },
                )
                allCategories.forEach { cat ->
                  DropdownMenuItem(
                    text = { Text(AppStrings.categoryLabel(lang, cat)) },
                    onClick = {
                      onUpdateFilter { it.copy(category = cat) }
                      categoryExpanded = false
                    },
                  )
                }
              }
            }
          }
        }
      }

      if (transactions.isEmpty()) {
        item {
          EmptyStateBanner(
            title = AppStrings.tr(lang, "No matching transactions", "কোনো লেনদেন পাওয়া যায়নি"),
            subtitle =
              AppStrings.tr(
                lang,
                "Try clearing filters or tap + to add a new transaction.",
                "ফিল্টার পরিবর্তন করুন অথবা + বোতামে ট্যাপ করে নতুন লেনদেন যোগ করুন।",
              ),
          )
        }
      } else {
        items(transactions, key = { "${it.scope.name}_${it.id}" }) { tx ->
          TransactionRowCard(
            tx = tx,
            lang = lang,
            onClick = {
              if (filter.query.trim().length >= 2) {
                onSubmitSearch(filter.query)
              }
              selectedTx = tx
            },
          )
        }
      }
    }
  }

  if (showDatePicker) {
    DatePickerDialogModal(
      initialMillis =
        Formatters.parseDateMillis(filter.dateFilter) ?: System.currentTimeMillis(),
      onDismiss = { showDatePicker = false },
      onDateSelected = { _, formatted ->
        onUpdateFilter { it.copy(dateFilter = formatted) }
        showDatePicker = false
      },
    )
  }

  selectedTx?.let { tx ->
    val relatedLogs = remember(tx.id, auditLogs) { auditLogs.filter { it.entityId == tx.id } }
    ModalBottomSheet(onDismissRequest = { selectedTx = null }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = tx.partyName,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = Formatters.formatTaka(tx.amount),
          style = MonoAmountStyle.copy(fontSize = 24.sp),
          color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider()
        DetailLine("ID", tx.id)
        DetailLine(
          AppStrings.accountTypeLabel(lang),
          if (tx.scope == AccountScope.PERSONAL) AppStrings.scopePersonal(lang)
          else AppStrings.scopeIsp(lang),
        )
        DetailLine(AppStrings.categoryFieldLabel(lang), AppStrings.categoryLabel(lang, tx.category))
        DetailLine(AppStrings.dateLabel(lang), tx.dateFormatted)
        DetailLine(
          AppStrings.paymentMethodLabel(lang),
          when (tx.method) {
            PaymentMethod.CASH -> AppStrings.cash(lang)
            PaymentMethod.BANK -> AppStrings.bank(lang)
            PaymentMethod.MOBILE_BANKING -> AppStrings.mobileBanking(lang)
          } + (tx.targetMethod?.let { " -> ${it.name}" } ?: ""),
        )
        if (tx.referenceNumber.isNotBlank()) {
          DetailLine(AppStrings.referenceNumber(lang), tx.referenceNumber)
        }
        if (tx.description.isNotBlank()) {
          DetailLine(AppStrings.description(lang), tx.description)
        }
        DetailLine(AppStrings.tr(lang, "Recorded By", "লিপিবদ্ধ করেছেন"), tx.createdByName)

        if (relatedLogs.isNotEmpty()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = AppStrings.tr(lang, "Audit History", "অডিট ইতিহাস"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          relatedLogs.take(4).forEach { log ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(8.dp)) {
                Text(
                  text = "${log.action} • ${log.performedByName}",
                  style = MaterialTheme.typography.labelLarge,
                )
                Text(text = log.summary, style = MaterialTheme.typography.bodyMedium)
                if (log.previousValues.isNotBlank()) {
                  Text(
                    text = "${log.previousValues} -> ${log.newValues}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = {
              val target = tx
              selectedTx = null
              onEditTransaction(target)
            },
            modifier = Modifier.weight(1f).height(48.dp).testTag("detail_edit_tx"),
          ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(AppStrings.edit(lang))
          }
          if (isAdmin) {
            Button(
              onClick = {
                pendingDeleteTx = tx
                selectedTx = null
              },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.weight(1f).height(48.dp).testTag("detail_delete_tx"),
            ) {
              Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(AppStrings.delete(lang))
            }
          }
        }
      }
    }
  }

  pendingDeleteTx?.let { tx ->
    AlertDialog(
      onDismissRequest = { pendingDeleteTx = null },
      title = { Text(AppStrings.confirmDeleteTitle(lang)) },
      text = { Text(AppStrings.confirmDeleteMsg(lang)) },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            onDeleteTransaction(tx)
            pendingDeleteTx = null
          },
          modifier = Modifier.testTag("confirm_delete_button"),
        ) {
          Text(AppStrings.delete(lang))
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteTx = null }) {
          Text(AppStrings.cancel(lang))
        }
      },
    )
  }
}

@Composable
private fun DetailLine(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
    )
  }
}
