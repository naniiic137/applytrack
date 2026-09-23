import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
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
import { ArrowRightLeft, CalendarClock, MapPin } from 'lucide-react';
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

/** How a card is named to screen readers: "Northwind Labs, Junior Developer". */
export function describeCard(app: ApplicationSummary | undefined): string {
  return app ? `${app.company}, ${app.role}` : 'application';
}

export function KanbanBoard({ applications, onMove, onOpen }: Props) {
  const columns = useMemo(() => groupByStatus(applications), [applications]);
  const [active, setActive] = useState<ApplicationSummary | null>(null);
  const byId = (id: string | number) => applications.find((a) => a.id === id);
  const column = (id: string | number) => STATUS_META[id as ApplicationStatus].label;

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
          onDragStart: ({ active: a }) => `Picked up ${describeCard(byId(a.id))}.`,
          onDragOver: ({ active: a, over }) =>
            over ? `${describeCard(byId(a.id))} is over the ${column(over.id)} column.` : '',
          onDragEnd: ({ active: a, over }) =>
            over
              ? `${describeCard(byId(a.id))} dropped in the ${column(over.id)} column.`
              : `${describeCard(byId(a.id))} dropped.`,
          onDragCancel: ({ active: a }) => `Moving ${describeCard(byId(a.id))} was cancelled.`,
        },
      }}
    >
      <div className="board" role="list" aria-label="Applications by status">
        {STATUSES.map((status) => (
          <Column key={status} status={status} items={columns[status]} onOpen={onOpen} onMove={onMove} />
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
  onMove,
}: {
  status: ApplicationStatus;
  items: ApplicationSummary[];
  onOpen: (id: number) => void;
  onMove: (id: number, status: ApplicationStatus) => void;
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
          <div key={app.id} className="card-shell">
            <DraggableCard app={app} onOpen={onOpen} />
            <MoveMenu app={app} onMove={onMove} />
          </div>
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

/**
 * Keyboard- and screen-reader-friendly alternative to drag and drop: a "Move to" menu button next to each
 * card (a sibling of the draggable, so no interactive element is nested inside another).
 */
function MoveMenu({ app, onMove }: { app: ApplicationSummary; onMove: (id: number, status: ApplicationStatus) => void }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLUListElement>(null);
  const targets = STATUSES.filter((s) => s !== app.status);

  useEffect(() => {
    if (!open) return;
    menuRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
    const onPointerDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open]);

  const close = () => {
    setOpen(false);
    buttonRef.current?.focus();
  };

  const onMenuKeyDown = (e: KeyboardEvent<HTMLUListElement>) => {
    const items = Array.from(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
    const index = items.indexOf(document.activeElement as HTMLElement);
    const focusAt = (i: number) => items[(i + items.length) % items.length]?.focus();
    if (e.key === 'ArrowDown') focusAt(index + 1);
    else if (e.key === 'ArrowUp') focusAt(index - 1);
    else if (e.key === 'Home') focusAt(0);
    else if (e.key === 'End') focusAt(items.length - 1);
    else if (e.key === 'Escape') {
      e.stopPropagation(); // do not also close a surrounding dialog
      close();
    } else if (e.key === 'Tab') setOpen(false);
    else return;
    if (e.key !== 'Tab') e.preventDefault();
  };

  return (
    <div ref={rootRef} className="move-menu">
      <button
        ref={buttonRef}
        type="button"
        className="icon-btn move-btn"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Move ${describeCard(app)} to another column`}
        title="Move to..."
        onClick={() => setOpen((o) => !o)}
      >
        <ArrowRightLeft size={14} aria-hidden="true" />
      </button>
      {open && (
        <ul ref={menuRef} role="menu" aria-label={`Move ${app.company} to`} className="move-list" onKeyDown={onMenuKeyDown}>
          {targets.map((s) => (
            <li key={s} role="none">
              <button
                type="button"
                role="menuitem"
                tabIndex={-1}
                onClick={() => {
                  close();
                  onMove(app.id, s);
                }}
              >
                <StatusDot status={s} /> {STATUS_META[s].label}
              </button>
            </li>
          ))}
        </ul>
      )}
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
