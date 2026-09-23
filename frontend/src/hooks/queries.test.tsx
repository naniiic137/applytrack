import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import type { ApplicationDetail, ApplicationSummary, Page } from '../api/types';
import { summary } from '../test/fixtures';
import { BOARD_QUERY, keys, useChangeStatus } from './queries';

function setup(cards: ApplicationSummary[]) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const page: Page<ApplicationSummary> = { content: cards, page: 0, size: 200, totalElements: cards.length, totalPages: 1 };
  qc.setQueryData(keys.applicationList(BOARD_QUERY), page);
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
  const { result } = renderHook(() => useChangeStatus(), { wrapper });
  const board = () => qc.getQueryData<Page<ApplicationSummary>>(keys.applicationList(BOARD_QUERY))!.content;
  return { result, board };
}

describe('useChangeStatus', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sends the version the card was read with and stores the new one from the response', async () => {
    const spy = vi.spyOn(api, 'changeStatus').mockResolvedValue({
      id: 1,
      status: 'INTERVIEW',
      appliedOn: '2026-03-01',
      updatedAt: '2026-03-05T10:00:00Z',
      version: 4,
    } as ApplicationDetail);
    const { result, board } = setup([summary({ id: 1, status: 'APPLIED', version: 3 })]);

    await act(() => result.current.mutateAsync({ id: 1, status: 'INTERVIEW', version: 3 }));

    expect(spy).toHaveBeenCalledWith(1, 'INTERVIEW', 3);
    expect(board()[0]).toMatchObject({ status: 'INTERVIEW', version: 4 });
  });

  it('rolls the optimistic move back when the version is stale (409)', async () => {
    vi.spyOn(api, 'changeStatus').mockRejectedValue(
      new ApiError(409, { status: 409, title: 'Conflict', code: 'stale_version' }),
    );
    const { result, board } = setup([summary({ id: 1, status: 'APPLIED', version: 0 })]);

    act(() => result.current.mutate({ id: 1, status: 'OFFER', version: 0 }));

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(board()[0].status).toBe('APPLIED');
  });
});
