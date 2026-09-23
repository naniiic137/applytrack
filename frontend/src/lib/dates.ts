const DAY_MS = 24 * 60 * 60 * 1000;

/** Parses "YYYY-MM-DD" as a local calendar date (not UTC midnight, which can shift the day). */
export function parseIsoDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

/** Whole calendar days from `today` to `isoDate` (negative = in the past). */
export function daysUntil(isoDate: string, today: Date = new Date()): number {
  return Math.round((parseIsoDate(isoDate).getTime() - startOfDay(today).getTime()) / DAY_MS);
}

/** "Today", "Tomorrow", "In 3 days", "Yesterday", "4 days ago" */
export function relativeDay(isoDate: string, today: Date = new Date()): string {
  const diff = daysUntil(isoDate, today);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Tomorrow';
  if (diff === -1) return 'Yesterday';
  return diff > 0 ? `In ${diff} days` : `${-diff} days ago`;
}

const shortDate = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' });
const longDate = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
const dateTime = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDate(isoDate: string | null | undefined, withYear = false): string {
  if (!isoDate) return '-';
  const date = isoDate.length === 10 ? parseIsoDate(isoDate) : new Date(isoDate);
  return (withYear ? longDate : shortDate).format(date);
}

export function formatDateTime(isoInstant: string): string {
  return dateTime.format(new Date(isoInstant));
}

/** Value for an <input type="datetime-local"> from an ISO instant, in local time. */
export function toDateTimeLocal(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${toIsoDate(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
