import { useMemo, useState } from 'react';
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, Plus, Search, SearchX } from 'lucide-react';
import { STATUSES, type ApplicationQuery, type ApplicationStatus } from '../api/types';
import { EmptyState, ErrorState, Spinner } from '../components/Feedback';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge, StatusDot } from '../components/StatusBadge';
import { useApplications, useTags } from '../hooks/queries';
import { useDebounced } from '../hooks/useDebounced';
import { usePanels } from '../hooks/usePanels';
import { daysUntil, formatDate } from '../lib/dates';
import { STATUS_META } from '../lib/status';

type SortField = 'company' | 'appliedOn' | 'followUpOn' | 'updatedAt';

const PAGE_SIZE = 10;

export function ListPage() {
  const panels = usePanels();
  const [search, setSearch] = useState('');
  const [statuses, setStatuses] = useState<ApplicationStatus[]>([]);
  const [tag, setTag] = useState('');
  const [sort, setSort] = useState<{ field: SortField; dir: 'asc' | 'desc' }>({ field: 'updatedAt', dir: 'desc' });
  const [page, setPage] = useState(0);
  const q = useDebounced(search);
  const tags = useTags();

  const query = useMemo<ApplicationQuery>(
    () => ({ q, status: statuses, tag: tag || undefined, page, size: PAGE_SIZE, sort: `${sort.field},${sort.dir}` }),
    [q, statuses, tag, page, sort],
  );
  const { data, isLoading, isFetching, error, refetch } = useApplications(query);

  const toggleStatus = (s: ApplicationStatus) => {
    setPage(0);
    setStatuses((prev) => (prev.includes(s) ? prev.filter((x) => x !== s) : [...prev, s]));
  };

  const sortBy = (field: SortField) => {
    setPage(0);
    setSort((prev) =>
      prev.field === field
        ? { field, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { field, dir: field === 'company' ? 'asc' : 'desc' },
    );
  };

  const header = (field: SortField, label: string) => (
    <th aria-sort={sort.field === field ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button type="button" className="th-sort" onClick={() => sortBy(field)}>
        {label}
        {sort.field === field && (sort.dir === 'asc' ? <ArrowUp size={13} /> : <ArrowDown size={13} />)}
      </button>
    </th>
  );

  return (
    <>
      <PageHeader
        title="Applications"
        subtitle="Search, filter and sort everything you have tracked."
        actions={
          <button type="button" className="btn btn-primary desktop-only" onClick={panels.startCreate}>
            <Plus size={16} /> New application
          </button>
        }
      />

      <div className="filters">
        <div className="search">
          <Search size={16} aria-hidden="true" />
          <input
            type="search"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            placeholder="Search company, role or location"
            aria-label="Search applications"
          />
        </div>
        <select
          className="select"
          value={tag}
          onChange={(e) => {
            setTag(e.target.value);
            setPage(0);
          }}
          aria-label="Filter by tag"
        >
          <option value="">All tags</option>
          {tags.data?.map((t) => (
            <option key={t} value={t}>
              #{t}
            </option>
          ))}
        </select>
      </div>
      <div className="chips" role="group" aria-label="Filter by status">
        {STATUSES.map((s) => (
          <button
            key={s}
            type="button"
            className={`chip${statuses.includes(s) ? ' is-active' : ''}`}
            aria-pressed={statuses.includes(s)}
            onClick={() => toggleStatus(s)}
          >
            <StatusDot status={s} />
            {STATUS_META[s].label}
          </button>
        ))}
        {statuses.length > 0 && (
          <button type="button" className="chip chip-clear" onClick={() => setStatuses([])}>
            Clear
          </button>
        )}
      </div>

      {isLoading && <Spinner />}
      {error && <ErrorState error={error} onRetry={() => void refetch()} />}
      {data && data.totalElements === 0 && (
        <EmptyState icon={<SearchX size={22} />} title="No matching applications">
          Try a different search or remove a filter.
        </EmptyState>
      )}
      {data && data.totalElements > 0 && (
        <div className={`table-card${isFetching ? ' is-fetching' : ''}`}>
          <table className="table">
            <thead>
              <tr>
                {header('company', 'Company')}
                <th>Status</th>
                <th className="hide-md">Location</th>
                {header('appliedOn', 'Applied')}
                {header('followUpOn', 'Follow-up')}
                <th className="hide-md">Tags</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((a) => {
                const due = a.followUpOn ? daysUntil(a.followUpOn) : null;
                return (
                  <tr key={a.id} onClick={() => panels.openApp(a.id)} className="row-link">
                    <td data-label="Company">
                      <button
                        type="button"
                        className="row-title"
                        onClick={(e) => {
                          e.stopPropagation();
                          panels.openApp(a.id);
                        }}
                      >
                        <strong>{a.company}</strong>
                        <span className="muted">{a.role}</span>
                      </button>
                    </td>
                    <td data-label="Status">
                      <StatusBadge status={a.status} />
                    </td>
                    <td data-label="Location" className="hide-md">
                      {a.location ?? '-'}
                    </td>
                    <td data-label="Applied">{formatDate(a.appliedOn)}</td>
                    <td data-label="Follow-up" className={due !== null && due < 0 ? 'text-warn' : undefined}>
                      {formatDate(a.followUpOn)}
                    </td>
                    <td data-label="Tags" className="hide-md">
                      <div className="tag-row">
                        {a.tags.map((t) => (
                          <span key={t} className="tag sm">
                            {t}
                          </span>
                        ))}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div className="pager">
            <span className="muted small">
              {data.page * data.size + 1}-{data.page * data.size + data.content.length} of {data.totalElements}
            </span>
            <div className="pager-buttons">
              <button
                type="button"
                className="icon-btn"
                disabled={data.page === 0}
                onClick={() => setPage((p) => p - 1)}
                aria-label="Previous page"
              >
                <ChevronLeft size={16} />
              </button>
              <button
                type="button"
                className="icon-btn"
                disabled={data.page + 1 >= data.totalPages}
                onClick={() => setPage((p) => p + 1)}
                aria-label="Next page"
              >
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
