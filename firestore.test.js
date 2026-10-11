const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date(Date.now() - 1000);
}

function validSignalPayload(userId) {
  return {
    userId,
    mentorId: "mentor_1",
    mentorName: "CryptoWhale",
    pair: "BTC/USDT",
    direction: "LONG",
    signalTimestampMillis: 1720000000000,
    entryPrices: [60000.0, 59500.0],
    reachedEntryIndices: [],
    missedEntryIndices: [],
    tpPrices: [61500.0, 63000.0],
    reachedTpIndices: [],
    slPrice: 58000.0,
    slHit: false,
    returnedToEntry: false,
    status: "ACTIVE",
    outcome: "PENDING",
    isConservativeEstimation: false,
    priceSource: "BINANCE_FUTURES",
    outcomeSource: "NONE",
    hasSourceConflict: false,
    lastCheckedPrice: 60100.0,
    lastCheckedAtMillis: 1720000005000,
    completedAtMillis: 0,
    isDeleted: false,
    revision: 1,
    lastOperationId: "op_init_1",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

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

test("Unauthenticated user: cannot read or write signals", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("signals").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").set(validSignalPayload(ALICE_UID))
  );
});

test("Authenticated user: can create and query own signal", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").set(validSignalPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("signals")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Authenticated user: cannot read or write another user's signal", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").set(validSignalPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("signals").where("userId", "==", ALICE_UID).get()
  );
});

test("Shadow update test: rejects ghost fields on signal update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1");
  await assertSucceeds(docRef.set(validSignalPayload(ALICE_UID)));

  await assertFails(
    docRef.update({
      isVerifiedAdmin: true,
      updatedAt: serverTimestamp(),
    })
  );
});

test("Terminal state locking: COMPLETED signal cannot be reactivated to ACTIVE", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_completed");
  const completedPayload = {
    ...validSignalPayload(ALICE_UID),
    status: "COMPLETED",
    outcome: "WIN",
    completedAtMillis: 1720001000000,
  };
  await assertSucceeds(docRef.set(completedPayload));

  // Attempting to reactivate a COMPLETED signal back to ACTIVE must fail
  await assertFails(
    docRef.update({
      status: "ACTIVE",
      revision: 2,
      lastOperationId: "op_reactivate",
      updatedAt: serverTimestamp(),
    })
  );

  // Archiving a COMPLETED signal is allowed
  await assertSucceeds(
    docRef.update({
      status: "ARCHIVED",
      revision: 2,
      lastOperationId: "op_archive",
      updatedAt: serverTimestamp(),
    })
  );
});
