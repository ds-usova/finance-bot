import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { en } from '../i18n/en';
import { expandDays } from '../testing/accordion';
import { substituteCatalogue } from '../testing/catalogue';
import { chooseFromList } from '../testing/combobox';
import { aCategory, aDay, aGrouping, anExpense, categoryNames } from '../testing/fixtures';
import { ExpenseDaySection, type ExpenseDaySectionProps } from './ExpenseDaySection';
import type { ExpenseDay } from './expenseDays';

/** The section under test, with the props a case says nothing about left inert. */
function section(props: Partial<ExpenseDaySectionProps> & { day: ExpenseDay }) {
  return (
    <ExpenseDaySection
      categoryNames={categoryNames}
      categories={[]}
      groupings={[]}
      tickedIds={new Set()}
      onTick={vi.fn()}
      onTickDay={vi.fn()}
      tickHeadroom={Infinity}
      changingKey={null}
      changeFailure={null}
      focusDay={null}
      onChangeCategory={vi.fn()}
      {...props}
    />
  );
}

function renderSection(props: Partial<ExpenseDaySectionProps> & { day: ExpenseDay }) {
  return render(section(props));
}

afterEach(() => {
  vi.useRealTimers();
});

describe('the rendered day section', () => {
  it('carries the day, the entry count and the total in its header, and lists every entry once opened', () => {
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

    renderSection({ day });

    const header = screen.getByRole('button');
    expect(header).toHaveTextContent('Today');
    expect(header).toHaveTextContent('3 entries');
    expect(header).toHaveTextContent('EUR12.50');
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

    const { unmount } = renderSection({ day: aDay({ day: '2026-08-05' }) });
    expect(screen.getByRole('button')).toHaveTextContent('Today');
    unmount();

    renderSection({ day: aDay({ day: '2026-08-04' }) });
    expect(screen.getByRole('button')).toHaveTextContent('Yesterday');
  });

  it('shows the day as a readable date at UTC once it is neither today nor yesterday', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-09T10:00:00Z'));

    const day = aDay({ day: '2026-08-01' });

    renderSection({ day });

    // A fixed expected string, not the component's own Intl call repeated: built from the same expression,
    // this would hold in a zone that dated the heading a day off the entries the section holds.
    const header = screen.getByRole('button');
    expect(header).toHaveTextContent('Aug 1, 2026');
    expect(header).not.toHaveTextContent('Today');
    expect(header).not.toHaveTextContent('Yesterday');
  });

  it('lists a recorded entry with its description, its merchant, its category name and the amount it was given, and no status badge', () => {
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

    renderSection({ day });
    expandDays();

    const item = screen.getByRole('listitem', { name: /lunch/i });
    expect(item).toHaveTextContent('lunch');
    expect(item).toHaveTextContent('Corner Cafe');
    expect(item).toHaveTextContent('Groceries');
    expect(item).toHaveTextContent('EUR12.50');
    // Only a proposal is badged: a recorded entry is the ordinary case and carries no label of its own.
    expect(within(item).queryByText('Recorded')).not.toBeInTheDocument();
    expect(within(item).queryByText('Pending')).not.toBeInTheDocument();
    // A recorded entry is already settled, so it offers no checkbox at all.
    expect(within(item).queryByRole('checkbox')).not.toBeInTheDocument();
    // The category name is now carried by a control, not by plain text beside the merchant.
    expect(within(item).getByRole('button', { name: 'Change lunch’s category' })).toHaveTextContent(
      'Groceries',
    );
  });

  it('keeps the header’s day, count and total whether it is open or closed', async () => {
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

    renderSection({ day });
    const header = screen.getByRole('button');

    expect(header).toHaveTextContent('EUR12.50');
    expect(screen.queryByText('lunch')).not.toBeInTheDocument();

    await user.click(header);
    expect(screen.getByText('lunch')).toBeInTheDocument();

    await user.click(header);

    expect(header).toHaveTextContent('EUR12.50');
    expect(screen.queryByText('lunch')).not.toBeInTheDocument();
    expect(screen.queryByText('dinner')).not.toBeInTheDocument();
    expect(screen.queryByText('taxi')).not.toBeInTheDocument();
  });

  it('shows one figure per currency its day was answered', () => {
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

    renderSection({ day });

    const header = screen.getByRole('button');
    expect(header).toHaveTextContent('EUR12.50');
    expect(header).toHaveTextContent('USD9.00');
  });

  it('shows the figure its day was answered rather than one covering every entry, and says one awaits a decision', () => {
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

    renderSection({ day });

    const header = screen.getByRole('button');
    expect(header).toHaveTextContent('EUR8.00');
    // 5.00 + 3.00 + 9.00 would read EUR17.00 if the pending entry were wrongly counted in.
    expect(header).not.toHaveTextContent('EUR17.00');
    expect(header).toHaveTextContent('1 entry awaits a decision');
  });

  it('shows an entry’s money as amount, currency and separator joined in order, character for character', () => {
    const entry = anExpense({
      id: 1,
      status: 'RECORDED',
      description: 'vending machine',
      money: { amount: '900', currency: '¥', separator: '' },
    });
    const day = aDay({ entries: [entry] });

    renderSection({ day });
    expandDays();

    const item = screen.getByRole('listitem', { name: /vending machine/i });
    expect(within(item).getByText('¥900')).toBeInTheDocument();
  });

  it('shows an entry’s money with a one-space separator between a code label and its digits, and no other space', () => {
    const entry = anExpense({
      id: 1,
      status: 'RECORDED',
      description: 'hotel',
      money: { amount: '1,245.00', currency: 'CHF', separator: ' ' },
    });
    const day = aDay({ entries: [entry] });

    renderSection({ day });
    expandDays();

    const item = screen.getByRole('listitem', { name: /hotel/i });
    expect(within(item).getByText('CHF 1,245.00')).toBeInTheDocument();
  });

  it('shows the day heading’s figures in the order they were answered', () => {
    const day = aDay({
      totals: [
        { amount: '12.50', currency: '€', separator: '' },
        { amount: '900', currency: '¥', separator: '' },
      ],
    });

    renderSection({ day });

    const header = screen.getByRole('button');
    expect(header).toHaveTextContent('€12.50');
    expect(header).toHaveTextContent('¥900');
    expect(header.textContent!.indexOf('€12.50')).toBeLessThan(header.textContent!.indexOf('¥900'));
  });

  it('shows the day heading’s figure with a one-space separator between a code label and its digits, and no other space', () => {
    const day = aDay({
      totals: [{ amount: '1,245.00', currency: 'CHF', separator: ' ' }],
    });

    renderSection({ day });

    const header = screen.getByRole('button');
    expect(within(header).getByText('CHF 1,245.00')).toBeInTheDocument();
  });

  it('shows the figures it was last answered for a day, after a re-render replaces both', () => {
    const day = aDay({
      totals: [
        { amount: '12.50', currency: '€', separator: '' },
        { amount: '9.00', currency: '$', separator: '' },
      ],
    });

    const { rerender } = renderSection({ day });
    let header = screen.getByRole('button');
    expect(header).toHaveTextContent('€12.50');
    expect(header).toHaveTextContent('$9.00');

    const updatedDay = aDay({
      day: day.day,
      totals: [
        { amount: '20.00', currency: '£', separator: '' },
        { amount: '5.00', currency: '¥', separator: '' },
      ],
    });

    rerender(section({ day: updatedDay }));

    header = screen.getByRole('button');
    expect(header).toHaveTextContent('£20.00');
    expect(header).toHaveTextContent('¥5.00');
    expect(header).not.toHaveTextContent('€12.50');
    expect(header).not.toHaveTextContent('$9.00');
  });

  it('shows no total and says one entry awaits a decision when the day holds only a pending entry', () => {
    const entry = anExpense({
      id: 1,
      status: 'PENDING',
      description: 'taxi',
      money: { amount: '9.00', currency: 'EUR', separator: '' },
    });
    const day = aDay({ entries: [entry], awaiting: 1, totals: [] });

    renderSection({ day });

    const header = screen.getByRole('button');
    // The heading's figure comes only from the section's totals, never from an entry's own money — so
    // with totals empty, neither half of the pending entry's money should appear in the header.
    expect(header).not.toHaveTextContent(entry.money.currency);
    expect(header).not.toHaveTextContent(entry.money.amount);
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

    renderSection({ day });
    expandDays();

    const item = screen.getByRole('listitem', { name: /taxi/i });
    expect(within(item).getByText('Pending')).toBeInTheDocument();
    expect(screen.queryByRole('columnheader')).not.toBeInTheDocument();
    expect(screen.queryByText(/^status$/i)).not.toBeInTheDocument();
    // An entry still awaiting a decision offers a checkbox, findable by its accessible name.
    expect(
      within(item).getByRole('checkbox', { name: en.listing.entryCheckboxLabel }),
    ).toBeInTheDocument();
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

    renderSection({ day });
    expandDays();

    const recordedItem = screen.getByRole('listitem', { name: /lunch/i });
    const proposalItem = screen.getByRole('listitem', { name: /coffee/i });
    expect(within(recordedItem).queryByText('Pending')).not.toBeInTheDocument();
    expect(within(proposalItem).getByText('Pending')).toBeInTheDocument();
  });

  it('still shows the description, the merchant and the amount when the category is absent from the lookup, without leaking the id, null or undefined', () => {
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

    renderSection({ day });
    expandDays();

    const item = screen.getByRole('listitem', { name: /stamps/i });
    expect(item).toHaveTextContent('stamps');
    expect(item).toHaveTextContent('Post Office');
    expect(item).toHaveTextContent('EUR5.00');
    expect(item).not.toHaveTextContent('999');
    expect(item).not.toHaveTextContent(/null|undefined/i);
    // An unnamed category earns no control at all.
    expect(within(item).queryByRole('button')).not.toBeInTheDocument();
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

    renderSection({ day });
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

    // The pending entry ticked, so the header's ticked count is exercised too.
    renderSection({ day, tickedIds: new Set([2]) });

    const header = screen.getByRole('button');
    expect(header).toHaveTextContent('‹Today›');
    expect(header).toHaveTextContent('‹1 entry ticked›');
    // The day's own checkbox, findable by its catalogue-substituted accessible name.
    expect(
      screen.getByRole('checkbox', { name: '‹Select the 1 pending entry›' }),
    ).toBeInTheDocument();

    expandDays();

    const pendingItem = screen.getByRole('listitem', { name: /taxi/i });
    expect(within(pendingItem).getByText('‹Pending›')).toBeInTheDocument();
    expect(
      within(pendingItem).getByRole('checkbox', { name: '‹Select this entry›' }),
    ).toBeInTheDocument();
    // The row control's own label cannot be a literal in the component either.
    expect(
      within(pendingItem).getByRole('button', { name: '‹Change taxi’s category›' }),
    ).toBeInTheDocument();
  });

  it('carries a checkbox on a pending row, findable by its accessible name, and none on a recorded row', () => {
    const pending = anExpense({ id: 1, status: 'PENDING', description: 'taxi' });
    const recorded = anExpense({ id: 2, status: 'RECORDED', description: 'lunch' });
    const day = aDay({ entries: [pending, recorded], awaiting: 1, totals: [] });

    renderSection({ day });
    expandDays();

    const pendingItem = screen.getByRole('listitem', { name: /taxi/i });
    const recordedItem = screen.getByRole('listitem', { name: /lunch/i });
    expect(
      within(pendingItem).getByRole('checkbox', { name: en.listing.entryCheckboxLabel }),
    ).toBeInTheDocument();
    expect(within(recordedItem).queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('calls onTick with the entry’s id and false when its ticked checkbox is clicked', async () => {
    const user = userEvent.setup();
    const onTick = vi.fn();
    const pending = anExpense({ id: 1, status: 'PENDING', description: 'taxi' });
    const day = aDay({ entries: [pending], awaiting: 1, totals: [] });

    renderSection({ day, tickedIds: new Set([1]), onTick });
    expandDays();

    const item = screen.getByRole('listitem', { name: /taxi/i });
    const checkbox = within(item).getByRole('checkbox', { name: en.listing.entryCheckboxLabel });
    await user.click(checkbox);

    expect(onTick).toHaveBeenCalledWith(1, false);
    // Ticking an entry never opens or closes the section it sits in.
    expect(screen.getByText('taxi')).toBeInTheDocument();
  });

  it('calls onTick with the entry’s id and true when its unticked checkbox is clicked', async () => {
    const user = userEvent.setup();
    const onTick = vi.fn();
    const pending = anExpense({ id: 1, status: 'PENDING', description: 'taxi' });
    const day = aDay({ entries: [pending], awaiting: 1, totals: [] });

    renderSection({ day, onTick });
    expandDays();

    const item = screen.getByRole('listitem', { name: /taxi/i });
    const checkbox = within(item).getByRole('checkbox', { name: en.listing.entryCheckboxLabel });
    await user.click(checkbox);

    expect(onTick).toHaveBeenCalledWith(1, true);
  });

  it('calls onTickDay with only the pending ids and true when the day’s own checkbox is clicked with none ticked', async () => {
    const user = userEvent.setup();
    const onTickDay = vi.fn();
    const pendingA = anExpense({ id: 1, status: 'PENDING', description: 'a' });
    const pendingB = anExpense({ id: 2, status: 'PENDING', description: 'b' });
    const recorded = anExpense({ id: 3, status: 'RECORDED', description: 'c' });
    const day = aDay({ entries: [pendingA, pendingB, recorded], awaiting: 2, totals: [] });

    renderSection({ day, onTickDay });

    const dayCheckbox = screen.getByRole('checkbox', { name: 'Select all 2 pending entries' });
    await user.click(dayCheckbox);

    expect(onTickDay).toHaveBeenCalledWith([1, 2], true);
    // The day is collapsed and stays collapsed: clicking its checkbox is not clicking its header.
    expect(screen.queryByText('a')).not.toBeInTheDocument();
  });

  it('reads the day’s checkbox as ticked when every pending entry is ticked, and calls onTickDay with those ids and false when clicked', async () => {
    const user = userEvent.setup();
    const onTickDay = vi.fn();
    const pendingA = anExpense({ id: 1, status: 'PENDING', description: 'a' });
    const pendingB = anExpense({ id: 2, status: 'PENDING', description: 'b' });
    const day = aDay({ entries: [pendingA, pendingB], awaiting: 2, totals: [] });

    renderSection({ day, tickedIds: new Set([1, 2]), onTickDay });

    const dayCheckbox = screen.getByRole('checkbox', { name: 'Select all 2 pending entries' });
    expect(dayCheckbox).toBeChecked();

    await user.click(dayCheckbox);
    expect(onTickDay).toHaveBeenCalledWith([1, 2], false);
  });

  it('reads the day’s checkbox as partly ticked when one of three pending entries is ticked, and calls onTickDay with all three and true when clicked', async () => {
    const user = userEvent.setup();
    const onTickDay = vi.fn();
    const pendingA = anExpense({ id: 1, status: 'PENDING', description: 'a' });
    const pendingB = anExpense({ id: 2, status: 'PENDING', description: 'b' });
    const pendingC = anExpense({ id: 3, status: 'PENDING', description: 'c' });
    const day = aDay({ entries: [pendingA, pendingB, pendingC], awaiting: 3, totals: [] });

    renderSection({ day, tickedIds: new Set([1]), onTickDay });

    const dayCheckbox = screen.getByRole('checkbox', { name: 'Select all 3 pending entries' });
    expect(dayCheckbox).toBePartiallyChecked();

    await user.click(dayCheckbox);
    expect(onTickDay).toHaveBeenCalledWith([1, 2, 3], true);
  });

  it('offers no day checkbox at all when the day holds no pending entry', () => {
    const recorded = anExpense({ id: 1, status: 'RECORDED', description: 'lunch' });
    const day = aDay({
      entries: [recorded],
      awaiting: 0,
      totals: [{ amount: '5.00', currency: 'EUR', separator: '' }],
    });

    renderSection({ day });

    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('disables an unticked entry’s checkbox at the bound while leaving a ticked one live, so unticking stays possible', () => {
    const tickedEntry = anExpense({ id: 1, status: 'PENDING', description: 'alpha-ticked' });
    const uncheckedEntry = anExpense({ id: 2, status: 'PENDING', description: 'beta-unticked' });
    const day = aDay({ entries: [tickedEntry, uncheckedEntry], awaiting: 2, totals: [] });

    renderSection({ day, tickedIds: new Set([1]), tickHeadroom: 0 });
    expandDays();

    const tickedItem = screen.getByRole('listitem', { name: /alpha-ticked/i });
    const uncheckedItem = screen.getByRole('listitem', { name: /beta-unticked/i });
    expect(
      within(uncheckedItem).getByRole('checkbox', { name: en.listing.entryCheckboxLabel }),
    ).toBeDisabled();
    expect(
      within(tickedItem).getByRole('checkbox', { name: en.listing.entryCheckboxLabel }),
    ).toBeEnabled();
  });

  it('disables the day’s own checkbox at the bound when not every pending entry is ticked, since ticking it would carry the set past the bound', () => {
    const tickedEntry = anExpense({ id: 1, status: 'PENDING', description: 'alpha-ticked' });
    const uncheckedEntry = anExpense({ id: 2, status: 'PENDING', description: 'beta-unticked' });
    const day = aDay({ entries: [tickedEntry, uncheckedEntry], awaiting: 2, totals: [] });

    renderSection({ day, tickedIds: new Set([1]), tickHeadroom: 0 });

    expect(screen.getByRole('checkbox', { name: 'Select all 2 pending entries' })).toBeDisabled();
  });

  it('disables the day’s own checkbox when its pending count outnumbers the headroom, while each entry’s own checkbox stays live', () => {
    const entryA = anExpense({ id: 1, status: 'PENDING', description: 'alpha' });
    const entryB = anExpense({ id: 2, status: 'PENDING', description: 'beta' });
    const entryC = anExpense({ id: 3, status: 'PENDING', description: 'gamma' });
    const day = aDay({ entries: [entryA, entryB, entryC], awaiting: 3, totals: [] });

    renderSection({ day, tickHeadroom: 2 });
    expandDays();

    expect(screen.getByRole('checkbox', { name: 'Select all 3 pending entries' })).toBeDisabled();
    for (const name of [/alpha/i, /beta/i, /gamma/i]) {
      expect(
        within(screen.getByRole('listitem', { name })).getByRole('checkbox', {
          name: en.listing.entryCheckboxLabel,
        }),
      ).toBeEnabled();
    }
  });

  it('leaves the day’s own checkbox live when the headroom covers its whole pending count', () => {
    const entryA = anExpense({ id: 1, status: 'PENDING', description: 'alpha' });
    const entryB = anExpense({ id: 2, status: 'PENDING', description: 'beta' });
    const entryC = anExpense({ id: 3, status: 'PENDING', description: 'gamma' });
    const day = aDay({ entries: [entryA, entryB, entryC], awaiting: 3, totals: [] });

    renderSection({ day, tickHeadroom: 3 });

    expect(screen.getByRole('checkbox', { name: 'Select all 3 pending entries' })).toBeEnabled();
  });

  it('disables no checkbox when the bound is not reached', () => {
    const tickedEntry = anExpense({ id: 1, status: 'PENDING', description: 'alpha-ticked' });
    const uncheckedEntry = anExpense({ id: 2, status: 'PENDING', description: 'beta-unticked' });
    const day = aDay({ entries: [tickedEntry, uncheckedEntry], awaiting: 2, totals: [] });

    renderSection({ day, tickedIds: new Set([1]) });
    expandDays();

    const uncheckedItem = screen.getByRole('listitem', { name: /beta-unticked/i });
    expect(
      within(uncheckedItem).getByRole('checkbox', { name: en.listing.entryCheckboxLabel }),
    ).toBeEnabled();
    expect(screen.getByRole('checkbox', { name: 'Select all 2 pending entries' })).toBeEnabled();
  });

  it('says how many are ticked instead of what awaits a decision, on a collapsed day with two ticked', () => {
    const pendingA = anExpense({ id: 1, status: 'PENDING', description: 'a' });
    const pendingB = anExpense({ id: 2, status: 'PENDING', description: 'b' });
    const pendingC = anExpense({ id: 3, status: 'PENDING', description: 'c' });
    const day = aDay({ entries: [pendingA, pendingB, pendingC], awaiting: 3, totals: [] });

    renderSection({ day, tickedIds: new Set([1, 2]) });

    const header = screen.getByRole('button');
    expect(header).toHaveTextContent('2 entries ticked');
    // One badge, not two: ticking replaces what the header says rather than adding a second label beside it.
    expect(header).not.toHaveTextContent('3 entries await a decision');
  });

  it('says nothing about ticks on a collapsed day with none ticked', () => {
    const pending = anExpense({ id: 1, status: 'PENDING', description: 'a' });
    const day = aDay({ entries: [pending], awaiting: 1, totals: [] });

    renderSection({ day });

    const header = screen.getByRole('button');
    expect(header).not.toHaveTextContent(/ticked/i);
  });

  it('offers each row’s category as a control, and pressing it offers the person’s categories grouped, with no all entry', async () => {
    const everyday = aGrouping({ id: 100, name: 'Everyday' });
    const groceries = aCategory({
      id: 10,
      name: 'Groceries',
      groupingId: everyday.id,
      groupingName: everyday.name,
    });
    const transport = aCategory({
      id: 20,
      name: 'Transport',
      groupingId: everyday.id,
      groupingName: everyday.name,
    });
    const recorded = anExpense({ id: 1, status: 'RECORDED', categoryId: 10, description: 'lunch' });
    const pending = anExpense({ id: 2, status: 'PENDING', categoryId: 10, description: 'taxi' });
    const day = aDay({ entries: [recorded, pending] });

    renderSection({ day, categories: [groceries, transport], groupings: [everyday] });
    expandDays();

    const recordedControl = screen.getByRole('button', { name: 'Change lunch’s category' });
    const pendingControl = screen.getByRole('button', { name: 'Change taxi’s category' });
    expect(recordedControl).toBeInTheDocument();
    expect(pendingControl).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(recordedControl);

    expect(await screen.findByRole('option', { name: 'Groceries' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Transport' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'All' })).not.toBeInTheDocument();
  });

  it('calls onChangeCategory once with the row’s own entry and the chosen category id when a different one is picked', async () => {
    const everyday = aGrouping({ id: 100, name: 'Everyday' });
    const groceries = aCategory({
      id: 10,
      name: 'Groceries',
      groupingId: everyday.id,
      groupingName: everyday.name,
    });
    const transport = aCategory({
      id: 20,
      name: 'Transport',
      groupingId: everyday.id,
      groupingName: everyday.name,
    });
    const entry = anExpense({ id: 1, status: 'RECORDED', categoryId: 10, description: 'lunch' });
    const day = aDay({ entries: [entry] });
    const onChangeCategory = vi.fn();

    renderSection({
      day,
      categories: [groceries, transport],
      groupings: [everyday],
      onChangeCategory,
    });
    expandDays();

    await chooseFromList('Change lunch’s category', 'Transport');

    expect(onChangeCategory).toHaveBeenCalledTimes(1);
    expect(onChangeCategory).toHaveBeenCalledWith(entry, transport.id);
  });

  it('stands the control alone on the secondary line, with no separator, when the merchant is absent', () => {
    const entry = anExpense({
      id: 1,
      status: 'RECORDED',
      categoryId: 10,
      description: 'stamps',
      merchant: null,
    });
    const day = aDay({ entries: [entry] });

    renderSection({ day });
    expandDays();

    const item = screen.getByRole('listitem', { name: /stamps/i });
    expect(
      within(item).getByRole('button', { name: 'Change stamps’s category' }),
    ).toBeInTheDocument();
    expect(item).not.toHaveTextContent('·');
  });

  it('shows both the merchant and the control on the secondary line, in that order, when there is a merchant', () => {
    const entry = anExpense({
      id: 1,
      status: 'RECORDED',
      categoryId: 10,
      description: 'lunch',
      merchant: 'Corner Cafe',
    });
    const day = aDay({ entries: [entry] });

    renderSection({ day });
    expandDays();

    const item = screen.getByRole('listitem', { name: /lunch/i });
    const merchant = within(item).getByText('Corner Cafe');
    const control = within(item).getByRole('button', { name: 'Change lunch’s category' });

    // The control comes after the merchant in the DOM, not merely somewhere later in the text.
    expect(
      merchant.compareDocumentPosition(control) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('reads its old category and marks the control busy but still focusable when a change is out for it', () => {
    const entry = anExpense({ id: 1, status: 'RECORDED', categoryId: 10, description: 'lunch' });
    const day = aDay({ entries: [entry] });

    renderSection({ day, changingKey: 'RECORDED-1' });
    expandDays();

    const item = screen.getByRole('listitem', { name: /lunch/i });
    const control = within(item).getByRole('button', { name: 'Change lunch’s category' });
    expect(item).toHaveTextContent('Groceries');
    expect(control).toHaveAttribute('aria-busy', 'true');
    expect(control).toBeEnabled();
  });

  it('disables every other row’s control on the day while one change is out', () => {
    const first = anExpense({ id: 1, status: 'RECORDED', categoryId: 10, description: 'lunch' });
    const second = anExpense({ id: 2, status: 'RECORDED', categoryId: 10, description: 'dinner' });
    const day = aDay({ entries: [first, second] });

    renderSection({ day, changingKey: 'RECORDED-1' });
    expandDays();

    const secondItem = screen.getByRole('listitem', { name: /dinner/i });
    const control = within(secondItem).getByRole('button', { name: 'Change dinner’s category' });
    expect(control).toBeDisabled();
  });

  it('leaves every control enabled and not busy when no change is out', () => {
    const entry = anExpense({ id: 1, status: 'RECORDED', categoryId: 10, description: 'lunch' });
    const day = aDay({ entries: [entry] });

    renderSection({ day, changingKey: null });
    expandDays();

    const control = screen.getByRole('button', { name: 'Change lunch’s category' });
    expect(control).toBeEnabled();
    expect(control).not.toHaveAttribute('aria-busy', 'true');
  });

  it('shows the refusal message inside the row it names, and nowhere else on the day', () => {
    const first = anExpense({ id: 1, status: 'RECORDED', categoryId: 10, description: 'lunch' });
    const second = anExpense({ id: 2, status: 'RECORDED', categoryId: 10, description: 'dinner' });
    const day = aDay({ entries: [first, second] });

    renderSection({
      day,
      changeFailure: { key: 'RECORDED-2', message: 'That category no longer exists.' },
    });
    expandDays();

    const firstItem = screen.getByRole('listitem', { name: /lunch/i });
    const secondItem = screen.getByRole('listitem', { name: /dinner/i });
    expect(within(secondItem).getByRole('alert')).toHaveTextContent(
      'That category no longer exists.',
    );
    expect(within(firstItem).queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows no message on any row when there is no refusal', () => {
    const entry = anExpense({ id: 1, status: 'RECORDED', categoryId: 10, description: 'lunch' });
    const day = aDay({ entries: [entry] });

    renderSection({ day, changeFailure: null });
    expandDays();

    const item = screen.getByRole('listitem', { name: /lunch/i });
    expect(within(item).queryByRole('alert')).not.toBeInTheDocument();
  });

  it('offers no category control and leaks no id, null or undefined when the categoryNames map is empty', () => {
    const entry = anExpense({
      id: 1,
      status: 'RECORDED',
      categoryId: 10,
      description: 'stamps',
      merchant: 'Post Office',
    });
    const day = aDay({ entries: [entry] });

    renderSection({ day, categoryNames: new Map() });
    expandDays();

    const item = screen.getByRole('listitem', { name: /stamps/i });
    expect(item).toHaveTextContent('stamps');
    expect(item).toHaveTextContent('Post Office');
    expect(within(item).queryByRole('button')).not.toBeInTheDocument();
    expect(item).not.toHaveTextContent('10');
    expect(item).not.toHaveTextContent(/null|undefined/i);
  });

  it('focuses the day’s header when focusDay names this day', () => {
    const day = aDay({ day: '2026-08-01', entries: [anExpense({ id: 1, description: 'lunch' })] });

    renderSection({ day, focusDay: '2026-08-01' });

    expect(screen.getByRole('button')).toHaveFocus();
  });

  it('leaves the day’s header unfocused when focusDay names another day', () => {
    const day = aDay({ day: '2026-08-01', entries: [anExpense({ id: 1, description: 'lunch' })] });

    renderSection({ day, focusDay: '2026-08-02' });

    expect(screen.getByRole('button')).not.toHaveFocus();
  });
});
