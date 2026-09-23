import { describe, expect, it } from 'vitest';
import { daysUntil, parseIsoDate, relativeDay, toIsoDate } from './dates';

describe('dates', () => {
  const today = new Date(2026, 2, 10, 18, 30); // 10 March 2026, evening local time

  it('parses ISO dates as local calendar days', () => {
    const d = parseIsoDate('2026-03-01');
    expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 2, 1]);
    expect(toIsoDate(d)).toBe('2026-03-01');
  });

  it('counts whole days regardless of the time of day', () => {
    expect(daysUntil('2026-03-10', today)).toBe(0);
    expect(daysUntil('2026-03-11', today)).toBe(1);
    expect(daysUntil('2026-03-07', today)).toBe(-3);
    expect(daysUntil('2026-04-10', today)).toBe(31);
  });

  it('describes relative days in plain words', () => {
    expect(relativeDay('2026-03-10', today)).toBe('Today');
    expect(relativeDay('2026-03-11', today)).toBe('Tomorrow');
    expect(relativeDay('2026-03-09', today)).toBe('Yesterday');
    expect(relativeDay('2026-03-14', today)).toBe('In 4 days');
    expect(relativeDay('2026-03-05', today)).toBe('5 days ago');
  });
});
