import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type {
  ApplicationInput,
  ApplicationQuery,
  ApplicationStatus,
  ApplicationSummary,
  InterviewInput,
  Page,
} from '../api/types';
import { moveApplication } from '../lib/board';

export const keys = {
  applications: ['applications'] as const,
  applicationList: (query: ApplicationQuery) => ['applications', 'list', query] as const,
  application: (id: number) => ['applications', 'detail', id] as const,
  tags: ['applications', 'tags'] as const,
  stats: ['stats'] as const,
};

/** Board shows everything at once; the API caps page size at 200. */
export const BOARD_QUERY: ApplicationQuery = { size: 200, sort: 'updatedAt,desc' };

export function useApplications(query: ApplicationQuery) {
  return useQuery({
    queryKey: keys.applicationList(query),
    queryFn: ({ signal }) => api.listApplications(query, signal),
    placeholderData: keepPreviousData,
  });
}

export function useApplication(id: number | null) {
  return useQuery({
    queryKey: keys.application(id ?? -1),
    queryFn: ({ signal }) => api.getApplication(id!, signal),
    enabled: id !== null,
  });
}

export function useTags() {
  return useQuery({ queryKey: keys.tags, queryFn: api.tags, staleTime: 60_000 });
}

export function useStats() {
  return useQuery({ queryKey: keys.stats, queryFn: api.stats });
}

function useInvalidateAll() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: keys.applications });
    void qc.invalidateQueries({ queryKey: keys.stats });
  };
}

/**
 * Status change with an optimistic update: every cached list moves the card immediately,
 * and is rolled back if the server rejects the change.
 */
export function useChangeStatus() {
  const qc = useQueryClient();
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: ApplicationStatus }) => api.changeStatus(id, status),
    onMutate: async ({ id, status }) => {
      await qc.cancelQueries({ queryKey: ['applications', 'list'] });
      const snapshot = qc.getQueriesData<Page<ApplicationSummary>>({ queryKey: ['applications', 'list'] });
      qc.setQueriesData<Page<ApplicationSummary>>({ queryKey: ['applications', 'list'] }, (page) =>
        page ? { ...page, content: moveApplication(page.content, id, status) } : page,
      );
      return { snapshot };
    },
    onError: (_error, _vars, context) => {
      context?.snapshot.forEach(([key, data]) => qc.setQueryData(key, data));
    },
    onSuccess: (detail) => qc.setQueryData(keys.application(detail.id), detail),
    onSettled: invalidate,
  });
}

export function useSaveApplication() {
  const qc = useQueryClient();
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: ApplicationInput }) =>
      id ? api.updateApplication(id, input) : api.createApplication(input),
    onSuccess: (detail) => qc.setQueryData(keys.application(detail.id), detail),
    onSettled: invalidate,
  });
}

export function useDeleteApplication() {
  const qc = useQueryClient();
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (id: number) => api.deleteApplication(id),
    onSuccess: (_data, id) => qc.removeQueries({ queryKey: keys.application(id) }),
    onSettled: invalidate,
  });
}

export function useAddInterview(applicationId: number) {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (input: InterviewInput) => api.addInterview(applicationId, input),
    onSettled: invalidate,
  });
}

export function useDeleteInterview(applicationId: number) {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (interviewId: number) => api.deleteInterview(applicationId, interviewId),
    onSettled: invalidate,
  });
}
