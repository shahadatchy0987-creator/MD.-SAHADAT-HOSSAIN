package com.example.data

import android.content.Context
import com.example.R
import com.example.i18n.Formatters
import com.example.model.AccountBalanceConfig
import com.example.model.AccountScope
import com.example.model.AuditLog
import com.example.model.BillPayment
import com.example.model.CustomerBill
import com.example.model.EmployeeEntryType
import com.example.model.EmployeeLedgerEntry
import com.example.model.IspCustomer
import com.example.model.OfficeEmployee
import com.example.model.PaymentMethod
import com.example.model.SearchHistoryTerm
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.Workspace
import com.example.model.WorkspaceMember
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class AccountingRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUser(): FirebaseUser =
    auth.currentUser
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  private fun requireUserId(): String = requireUser().uid

  suspend fun ensureUserProfileAndWorkspace(): Result<UserProfile> {
    val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
    val uid = user.uid
    val userPath = "users/$uid"
    return try {
      val userSnap = db.collection("users").document(uid).get().await()
      if (userSnap.exists()) {
        val profile =
          userSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: UserProfile(
              userId = uid,
              email = user.email.orEmpty(),
              displayName = user.displayName ?: "Account User",
              workspaceId = "ws_$uid",
              role = UserRole.ADMIN.name,
            )
        ensureDefaultAccounts(uid, profile.workspaceId, listOf(uid))
        return Result.success(profile)
      }

      val email = (user.email ?: "").trim().lowercase(Locale.US)
      val displayName =
        user.displayName?.takeIf { it.isNotBlank() }
          ?: email.substringBefore("@").ifBlank { "Plusnet User" }

      // Check for team invite if email is verified
      var targetWorkspaceId = "ws_$uid"
      var targetRole = UserRole.ADMIN.name
      var inviteDocId: String? = null

      if (email.isNotBlank() && user.isEmailVerified) {
        val invites =
          db.collection("team_invites")
            .whereEqualTo("inviteEmail", email)
            .limit(1)
            .get()
            .await()
        val inviteSnap = invites.documents.firstOrNull()
        if (inviteSnap != null) {
          val wsId = inviteSnap.getString("workspaceId")
          val invitedRole = inviteSnap.getString("role")
          if (!wsId.isNullOrBlank() && invitedRole in listOf("ADMIN", "EMPLOYEE")) {
            targetWorkspaceId = wsId
            targetRole = invitedRole!!
            inviteDocId = inviteSnap.id
          }
        }
      }

      val batch = db.batch()
      if (inviteDocId != null) {
        batch.update(
          db.collection("workspaces").document(targetWorkspaceId),
          mapOf(
            "memberUids" to FieldValue.arrayUnion(uid),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.set(
          db.collection("workspaces")
            .document(targetWorkspaceId)
            .collection("members")
            .document(uid),
          mapOf(
            "uid" to uid,
            "email" to email.ifBlank { "user@plusnet.local" },
            "displayName" to displayName.take(120),
            "role" to targetRole,
            "workspaceId" to targetWorkspaceId,
            "workspaceMemberUids" to listOf(uid),
            "inviteDocId" to inviteDocId,
            "joinedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        val wsRef = db.collection("workspaces").document(targetWorkspaceId)
        batch.set(
          wsRef,
          mapOf(
            "name" to "Plusnet ISP",
            "ownerUid" to uid,
            "memberUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.set(
          wsRef.collection("members").document(uid),
          mapOf(
            "uid" to uid,
            "email" to email.ifBlank { "user@plusnet.local" },
            "displayName" to displayName.take(120),
            "role" to UserRole.ADMIN.name,
            "workspaceId" to targetWorkspaceId,
            "workspaceMemberUids" to listOf(uid),
            "joinedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.set(
        db.collection("users").document(uid),
        mapOf(
          "userId" to uid,
          "email" to email.ifBlank { "user@plusnet.local" },
          "displayName" to displayName.take(120),
          "workspaceId" to targetWorkspaceId,
          "role" to targetRole,
          "preferredLanguage" to "en",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()

      ensureDefaultAccounts(uid, targetWorkspaceId, listOf(uid))
      val createdSnap = db.collection("users").document(uid).get().await()
      val createdProfile =
        createdSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          ?: UserProfile(
            userId = uid,
            email = email,
            displayName = displayName,
            workspaceId = targetWorkspaceId,
            role = targetRole,
          )
      Result.success(createdProfile)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userPath)
      Result.failure(e)
    }
  }

  private suspend fun ensureDefaultAccounts(
    uid: String,
    workspaceId: String,
    memberUids: List<String>,
  ) {
    val personalCol = db.collection("users").document(uid).collection("personal_accounts")
    val personalExisting = personalCol.get().await()
    if (personalExisting.isEmpty) {
      val batch = db.batch()
      listOf(
        Triple("acc_cash", PaymentMethod.CASH.name, "Personal Cash"),
        Triple("acc_bank", PaymentMethod.BANK.name, "Personal Bank"),
        Triple("acc_mobile", PaymentMethod.MOBILE_BANKING.name, "Personal bKash/Nagad"),
      ).forEach { (docId, method, label) ->
        batch.set(
          personalCol.document(docId),
          mapOf(
            "userId" to uid,
            "method" to method,
            "openingBalance" to 0.0,
            "accountLabel" to label,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
    }

    val ispCol = db.collection("workspaces").document(workspaceId).collection("isp_accounts")
    val ispExisting = ispCol.whereArrayContains("memberUids", uid).get().await()
    if (ispExisting.isEmpty) {
      val memberSnap =
        db.collection("workspaces").document(workspaceId).collection("members").document(uid).get().await()
      if (memberSnap.getString("role") == UserRole.ADMIN.name) {
        val batch = db.batch()
        listOf(
          Triple("isp_cash", PaymentMethod.CASH.name, "Office Cash Drawer"),
          Triple("isp_bank", PaymentMethod.BANK.name, "ISP Business Bank"),
          Triple("isp_mobile", PaymentMethod.MOBILE_BANKING.name, "ISP Merchant Mobile"),
        ).forEach { (docId, method, label) ->
          batch.set(
            ispCol.document(docId),
            mapOf(
              "workspaceId" to workspaceId,
              "memberUids" to memberUids,
              "method" to method,
              "openingBalance" to 0.0,
              "accountLabel" to label,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
        batch.commit().await()
      }
    }
  }

  // --- Observations ---
  fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
    val path = "users/$uid"
    emitAll(
      db.collection("users")
        .document(uid)
        .snapshots()
        .map { snap ->
          snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { e -> handleStreamError(e, OperationType.GET, path, uid) }
    )
  }

  fun observeWorkspace(workspaceId: String, uid: String): Flow<Workspace?> = flow {
    val path = "workspaces/$workspaceId"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .snapshots()
        .map { snap ->
          snap.toObject(Workspace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { e -> handleStreamError(e, OperationType.GET, path, uid) }
    )
  }

  fun observeWorkspaceMembers(workspaceId: String, uid: String): Flow<List<WorkspaceMember>> = flow {
    val path = "workspaces/$workspaceId/members"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("members")
        .whereArrayContains("workspaceMemberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(WorkspaceMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  fun observePersonalTransactions(uid: String): Flow<List<TransactionRecord>> = flow {
    val path = "users/$uid/personal_transactions"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("personal_transactions")
        .orderBy("dateMillis", Query.Direction.DESCENDING)
        .snapshots()
        .map { snap ->
          snap.toObjects(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .map { it.copy(scope = AccountScope.PERSONAL) }
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  fun observePersonalAccounts(uid: String): Flow<List<AccountBalanceConfig>> = flow {
    val path = "users/$uid/personal_accounts"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("personal_accounts")
        .snapshots()
        .map { snap ->
          snap.toObjects(AccountBalanceConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  fun observeSearchHistory(uid: String): Flow<List<SearchHistoryTerm>> = flow {
    val path = "users/$uid/search_history"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("search_history")
        .snapshots()
        .map { snap ->
          snap.toObjects(SearchHistoryTerm::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(
              compareByDescending<SearchHistoryTerm> { it.searchCount }
                .thenByDescending { it.lastSearchedAt?.seconds ?: 0L }
            )
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  fun observeIspTransactions(workspaceId: String, uid: String): Flow<List<TransactionRecord>> = flow {
    val path = "workspaces/$workspaceId/isp_transactions"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("isp_transactions")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .map { it.copy(scope = AccountScope.ISP_BUSINESS) }
            .sortedByDescending { it.dateMillis }
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  fun observeIspAccounts(workspaceId: String, uid: String): Flow<List<AccountBalanceConfig>> = flow {
    val path = "workspaces/$workspaceId/isp_accounts"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("isp_accounts")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(AccountBalanceConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  fun observeCustomers(workspaceId: String, uid: String): Flow<List<IspCustomer>> = flow {
    val path = "workspaces/$workspaceId/customers"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("customers")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(IspCustomer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.name.lowercase(Locale.US) }
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  fun observeCustomerBills(workspaceId: String, uid: String): Flow<List<CustomerBill>> = flow {
    val path = "workspaces/$workspaceId/customer_bills"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("customer_bills")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(CustomerBill::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.billingMonth }
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  fun observeBillPayments(workspaceId: String, uid: String): Flow<List<BillPayment>> = flow {
    val path = "workspaces/$workspaceId/bill_payments"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("bill_payments")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(BillPayment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.paymentDateMillis }
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  fun observeEmployees(workspaceId: String, uid: String): Flow<List<OfficeEmployee>> = flow {
    val path = "workspaces/$workspaceId/employees"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("employees")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(OfficeEmployee::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.name.lowercase(Locale.US) }
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  fun observeEmployeeEntries(workspaceId: String, uid: String): Flow<List<EmployeeLedgerEntry>> = flow {
    val path = "workspaces/$workspaceId/employee_entries"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("employee_entries")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(EmployeeLedgerEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.dateMillis }
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  fun observeAuditLogs(workspaceId: String, uid: String): Flow<List<AuditLog>> = flow {
    val path = "workspaces/$workspaceId/audit_logs"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("audit_logs")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { e -> handleStreamError(e, OperationType.LIST, path, uid) }
    )
  }

  private fun handleStreamError(
    error: Throwable,
    op: OperationType,
    path: String,
    uid: String,
  ): Nothing {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val ex = firestoreError ?: (error as? Exception)
    if (ex != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(ex, op, path)
    }
    throw error
  }

  // --- Search History ---
  suspend fun recordSearchQuery(rawTerm: String): Result<Unit> {
    val term = rawTerm.trim().take(100)
    if (term.isBlank()) return Result.success(Unit)
    var docPath = "users/unknown/search_history"
    return try {
      val uid = requireUserId()
      val termId = "term_${sha256Short(term.lowercase(Locale.US))}"
      val docRef =
        db.collection("users").document(uid).collection("search_history").document(termId)
      docPath = docRef.path
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef
          .update(
            mapOf(
              "searchCount" to FieldValue.increment(1L),
              "lastSearchedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "term" to term,
              "searchCount" to 1L,
              "createdAt" to FieldValue.serverTimestamp(),
              "lastSearchedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteSearchTerm(termId: String): Result<Unit> {
    var docPath = "users/unknown/search_history/$termId"
    return try {
      val uid = requireUserId()
      val docRef = db.collection("users").document(uid).collection("search_history").document(termId)
      docPath = docRef.path
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun clearSearchHistory(): Result<Unit> {
    var path = "users/unknown/search_history"
    return try {
      val uid = requireUserId()
      val colRef = db.collection("users").document(uid).collection("search_history")
      path = colRef.path
      val docs = colRef.get().await()
      if (!docs.isEmpty) {
        val batch = db.batch()
        docs.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  // --- Transactions (Income, Expense, Transfer) + Audit Logs ---
  suspend fun saveTransaction(
    scope: AccountScope,
    workspaceId: String,
    memberUids: List<String>,
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
    editingExisting: TransactionRecord? = null,
    userName: String = "",
  ): Result<Unit> {
    var docPath = "transactions"
    return try {
      val uid = requireUserId()
      require(amount > 0.0) { "Amount must be greater than zero." }
      require(partyName.isNotBlank()) { "Party name is required." }
      require(category.isNotBlank()) { "Category is required." }
      if (type == TransactionType.TRANSFER) {
        require(toPaymentMethod != null && toPaymentMethod != paymentMethod) {
          "Transfer destination must differ from source."
        }
      }
      val safeMembers = (memberUids + uid).distinct().take(25)
      val colRef =
        if (scope == AccountScope.PERSONAL) {
          db.collection("users").document(uid).collection("personal_transactions")
        } else {
          db.collection("workspaces").document(workspaceId).collection("isp_transactions")
        }

      val batch = db.batch()
      if (editingExisting == null) {
        val docRef = colRef.document()
        docPath = docRef.path
        val payload =
          buildMap<String, Any> {
            if (scope == AccountScope.PERSONAL) {
              put("userId", uid)
            } else {
              put("workspaceId", workspaceId)
              put("memberUids", safeMembers)
            }
            put("type", type.name)
            put("category", category.take(80))
            put("partyName", partyName.trim().take(160))
            put("amount", amount)
            put("paymentMethod", paymentMethod.name)
            if (type == TransactionType.TRANSFER && toPaymentMethod != null) {
              put("toPaymentMethod", toPaymentMethod.name)
            }
            put("referenceNumber", referenceNumber.trim().take(80))
            put("description", description.trim().take(500))
            put("dateMillis", dateMillis)
            put("dateFormatted", dateFormatted)
            put("createdByUid", uid)
            put("createdByName", userName.ifBlank { "User" }.take(120))
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
          }
        batch.set(docRef, payload)

        if (workspaceId.isNotBlank()) {
          val logRef =
            db.collection("workspaces").document(workspaceId).collection("audit_logs").document()
          batch.set(
            logRef,
            mapOf(
              "workspaceId" to workspaceId,
              "memberUids" to safeMembers,
              "entityType" to if (scope == AccountScope.PERSONAL) "PERSONAL_TX" else "ISP_TX",
              "entityId" to docRef.id,
              "action" to "CREATE",
              "summary" to "Created ${type.name}: ${partyName.trim()} (${Formatters.formatTaka(amount)})",
              "newValues" to "$category | $dateFormatted | $paymentMethod",
              "performedByUid" to uid,
              "performedByName" to userName.ifBlank { "User" }.take(120),
              "createdAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      } else {
        val docRef = colRef.document(editingExisting.id)
        docPath = docRef.path
        val updateMap =
          buildMap<String, Any> {
            put("type", type.name)
            put("category", category.take(80))
            put("partyName", partyName.trim().take(160))
            put("amount", amount)
            put("paymentMethod", paymentMethod.name)
            if (type == TransactionType.TRANSFER && toPaymentMethod != null) {
              put("toPaymentMethod", toPaymentMethod.name)
            }
            put("referenceNumber", referenceNumber.trim().take(80))
            put("description", description.trim().take(500))
            put("dateMillis", dateMillis)
            put("dateFormatted", dateFormatted)
            if (scope == AccountScope.ISP_BUSINESS) {
              put("memberUids", safeMembers)
            }
            put("updatedAt", FieldValue.serverTimestamp())
          }
        batch.update(docRef, updateMap)

        if (workspaceId.isNotBlank()) {
          val logRef =
            db.collection("workspaces").document(workspaceId).collection("audit_logs").document()
          batch.set(
            logRef,
            mapOf(
              "workspaceId" to workspaceId,
              "memberUids" to safeMembers,
              "entityType" to if (scope == AccountScope.PERSONAL) "PERSONAL_TX" else "ISP_TX",
              "entityId" to editingExisting.id,
              "action" to "UPDATE",
              "summary" to "Edited ${editingExisting.partyName} -> ${partyName.trim()}",
              "previousValues" to
                "${editingExisting.category} | ${Formatters.formatTaka(editingExisting.amount)} | ${editingExisting.dateFormatted}",
              "newValues" to "$category | ${Formatters.formatTaka(amount)} | $dateFormatted",
              "performedByUid" to uid,
              "performedByName" to userName.ifBlank { "User" }.take(120),
              "createdAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteTransaction(
    tx: TransactionRecord,
    workspaceId: String,
    memberUids: List<String>,
    performedByName: String,
  ): Result<Unit> {
    var docPath = "transactions/${tx.id}"
    return try {
      val uid = requireUserId()
      val safeMembers = (memberUids + uid).distinct().take(25)
      val docRef =
        if (tx.scope == AccountScope.PERSONAL) {
          db.collection("users").document(uid).collection("personal_transactions").document(tx.id)
        } else {
          db.collection("workspaces").document(workspaceId).collection("isp_transactions").document(tx.id)
        }
      docPath = docRef.path
      val batch = db.batch()
      batch.delete(docRef)
      if (workspaceId.isNotBlank()) {
        val logRef =
          db.collection("workspaces").document(workspaceId).collection("audit_logs").document()
        batch.set(
          logRef,
          mapOf(
            "workspaceId" to workspaceId,
            "memberUids" to safeMembers,
            "entityType" to if (tx.scope == AccountScope.PERSONAL) "PERSONAL_TX" else "ISP_TX",
            "entityId" to tx.id,
            "action" to "DELETE",
            "summary" to "Deleted ${tx.type}: ${tx.partyName} (${Formatters.formatTaka(tx.amount)})",
            "previousValues" to "${tx.category} | ${tx.dateFormatted} | ${tx.paymentMethod}",
            "performedByUid" to uid,
            "performedByName" to performedByName.ifBlank { "Admin" }.take(120),
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateAccountOpeningBalance(
    scope: AccountScope,
    workspaceId: String,
    memberUids: List<String>,
    accountId: String,
    method: PaymentMethod,
    openingBalance: Double,
    label: String,
  ): Result<Unit> {
    var docPath = "accounts/$accountId"
    return try {
      val uid = requireUserId()
      val safeMembers = (memberUids + uid).distinct().take(25)
      val colRef =
        if (scope == AccountScope.PERSONAL) {
          db.collection("users").document(uid).collection("personal_accounts")
        } else {
          db.collection("workspaces").document(workspaceId).collection("isp_accounts")
        }
      val docRef = colRef.document(accountId)
      docPath = docRef.path
      val snap = docRef.get().await()
      if (snap.exists()) {
        val update =
          buildMap<String, Any> {
            put("openingBalance", openingBalance)
            put("accountLabel", label.trim().take(80))
            if (scope == AccountScope.ISP_BUSINESS) put("memberUids", safeMembers)
            put("updatedAt", FieldValue.serverTimestamp())
          }
        docRef.update(update).await()
      } else {
        val create =
          buildMap<String, Any> {
            if (scope == AccountScope.PERSONAL) put("userId", uid)
            else {
              put("workspaceId", workspaceId)
              put("memberUids", safeMembers)
            }
            put("method", method.name)
            put("openingBalance", openingBalance)
            put("accountLabel", label.trim().take(80))
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
          }
        docRef.set(create).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  // --- ISP Customers & Billing ---
  suspend fun saveCustomer(
    workspaceId: String,
    memberUids: List<String>,
    customerCode: String,
    name: String,
    phone: String,
    address: String,
    packageName: String,
    monthlyFee: Double,
    existingId: String? = null,
  ): Result<Unit> {
    var docPath = "workspaces/$workspaceId/customers"
    return try {
      val uid = requireUserId()
      val safeMembers = (memberUids + uid).distinct().take(25)
      val colRef = db.collection("workspaces").document(workspaceId).collection("customers")
      if (existingId == null) {
        val docRef = colRef.document()
        docPath = docRef.path
        docRef
          .set(
            mapOf(
              "workspaceId" to workspaceId,
              "memberUids" to safeMembers,
              "customerCode" to customerCode.trim().take(60),
              "name" to name.trim().take(140),
              "phone" to phone.trim().take(40),
              "address" to address.trim().take(240),
              "packageName" to packageName.trim().take(100),
              "monthlyFee" to monthlyFee.coerceAtLeast(0.0),
              "active" to true,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        val docRef = colRef.document(existingId)
        docPath = docRef.path
        docRef
          .update(
            mapOf(
              "customerCode" to customerCode.trim().take(60),
              "name" to name.trim().take(140),
              "phone" to phone.trim().take(40),
              "address" to address.trim().take(240),
              "packageName" to packageName.trim().take(100),
              "monthlyFee" to monthlyFee.coerceAtLeast(0.0),
              "memberUids" to safeMembers,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  suspend fun createCustomerBill(
    workspaceId: String,
    memberUids: List<String>,
    customer: IspCustomer,
    billingMonth: String,
    monthlyBillAmount: Double,
    previousDue: Double,
  ): Result<Unit> {
    var docPath = "workspaces/$workspaceId/customer_bills"
    return try {
      val uid = requireUserId()
      val safeMembers = (memberUids + uid).distinct().take(25)
      val billId = "bill_${customer.id}_${billingMonth.replace("-", "_")}"
      val docRef =
        db.collection("workspaces").document(workspaceId).collection("customer_bills").document(billId)
      docPath = docRef.path
      val snap = docRef.get().await()
      val existingPaid = if (snap.exists()) snap.getDouble("paidAmount") ?: 0.0 else 0.0
      val (totalPayable, remainingDue, status) =
        AccountingCalculator.calculateBillTotals(monthlyBillAmount, previousDue, existingPaid)
      if (snap.exists()) {
        docRef
          .update(
            mapOf(
              "monthlyBillAmount" to monthlyBillAmount,
              "previousDue" to previousDue,
              "totalPayable" to totalPayable,
              "remainingDue" to remainingDue,
              "status" to status.name,
              "memberUids" to safeMembers,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .set(
            mapOf(
              "workspaceId" to workspaceId,
              "memberUids" to safeMembers,
              "customerDocId" to customer.id,
              "customerCode" to customer.customerCode,
              "customerName" to customer.name,
              "billingMonth" to billingMonth,
              "monthlyBillAmount" to monthlyBillAmount,
              "previousDue" to previousDue,
              "totalPayable" to totalPayable,
              "paidAmount" to 0.0,
              "remainingDue" to remainingDue,
              "status" to status.name,
              "lastPaymentDate" to "",
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  suspend fun recordBillPayment(
    workspaceId: String,
    memberUids: List<String>,
    bill: CustomerBill,
    paidNow: Double,
    paymentDateMillis: Long,
    paymentDateFormatted: String,
    paymentMethod: PaymentMethod,
    collectedByEmployee: OfficeEmployee?,
    referenceNumber: String,
    note: String,
    userName: String,
  ): Result<Unit> {
    var docPath = "workspaces/$workspaceId/bill_payments"
    return try {
      val uid = requireUserId()
      require(paidNow > 0.0) { "Payment amount must be greater than zero." }
      val safeMembers = (memberUids + uid).distinct().take(25)
      val wsRef = db.collection("workspaces").document(workspaceId)
      val billRef = wsRef.collection("customer_bills").document(bill.id)
      val payRef = wsRef.collection("bill_payments").document()
      val txRef = wsRef.collection("isp_transactions").document()
      docPath = payRef.path

      val newPaid = bill.paidAmount + paidNow
      val (totalPayable, remainingDue, status) =
        AccountingCalculator.calculateBillTotals(
          bill.monthlyBillAmount,
          bill.previousDue,
          newPaid,
        )

      val batch = db.batch()
      batch.set(
        payRef,
        mapOf(
          "workspaceId" to workspaceId,
          "memberUids" to safeMembers,
          "billId" to bill.id,
          "customerDocId" to bill.customerDocId,
          "customerCode" to bill.customerCode,
          "customerName" to bill.customerName,
          "billingMonth" to bill.billingMonth,
          "paidAmount" to paidNow,
          "paymentDateMillis" to paymentDateMillis,
          "paymentDateFormatted" to paymentDateFormatted,
          "paymentMethod" to paymentMethod.name,
          "collectedByEmployeeId" to (collectedByEmployee?.id ?: ""),
          "collectedByEmployeeName" to (collectedByEmployee?.name ?: ""),
          "referenceNumber" to referenceNumber.trim().take(80),
          "note" to note.trim().take(300),
          "createdByUid" to uid,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )

      batch.update(
        billRef,
        mapOf(
          "totalPayable" to totalPayable,
          "paidAmount" to newPaid,
          "remainingDue" to remainingDue,
          "status" to status.name,
          "lastPaymentDate" to paymentDateFormatted,
          "memberUids" to safeMembers,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      // Automatically record ISP Business income transaction (Customer Bill Collection)
      batch.set(
        txRef,
        mapOf(
          "workspaceId" to workspaceId,
          "memberUids" to safeMembers,
          "type" to TransactionType.INCOME.name,
          "category" to "Customer Bill Collection",
          "partyName" to "${bill.customerName} (${bill.customerCode})",
          "amount" to paidNow,
          "paymentMethod" to paymentMethod.name,
          "referenceNumber" to referenceNumber.trim().take(80),
          "description" to "Bill ${bill.billingMonth} ${note.trim()}".trim().take(500),
          "dateMillis" to paymentDateMillis,
          "dateFormatted" to paymentDateFormatted,
          "sourceType" to "CUSTOMER_PAYMENT",
          "linkedBillId" to bill.id,
          "linkedPaymentId" to payRef.id,
          "createdByUid" to uid,
          "createdByName" to userName.ifBlank { "User" }.take(120),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      // Automatically record employee collection entry if collected by a team member
      if (collectedByEmployee != null) {
        val empEntryRef = wsRef.collection("employee_entries").document()
        batch.set(
          empEntryRef,
          mapOf(
            "workspaceId" to workspaceId,
            "memberUids" to safeMembers,
            "employeeId" to collectedByEmployee.id,
            "employeeName" to collectedByEmployee.name,
            "entryType" to EmployeeEntryType.COLLECTION.name,
            "amount" to paidNow,
            "dateMillis" to paymentDateMillis,
            "dateFormatted" to paymentDateFormatted,
            "paymentMethod" to paymentMethod.name,
            "referenceNumber" to referenceNumber.trim().take(80),
            "customerName" to bill.customerName,
            "note" to "Bill ${bill.billingMonth}".take(300),
            "createdByUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  // --- Office Team / Employees ---
  suspend fun saveEmployee(
    workspaceId: String,
    memberUids: List<String>,
    name: String,
    phone: String,
    designation: String,
  ): Result<Unit> {
    var docPath = "workspaces/$workspaceId/employees"
    return try {
      val uid = requireUserId()
      val safeMembers = (memberUids + uid).distinct().take(25)
      val docRef = db.collection("workspaces").document(workspaceId).collection("employees").document()
      docPath = docRef.path
      docRef
        .set(
          mapOf(
            "workspaceId" to workspaceId,
            "memberUids" to safeMembers,
            "name" to name.trim().take(120),
            "phone" to phone.trim().take(40),
            "designation" to designation.trim().take(80),
            "active" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun recordEmployeeEntry(
    workspaceId: String,
    memberUids: List<String>,
    employee: OfficeEmployee,
    entryType: EmployeeEntryType,
    amount: Double,
    dateMillis: Long,
    dateFormatted: String,
    paymentMethod: PaymentMethod,
    referenceNumber: String,
    customerName: String,
    note: String,
  ): Result<Unit> {
    var docPath = "workspaces/$workspaceId/employee_entries"
    return try {
      val uid = requireUserId()
      require(amount > 0.0) { "Amount must be greater than zero." }
      val safeMembers = (memberUids + uid).distinct().take(25)
      val docRef =
        db.collection("workspaces").document(workspaceId).collection("employee_entries").document()
      docPath = docRef.path
      docRef
        .set(
          mapOf(
            "workspaceId" to workspaceId,
            "memberUids" to safeMembers,
            "employeeId" to employee.id,
            "employeeName" to employee.name,
            "entryType" to entryType.name,
            "amount" to amount,
            "dateMillis" to dateMillis,
            "dateFormatted" to dateFormatted,
            "paymentMethod" to paymentMethod.name,
            "referenceNumber" to referenceNumber.trim().take(80),
            "customerName" to customerName.trim().take(140),
            "note" to note.trim().take(300),
            "createdByUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  // --- Team Invites & Language ---
  suspend fun inviteWorkspaceMember(
    workspaceId: String,
    workspaceName: String,
    email: String,
    role: UserRole,
  ): Result<Unit> {
    var docPath = "team_invites"
    return try {
      val uid = requireUserId()
      val cleanEmail = email.trim().lowercase(Locale.US)
      require(cleanEmail.contains("@")) { "Valid email address required." }
      val inviteId = "inv_${sha256Short("${workspaceId}_$cleanEmail")}"
      val docRef = db.collection("team_invites").document(inviteId)
      docPath = docRef.path
      docRef
        .set(
          mapOf(
            "inviteEmail" to cleanEmail,
            "workspaceId" to workspaceId,
            "workspaceName" to workspaceName.take(120),
            "role" to role.name,
            "invitedByUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateMemberRole(
    workspaceId: String,
    memberUid: String,
    newRole: UserRole,
  ): Result<Unit> {
    val docPath = "workspaces/$workspaceId/members/$memberUid"
    return try {
      requireUserId()
      db.document(docPath)
        .update(
          mapOf(
            "role" to newRole.name,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  // --- Backup & Restore ---
  fun buildBackupJson(
    personalTxs: List<TransactionRecord>,
    ispTxs: List<TransactionRecord>,
    customers: List<IspCustomer>,
    bills: List<CustomerBill>,
    employees: List<OfficeEmployee>,
  ): String {
    fun txToJson(tx: TransactionRecord) =
      JSONObject().apply {
        put("id", tx.id)
        put("scope", tx.scope.name)
        put("type", tx.type)
        put("category", tx.category)
        put("partyName", tx.partyName)
        put("amount", tx.amount)
        put("paymentMethod", tx.paymentMethod)
        put("toPaymentMethod", tx.toPaymentMethod ?: "")
        put("referenceNumber", tx.referenceNumber)
        put("description", tx.description)
        put("dateMillis", tx.dateMillis)
        put("dateFormatted", tx.dateFormatted)
      }

    return JSONObject()
      .apply {
        put("app", "Plusnet Accounts")
        put("exportedAt", Formatters.currentDateFormatted())
        put("personalTransactions", JSONArray(personalTxs.map(::txToJson)))
        put("ispTransactions", JSONArray(ispTxs.map(::txToJson)))
        put(
          "customers",
          JSONArray(
            customers.map { c ->
              JSONObject().apply {
                put("customerCode", c.customerCode)
                put("name", c.name)
                put("phone", c.phone)
                put("address", c.address)
                put("packageName", c.packageName)
                put("monthlyFee", c.monthlyFee)
              }
            }
          ),
        )
        put("billCount", bills.size)
        put("employeeCount", employees.size)
      }
      .toString(2)
  }

  suspend fun restoreTransactionsFromBackup(
    jsonText: String,
    workspaceId: String,
    memberUids: List<String>,
    userName: String,
  ): Result<Int> {
    var restoredCount = 0
    return try {
      val root = JSONObject(jsonText)
      val personalArr = root.optJSONArray("personalTransactions") ?: JSONArray()
      for (i in 0 until personalArr.length()) {
        val obj = personalArr.getJSONObject(i)
        saveTransaction(
          scope = AccountScope.PERSONAL,
          workspaceId = workspaceId,
          memberUids = memberUids,
          type = TransactionType.safeValueOf(obj.optString("type", "INCOME")),
          category = obj.optString("category", "Other Income"),
          partyName = obj.optString("partyName", "Restored Entry"),
          amount = obj.optDouble("amount", 0.0).coerceAtLeast(1.0),
          paymentMethod = PaymentMethod.safeValueOf(obj.optString("paymentMethod", "CASH")),
          toPaymentMethod = obj.optString("toPaymentMethod").takeIf { it.isNotBlank() }?.let { PaymentMethod.safeValueOf(it) },
          referenceNumber = obj.optString("referenceNumber", ""),
          description = obj.optString("description", ""),
          dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
          dateFormatted = obj.optString("dateFormatted", Formatters.currentDateFormatted()),
          userName = userName,
        )
        restoredCount++
      }
      val ispArr = root.optJSONArray("ispTransactions") ?: JSONArray()
      for (i in 0 until ispArr.length()) {
        val obj = ispArr.getJSONObject(i)
        saveTransaction(
          scope = AccountScope.ISP_BUSINESS,
          workspaceId = workspaceId,
          memberUids = memberUids,
          type = TransactionType.safeValueOf(obj.optString("type", "INCOME")),
          category = obj.optString("category", "Other Income"),
          partyName = obj.optString("partyName", "Restored Entry"),
          amount = obj.optDouble("amount", 0.0).coerceAtLeast(1.0),
          paymentMethod = PaymentMethod.safeValueOf(obj.optString("paymentMethod", "CASH")),
          toPaymentMethod = obj.optString("toPaymentMethod").takeIf { it.isNotBlank() }?.let { PaymentMethod.safeValueOf(it) },
          referenceNumber = obj.optString("referenceNumber", ""),
          description = obj.optString("description", ""),
          dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
          dateFormatted = obj.optString("dateFormatted", Formatters.currentDateFormatted()),
          userName = userName,
        )
        restoredCount++
      }
      Result.success(restoredCount)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun sha256Short(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.take(12).joinToString("") { "%02x".format(it) }
  }
}
