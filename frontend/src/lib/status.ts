import type { ApplicationStatus, InterviewType } from '../api/types';

interface StatusMeta {
  label: string;
  /** Identity colour for the status. Always shown next to the label, never on its own. */
  color: string;
  description: string;
}

// Palette validated for the dark surface (lightness band, chroma, adjacent CVD / normal-vision separation).
export const STATUS_META: Record<ApplicationStatus, StatusMeta> = {
  WISHLIST: { label: 'Wishlist', color: '#d55181', description: 'Saved, not applied yet' },
  APPLIED: { label: 'Applied', color: '#3987e5', description: 'Waiting for a reply' },
  INTERVIEW: { label: 'Interview', color: '#c98500', description: 'In the interview process' },
  OFFER: { label: 'Offer', color: '#199e70', description: 'Offer received' },
  REJECTED: { label: 'Rejected', color: '#d95926', description: 'Closed by the company' },
  GHOSTED: { label: 'Ghosted', color: '#9085e9', description: 'No reply' },
};

export const INTERVIEW_TYPE_LABEL: Record<InterviewType, string> = {
  PHONE_SCREEN: 'Phone screen',
  TECHNICAL: 'Technical',
  BEHAVIORAL: 'Behavioral',
  ONSITE: 'On-site',
  FINAL: 'Final round',
  OTHER: 'Other',
};

export function statusLabel(status: ApplicationStatus): string {
  return STATUS_META[status].label;
}
