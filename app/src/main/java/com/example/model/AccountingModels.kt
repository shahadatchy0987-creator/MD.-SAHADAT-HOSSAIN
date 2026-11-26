package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

enum class AccountScope {
  PERSONAL,
  ISP_BUSINESS,
}

enum class DashboardScopeFilter {
  COMBINED,
  PERSONAL,
  ISP_BUSINESS,
}

enum class TransactionType {
  INCOME,
  EXPENSE,
  TRANSFER;

  companion object {
    fun safeValueOf(raw: String): TransactionType =
      entries.firstOrNull { it.name == raw } ?: INCOME
  }
}

enum class PaymentMethod {
  CASH,
  BANK,
  MOBILE_BANKING;

  companion object {
    fun safeValueOf(raw: String): PaymentMethod =
      entries.firstOrNull { it.name == raw } ?: CASH
  }
}

enum class UserRole {
  ADMIN,
  EMPLOYEE;

  companion object {
    fun safeValueOf(raw: String): UserRole =
      entries.firstOrNull { it.name == raw } ?: EMPLOYEE
  }
}

enum class BillStatus {
  UNPAID,
  PARTIAL,
  PAID;

  companion object {
    fun safeValueOf(raw: String): BillStatus =
      entries.firstOrNull { it.name == raw } ?: UNPAID
  }
}

enum class EmployeeEntryType {
  COLLECTION,
  DEPOSIT;

  companion object {
    fun safeValueOf(raw: String): EmployeeEntryType =
      entries.firstOrNull { it.name == raw } ?: COLLECTION
  }
}

enum class ReportType {
  DAILY_INCOME,
  DAILY_EXPENSE,
  MONTHLY_INCOME,
  MONTHLY_EXPENSE,
  YEARLY_FINANCIAL,
  CUSTOMER_COLLECTION,
  CUSTOMER_DUE,
  IIG_CDN_CFAN,
  EMPLOYEE_COLLECTION,
  PROFIT_AND_LOSS,
  CASH_FLOW,
}

object AccountCategories {
  val personalIncome = listOf("Personal Income", "Other Income")
  val personalExpense = listOf("Personal Expenses", "Other Expenses")

  val ispIncome =
    listOf(
      "IIG Income",
      "CDN Income",
      "CFAN Income",
      "Bandwidth Sales",
      "Customer Bill Collection",
      "Other Income",
    )

  val ispExpense =
    listOf(
      "IIG Bill Payment",
      "CDN Bill Payment",
      "CFAN Bill Payment",
      "Bandwidth Purchase",
      "Office Team Payment",
      "Employee Salary",
      "Office Rent",
      "Electricity Bill",
      "Maintenance Cost",
      "Other Expenses",
    )

  fun categoriesFor(scope: AccountScope, type: TransactionType): List<String> =
    when (type) {
      TransactionType.TRANSFER -> listOf("Cash / Bank Transfer")
      TransactionType.INCOME ->
        if (scope == AccountScope.PERSONAL) personalIncome else ispIncome
      TransactionType.EXPENSE ->
        if (scope == AccountScope.PERSONAL) personalExpense else ispExpense
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String = "",
  val displayName: String = "",
  val workspaceId: String = "",
  val role: String = UserRole.ADMIN.name,
  val preferredLanguage: String = "en",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  @get:Exclude
  val userRole: UserRole
    get() = UserRole.safeValueOf(role)
}

@Keep
@IgnoreExtraProperties
data class Workspace(
  @DocumentId val id: String = "",
  val name: String = "Plusnet ISP",
  val ownerUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class WorkspaceMember(
  @DocumentId val id: String = "",
  val uid: String = "",
  val email: String = "",
  val displayName: String = "",
  val role: String = UserRole.EMPLOYEE.name,
  val workspaceId: String = "",
  val workspaceMemberUids: List<String> = emptyList(),
  val inviteDocId: String? = null,
  @ServerTimestamp val joinedAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class TeamInvite(
  @DocumentId val id: String = "",
  val inviteEmail: String = "",
  val workspaceId: String = "",
  val workspaceName: String = "",
  val role: String = UserRole.EMPLOYEE.name,
  val invitedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class TransactionRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val workspaceId: String = "",
  val memberUids: List<String> = emptyList(),
  val type: String = TransactionType.INCOME.name,
  val category: String = "",
  val partyName: String = "",
  val amount: Double = 0.0,
  val paymentMethod: String = PaymentMethod.CASH.name,
  val toPaymentMethod: String? = null,
  val referenceNumber: String = "",
  val description: String = "",
  val dateMillis: Long = 0L,
  val dateFormatted: String = "",
  val sourceType: String? = null,
  val linkedBillId: String? = null,
  val linkedPaymentId: String? = null,
  val createdByUid: String = "",
  val createdByName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
  @get:Exclude val scope: AccountScope = AccountScope.ISP_BUSINESS,
) {
  @get:Exclude
  val txType: TransactionType
    get() = TransactionType.safeValueOf(type)

  @get:Exclude
  val method: PaymentMethod
    get() = PaymentMethod.safeValueOf(paymentMethod)

  @get:Exclude
  val targetMethod: PaymentMethod?
    get() = toPaymentMethod?.let { PaymentMethod.safeValueOf(it) }
}

@Keep
@IgnoreExtraProperties
data class AccountBalanceConfig(
  @DocumentId val id: String = "",
  val userId: String = "",
  val workspaceId: String = "",
  val memberUids: List<String> = emptyList(),
  val method: String = PaymentMethod.CASH.name,
  val openingBalance: Double = 0.0,
  val accountLabel: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  @get:Exclude
  val paymentMethod: PaymentMethod
    get() = PaymentMethod.safeValueOf(method)
}

@Keep
@IgnoreExtraProperties
data class SearchHistoryTerm(
  @DocumentId val id: String = "",
  val userId: String = "",
  val term: String = "",
  val searchCount: Long = 1L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val lastSearchedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class IspCustomer(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val memberUids: List<String> = emptyList(),
  val customerCode: String = "",
  val name: String = "",
  val phone: String = "",
  val address: String = "",
  val packageName: String = "",
  val monthlyFee: Double = 0.0,
  val active: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CustomerBill(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val memberUids: List<String> = emptyList(),
  val customerDocId: String = "",
  val customerCode: String = "",
  val customerName: String = "",
  val billingMonth: String = "",
  val monthlyBillAmount: Double = 0.0,
  val previousDue: Double = 0.0,
  val totalPayable: Double = 0.0,
  val paidAmount: Double = 0.0,
  val remainingDue: Double = 0.0,
  val status: String = BillStatus.UNPAID.name,
  val lastPaymentDate: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  @get:Exclude
  val billStatus: BillStatus
    get() = BillStatus.safeValueOf(status)
}

@Keep
@IgnoreExtraProperties
data class BillPayment(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val memberUids: List<String> = emptyList(),
  val billId: String = "",
  val customerDocId: String = "",
  val customerCode: String = "",
  val customerName: String = "",
  val billingMonth: String = "",
  val paidAmount: Double = 0.0,
  val paymentDateMillis: Long = 0L,
  val paymentDateFormatted: String = "",
  val paymentMethod: String = PaymentMethod.CASH.name,
  val collectedByEmployeeId: String = "",
  val collectedByEmployeeName: String = "",
  val referenceNumber: String = "",
  val note: String = "",
  val createdByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
) {
  @get:Exclude
  val method: PaymentMethod
    get() = PaymentMethod.safeValueOf(paymentMethod)
}

@Keep
@IgnoreExtraProperties
data class OfficeEmployee(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val memberUids: List<String> = emptyList(),
  val name: String = "",
  val phone: String = "",
  val designation: String = "",
  val active: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class EmployeeLedgerEntry(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val memberUids: List<String> = emptyList(),
  val employeeId: String = "",
  val employeeName: String = "",
  val entryType: String = EmployeeEntryType.COLLECTION.name,
  val amount: Double = 0.0,
  val dateMillis: Long = 0L,
  val dateFormatted: String = "",
  val paymentMethod: String = PaymentMethod.CASH.name,
  val referenceNumber: String = "",
  val customerName: String = "",
  val note: String = "",
  val createdByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
) {
  @get:Exclude
  val type: EmployeeEntryType
    get() = EmployeeEntryType.safeValueOf(entryType)

  @get:Exclude
  val method: PaymentMethod
    get() = PaymentMethod.safeValueOf(paymentMethod)
}

@Keep
@IgnoreExtraProperties
data class AuditLog(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val memberUids: List<String> = emptyList(),
  val entityType: String = "",
  val entityId: String = "",
  val action: String = "",
  val summary: String = "",
  val previousValues: String = "",
  val newValues: String = "",
  val performedByUid: String = "",
  val performedByName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)
