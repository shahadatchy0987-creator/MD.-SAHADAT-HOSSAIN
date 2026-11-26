package com.example

import com.example.data.AccountingCalculator
import com.example.model.AccountBalanceConfig
import com.example.model.AccountScope
import com.example.model.EmployeeEntryType
import com.example.model.EmployeeLedgerEntry
import com.example.model.OfficeEmployee
import com.example.model.PaymentMethod
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingCalculatorTest {

  @Test
  fun transfersDoNotCountAsIncomeOrExpense_andAccountsStaySeparated() {
    val personalAccounts = listOf(
      AccountBalanceConfig(id = "p1", method = PaymentMethod.CASH.name, openingBalance = 1000.0),
      AccountBalanceConfig(id = "p2", method = PaymentMethod.BANK.name, openingBalance = 5000.0),
    )
    val ispAccounts = listOf(
      AccountBalanceConfig(id = "i1", method = PaymentMethod.CASH.name, openingBalance = 2000.0),
      AccountBalanceConfig(id = "i2", method = PaymentMethod.BANK.name, openingBalance = 10000.0),
    )

    val personalTxs = listOf(
      TransactionRecord(
        id = "pt1",
        scope = AccountScope.PERSONAL,
        type = TransactionType.INCOME.name,
        category = "Personal Income",
        partyName = "Salary",
        amount = 500.0,
        paymentMethod = PaymentMethod.CASH.name,
        dateMillis = 1_700_000_000_000L,
        dateFormatted = "15-11-2023",
      ),
      TransactionRecord(
        id = "pt2",
        scope = AccountScope.PERSONAL,
        type = TransactionType.TRANSFER.name,
        category = "Cash / Bank Transfer",
        partyName = "Deposit to Bank",
        amount = 400.0,
        paymentMethod = PaymentMethod.CASH.name,
        toPaymentMethod = PaymentMethod.BANK.name,
        dateMillis = 1_700_000_100_000L,
        dateFormatted = "15-11-2023",
      ),
    )

    val ispTxs = listOf(
      TransactionRecord(
        id = "it1",
        scope = AccountScope.ISP_BUSINESS,
        type = TransactionType.INCOME.name,
        category = "IIG Income",
        partyName = "Upstream",
        amount = 8000.0,
        paymentMethod = PaymentMethod.BANK.name,
        dateMillis = 1_700_000_000_000L,
        dateFormatted = "15-11-2023",
      ),
      TransactionRecord(
        id = "it2",
        scope = AccountScope.ISP_BUSINESS,
        type = TransactionType.EXPENSE.name,
        category = "Office Rent",
        partyName = "Landlord",
        amount = 3000.0,
        paymentMethod = PaymentMethod.CASH.name,
        dateMillis = 1_700_000_200_000L,
        dateFormatted = "15-11-2023",
      ),
    )

    val personalSummary = AccountingCalculator.computeSummary(personalTxs, personalAccounts, "15-11-2023")
    val ispSummary = AccountingCalculator.computeSummary(ispTxs, ispAccounts, "15-11-2023")

    // Transfer of 400 from Cash -> Bank must NOT inflate income or expense
    assertEquals(500.0, personalSummary.totalIncome, 0.001)
    assertEquals(0.0, personalSummary.totalExpense, 0.001)
    assertEquals(500.0, personalSummary.netProfit, 0.001)
    assertEquals(1100.0, personalSummary.cashBalance, 0.001)
    assertEquals(5400.0, personalSummary.bankBalance, 0.001)

    // ISP business must remain strictly isolated
    assertEquals(8000.0, ispSummary.totalIncome, 0.001)
    assertEquals(3000.0, ispSummary.totalExpense, 0.001)
    assertEquals(5000.0, ispSummary.netProfit, 0.001)
    assertEquals(-1000.0, ispSummary.cashBalance, 0.001)
    assertEquals(18000.0, ispSummary.bankBalance, 0.001)
  }

  @Test
  fun customerBillTotals_supportPartialAndPaidStates() {
    val (payable, remaining1, status1) = AccountingCalculator.calculateBillTotals(1000.0, 500.0, 600.0)
    assertEquals(1500.0, payable, 0.001)
    assertEquals(900.0, remaining1, 0.001)
    assertEquals(AccountingCalculator.BillStatusCompat.PARTIAL, status1)

    val (_, remaining2, status2) = AccountingCalculator.calculateBillTotals(1000.0, 500.0, 1500.0)
    assertEquals(0.0, remaining2, 0.001)
    assertEquals(AccountingCalculator.BillStatusCompat.PAID, status2)

    val (_, remaining3, status3) = AccountingCalculator.calculateBillTotals(800.0, 0.0, 0.0)
    assertEquals(800.0, remaining3, 0.001)
    assertEquals(AccountingCalculator.BillStatusCompat.UNPAID, status3)
  }

  @Test
  fun duplicateDetection_flagsMatchingRecordButIgnoresSelfOnEdit() {
    val existing = listOf(
      TransactionRecord(
        id = "tx_1",
        scope = AccountScope.ISP_BUSINESS,
        type = TransactionType.EXPENSE.name,
        category = "Electricity Bill",
        partyName = "DESCO",
        amount = 4500.0,
        dateFormatted = "20-11-2026",
        referenceNumber = "REF-99",
      )
    )

    assertTrue(
      AccountingCalculator.isPotentialDuplicate(
        existing = existing,
        type = TransactionType.EXPENSE,
        scope = AccountScope.ISP_BUSINESS,
        category = "Electricity Bill",
        partyName = " desco ",
        amount = 4500.0,
        dateFormatted = "20-11-2026",
        referenceNumber = "ref-99",
        editingId = null,
      )
    )

    assertFalse(
      AccountingCalculator.isPotentialDuplicate(
        existing = existing,
        type = TransactionType.EXPENSE,
        scope = AccountScope.ISP_BUSINESS,
        category = "Electricity Bill",
        partyName = "DESCO",
        amount = 4500.0,
        dateFormatted = "20-11-2026",
        referenceNumber = "REF-99",
        editingId = "tx_1",
      )
    )
  }

  @Test
  fun employeeSummaries_calculateRemainingCashInHand() {
    val emp = OfficeEmployee(id = "e1", name = "Rahim")
    val entries = listOf(
      EmployeeLedgerEntry(
        id = "1",
        employeeId = "e1",
        entryType = EmployeeEntryType.COLLECTION.name,
        amount = 5000.0,
        dateMillis = 100L,
      ),
      EmployeeLedgerEntry(
        id = "2",
        employeeId = "e1",
        entryType = EmployeeEntryType.COLLECTION.name,
        amount = 2500.0,
        dateMillis = 200L,
      ),
      EmployeeLedgerEntry(
        id = "3",
        employeeId = "e1",
        entryType = EmployeeEntryType.DEPOSIT.name,
        amount = 6000.0,
        dateMillis = 300L,
      ),
    )

    val summaries = AccountingCalculator.computeEmployeeSummaries(listOf(emp), entries)
    assertEquals(1, summaries.size)
    assertEquals(7500.0, summaries[0].totalCollected, 0.001)
    assertEquals(6000.0, summaries[0].totalDeposited, 0.001)
    assertEquals(1500.0, summaries[0].remainingMoney, 0.001)
  }
}
