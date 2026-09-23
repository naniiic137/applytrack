import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Modal } from './Modal';

function Page({ onClose }: { onClose: () => void }) {
  return (
    <div>
      <button type="button">Background button</button>
      <Modal title="Edit" onClose={onClose}>
        <input aria-label="Company" autoFocus />
        <button type="button">Save</button>
      </Modal>
    </div>
  );
}

describe('Modal', () => {
  it('keeps Tab and Shift+Tab inside the dialog', async () => {
    const user = userEvent.setup();
    render(<Page onClose={vi.fn()} />);
    const close = screen.getByRole('button', { name: 'Close' });
    const save = screen.getByRole('button', { name: 'Save' });

    expect(screen.getByLabelText('Company')).toHaveFocus(); // autoFocus is not stolen by the panel

    save.focus();
    await user.tab();
    expect(close).toHaveFocus(); // wrapped from the last element to the first

    await user.tab({ shift: true });
    expect(save).toHaveFocus(); // and back
  });

  it('makes the background inert while open and restores it on close', () => {
    const Toggle = ({ open }: { open: boolean }) => (
      <div>
        <button type="button">Background button</button>
        {open && (
          <Modal title="Edit" onClose={vi.fn()}>
            <button type="button">Save</button>
          </Modal>
        )}
      </div>
    );
    const { rerender } = render(<Toggle open />);
    const background = screen.getByRole('button', { name: 'Background button', hidden: true });

    expect(background).toHaveAttribute('inert');

    rerender(<Toggle open={false} />);
    expect(background).not.toHaveAttribute('inert');
  });

  it('does not move the focus when the parent re-renders with a new onClose, and Escape uses the latest one', async () => {
    const user = userEvent.setup();
    const first = vi.fn();
    const latest = vi.fn();
    const { rerender } = render(<Page onClose={first} />);
    const input = screen.getByLabelText('Company');
    await user.type(input, 'Acme');

    rerender(<Page onClose={latest} />);

    expect(input).toHaveFocus();
    await user.keyboard('{Escape}');
    expect(latest).toHaveBeenCalledOnce();
    expect(first).not.toHaveBeenCalled();
  });
});
