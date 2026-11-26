const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_001";
const EMPLOYEE_UID = "employee_uid_002";
const STRANGER_UID = "stranger_uid_003";
const WORKSPACE_ID = `ws_${ADMIN_UID}`;

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedWorkspaceWithAdminAndEmployee() {
  const past = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("workspaces").doc(WORKSPACE_ID).set({
      name: "Plusnet ISP",
      ownerUid: ADMIN_UID,
      memberUids: [ADMIN_UID, EMPLOYEE_UID],
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("workspaces").doc(WORKSPACE_ID).collection("members").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: "admin@plusnet.local",
      displayName: "Admin User",
      role: "ADMIN",
      workspaceId: WORKSPACE_ID,
      workspaceMemberUids: [ADMIN_UID, EMPLOYEE_UID],
      joinedAt: past,
      updatedAt: past,
    });
    await db.collection("workspaces").doc(WORKSPACE_ID).collection("members").doc(EMPLOYEE_UID).set({
      uid: EMPLOYEE_UID,
      email: "employee@plusnet.local",
      displayName: "Field Staff",
      role: "EMPLOYEE",
      workspaceId: WORKSPACE_ID,
      workspaceMemberUids: [ADMIN_UID, EMPLOYEE_UID],
      joinedAt: past,
      updatedAt: past,
    });
  });
}

test("Unauthenticated requests are rejected across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ADMIN_UID).get());
  await assertFails(unauthDb.collection("users").doc(ADMIN_UID).collection("personal_transactions").get());
  await assertFails(unauthDb.collection("users").doc(ADMIN_UID).collection("search_history").get());
  await assertFails(unauthDb.collection("workspaces").doc(WORKSPACE_ID).collection("isp_transactions").get());
});

test("User profile creation & anti-privilege-escalation checks", async () => {
  const now = new Date(Date.now() - 1000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "admin@plusnet.local",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      email: "admin@plusnet.local",
      displayName: "Admin User",
      workspaceId: WORKSPACE_ID,
      role: "ADMIN",
      preferredLanguage: "en",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cannot escalate role or switch workspace on update
  await assertFails(
    adminDb.collection("users").doc(ADMIN_UID).update({
      role: "EMPLOYEE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Can update displayName and preferredLanguage
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).update({
      preferredLanguage: "bn",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Stranger cannot claim ADMIN role on someone else's workspace
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID, {
    email: "stranger@plusnet.local",
    email_verified: true,
  }).firestore();
  await assertFails(
    strangerDb.collection("users").doc(STRANGER_UID).set({
      userId: STRANGER_UID,
      email: "stranger@plusnet.local",
      displayName: "Stranger",
      workspaceId: WORKSPACE_ID,
      role: "ADMIN",
      preferredLanguage: "en",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Personal transactions & search history are strictly private to owner", async () => {
  await seedWorkspaceWithAdminAndEmployee();
  const now = new Date(Date.now() - 1000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const employeeDb = testEnv.authenticatedContext(EMPLOYEE_UID).firestore();

  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).collection("personal_transactions").doc("ptx_1").set({
      userId: ADMIN_UID,
      type: "INCOME",
      category: "Personal Income",
      partyName: "Consulting",
      amount: 15000.0,
      paymentMethod: "BANK",
      referenceNumber: "REF-101",
      description: "Monthly retainer",
      dateMillis: 1763700000000,
      dateFormatted: "21-11-2026",
      createdByUid: ADMIN_UID,
      createdByName: "Admin User",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Employee in same ISP workspace CANNOT read or write Admin's personal transactions
  await assertFails(employeeDb.collection("users").doc(ADMIN_UID).collection("personal_transactions").get());
  await assertFails(employeeDb.collection("users").doc(ADMIN_UID).collection("personal_transactions").doc("ptx_1").get());

  // Search history CRUD for owner + isolation from others
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).collection("search_history").doc("term_iig").set({
      userId: ADMIN_UID,
      term: "IIG",
      searchCount: 1,
      createdAt: now,
      lastSearchedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).collection("search_history").doc("term_iig").update({
      searchCount: 2,
      lastSearchedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(adminDb.collection("users").doc(ADMIN_UID).collection("search_history").get());
  await assertFails(employeeDb.collection("users").doc(ADMIN_UID).collection("search_history").get());
});

test("ISP transactions: Admin & Employee access, Employee cannot delete, filtered query enforced", async () => {
  await seedWorkspaceWithAdminAndEmployee();
  const now = new Date(Date.now() - 1000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const employeeDb = testEnv.authenticatedContext(EMPLOYEE_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();

  const txRef = adminDb.collection("workspaces").doc(WORKSPACE_ID).collection("isp_transactions").doc("itx_1");
  await assertSucceeds(
    txRef.set({
      workspaceId: WORKSPACE_ID,
      memberUids: [ADMIN_UID, EMPLOYEE_UID],
      type: "INCOME",
      category: "IIG Income",
      partyName: "Summit Link",
      amount: 45000.0,
      paymentMethod: "BANK",
      referenceNumber: "INV-88",
      description: "IIG uplink billing",
      dateMillis: 1763700000000,
      dateFormatted: "21-11-2026",
      createdByUid: ADMIN_UID,
      createdByName: "Admin User",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Employee can query using array-contains memberUids, cannot run unfiltered list
  await assertSucceeds(
    employeeDb.collection("workspaces").doc(WORKSPACE_ID).collection("isp_transactions")
      .where("memberUids", "array-contains", EMPLOYEE_UID)
      .get()
  );
  await assertFails(
    employeeDb.collection("workspaces").doc(WORKSPACE_ID).collection("isp_transactions").get()
  );

  // Stranger cannot query workspace docs or read directly
  await assertFails(
    strangerDb.collection("workspaces").doc(WORKSPACE_ID).collection("isp_transactions")
      .where("memberUids", "array-contains", ADMIN_UID)
      .get()
  );
  await assertFails(
    strangerDb.collection("workspaces").doc(WORKSPACE_ID).collection("isp_transactions")
      .doc("itx_1")
      .get()
  );

  // Employee can create an expense entry (e.g., Maintenance Cost)
  await assertSucceeds(
    employeeDb.collection("workspaces").doc(WORKSPACE_ID).collection("isp_transactions").doc("itx_2").set({
      workspaceId: WORKSPACE_ID,
      memberUids: [ADMIN_UID, EMPLOYEE_UID],
      type: "EXPENSE",
      category: "Maintenance Cost",
      partyName: "Fiber Patch Shop",
      amount: 1200.0,
      paymentMethod: "CASH",
      dateMillis: 1763700000000,
      dateFormatted: "21-11-2026",
      createdByUid: EMPLOYEE_UID,
      createdByName: "Field Staff",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Employee CANNOT create Cash/Bank TRANSFER or delete any financial transaction
  await assertFails(
    employeeDb.collection("workspaces").doc(WORKSPACE_ID).collection("isp_transactions").doc("itx_transfer").set({
      workspaceId: WORKSPACE_ID,
      memberUids: [ADMIN_UID, EMPLOYEE_UID],
      type: "TRANSFER",
      category: "Cash to Bank Transfer",
      partyName: "Internal Transfer",
      amount: 5000.0,
      paymentMethod: "CASH",
      toPaymentMethod: "BANK",
      dateMillis: 1763700000000,
      dateFormatted: "21-11-2026",
      createdByUid: EMPLOYEE_UID,
      createdByName: "Field Staff",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    employeeDb.collection("workspaces").doc(WORKSPACE_ID).collection("isp_transactions").doc("itx_2").delete()
  );

  // Admin CAN delete financial transaction
  await assertSucceeds(txRef.delete());
});

test("Customer billing, partial payments, employee entries, and immutable audit logs", async () => {
  await seedWorkspaceWithAdminAndEmployee();
  const now = new Date(Date.now() - 1000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const employeeDb = testEnv.authenticatedContext(EMPLOYEE_UID).firestore();

  // Customer & Bill creation
  await assertSucceeds(
    employeeDb.collection("workspaces").doc(WORKSPACE_ID).collection("customers").doc("cust_1").set({
      workspaceId: WORKSPACE_ID,
      memberUids: [ADMIN_UID, EMPLOYEE_UID],
      customerCode: "PN-101",
      name: "Rahim Traders",
      phone: "01711000000",
      address: "Mirpur 10",
      packageName: "50 Mbps Dedicated",
      monthlyFee: 1500.0,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    employeeDb.collection("workspaces").doc(WORKSPACE_ID).collection("customer_bills").doc("bill_1").set({
      workspaceId: WORKSPACE_ID,
      memberUids: [ADMIN_UID, EMPLOYEE_UID],
      customerDocId: "cust_1",
      customerCode: "PN-101",
      customerName: "Rahim Traders",
      billingMonth: "11-2026",
      monthlyBillAmount: 1500.0,
      previousDue: 500.0,
      totalPayable: 2000.0,
      paidAmount: 1000.0,
      remainingDue: 1000.0,
      status: "PARTIAL",
      lastPaymentDate: "21-11-2026",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Payment record is immutable (cannot update, employee cannot delete)
  const payRef = employeeDb.collection("workspaces").doc(WORKSPACE_ID).collection("bill_payments").doc("pay_1");
  await assertSucceeds(
    payRef.set({
      workspaceId: WORKSPACE_ID,
      memberUids: [ADMIN_UID, EMPLOYEE_UID],
      billId: "bill_1",
      customerDocId: "cust_1",
      customerCode: "PN-101",
      customerName: "Rahim Traders",
      billingMonth: "11-2026",
      paidAmount: 1000.0,
      paymentDateMillis: 1763700000000,
      paymentDateFormatted: "21-11-2026",
      paymentMethod: "CASH",
      createdByUid: EMPLOYEE_UID,
      createdAt: now,
    })
  );
  await assertFails(payRef.update({ paidAmount: 2000.0 }));
  await assertFails(payRef.delete());

  // Audit logs are append-only (nobody can update or delete)
  const auditRef = adminDb.collection("workspaces").doc(WORKSPACE_ID).collection("audit_logs").doc("log_1");
  await assertSucceeds(
    auditRef.set({
      workspaceId: WORKSPACE_ID,
      memberUids: [ADMIN_UID, EMPLOYEE_UID],
      entityType: "ISP_TRANSACTION",
      entityId: "itx_1",
      action: "DELETE",
      summary: "Deleted duplicate entry",
      performedByUid: ADMIN_UID,
      performedByName: "Admin User",
      createdAt: now,
    })
  );
  await assertFails(auditRef.update({ summary: "Tampered" }));
  await assertFails(auditRef.delete());
});
