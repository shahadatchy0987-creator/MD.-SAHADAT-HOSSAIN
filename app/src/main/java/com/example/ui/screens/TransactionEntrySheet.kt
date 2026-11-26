package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.Formatters
import com.example.model.AccountCategories
import com.example.model.AccountScope
import com.example.model.PaymentMethod
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import com.example.ui.components.DatePickerDialogModal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEntrySheet(
  lang: AppLang,
  isAdmin: Boolean,
  initialType: TransactionType,
  editingRecord: TransactionRecord?,
  onCheckDuplicate: (
    scope: AccountScope,
    type: TransactionType,
    category: String,
    partyName: String,
    amount: Double,
    dateFormatted: String,
    referenceNumber: String,
    editingId: String?,
  ) -> Boolean,
  onSave: (
    scope: AccountScope,
    type: TransactionType,
    category: String,
    partyName: String,
    amount: Double,
    paymentMethod: PaymentMethod,
    toPaymentMethod: PaymentMethod?,
    referenceNumber: String,
    description: String,
    dateMillis: Long,
    dateFormatted: String,
  ) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var txType by remember {
    mutableStateOf(editingRecord?.txType ?: initialType)
  }
  var scope by remember {
    mutableStateOf(
      if (!isAdmin) AccountScope.ISP_BUSINESS
      else editingRecord?.scope ?: AccountScope.ISP_BUSINESS
    )
  }
  val categories = remember(scope, txType) { AccountCategories.categoriesFor(scope, txType) }
  var selectedCategory by remember(categories) {
    mutableStateOf(
      editingRecord?.category?.takeIf { it in categories } ?: categories.first()
    )
  }
  var categoryExpanded by remember { mutableStateOf(false) }

  var partyName by remember {
    mutableStateOf(
      editingRecord?.partyName
        ?: if (txType == TransactionType.TRANSFER) "Internal Transfer" else ""
    )
  }
  var amountText by remember {
    mutableStateOf(editingRecord?.amount?.toString() ?: "")
  }
  var paymentMethod by remember {
    mutableStateOf(editingRecord?.method ?: PaymentMethod.CASH)
  }
  var toPaymentMethod by remember {
    mutableStateOf(editingRecord?.targetMethod ?: PaymentMethod.BANK)
  }
  var referenceNumber by remember { mutableStateOf(editingRecord?.referenceNumber ?: "") }
  var description by remember { mutableStateOf(editingRecord?.description ?: "") }
  var dateMillis by remember {
    mutableLongStateOf(editingRecord?.dateMillis ?: System.currentTimeMillis())
  }
  var dateFormatted by remember {
    mutableStateOf(editingRecord?.dateFormatted ?: Formatters.currentDateFormatted())
  }
  var showDatePicker by remember { mutableStateOf(false) }
  var validationError by remember { mutableStateOf<String?>(null) }
  var showDuplicateConfirm by remember { mutableStateOf(false) }

  fun attemptSave(bypassDuplicateCheck: Boolean) {
    val parsedAmount = amountText.trim().toDoubleOrNull()
    val parsedDateMillis = Formatters.parseDateMillis(dateFormatted.trim()) ?: dateMillis
    val resolvedParty =
      if (txType == TransactionType.TRANSFER && partyName.isBlank()) "Cash / Bank Transfer"
      else partyName.trim()

    when {
      parsedAmount == null || parsedAmount <= 0.0 -> {
        validationError = AppStrings.tr(lang, "Enter a valid positive amount.", "সঠিক পরিমাণ লিখুন।")
        return
      }
      resolvedParty.isBlank() -> {
        validationError =
          AppStrings.tr(lang, "Please enter Received From / Paid To.", "নাম বা পক্ষ লিখুন।")
        return
      }
      txType == TransactionType.TRANSFER && paymentMethod == toPaymentMethod -> {
        validationError =
          AppStrings.tr(lang, "Source and destination accounts must be different.", "উৎস ও গন্তব্য অ্যাকাউন্ট ভিন্ন হতে হবে।")
        return
      }
    }

    if (!bypassDuplicateCheck &&
      onCheckDuplicate(
        scope,
        txType,
        selectedCategory,
        resolvedParty,
        parsedAmount,
        dateFormatted.trim(),
        referenceNumber.trim(),
        editingRecord?.id,
      )
    ) {
      showDuplicateConfirm = true
      return
    }

    onSave(
      scope,
      txType,
      selectedCategory,
      resolvedParty,
      parsedAmount,
      paymentMethod,
      if (txType == TransactionType.TRANSFER) toPaymentMethod else null,
      referenceNumber.trim(),
      description.trim(),
      parsedDateMillis,
      dateFormatted.trim(),
    )
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val title =
        when {
          editingRecord != null -> AppStrings.editTransaction(lang)
          txType == TransactionType.INCOME -> AppStrings.addIncome(lang)
          txType == TransactionType.EXPENSE -> AppStrings.addExpense(lang)
          else -> AppStrings.transfer(lang)
        }
      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )

      // Transaction Type Selector
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = txType == TransactionType.INCOME,
          onClick = { txType = TransactionType.INCOME },
          label = { Text(AppStrings.income(lang)) },
          modifier = Modifier.testTag("entry_type_income"),
        )
        FilterChip(
          selected = txType == TransactionType.EXPENSE,
          onClick = { txType = TransactionType.EXPENSE },
          label = { Text(AppStrings.expense(lang)) },
          modifier = Modifier.testTag("entry_type_expense"),
        )
        if (isAdmin) {
          FilterChip(
            selected = txType == TransactionType.TRANSFER,
            onClick = {
              txType = TransactionType.TRANSFER
              if (partyName.isBlank()) partyName = "Internal Transfer"
            },
            label = { Text(AppStrings.addTransfer(lang)) },
            modifier = Modifier.testTag("entry_type_transfer"),
          )
        }
      }

      // Account Scope Selector (Personal vs ISP Business)
      if (isAdmin) {
        Text(
          text = AppStrings.accountTypeLabel(lang),
          style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = scope == AccountScope.ISP_BUSINESS,
            onClick = { scope = AccountScope.ISP_BUSINESS },
            label = { Text(AppStrings.scopeIsp(lang)) },
            modifier = Modifier.testTag("entry_scope_isp"),
          )
          FilterChip(
            selected = scope == AccountScope.PERSONAL,
            onClick = { scope = AccountScope.PERSONAL },
            label = { Text(AppStrings.scopePersonal(lang)) },
            modifier = Modifier.testTag("entry_scope_personal"),
          )
        }
      }

      // Date Field (DD-MM-YYYY)
      OutlinedTextField(
        value = dateFormatted,
        onValueChange = { dateFormatted = it },
        label = { Text(AppStrings.dateLabel(lang)) },
        trailingIcon = {
          IconButton(onClick = { showDatePicker = true }) {
            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
          }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("entry_date_input"),
      )

      // Category Dropdown
      if (txType != TransactionType.TRANSFER) {
        ExposedDropdownMenuBox(
          expanded = categoryExpanded,
          onExpandedChange = { categoryExpanded = !categoryExpanded },
        ) {
          OutlinedTextField(
            value = AppStrings.categoryLabel(lang, selectedCategory),
            onValueChange = {},
            readOnly = true,
            label = { Text(AppStrings.categoryFieldLabel(lang)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
            modifier =
              Modifier.fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .testTag("entry_category_dropdown"),
          )
          ExposedDropdownMenu(
            expanded = categoryExpanded,
            onDismissRequest = { categoryExpanded = false },
          ) {
            categories.forEach { cat ->
              DropdownMenuItem(
                text = { Text(AppStrings.categoryLabel(lang, cat)) },
                onClick = {
                  selectedCategory = cat
                  categoryExpanded = false
                },
              )
            }
          }
        }
      }

      // Party Field
      OutlinedTextField(
        value = partyName,
        onValueChange = { partyName = it },
        label = {
          Text(
            when (txType) {
              TransactionType.INCOME -> AppStrings.receivedFrom(lang)
              TransactionType.EXPENSE -> AppStrings.paidTo(lang)
              TransactionType.TRANSFER -> AppStrings.description(lang)
            }
          )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("entry_party_input"),
      )

      // Amount (৳)
      OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = it },
        label = { Text(AppStrings.amountLabel(lang)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("entry_amount_input"),
      )

      // Payment Method
      Text(
        text =
          if (txType == TransactionType.TRANSFER) AppStrings.fromAccount(lang)
          else AppStrings.paymentMethodLabel(lang),
        style = MaterialTheme.typography.labelLarge,
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaymentMethod.entries.forEach { method ->
          FilterChip(
            selected = paymentMethod == method,
            onClick = { paymentMethod = method },
            label = {
              Text(
                when (method) {
                  PaymentMethod.CASH -> AppStrings.cash(lang)
                  PaymentMethod.BANK -> AppStrings.bank(lang)
                  PaymentMethod.MOBILE_BANKING -> AppStrings.mobileBanking(lang)
                }
              )
            },
          )
        }
      }

      if (txType == TransactionType.TRANSFER) {
        Text(text = AppStrings.toAccount(lang), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          PaymentMethod.entries.forEach { method ->
            FilterChip(
              selected = toPaymentMethod == method,
              onClick = { toPaymentMethod = method },
              label = {
                Text(
                  when (method) {
                    PaymentMethod.CASH -> AppStrings.cash(lang)
                    PaymentMethod.BANK -> AppStrings.bank(lang)
                    PaymentMethod.MOBILE_BANKING -> AppStrings.mobileBanking(lang)
                  }
                )
              },
            )
          }
        }
      }

      OutlinedTextField(
        value = referenceNumber,
        onValueChange = { referenceNumber = it },
        label = { Text(AppStrings.referenceNumber(lang)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("entry_ref_input"),
      )

      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text(AppStrings.description(lang)) },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth().testTag("entry_desc_input"),
      )

      if (!validationError.isNullOrBlank()) {
        Text(
          text = validationError!!,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.weight(1f).height(48.dp),
        ) {
          Text(AppStrings.cancel(lang))
        }
        Button(
          onClick = {
            validationError = null
            attemptSave(bypassDuplicateCheck = false)
          },
          modifier = Modifier.weight(1f).height(48.dp).testTag("entry_save_button"),
        ) {
          Text(AppStrings.save(lang))
        }
      }
    }
  }

  if (showDatePicker) {
    DatePickerDialogModal(
      initialMillis = dateMillis,
      onDismiss = { showDatePicker = false },
      onDateSelected = { millis, fmt ->
        dateMillis = millis
        dateFormatted = fmt
        showDatePicker = false
      },
    )
  }

  if (showDuplicateConfirm) {
    AlertDialog(
      onDismissRequest = { showDuplicateConfirm = false },
      title = { Text(AppStrings.duplicateWarningTitle(lang)) },
      text = { Text(AppStrings.duplicateWarningMsg(lang)) },
      confirmButton = {
        Button(
          onClick = {
            showDuplicateConfirm = false
            attemptSave(bypassDuplicateCheck = true)
          }
        ) {
          Text(AppStrings.saveAnyway(lang))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDuplicateConfirm = false }) {
          Text(AppStrings.cancel(lang))
        }
      },
    )
  }
}
