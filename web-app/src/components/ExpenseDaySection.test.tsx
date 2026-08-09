import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { expandDays } from '../testing/accordion';
import { substituteCatalogue } from '../testing/catalogue';
import { anExpense } from '../testing/fixtures';
import { ExpenseDaySection } from './ExpenseDaySection';
import type { ExpenseDay } from './expenseDays';

const categoryNames = new Map([
  [10, 'Groceries'],
  [20, 'Transport'],
]);

function aDay(overrides: Partial<ExpenseDay> = {}): ExpenseDay {
  return {
    day: '2026-08-01',
    entries: [],
    awaiting: 0,
    totals: [],
    ...overrides,
  };
}

function amount(currency: string, minorUnits: number): string {
  return new Intl.NumberFormat('en', { style: 'currency', currency }).format(minorUnits / 100);
}

afterEach(() => {
  vi.useRealTimers();
});

describe('the rendered day section', () => {
  // TODO web-app RU02: the day heading's total is not rendered yet
  it.skip('carries the day, the entry count and the total in its header, and lists every entry once opened', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-01T15:00:00Z'));

    const entries = [
      anExpense({
        id: 1,
        status: 'RECORDED',
        categoryId: 10,
        description: 'lunch',
        money: { amount: '5.00', currency: 'EUR', separator: '' },
      }),
      anExpense({
        id: 2,
        status: 'RECORDED',
        categoryId: 10,
        description: 'dinner',
        money: { amount: '3.00', currency: 'EUR', separator: '' },
      }),
      anExpense({
        id: 3,
        status: 'RECORDED',
        categoryId: 10,
        description: 'taxi',
        money: { amount: '4.50', currency: 'EUR', separator: '' },
      }),
    ];
    const day = aDay({
      day: '2026-08-01',
      entries,
      awaiting: 0,
      totals: [{ amount: '12.50', currency: 'EUR', separator: '' }],
    });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);

    const header = screen.getByRole('button');
    expect(header).toHaveTextContent('Today');
    expect(header).toHaveTextContent('3 entries');
    expect(header).toHaveTextContent(amount('EUR', 1250));
    // The section arrives collapsed, so the header is all there is until it is opened.
    expect(screen.queryByText('lunch')).not.toBeInTheDocument();

    expandDays();

    expect(screen.getByText('lunch')).toBeInTheDocument();
    expect(screen.getByText('dinner')).toBeInTheDocument();
    expect(screen.getByText('taxi')).toBeInTheDocument();
  });

  it('names the day today or yesterday, resolved through relativeDay rather than dated', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-05T10:00:00Z'));

    const today = aDay({ day: '2026-08-05' });
    const { unmount } = render(<ExpenseDaySection day={today} categoryNames={categoryNames} />);
    expect(screen.getByRole('button')).toHaveTextContent('Today');
    unmount();

    const yesterday = aDay({ day: '2026-08-04' });
    render(<ExpenseDaySection day={yesterday} categoryNames={categoryNames} />);
    expect(screen.getByRole('button')).toHaveTextContent('Yesterday');
  });

  it('shows the day as a readable date at UTC once it is neither today nor yesterday', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-09T10:00:00Z'));

    const day = aDay({ day: '2026-08-01' });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);

    // A fixed expected string, not the component's own Intl call repeated: built from the same expression,
    // this would hold in a zone that dated the heading a day off the entries the section holds.
    const header = screen.getByRole('button');
    expect(header).toHaveTextContent('Aug 1, 2026');
    expect(header).not.toHaveTextContent('Today');
    expect(header).not.toHaveTextContent('Yesterday');
  });

  // TODO web-app RU02: the entry row's amount is not rendered yet
  it.skip('lists a recorded entry with its description, its merchant, its category name and its Intl-formatted amount, and no status badge', () => {
    const entry = anExpense({
      id: 1,
      status: 'RECORDED',
      categoryId: 10,
      description: 'lunch',
      merchant: 'Corner Cafe',
      money: { amount: '12.50', currency: 'EUR', separator: '' },
    });
    const day = aDay({
      entries: [entry],
      totals: [{ amount: '12.50', currency: 'EUR', separator: '' }],
    });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);
    expandDays();

    const item = screen.getByRole('listitem', { name: /lunch/i });
    expect(item).toHaveTextContent('lunch');
    expect(item).toHaveTextContent('Corner Cafe');
    expect(item).toHaveTextContent('Groceries');
    expect(item).toHaveTextContent(amount('EUR', 1250));
    // Only a proposal is badged: a recorded entry is the ordinary case and carries no label of its own.
    expect(within(item).queryByText('Recorded')).not.toBeInTheDocument();
    expect(within(item).queryByText('Pending')).not.toBeInTheDocument();
  });

  // TODO web-app RU02: the day heading's total is not rendered yet
  it.skip('keeps the header’s day, count and total whether it is open or closed', async () => {
    const user = userEvent.setup();
    const entries = [
      anExpense({
        id: 1,
        description: 'lunch',
        money: { amount: '5.00', currency: 'EUR', separator: '' },
      }),
      anExpense({
        id: 2,
        description: 'dinner',
        money: { amount: '3.00', currency: 'EUR', separator: '' },
      }),
      anExpense({
        id: 3,
        description: 'taxi',
        money: { amount: '4.50', currency: 'EUR', separator: '' },
      }),
    ];
    const day = aDay({ entries, totals: [{ amount: '12.50', currency: 'EUR', separator: '' }] });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);
    const header = screen.getByRole('button');

    expect(header).toHaveTextContent(amount('EUR', 1250));
    expect(screen.queryByText('lunch')).not.toBeInTheDocument();

    await user.click(header);
    expect(screen.getByText('lunch')).toBeInTheDocument();

    await user.click(header);

    expect(header).toHaveTextContent(amount('EUR', 1250));
    expect(screen.queryByText('lunch')).not.toBeInTheDocument();
    expect(screen.queryByText('dinner')).not.toBeInTheDocument();
    expect(screen.queryByText('taxi')).not.toBeInTheDocument();
  });

  // TODO web-app RU02: the day heading's totals are not rendered yet
  it.skip('shows a total for each currency present among the day’s recorded entries', () => {
    const day = aDay({
      entries: [
        anExpense({
          id: 1,
          description: 'lunch',
          money: { amount: '12.50', currency: 'EUR', separator: '' },
        }),
        anExpense({
          id: 2,
          description: 'cab',
          money: { amount: '9.00', currency: 'USD', separator: '' },
        }),
      ],
      totals: [
        { amount: '12.50', currency: 'EUR', separator: '' },
        { amount: '9.00', currency: 'USD', separator: '' },
      ],
    });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);

    const header = screen.getByRole('button');
    expect(header).toHaveTextContent(amount('EUR', 1250));
    expect(header).toHaveTextContent(amount('USD', 900));
  });

  // TODO web-app RU02: the day heading's total is not rendered yet
  it.skip('sums only the recorded entries into the total and says one entry awaits a decision', () => {
    const day = aDay({
      entries: [
        anExpense({
          id: 1,
          status: 'RECORDED',
          description: 'lunch',
          money: { amount: '5.00', currency: 'EUR', separator: '' },
        }),
        anExpense({
          id: 2,
          status: 'RECORDED',
          description: 'dinner',
          money: { amount: '3.00', currency: 'EUR', separator: '' },
        }),
        anExpense({
          id: 3,
          status: 'PENDING',
          description: 'taxi',
          money: { amount: '9.00', currency: 'EUR', separator: '' },
        }),
      ],
      awaiting: 1,
      totals: [{ amount: '8.00', currency: 'EUR', separator: '' }],
    });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);

    const header = screen.getByRole('button');
    expect(header).toHaveTextContent(amount('EUR', 800));
    expect(header).not.toHaveTextContent(amount('EUR', 1700));
    expect(header).toHaveTextContent('1 entry awaits a decision');
  });

  it('shows no total and says one entry awaits a decision when the day holds only a pending entry', () => {
    const day = aDay({
      entries: [
        anExpense({
          id: 1,
          status: 'PENDING',
          description: 'taxi',
          money: { amount: '9.00', currency: 'EUR', separator: '' },
        }),
      ],
      awaiting: 1,
      totals: [],
    });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);

    const header = screen.getByRole('button');
    expect(header).not.toHaveTextContent('€');
    expect(header).toHaveTextContent('1 entry awaits a decision');
  });

  it('carries a Pending badge on an entry still awaiting a decision, with no Status column anywhere', () => {
    const entry = anExpense({
      id: 1,
      status: 'PENDING',
      description: 'taxi',
      money: { amount: '9.00', currency: 'EUR', separator: '' },
    });
    const day = aDay({ entries: [entry], awaiting: 1, totals: [] });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);
    expandDays();

    const item = screen.getByRole('listitem', { name: /taxi/i });
    expect(within(item).getByText('Pending')).toBeInTheDocument();
    expect(screen.queryByRole('columnheader')).not.toBeInTheDocument();
    expect(screen.queryByText(/^status$/i)).not.toBeInTheDocument();
  });

  it('lists a recorded entry and a proposal that share an id as two separate entries, each carrying its own badge', () => {
    const recorded = anExpense({
      id: 1,
      status: 'RECORDED',
      description: 'lunch',
      merchant: 'Corner Cafe',
      money: { amount: '12.50', currency: 'EUR', separator: '' },
    });
    const proposal = anExpense({
      id: 1,
      status: 'PENDING',
      description: 'coffee',
      merchant: 'Corner Cafe',
      money: { amount: '3.00', currency: 'EUR', separator: '' },
    });
    const day = aDay({
      entries: [recorded, proposal],
      awaiting: 1,
      totals: [{ amount: '12.50', currency: 'EUR', separator: '' }],
    });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);
    expandDays();

    const recordedItem = screen.getByRole('listitem', { name: /lunch/i });
    const proposalItem = screen.getByRole('listitem', { name: /coffee/i });
    expect(within(recordedItem).queryByText('Pending')).not.toBeInTheDocument();
    expect(within(proposalItem).getByText('Pending')).toBeInTheDocument();
  });

  // TODO web-app RU02: the entry row's amount is not rendered yet
  it.skip('still shows the description, the merchant and the amount when the category is absent from the lookup, without leaking the id, null or undefined', () => {
    const entry = anExpense({
      id: 1,
      status: 'RECORDED',
      categoryId: 999,
      description: 'stamps',
      merchant: 'Post Office',
      money: { amount: '5.00', currency: 'EUR', separator: '' },
    });
    const day = aDay({
      entries: [entry],
      totals: [{ amount: '5.00', currency: 'EUR', separator: '' }],
    });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);
    expandDays();

    const item = screen.getByRole('listitem', { name: /stamps/i });
    expect(item).toHaveTextContent('stamps');
    expect(item).toHaveTextContent('Post Office');
    expect(item).toHaveTextContent(amount('EUR', 500));
    expect(item).not.toHaveTextContent('999');
    expect(item).not.toHaveTextContent(/null|undefined/i);
  });

  it('shows the merchant when it is given and no null or undefined in its place when it is not', () => {
    const withMerchant = anExpense({
      id: 1,
      description: 'coffee',
      merchant: 'Corner Cafe',
      money: { amount: '3.00', currency: 'EUR', separator: '' },
    });
    const withoutMerchant = anExpense({
      id: 2,
      description: 'stamps',
      merchant: null,
      money: { amount: '5.00', currency: 'EUR', separator: '' },
    });
    const day = aDay({
      entries: [withMerchant, withoutMerchant],
      totals: [{ amount: '8.00', currency: 'EUR', separator: '' }],
    });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);
    expandDays();

    expect(screen.getByRole('listitem', { name: /coffee/i })).toHaveTextContent('Corner Cafe');
    const stampsItem = screen.getByRole('listitem', { name: /stamps/i });
    expect(stampsItem).not.toHaveTextContent(/null|undefined/i);
  });

  it('shows the catalogue’s substituted text rather than a literal, once the catalogue is swapped', () => {
    substituteCatalogue();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-05T10:00:00Z'));

    const entries = [
      anExpense({
        id: 1,
        status: 'RECORDED',
        description: 'lunch',
        money: { amount: '5.00', currency: 'EUR', separator: '' },
      }),
      anExpense({
        id: 2,
        status: 'PENDING',
        description: 'taxi',
        money: { amount: '9.00', currency: 'EUR', separator: '' },
      }),
    ];
    const day = aDay({
      day: '2026-08-05',
      entries,
      awaiting: 1,
      totals: [{ amount: '5.00', currency: 'EUR', separator: '' }],
    });

    render(<ExpenseDaySection day={day} categoryNames={categoryNames} />);

    const header = screen.getByRole('button');
    expect(header).toHaveTextContent('‹Today›');
    expect(header).toHaveTextContent('‹1 entry awaits a decision›');

    expandDays();

    const pendingItem = screen.getByRole('listitem', { name: /taxi/i });
    expect(within(pendingItem).getByText('‹Pending›')).toBeInTheDocument();
  });
});
