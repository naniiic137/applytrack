import type { Problem } from './types';

/** Error thrown for any non-2xx response. Carries the parsed problem detail when available. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem;

  constructor(status: number, problem: Problem) {
    super(problem.detail || problem.title || `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  /** Field-level validation messages (empty object if none). */
  get fieldErrors(): Record<string, string> {
    return this.problem.errors ?? {};
  }
}

type TokenProvider = () => string | null;
type UnauthorizedHandler = () => void;

let getToken: TokenProvider = () => null;
let onUnauthorized: UnauthorizedHandler = () => {};

/** Wired once by the AuthProvider so the client stays framework-agnostic and testable. */
export function configureClient(options: { getToken: TokenProvider; onUnauthorized: UnauthorizedHandler }) {
  getToken = options.getToken;
  onUnauthorized = options.onUnauthorized;
}

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

/** Header carrying the browser's IANA time zone, so the API computes "today" the way the user sees it. */
export const TIME_ZONE_HEADER = 'X-Time-Zone';

export function browserTimeZone(): string | undefined {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || undefined;
  } catch {
    return undefined;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
  /** Skip the Authorization header and the global 401 handler (login / register). */
  anonymous?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  const timeZone = browserTimeZone();
  if (timeZone) headers[TIME_ZONE_HEADER] = timeZone;

  const token = options.anonymous ? null : getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(BASE_URL + path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
  });

  if (response.status === 401 && !options.anonymous) {
    onUnauthorized();
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function readProblem(response: Response): Promise<Problem> {
  try {
    const text = await response.text();
    if (text) return JSON.parse(text) as Problem;
  } catch {
    // not JSON - fall through
  }
  return { status: response.status, title: response.statusText };
}

/** Builds "?a=1&status=X&status=Y" - arrays become repeated keys, empty values are dropped. */
export function toQueryString(params: Record<string, string | number | string[] | undefined | null>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) value.forEach((v) => search.append(key, v));
    else search.append(key, String(value));
  }
  const qs = search.toString();
  return qs ? `?${qs}` : '';
}
