'use strict';

/**
 * Pure validation helpers shared by the Cloud Functions. Nothing in this file touches
 * Firestore, so every rule here is covered by fast unit tests (test/unit).
 * Keep these rules in sync with the Android client (InputValidator / QrPayloadCodec /
 * SessionExpiryPolicy) – the server is the source of truth.
 */

const crypto = require('node:crypto');

const ROLES = Object.freeze({ TEACHER: 'teacher', STUDENT: 'student' });

const SESSION_STATUS = Object.freeze({ ACTIVE: 'active', EXPIRED: 'expired' });

/** Session lengths (minutes) a teacher may choose. */
const ALLOWED_DURATIONS = Object.freeze([2, 5, 10, 15, 20]);

/** Machine-readable reasons sent to the app in HttpsError.details.reason. */
const REASONS = Object.freeze({
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  INVALID_INPUT: 'INVALID_INPUT',
  INVALID_PAYLOAD: 'INVALID_PAYLOAD',
  INVALID_TOKEN: 'INVALID_TOKEN',
  SESSION_NOT_FOUND: 'SESSION_NOT_FOUND',
  SESSION_EXPIRED: 'SESSION_EXPIRED',
  ALREADY_MARKED: 'ALREADY_MARKED',
  NOT_A_STUDENT: 'NOT_A_STUDENT',
  NOT_A_TEACHER: 'NOT_A_TEACHER',
  PROFILE_MISSING: 'PROFILE_MISSING',
  TEACHER_NOT_APPROVED: 'TEACHER_NOT_APPROVED',
  EMAIL_EXISTS: 'EMAIL_EXISTS',
  WEAK_PASSWORD: 'WEAK_PASSWORD',
  REGISTRATION_FAILED: 'REGISTRATION_FAILED',
});

const EMAIL_REGEX = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const ROLL_NUMBER_REGEX = /^[A-Za-z0-9/_-]{1,30}$/;
const SESSION_ID_REGEX = /^[A-Za-z0-9_-]{1,128}$/;
const TOKEN_REGEX = /^[A-Za-z0-9_-]{32,128}$/;
const TOKEN_BYTES = 32;

class ValidationError extends Error {
  constructor(reason, message) {
    super(message);
    this.name = 'ValidationError';
    this.reason = reason;
  }
}

function normalizeWhitespace(value) {
  return value.trim().replace(/\s+/g, ' ');
}

function asObject(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new ValidationError(REASONS.INVALID_INPUT, 'Request data is missing.');
  }
  return data;
}

/** Validates the registerUser payload. The role is only a *request*; teachers need an invite. */
function validateRegistration(rawData) {
  const data = asObject(rawData);
  const name = typeof data.name === 'string' ? normalizeWhitespace(data.name) : '';
  const email = typeof data.email === 'string' ? data.email.trim().toLowerCase() : '';
  const password = typeof data.password === 'string' ? data.password : '';
  const role = data.role;
  const rollNumber = typeof data.rollNumber === 'string' ? data.rollNumber.trim() : '';

  if (name.length < 2 || name.length > 60) {
    throw new ValidationError(REASONS.INVALID_INPUT, 'Name must be between 2 and 60 characters.');
  }
  if (email.length > 254 || !EMAIL_REGEX.test(email)) {
    throw new ValidationError(REASONS.INVALID_INPUT, 'Enter a valid email address.');
  }
  if (
    password.length < 8 ||
    password.length > 128 ||
    !/\p{L}/u.test(password) ||
    !/\p{Nd}/u.test(password)
  ) {
    throw new ValidationError(
      REASONS.WEAK_PASSWORD,
      'Password must be at least 8 characters long and contain letters and numbers.',
    );
  }
  if (role !== ROLES.TEACHER && role !== ROLES.STUDENT) {
    throw new ValidationError(REASONS.INVALID_INPUT, 'Role must be "teacher" or "student".');
  }
  if (role === ROLES.STUDENT && rollNumber === '') {
    throw new ValidationError(REASONS.INVALID_INPUT, 'Roll number is required for students.');
  }
  if (rollNumber !== '' && !ROLL_NUMBER_REGEX.test(rollNumber)) {
    throw new ValidationError(
      REASONS.INVALID_INPUT,
      'Roll number may only contain letters, numbers, "-", "/" and "_" (max 30 characters).',
    );
  }
  return { name, email, password, role, rollNumber: rollNumber === '' ? null : rollNumber };
}

/** Validates the createSession payload. */
function validateCreateSession(rawData) {
  const data = asObject(rawData);
  const subject = typeof data.subject === 'string' ? normalizeWhitespace(data.subject) : '';
  const durationMinutes = data.durationMinutes;

  if (subject.length < 2 || subject.length > 60) {
    throw new ValidationError(REASONS.INVALID_INPUT, 'Subject must be between 2 and 60 characters.');
  }
  if (!Number.isInteger(durationMinutes) || !ALLOWED_DURATIONS.includes(durationMinutes)) {
    throw new ValidationError(
      REASONS.INVALID_INPUT,
      `Duration must be one of: ${ALLOWED_DURATIONS.join(', ')} minutes.`,
    );
  }
  return { subject, subjectKey: subject.toLowerCase(), durationMinutes };
}

/** Validates the markAttendance payload (the scanned QR content). */
function validateAttendancePayload(rawData) {
  if (!rawData || typeof rawData !== 'object') {
    throw new ValidationError(REASONS.INVALID_PAYLOAD, 'Invalid QR code.');
  }
  const { sessionId, token } = rawData;
  if (typeof sessionId !== 'string' || !SESSION_ID_REGEX.test(sessionId)) {
    throw new ValidationError(REASONS.INVALID_PAYLOAD, 'Invalid QR code.');
  }
  if (typeof token !== 'string' || !TOKEN_REGEX.test(token)) {
    throw new ValidationError(REASONS.INVALID_PAYLOAD, 'Invalid QR code.');
  }
  return { sessionId, token };
}

/** 256-bit cryptographically secure token, base64url encoded (43 characters). */
function generateToken() {
  return crypto.randomBytes(TOKEN_BYTES).toString('base64url');
}

/** Constant-time comparison so the token can't be guessed byte by byte via timing. */
function tokensMatch(expected, provided) {
  if (typeof expected !== 'string' || typeof provided !== 'string') return false;
  const a = Buffer.from(expected, 'utf8');
  const b = Buffer.from(provided, 'utf8');
  if (a.length !== b.length) return false;
  return crypto.timingSafeEqual(a, b);
}

/** Converts Firestore Timestamp / Date / number to epoch millis (or null). */
function toMillis(value) {
  if (value === null || value === undefined) return null;
  if (typeof value === 'number') return value;
  if (value instanceof Date) return value.getTime();
  if (typeof value.toMillis === 'function') return value.toMillis();
  return null;
}

/** A session is expired when its stored status is not active or server time has reached expiresAt. */
function isSessionExpired(session, nowMs) {
  if (!session || session.status !== SESSION_STATUS.ACTIVE) return true;
  const expiresAtMs = toMillis(session.expiresAt);
  return expiresAtMs === null || nowMs >= expiresAtMs;
}

/**
 * Core attendance decision. Returns null when attendance may be recorded, otherwise a
 * REASONS value. Order matters:
 *  1. unknown session          → SESSION_NOT_FOUND
 *  2. token mismatch           → INVALID_TOKEN (forged / old QR)
 *  3. already recorded         → ALREADY_MARKED (more helpful than "expired" on a re-scan)
 *  4. expired by server clock  → SESSION_EXPIRED
 */
function evaluateAttendance({ session, token, nowMs, alreadyMarked }) {
  if (!session) return REASONS.SESSION_NOT_FOUND;
  if (!tokensMatch(session.token, token)) return REASONS.INVALID_TOKEN;
  if (alreadyMarked) return REASONS.ALREADY_MARKED;
  if (isSessionExpired(session, nowMs)) return REASONS.SESSION_EXPIRED;
  return null;
}

module.exports = {
  ROLES,
  SESSION_STATUS,
  ALLOWED_DURATIONS,
  REASONS,
  ValidationError,
  validateRegistration,
  validateCreateSession,
  validateAttendancePayload,
  generateToken,
  tokensMatch,
  toMillis,
  isSessionExpired,
  evaluateAttendance,
};
