import { BellRing, CalendarClock } from 'lucide-react';
import { EmptyState, ErrorState, Spinner } from '../components/Feedback';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { useStats } from '../hooks/queries';
import { usePanels } from '../hooks/usePanels';
import { formatDate, formatDateTime, relativeDay } from '../lib/dates';
import { INTERVIEW_TYPE_LABEL } from '../lib/status';

export function FollowUpsPage() {
  const { data, isLoading, error, refetch } = useStats();
  const panels = usePanels();

  if (isLoading) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  if (!data) return null;

  const overdue = data.upcomingFollowUps.filter((f) => f.overdue);
  const upcoming = data.upcomingFollowUps.filter((f) => !f.overdue);

  return (
    <>
      <PageHeader title="Follow-ups" subtitle="Active applications with a follow-up due in the next 14 days, plus scheduled interviews." />

      {data.upcomingFollowUps.length === 0 && data.upcomingInterviews.length === 0 && (
        <EmptyState icon={<BellRing size={22} />} title="You're all caught up">
          Set a follow-up date on an application and it will appear here.
        </EmptyState>
      )}

      <div className="followups">
        {overdue.length > 0 && (
          <FollowUpGroup title="Overdue" tone="warn" items={overdue} onOpen={panels.openApp} />
        )}
        {upcoming.length > 0 && <FollowUpGroup title="Coming up" items={upcoming} onOpen={panels.openApp} />}

        {data.upcomingInterviews.length > 0 && (
          <section className="panel-card">
            <header className="panel-card-head">
              <h2>Interviews</h2>
            </header>
            <ul className="agenda">
              {data.upcomingInterviews.map((i) => (
                <li key={i.interviewId}>
                  <button type="button" className="agenda-row" onClick={() => panels.openApp(i.applicationId)}>
                    <CalendarClock size={16} aria-hidden="true" />
                    <span className="agenda-main">
                      <strong>{i.company}</strong>
                      <span className="muted">
                        {INTERVIEW_TYPE_LABEL[i.type]} · {i.role}
                      </span>
                    </span>
                    <time className="agenda-when" dateTime={i.scheduledAt}>
                      {formatDateTime(i.scheduledAt)}
                    </time>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </>
  );
}

function FollowUpGroup({
  title,
  items,
  tone,
  onOpen,
}: {
  title: string;
  items: { applicationId: number; company: string; role: string; status: Parameters<typeof StatusBadge>[0]['status']; followUpOn: string }[];
  tone?: 'warn';
  onOpen: (id: number) => void;
}) {
  return (
    <section className="panel-card">
      <header className="panel-card-head">
        <h2 className={tone === 'warn' ? 'text-warn' : undefined}>{title}</h2>
        <span className="count">{items.length}</span>
      </header>
      <ul className="agenda">
        {items.map((f) => (
          <li key={f.applicationId}>
            <button type="button" className="agenda-row" onClick={() => onOpen(f.applicationId)}>
              <span className="agenda-main">
                <strong>{f.company}</strong>
                <span className="muted">{f.role}</span>
              </span>
              <StatusBadge status={f.status} />
              <span className={`agenda-when${tone === 'warn' ? ' text-warn' : ''}`}>
                {relativeDay(f.followUpOn)}
                <span className="muted small"> · {formatDate(f.followUpOn)}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
