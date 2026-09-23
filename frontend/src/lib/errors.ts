import { ApiError } from '../api/client';

/** Problem `code` the API sends when an update was based on an out-of-date version (optimistic locking). */
export const STALE_VERSION_CODE = 'stale_version';

export const STALE_VERSION_MESSAGE =
  'This application was updated elsewhere (another tab or device), so it has been refreshed. Please try again.';

/** True for the 409 the API returns when someone else changed the application since we read it. */
export function isStaleVersion(error: unknown): boolean {
  return error instanceof ApiError && error.status === 409 && error.problem.code === STALE_VERSION_CODE;
}

/** User-facing message for any error thrown by the API client. */
export function errorMessage(error: unknown): string {
  if (isStaleVersion(error)) return STALE_VERSION_MESSAGE;
  return error instanceof Error && error.message ? error.message : 'Something went wrong';
}
