'use strict';

/**
 * Firestore security rules tests (../../firestore.rules), run against the emulator:
 *   npm run test:emulator
 */

const { describe, it, before, beforeEach, after } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const firebase = require('firebase/compat/app');
require('firebase/compat/firestore');
const { PROJECT_ID, SKIP_FIRESTORE } = require('./helpers');

const RULES_PATH = path.join(__dirname, '..', '..', '..', 'firestore.rules');

describe('Firestore security rules (emulator)', { skip: SKIP_FIRESTORE }, () => {
  let testEnv;
  const now = Date.now();
  const ts = (ms) => firebase.firestore.Timestamp.fromMillis(ms);
  const serverTimestamp = () => firebase.firestore.FieldValue.serverTimestamp();

  const db = (uid) => (uid ? testEnv.authenticatedContext(uid).firestore() : testEnv.unauthenticatedContext().firestore());

  before(async () => {
    const [host, port] = process.env.FIRESTORE_EMULATOR_HOST.split(':');
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: { rules: fs.readFileSync(RULES_PATH, 'utf8'), host, port: Number(port) },
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const admin = context.firestore();
      const user = (uid, role, name) => ({
        uid,
        name,
        email: `${uid}@college.edu`,
        role,
        rollNumber: role === 'student' ? 'R1' : null,
        createdAt: ts(now),
      });
      await admin.doc('users/teacher1').set(user('teacher1', 'teacher', 'Dr. Rao'));
      await admin.doc('users/teacher2').set(user('teacher2', 'teacher', 'Ms. Iyer'));
      await admin.doc('users/student1').set(user('student1', 'student', 'Asha'));
      await admin.doc('users/student2').set(user('student2', 'student', 'Ravi'));
      await admin.doc('sessions/s1').set({
        sessionId: 's1',
        teacherId: 'teacher1',
        teacherName: 'Dr. Rao',
        subject: 'Maths',
        subjectKey: 'maths',
        createdAt: ts(now),
        expiresAt: ts(now + 300_000),
        token: 'secret-token-secret-token-secret-token-abc',
        status: 'active',
        durationMinutes: 5,
        attendanceCount: 1,
      });
      await admin.doc('sessions/s1/attendance/student1').set({
        studentId: 'student1',
        studentName: 'Asha',
        rollNumber: 'R1',
        markedAt: ts(now),
        status: 'present',
      });
      await admin.doc('users/student1/attendance/s1').set({
        sessionId: 's1',
        subject: 'Maths',
        teacherId: 'teacher1',
        teacherName: 'Dr. Rao',
        markedAt: ts(now),
        status: 'present',
      });
      await admin.doc('teacherInvites/new@college.edu').set({ used: false });
    });
  });

  // ------------------------------------------------------------------ users
  describe('users', () => {
    it('a user can read only their own profile', async () => {
      await assertSucceeds(db('student1').doc('users/student1').get());
      await assertFails(db('student1').doc('users/student2').get());
      await assertFails(db('student1').collection('users').get());
      await assertFails(db(null).doc('users/student1').get());
    });

    it('clients cannot create profiles (registration goes through the function)', async () => {
      await assertFails(
        db('newUser').doc('users/newUser').set({ uid: 'newUser', name: 'X Y', role: 'teacher', email: 'x@y.com' }),
      );
    });

    it('a user cannot change their role, email or roll number', async () => {
      await assertFails(db('student1').doc('users/student1').update({ role: 'teacher' }));
      await assertFails(db('student1').doc('users/student1').update({ email: 'other@college.edu' }));
      await assertFails(db('student1').doc('users/student1').update({ rollNumber: 'R2' }));
      await assertFails(db('student1').doc('users/student1').update({ name: 'Asha V', role: 'teacher' }));
    });

    it('a user can update their own name only', async () => {
      await assertSucceeds(db('student1').doc('users/student1').update({ name: 'Asha Verma' }));
      await assertFails(db('student1').doc('users/student2').update({ name: 'Hacked' }));
      await assertFails(db('student1').doc('users/student1').update({ name: 'A' }));
    });

    it('students can read only their own attendance history and never write it', async () => {
      await assertSucceeds(db('student1').collection('users/student1/attendance').get());
      await assertFails(db('student2').collection('users/student1/attendance').get());
      await assertFails(db('student1').doc('users/student1/attendance/s2').set({ subject: 'Fake' }));
    });
  });

  // ------------------------------------------------------------------ teacher invites
  it('teacher invites are not accessible to clients', async () => {
    await assertFails(db('student1').doc('teacherInvites/new@college.edu').get());
    await assertFails(db('teacher1').collection('teacherInvites').get());
    await assertFails(db('student1').doc('teacherInvites/student1@college.edu').set({ used: false }));
  });

  // ------------------------------------------------------------------ sessions
  describe('sessions', () => {
    it('only the owning teacher can read a session (token protected)', async () => {
      await assertSucceeds(db('teacher1').doc('sessions/s1').get());
      await assertFails(db('teacher2').doc('sessions/s1').get());
      await assertFails(db('student1').doc('sessions/s1').get());
      await assertFails(db(null).doc('sessions/s1').get());
    });

    it('teachers can list only their own sessions', async () => {
      await assertSucceeds(db('teacher1').collection('sessions').where('teacherId', '==', 'teacher1').get());
      await assertFails(db('teacher1').collection('sessions').get());
      await assertFails(db('teacher2').collection('sessions').where('teacherId', '==', 'teacher1').get());
    });

    it('clients cannot create or delete sessions', async () => {
      await assertFails(
        db('teacher1').doc('sessions/new').set({
          teacherId: 'teacher1',
          status: 'active',
          token: 'x'.repeat(43),
          expiresAt: ts(now + 999_999_999),
        }),
      );
      await assertFails(db('teacher1').doc('sessions/s1').delete());
    });

    it('the owner can end an active session', async () => {
      await assertSucceeds(
        db('teacher1').doc('sessions/s1').update({ status: 'expired', endedAt: serverTimestamp() }),
      );
    });

    it('nobody else can end it, and the owner cannot tamper with other fields', async () => {
      await assertFails(db('teacher2').doc('sessions/s1').update({ status: 'expired', endedAt: serverTimestamp() }));
      await assertFails(db('student1').doc('sessions/s1').update({ status: 'expired', endedAt: serverTimestamp() }));
      await assertFails(db('teacher1').doc('sessions/s1').update({ expiresAt: ts(now + 999_999_999) }));
      await assertFails(db('teacher1').doc('sessions/s1').update({ token: 'y'.repeat(43) }));
      await assertFails(db('teacher1').doc('sessions/s1').update({ attendanceCount: 99 }));
      await assertFails(
        db('teacher1').doc('sessions/s1').update({ status: 'expired', endedAt: ts(now + 999_999) }),
      );
    });

    it('an ended session cannot be re-activated', async () => {
      await testEnv.withSecurityRulesDisabled((context) =>
        context.firestore().doc('sessions/s1').update({ status: 'expired' }),
      );
      await assertFails(db('teacher1').doc('sessions/s1').update({ status: 'active' }));
      await assertFails(db('teacher1').doc('sessions/s1').update({ status: 'expired', endedAt: serverTimestamp() }));
    });
  });

  // ------------------------------------------------------------------ attendance
  describe('attendance', () => {
    it('only the owning teacher can read the attendance list', async () => {
      await assertSucceeds(db('teacher1').collection('sessions/s1/attendance').get());
      await assertFails(db('teacher2').collection('sessions/s1/attendance').get());
      await assertFails(db('student1').collection('sessions/s1/attendance').get());
    });

    it('students cannot write attendance directly', async () => {
      await assertFails(
        db('student2').doc('sessions/s1/attendance/student2').set({
          studentId: 'student2',
          studentName: 'Ravi',
          markedAt: ts(now),
          status: 'present',
        }),
      );
      await assertFails(db('student1').doc('sessions/s1/attendance/student1').update({ status: 'absent' }));
      await assertFails(db('student1').doc('sessions/s1/attendance/student1').delete());
    });

    it('teachers cannot modify attendance records either', async () => {
      await assertFails(db('teacher1').doc('sessions/s1/attendance/student1').delete());
      await assertFails(
        db('teacher1').doc('sessions/s1/attendance/student2').set({ studentId: 'student2', status: 'present' }),
      );
    });
  });

  it('unknown collections are denied', async () => {
    await assertFails(db('teacher1').collection('anything').get());
    await assertFails(db('teacher1').doc('anything/x').set({ a: 1 }));
  });
});
