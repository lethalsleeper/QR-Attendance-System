'use strict';

/**
 * QR Attendance System – Cloud Functions (2nd gen).
 *
 * Callable functions (invoked from the Android app with Firebase callable SDK):
 *   registerUser     – create account + profile; role assigned server-side
 *   createSession    – teacher starts a session (server generates id, token, expiry)
 *   markAttendance   – student submits scanned QR; validated + recorded in a transaction
 *   getStudentStats  – attendance statistics for the signed-in student
 * Scheduled:
 *   expireSessions   – marks overdue sessions as expired every 5 minutes
 *
 * All business logic lives in src/ so it can be unit / emulator tested.
 */

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const { setGlobalOptions } = require('firebase-functions/v2');
const { onCall } = require('firebase-functions/v2/https');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const logger = require('firebase-functions/logger');
const { createHandlers } = require('./src/handlers');

// Must match BuildConfig.FUNCTIONS_REGION in app/build.gradle.kts.
const REGION = 'us-central1';

setGlobalOptions({ region: REGION, maxInstances: 10 });

initializeApp();

const handlers = createHandlers({ db: getFirestore(), auth: getAuth() });

// Set enforceAppCheck to true after configuring Firebase App Check (see README → Hardening).
const callableOptions = { enforceAppCheck: false };

exports.registerUser = onCall(callableOptions, (request) => handlers.registerUser(request));
exports.createSession = onCall(callableOptions, (request) => handlers.createSession(request));
exports.markAttendance = onCall(callableOptions, (request) => handlers.markAttendance(request));
exports.getStudentStats = onCall(callableOptions, (request) => handlers.getStudentStats(request));

exports.expireSessions = onSchedule({ schedule: 'every 5 minutes', timeZone: 'Etc/UTC' }, async () => {
  const count = await handlers.expireStaleSessions();
  if (count > 0) logger.info(`expireSessions: marked ${count} session(s) as expired`);
});
