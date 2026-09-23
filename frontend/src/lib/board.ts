import { STATUSES, type ApplicationStatus, type ApplicationSummary } from '../api/types';

export type BoardColumns = Record<ApplicationStatus, ApplicationSummary[]>;

/** Groups applications into one column per status, keeping the incoming order inside each column. */
export function groupByStatus(applications: ApplicationSummary[]): BoardColumns {
  const columns = Object.fromEntries(STATUSES.map((s) => [s, [] as ApplicationSummary[]])) as BoardColumns;
  for (const app of applications) columns[app.status].push(app);
  return columns;
}

/**
 * Returns a copy of the list with one application moved to a new status (and bumped to the top),
 * used for the optimistic update while the PATCH request is in flight.
 */
export function moveApplication(
  applications: ApplicationSummary[],
  id: number,
  status: ApplicationStatus,
  now: Date = new Date(),
): ApplicationSummary[] {
  const moved = applications.find((a) => a.id === id);
  if (!moved || moved.status === status) return applications;
  const updated: ApplicationSummary = {
    ...moved,
    status,
    appliedOn: moved.appliedOn ?? (status === 'WISHLIST' ? null : localIsoDate(now)),
    updatedAt: now.toISOString(),
  };
  return [updated, ...applications.filter((a) => a.id !== id)];
}

function localIsoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Parses the comma-separated tag input from the form. */
export function parseTags(input: string): string[] {
  const seen = new Set<string>();
  for (const raw of input.split(',')) {
    const tag = raw.trim().toLowerCase();
    if (tag) seen.add(tag);
  }
  return [...seen];
}
