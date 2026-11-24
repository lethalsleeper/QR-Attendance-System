'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { courseKey, groupHistoryByCourse, buildStats, roundPercentage } = require('../../src/stats');

describe('stats', () => {
  const history = [
    { teacherId: 't1', subject: 'Data Structures', subjectKey: 'data structures', teacherName: 'Dr. Rao' },
    { teacherId: 't1', subject: 'Data Structures', subjectKey: 'data structures', teacherName: 'Dr. Rao' },
    { teacherId: 't2', subject: 'Algebra', subjectKey: 'algebra', teacherName: 'Ms. Iyer' },
    { teacherId: 't1', subject: 'Algebra', teacherName: 'Dr. Rao' }, // legacy doc without subjectKey
  ];

  it('groups history by teacher + subject', () => {
    const courses = groupHistoryByCourse(history);
    assert.equal(courses.size, 3);
    assert.equal(courses.get(courseKey('t1', 'data structures')).attended, 2);
    assert.equal(courses.get(courseKey('t2', 'algebra')).attended, 1);
    assert.equal(courses.get(courseKey('t1', 'algebra')).attended, 1);
  });

  it('computes totals and a one-decimal percentage', () => {
    const courses = groupHistoryByCourse(history);
    const conducted = new Map([
      [courseKey('t1', 'data structures'), 3],
      [courseKey('t2', 'algebra'), 2],
      [courseKey('t1', 'algebra'), 1],
    ]);
    const stats = buildStats(courses, conducted);
    assert.equal(stats.attended, 4);
    assert.equal(stats.conducted, 6);
    assert.equal(stats.percentage, 66.7);
    assert.deepEqual(stats.courses.map((c) => c.subject), ['Algebra', 'Algebra', 'Data Structures']);
  });

  it('never reports fewer conducted than attended sessions', () => {
    const courses = groupHistoryByCourse(history.slice(0, 2));
    const stats = buildStats(courses, new Map());
    assert.equal(stats.conducted, 2);
    assert.equal(stats.percentage, 100);
  });

  it('returns null percentage when nothing was conducted', () => {
    assert.equal(roundPercentage(0, 0), null);
    assert.deepEqual(buildStats(new Map(), new Map()), { attended: 0, conducted: 0, percentage: null, courses: [] });
  });
});
