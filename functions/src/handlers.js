'use strict';

const { HttpsError } = require('firebase-functions/v2/https');
const logger = require('firebase-functions/logger');
const { FieldValue, Timestamp } = require('firebase-admin/firestore');
const {
  ROLES,
  SESSION_STATUS,
  REASONS,
  ValidationError,
  validateRegistration,
  validateCreateSession,
  validateAttendancePayload,
  generateToken: defaultGenerateToken,
  evaluateAttendance,
} = require('./validation');
const { courseKey, groupHistoryByCourse, buildStats } = require('./stats');

const COLLECTIONS = Object.freeze({
  USERS: 'users',
  SESSIONS: 'sessions',
  ATTENDANCE: 'attendance',
  TEACHER_INVITES: 'teacherInvites',
});

/** HttpsError carrying a machine-readable reason in details.reason. */
function fail(code, reason, message) {
  return new HttpsError(code, message, { reason });
}

function requireAuth(request) {
  const uid = request && request.auth && request.auth.uid;
  if (!uid) throw fail('unauthenticated', REASONS.UNAUTHENTICATED, 'You must be signed in.');
  return uid;
}

function validated(fn, code = 'invalid-argument') {
  try {
    return fn();
  } catch (error) {
    if (error instanceof ValidationError) throw fail(code, error.reason, error.message);
    throw error;
  }
}

/**
 * Creates all callable handlers with injectable dependencies so they can be tested
 * against the Firebase Emulator Suite with a controllable clock.
 *
 * @param {object} deps
 * @param {FirebaseFirestore.Firestore} deps.db
 * @param {import('firebase-admin/auth').Auth} deps.auth
 * @param {() => number} [deps.now] server clock (epoch millis)
 * @param {() => string} [deps.generateToken]
 */
function createHandlers({ db, auth, now = () => Date.now(), generateToken = defaultGenerateToken }) {
  const users = () => db.collection(COLLECTIONS.USERS);
  const sessions = () => db.collection(COLLECTIONS.SESSIONS);
  const invites = () => db.collection(COLLECTIONS.TEACHER_INVITES);

  async function loadProfile(uid) {
    const snapshot = await users().doc(uid).get();
    if (!snapshot.exists) {
      throw fail('failed-precondition', REASONS.PROFILE_MISSING, 'Your user profile was not found.');
    }
    return snapshot;
  }

  /**
   * registerUser – creates the Auth account and the Firestore profile.
   * The role is decided here, never trusted from the client: "teacher" is granted only
   * when an administrator has created teacherInvites/{email}. Invites are single-use.
   */
  async function registerUser(request) {
    const input = validated(() => validateRegistration(request.data));
    const inviteRef = invites().doc(input.email);

    if (input.role === ROLES.TEACHER) {
      const invite = await inviteRef.get();
      if (!invite.exists || invite.get('used') === true) {
        throw fail(
          'permission-denied',
          REASONS.TEACHER_NOT_APPROVED,
          'This email is not approved for a teacher account. Contact your administrator.',
        );
      }
    }

    let userRecord;
    try {
      userRecord = await auth.createUser({
        email: input.email,
        password: input.password,
        displayName: input.name,
      });
    } catch (error) {
      switch (error && error.code) {
        case 'auth/email-already-exists':
          throw fail('already-exists', REASONS.EMAIL_EXISTS, 'An account with this email already exists.');
        case 'auth/invalid-password':
          throw fail('invalid-argument', REASONS.WEAK_PASSWORD, 'Password is too weak.');
        case 'auth/invalid-email':
          throw fail('invalid-argument', REASONS.INVALID_INPUT, 'Enter a valid email address.');
        default:
          logger.error('registerUser: createUser failed', error);
          throw fail('internal', REASONS.REGISTRATION_FAILED, 'Could not create the account. Please try again.');
      }
    }

    const uid = userRecord.uid;
    try {
      await db.runTransaction(async (tx) => {
        if (input.role === ROLES.TEACHER) {
          const invite = await tx.get(inviteRef);
          if (!invite.exists || invite.get('used') === true) {
            throw fail(
              'permission-denied',
              REASONS.TEACHER_NOT_APPROVED,
              'This email is not approved for a teacher account. Contact your administrator.',
            );
          }
          tx.update(inviteRef, { used: true, usedBy: uid, usedAt: FieldValue.serverTimestamp() });
        }
        tx.create(users().doc(uid), {
          uid,
          name: input.name,
          email: input.email,
          role: input.role,
          rollNumber: input.rollNumber,
          createdAt: FieldValue.serverTimestamp(),
        });
      });
    } catch (error) {
      // Roll back the Auth account so the email can be used again.
      await auth.deleteUser(uid).catch((rollbackError) => {
        logger.error('registerUser: rollback of auth user failed', { uid, rollbackError });
      });
      if (error instanceof HttpsError) throw error;
      logger.error('registerUser: profile transaction failed', error);
      throw fail('internal', REASONS.REGISTRATION_FAILED, 'Could not create the account. Please try again.');
    }

    logger.info('registerUser: account created', { uid, role: input.role });
    return { uid, role: input.role };
  }

  /**
   * createSession – teacher only. The session id, a 256-bit random token and the
   * created/expiry timestamps are all generated on the server. The token is NOT
   * returned; the owning teacher reads it from Firestore (rules allow only them).
   */
  async function createSession(request) {
    const uid = requireAuth(request);
    const input = validated(() => validateCreateSession(request.data));
    const profile = await loadProfile(uid);
    if (profile.get('role') !== ROLES.TEACHER) {
      throw fail('permission-denied', REASONS.NOT_A_TEACHER, 'Only teachers can start attendance sessions.');
    }

    const nowMs = now();
    const createdAt = Timestamp.fromMillis(nowMs);
    const expiresAt = Timestamp.fromMillis(nowMs + input.durationMinutes * 60 * 1000);
    const ref = sessions().doc();

    await ref.create({
      sessionId: ref.id,
      teacherId: uid,
      teacherName: profile.get('name') || '',
      subject: input.subject,
      subjectKey: input.subjectKey,
      createdAt,
      expiresAt,
      token: generateToken(),
      status: SESSION_STATUS.ACTIVE,
      durationMinutes: input.durationMinutes,
      attendanceCount: 0,
    });

    return {
      sessionId: ref.id,
      createdAt: nowMs,
      expiresAt: expiresAt.toMillis(),
      durationMinutes: input.durationMinutes,
      serverTime: nowMs,
    };
  }

  /**
   * markAttendance – student only. Validates the scanned payload and records attendance
   * atomically. The attendance document id is the student's uid, so a student can have
   * at most one record per session; concurrent duplicate requests are serialised by the
   * transaction and the loser receives ALREADY_MARKED.
   */
  async function markAttendance(request) {
    const uid = requireAuth(request);
    const { sessionId, token } = validated(() => validateAttendancePayload(request.data));

    const userRef = users().doc(uid);
    const sessionRef = sessions().doc(sessionId);
    const attendanceRef = sessionRef.collection(COLLECTIONS.ATTENDANCE).doc(uid);
    const historyRef = userRef.collection(COLLECTIONS.ATTENDANCE).doc(sessionId);

    return db.runTransaction(async (tx) => {
      const [userSnap, sessionSnap, attendanceSnap] = await tx.getAll(userRef, sessionRef, attendanceRef);

      if (!userSnap.exists) {
        throw fail('failed-precondition', REASONS.PROFILE_MISSING, 'Your user profile was not found.');
      }
      if (userSnap.get('role') !== ROLES.STUDENT) {
        throw fail('permission-denied', REASONS.NOT_A_STUDENT, 'Only students can mark attendance.');
      }

      // Server time, captured inside the transaction (retries re-evaluate it).
      const nowMs = now();
      const session = sessionSnap.exists ? sessionSnap.data() : null;
      const reason = evaluateAttendance({ session, token, nowMs, alreadyMarked: attendanceSnap.exists });

      switch (reason) {
        case null:
          break;
        case REASONS.SESSION_NOT_FOUND:
          throw fail('not-found', reason, 'Session not found.');
        case REASONS.INVALID_TOKEN:
          throw fail('permission-denied', reason, 'Invalid QR code.');
        case REASONS.ALREADY_MARKED:
          throw fail('already-exists', reason, 'Attendance already marked for this session.');
        case REASONS.SESSION_EXPIRED:
          throw fail('failed-precondition', reason, 'This QR code has expired.');
        default:
          throw fail('internal', reason, 'Attendance could not be recorded.');
      }

      const markedAt = Timestamp.fromMillis(nowMs);
      const studentName = userSnap.get('name') || '';
      const rollNumber = userSnap.get('rollNumber') || null;

      tx.create(attendanceRef, {
        studentId: uid,
        studentName,
        rollNumber,
        markedAt,
        status: 'present',
      });
      tx.create(historyRef, {
        sessionId,
        subject: session.subject,
        subjectKey: session.subjectKey || String(session.subject || '').toLowerCase(),
        teacherId: session.teacherId,
        teacherName: session.teacherName || null,
        sessionCreatedAt: session.createdAt,
        markedAt,
        status: 'present',
      });
      tx.update(sessionRef, { attendanceCount: FieldValue.increment(1) });

      return {
        status: 'SUCCESS',
        sessionId,
        subject: session.subject,
        teacherName: session.teacherName || null,
        markedAt: nowMs,
        serverTime: nowMs,
      };
    });
  }

  /** getStudentStats – see src/stats.js for the exact definition. */
  async function getStudentStats(request) {
    const uid = requireAuth(request);
    const profile = await loadProfile(uid);
    if (profile.get('role') !== ROLES.STUDENT) {
      throw fail('permission-denied', REASONS.NOT_A_STUDENT, 'Statistics are available to students only.');
    }

    const registeredAt = profile.get('createdAt') || Timestamp.fromMillis(0);
    const history = await users().doc(uid).collection(COLLECTIONS.ATTENDANCE).get();
    const courses = groupHistoryByCourse(history.docs.map((doc) => doc.data()));

    const conductedBy = new Map();
    await Promise.all(
      [...courses.entries()].map(async ([key, course]) => {
        const aggregate = await sessions()
          .where('teacherId', '==', course.teacherId)
          .where('subjectKey', '==', course.subjectKey)
          .where('createdAt', '>=', registeredAt)
          .count()
          .get();
        conductedBy.set(key, aggregate.data().count);
      }),
    );

    return { ...buildStats(courses, conductedBy), serverTime: now() };
  }

  /**
   * Marks sessions whose expiry time has passed as expired (runs on a schedule).
   * Covers the case where the teacher closed the app before the countdown finished.
   */
  async function expireStaleSessions() {
    const cutoff = Timestamp.fromMillis(now());
    const pageSize = 400;
    let total = 0;
    for (let page = 0; page < 25; page += 1) {
      const snapshot = await sessions()
        .where('status', '==', SESSION_STATUS.ACTIVE)
        .where('expiresAt', '<=', cutoff)
        .limit(pageSize)
        .get();
      if (snapshot.empty) break;
      const batch = db.batch();
      snapshot.docs.forEach((doc) => {
        batch.update(doc.ref, { status: SESSION_STATUS.EXPIRED, endedAt: doc.get('expiresAt') });
      });
      await batch.commit();
      total += snapshot.size;
      if (snapshot.size < pageSize) break;
    }
    return total;
  }

  return { registerUser, createSession, markAttendance, getStudentStats, expireStaleSessions };
}

module.exports = { createHandlers, COLLECTIONS, fail, courseKey };
