import { request, toQueryString } from './client';
import type {
  ApplicationDetail,
  ApplicationInput,
  ApplicationQuery,
  ApplicationStatus,
  ApplicationSummary,
  AuthResponse,
  Interview,
  InterviewInput,
  Page,
  Stats,
  User,
} from './types';

export const api = {
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body: { email, password }, anonymous: true }),

  register: (email: string, password: string, displayName: string) =>
    request<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: { email, password, displayName },
      anonymous: true,
    }),

  me: () => request<User>('/api/auth/me'),

  listApplications: (query: ApplicationQuery = {}, signal?: AbortSignal) =>
    request<Page<ApplicationSummary>>(
      '/api/applications' +
        toQueryString({
          status: query.status,
          q: query.q?.trim(),
          tag: query.tag,
          page: query.page,
          size: query.size,
          sort: query.sort,
        }),
      { signal },
    ),

  getApplication: (id: number, signal?: AbortSignal) =>
    request<ApplicationDetail>(`/api/applications/${id}`, { signal }),

  createApplication: (input: ApplicationInput) =>
    request<ApplicationDetail>('/api/applications', { method: 'POST', body: input }),

  updateApplication: (id: number, input: ApplicationInput) =>
    request<ApplicationDetail>(`/api/applications/${id}`, { method: 'PUT', body: input }),

  changeStatus: (id: number, status: ApplicationStatus, version: number) =>
    request<ApplicationDetail>(`/api/applications/${id}/status`, { method: 'PATCH', body: { status, version } }),

  deleteApplication: (id: number) => request<void>(`/api/applications/${id}`, { method: 'DELETE' }),

  addInterview: (id: number, input: InterviewInput) =>
    request<Interview>(`/api/applications/${id}/interviews`, { method: 'POST', body: input }),

  deleteInterview: (id: number, interviewId: number) =>
    request<void>(`/api/applications/${id}/interviews/${interviewId}`, { method: 'DELETE' }),

  tags: () => request<string[]>('/api/applications/tags'),

  stats: () => request<Stats>('/api/stats'),
};
