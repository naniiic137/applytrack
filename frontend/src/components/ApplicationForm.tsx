import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { STATUSES, type ApplicationDetail, type ApplicationInput, type ApplicationStatus } from '../api/types';
import { useApplication, useSaveApplication } from '../hooks/queries';
import { parseTags } from '../lib/board';
import { STATUS_META } from '../lib/status';
import { Spinner } from './Feedback';
import { Modal } from './Modal';

interface Props {
  applicationId: number | null;
  onClose: () => void;
  onSaved: (id: number) => void;
}

/** Create / edit dialog. For edits it waits for the detail query before rendering the form. */
export function ApplicationFormDialog({ applicationId, onClose, onSaved }: Props) {
  const { data, isLoading } = useApplication(applicationId);
  const title = applicationId ? 'Edit application' : 'New application';
  return (
    <Modal title={title} onClose={onClose}>
      <h2 className="panel-title">{title}</h2>
      {applicationId && isLoading ? (
        <Spinner />
      ) : (
        <ApplicationForm key={data?.id ?? 'new'} initial={data} onCancel={onClose} onSaved={onSaved} />
      )}
    </Modal>
  );
}

interface FormState {
  company: string;
  role: string;
  location: string;
  url: string;
  salaryRange: string;
  status: ApplicationStatus;
  appliedOn: string;
  followUpOn: string;
  tags: string;
  notes: string;
}

function toState(a?: ApplicationDetail): FormState {
  return {
    company: a?.company ?? '',
    role: a?.role ?? '',
    location: a?.location ?? '',
    url: a?.url ?? '',
    salaryRange: a?.salaryRange ?? '',
    status: a?.status ?? 'WISHLIST',
    appliedOn: a?.appliedOn ?? '',
    followUpOn: a?.followUpOn ?? '',
    tags: a?.tags.join(', ') ?? '',
    notes: a?.notes ?? '',
  };
}

export function toInput(s: FormState): ApplicationInput {
  const opt = (v: string) => (v.trim() ? v.trim() : null);
  return {
    company: s.company.trim(),
    role: s.role.trim(),
    location: opt(s.location),
    url: opt(s.url),
    salaryRange: opt(s.salaryRange),
    status: s.status,
    appliedOn: opt(s.appliedOn),
    followUpOn: opt(s.followUpOn),
    notes: opt(s.notes),
    tags: parseTags(s.tags),
  };
}

function ApplicationForm({
  initial,
  onCancel,
  onSaved,
}: {
  initial?: ApplicationDetail;
  onCancel: () => void;
  onSaved: (id: number) => void;
}) {
  const [form, setForm] = useState<FormState>(() => toState(initial));
  const save = useSaveApplication();
  const fieldErrors = save.error instanceof ApiError ? save.error.fieldErrors : {};
  const generalError =
    save.error && Object.keys(fieldErrors).length === 0 ? (save.error as Error).message : null;

  const set = <K extends keyof FormState>(key: K) =>
    (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value as FormState[K] }));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate({ id: initial?.id, input: toInput(form) }, { onSuccess: (detail) => onSaved(detail.id) });
  };

  const err = (name: string) =>
    fieldErrors[name] ? (
      <span className="field-error" role="alert">
        {fieldErrors[name]}
      </span>
    ) : null;

  return (
    <form className="form" onSubmit={submit} noValidate>
      <div className="form-grid">
        <label className="field">
          <span>Company *</span>
          <input value={form.company} onChange={set('company')} required maxLength={120} autoFocus />
          {err('company')}
        </label>
        <label className="field">
          <span>Role *</span>
          <input value={form.role} onChange={set('role')} required maxLength={120} />
          {err('role')}
        </label>
        <label className="field">
          <span>Location</span>
          <input value={form.location} onChange={set('location')} placeholder="Tunis, Remote..." maxLength={120} />
          {err('location')}
        </label>
        <label className="field">
          <span>Salary range</span>
          <input value={form.salaryRange} onChange={set('salaryRange')} placeholder="EUR 35-40k" maxLength={80} />
          {err('salaryRange')}
        </label>
        <label className="field field-wide">
          <span>Job posting URL</span>
          <input value={form.url} onChange={set('url')} type="url" placeholder="https://" maxLength={500} />
          {err('url')}
        </label>
        <label className="field">
          <span>Status</span>
          <select value={form.status} onChange={set('status')}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_META[s].label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Tags</span>
          <input value={form.tags} onChange={set('tags')} placeholder="react, remote" />
          {err('tags')}
        </label>
        <label className="field">
          <span>Applied on</span>
          <input type="date" value={form.appliedOn} onChange={set('appliedOn')} />
        </label>
        <label className="field">
          <span>Follow up on</span>
          <input type="date" value={form.followUpOn} onChange={set('followUpOn')} />
          {err('followUpAfterApplied')}
        </label>
        <label className="field field-wide">
          <span>Notes</span>
          <textarea value={form.notes} onChange={set('notes')} rows={4} maxLength={4000} />
          {err('notes')}
        </label>
      </div>
      {generalError && <p className="form-error">{generalError}</p>}
      <div className="form-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {save.isPending ? 'Saving...' : initial ? 'Save changes' : 'Add application'}
        </button>
      </div>
    </form>
  );
}
