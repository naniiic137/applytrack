import { Inbox, Plus } from 'lucide-react';
import { KanbanBoard } from '../components/KanbanBoard';
import { EmptyState, ErrorState, Spinner } from '../components/Feedback';
import { PageHeader } from '../components/PageHeader';
import { BOARD_QUERY, useApplications, useChangeStatus } from '../hooks/queries';
import { usePanels } from '../hooks/usePanels';

export function BoardPage() {
  const { data, isLoading, error, refetch } = useApplications(BOARD_QUERY);
  const changeStatus = useChangeStatus();
  const panels = usePanels();

  const total = data?.totalElements ?? 0;

  return (
    <>
      <PageHeader
        title="Board"
        subtitle={data ? `${total} application${total === 1 ? '' : 's'} · drag a card to change its status` : undefined}
        actions={
          <button type="button" className="btn btn-primary desktop-only" onClick={panels.startCreate}>
            <Plus size={16} /> New application
          </button>
        }
      />
      {changeStatus.isError && <ErrorState error={changeStatus.error} />}
      {isLoading && <Spinner />}
      {error && <ErrorState error={error} onRetry={() => void refetch()} />}
      {data && total === 0 && (
        <EmptyState icon={<Inbox size={22} />} title="No applications yet">
          <button type="button" className="btn btn-primary" onClick={panels.startCreate}>
            Add your first application
          </button>
        </EmptyState>
      )}
      {data && total > 0 && (
        <KanbanBoard
          applications={data.content}
          onOpen={panels.openApp}
          onMove={(id, status) => changeStatus.mutate({ id, status })}
        />
      )}
    </>
  );
}
