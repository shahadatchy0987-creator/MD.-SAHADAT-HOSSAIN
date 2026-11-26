package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EmployeeSummary
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.Formatters
import com.example.model.EmployeeEntryType
import com.example.model.EmployeeLedgerEntry
import com.example.model.OfficeEmployee
import com.example.model.PaymentMethod
import com.example.ui.components.DatePickerDialogModal
import com.example.ui.components.EmptyStateBanner
import com.example.ui.components.KpiMetricCard
import com.example.ui.theme.DueAmber
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.MonoAmountStyle
import com.example.ui.theme.TransferIndigo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamScreen(
  lang: AppLang,
  isAdmin: Boolean,
  employeeSummaries: List<EmployeeSummary>,
  allEntries: List<EmployeeLedgerEntry>,
  onAddEmployee: (name: String, phone: String, designation: String, onDone: () -> Unit) -> Unit,
  onRecordEntry: (
    employee: OfficeEmployee,
    entryType: EmployeeEntryType,
    amount: Double,
    dateMillis: Long,
    dateFormatted: String,
    paymentMethod: PaymentMethod,
    ref: String,
    customerName: String,
    note: String,
    onDone: () -> Unit,
  ) -> Unit,
) {
  var dateFilter by remember { mutableStateOf("") }
  var showDatePicker by remember { mutableStateOf(false) }
  var showAddEmployee by remember { mutableStateOf(false) }
  var entryDialogData by remember { mutableStateOf<Pair<OfficeEmployee, EmployeeEntryType>?>(null) }

  val totalCollected = employeeSummaries.sumOf { it.totalCollected }
  val totalDeposited = employeeSummaries.sumOf { it.totalDeposited }
  val totalRemaining = totalCollected - totalDeposited

  val filteredEntries =
    remember(allEntries, dateFilter) {
      if (dateFilter.isBlank()) allEntries
      else allEntries.filter { it.dateFormatted == dateFilter }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = AppStrings.tr(lang, "Office Team Collection", "অফিস টিম কালেকশন"),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        if (isAdmin) {
          Button(
            onClick = { showAddEmployee = true },
            modifier = Modifier.testTag("add_employee_btn"),
          ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(AppStrings.tr(lang, "Member", "সদস্য"))
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        KpiMetricCard(
          title = AppStrings.tr(lang, "Collected", "মোট আদায়"),
          amount = totalCollected,
          icon = Icons.Default.Payments,
          accentColor = IncomeGreen,
          modifier = Modifier.weight(1f),
        )
        KpiMetricCard(
          title = AppStrings.tr(lang, "Deposited", "অফিসে জমা"),
          amount = totalDeposited,
          icon = Icons.Default.Savings,
          accentColor = TransferIndigo,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      KpiMetricCard(
        title = AppStrings.tr(lang, "Remaining Cash With Team", "টিমের হাতে অবশিষ্ট"),
        amount = totalRemaining,
        icon = Icons.Default.AccountBalanceWallet,
        accentColor = DueAmber,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (employeeSummaries.isEmpty()) {
      item {
        EmptyStateBanner(
          title = AppStrings.tr(lang, "No team members added", "কোনো টিম সদস্য যোগ করা হয়নি"),
          subtitle =
            AppStrings.tr(
              lang,
              "Add office field collectors to track how much each employee collects and deposits.",
              "প্রতিটি কর্মচারী কত আদায় করে এবং কত অফিসে জমা দেয় তা ট্র্যাক করতে সদস্য যোগ করুন।",
            ),
        )
      }
    } else {
      items(employeeSummaries, key = { it.employee.id }) { summary ->
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column {
                Text(
                  text = summary.employee.name,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    listOf(summary.employee.designation, summary.employee.phone)
                      .filter { it.isNotBlank() }
                      .joinToString(" • ")
                      .ifBlank { "Collector" },
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column {
                Text(
                  AppStrings.tr(lang, "Collected", "আদায়"),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  Formatters.formatTaka(summary.totalCollected),
                  style = MonoAmountStyle.copy(fontSize = 13.sp),
                  color = IncomeGreen,
                )
              }
              Column {
                Text(
                  AppStrings.tr(lang, "Deposited", "জমা"),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  Formatters.formatTaka(summary.totalDeposited),
                  style = MonoAmountStyle.copy(fontSize = 13.sp),
                  color = TransferIndigo,
                )
              }
              Column {
                Text(
                  AppStrings.tr(lang, "Remaining", "বাকি"),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  Formatters.formatTaka(summary.remainingMoney),
                  style = MonoAmountStyle.copy(fontSize = 13.sp),
                  color = DueAmber,
                )
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = { entryDialogData = summary.employee to EmployeeEntryType.COLLECTION },
                modifier = Modifier.weight(1f).height(44.dp),
              ) {
                Text(AppStrings.tr(lang, "+ Collection", "+ আদায়"), maxLines = 1)
              }
              Button(
                onClick = { entryDialogData = summary.employee to EmployeeEntryType.DEPOSIT },
                modifier = Modifier.weight(1f).height(44.dp),
              ) {
                Text(AppStrings.tr(lang, "Deposit", "অফিস জমা"), maxLines = 1)
              }
            }
          }
        }
      }
    }

    // Date-wise Collection & Deposit History
    item {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = AppStrings.tr(lang, "Date-wise Collection History", "তারিখ অনুযায়ী আদায় ও জমা"),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          TextButton(onClick = { showDatePicker = true }) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(dateFilter.ifBlank { AppStrings.tr(lang, "All Dates", "সব তারিখ") })
          }
          if (dateFilter.isNotBlank()) {
            IconButton(onClick = { dateFilter = "" }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear date")
            }
          }
        }
      }
    }

    if (filteredEntries.isEmpty()) {
      item {
        EmptyStateBanner(
          title = AppStrings.tr(lang, "No collection entries", "কোনো এন্ট্রি নেই"),
          subtitle = AppStrings.tr(lang, "Record collections or office deposits for any team member.", "কর্মচারীর আদায় বা জমা যোগ করুন।"),
        )
      }
    } else {
      items(filteredEntries, key = { it.id }) { entry ->
        val isCollection = entry.type == EmployeeEntryType.COLLECTION
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 1.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${entry.employeeName} • ${if (isCollection) AppStrings.tr(lang, "Collection", "আদায়") else AppStrings.tr(lang, "Deposit", "জমা")}",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text =
                  "${entry.dateFormatted} • ${entry.paymentMethod}" +
                    if (entry.customerName.isNotBlank()) " • ${entry.customerName}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = Formatters.formatTaka(entry.amount),
              style = MonoAmountStyle.copy(fontSize = 15.sp),
              color = if (isCollection) IncomeGreen else TransferIndigo,
            )
          }
        }
      }
    }
  }

  if (showDatePicker) {
    DatePickerDialogModal(
      initialMillis = Formatters.parseDateMillis(dateFilter) ?: System.currentTimeMillis(),
      onDismiss = { showDatePicker = false },
      onDateSelected = { _, formatted ->
        dateFilter = formatted
        showDatePicker = false
      },
    )
  }

  if (showAddEmployee) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("Field Collector") }
    AlertDialog(
      onDismissRequest = { showAddEmployee = false },
      title = { Text(AppStrings.tr(lang, "Add Team Member", "টিম সদস্য যোগ করুন")) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(AppStrings.tr(lang, "Employee Name", "কর্মচারীর নাম")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text(AppStrings.tr(lang, "Phone", "মোবাইল নম্বর")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = designation,
            onValueChange = { designation = it },
            label = { Text(AppStrings.tr(lang, "Designation", "পদবি")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          enabled = name.isNotBlank(),
          onClick = {
            onAddEmployee(name, phone, designation) { showAddEmployee = false }
          },
        ) {
          Text(AppStrings.save(lang))
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddEmployee = false }) { Text(AppStrings.cancel(lang)) }
      },
    )
  }

  entryDialogData?.let { (employee, type) ->
    var amountText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dateFormatted by remember { mutableStateOf(Formatters.currentDateFormatted()) }
    var showPicker by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var customerName by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { entryDialogData = null },
      title = {
        Text(
          if (type == EmployeeEntryType.COLLECTION)
            "${AppStrings.tr(lang, "Record Collection", "আদায় যোগ")}: ${employee.name}"
          else "${AppStrings.tr(lang, "Office Deposit", "অফিসে জমা")}: ${employee.name}"
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text(AppStrings.amountLabel(lang)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = dateFormatted,
            onValueChange = { dateFormatted = it },
            label = { Text(AppStrings.dateLabel(lang)) },
            trailingIcon = {
              IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
              }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PaymentMethod.entries.forEach { m ->
              FilterChip(
                selected = method == m,
                onClick = { method = m },
                label = {
                  Text(
                    when (m) {
                      PaymentMethod.CASH -> AppStrings.cash(lang)
                      PaymentMethod.BANK -> AppStrings.bank(lang)
                      PaymentMethod.MOBILE_BANKING -> AppStrings.mobileBanking(lang)
                    }
                  )
                },
              )
            }
          }
          if (type == EmployeeEntryType.COLLECTION) {
            OutlinedTextField(
              value = customerName,
              onValueChange = { customerName = it },
              label = { Text(AppStrings.tr(lang, "Customer Name", "গ্রাহকের নাম")) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
          OutlinedTextField(
            value = reference,
            onValueChange = { reference = it },
            label = { Text(AppStrings.referenceNumber(lang)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(AppStrings.tr(lang, "Note", "নোট")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        val parsed = amountText.toDoubleOrNull() ?: 0.0
        Button(
          enabled = parsed > 0.0,
          onClick = {
            val millis = Formatters.parseDateMillis(dateFormatted) ?: dateMillis
            onRecordEntry(
              employee,
              type,
              parsed,
              millis,
              dateFormatted,
              method,
              reference,
              customerName,
              note,
            ) {
              entryDialogData = null
            }
          },
        ) {
          Text(AppStrings.save(lang))
        }
      },
      dismissButton = {
        TextButton(onClick = { entryDialogData = null }) { Text(AppStrings.cancel(lang)) }
      },
    )

    if (showPicker) {
      DatePickerDialogModal(
        initialMillis = dateMillis,
        onDismiss = { showPicker = false },
        onDateSelected = { m, fmt ->
          dateMillis = m
          dateFormatted = fmt
          showPicker = false
        },
      )
    }
  }
}
