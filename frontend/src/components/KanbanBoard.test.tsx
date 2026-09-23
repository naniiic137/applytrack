import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { summary } from '../test/fixtures';
import { KanbanBoard } from './KanbanBoard';

describe('KanbanBoard', () => {
  const apps = [
    summary({ id: 1, company: 'Northwind', status: 'APPLIED', tags: ['react', 'java'] }),
    summary({ id: 2, company: 'Cedar', status: 'APPLIED' }),
    summary({ id: 3, company: 'Halfmoon', status: 'OFFER' }),
  ];

  it('renders one column per status with the right counts', () => {
    render(<KanbanBoard applications={apps} onMove={vi.fn()} onOpen={vi.fn()} />);

    const applied = screen.getByRole('listitem', { name: 'Applied' });
    expect(within(applied).getByText('2')).toBeInTheDocument();
    expect(within(applied).getByText('Northwind')).toBeInTheDocument();
    expect(within(applied).getByText('react')).toBeInTheDocument();

    const offer = screen.getByRole('listitem', { name: 'Offer' });
    expect(within(offer).getByText('Halfmoon')).toBeInTheDocument();

    const wishlist = screen.getByRole('listitem', { name: 'Wishlist' });
    expect(within(wishlist).getByText('Drop here')).toBeInTheDocument();
  });

  it('opens the detail view when a card is clicked', async () => {
    const onOpen = vi.fn();
    render(<KanbanBoard applications={apps} onMove={vi.fn()} onOpen={onOpen} />);

    await userEvent.click(screen.getByText('Cedar'));

    expect(onOpen).toHaveBeenCalledWith(2);
  });
});
