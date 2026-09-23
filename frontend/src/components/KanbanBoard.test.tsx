import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { summary } from '../test/fixtures';
import { KanbanBoard, describeCard } from './KanbanBoard';

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

  it('moves a card with the keyboard through the "Move to" menu', async () => {
    const user = userEvent.setup();
    const onMove = vi.fn();
    render(<KanbanBoard applications={apps} onMove={onMove} onOpen={vi.fn()} />);
    const button = screen.getByRole('button', { name: 'Move Cedar, Junior Developer to another column' });

    button.focus();
    await user.keyboard('{Enter}');

    const menu = screen.getByRole('menu', { name: 'Move Cedar to' });
    const items = within(menu).getAllByRole('menuitem');
    // every column except the current one (Applied)
    expect(items.map((i) => i.textContent?.trim())).toEqual(['Wishlist', 'Interview', 'Offer', 'Rejected', 'Ghosted']);
    expect(items[0]).toHaveFocus();

    await user.keyboard('{ArrowDown}{Enter}');

    expect(onMove).toHaveBeenCalledWith(2, 'INTERVIEW');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(button).toHaveFocus();
  });

  it('closes the "Move to" menu with Escape without moving anything', async () => {
    const user = userEvent.setup();
    const onMove = vi.fn();
    const onOpen = vi.fn();
    render(<KanbanBoard applications={apps} onMove={onMove} onOpen={onOpen} />);

    await user.click(screen.getByRole('button', { name: /^Move Halfmoon/ }));
    await user.keyboard('{Escape}');

    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(onMove).not.toHaveBeenCalled();
    expect(onOpen).not.toHaveBeenCalled(); // the menu button does not open the card
  });

  it('announces keyboard drags by company and role, not by database id', async () => {
    const user = userEvent.setup();
    render(<KanbanBoard applications={apps} onMove={vi.fn()} onOpen={vi.fn()} />);

    screen.getByRole('button', { name: /^Cedar, Junior Developer\. Press space/ }).focus();
    await user.keyboard(' ');

    expect(await screen.findByText('Picked up Cedar, Junior Developer.')).toBeInTheDocument();
    expect(describeCard(undefined)).toBe('application');
  });
});
