package com.example.data

import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.Formatters
import com.example.model.AccountBalanceConfig
import com.example.model.AccountScope
import com.example.model.BillPayment
import com.example.model.CustomerBill
import com.example.model.DashboardScopeFilter
import com.example.model.EmployeeEntryType
import com.example.model.EmployeeLedgerEntry
import com.example.model.OfficeEmployee
import com.example.model.PaymentMethod
import com.example.model.ReportType
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import java.util.Calendar
import java.util.Locale

data class AccountSummary(
  val totalIncome: Double = 0.0,
  val totalExpense: Double = 0.0,
  val netProfit: Double = 0.0,
  val cashBalance: Double = 0.0,
  val bankBalance: Double = 0.0,
  val mobileBalance: Double = 0.0,
  val dailyOpeningBalance: Double = 0.0,
  val dailyClosingBalance: Double = 0.0,
)

data class MonthlyChartPoint(
  val monthKey: String, // MM-YYYY
  val label: String,
  val income: Double,
  val expense: Double,
)

data class EmployeeSummary(
  val employee: OfficeEmployee,
  val totalCollected: Double,
  val totalDeposited: Double,
  val remainingMoney: Double,
  val entries: List<EmployeeLedgerEntry>,
)

data class ReportDataset(
  val title: String,
  val subtitle: String,
  val headers: List<String>,
  val rows: List<List<String>>,
  val summaryLines: List<Pair<String, String>>,
)

object AccountingCalculator {

  fun computeSummary(
    transactions: List<TransactionRecord>,
    accounts: List<AccountBalanceConfig>,
    targetDateFormatted: String = Formatters.currentDateFormatted(),
  ): AccountSummary {
    val openingCash =
      accounts.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.openingBalance }
    val openingBank =
      accounts.filter { it.paymentMethod == PaymentMethod.BANK }.sumOf { it.openingBalance }
    val openingMobile =
      accounts
        .filter { it.paymentMethod == PaymentMethod.MOBILE_BANKING }
        .sumOf { it.openingBalance }

    var totalIncome = 0.0
    var totalExpense = 0.0
    var cashBal = openingCash
    var bankBal = openingBank
    var mobileBal = openingMobile

    val targetStartMillis = startOfDayMillis(targetDateFormatted)
    val targetEndMillis = targetStartMillis + 86_400_000L - 1L
    var dailyOpening = openingCash + openingBank + openingMobile
    var dailyDelta = 0.0

    for (tx in transactions) {
      val amount = tx.amount
      val netEffect: Double
      when (tx.txType) {
        TransactionType.INCOME -> {
          totalIncome += amount
          netEffect = amount
          when (tx.method) {
            PaymentMethod.CASH -> cashBal += amount
            PaymentMethod.BANK -> bankBal += amount
            PaymentMethod.MOBILE_BANKING -> mobileBal += amount
          }
        }
        TransactionType.EXPENSE -> {
          totalExpense += amount
          netEffect = -amount
          when (tx.method) {
            PaymentMethod.CASH -> cashBal -= amount
            PaymentMethod.BANK -> bankBal -= amount
            PaymentMethod.MOBILE_BANKING -> mobileBal -= amount
          }
        }
        TransactionType.TRANSFER -> {
          // Transfers between Cash / Bank / Mobile NEVER count as Income or Expense
          netEffect = 0.0
          when (tx.method) {
            PaymentMethod.CASH -> cashBal -= amount
            PaymentMethod.BANK -> bankBal -= amount
            PaymentMethod.MOBILE_BANKING -> mobileBal -= amount
          }
          when (tx.targetMethod) {
            PaymentMethod.CASH -> cashBal += amount
            PaymentMethod.BANK -> bankBal += amount
            PaymentMethod.MOBILE_BANKING -> mobileBal += amount
            null -> {}
          }
        }
      }

      if (tx.dateMillis < targetStartMillis) {
        dailyOpening += netEffect
      } else if (tx.dateMillis in targetStartMillis..targetEndMillis) {
        dailyDelta += netEffect
      }
    }

    return AccountSummary(
      totalIncome = totalIncome,
      totalExpense = totalExpense,
      netProfit = totalIncome - totalExpense,
      cashBalance = cashBal,
      bankBalance = bankBal,
      mobileBalance = mobileBal,
      dailyOpeningBalance = dailyOpening,
      dailyClosingBalance = dailyOpening + dailyDelta,
    )
  }

  fun combineSummaries(a: AccountSummary, b: AccountSummary): AccountSummary =
    AccountSummary(
      totalIncome = a.totalIncome + b.totalIncome,
      totalExpense = a.totalExpense + b.totalExpense,
      netProfit = a.netProfit + b.netProfit,
      cashBalance = a.cashBalance + b.cashBalance,
      bankBalance = a.bankBalance + b.bankBalance,
      mobileBalance = a.mobileBalance + b.mobileBalance,
      dailyOpeningBalance = a.dailyOpeningBalance + b.dailyOpeningBalance,
      dailyClosingBalance = a.dailyClosingBalance + b.dailyClosingBalance,
    )

  fun computeMonthlyChart(
    transactions: List<TransactionRecord>,
    monthsBack: Int = 6,
  ): List<MonthlyChartPoint> {
    val cal = Calendar.getInstance()
    val monthBuckets = mutableListOf<Pair<String, String>>()
    val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.add(Calendar.MONTH, -(monthsBack - 1))
    repeat(monthsBack) {
      val m = cal.get(Calendar.MONTH)
      val y = cal.get(Calendar.YEAR)
      val key = String.format(Locale.US, "%02d-%04d", m + 1, y)
      val label = monthNames[m]
      monthBuckets.add(key to label)
      cal.add(Calendar.MONTH, 1)
    }

    val grouped = transactions.groupBy { tx ->
      if (tx.dateFormatted.length == 10) tx.dateFormatted.substring(3, 10) else ""
    }

    return monthBuckets.map { (key, label) ->
      val items = grouped[key].orEmpty()
      val inc = items.filter { it.txType == TransactionType.INCOME }.sumOf { it.amount }
      val exp = items.filter { it.txType == TransactionType.EXPENSE }.sumOf { it.amount }
      MonthlyChartPoint(monthKey = key, label = label, income = inc, expense = exp)
    }
  }

  fun computeEmployeeSummaries(
    employees: List<OfficeEmployee>,
    entries: List<EmployeeLedgerEntry>,
  ): List<EmployeeSummary> {
    val byEmp = entries.groupBy { it.employeeId }
    return employees.map { emp ->
      val list = byEmp[emp.id].orEmpty().sortedByDescending { it.dateMillis }
      val collected =
        list.filter { it.type == EmployeeEntryType.COLLECTION }.sumOf { it.amount }
      val deposited =
        list.filter { it.type == EmployeeEntryType.DEPOSIT }.sumOf { it.amount }
      EmployeeSummary(
        employee = emp,
        totalCollected = collected,
        totalDeposited = deposited,
        remainingMoney = collected - deposited,
        entries = list,
      )
    }
  }

  fun isPotentialDuplicate(
    existing: List<TransactionRecord>,
    type: TransactionType,
    scope: AccountScope,
    category: String,
    partyName: String,
    amount: Double,
    dateFormatted: String,
    referenceNumber: String,
    editingId: String? = null,
  ): Boolean {
    val normParty = partyName.trim().lowercase(Locale.US)
    val normRef = referenceNumber.trim().lowercase(Locale.US)
    return existing.any { item ->
      if (editingId != null && item.id == editingId) return@any false
      item.scope == scope &&
        item.txType == type &&
        item.category == category &&
        item.dateFormatted == dateFormatted &&
        kotlin.math.abs(item.amount - amount) < 0.01 &&
        item.partyName.trim().lowercase(Locale.US) == normParty &&
        (normRef.isEmpty() || item.referenceNumber.trim().lowercase(Locale.US) == normRef)
    }
  }

  fun calculateBillTotals(
    monthlyBillAmount: Double,
    previousDue: Double,
    paidAmount: Double,
  ): Triple<Double, Double, BillStatusCompat> {
    val totalPayable = (monthlyBillAmount + previousDue).coerceAtLeast(0.0)
    val remaining = (totalPayable - paidAmount).coerceAtLeast(0.0)
    val status =
      when {
        remaining <= 0.009 -> BillStatusCompat.PAID
        paidAmount > 0.0 -> BillStatusCompat.PARTIAL
        else -> BillStatusCompat.UNPAID
      }
    return Triple(totalPayable, remaining, status)
  }

  enum class BillStatusCompat {
    UNPAID,
    PARTIAL,
    PAID,
  }

  fun generateReport(
    type: ReportType,
    lang: AppLang,
    scopeFilter: DashboardScopeFilter,
    selectedDate: String,
    selectedMonth: String,
    selectedYear: String,
    personalTxs: List<TransactionRecord>,
    ispTxs: List<TransactionRecord>,
    personalAccounts: List<AccountBalanceConfig>,
    ispAccounts: List<AccountBalanceConfig>,
    bills: List<CustomerBill>,
    payments: List<BillPayment>,
    employees: List<OfficeEmployee>,
    employeeEntries: List<EmployeeLedgerEntry>,
  ): ReportDataset {
    val scopedTxs =
      when (scopeFilter) {
        DashboardScopeFilter.PERSONAL -> personalTxs
        DashboardScopeFilter.ISP_BUSINESS -> ispTxs
        DashboardScopeFilter.COMBINED -> (personalTxs + ispTxs).sortedByDescending { it.dateMillis }
      }
    val scopedAccounts =
      when (scopeFilter) {
        DashboardScopeFilter.PERSONAL -> personalAccounts
        DashboardScopeFilter.ISP_BUSINESS -> ispAccounts
        DashboardScopeFilter.COMBINED -> personalAccounts + ispAccounts
      }

    fun methodLabel(m: PaymentMethod) =
      when (m) {
        PaymentMethod.CASH -> AppStrings.cash(lang)
        PaymentMethod.BANK -> AppStrings.bank(lang)
        PaymentMethod.MOBILE_BANKING -> AppStrings.mobileBanking(lang)
      }

    return when (type) {
      ReportType.DAILY_INCOME -> {
        val rowsData =
          scopedTxs.filter {
            it.txType == TransactionType.INCOME && it.dateFormatted == selectedDate
          }
        val total = rowsData.sumOf { it.amount }
        ReportDataset(
          title = AppStrings.tr(lang, "Daily Income Report", "দৈনিক আয় রিপোর্ট"),
          subtitle = "${AppStrings.tr(lang, "Date", "তারিখ")}: $selectedDate",
          headers =
            listOf(
              AppStrings.tr(lang, "Date", "তারিখ"),
              AppStrings.tr(lang, "Category", "বিভাগ"),
              AppStrings.tr(lang, "Party", "পক্ষ"),
              AppStrings.tr(lang, "Method", "মাধ্যম"),
              AppStrings.tr(lang, "Amount", "পরিমাণ"),
            ),
          rows =
            rowsData.map {
              listOf(
                it.dateFormatted,
                AppStrings.categoryLabel(lang, it.category),
                it.partyName,
                methodLabel(it.method),
                Formatters.formatTaka(it.amount),
              )
            },
          summaryLines =
            listOf(
              AppStrings.tr(lang, "Total Entries", "মোট এন্ট্রি") to "${rowsData.size}",
              AppStrings.totalIncome(lang) to Formatters.formatTaka(total),
            ),
        )
      }

      ReportType.DAILY_EXPENSE -> {
        val rowsData =
          scopedTxs.filter {
            it.txType == TransactionType.EXPENSE && it.dateFormatted == selectedDate
          }
        val total = rowsData.sumOf { it.amount }
        ReportDataset(
          title = AppStrings.tr(lang, "Daily Expense Report", "দৈনিক ব্যয় রিপোর্ট"),
          subtitle = "${AppStrings.tr(lang, "Date", "তারিখ")}: $selectedDate",
          headers =
            listOf(
              AppStrings.tr(lang, "Date", "তারিখ"),
              AppStrings.tr(lang, "Category", "বিভাগ"),
              AppStrings.tr(lang, "Paid To", "প্রাপক"),
              AppStrings.tr(lang, "Method", "মাধ্যম"),
              AppStrings.tr(lang, "Amount", "পরিমাণ"),
            ),
          rows =
            rowsData.map {
              listOf(
                it.dateFormatted,
                AppStrings.categoryLabel(lang, it.category),
                it.partyName,
                methodLabel(it.method),
                Formatters.formatTaka(it.amount),
              )
            },
          summaryLines =
            listOf(
              AppStrings.tr(lang, "Total Entries", "মোট এন্ট্রি") to "${rowsData.size}",
              AppStrings.totalExpenses(lang) to Formatters.formatTaka(total),
            ),
        )
      }

      ReportType.MONTHLY_INCOME -> {
        val rowsData =
          scopedTxs.filter {
            it.txType == TransactionType.INCOME && it.dateFormatted.endsWith(selectedMonth)
          }
        val total = rowsData.sumOf { it.amount }
        ReportDataset(
          title = AppStrings.tr(lang, "Monthly Income Report", "মাসিক আয় রিপোর্ট"),
          subtitle = "${AppStrings.tr(lang, "Month", "মাস")}: $selectedMonth",
          headers =
            listOf(
              AppStrings.tr(lang, "Date", "তারিখ"),
              AppStrings.tr(lang, "Account", "হিসাব"),
              AppStrings.tr(lang, "Category", "বিভাগ"),
              AppStrings.tr(lang, "From", "উৎস"),
              AppStrings.tr(lang, "Amount", "পরিমাণ"),
            ),
          rows =
            rowsData.map {
              listOf(
                it.dateFormatted,
                if (it.scope == AccountScope.PERSONAL) AppStrings.scopePersonal(lang)
                else AppStrings.scopeIsp(lang),
                AppStrings.categoryLabel(lang, it.category),
                it.partyName,
                Formatters.formatTaka(it.amount),
              )
            },
          summaryLines = listOf(AppStrings.totalIncome(lang) to Formatters.formatTaka(total)),
        )
      }

      ReportType.MONTHLY_EXPENSE -> {
        val rowsData =
          scopedTxs.filter {
            it.txType == TransactionType.EXPENSE && it.dateFormatted.endsWith(selectedMonth)
          }
        val total = rowsData.sumOf { it.amount }
        ReportDataset(
          title = AppStrings.tr(lang, "Monthly Expense Report", "মাসিক ব্যয় রিপোর্ট"),
          subtitle = "${AppStrings.tr(lang, "Month", "মাস")}: $selectedMonth",
          headers =
            listOf(
              AppStrings.tr(lang, "Date", "তারিখ"),
              AppStrings.tr(lang, "Account", "হিসাব"),
              AppStrings.tr(lang, "Category", "বিভাগ"),
              AppStrings.tr(lang, "Paid To", "প্রাপক"),
              AppStrings.tr(lang, "Amount", "পরিমাণ"),
            ),
          rows =
            rowsData.map {
              listOf(
                it.dateFormatted,
                if (it.scope == AccountScope.PERSONAL) AppStrings.scopePersonal(lang)
                else AppStrings.scopeIsp(lang),
                AppStrings.categoryLabel(lang, it.category),
                it.partyName,
                Formatters.formatTaka(it.amount),
              )
            },
          summaryLines = listOf(AppStrings.totalExpenses(lang) to Formatters.formatTaka(total)),
        )
      }

      ReportType.YEARLY_FINANCIAL -> {
        val yearTxs = scopedTxs.filter { it.dateFormatted.endsWith(selectedYear) }
        val byMonth =
          yearTxs.groupBy {
            if (it.dateFormatted.length == 10) it.dateFormatted.substring(3, 10) else ""
          }
        val months = byMonth.keys.sorted()
        val totalInc = yearTxs.filter { it.txType == TransactionType.INCOME }.sumOf { it.amount }
        val totalExp = yearTxs.filter { it.txType == TransactionType.EXPENSE }.sumOf { it.amount }
        ReportDataset(
          title = AppStrings.tr(lang, "Yearly Financial Report", "বার্ষিক আর্থিক রিপোর্ট"),
          subtitle = "${AppStrings.tr(lang, "Year", "বছর")}: $selectedYear",
          headers =
            listOf(
              AppStrings.tr(lang, "Month", "মাস"),
              AppStrings.totalIncome(lang),
              AppStrings.totalExpenses(lang),
              AppStrings.netProfit(lang),
            ),
          rows =
            months.map { m ->
              val items = byMonth[m].orEmpty()
              val inc = items.filter { it.txType == TransactionType.INCOME }.sumOf { it.amount }
              val exp = items.filter { it.txType == TransactionType.EXPENSE }.sumOf { it.amount }
              listOf(
                m,
                Formatters.formatTaka(inc),
                Formatters.formatTaka(exp),
                Formatters.formatTaka(inc - exp),
              )
            },
          summaryLines =
            listOf(
              AppStrings.totalIncome(lang) to Formatters.formatTaka(totalInc),
              AppStrings.totalExpenses(lang) to Formatters.formatTaka(totalExp),
              AppStrings.netProfit(lang) to Formatters.formatTaka(totalInc - totalExp),
            ),
        )
      }

      ReportType.CUSTOMER_COLLECTION -> {
        val filtered =
          if (selectedMonth.isBlank()) payments
          else payments.filter { it.billingMonth == selectedMonth || it.paymentDateFormatted.endsWith(selectedMonth) }
        val total = filtered.sumOf { it.paidAmount }
        ReportDataset(
          title = AppStrings.tr(lang, "Customer Collection Report", "গ্রাহক বিল আদায় রিপোর্ট"),
          subtitle = "${AppStrings.tr(lang, "Billing Month", "বিল মাস")}: $selectedMonth",
          headers =
            listOf(
              AppStrings.tr(lang, "Date", "তারিখ"),
              AppStrings.tr(lang, "Customer ID", "আইডি"),
              AppStrings.tr(lang, "Customer", "গ্রাহক"),
              AppStrings.tr(lang, "Method", "মাধ্যম"),
              AppStrings.tr(lang, "Paid", "আদায়"),
            ),
          rows =
            filtered.map {
              listOf(
                it.paymentDateFormatted,
                it.customerCode,
                it.customerName,
                methodLabel(it.method),
                Formatters.formatTaka(it.paidAmount),
              )
            },
          summaryLines =
            listOf(
              AppStrings.totalCustomerCollection(lang) to Formatters.formatTaka(total)
            ),
        )
      }

      ReportType.CUSTOMER_DUE -> {
        val dueBills = bills.filter { it.remainingDue > 0.0 }
        val totalDue = dueBills.sumOf { it.remainingDue }
        ReportDataset(
          title = AppStrings.tr(lang, "Customer Due Report", "গ্রাহক বকেয়া রিপোর্ট"),
          subtitle = AppStrings.tr(lang, "Outstanding customer balances", "বকেয়া বিল তালিকা"),
          headers =
            listOf(
              AppStrings.tr(lang, "ID", "আইডি"),
              AppStrings.tr(lang, "Customer", "গ্রাহক"),
              AppStrings.tr(lang, "Month", "মাস"),
              AppStrings.tr(lang, "Payable", "মোট বিল"),
              AppStrings.tr(lang, "Due", "বকেয়া"),
            ),
          rows =
            dueBills.map {
              listOf(
                it.customerCode,
                it.customerName,
                it.billingMonth,
                Formatters.formatTaka(it.totalPayable),
                Formatters.formatTaka(it.remainingDue),
              )
            },
          summaryLines =
            listOf(AppStrings.totalCustomerDue(lang) to Formatters.formatTaka(totalDue)),
        )
      }

      ReportType.IIG_CDN_CFAN -> {
        val cats =
          setOf(
            "IIG Income",
            "CDN Income",
            "CFAN Income",
            "IIG Bill Payment",
            "CDN Bill Payment",
            "CFAN Bill Payment",
          )
        val rowsData =
          ispTxs.filter { it.category in cats && it.dateFormatted.endsWith(selectedMonth) }
        val inc = rowsData.filter { it.txType == TransactionType.INCOME }.sumOf { it.amount }
        val exp = rowsData.filter { it.txType == TransactionType.EXPENSE }.sumOf { it.amount }
        ReportDataset(
          title = AppStrings.tr(lang, "IIG / CDN / CFAN Report", "আইআইজি / সিডিএন / সিএফএএন রিপোর্ট"),
          subtitle = "${AppStrings.tr(lang, "Month", "মাস")}: $selectedMonth",
          headers =
            listOf(
              AppStrings.tr(lang, "Date", "তারিখ"),
              AppStrings.tr(lang, "Type", "ধরন"),
              AppStrings.tr(lang, "Category", "বিভাগ"),
              AppStrings.tr(lang, "Party", "প্রতিষ্ঠান"),
              AppStrings.tr(lang, "Amount", "পরিমাণ"),
            ),
          rows =
            rowsData.map {
              listOf(
                it.dateFormatted,
                if (it.txType == TransactionType.INCOME) AppStrings.income(lang)
                else AppStrings.expense(lang),
                AppStrings.categoryLabel(lang, it.category),
                it.partyName,
                Formatters.formatTaka(it.amount),
              )
            },
          summaryLines =
            listOf(
              AppStrings.totalIncome(lang) to Formatters.formatTaka(inc),
              AppStrings.totalExpenses(lang) to Formatters.formatTaka(exp),
              AppStrings.netProfit(lang) to Formatters.formatTaka(inc - exp),
            ),
        )
      }

      ReportType.EMPLOYEE_COLLECTION -> {
        val summaries = computeEmployeeSummaries(employees, employeeEntries)
        val totalCol = summaries.sumOf { it.totalCollected }
        val totalDep = summaries.sumOf { it.totalDeposited }
        ReportDataset(
          title = AppStrings.tr(lang, "Employee Collection Report", "কর্মচারী আদায় রিপোর্ট"),
          subtitle = AppStrings.tr(lang, "Office team collections & deposits", "টিম আদায় ও জমা বিবরণী"),
          headers =
            listOf(
              AppStrings.tr(lang, "Employee", "কর্মচারী"),
              AppStrings.tr(lang, "Collected", "মোট আদায়"),
              AppStrings.tr(lang, "Deposited", "অফিসে জমা"),
              AppStrings.tr(lang, "Remaining", "হাতে অবশিষ্ট"),
            ),
          rows =
            summaries.map {
              listOf(
                it.employee.name,
                Formatters.formatTaka(it.totalCollected),
                Formatters.formatTaka(it.totalDeposited),
                Formatters.formatTaka(it.remainingMoney),
              )
            },
          summaryLines =
            listOf(
              AppStrings.tr(lang, "Total Collected", "মোট আদায়") to Formatters.formatTaka(totalCol),
              AppStrings.tr(lang, "Total Deposited", "মোট জমা") to Formatters.formatTaka(totalDep),
              AppStrings.tr(lang, "Remaining", "অবশিষ্ট") to Formatters.formatTaka(totalCol - totalDep),
            ),
        )
      }

      ReportType.PROFIT_AND_LOSS -> {
        val monthTxs = scopedTxs.filter { it.dateFormatted.endsWith(selectedMonth) }
        val incomeByCat =
          monthTxs
            .filter { it.txType == TransactionType.INCOME }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val expenseByCat =
          monthTxs
            .filter { it.txType == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val totalInc = incomeByCat.values.sum()
        val totalExp = expenseByCat.values.sum()
        val rows = buildList {
          incomeByCat.forEach { (cat, amt) ->
            add(
              listOf(
                AppStrings.income(lang),
                AppStrings.categoryLabel(lang, cat),
                Formatters.formatTaka(amt),
              )
            )
          }
          expenseByCat.forEach { (cat, amt) ->
            add(
              listOf(
                AppStrings.expense(lang),
                AppStrings.categoryLabel(lang, cat),
                Formatters.formatTaka(amt),
              )
            )
          }
        }
        ReportDataset(
          title = AppStrings.tr(lang, "Profit & Loss Report", "লাভ ও ক্ষতি বিবরণী"),
          subtitle = "${AppStrings.tr(lang, "Month", "মাস")}: $selectedMonth",
          headers =
            listOf(
              AppStrings.tr(lang, "Type", "ধরন"),
              AppStrings.tr(lang, "Category", "বিভাগ"),
              AppStrings.tr(lang, "Total", "মোট"),
            ),
          rows = rows,
          summaryLines =
            listOf(
              AppStrings.totalIncome(lang) to Formatters.formatTaka(totalInc),
              AppStrings.totalExpenses(lang) to Formatters.formatTaka(totalExp),
              AppStrings.netProfit(lang) to Formatters.formatTaka(totalInc - totalExp),
            ),
        )
      }

      ReportType.CASH_FLOW -> {
        val summary = computeSummary(scopedTxs, scopedAccounts, selectedDate)
        val flows =
          scopedTxs
            .filter { it.dateFormatted.endsWith(selectedMonth) }
            .sortedByDescending { it.dateMillis }
        ReportDataset(
          title = AppStrings.tr(lang, "Cash Flow Report", "নগদ প্রবাহ রিপোর্ট"),
          subtitle = "${AppStrings.tr(lang, "Month", "মাস")}: $selectedMonth",
          headers =
            listOf(
              AppStrings.tr(lang, "Date", "তারিখ"),
              AppStrings.tr(lang, "Type", "ধরন"),
              AppStrings.tr(lang, "Method", "মাধ্যম"),
              AppStrings.tr(lang, "Party", "বিবরণ"),
              AppStrings.tr(lang, "Amount", "পরিমাণ"),
            ),
          rows =
            flows.map {
              val mStr =
                if (it.txType == TransactionType.TRANSFER && it.targetMethod != null) {
                  "${methodLabel(it.method)} -> ${methodLabel(it.targetMethod!!)}"
                } else methodLabel(it.method)
              listOf(
                it.dateFormatted,
                it.txType.name,
                mStr,
                it.partyName,
                Formatters.formatTaka(it.amount),
              )
            },
          summaryLines =
            listOf(
              AppStrings.cashBalance(lang) to Formatters.formatTaka(summary.cashBalance),
              AppStrings.bankBalance(lang) to Formatters.formatTaka(summary.bankBalance),
              AppStrings.mobileBalance(lang) to Formatters.formatTaka(summary.mobileBalance),
            ),
        )
      }
    }
  }

  private fun startOfDayMillis(ddMmYyyy: String): Long {
    val parsed = Formatters.parseDateMillis(ddMmYyyy) ?: System.currentTimeMillis()
    val cal = Calendar.getInstance()
    cal.timeInMillis = parsed
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
  }
}
