import { useMemo, useState } from 'react';
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  TouchSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import { CalendarClock, MapPin } from 'lucide-react';
import { STATUSES, type ApplicationStatus, type ApplicationSummary } from '../api/types';
import { groupByStatus } from '../lib/board';
import { daysUntil, formatDate, relativeDay } from '../lib/dates';
import { STATUS_META } from '../lib/status';
import { StatusDot } from './StatusBadge';

interface Props {
  applications: ApplicationSummary[];
  onMove: (id: number, status: ApplicationStatus) => void;
  onOpen: (id: number) => void;
}

export function KanbanBoard({ applications, onMove, onOpen }: Props) {
  const columns = useMemo(() => groupByStatus(applications), [applications]);
  const [active, setActive] = useState<ApplicationSummary | null>(null);

  const sensors = useSensors(
    // A small distance threshold keeps plain clicks working (they open the drawer).
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    // On touch screens a short press starts the drag so horizontal scrolling still works.
    useSensor(TouchSensor, { activationConstraint: { delay: 220, tolerance: 8 } }),
    useSensor(KeyboardSensor),
  );

  const handleStart = (event: DragStartEvent) => {
    setActive(applications.find((a) => a.id === event.active.id) ?? null);
  };

  const handleEnd = (event: DragEndEvent) => {
    setActive(null);
    const target = event.over?.id as ApplicationStatus | undefined;
    const card = applications.find((a) => a.id === event.active.id);
    if (card && target && card.status !== target) onMove(card.id, target);
  };

  return (
    <DndContext
      sensors={sensors}
      onDragStart={handleStart}
      onDragEnd={handleEnd}
      onDragCancel={() => setActive(null)}
      accessibility={{
        announcements: {
          onDragStart: ({ active: a }) => `Picked up application ${a.id}.`,
          onDragOver: ({ over }) => (over ? `Over ${STATUS_META[over.id as ApplicationStatus].label}.` : ''),
          onDragEnd: ({ over }) => (over ? `Moved to ${STATUS_META[over.id as ApplicationStatus].label}.` : 'Dropped.'),
          onDragCancel: () => 'Move cancelled.',
        },
      }}
    >
      <div className="board" role="list" aria-label="Applications by status">
        {STATUSES.map((status) => (
          <Column key={status} status={status} items={columns[status]} onOpen={onOpen} />
        ))}
      </div>
      <DragOverlay dropAnimation={{ duration: 160, easing: 'ease-out' }}>
        {active ? <Card app={active} overlay /> : null}
      </DragOverlay>
    </DndContext>
  );
}

function Column({
  status,
  items,
  onOpen,
}: {
  status: ApplicationStatus;
  items: ApplicationSummary[];
  onOpen: (id: number) => void;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: status });
  return (
    <section ref={setNodeRef} className={`column${isOver ? ' is-over' : ''}`} role="listitem" aria-label={STATUS_META[status].label}>
      <header className="column-head">
        <StatusDot status={status} />
        <h2>{STATUS_META[status].label}</h2>
        <span className="count">{items.length}</span>
      </header>
      <div className="column-body">
        {items.map((app) => (
          <DraggableCard key={app.id} app={app} onOpen={onOpen} />
        ))}
        {items.length === 0 && <p className="column-empty">Drop here</p>}
      </div>
    </section>
  );
}

function DraggableCard({ app, onOpen }: { app: ApplicationSummary; onOpen: (id: number) => void }) {
  const { setNodeRef, attributes, listeners, isDragging } = useDraggable({ id: app.id });
  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      className={`card-wrap${isDragging ? ' is-dragging' : ''}`}
      aria-label={`${app.company}, ${app.role}. Press space to move, enter to open.`}
      onClick={() => onOpen(app.id)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') onOpen(app.id);
        else listeners?.onKeyDown?.(e);
      }}
    >
      <Card app={app} />
    </div>
  );
}

export function Card({ app, overlay = false }: { app: ApplicationSummary; overlay?: boolean }) {
  const followUp = app.followUpOn ? daysUntil(app.followUpOn) : null;
  const showFollowUp = followUp !== null && followUp <= 7 && ['WISHLIST', 'APPLIED', 'INTERVIEW'].includes(app.status);
  return (
    <article className={`card${overlay ? ' is-overlay' : ''}`}>
      <div className="card-top">
        <div className="company-avatar sm" aria-hidden="true">
          {app.company.charAt(0)}
        </div>
        <div className="card-title">
          <h3>{app.company}</h3>
          <p>{app.role}</p>
        </div>
      </div>
      <div className="card-meta">
        {app.location && (
          <span>
            <MapPin size={12} aria-hidden="true" /> {app.location}
          </span>
        )}
        {app.appliedOn && <span>Applied {formatDate(app.appliedOn)}</span>}
      </div>
      {(app.tags.length > 0 || showFollowUp || app.interviewCount > 0) && (
        <div className="card-foot">
          {app.tags.slice(0, 3).map((t) => (
            <span key={t} className="tag sm">
              {t}
            </span>
          ))}
          {showFollowUp && (
            <span className={`due${followUp! < 0 ? ' overdue' : ''}`}>
              <CalendarClock size={12} aria-hidden="true" /> {relativeDay(app.followUpOn!)}
            </span>
          )}
        </div>
      )}
    </article>
  );
}
