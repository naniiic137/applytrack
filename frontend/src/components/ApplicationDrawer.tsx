import { useState, type FormEvent } from 'react';
import { CalendarClock, ExternalLink, MapPin, Pencil, Plus, Trash2, Wallet } from 'lucide-react';
import { INTERVIEW_TYPES, STATUSES, type ApplicationDetail, type InterviewType } from '../api/types';
import {
  useAddInterview,
  useApplication,
  useChangeStatus,
  useDeleteApplication,
  useDeleteInterview,
} from '../hooks/queries';
import { daysUntil, formatDate, formatDateTime, relativeDay, toDateTimeLocal } from '../lib/dates';
import { INTERVIEW_TYPE_LABEL, STATUS_META } from '../lib/status';
import { ErrorState, Spinner } from './Feedback';
import { Modal } from './Modal';
import { StatusDot } from './StatusBadge';

interface Props {
  applicationId: number;
  onClose: () => void;
  onEdit: (id: number) => void;
}

export function ApplicationDrawer({ applicationId, onClose, onEdit }: Props) {
  const { data, isLoading, error, refetch } = useApplication(applicationId);

  return (
    <Modal title={data ? `${data.company} - ${data.role}` : 'Application'} onClose={onClose} variant="drawer">
      {isLoading && <Spinner />}
      {error && <ErrorState error={error} onRetry={() => void refetch()} />}
      {data && <DrawerContent app={data} onClose={onClose} onEdit={onEdit} />}
    </Modal>
  );
}

function DrawerContent({ app, onClose, onEdit }: { app: ApplicationDetail; onClose: () => void; onEdit: (id: number) => void }) {
  const changeStatus = useChangeStatus();
  const remove = useDeleteApplication();

  const followUpDiff = app.followUpOn ? daysUntil(app.followUpOn) : null;

  return (
    <div className="drawer">
      <header className="drawer-header">
        <div className="company-avatar" aria-hidden="true">
          {app.company.charAt(0)}
        </div>
        <div className="drawer-heading">
          <h2>{app.company}</h2>
          <p className="muted">{app.role}</p>
        </div>
      </header>

      <div className="drawer-toolbar">
        <label className="status-select">
          <span className="sr-only">Status</span>
          <StatusDot status={app.status} />
          <select
            value={app.status}
            onChange={(e) => changeStatus.mutate({ id: app.id, status: e.target.value as ApplicationDetail['status'] })}
            disabled={changeStatus.isPending}
          >
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_META[s].label}
              </option>
            ))}
          </select>
        </label>
        <div className="toolbar-actions">
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => onEdit(app.id)}>
            <Pencil size={14} /> Edit
          </button>
          <button
            type="button"
            className="btn btn-danger btn-sm"
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm(`Delete the application to ${app.company}?`)) {
                remove.mutate(app.id, { onSuccess: onClose });
              }
            }}
          >
            <Trash2 size={14} /> Delete
          </button>
        </div>
      </div>

      <dl className="facts">
        <div>
          <dt>
            <MapPin size={14} /> Location
          </dt>
          <dd>{app.location ?? '-'}</dd>
        </div>
        <div>
          <dt>
            <Wallet size={14} /> Salary
          </dt>
          <dd>{app.salaryRange ?? '-'}</dd>
        </div>
        <div>
          <dt>Applied</dt>
          <dd>{formatDate(app.appliedOn, true)}</dd>
        </div>
        <div>
          <dt>Follow up</dt>
          <dd className={followUpDiff !== null && followUpDiff < 0 ? 'text-warn' : undefined}>
            {app.followUpOn ? `${formatDate(app.followUpOn)} · ${relativeDay(app.followUpOn)}` : '-'}
          </dd>
        </div>
      </dl>

      {app.url && (
        <a className="posting-link" href={app.url} target="_blank" rel="noreferrer noopener">
          <ExternalLink size={14} /> View job posting
        </a>
      )}

      {app.tags.length > 0 && (
        <div className="tag-row">
          {app.tags.map((t) => (
            <span key={t} className="tag">
              {t}
            </span>
          ))}
        </div>
      )}

      {app.notes && (
        <section className="drawer-section">
          <h3>Notes</h3>
          <p className="notes">{app.notes}</p>
        </section>
      )}

      <InterviewsSection app={app} />

      <section className="drawer-section">
        <h3>Timeline</h3>
        <ol className="timeline">
          {[...app.timeline].reverse().map((change) => (
            <li key={change.id}>
              <span className="timeline-dot" style={{ borderColor: STATUS_META[change.toStatus].color }} />
              <div>
                <p>
                  {change.fromStatus ? (
                    <>
                      Moved from <strong>{STATUS_META[change.fromStatus].label}</strong> to{' '}
                      <strong>{STATUS_META[change.toStatus].label}</strong>
                    </>
                  ) : (
                    <>
                      Added as <strong>{STATUS_META[change.toStatus].label}</strong>
                    </>
                  )}
                </p>
                <time className="muted" dateTime={change.changedAt}>
                  {formatDateTime(change.changedAt)}
                </time>
              </div>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}

function InterviewsSection({ app }: { app: ApplicationDetail }) {
  const [adding, setAdding] = useState(false);
  const [when, setWhen] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 3);
    d.setHours(10, 0, 0, 0);
    return toDateTimeLocal(d);
  });
  const [type, setType] = useState<InterviewType>('TECHNICAL');
  const [notes, setNotes] = useState('');
  const add = useAddInterview(app.id);
  const remove = useDeleteInterview(app.id);
  const now = Date.now();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    add.mutate(
      { scheduledAt: new Date(when).toISOString(), type, notes: notes.trim() || null },
      {
        onSuccess: () => {
          setAdding(false);
          setNotes('');
        },
      },
    );
  };

  return (
    <section className="drawer-section">
      <div className="section-head">
        <h3>Interviews</h3>
        {!adding && (
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAdding(true)}>
            <Plus size={14} /> Add
          </button>
        )}
      </div>

      {adding && (
        <form className="interview-form" onSubmit={submit}>
          <input
            type="datetime-local"
            value={when}
            onChange={(e) => setWhen(e.target.value)}
            required
            aria-label="Date and time"
          />
          <select value={type} onChange={(e) => setType(e.target.value as InterviewType)} aria-label="Type">
            {INTERVIEW_TYPES.map((t) => (
              <option key={t} value={t}>
                {INTERVIEW_TYPE_LABEL[t]}
              </option>
            ))}
          </select>
          <input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Notes (optional)" aria-label="Notes" />
          <div className="form-actions">
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAdding(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary btn-sm" disabled={add.isPending}>
              Save interview
            </button>
          </div>
        </form>
      )}

      {app.interviews.length === 0 && !adding ? (
        <p className="muted small">No interviews yet.</p>
      ) : (
        <ul className="interview-list">
          {app.interviews.map((i) => {
            const upcoming = new Date(i.scheduledAt).getTime() >= now;
            return (
              <li key={i.id} className={upcoming ? 'is-upcoming' : undefined}>
                <CalendarClock size={16} aria-hidden="true" />
                <div className="interview-body">
                  <p>
                    <strong>{INTERVIEW_TYPE_LABEL[i.type]}</strong>
                    {upcoming && <span className="pill">Upcoming</span>}
                  </p>
                  <time className="muted small" dateTime={i.scheduledAt}>
                    {formatDateTime(i.scheduledAt)}
                  </time>
                  {i.notes && <p className="small">{i.notes}</p>}
                </div>
                <button
                  type="button"
                  className="icon-btn"
                  aria-label="Delete interview"
                  onClick={() => remove.mutate(i.id)}
                >
                  <Trash2 size={14} />
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
