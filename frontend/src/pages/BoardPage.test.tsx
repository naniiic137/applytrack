import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api/endpoints';
import { summary } from '../test/fixtures';
import { BoardPage } from './BoardPage';

function renderBoard() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <BoardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('BoardPage', () => {
  afterEach(() => vi.restoreAllMocks());

  it('says so when there are more applications than the board loads', async () => {
    const cards = [summary({ id: 1, company: 'Northwind' }), summary({ id: 2, company: 'Cedar' })];
    vi.spyOn(api, 'listApplications').mockResolvedValue({
      content: cards,
      page: 0,
      size: 200,
      totalElements: 250,
      totalPages: 2,
    });

    renderBoard();

    expect(await screen.findByText(/Showing the 2 most recently updated of 250 applications/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open the list view' })).toHaveAttribute('href', '/applications');
  });

  it('shows no notice when everything fits', async () => {
    vi.spyOn(api, 'listApplications').mockResolvedValue({
      content: [summary({ id: 1, company: 'Northwind' })],
      page: 0,
      size: 200,
      totalElements: 1,
      totalPages: 1,
    });

    renderBoard();

    expect(await screen.findByText('Northwind')).toBeInTheDocument();
    expect(screen.queryByText(/most recently updated/)).not.toBeInTheDocument();
  });
});
