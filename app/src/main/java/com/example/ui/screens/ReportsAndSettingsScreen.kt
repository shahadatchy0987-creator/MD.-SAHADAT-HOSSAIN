package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReportDataset
import com.example.export.ReportExporter
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.Formatters
import com.example.model.AccountBalanceConfig
import com.example.model.AccountScope
import com.example.model.AuditLog
import com.example.model.PaymentMethod
import com.example.model.ReportType
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.WorkspaceMember
import com.example.ui.components.DatePickerDialogModal
import com.example.ui.theme.MonoAmountStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsAndSettingsScreen(
  lang: AppLang,
  isAdmin: Boolean,
  profile: UserProfile?,
  selectedDate: String,
  selectedMonth: String,
  personalAccounts: List<AccountBalanceConfig>,
  ispAccounts: List<AccountBalanceConfig>,
  workspaceMembers: List<WorkspaceMember>,
  auditLogs: List<AuditLog>,
  onSelectedDateChange: (String) -> Unit,
  onSelectedMonthChange: (String) -> Unit,
  onLanguageChange: (AppLang) -> Unit,
  onBuildReport: (ReportType) -> ReportDataset,
  onUpdateAccountOpening: (AccountScope, String, PaymentMethod, Double, String) -> Unit,
  onInviteMember: (String, UserRole, () -> Unit) -> Unit,
  onUpdateMemberRole: (String, UserRole) -> Unit,
  onBuildBackupJson: () -> String,
  onRestoreBackupJson: (String) -> Unit,
  onNotify: (String) -> Unit,
) {
  val context = LocalContext.current
  var subTab by remember { mutableIntStateOf(0) }
  var selectedReport by remember { mutableStateOf(ReportType.PROFIT_AND_LOSS) }
  var showDatePicker by remember { mutableStateOf(false) }

  val dataset = onBuildReport(selectedReport)

  val savePdfLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
      if (uri != null) {
        try {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            ReportExporter.writePdfToStream(dataset, out)
          }
          onNotify("PDF saved successfully")
        } catch (e: Exception) {
          onNotify("PDF export failed: ${e.localizedMessage}")
        }
      }
    }

  val saveExcelLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      )
    ) { uri ->
      if (uri != null) {
        try {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            ReportExporter.writeXlsxToStream(dataset, out)
          }
          onNotify("Excel (.xlsx) saved successfully")
        } catch (e: Exception) {
          onNotify("Excel export failed: ${e.localizedMessage}")
        }
      }
    }

  val exportBackupLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
      if (uri != null) {
        try {
          val json = onBuildBackupJson()
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
          }
          onNotify("Backup exported")
        } catch (e: Exception) {
          onNotify("Backup failed: ${e.localizedMessage}")
        }
      }
    }

  val restoreBackupLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.OpenDocument()
    ) { uri ->
      if (uri != null) {
        try {
          val content =
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
          if (!content.isNullOrBlank()) {
            onRestoreBackupJson(content)
          }
        } catch (e: Exception) {
          onNotify("Restore failed: ${e.localizedMessage}")
        }
      }
    }

  var editingAccount by remember { mutableStateOf<Pair<AccountScope, AccountBalanceConfig>?>(null) }
  var showInviteDialog by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text(
        text = AppStrings.tr(lang, "Reports & Settings", "রিপোর্ট ও সেটিংস"),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
    }

    item {
      TabRow(selectedTabIndex = subTab) {
        Tab(
          selected = subTab == 0,
          onClick = { subTab = 0 },
          text = { Text(AppStrings.navReports(lang)) },
          modifier = Modifier.testTag("tab_reports_view"),
        )
        Tab(
          selected = subTab == 1,
          onClick = { subTab = 1 },
          text = { Text(AppStrings.tr(lang, "Accounts & Settings", "অ্যাকাউন্ট ও সেটিংস")) },
          modifier = Modifier.testTag("tab_settings_view"),
        )
      }
    }

    if (subTab == 0) {
      // Report Type Chips (all 11 reports)
      item {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ReportType.entries.forEach { rt ->
            FilterChip(
              selected = selectedReport == rt,
              onClick = { selectedReport = rt },
              label = { Text(reportTitle(lang, rt)) },
              modifier = Modifier.testTag("report_chip_${rt.name}"),
            )
          }
        }
      }

      // Date / Month Controls
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(selectedDate)
          }
          OutlinedTextField(
            value = selectedMonth,
            onValueChange = onSelectedMonthChange,
            label = { Text(AppStrings.tr(lang, "Month (MM-YYYY)", "মাস (MM-YYYY)")) },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Export Buttons (PDF & Excel)
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = {
              val safe = selectedReport.name.lowercase()
              savePdfLauncher.launch("plusnet_${safe}_${selectedDate}.pdf")
            },
            modifier = Modifier.weight(1f).height(46.dp).testTag("export_pdf_btn"),
          ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("PDF")
          }
          Button(
            onClick = {
              val safe = selectedReport.name.lowercase()
              saveExcelLauncher.launch("plusnet_${safe}_${selectedDate}.xlsx")
            },
            modifier = Modifier.weight(1f).height(46.dp).testTag("export_excel_btn"),
          ) {
            Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Excel")
          }
          OutlinedButton(
            onClick = { ReportExporter.exportAndShare(context, dataset, isPdf = true) },
            modifier = Modifier.height(46.dp),
          ) {
            Icon(Icons.Default.Share, contentDescription = "Share PDF", modifier = Modifier.size(18.dp))
          }
        }
      }

      // Live Report Preview Card
      item {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = dataset.title,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = dataset.subtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            Column(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                dataset.headers.forEach { h ->
                  Text(
                    text = h,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(105.dp),
                  )
                }
              }
              HorizontalDivider()
              if (dataset.rows.isEmpty()) {
                Text(
                  text = AppStrings.tr(lang, "No entries in this report period.", "এই সময়ে কোনো তথ্য নেই।"),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(vertical = 8.dp),
                )
              } else {
                dataset.rows.take(50).forEach { row ->
                  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { cell ->
                      Text(
                        text = cell,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(105.dp),
                      )
                    }
                  }
                }
              }
            }

            HorizontalDivider()
            dataset.summaryLines.forEach { (k, v) ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(k, style = MaterialTheme.typography.titleMedium)
                Text(
                  v,
                  style = MonoAmountStyle.copy(fontSize = 15.sp),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    } else {
      // Language Setting
      item {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = AppStrings.tr(lang, "Language / ভাষা", "ভাষা / Language"),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = lang == AppLang.EN,
                onClick = { onLanguageChange(AppLang.EN) },
                label = { Text("English") },
              )
              FilterChip(
                selected = lang == AppLang.BN,
                onClick = { onLanguageChange(AppLang.BN) },
                label = { Text("বাংলা (Bengali)") },
              )
            }
          }
        }
      }

      // Cash & Bank Opening Balances (Personal & ISP Separated)
      if (isAdmin) {
        item {
          AccountSectionCard(
            title = AppStrings.tr(lang, "ISP Business Cash & Bank Accounts", "আইএসপি ব্যবসা ক্যাশ ও ব্যাংক"),
            accounts = ispAccounts,
            onEdit = { acc -> editingAccount = AccountScope.ISP_BUSINESS to acc },
          )
        }
        item {
          AccountSectionCard(
            title = AppStrings.tr(lang, "Personal Cash & Bank Accounts", "ব্যক্তিগত ক্যাশ ও ব্যাংক"),
            accounts = personalAccounts,
            onEdit = { acc -> editingAccount = AccountScope.PERSONAL to acc },
          )
        }
      }

      // Team Roles & Permissions
      item {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = AppStrings.tr(lang, "User Roles & Access", "ব্যবহারকারীর রোল ও অনুমতি"),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
              if (isAdmin) {
                TextButton(onClick = { showInviteDialog = true }) {
                  Text(AppStrings.tr(lang, "+ Invite", "+ আমন্ত্রণ"))
                }
              }
            }
            Text(
              text =
                AppStrings.tr(
                  lang,
                  "Your Role: ${profile?.role ?: "EMPLOYEE"} • Only Admins can delete financial transactions or access Personal accounts.",
                  "আপনার রোল: ${profile?.role ?: "EMPLOYEE"} • শুধুমাত্র অ্যাডমিন লেনদেন মুছতে ও ব্যক্তিগত হিসাব দেখতে পারেন।",
                ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            workspaceMembers.forEach { m ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(m.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                  Text(m.email, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isAdmin && m.uid != profile?.userId) {
                  OutlinedButton(
                    onClick = {
                      val next = if (m.role == "ADMIN") UserRole.EMPLOYEE else UserRole.ADMIN
                      onUpdateMemberRole(m.uid, next)
                    }
                  ) {
                    Text(m.role)
                  }
                } else {
                  Text(m.role, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }
      }

      // Data Backup & Restore + Cloud Sync
      item {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = AppStrings.tr(lang, "Cloud Backup & Restore", "ক্লাউড ব্যাকআপ ও পুনরুদ্ধার"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
            Text(
              text =
                AppStrings.tr(
                  lang,
                  "Real-time Cloud Firestore synchronization is active across devices. You can also export or restore a JSON backup anytime.",
                  "ক্লাউড ফায়ারস্টোরের মাধ্যমে আপনার ডেটা স্বয়ংক্রিয়ভাবে সংরক্ষিত ও সিঙ্ক হচ্ছে। চাইলে JSON ব্যাকআপ ডাউনলোড বা রিস্টোর করতে পারেন।",
                ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isAdmin) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Button(
                  onClick = {
                    exportBackupLauncher.launch("plusnet_backup_${Formatters.currentDateFormatted()}.json")
                  },
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(AppStrings.tr(lang, "Backup JSON", "ব্যাকআপ"))
                }
                OutlinedButton(
                  onClick = { restoreBackupLauncher.launch(arrayOf("application/json")) },
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(AppStrings.tr(lang, "Restore", "রিস্টোর"))
                }
              }
            }
          }
        }
      }

      // Recent Audit Logs
      item {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = AppStrings.tr(lang, "Audit Trail (${auditLogs.size})", "অডিট লগ (${auditLogs.size})"),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            if (auditLogs.isEmpty()) {
              Text(
                text = AppStrings.tr(lang, "All transaction edits and deletions are logged here.", "সকল পরিবর্তন ও মুছে ফেলা এখানে সংরক্ষিত থাকে।"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              auditLogs.take(15).forEach { log ->
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                  shape = MaterialTheme.shapes.small,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(modifier = Modifier.padding(8.dp)) {
                    Text("${log.action} • ${log.performedByName}", style = MaterialTheme.typography.labelLarge)
                    Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                  }
                }
              }
            }
          }
        }
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

  editingAccount?.let { (scope, acc) ->
    var balanceText by remember(acc) { mutableStateOf(acc.openingBalance.toString()) }
    var labelText by remember(acc) { mutableStateOf(acc.accountLabel) }
    AlertDialog(
      onDismissRequest = { editingAccount = null },
      title = { Text("${AppStrings.openingBalance(lang)} (${acc.method})") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = labelText,
            onValueChange = { labelText = it },
            label = { Text(AppStrings.tr(lang, "Account Name", "অ্যাকাউন্টের নাম")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = balanceText,
            onValueChange = { balanceText = it },
            label = { Text(AppStrings.amountLabel(lang)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val valDouble = balanceText.toDoubleOrNull() ?: 0.0
            onUpdateAccountOpening(scope, acc.id, acc.paymentMethod, valDouble, labelText)
            editingAccount = null
          }
        ) {
          Text(AppStrings.save(lang))
        }
      },
      dismissButton = {
        TextButton(onClick = { editingAccount = null }) { Text(AppStrings.cancel(lang)) }
      },
    )
  }

  if (showInviteDialog) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.EMPLOYEE) }
    AlertDialog(
      onDismissRequest = { showInviteDialog = false },
      title = { Text(AppStrings.tr(lang, "Invite Team Member", "সদস্য আমন্ত্রণ করুন")) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(AppStrings.tr(lang, "Google Email Address", "গুগল ইমেইল")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = role == UserRole.EMPLOYEE,
              onClick = { role = UserRole.EMPLOYEE },
              label = { Text(AppStrings.roleEmployee(lang)) },
            )
            FilterChip(
              selected = role == UserRole.ADMIN,
              onClick = { role = UserRole.ADMIN },
              label = { Text(AppStrings.roleAdmin(lang)) },
            )
          }
        }
      },
      confirmButton = {
        Button(
          enabled = email.contains("@"),
          onClick = { onInviteMember(email, role) { showInviteDialog = false } },
        ) {
          Text(AppStrings.tr(lang, "Send Invite", "পাঠান"))
        }
      },
      dismissButton = {
        TextButton(onClick = { showInviteDialog = false }) { Text(AppStrings.cancel(lang)) }
      },
    )
  }
}

@Composable
private fun AccountSectionCard(
  title: String,
  accounts: List<AccountBalanceConfig>,
  onEdit: (AccountBalanceConfig) -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      accounts.forEach { acc ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(acc.accountLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
              "Opening: ${Formatters.formatTaka(acc.openingBalance)}",
              style = MonoAmountStyle.copy(fontSize = 13.sp),
              color = MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(onClick = { onEdit(acc) }) {
            Icon(Icons.Default.Edit, contentDescription = "Edit opening balance")
          }
        }
      }
    }
  }
}

private fun reportTitle(lang: AppLang, type: ReportType): String =
  when (type) {
    ReportType.DAILY_INCOME -> AppStrings.tr(lang, "Daily Income", "দৈনিক আয়")
    ReportType.DAILY_EXPENSE -> AppStrings.tr(lang, "Daily Expense", "দৈনিক ব্যয়")
    ReportType.MONTHLY_INCOME -> AppStrings.tr(lang, "Monthly Income", "মাসিক আয়")
    ReportType.MONTHLY_EXPENSE -> AppStrings.tr(lang, "Monthly Expense", "মাসিক ব্যয়")
    ReportType.YEARLY_FINANCIAL -> AppStrings.tr(lang, "Yearly Report", "বার্ষিক রিপোর্ট")
    ReportType.CUSTOMER_COLLECTION -> AppStrings.tr(lang, "Customer Collection", "গ্রাহক আদায়")
    ReportType.CUSTOMER_DUE -> AppStrings.tr(lang, "Customer Due", "গ্রাহক বকেয়া")
    ReportType.IIG_CDN_CFAN -> AppStrings.tr(lang, "IIG/CDN/CFAN", "আইআইজি/সিডিএন/সিএফএএন")
    ReportType.EMPLOYEE_COLLECTION -> AppStrings.tr(lang, "Employee Collection", "কর্মচারী আদায়")
    ReportType.PROFIT_AND_LOSS -> AppStrings.tr(lang, "Profit & Loss", "লাভ ও ক্ষতি")
    ReportType.CASH_FLOW -> AppStrings.tr(lang, "Cash Flow", "নগদ প্রবাহ")
  }
