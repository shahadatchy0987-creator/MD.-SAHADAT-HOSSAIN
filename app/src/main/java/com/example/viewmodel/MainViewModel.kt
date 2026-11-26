package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountSummary
import com.example.data.AccountingCalculator
import com.example.data.AccountingRepository
import com.example.data.EmployeeSummary
import com.example.data.MonthlyChartPoint
import com.example.data.ReportDataset
import com.example.i18n.AppLang
import com.example.i18n.Formatters
import com.example.i18n.LanguageStore
import com.example.model.AccountBalanceConfig
import com.example.model.AccountScope
import com.example.model.AuditLog
import com.example.model.BillPayment
import com.example.model.CustomerBill
import com.example.model.DashboardScopeFilter
import com.example.model.EmployeeEntryType
import com.example.model.EmployeeLedgerEntry
import com.example.model.IspCustomer
import com.example.model.OfficeEmployee
import com.example.model.PaymentMethod
import com.example.model.ReportType
import com.example.model.SearchHistoryTerm
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.Workspace
import com.example.model.WorkspaceMember
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LogFilterState(
  val query: String = "",
  val scopeFilter: DashboardScopeFilter = DashboardScopeFilter.COMBINED,
  val txType: TransactionType? = null,
  val paymentMethod: PaymentMethod? = null,
  val category: String? = null,
  val dateFilter: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
  private val repository: AccountingRepository,
  private val languageStore: LanguageStore,
  val currentUserId: String,
) : ViewModel() {

  private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val uiMessages = _uiMessages.asSharedFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _bootstrapError = MutableStateFlow<String?>(null)
  val bootstrapError: StateFlow<String?> = _bootstrapError.asStateFlow()

  val language: StateFlow<AppLang> =
    languageStore.languageFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = AppLang.EN,
    )

  private val _dashboardScope = MutableStateFlow(DashboardScopeFilter.COMBINED)
  val dashboardScope: StateFlow<DashboardScopeFilter> = _dashboardScope.asStateFlow()

  private val _logFilter = MutableStateFlow(LogFilterState())
  val logFilter: StateFlow<LogFilterState> = _logFilter.asStateFlow()

  private val _selectedDate = MutableStateFlow(Formatters.currentDateFormatted())
  val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

  private val _selectedMonth = MutableStateFlow(Formatters.currentMonthFormatted())
  val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

  init {
    bootstrapSession()
  }

  fun bootstrapSession() {
    viewModelScope.launch {
      _bootstrapError.value = null
      repository.ensureUserProfileAndWorkspace().onFailure { e ->
        _bootstrapError.value = e.localizedMessage ?: "Failed to initialize workspace"
      }
    }
  }

  val userProfileState: StateFlow<UiState<UserProfile?>> =
    repository
      .observeUserProfile(currentUserId)
      .asUiState("Failed to load user profile")

  private val workspaceIdFlow: Flow<String> =
    userProfileState.map { state ->
      (state as? UiState.Success)?.data?.workspaceId.orEmpty()
    }

  val workspace: StateFlow<Workspace?> =
    workspaceIdFlow
      .flatMapLatest { wsId ->
        if (wsId.isBlank()) flowOf(null)
        else repository.observeWorkspace(wsId, currentUserId).catch { emit(null) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val workspaceMembers: StateFlow<List<WorkspaceMember>> =
    workspaceIdFlow
      .flatMapLatest { wsId ->
        if (wsId.isBlank()) flowOf(emptyList())
        else repository.observeWorkspaceMembers(wsId, currentUserId).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val personalTransactions: StateFlow<UiState<List<TransactionRecord>>> =
    repository
      .observePersonalTransactions(currentUserId)
      .asUiState("Failed to load personal transactions")

  val personalAccounts: StateFlow<List<AccountBalanceConfig>> =
    repository
      .observePersonalAccounts(currentUserId)
      .catch { e ->
        if (e is CancellationException && !hasFirestoreCause(e)) throw e
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val searchHistory: StateFlow<List<SearchHistoryTerm>> =
    repository
      .observeSearchHistory(currentUserId)
      .catch { e ->
        if (e is CancellationException && !hasFirestoreCause(e)) throw e
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val frequentSearchTerms: StateFlow<List<SearchHistoryTerm>> =
    searchHistory
      .map { list ->
        list
          .sortedWith(
            compareByDescending<SearchHistoryTerm> { it.searchCount }
              .thenByDescending { it.lastSearchedAt?.seconds ?: 0L }
          )
          .take(8)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val recentSearchTerms: StateFlow<List<SearchHistoryTerm>> =
    searchHistory
      .map { list ->
        list.sortedByDescending { it.lastSearchedAt?.seconds ?: 0L }.take(6)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val ispTransactions: StateFlow<UiState<List<TransactionRecord>>> =
    workspaceIdFlow
      .flatMapLatest { wsId ->
        if (wsId.isBlank()) emptyFlow()
        else repository.observeIspTransactions(wsId, currentUserId)
      }
      .asUiState("Failed to load ISP transactions")

  val ispAccounts: StateFlow<List<AccountBalanceConfig>> =
    workspaceIdFlow
      .flatMapLatest { wsId ->
        if (wsId.isBlank()) flowOf(emptyList())
        else repository.observeIspAccounts(wsId, currentUserId).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val customers: StateFlow<List<IspCustomer>> =
    workspaceIdFlow
      .flatMapLatest { wsId ->
        if (wsId.isBlank()) flowOf(emptyList())
        else repository.observeCustomers(wsId, currentUserId).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val customerBills: StateFlow<List<CustomerBill>> =
    workspaceIdFlow
      .flatMapLatest { wsId ->
        if (wsId.isBlank()) flowOf(emptyList())
        else repository.observeCustomerBills(wsId, currentUserId).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val billPayments: StateFlow<List<BillPayment>> =
    workspaceIdFlow
      .flatMapLatest { wsId ->
        if (wsId.isBlank()) flowOf(emptyList())
        else repository.observeBillPayments(wsId, currentUserId).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val employees: StateFlow<List<OfficeEmployee>> =
    workspaceIdFlow
      .flatMapLatest { wsId ->
        if (wsId.isBlank()) flowOf(emptyList())
        else repository.observeEmployees(wsId, currentUserId).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val employeeEntries: StateFlow<List<EmployeeLedgerEntry>> =
    workspaceIdFlow
      .flatMapLatest { wsId ->
        if (wsId.isBlank()) flowOf(emptyList())
        else repository.observeEmployeeEntries(wsId, currentUserId).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val auditLogs: StateFlow<List<AuditLog>> =
    workspaceIdFlow
      .flatMapLatest { wsId ->
        if (wsId.isBlank()) flowOf(emptyList())
        else repository.observeAuditLogs(wsId, currentUserId).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val personalSummary: StateFlow<AccountSummary> =
    combine(personalTransactions, personalAccounts, selectedDate) { txState, accs, date ->
      val txs = (txState as? UiState.Success)?.data.orEmpty()
      AccountingCalculator.computeSummary(txs, accs, date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AccountSummary())

  val ispSummary: StateFlow<AccountSummary> =
    combine(ispTransactions, ispAccounts, selectedDate) { txState, accs, date ->
      val txs = (txState as? UiState.Success)?.data.orEmpty()
      AccountingCalculator.computeSummary(txs, accs, date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AccountSummary())

  val activeSummary: StateFlow<AccountSummary> =
    combine(dashboardScope, personalSummary, ispSummary) { scope, p, isp ->
      when (scope) {
        DashboardScopeFilter.PERSONAL -> p
        DashboardScopeFilter.ISP_BUSINESS -> isp
        DashboardScopeFilter.COMBINED -> AccountingCalculator.combineSummaries(p, isp)
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AccountSummary())

  val allTransactions: StateFlow<List<TransactionRecord>> =
    combine(personalTransactions, ispTransactions, userProfileState) { pState, ispState, profState ->
      val profile = (profState as? UiState.Success)?.data
      val isAdmin = profile?.userRole == UserRole.ADMIN
      val pList = if (isAdmin) (pState as? UiState.Success)?.data.orEmpty() else emptyList()
      val iList = (ispState as? UiState.Success)?.data.orEmpty()
      (pList + iList).sortedByDescending { it.dateMillis }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val filteredTransactions: StateFlow<List<TransactionRecord>> =
    combine(allTransactions, logFilter) { list, filter ->
      val q = filter.query.trim().lowercase()
      list.filter { tx ->
        val scopeMatch =
          when (filter.scopeFilter) {
            DashboardScopeFilter.COMBINED -> true
            DashboardScopeFilter.PERSONAL -> tx.scope == AccountScope.PERSONAL
            DashboardScopeFilter.ISP_BUSINESS -> tx.scope == AccountScope.ISP_BUSINESS
          }
        val typeMatch = filter.txType == null || tx.txType == filter.txType
        val methodMatch = filter.paymentMethod == null || tx.method == filter.paymentMethod
        val catMatch = filter.category.isNullOrBlank() || tx.category == filter.category
        val dateMatch = filter.dateFilter.isBlank() || tx.dateFormatted == filter.dateFilter
        val queryMatch =
          q.isEmpty() ||
            tx.partyName.lowercase().contains(q) ||
            tx.category.lowercase().contains(q) ||
            tx.description.lowercase().contains(q) ||
            tx.referenceNumber.lowercase().contains(q) ||
            tx.id.lowercase().contains(q) ||
            tx.amount.toString().contains(q)
        scopeMatch && typeMatch && methodMatch && catMatch && dateMatch && queryMatch
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val monthlyChartData: StateFlow<List<MonthlyChartPoint>> =
    combine(dashboardScope, personalTransactions, ispTransactions, userProfileState) { scope, pState, iState, profState ->
      val profile = (profState as? UiState.Success)?.data
      val isAdmin = profile?.userRole == UserRole.ADMIN
      val p = if (isAdmin) (pState as? UiState.Success)?.data.orEmpty() else emptyList()
      val i = (iState as? UiState.Success)?.data.orEmpty()
      val txs =
        when (scope) {
          DashboardScopeFilter.PERSONAL -> p
          DashboardScopeFilter.ISP_BUSINESS -> i
          DashboardScopeFilter.COMBINED -> p + i
        }
      AccountingCalculator.computeMonthlyChart(txs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val totalCustomerCollection: StateFlow<Double> =
    billPayments
      .map { list -> list.sumOf { it.paidAmount } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0.0)

  val totalCustomerDue: StateFlow<Double> =
    customerBills
      .map { list -> list.sumOf { it.remainingDue } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0.0)

  val employeeSummaries: StateFlow<List<EmployeeSummary>> =
    combine(employees, employeeEntries) { emps, entries ->
      AccountingCalculator.computeEmployeeSummaries(emps, entries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  // --- Actions ---
  fun setLanguage(lang: AppLang) {
    viewModelScope.launch { languageStore.setLanguage(lang) }
  }

  fun setDashboardScope(scope: DashboardScopeFilter) {
    _dashboardScope.value = scope
  }

  fun setSelectedDate(date: String) {
    _selectedDate.value = date
  }

  fun setSelectedMonth(month: String) {
    _selectedMonth.value = month
  }

  fun updateSearchQuery(query: String) {
    _logFilter.value = _logFilter.value.copy(query = query)
  }

  fun submitSearchTerm(term: String) {
    val clean = term.trim()
    _logFilter.value = _logFilter.value.copy(query = clean)
    if (clean.length >= 2) {
      viewModelScope.launch { repository.recordSearchQuery(clean) }
    }
  }

  fun deleteSearchTerm(termId: String) {
    viewModelScope.launch { repository.deleteSearchTerm(termId) }
  }

  fun clearSearchHistory() {
    viewModelScope.launch { repository.clearSearchHistory() }
  }

  fun updateLogFilter(transform: (LogFilterState) -> LogFilterState) {
    _logFilter.value = transform(_logFilter.value)
  }

  private fun currentWorkspaceContext(): Triple<String, List<String>, String> {
    val prof = (userProfileState.value as? UiState.Success)?.data
    val wsId = prof?.workspaceId?.takeIf { it.isNotBlank() } ?: "ws_$currentUserId"
    val members = workspace.value?.memberUids?.takeIf { it.isNotEmpty() } ?: listOf(currentUserId)
    val userName = prof?.displayName?.ifBlank { "User" } ?: "User"
    return Triple(wsId, members, userName)
  }

  fun isCurrentUserAdmin(): Boolean {
    val prof = (userProfileState.value as? UiState.Success)?.data ?: return false
    return prof.userRole == UserRole.ADMIN
  }

  fun checkDuplicateTransaction(
    scope: AccountScope,
    type: TransactionType,
    category: String,
    partyName: String,
    amount: Double,
    dateFormatted: String,
    referenceNumber: String,
    editingId: String?,
  ): Boolean {
    return AccountingCalculator.isPotentialDuplicate(
      existing = allTransactions.value,
      type = type,
      scope = scope,
      category = category,
      partyName = partyName,
      amount = amount,
      dateFormatted = dateFormatted,
      referenceNumber = referenceNumber,
      editingId = editingId,
    )
  }

  fun saveTransaction(
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
    editingExisting: TransactionRecord?,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val (wsId, members, userName) = currentWorkspaceContext()
      val res =
        repository.saveTransaction(
          scope = scope,
          workspaceId = wsId,
          memberUids = members,
          type = type,
          category = category,
          partyName = partyName,
          amount = amount,
          paymentMethod = paymentMethod,
          toPaymentMethod = toPaymentMethod,
          referenceNumber = referenceNumber,
          description = description,
          dateMillis = dateMillis,
          dateFormatted = dateFormatted,
          editingExisting = editingExisting,
          userName = userName,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _uiMessages.tryEmit("Transaction saved")
          onSuccess()
        }
        .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Failed to save transaction") }
    }
  }

  fun deleteTransaction(tx: TransactionRecord, onDeleted: () -> Unit = {}) {
    viewModelScope.launch {
      _isBusy.value = true
      val (wsId, members, userName) = currentWorkspaceContext()
      val res = repository.deleteTransaction(tx, wsId, members, userName)
      _isBusy.value = false
      res
        .onSuccess {
          _uiMessages.tryEmit("Transaction deleted")
          onDeleted()
        }
        .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Failed to delete transaction") }
    }
  }

  fun updateAccountOpeningBalance(
    scope: AccountScope,
    accountId: String,
    method: PaymentMethod,
    openingBalance: Double,
    label: String,
  ) {
    viewModelScope.launch {
      val (wsId, members, _) = currentWorkspaceContext()
      repository
        .updateAccountOpeningBalance(scope, wsId, members, accountId, method, openingBalance, label)
        .onSuccess { _uiMessages.tryEmit("Opening balance updated") }
        .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Update failed") }
    }
  }

  fun saveCustomer(
    customerCode: String,
    name: String,
    phone: String,
    address: String,
    packageName: String,
    monthlyFee: Double,
    existingId: String? = null,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val (wsId, members, _) = currentWorkspaceContext()
      val res =
        repository.saveCustomer(
          workspaceId = wsId,
          memberUids = members,
          customerCode = customerCode,
          name = name,
          phone = phone,
          address = address,
          packageName = packageName,
          monthlyFee = monthlyFee,
          existingId = existingId,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _uiMessages.tryEmit("Customer saved")
          onSuccess()
        }
        .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Failed to save customer") }
    }
  }

  fun createCustomerBill(
    customer: IspCustomer,
    billingMonth: String,
    monthlyBillAmount: Double,
    previousDue: Double,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val (wsId, members, _) = currentWorkspaceContext()
      val res =
        repository.createCustomerBill(
          workspaceId = wsId,
          memberUids = members,
          customer = customer,
          billingMonth = billingMonth,
          monthlyBillAmount = monthlyBillAmount,
          previousDue = previousDue,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _uiMessages.tryEmit("Monthly bill saved")
          onSuccess()
        }
        .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Failed to create bill") }
    }
  }

  fun recordBillPayment(
    bill: CustomerBill,
    paidNow: Double,
    paymentDateMillis: Long,
    paymentDateFormatted: String,
    paymentMethod: PaymentMethod,
    collectedByEmployee: OfficeEmployee?,
    referenceNumber: String,
    note: String,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val (wsId, members, userName) = currentWorkspaceContext()
      val res =
        repository.recordBillPayment(
          workspaceId = wsId,
          memberUids = members,
          bill = bill,
          paidNow = paidNow,
          paymentDateMillis = paymentDateMillis,
          paymentDateFormatted = paymentDateFormatted,
          paymentMethod = paymentMethod,
          collectedByEmployee = collectedByEmployee,
          referenceNumber = referenceNumber,
          note = note,
          userName = userName,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _uiMessages.tryEmit("Payment recorded")
          onSuccess()
        }
        .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Failed to record payment") }
    }
  }

  fun saveEmployee(
    name: String,
    phone: String,
    designation: String,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val (wsId, members, _) = currentWorkspaceContext()
      val res = repository.saveEmployee(wsId, members, name, phone, designation)
      _isBusy.value = false
      res
        .onSuccess {
          _uiMessages.tryEmit("Team member added")
          onSuccess()
        }
        .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Failed to add team member") }
    }
  }

  fun recordEmployeeEntry(
    employee: OfficeEmployee,
    entryType: EmployeeEntryType,
    amount: Double,
    dateMillis: Long,
    dateFormatted: String,
    paymentMethod: PaymentMethod,
    referenceNumber: String,
    customerName: String,
    note: String,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val (wsId, members, _) = currentWorkspaceContext()
      val res =
        repository.recordEmployeeEntry(
          workspaceId = wsId,
          memberUids = members,
          employee = employee,
          entryType = entryType,
          amount = amount,
          dateMillis = dateMillis,
          dateFormatted = dateFormatted,
          paymentMethod = paymentMethod,
          referenceNumber = referenceNumber,
          customerName = customerName,
          note = note,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _uiMessages.tryEmit("Entry saved")
          onSuccess()
        }
        .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Failed to save entry") }
    }
  }

  fun inviteMember(email: String, role: UserRole, onSuccess: () -> Unit) {
    viewModelScope.launch {
      val (wsId, _, _) = currentWorkspaceContext()
      val wsName = workspace.value?.name ?: "Plusnet ISP"
      repository
        .inviteWorkspaceMember(wsId, wsName, email, role)
        .onSuccess {
          _uiMessages.tryEmit("Invitation sent for $email")
          onSuccess()
        }
        .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Invite failed") }
    }
  }

  fun updateMemberRole(memberUid: String, newRole: UserRole) {
    viewModelScope.launch {
      val (wsId, _, _) = currentWorkspaceContext()
      repository
        .updateMemberRole(wsId, memberUid, newRole)
        .onSuccess { _uiMessages.tryEmit("Role updated") }
        .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Role update failed") }
    }
  }

  fun buildReport(type: ReportType): ReportDataset {
    val lang = language.value
    val scope = if (isCurrentUserAdmin()) dashboardScope.value else DashboardScopeFilter.ISP_BUSINESS
    val date = selectedDate.value
    val month = selectedMonth.value
    val year = if (date.length == 10) date.substring(6, 10) else "2026"
    return AccountingCalculator.generateReport(
      type = type,
      lang = lang,
      scopeFilter = scope,
      selectedDate = date,
      selectedMonth = month,
      selectedYear = year,
      personalTxs =
        if (isCurrentUserAdmin()) (personalTransactions.value as? UiState.Success)?.data.orEmpty()
        else emptyList(),
      ispTxs = (ispTransactions.value as? UiState.Success)?.data.orEmpty(),
      personalAccounts = if (isCurrentUserAdmin()) personalAccounts.value else emptyList(),
      ispAccounts = ispAccounts.value,
      bills = customerBills.value,
      payments = billPayments.value,
      employees = employees.value,
      employeeEntries = employeeEntries.value,
    )
  }

  fun buildBackupJson(): String {
    val pTxs = (personalTransactions.value as? UiState.Success)?.data.orEmpty()
    val iTxs = (ispTransactions.value as? UiState.Success)?.data.orEmpty()
    return repository.buildBackupJson(
      personalTxs = pTxs,
      ispTxs = iTxs,
      customers = customers.value,
      bills = customerBills.value,
      employees = employees.value,
    )
  }

  fun restoreBackup(jsonText: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val (wsId, members, userName) = currentWorkspaceContext()
      val res = repository.restoreTransactionsFromBackup(jsonText, wsId, members, userName)
      _isBusy.value = false
      res
        .onSuccess { count -> _uiMessages.tryEmit("Restored $count records") }
        .onFailure { _uiMessages.tryEmit(it.localizedMessage ?: "Restore failed") }
    }
  }

  private fun hasFirestoreCause(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { it is FirebaseFirestoreException }

  private fun <T> Flow<T>.asUiState(fallbackMsg: String): StateFlow<UiState<T>> =
    this.map<T, UiState<T>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        emit(UiState.Error(root.message ?: fallbackMsg))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )
}
