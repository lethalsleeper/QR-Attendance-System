'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  REASONS,
  ValidationError,
  validateRegistration,
  validateCreateSession,
  validateAttendancePayload,
  generateToken,
  tokensMatch,
  isSessionExpired,
  evaluateAttendance,
} = require('../../src/validation');

const validStudent = {
  name: '  Asha   Verma ',
  email: ' Asha@College.EDU ',
  password: 'secret123',
  role: 'student',
  rollNumber: '21CS045',
};

function expectValidationError(fn, reason) {
  assert.throws(fn, (error) => error instanceof ValidationError && error.reason === reason);
}

describe('validateRegistration', () => {
  it('normalises a valid student registration', () => {
    const result = validateRegistration(validStudent);
    assert.deepEqual(result, {
      name: 'Asha Verma',
      email: 'asha@college.edu',
      password: 'secret123',
      role: 'student',
      rollNumber: '21CS045',
    });
  });

  it('allows a teacher without a roll number', () => {
    const result = validateRegistration({ ...validStudent, role: 'teacher', rollNumber: '' });
    assert.equal(result.rollNumber, null);
  });

  it('requires a roll number for students', () => {
    expectValidationError(() => validateRegistration({ ...validStudent, rollNumber: '   ' }), REASONS.INVALID_INPUT);
  });

  it('rejects roles other than teacher / student (e.g. admin)', () => {
    expectValidationError(() => validateRegistration({ ...validStudent, role: 'admin' }), REASONS.INVALID_INPUT);
  });

  it('rejects weak passwords', () => {
    expectValidationError(() => validateRegistration({ ...validStudent, password: 'short1' }), REASONS.WEAK_PASSWORD);
    expectValidationError(() => validateRegistration({ ...validStudent, password: 'lettersonly' }), REASONS.WEAK_PASSWORD);
    expectValidationError(() => validateRegistration({ ...validStudent, password: '12345678' }), REASONS.WEAK_PASSWORD);
  });

  it('rejects invalid emails and names', () => {
    expectValidationError(() => validateRegistration({ ...validStudent, email: 'not-an-email' }), REASONS.INVALID_INPUT);
    expectValidationError(() => validateRegistration({ ...validStudent, name: 'A' }), REASONS.INVALID_INPUT);
  });

  it('rejects roll numbers with illegal characters', () => {
    expectValidationError(
      () => validateRegistration({ ...validStudent, rollNumber: '21 CS <script>' }),
      REASONS.INVALID_INPUT,
    );
  });

  it('rejects missing data', () => {
    expectValidationError(() => validateRegistration(null), REASONS.INVALID_INPUT);
  });
});

describe('validateCreateSession', () => {
  it('accepts an allowed duration and normalises the subject', () => {
    assert.deepEqual(validateCreateSession({ subject: '  Data   Structures ', durationMinutes: 5 }), {
      subject: 'Data Structures',
      subjectKey: 'data structures',
      durationMinutes: 5,
    });
  });

  it('rejects durations that are not allowed', () => {
    for (const durationMinutes of [0, 1, 3, 60, 5.5, '5', null]) {
      expectValidationError(
        () => validateCreateSession({ subject: 'Maths', durationMinutes }),
        REASONS.INVALID_INPUT,
      );
    }
  });

  it('rejects empty subjects', () => {
    expectValidationError(() => validateCreateSession({ subject: '   ', durationMinutes: 5 }), REASONS.INVALID_INPUT);
  });
});

describe('validateAttendancePayload', () => {
  const token = generateToken();

  it('accepts a well-formed payload', () => {
    assert.deepEqual(validateAttendancePayload({ sessionId: 'abcDEF123', token }), { sessionId: 'abcDEF123', token });
  });

  it('rejects missing or malformed fields', () => {
    const bad = [
      {},
      { sessionId: 'abc' },
      { token },
      { sessionId: '../users/x', token },
      { sessionId: 'abc', token: 'short' },
      { sessionId: 123, token },
      null,
    ];
    for (const payload of bad) {
      expectValidationError(() => validateAttendancePayload(payload), REASONS.INVALID_PAYLOAD);
    }
  });
});

describe('generateToken / tokensMatch', () => {
  it('generates 256-bit base64url tokens that are unique', () => {
    const tokens = new Set(Array.from({ length: 200 }, () => generateToken()));
    assert.equal(tokens.size, 200);
    for (const t of tokens) assert.match(t, /^[A-Za-z0-9_-]{43}$/);
  });

  it('compares tokens exactly', () => {
    const t = generateToken();
    assert.equal(tokensMatch(t, t), true);
    assert.equal(tokensMatch(t, `${t.slice(0, -1)}x`), t.endsWith('x') ? true : false);
    assert.equal(tokensMatch(t, t.slice(1)), false);
    assert.equal(tokensMatch(undefined, t), false);
    assert.equal(tokensMatch(t, null), false);
  });
});

describe('expiry and attendance decisions', () => {
  const nowMs = 1_700_000_000_000;
  const token = generateToken();
  const activeSession = { status: 'active', token, expiresAt: nowMs + 60_000 };

  it('treats a session as expired exactly at expiresAt (server time)', () => {
    assert.equal(isSessionExpired({ ...activeSession, expiresAt: nowMs + 1 }, nowMs), false);
    assert.equal(isSessionExpired({ ...activeSession, expiresAt: nowMs }, nowMs), true);
    assert.equal(isSessionExpired({ ...activeSession, expiresAt: nowMs - 1 }, nowMs), true);
  });

  it('treats ended / malformed sessions as expired', () => {
    assert.equal(isSessionExpired({ ...activeSession, status: 'expired' }, nowMs), true);
    assert.equal(isSessionExpired({ ...activeSession, expiresAt: undefined }, nowMs), true);
    assert.equal(isSessionExpired(null, nowMs), true);
  });

  it('supports Firestore Timestamp-like expiry values', () => {
    const ts = { toMillis: () => nowMs + 10 };
    assert.equal(isSessionExpired({ ...activeSession, expiresAt: ts }, nowMs), false);
  });

  it('allows a valid scan', () => {
    assert.equal(evaluateAttendance({ session: activeSession, token, nowMs, alreadyMarked: false }), null);
  });

  it('returns SESSION_NOT_FOUND for unknown sessions', () => {
    assert.equal(
      evaluateAttendance({ session: null, token, nowMs, alreadyMarked: false }),
      REASONS.SESSION_NOT_FOUND,
    );
  });

  it('returns INVALID_TOKEN for a forged token, even if the session is expired', () => {
    assert.equal(
      evaluateAttendance({ session: { ...activeSession, expiresAt: 0 }, token: generateToken(), nowMs, alreadyMarked: false }),
      REASONS.INVALID_TOKEN,
    );
  });

  it('returns ALREADY_MARKED for duplicate scans (before expiry checks)', () => {
    assert.equal(
      evaluateAttendance({ session: activeSession, token, nowMs, alreadyMarked: true }),
      REASONS.ALREADY_MARKED,
    );
    assert.equal(
      evaluateAttendance({ session: { ...activeSession, expiresAt: 0 }, token, nowMs, alreadyMarked: true }),
      REASONS.ALREADY_MARKED,
    );
  });

  it('returns SESSION_EXPIRED after expiry', () => {
    assert.equal(
      evaluateAttendance({ session: activeSession, token, nowMs: nowMs + 60_000, alreadyMarked: false }),
      REASONS.SESSION_EXPIRED,
    );
  });
});
