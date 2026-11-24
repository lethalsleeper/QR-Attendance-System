'use strict';

/** Shared helpers for tests that run against the Firebase Emulator Suite. */

const PROJECT_ID = process.env.GCLOUD_PROJECT || 'demo-qr-attendance';
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST;
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST;

/** Emulator tests are skipped (not failed) when run outside `firebase emulators:exec`. */
const SKIP_FIRESTORE = FIRESTORE_HOST
  ? false
  : 'FIRESTORE_EMULATOR_HOST not set – run `npm run test:emulator`';
const SKIP_AUTH = FIRESTORE_HOST && AUTH_HOST
  ? false
  : 'Auth/Firestore emulators not running – run `npm run test:emulator`';

async function clearFirestore() {
  const url = `http://${FIRESTORE_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
  const response = await fetch(url, { method: 'DELETE' });
  if (!response.ok) throw new Error(`Failed to clear Firestore emulator: ${response.status}`);
}

async function clearAuth() {
  if (!AUTH_HOST) return;
  const url = `http://${AUTH_HOST}/emulator/v1/projects/${PROJECT_ID}/accounts`;
  const response = await fetch(url, { method: 'DELETE', headers: { Authorization: 'Bearer owner' } });
  if (!response.ok) throw new Error(`Failed to clear Auth emulator: ${response.status}`);
}

/** Asserts an HttpsError with the given code and details.reason. */
function isHttpsError(code, reason) {
  return (error) => {
    if (error.code !== code) {
      throw new Error(`Expected code ${code} but got ${error.code} (${error.message})`);
    }
    if (reason && (!error.details || error.details.reason !== reason)) {
      throw new Error(`Expected reason ${reason} but got ${JSON.stringify(error.details)}`);
    }
    return true;
  };
}

module.exports = { PROJECT_ID, SKIP_FIRESTORE, SKIP_AUTH, clearFirestore, clearAuth, isHttpsError };
