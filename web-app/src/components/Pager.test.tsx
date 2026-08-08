import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { anExpense, anExpensePage } from '../testing/fixtures';
import { Pager } from './Pager';

function aPage(overrides: { limit?: number; offset?: number; total?: number }, items = 2) {
  const entries = Array.from({ length: items }, (_, index) => anExpense({ id: index + 1 }));
  return anExpensePage(entries, { limit: 2, offset: 0, total: 6, ...overrides });
}

describe('the pager', () => {
  it('renders nothing when the whole listing is already on screen', () => {
    render(<Pager page={aPage({ total: 2 })} onOffset={vi.fn()} />);

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('asks for the next page one page size past the offset it was answered with', async () => {
    const onOffset = vi.fn();

    render(<Pager page={aPage({ limit: 2, offset: 2 })} onOffset={onOffset} />);
    await userEvent.click(screen.getByRole('button', { name: /next/i }));

    expect(onOffset).toHaveBeenCalledWith(4);
  });

  it('asks for the previous page one page size back', async () => {
    const onOffset = vi.fn();

    render(<Pager page={aPage({ limit: 2, offset: 2 })} onOffset={onOffset} />);
    await userEvent.click(screen.getByRole('button', { name: /previous/i }));

    expect(onOffset).toHaveBeenCalledWith(0);
  });

  it('offers no way back from the first page', () => {
    render(<Pager page={aPage({ offset: 0 })} onOffset={vi.fn()} />);

    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled();
  });

  it('offers no way on from the last page', () => {
    render(<Pager page={aPage({ limit: 2, offset: 4, total: 6 })} onOffset={vi.fn()} />);

    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /previous/i })).toBeEnabled();
  });

  it('names which of the matching rows are on screen', () => {
    render(<Pager page={aPage({ limit: 2, offset: 2, total: 6 })} onOffset={vi.fn()} />);

    expect(screen.getByText(/3–4 of 6/)).toBeInTheDocument();
  });

  it('still offers the way back when the offset is past the last row', () => {
    const onOffset = vi.fn();

    render(<Pager page={aPage({ limit: 2, offset: 8, total: 6 }, 0)} onOffset={onOffset} />);

    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /previous/i })).toBeEnabled();
    expect(screen.queryByText(/of 6/)).not.toBeInTheDocument();
  });
});
