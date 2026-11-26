package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AccountingRepository
import com.example.data.AuthManager
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.LanguageStore
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import com.example.model.UiState
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BillingScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ReportsAndSettingsScreen
import com.example.ui.screens.TeamScreen
import com.example.ui.screens.TransactionEntrySheet
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.PlusnetAccountsTheme
import com.example.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      PlusnetAccountsTheme {
        AppRoot()
      }
    }
  }
}

private enum class MainTab {
  DASHBOARD,
  LOGS,
  BILLING,
  TEAM,
  REPORTS,
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
private fun AppRoot() {
  val context = LocalContext.current
  val languageStore = remember(context) { LanguageStore(context.applicationContext) }
  val lang by languageStore.languageFlow.collectAsStateWithLifecycle(initialValue = AppLang.EN)
  val scope = rememberCoroutineScope()

  val currentUser by
    Firebase.auth.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(
      lang = lang,
      onLanguageChange = { newLang -> scope.launch { languageStore.setLanguage(newLang) } },
      onAuthSuccess = {},
    )
  } else {
    key(user.uid) {
      AuthenticatedApp(
        currentUserId = user.uid,
        languageStore = languageStore,
      )
    }
  }
}

@Composable
private fun AuthenticatedApp(
  currentUserId: String,
  languageStore: LanguageStore,
) {
  val context = LocalContext.current
  val viewModel: MainViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            MainViewModel(
              repository = AccountingRepository(app),
              languageStore = languageStore,
              currentUserId = currentUserId,
            )
          }
        },
    )

  val lang by viewModel.language.collectAsStateWithLifecycle()
  val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
  val profile = (profileState as? UiState.Success)?.data
  val isAdmin = viewModel.isCurrentUserAdmin()

  val dashboardScope by viewModel.dashboardScope.collectAsStateWithLifecycle()
  val summary by viewModel.activeSummary.collectAsStateWithLifecycle()
  val customerCollection by viewModel.totalCustomerCollection.collectAsStateWithLifecycle()
  val customerDue by viewModel.totalCustomerDue.collectAsStateWithLifecycle()
  val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
  val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
  val chartPoints by viewModel.monthlyChartData.collectAsStateWithLifecycle()
  val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
  val filteredTransactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
  val logFilter by viewModel.logFilter.collectAsStateWithLifecycle()
  val frequentTerms by viewModel.frequentSearchTerms.collectAsStateWithLifecycle()
  val recentTerms by viewModel.recentSearchTerms.collectAsStateWithLifecycle()
  val personalAccounts by viewModel.personalAccounts.collectAsStateWithLifecycle()
  val ispAccounts by viewModel.ispAccounts.collectAsStateWithLifecycle()
  val customers by viewModel.customers.collectAsStateWithLifecycle()
  val bills by viewModel.customerBills.collectAsStateWithLifecycle()
  val payments by viewModel.billPayments.collectAsStateWithLifecycle()
  val employees by viewModel.employees.collectAsStateWithLifecycle()
  val employeeSummaries by viewModel.employeeSummaries.collectAsStateWithLifecycle()
  val employeeEntries by viewModel.employeeEntries.collectAsStateWithLifecycle()
  val workspaceMembers by viewModel.workspaceMembers.collectAsStateWithLifecycle()
  val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

  var activeTab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
  var showEntrySheet by remember { mutableStateOf(false) }
  var entryInitialType by remember { mutableStateOf(TransactionType.INCOME) }
  var editingRecord by remember { mutableStateOf<TransactionRecord?>(null) }

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  LaunchedEffect(Unit) {
    viewModel.uiMessages.collect { msg -> snackbarHostState.showSnackbar(msg) }
  }

  if (activeTab != MainTab.DASHBOARD) {
    BackHandler { activeTab = MainTab.DASHBOARD }
  }

  val navItems =
    listOf(
      Triple(MainTab.DASHBOARD, AppStrings.navDashboard(lang), Icons.Filled.Dashboard to Icons.Outlined.Dashboard),
      Triple(MainTab.LOGS, AppStrings.navLedger(lang), Icons.AutoMirrored.Filled.ReceiptLong to Icons.AutoMirrored.Outlined.ReceiptLong),
      Triple(MainTab.BILLING, AppStrings.navBilling(lang), Icons.Filled.Payments to Icons.Outlined.Payments),
      Triple(MainTab.TEAM, AppStrings.navTeam(lang), Icons.Filled.Groups to Icons.Outlined.Groups),
      Triple(MainTab.REPORTS, AppStrings.navReports(lang), Icons.Filled.Assessment to Icons.Outlined.Assessment),
    )

  if (profileState is UiState.Loading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      bottomBar = {
        if (!useRail) {
          NavigationBar {
            navItems.forEach { (tab, label, icons) ->
              val selected = activeTab == tab
              NavigationBarItem(
                selected = selected,
                onClick = { activeTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) icons.first else icons.second,
                    contentDescription = label,
                  )
                },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.testTag("nav_${tab.name}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail {
            navItems.forEach { (tab, label, icons) ->
              val selected = activeTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { activeTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) icons.first else icons.second,
                    contentDescription = label,
                  )
                },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.testTag("rail_${tab.name}"),
              )
            }
          }
        }

        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxSize()
              .padding(top = 8.dp),
          contentAlignment = Alignment.TopCenter,
        ) {
          Box(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
            when (activeTab) {
              MainTab.DASHBOARD ->
                DashboardScreen(
                  lang = lang,
                  profile = profile,
                  isAdmin = isAdmin,
                  scopeFilter = dashboardScope,
                  summary = summary,
                  customerCollection = customerCollection,
                  customerDue = customerDue,
                  selectedDate = selectedDate,
                  chartPoints = chartPoints,
                  recentTransactions = allTransactions,
                  onScopeChange = viewModel::setDashboardScope,
                  onSelectedDateChange = viewModel::setSelectedDate,
                  onLanguageToggle = {
                    val next = if (lang == AppLang.EN) AppLang.BN else AppLang.EN
                    viewModel.setLanguage(next)
                  },
                  onSignOut = { AuthManager.signOut(credentialManager, scope) },
                  onQuickAdd = { type ->
                    entryInitialType = type
                    editingRecord = null
                    showEntrySheet = true
                  },
                  onTransactionClick = { tx ->
                    editingRecord = tx
                    entryInitialType = tx.txType
                    showEntrySheet = true
                  },
                  onViewAllTransactions = { activeTab = MainTab.LOGS },
                )

              MainTab.LOGS ->
                TransactionsScreen(
                  lang = lang,
                  isAdmin = isAdmin,
                  filter = logFilter,
                  transactions = filteredTransactions,
                  frequentTerms = frequentTerms,
                  recentTerms = recentTerms,
                  auditLogs = auditLogs,
                  onQueryChange = viewModel::updateSearchQuery,
                  onSubmitSearch = viewModel::submitSearchTerm,
                  onDeleteSearchTerm = viewModel::deleteSearchTerm,
                  onClearSearchHistory = viewModel::clearSearchHistory,
                  onUpdateFilter = viewModel::updateLogFilter,
                  onAddTransaction = {
                    editingRecord = null
                    entryInitialType = TransactionType.INCOME
                    showEntrySheet = true
                  },
                  onEditTransaction = { tx ->
                    editingRecord = tx
                    entryInitialType = tx.txType
                    showEntrySheet = true
                  },
                  onDeleteTransaction = { tx -> viewModel.deleteTransaction(tx) },
                )

              MainTab.BILLING ->
                BillingScreen(
                  lang = lang,
                  customers = customers,
                  bills = bills,
                  payments = payments,
                  employees = employees,
                  totalCollection = customerCollection,
                  totalDue = customerDue,
                  onSaveCustomer = { code, name, phone, addr, pkg, fee, onDone ->
                    viewModel.saveCustomer(code, name, phone, addr, pkg, fee, onSuccess = onDone)
                  },
                  onCreateBill = { cust, month, mAmt, pDue, onDone ->
                    viewModel.createCustomerBill(cust, month, mAmt, pDue, onSuccess = onDone)
                  },
                  onRecordPayment = { bill, paidNow, dMillis, dFmt, method, emp, ref, note, onDone ->
                    viewModel.recordBillPayment(
                      bill,
                      paidNow,
                      dMillis,
                      dFmt,
                      method,
                      emp,
                      ref,
                      note,
                      onSuccess = onDone,
                    )
                  },
                )

              MainTab.TEAM ->
                TeamScreen(
                  lang = lang,
                  isAdmin = isAdmin,
                  employeeSummaries = employeeSummaries,
                  allEntries = employeeEntries,
                  onAddEmployee = { name, phone, desig, onDone ->
                    viewModel.saveEmployee(name, phone, desig, onSuccess = onDone)
                  },
                  onRecordEntry = { emp, type, amt, dMillis, dFmt, method, ref, cust, note, onDone ->
                    viewModel.recordEmployeeEntry(
                      emp,
                      type,
                      amt,
                      dMillis,
                      dFmt,
                      method,
                      ref,
                      cust,
                      note,
                      onSuccess = onDone,
                    )
                  },
                )

              MainTab.REPORTS ->
                ReportsAndSettingsScreen(
                  lang = lang,
                  isAdmin = isAdmin,
                  profile = profile,
                  selectedDate = selectedDate,
                  selectedMonth = selectedMonth,
                  personalAccounts = personalAccounts,
                  ispAccounts = ispAccounts,
                  workspaceMembers = workspaceMembers,
                  auditLogs = auditLogs,
                  onSelectedDateChange = viewModel::setSelectedDate,
                  onSelectedMonthChange = viewModel::setSelectedMonth,
                  onLanguageChange = viewModel::setLanguage,
                  onBuildReport = viewModel::buildReport,
                  onUpdateAccountOpening = viewModel::updateAccountOpeningBalance,
                  onInviteMember = viewModel::inviteMember,
                  onUpdateMemberRole = viewModel::updateMemberRole,
                  onBuildBackupJson = viewModel::buildBackupJson,
                  onRestoreBackupJson = viewModel::restoreBackup,
                  onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                )
            }
          }
        }
      }
    }
  }

  if (showEntrySheet) {
    TransactionEntrySheet(
      lang = lang,
      isAdmin = isAdmin,
      initialType = entryInitialType,
      editingRecord = editingRecord,
      onCheckDuplicate = viewModel::checkDuplicateTransaction,
      onSave = { scopeSel, type, category, party, amount, method, toMethod, ref, desc, dMillis, dFmt ->
        viewModel.saveTransaction(
          scope = scopeSel,
          type = type,
          category = category,
          partyName = party,
          amount = amount,
          paymentMethod = method,
          toPaymentMethod = toMethod,
          referenceNumber = ref,
          description = desc,
          dateMillis = dMillis,
          dateFormatted = dFmt,
          editingExisting = editingRecord,
          onSuccess = {
            showEntrySheet = false
            editingRecord = null
          },
        )
      },
      onDismiss = {
        showEntrySheet = false
        editingRecord = null
      },
    )
  }
}
