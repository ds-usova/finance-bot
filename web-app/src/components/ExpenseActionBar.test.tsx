import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { substituteCatalogue } from '../testing/catalogue';
import { ExpenseActionBar } from './ExpenseActionBar';

describe('the action bar', () => {
  it('offers no acceptance action when nothing is ticked', () => {
    render(<ExpenseActionBar count={0} onAccept={vi.fn()} busy={false} />);

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('offers one action naming three, findable by role and accessible name, when three are ticked', () => {
    render(<ExpenseActionBar count={3} onAccept={vi.fn()} busy={false} />);

    expect(screen.getByRole('button', { name: 'Accept 3 entries' })).toBeInTheDocument();
  });

  it('names one in the singular when one is ticked', () => {
    render(<ExpenseActionBar count={1} onAccept={vi.fn()} busy={false} />);

    expect(screen.getByRole('button', { name: 'Accept 1 entry' })).toBeInTheDocument();
  });

  it('calls onAccept once when the action is pressed', async () => {
    const user = userEvent.setup();
    const onAccept = vi.fn();

    render(<ExpenseActionBar count={3} onAccept={onAccept} busy={false} />);
    await user.click(screen.getByRole('button', { name: 'Accept 3 entries' }));

    expect(onAccept).toHaveBeenCalledTimes(1);
  });

  it('disables the action while the call is out, so pressing it calls nothing', async () => {
    const user = userEvent.setup();
    const onAccept = vi.fn();

    render(<ExpenseActionBar count={3} onAccept={onAccept} busy />);
    const button = screen.getByRole('button', { name: 'Accept 3 entries' });
    expect(button).toBeDisabled();

    await user.click(button);
    expect(onAccept).not.toHaveBeenCalled();
  });

  it('shows the catalogue’s substituted text for both the singular and the plural form, rather than a literal', () => {
    substituteCatalogue();

    const { rerender } = render(<ExpenseActionBar count={1} onAccept={vi.fn()} busy={false} />);
    expect(screen.getByRole('button', { name: '‹Accept 1 entry›' })).toBeInTheDocument();

    rerender(<ExpenseActionBar count={3} onAccept={vi.fn()} busy={false} />);
    expect(screen.getByRole('button', { name: '‹Accept 3 entries›' })).toBeInTheDocument();
  });
});
