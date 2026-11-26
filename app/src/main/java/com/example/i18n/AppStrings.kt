package com.example.i18n

object AppStrings {
  fun tr(lang: AppLang, en: String, bn: String): String = if (lang == AppLang.BN) bn else en

  fun appTitle(lang: AppLang) = tr(lang, "Plusnet Accounts", "প্লাসনেট অ্যাকাউন্টস")

  // Navigation Tabs
  fun navDashboard(lang: AppLang) = tr(lang, "Dashboard", "ড্যাশবোর্ড")
  fun navLedger(lang: AppLang) = tr(lang, "Logs", "লেনদেন")
  fun navBilling(lang: AppLang) = tr(lang, "Billing", "বিলিং")
  fun navTeam(lang: AppLang) = tr(lang, "Team", "টিম")
  fun navReports(lang: AppLang) = tr(lang, "Reports", "রিপোর্ট")

  // Account Scopes
  fun scopeAll(lang: AppLang) = tr(lang, "Combined", "সমন্বিত")
  fun scopePersonal(lang: AppLang) = tr(lang, "Personal", "ব্যক্তিগত")
  fun scopeIsp(lang: AppLang) = tr(lang, "ISP Business", "আইএসপি ব্যবসা")

  // Dashboard Metrics
  fun totalIncome(lang: AppLang) = tr(lang, "Total Income", "মোট আয়")
  fun totalExpenses(lang: AppLang) = tr(lang, "Total Expenses", "মোট ব্যয়")
  fun cashBalance(lang: AppLang) = tr(lang, "Cash Balance", "নগদ ব্যালেন্স")
  fun bankBalance(lang: AppLang) = tr(lang, "Bank Balance", "ব্যাংক ব্যালেন্স")
  fun mobileBalance(lang: AppLang) = tr(lang, "Mobile Banking", "মোবাইল ব্যাংকিং")
  fun netProfit(lang: AppLang) = tr(lang, "Net Profit", "নিট লাভ")
  fun totalCustomerCollection(lang: AppLang) = tr(lang, "Customer Collection", "গ্রাহক আদায়")
  fun totalCustomerDue(lang: AppLang) = tr(lang, "Customer Due", "গ্রাহক বকেয়া")
  fun monthlyChartTitle(lang: AppLang) = tr(lang, "Monthly Income & Expense", "মাসিক আয় ও ব্যয়")
  fun recentTransactions(lang: AppLang) = tr(lang, "Recent Transactions", "সাম্প্রতিক লেনদেন")
  fun dailyBalanceSummary(lang: AppLang) = tr(lang, "Daily Opening & Closing", "দৈনিক প্রারম্ভিক ও সমাপনী")
  fun openingBalance(lang: AppLang) = tr(lang, "Opening Balance", "প্রারম্ভিক ব্যালেন্স")
  fun closingBalance(lang: AppLang) = tr(lang, "Closing Balance", "সমাপনী ব্যালেন্স")

  // Transaction Types & Methods
  fun income(lang: AppLang) = tr(lang, "Income", "আয়")
  fun expense(lang: AppLang) = tr(lang, "Expense", "ব্যয়")
  fun transfer(lang: AppLang) = tr(lang, "Cash / Bank Transfer", "নগদ / ব্যাংক ট্রান্সফার")
  fun cash(lang: AppLang) = tr(lang, "Cash", "নগদ")
  fun bank(lang: AppLang) = tr(lang, "Bank", "ব্যাংক")
  fun mobileBanking(lang: AppLang) = tr(lang, "Mobile Banking", "মোবাইল ব্যাংকিং")

  // Categories
  fun categoryLabel(lang: AppLang, key: String): String {
    if (lang == AppLang.EN) return key
    return when (key) {
      "Personal Income" -> "ব্যক্তিগত আয়"
      "Personal Expenses" -> "ব্যক্তিগত ব্যয়"
      "Other Income" -> "অন্যান্য আয়"
      "Other Expenses" -> "অন্যান্য ব্যয়"
      "IIG Income" -> "আইআইজি আয়"
      "CDN Income" -> "সিডিএন আয়"
      "CFAN Income" -> "সিএফএএন আয়"
      "Bandwidth Sales" -> "ব্যান্ডউইথ বিক্রয়"
      "Customer Bill Collection" -> "গ্রাহক বিল আদায়"
      "IIG Bill Payment" -> "আইআইজি বিল পরিশোধ"
      "CDN Bill Payment" -> "সিডিএন বিল পরিশোধ"
      "CFAN Bill Payment" -> "সিএফএএন বিল পরিশোধ"
      "Bandwidth Purchase" -> "ব্যান্ডউইথ ক্রয়"
      "Office Team Payment" -> "অফিস টিম পেমেন্ট"
      "Employee Salary" -> "কর্মচারী বেতন"
      "Office Rent" -> "অফিস ভাড়া"
      "Electricity Bill" -> "বিদ্যুৎ বিল"
      "Maintenance Cost" -> "রক্ষণাবেক্ষণ খরচ"
      "Cash / Bank Transfer" -> "নগদ / ব্যাংক ট্রান্সফার"
      else -> key
    }
  }

  // Forms
  fun addIncome(lang: AppLang) = tr(lang, "Add Income", "আয় যোগ করুন")
  fun addExpense(lang: AppLang) = tr(lang, "Add Expense", "ব্যয় যোগ করুন")
  fun addTransfer(lang: AppLang) = tr(lang, "Transfer", "ট্রান্সফার")
  fun editTransaction(lang: AppLang) = tr(lang, "Edit Transaction", "লেনদেন সম্পাদনা")
  fun dateLabel(lang: AppLang) = tr(lang, "Date (DD-MM-YYYY)", "তারিখ (DD-MM-YYYY)")
  fun accountTypeLabel(lang: AppLang) = tr(lang, "Account Type", "অ্যাকাউন্টের ধরন")
  fun categoryFieldLabel(lang: AppLang) = tr(lang, "Category", "বিভাগ")
  fun receivedFrom(lang: AppLang) = tr(lang, "Received From", "যার কাছ থেকে প্রাপ্ত")
  fun paidTo(lang: AppLang) = tr(lang, "Paid To", "যাকে পরিশোধ করা হয়েছে")
  fun amountLabel(lang: AppLang) = tr(lang, "Amount (৳)", "পরিমাণ (৳)")
  fun paymentMethodLabel(lang: AppLang) = tr(lang, "Payment Method", "পেমেন্ট মাধ্যম")
  fun fromAccount(lang: AppLang) = tr(lang, "From Account", "যে অ্যাকাউন্ট থেকে")
  fun toAccount(lang: AppLang) = tr(lang, "To Account", "যে অ্যাকাউন্টে")
  fun referenceNumber(lang: AppLang) = tr(lang, "Reference Number", "রেফারেন্স নম্বর")
  fun description(lang: AppLang) = tr(lang, "Description", "বিবরণ")
  fun save(lang: AppLang) = tr(lang, "Save", "সংরক্ষণ করুন")
  fun cancel(lang: AppLang) = tr(lang, "Cancel", "বাতিল")
  fun delete(lang: AppLang) = tr(lang, "Delete", "মুছে ফেলুন")
  fun edit(lang: AppLang) = tr(lang, "Edit", "সম্পাদনা")

  // Search & Search History
  fun searchTransactions(lang: AppLang) = tr(lang, "Search party, ref, category, ID...", "নাম, রেফারেন্স, বিভাগ বা আইডি খুঁজুন...")
  fun frequentSearches(lang: AppLang) = tr(lang, "Frequent Searches", "সর্বাধিক অনুসন্ধান")
  fun recentSearches(lang: AppLang) = tr(lang, "Recent Searches", "সাম্প্রতিক অনুসন্ধান")
  fun clearHistory(lang: AppLang) = tr(lang, "Clear", "মুছুন")

  // Roles & Confirmation
  fun roleAdmin(lang: AppLang) = tr(lang, "Admin", "অ্যাডমিন")
  fun roleEmployee(lang: AppLang) = tr(lang, "Employee", "কর্মচারী")
  fun confirmDeleteTitle(lang: AppLang) = tr(lang, "Confirm Deletion", "মুছে ফেলা নিশ্চিত করুন")
  fun confirmDeleteMsg(lang: AppLang) = tr(lang, "This transaction will be permanently deleted and recorded in the audit log. Continue?", "এই লেনদেনটি স্থায়ীভাবে মুছে ফেলা হবে এবং অডিট লগে সংরক্ষিত থাকবে। নিশ্চিত?")
  fun duplicateWarningTitle(lang: AppLang) = tr(lang, "Possible Duplicate Entry", "সম্ভাব্য ডুপ্লিকেট এন্ট্রি")
  fun duplicateWarningMsg(lang: AppLang) = tr(lang, "A matching transaction with the same date, party, category, and amount already exists. Save anyway?", "একই তারিখ, নাম, বিভাগ ও পরিমাণের একটি লেনদেন ইতিমধ্যে রয়েছে। তবুও সংরক্ষণ করবেন?")
  fun saveAnyway(lang: AppLang) = tr(lang, "Save Anyway", "তবুও সংরক্ষণ করুন")
}
