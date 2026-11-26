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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.AccountingCalculator
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.Formatters
import com.example.model.BillPayment
import com.example.model.BillStatus
import com.example.model.CustomerBill
import com.example.model.IspCustomer
import com.example.model.OfficeEmployee
import com.example.model.PaymentMethod
import com.example.ui.components.DatePickerDialogModal
import com.example.ui.components.EmptyStateBanner
import com.example.ui.components.KpiMetricCard
import com.example.ui.theme.DueAmber
import com.example.ui.theme.DueAmberBg
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenBg
import com.example.ui.theme.MonoAmountStyle

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
  lang: AppLang,
  customers: List<IspCustomer>,
  bills: List<CustomerBill>,
  payments: List<BillPayment>,
  employees: List<OfficeEmployee>,
  totalCollection: Double,
  totalDue: Double,
  onSaveCustomer: (
    code: String,
    name: String,
    phone: String,
    address: String,
    pkg: String,
    fee: Double,
    onDone: () -> Unit,
  ) -> Unit,
  onCreateBill: (
    customer: IspCustomer,
    month: String,
    monthlyAmount: Double,
    previousDue: Double,
    onDone: () -> Unit,
  ) -> Unit,
  onRecordPayment: (
    bill: CustomerBill,
    paidNow: Double,
    dateMillis: Long,
    dateFormatted: String,
    method: PaymentMethod,
    employee: OfficeEmployee?,
    ref: String,
    note: String,
    onDone: () -> Unit,
  ) -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var searchText by remember { mutableStateOf("") }
  var showAddCustomer by remember { mutableStateOf(false) }
  var showCreateBillFor by remember { mutableStateOf<IspCustomer?>(null) }
  var showCreateBillGeneric by remember { mutableStateOf(false) }
  var showPaymentForBill by remember { mutableStateOf<CustomerBill?>(null) }

  val filteredBills =
    remember(bills, searchText) {
      val q = searchText.trim().lowercase()
      if (q.isEmpty()) bills
      else
        bills.filter {
          it.customerName.lowercase().contains(q) ||
            it.customerCode.lowercase().contains(q) ||
            it.billingMonth.lowercase().contains(q)
        }
    }

  val filteredCustomers =
    remember(customers, searchText) {
      val q = searchText.trim().lowercase()
      if (q.isEmpty()) customers
      else
        customers.filter {
          it.name.lowercase().contains(q) ||
            it.customerCode.lowercase().contains(q) ||
            it.phone.lowercase().contains(q)
        }
    }

  val filteredPayments =
    remember(payments, searchText) {
      val q = searchText.trim().lowercase()
      if (q.isEmpty()) payments
      else
        payments.filter {
          it.customerName.lowercase().contains(q) ||
            it.customerCode.lowercase().contains(q) ||
            it.billingMonth.lowercase().contains(q)
        }
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
          text = AppStrings.tr(lang, "ISP Customer Billing", "গ্রাহক বিলিং ও আদায়"),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(
            onClick = { showAddCustomer = true },
            modifier = Modifier.testTag("add_customer_btn"),
          ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(AppStrings.tr(lang, "Customer", "গ্রাহক"))
          }
          Button(
            onClick = { showCreateBillGeneric = true },
            enabled = customers.isNotEmpty(),
            modifier = Modifier.testTag("create_bill_btn"),
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(AppStrings.tr(lang, "Bill", "বিল"))
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        KpiMetricCard(
          title = AppStrings.totalCustomerCollection(lang),
          amount = totalCollection,
          icon = Icons.Default.Payments,
          accentColor = IncomeGreen,
          modifier = Modifier.weight(1f),
        )
        KpiMetricCard(
          title = AppStrings.totalCustomerDue(lang),
          amount = totalDue,
          icon = Icons.Default.Schedule,
          accentColor = DueAmber,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      OutlinedTextField(
        value = searchText,
        onValueChange = { searchText = it },
        label = {
          Text(AppStrings.tr(lang, "Filter by customer name, ID, or month", "গ্রাহকের নাম, আইডি বা মাস"))
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    item {
      TabRow(selectedTabIndex = selectedTab) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text(AppStrings.tr(lang, "Bills (${filteredBills.size})", "বিল (${filteredBills.size})")) },
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text(AppStrings.tr(lang, "Customers (${filteredCustomers.size})", "গ্রাহক (${filteredCustomers.size})")) },
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text(AppStrings.tr(lang, "History (${filteredPayments.size})", "ইতিহাস (${filteredPayments.size})")) },
        )
      }
    }

    when (selectedTab) {
      0 -> {
        if (filteredBills.isEmpty()) {
          item {
            EmptyStateBanner(
              title = AppStrings.tr(lang, "No bills generated yet", "এখনও কোনো বিল তৈরি হয়নি"),
              subtitle =
                AppStrings.tr(
                  lang,
                  "Add an ISP customer first, then create their monthly bill to record partial or full payments.",
                  "প্রথমে একজন গ্রাহক যোগ করুন, তারপর মাসিক বিল তৈরি করে আংশিক বা সম্পূর্ণ পেমেন্ট সংগ্রহ করুন।",
                ),
            )
          }
        } else {
          items(filteredBills, key = { it.id }) { bill ->
            BillCard(
              bill = bill,
              lang = lang,
              onCollectClick = { showPaymentForBill = bill },
            )
          }
        }
      }

      1 -> {
        if (filteredCustomers.isEmpty()) {
          item {
            EmptyStateBanner(
              title = AppStrings.tr(lang, "No ISP customers yet", "কোনো আইএসপি গ্রাহক নেই"),
              subtitle =
                AppStrings.tr(
                  lang,
                  "Tap + Customer above to register subscribers with their ID and monthly package fee.",
                  "গ্রাহক আইডি ও মাসিক ফি সহ নতুন গ্রাহক যোগ করতে উপরের গ্রাহক বোতামে ট্যাপ করুন।",
                ),
            )
          }
        } else {
          items(filteredCustomers, key = { it.id }) { cust ->
            val custDue =
              bills.filter { it.customerDocId == cust.id }.sumOf { it.remainingDue }
            Card(
              shape = MaterialTheme.shapes.medium,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${cust.name} (${cust.customerCode})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "${cust.packageName.ifBlank { "Broadband" }} • ${Formatters.formatTaka(cust.monthlyFee)}/mo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (cust.phone.isNotBlank()) {
                    Text(
                      text = cust.phone,
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  if (custDue > 0) {
                    Text(
                      text = "${AppStrings.totalCustomerDue(lang)}: ${Formatters.formatTaka(custDue)}",
                      style = MaterialTheme.typography.labelLarge,
                      color = DueAmber,
                    )
                  }
                }
                OutlinedButton(onClick = { showCreateBillFor = cust }) {
                  Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(AppStrings.tr(lang, "New Bill", "বিল তৈরি"))
                }
              }
            }
          }
        }
      }

      2 -> {
        if (filteredPayments.isEmpty()) {
          item {
            EmptyStateBanner(
              title = AppStrings.tr(lang, "No payment history yet", "কোনো পেমেন্ট ইতিহাস নেই"),
              subtitle =
                AppStrings.tr(
                  lang,
                  "Partial and full customer bill payments appear here chronologically.",
                  "গ্রাহকদের আংশিক ও সম্পূর্ণ বিল পরিশোধের ইতিহাস এখানে দেখা যাবে।",
                ),
            )
          }
        } else {
          items(filteredPayments, key = { it.id }) { pay ->
            Card(
              shape = MaterialTheme.shapes.medium,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${pay.customerName} (${pay.customerCode})",
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text = "${AppStrings.tr(lang, "Month", "মাস")}: ${pay.billingMonth} • ${pay.paymentDateFormatted} • ${pay.paymentMethod}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (pay.collectedByEmployeeName.isNotBlank()) {
                    Text(
                      text = "${AppStrings.tr(lang, "Collected by", "আদায়কারী")}: ${pay.collectedByEmployeeName}",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
                Text(
                  text = Formatters.formatTaka(pay.paidAmount),
                  style = MonoAmountStyle.copy(fontSize = 15.sp),
                  color = IncomeGreen,
                )
              }
            }
          }
        }
      }
    }
  }

  // Add Customer Dialog
  if (showAddCustomer) {
    var code by remember { mutableStateOf("PN-${100 + customers.size + 1}") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("20 Mbps") }
    var fee by remember { mutableStateOf("800") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { showAddCustomer = false },
      title = { Text(AppStrings.tr(lang, "Register ISP Customer", "নতুন গ্রাহক নিবন্ধন")) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text(AppStrings.tr(lang, "Customer ID", "গ্রাহক আইডি")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(AppStrings.tr(lang, "Customer Name", "গ্রাহকের নাম")) },
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
            value = pkg,
            onValueChange = { pkg = it },
            label = { Text(AppStrings.tr(lang, "Package", "প্যাকেজ")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = fee,
            onValueChange = { fee = it },
            label = { Text(AppStrings.tr(lang, "Monthly Fee (৳)", "মাসিক বিল (৳)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(AppStrings.tr(lang, "Address", "ঠিকানা")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          if (!err.isNullOrBlank()) {
            Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val parsedFee = fee.toDoubleOrNull()
            if (code.isBlank() || name.isBlank() || parsedFee == null || parsedFee < 0) {
              err = AppStrings.tr(lang, "Enter valid customer ID, name, and monthly fee.", "সঠিক তথ্য দিন।")
            } else {
              onSaveCustomer(code, name, phone, address, pkg, parsedFee) {
                showAddCustomer = false
              }
            }
          }
        ) {
          Text(AppStrings.save(lang))
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddCustomer = false }) { Text(AppStrings.cancel(lang)) }
      },
    )
  }

  // Create Monthly Bill Dialog
  val billTargetCustomer = showCreateBillFor ?: if (showCreateBillGeneric) customers.firstOrNull() else null
  if (billTargetCustomer != null) {
    var selectedCustomer by remember(billTargetCustomer) { mutableStateOf(billTargetCustomer) }
    var custDropdown by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(Formatters.currentMonthFormatted()) }
    var monthlyAmt by remember(selectedCustomer) { mutableStateOf(selectedCustomer.monthlyFee.toString()) }
    val autoPrevDue =
      remember(selectedCustomer, bills, month) {
        bills
          .filter { it.customerDocId == selectedCustomer.id && it.billingMonth != month }
          .sumOf { it.remainingDue }
      }
    var prevDue by remember(autoPrevDue) { mutableStateOf(autoPrevDue.toString()) }

    val parsedMonthly = monthlyAmt.toDoubleOrNull() ?: 0.0
    val parsedPrev = prevDue.toDoubleOrNull() ?: 0.0
    val (totalPayable, _, _) = AccountingCalculator.calculateBillTotals(parsedMonthly, parsedPrev, 0.0)

    AlertDialog(
      onDismissRequest = {
        showCreateBillFor = null
        showCreateBillGeneric = false
      },
      title = { Text(AppStrings.tr(lang, "Create Monthly Bill", "মাসিক বিল তৈরি")) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ExposedDropdownMenuBox(
            expanded = custDropdown,
            onExpandedChange = { custDropdown = !custDropdown },
          ) {
            OutlinedTextField(
              value = "${selectedCustomer.name} (${selectedCustomer.customerCode})",
              onValueChange = {},
              readOnly = true,
              label = { Text(AppStrings.tr(lang, "Customer", "গ্রাহক")) },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = custDropdown) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
              expanded = custDropdown,
              onDismissRequest = { custDropdown = false },
            ) {
              customers.forEach { c ->
                DropdownMenuItem(
                  text = { Text("${c.name} (${c.customerCode})") },
                  onClick = {
                    selectedCustomer = c
                    custDropdown = false
                  },
                )
              }
            }
          }
          OutlinedTextField(
            value = month,
            onValueChange = { month = it },
            label = { Text(AppStrings.tr(lang, "Billing Month (MM-YYYY)", "বিলের মাস (MM-YYYY)")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = monthlyAmt,
            onValueChange = { monthlyAmt = it },
            label = { Text(AppStrings.tr(lang, "Monthly Bill (৳)", "মাসিক বিল (৳)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = prevDue,
            onValueChange = { prevDue = it },
            label = { Text(AppStrings.tr(lang, "Previous Due (৳)", "পূর্বের বকেয়া (৳)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(AppStrings.tr(lang, "Total Payable", "মোট প্রদেয়"), fontWeight = FontWeight.Bold)
              Text(Formatters.formatTaka(totalPayable), style = MonoAmountStyle)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (month.isNotBlank() && parsedMonthly >= 0.0 && parsedPrev >= 0.0) {
              onCreateBill(selectedCustomer, month.trim(), parsedMonthly, parsedPrev) {
                showCreateBillFor = null
                showCreateBillGeneric = false
              }
            }
          }
        ) {
          Text(AppStrings.save(lang))
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showCreateBillFor = null
            showCreateBillGeneric = false
          }
        ) {
          Text(AppStrings.cancel(lang))
        }
      },
    )
  }

  // Record Partial / Full Payment Dialog
  showPaymentForBill?.let { bill ->
    var paidNowText by remember(bill) { mutableStateOf(bill.remainingDue.toString()) }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dateFormatted by remember { mutableStateOf(Formatters.currentDateFormatted()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var selectedEmployee by remember { mutableStateOf<OfficeEmployee?>(null) }
    var empExpanded by remember { mutableStateOf(false) }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val paidNow = paidNowText.toDoubleOrNull() ?: 0.0
    val projectedRemaining = (bill.remainingDue - paidNow).coerceAtLeast(0.0)

    AlertDialog(
      onDismissRequest = { showPaymentForBill = null },
      title = {
        Text("${AppStrings.tr(lang, "Collect Payment", "বিল সংগ্রহ")}: ${bill.customerName}")
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text =
              "${AppStrings.tr(lang, "Month", "মাস")}: ${bill.billingMonth} • ${AppStrings.tr(lang, "Due", "বকেয়া")}: ${Formatters.formatTaka(bill.remainingDue)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
          )
          OutlinedTextField(
            value = paidNowText,
            onValueChange = { paidNowText = it },
            label = { Text(AppStrings.tr(lang, "Paid Amount (৳)", "জমার পরিমাণ (৳)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("bill_pay_amount_input"),
          )
          Text(
            text = "${AppStrings.tr(lang, "Remaining Due after payment", "পেমেন্টের পর বকেয়া")}: ${Formatters.formatTaka(projectedRemaining)}",
            style = MaterialTheme.typography.labelLarge,
            color = DueAmber,
          )
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

          if (employees.isNotEmpty()) {
            ExposedDropdownMenuBox(
              expanded = empExpanded,
              onExpandedChange = { empExpanded = !empExpanded },
            ) {
              OutlinedTextField(
                value = selectedEmployee?.name ?: AppStrings.tr(lang, "Direct Office Collection", "সরাসরি অফিস আদায়"),
                onValueChange = {},
                readOnly = true,
                label = { Text(AppStrings.tr(lang, "Collected By (Optional)", "আদায়কারী (ঐচ্ছিক)")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = empExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
              )
              ExposedDropdownMenu(
                expanded = empExpanded,
                onDismissRequest = { empExpanded = false },
              ) {
                DropdownMenuItem(
                  text = { Text(AppStrings.tr(lang, "Direct Office Collection", "সরাসরি অফিস আদায়")) },
                  onClick = {
                    selectedEmployee = null
                    empExpanded = false
                  },
                )
                employees.forEach { emp ->
                  DropdownMenuItem(
                    text = { Text(emp.name) },
                    onClick = {
                      selectedEmployee = emp
                      empExpanded = false
                    },
                  )
                }
              }
            }
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
        Button(
          enabled = paidNow > 0.0,
          onClick = {
            val parsedMillis = Formatters.parseDateMillis(dateFormatted) ?: dateMillis
            onRecordPayment(
              bill,
              paidNow,
              parsedMillis,
              dateFormatted,
              method,
              selectedEmployee,
              reference,
              note,
            ) {
              showPaymentForBill = null
            }
          },
          modifier = Modifier.testTag("bill_pay_confirm_btn"),
        ) {
          Text(AppStrings.save(lang))
        }
      },
      dismissButton = {
        TextButton(onClick = { showPaymentForBill = null }) { Text(AppStrings.cancel(lang)) }
      },
    )

    if (showDatePicker) {
      DatePickerDialogModal(
        initialMillis = dateMillis,
        onDismiss = { showDatePicker = false },
        onDateSelected = { m, fmt ->
          dateMillis = m
          dateFormatted = fmt
          showDatePicker = false
        },
      )
    }
  }
}

@Composable
private fun BillCard(
  bill: CustomerBill,
  lang: AppLang,
  onCollectClick: () -> Unit,
) {
  val (statusBg, statusColor, statusLabel) =
    when (bill.billStatus) {
      BillStatus.PAID ->
        Triple(IncomeGreenBg, IncomeGreen, AppStrings.tr(lang, "PAID", "পরিশোধিত"))
      BillStatus.PARTIAL ->
        Triple(DueAmberBg, DueAmber, AppStrings.tr(lang, "PARTIAL", "আংশিক"))
      BillStatus.UNPAID ->
        Triple(
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.error,
          AppStrings.tr(lang, "UNPAID", "বকেয়া"),
        )
    }

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${bill.customerName} (${bill.customerCode})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "${AppStrings.tr(lang, "Billing Month", "মাস")}: ${bill.billingMonth}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Surface(shape = RoundedCornerShape(50), color = statusBg) {
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        BillMetric(AppStrings.tr(lang, "Payable", "মোট প্রদেয়"), Formatters.formatTaka(bill.totalPayable))
        BillMetric(AppStrings.tr(lang, "Paid", "পরিশোধিত"), Formatters.formatTaka(bill.paidAmount), IncomeGreen)
        BillMetric(AppStrings.tr(lang, "Due", "বকেয়া"), Formatters.formatTaka(bill.remainingDue), DueAmber)
      }

      if (bill.remainingDue > 0.0) {
        Button(
          onClick = onCollectClick,
          modifier = Modifier.fillMaxWidth().height(44.dp).testTag("collect_bill_${bill.id}"),
        ) {
          Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(AppStrings.tr(lang, "Collect / Partial Payment", "বিল সংগ্রহ / আংশিক জমা"))
        }
      }
    }
  }
}

@Composable
private fun BillMetric(
  label: String,
  value: String,
  color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = value, style = MonoAmountStyle.copy(fontSize = 13.sp), color = color)
  }
}
