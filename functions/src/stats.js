'use strict';

/**
 * Attendance statistics (pure functions).
 *
 * Definition used throughout the app:
 *   A "course" = one subject (case-insensitive) taught by one teacher.
 *   attended   = sessions of that course the student attended.
 *   conducted  = sessions of that course created on/after the student registered.
 *   percentage = Σ attended ÷ Σ conducted over every course the student has attended
 *                at least once, rounded to one decimal place.
 * There is no enrolment list in this prototype, so courses the student never attended
 * are not counted (documented as a known limitation in README.md).
 */

function courseKey(teacherId, subjectKey) {
  return `${teacherId}::${subjectKey}`;
}

/** Groups history documents (users/{uid}/attendance) into courses. */
function groupHistoryByCourse(historyEntries) {
  const courses = new Map();
  for (const entry of historyEntries) {
    if (!entry || typeof entry.teacherId !== 'string') continue;
    const subject = typeof entry.subject === 'string' ? entry.subject : 'Untitled';
    const subjectKey =
      typeof entry.subjectKey === 'string' && entry.subjectKey !== '' ? entry.subjectKey : subject.toLowerCase();
    const key = courseKey(entry.teacherId, subjectKey);
    const existing = courses.get(key);
    if (existing) {
      existing.attended += 1;
    } else {
      courses.set(key, {
        teacherId: entry.teacherId,
        subjectKey,
        subject,
        teacherName: typeof entry.teacherName === 'string' ? entry.teacherName : null,
        attended: 1,
      });
    }
  }
  return courses;
}

function roundPercentage(attended, conducted) {
  if (conducted <= 0) return null;
  return Math.round((attended * 1000) / conducted) / 10;
}

/**
 * @param courses     Map from groupHistoryByCourse
 * @param conductedBy Map courseKey → number of sessions conducted
 */
function buildStats(courses, conductedBy) {
  const rows = [];
  let attended = 0;
  let conducted = 0;
  for (const [key, course] of courses) {
    // A student can never have attended more sessions than were held.
    const held = Math.max(conductedBy.get(key) || 0, course.attended);
    attended += course.attended;
    conducted += held;
    rows.push({
      subject: course.subject,
      teacherName: course.teacherName,
      attended: course.attended,
      conducted: held,
      percentage: roundPercentage(course.attended, held),
    });
  }
  rows.sort((a, b) => a.subject.localeCompare(b.subject));
  return { attended, conducted, percentage: roundPercentage(attended, conducted), courses: rows };
}

module.exports = { courseKey, groupHistoryByCourse, buildStats, roundPercentage };
