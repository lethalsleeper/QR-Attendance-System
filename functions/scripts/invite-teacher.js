#!/usr/bin/env node
'use strict';

/**
 * Administrator tool: approve an email address for a TEACHER account.
 *
 * Usage (from the functions/ folder):
 *   node scripts/invite-teacher.js teacher@college.edu --project <your-project-id>
 *   node scripts/invite-teacher.js teacher@college.edu --revoke --project <your-project-id>
 *
 * Credentials (one of):
 *   - Emulator:   set FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 (no credentials needed)
 *   - Production: `gcloud auth application-default login`, or set
 *                 GOOGLE_APPLICATION_CREDENTIALS to a service-account key JSON file.
 *
 * Alternatively create the document by hand in the Firebase console:
 *   collection "teacherInvites", document id = the lowercase email, field used = false.
 */

const { initializeApp } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');

function parseArgs(argv) {
  const args = { email: null, projectId: process.env.GCLOUD_PROJECT || null, revoke: false };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--project') {
      args.projectId = argv[i + 1];
      i += 1;
    } else if (arg === '--revoke') {
      args.revoke = true;
    } else if (!arg.startsWith('--')) {
      args.email = arg.trim().toLowerCase();
    }
  }
  return args;
}

async function main() {
  const { email, projectId, revoke } = parseArgs(process.argv.slice(2));
  if (!email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    console.error('Usage: node scripts/invite-teacher.js <email> --project <project-id> [--revoke]');
    process.exit(1);
  }
  if (!projectId) {
    console.error('Missing --project <project-id>.');
    process.exit(1);
  }

  initializeApp({ projectId });
  const ref = getFirestore().collection('teacherInvites').doc(email);

  if (revoke) {
    await ref.delete();
    console.log(`Removed teacher invite for ${email}.`);
    return;
  }

  await ref.set({ email, used: false, createdAt: FieldValue.serverTimestamp() }, { merge: true });
  console.log(`Approved ${email} for teacher registration in project ${projectId}.`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
