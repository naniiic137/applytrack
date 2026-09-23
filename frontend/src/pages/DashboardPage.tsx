import { Bar, BarChart, CartesianGrid, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { CalendarClock } from 'lucide-react';
import { Link } from 'react-router-dom';
import { STATUSES } from '../api/types';
import { EmptyState, ErrorState, Spinner } from '../components/Feedback';
import { PageHeader } from '../components/PageHeader';
import { useStats } from '../hooks/queries';
import { usePanels } from '../hooks/usePanels';
import { formatDate, formatDateTime, relativeDay } from '../lib/dates';
import { INTERVIEW_TYPE_LABEL, STATUS_META } from '../lib/status';

const AXIS = { fill: 'var(--text-muted)', fontSize: 12 };

interface TooltipProps {
  active?: boolean;
  label?: string | number;
  payload?: { value?: number | string; payload?: { name?: string } }[];
  labelFormatter?: (label: string) => string;
  unit: string;
}

function ChartTooltip({ active, payload, label, labelFormatter, unit }: TooltipProps) {
  if (!active || !payload?.length) return null;
  const value = Number(payload[0].value ?? 0);
  const title = labelFormatter ? labelFormatter(String(label)) : payload[0].payload?.name ?? String(label);
  return (
    <div className="chart-tooltip">
      <span className="muted small">{title}</span>
      <strong>
        {value} {unit}
        {value === 1 ? '' : 's'}
      </strong>
    </div>
  );
}

export function DashboardPage() {
  const { data, isLoading, error, refetch } = useStats();
  const panels = usePanels();

  if (isLoading) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  if (!data) return null;

  const pipeline = STATUSES.map((s) => ({ status: s, name: STATUS_META[s].label, count: data.byStatus[s] }));
  const weekly = data.applicationsPerWeek;
  const thisWeek = weekly.at(-1)?.count ?? 0;
  const weeklyAverage = weekly.reduce((sum, w) => sum + w.count, 0) / Math.max(weekly.length, 1);

  return (
    <>
      <PageHeader title="Dashboard" subtitle="How your search is going, based on every status change so far." />

      {data.total === 0 ? (
        <EmptyState title="Nothing to measure yet">Add a few applications and your numbers will show up here.</EmptyState>
      ) : (
        <>
          <div className="kpis">
            <Kpi label="Tracked" value={data.total} hint={`${data.active} still active`} />
            <Kpi label="Submitted" value={data.submitted} hint={`${thisWeek} this week`} />
            <Kpi label="Response rate" value={`${data.responseRate}%`} hint="got any reply" />
            <Kpi label="Interview rate" value={`${data.interviewRate}%`} hint="reached an interview" />
          </div>

          <div className="dash-grid">
            <section className="panel-card span-2">
              <header className="panel-card-head">
                <h2>Applications per week</h2>
                <span className="muted small">Last 12 weeks · avg {weeklyAverage.toFixed(1)}/week</span>
              </header>
              <div className="chart" style={{ height: 240 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={weekly} margin={{ top: 8, right: 4, left: -24, bottom: 0 }} barCategoryGap="22%">
                    <CartesianGrid vertical={false} stroke="var(--grid)" />
                    <XAxis
                      dataKey="weekStart"
                      tickFormatter={(d: string) => formatDate(d)}
                      tick={AXIS}
                      tickLine={false}
                      axisLine={false}
                      interval="preserveStartEnd"
                      minTickGap={16}
                    />
                    <YAxis allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} width={48} />
                    <Tooltip
                      cursor={{ fill: 'var(--hover)' }}
                      content={
                        <ChartTooltip unit="application" labelFormatter={(d) => `Week of ${formatDate(d, true)}`} />
                      }
                    />
                    <Bar dataKey="count" fill="var(--accent)" radius={[4, 4, 0, 0]} maxBarSize={28} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </section>

            <section className="panel-card">
              <header className="panel-card-head">
                <h2>Pipeline</h2>
                <span className="muted small">Current status</span>
              </header>
              <div className="chart" style={{ height: 240 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={pipeline} layout="vertical" margin={{ top: 0, right: 28, left: 0, bottom: 0 }} barCategoryGap={6}>
                    <XAxis type="number" hide allowDecimals={false} />
                    <YAxis type="category" dataKey="name" tick={AXIS} tickLine={false} axisLine={false} width={78} />
                    <Tooltip cursor={{ fill: 'var(--hover)' }} content={<ChartTooltip unit="application" />} />
                    <Bar dataKey="count" radius={[0, 4, 4, 0]} maxBarSize={22} minPointSize={2}>
                      {pipeline.map((p) => (
                        <Cell key={p.status} fill={STATUS_META[p.status].color} />
                      ))}
                      <LabelList dataKey="count" position="right" fill="var(--text-2)" fontSize={12} />
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </section>

            <section className="panel-card span-2">
              <header className="panel-card-head">
                <h2>Upcoming interviews</h2>
              </header>
              {data.upcomingInterviews.length === 0 ? (
                <p className="muted small">No interviews scheduled.</p>
              ) : (
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
              )}
            </section>

            <section className="panel-card">
              <header className="panel-card-head">
                <h2>Follow-ups</h2>
                <Link to="/follow-ups" className="small">
                  See all
                </Link>
              </header>
              {data.upcomingFollowUps.length === 0 ? (
                <p className="muted small">Nothing due in the next two weeks.</p>
              ) : (
                <ul className="agenda compact">
                  {data.upcomingFollowUps.slice(0, 4).map((f) => (
                    <li key={f.applicationId}>
                      <button type="button" className="agenda-row" onClick={() => panels.openApp(f.applicationId)}>
                        <span className="agenda-main">
                          <strong>{f.company}</strong>
                          <span className="muted">{STATUS_META[f.status].label}</span>
                        </span>
                        <span className={`agenda-when${f.overdue ? ' text-warn' : ''}`}>
                          {f.overdue ? 'Overdue' : relativeDay(f.followUpOn)}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </>
      )}
    </>
  );
}

function Kpi({ label, value, hint }: { label: string; value: string | number; hint: string }) {
  return (
    <div className="kpi">
      <span className="kpi-label">{label}</span>
      <strong className="kpi-value">{value}</strong>
      <span className="kpi-hint">{hint}</span>
    </div>
  );
}
