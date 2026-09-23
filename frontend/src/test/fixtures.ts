import type { ApplicationSummary } from '../api/types';

export function summary(overrides: Partial<ApplicationSummary> = {}): ApplicationSummary {
  return {
    id: 1,
    company: 'Acme',
    role: 'Junior Developer',
    location: 'Tunis',
    salaryRange: null,
    status: 'APPLIED',
    appliedOn: '2026-03-01',
    followUpOn: null,
    tags: [],
    interviewCount: 0,
    updatedAt: '2026-03-01T10:00:00Z',
    ...overrides,
  };
}
