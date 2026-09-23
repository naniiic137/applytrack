// Types mirroring the backend DTOs (dev.hamza.applytrack.*Dtos).

export const STATUSES = ['WISHLIST', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED', 'GHOSTED'] as const;
export type ApplicationStatus = (typeof STATUSES)[number];

export const INTERVIEW_TYPES = ['PHONE_SCREEN', 'TECHNICAL', 'BEHAVIORAL', 'ONSITE', 'FINAL', 'OTHER'] as const;
export type InterviewType = (typeof INTERVIEW_TYPES)[number];

/** ISO date "YYYY-MM-DD" */
export type IsoDate = string;
/** ISO instant "2026-01-01T10:00:00Z" */
export type IsoInstant = string;

export interface User {
  id: number;
  email: string;
  displayName: string;
}

export interface AuthResponse {
  token: string;
  expiresAt: IsoInstant;
  user: User;
}

export interface ApplicationSummary {
  id: number;
  company: string;
  role: string;
  location: string | null;
  salaryRange: string | null;
  status: ApplicationStatus;
  appliedOn: IsoDate | null;
  followUpOn: IsoDate | null;
  tags: string[];
  interviewCount: number;
  updatedAt: IsoInstant;
}

export interface StatusChange {
  id: number;
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus;
  changedAt: IsoInstant;
}

export interface Interview {
  id: number;
  scheduledAt: IsoInstant;
  type: InterviewType;
  notes: string | null;
}

export interface ApplicationDetail {
  id: number;
  company: string;
  role: string;
  location: string | null;
  url: string | null;
  salaryRange: string | null;
  status: ApplicationStatus;
  appliedOn: IsoDate | null;
  followUpOn: IsoDate | null;
  notes: string | null;
  tags: string[];
  timeline: StatusChange[];
  interviews: Interview[];
  createdAt: IsoInstant;
  updatedAt: IsoInstant;
}

export interface ApplicationInput {
  company: string;
  role: string;
  location?: string | null;
  url?: string | null;
  salaryRange?: string | null;
  status?: ApplicationStatus;
  appliedOn?: IsoDate | null;
  followUpOn?: IsoDate | null;
  notes?: string | null;
  tags?: string[];
}

export interface InterviewInput {
  scheduledAt: IsoInstant;
  type: InterviewType;
  notes?: string | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApplicationQuery {
  status?: ApplicationStatus[];
  q?: string;
  tag?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface Stats {
  total: number;
  active: number;
  submitted: number;
  byStatus: Record<ApplicationStatus, number>;
  responseRate: number;
  interviewRate: number;
  applicationsPerWeek: { weekStart: IsoDate; count: number }[];
  upcomingFollowUps: {
    applicationId: number;
    company: string;
    role: string;
    status: ApplicationStatus;
    followUpOn: IsoDate;
    overdue: boolean;
  }[];
  upcomingInterviews: {
    interviewId: number;
    applicationId: number;
    company: string;
    role: string;
    scheduledAt: IsoInstant;
    type: InterviewType;
  }[];
}

/** RFC 9457 problem detail as returned by the API. */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}
