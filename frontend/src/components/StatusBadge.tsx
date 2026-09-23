import type { ApplicationStatus } from '../api/types';
import { STATUS_META } from '../lib/status';

export function StatusDot({ status }: { status: ApplicationStatus }) {
  return <span className="status-dot" style={{ background: STATUS_META[status].color }} aria-hidden="true" />;
}

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return (
    <span className="status-badge">
      <StatusDot status={status} />
      {STATUS_META[status].label}
    </span>
  );
}
