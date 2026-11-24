'use strict';

/**
 * Integration tests for the Cloud Function handlers, run against the Firestore and
 * Auth emulators with a controllable server clock:
 *   npm run test:emulator
 */

const { describe, it, before, beforeEach, after } = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp, deleteApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const { createHandlers } = require('../../src/handlers');
const { PROJECT_ID, SKIP_FIRESTORE, SKIP_AUTH, clearFirestore, clearAuth, isHttpsError } = require('./helpers');

const T0 = Date.UTC(2026, 10, 14, 9, 0, 0);

describe('Cloud Function handlers (emulator)', { skip: SKIP_FIRESTORE }, () => {
  let app;
  let db;
  let auth;
  let clock;
  let handlers;

  const as = (uid, data) => ({ auth: uid ? { uid } : undefined, data });

  async function seedUser(uid, role, extra = {}) {
    await db.collection('users').doc(uid).set({
      uid,
      name: extra.name || `${role} ${uid}`,
      email: `${uid}@college.edu`,
      role,
      rollNumber: role === 'student' ? extra.rollNumber || `R-${uid}` : null,
      createdAt: Timestamp.fromMillis(extra.createdAt ?? T0 - 86_400_000),
    });
  }

  async function startSession(teacherUid = 'teacher1', subject = 'Data Structures', durationMinutes = 5) {
    const result = await handlers.createSession(as(teacherUid, { subject, durationMinutes }));
    const snapshot = await db.collection('sessions').doc(result.sessionId).get();
    return { result, token: snapshot.get('token'), sessionId: result.sessionId };
  }

  before(() => {
    app = initializeApp({ projectId: PROJECT_ID }, `functions-test-${Date.now()}`);
    db = getFirestore(app);
    auth = getAuth(app);
    handlers = createHandlers({ db, auth, now: () => clock });
  });

  after(async () => {
    await deleteApp(app);
  });

  beforeEach(async () => {
    clock = T0;
    await clearFirestore();
    await seedUser('teacher1', 'teacher', { name: 'Dr. Rao' });
    await seedUser('teacher2', 'teacher', { name: 'Ms. Iyer' });
    await seedUser('student1', 'student', { name: 'Asha Verma', rollNumber: '21CS045' });
    await seedUser('student2', 'student', { name: 'Ravi Kumar', rollNumber: '21CS046' });
  });

  // ---------------------------------------------------------------- createSession
  describe('createSession', () => {
    it('requires authentication', async () => {
      await assert.rejects(
        handlers.createSession(as(null, { subject: 'Maths', durationMinutes: 5 })),
        isHttpsError('unauthenticated', 'UNAUTHENTICATED'),
      );
    });

    it('rejects students', async () => {
      await assert.rejects(
        handlers.createSession(as('student1', { subject: 'Maths', durationMinutes: 5 })),
        isHttpsError('permission-denied', 'NOT_A_TEACHER'),
      );
    });

    it('rejects invalid durations', async () => {
      await assert.rejects(
        handlers.createSession(as('teacher1', { subject: 'Maths', durationMinutes: 600 })),
        isHttpsError('invalid-argument', 'INVALID_INPUT'),
      );
    });

    it('creates a session with a server-generated token and server-time expiry', async () => {
      const { result, token, sessionId } = await startSession('teacher1', 'Data Structures', 5);
      assert.equal(result.createdAt, T0);
      assert.equal(result.expiresAt, T0 + 5 * 60_000);
      assert.equal(result.token, undefined, 'token must not be returned by the function');
      assert.match(token, /^[A-Za-z0-9_-]{43}$/);

      const doc = (await db.collection('sessions').doc(sessionId).get()).data();
      assert.equal(doc.sessionId, sessionId);
      assert.equal(doc.teacherId, 'teacher1');
      assert.equal(doc.teacherName, 'Dr. Rao');
      assert.equal(doc.status, 'active');
      assert.equal(doc.subjectKey, 'data structures');
      assert.equal(doc.attendanceCount, 0);
      assert.equal(doc.expiresAt.toMillis() - doc.createdAt.toMillis(), 5 * 60_000);
    });

    it('generates a different token for every session', async () => {
      const a = await startSession();
      const b = await startSession();
      assert.notEqual(a.token, b.token);
      assert.notEqual(a.sessionId, b.sessionId);
    });
  });

  // ---------------------------------------------------------------- markAttendance
  describe('markAttendance', () => {
    it('records attendance atomically and returns details', async () => {
      const { sessionId, token } = await startSession();
      clock = T0 + 30_000;

      const result = await handlers.markAttendance(as('student1', { sessionId, token }));
      assert.equal(result.status, 'SUCCESS');
      assert.equal(result.subject, 'Data Structures');
      assert.equal(result.teacherName, 'Dr. Rao');
      assert.equal(result.markedAt, T0 + 30_000);

      const record = (await db.doc(`sessions/${sessionId}/attendance/student1`).get()).data();
      assert.equal(record.studentId, 'student1');
      assert.equal(record.studentName, 'Asha Verma');
      assert.equal(record.rollNumber, '21CS045');
      assert.equal(record.status, 'present');
      assert.equal(record.markedAt.toMillis(), T0 + 30_000);

      const history = (await db.doc(`users/student1/attendance/${sessionId}`).get()).data();
      assert.equal(history.subject, 'Data Structures');
      assert.equal(history.teacherId, 'teacher1');

      const session = (await db.doc(`sessions/${sessionId}`).get()).data();
      assert.equal(session.attendanceCount, 1);
    });

    it('prevents duplicate attendance', async () => {
      const { sessionId, token } = await startSession();
      await handlers.markAttendance(as('student1', { sessionId, token }));
      await assert.rejects(
        handlers.markAttendance(as('student1', { sessionId, token })),
        isHttpsError('already-exists', 'ALREADY_MARKED'),
      );
      const records = await db.collection(`sessions/${sessionId}/attendance`).get();
      assert.equal(records.size, 1);
      assert.equal((await db.doc(`sessions/${sessionId}`).get()).get('attendanceCount'), 1);
    });

    it('records exactly one attendance for concurrent duplicate requests', async () => {
      const { sessionId, token } = await startSession();
      const results = await Promise.allSettled([
        handlers.markAttendance(as('student1', { sessionId, token })),
        handlers.markAttendance(as('student1', { sessionId, token })),
        handlers.markAttendance(as('student1', { sessionId, token })),
      ]);
      assert.equal(results.filter((r) => r.status === 'fulfilled').length, 1);
      for (const r of results.filter((x) => x.status === 'rejected')) {
        assert.equal(r.reason.details.reason, 'ALREADY_MARKED');
      }
      assert.equal((await db.doc(`sessions/${sessionId}`).get()).get('attendanceCount'), 1);
    });

    it('lets different students mark the same session', async () => {
      const { sessionId, token } = await startSession();
      await handlers.markAttendance(as('student1', { sessionId, token }));
      await handlers.markAttendance(as('student2', { sessionId, token }));
      assert.equal((await db.doc(`sessions/${sessionId}`).get()).get('attendanceCount'), 2);
    });

    it('rejects an expired QR code using server time', async () => {
      const { sessionId, token } = await startSession('teacher1', 'Maths', 2);
      clock = T0 + 2 * 60_000; // exactly at expiry
      await assert.rejects(
        handlers.markAttendance(as('student1', { sessionId, token })),
        isHttpsError('failed-precondition', 'SESSION_EXPIRED'),
      );
      assert.equal((await db.doc(`sessions/${sessionId}/attendance/student1`).get()).exists, false);
    });

    it('rejects scans after the teacher ended the session early', async () => {
      const { sessionId, token } = await startSession();
      await db.doc(`sessions/${sessionId}`).update({ status: 'expired' });
      await assert.rejects(
        handlers.markAttendance(as('student1', { sessionId, token })),
        isHttpsError('failed-precondition', 'SESSION_EXPIRED'),
      );
    });

    it('rejects a token mismatch', async () => {
      const { sessionId } = await startSession();
      const forged = 'A'.repeat(43);
      await assert.rejects(
        handlers.markAttendance(as('student1', { sessionId, token: forged })),
        isHttpsError('permission-denied', 'INVALID_TOKEN'),
      );
    });

    it('rejects a token from another session', async () => {
      const first = await startSession('teacher1', 'Maths');
      const second = await startSession('teacher2', 'Physics');
      await assert.rejects(
        handlers.markAttendance(as('student1', { sessionId: second.sessionId, token: first.token })),
        isHttpsError('permission-denied', 'INVALID_TOKEN'),
      );
    });

    it('rejects unknown sessions', async () => {
      await assert.rejects(
        handlers.markAttendance(as('student1', { sessionId: 'doesNotExist', token: 'B'.repeat(43) })),
        isHttpsError('not-found', 'SESSION_NOT_FOUND'),
      );
    });

    it('rejects malformed payloads', async () => {
      await assert.rejects(
        handlers.markAttendance(as('student1', { sessionId: 'x' })),
        isHttpsError('invalid-argument', 'INVALID_PAYLOAD'),
      );
    });

    it('rejects teachers', async () => {
      const { sessionId, token } = await startSession();
      await assert.rejects(
        handlers.markAttendance(as('teacher2', { sessionId, token })),
        isHttpsError('permission-denied', 'NOT_A_STUDENT'),
      );
    });

    it('rejects users without a profile', async () => {
      const { sessionId, token } = await startSession();
      await assert.rejects(
        handlers.markAttendance(as('ghost', { sessionId, token })),
        isHttpsError('failed-precondition', 'PROFILE_MISSING'),
      );
    });

    it('requires authentication', async () => {
      const { sessionId, token } = await startSession();
      await assert.rejects(
        handlers.markAttendance(as(null, { sessionId, token })),
        isHttpsError('unauthenticated', 'UNAUTHENTICATED'),
      );
    });
  });

  // ---------------------------------------------------------------- statistics
  describe('getStudentStats', () => {
    it('computes attended / conducted per course', async () => {
      const s1 = await startSession('teacher1', 'Data Structures');
      clock += 1000;
      await startSession('teacher1', 'data   structures'); // same course (case/space-insensitive)
      clock += 1000;
      const s3 = await startSession('teacher2', 'Algebra');
      clock += 1000;
      await startSession('teacher2', 'Physics'); // student never attended → not counted

      await handlers.markAttendance(as('student1', { sessionId: s1.sessionId, token: s1.token }));
      await handlers.markAttendance(as('student1', { sessionId: s3.sessionId, token: s3.token }));

      const stats = await handlers.getStudentStats(as('student1', {}));
      assert.equal(stats.attended, 2);
      assert.equal(stats.conducted, 3);
      assert.equal(stats.percentage, 66.7);
      const ds = stats.courses.find((c) => c.subject === 'Data Structures');
      assert.deepEqual([ds.attended, ds.conducted], [1, 2]);
    });

    it('returns empty stats for a new student', async () => {
      const stats = await handlers.getStudentStats(as('student2', {}));
      assert.equal(stats.attended, 0);
      assert.equal(stats.conducted, 0);
      assert.equal(stats.percentage, null);
    });

    it('is restricted to students', async () => {
      await assert.rejects(handlers.getStudentStats(as('teacher1', {})), isHttpsError('permission-denied', 'NOT_A_STUDENT'));
    });
  });

  // ---------------------------------------------------------------- scheduled cleanup
  describe('expireStaleSessions', () => {
    it('marks only overdue active sessions as expired', async () => {
      const overdue = await startSession('teacher1', 'Maths', 2);
      clock = T0 + 60_000;
      const current = await startSession('teacher1', 'Physics', 15);
      clock = T0 + 3 * 60_000;

      const count = await handlers.expireStaleSessions();
      assert.equal(count, 1);
      assert.equal((await db.doc(`sessions/${overdue.sessionId}`).get()).get('status'), 'expired');
      assert.equal((await db.doc(`sessions/${current.sessionId}`).get()).get('status'), 'active');
    });
  });
});

// ---------------------------------------------------------------- registerUser (needs Auth emulator)
describe('registerUser (emulator)', { skip: SKIP_AUTH }, () => {
  let app;
  let db;
  let auth;
  let handlers;

  const register = (data) => handlers.registerUser({ data });
  const base = { name: 'Asha Verma', email: 'asha@college.edu', password: 'secret123', role: 'student', rollNumber: '21CS045' };

  before(() => {
    app = initializeApp({ projectId: PROJECT_ID }, `register-test-${Date.now()}`);
    db = getFirestore(app);
    auth = getAuth(app);
    handlers = createHandlers({ db, auth });
  });

  after(async () => {
    await deleteApp(app);
  });

  beforeEach(async () => {
    await clearFirestore();
    await clearAuth();
  });

  it('creates a student account and profile with the role set by the server', async () => {
    const result = await register(base);
    assert.equal(result.role, 'student');
    const profile = (await db.doc(`users/${result.uid}`).get()).data();
    assert.equal(profile.role, 'student');
    assert.equal(profile.email, 'asha@college.edu');
    assert.equal(profile.rollNumber, '21CS045');
    assert.ok(profile.createdAt);
    const user = await auth.getUser(result.uid);
    assert.equal(user.email, 'asha@college.edu');
  });

  it('rejects duplicate email registration', async () => {
    await register(base);
    await assert.rejects(register({ ...base, email: 'ASHA@college.edu' }), isHttpsError('already-exists', 'EMAIL_EXISTS'));
  });

  it('rejects teacher registration without an administrator invite', async () => {
    await assert.rejects(
      register({ ...base, email: 'rao@college.edu', role: 'teacher', rollNumber: '' }),
      isHttpsError('permission-denied', 'TEACHER_NOT_APPROVED'),
    );
    await assert.rejects(auth.getUserByEmail('rao@college.edu'), (e) => e.code === 'auth/user-not-found');
  });

  it('creates a teacher when invited, and the invite is single-use', async () => {
    await db.doc('teacherInvites/rao@college.edu').set({ used: false });
    const result = await register({ ...base, name: 'Dr. Rao', email: 'Rao@College.edu', role: 'teacher', rollNumber: '' });
    assert.equal(result.role, 'teacher');
    const invite = (await db.doc('teacherInvites/rao@college.edu').get()).data();
    assert.equal(invite.used, true);
    assert.equal(invite.usedBy, result.uid);

    await auth.deleteUser(result.uid);
    await assert.rejects(
      register({ ...base, email: 'rao@college.edu', role: 'teacher', rollNumber: '' }),
      isHttpsError('permission-denied', 'TEACHER_NOT_APPROVED'),
    );
  });

  it('rejects an attempt to self-assign a privileged role', async () => {
    await assert.rejects(register({ ...base, role: 'admin' }), isHttpsError('invalid-argument', 'INVALID_INPUT'));
  });
});
