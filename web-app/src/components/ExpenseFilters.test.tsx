import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { ExpenseFilter } from '../api/expenses';
import { aCategory, aGrouping } from '../testing/fixtures';
import { ExpenseFilters } from './ExpenseFilters';

const everyday = aGrouping({ id: 100, name: 'Everyday' });
const travel = aGrouping({ id: 200, name: 'Travel' });

const groceries = aCategory({
  id: 10,
  name: 'Groceries',
  groupingId: everyday.id,
  groupingName: everyday.name,
});
const rent = aCategory({
  id: 11,
  name: 'Rent',
  groupingId: everyday.id,
  groupingName: everyday.name,
});
const flights = aCategory({
  id: 20,
  name: 'Flights',
  groupingId: travel.id,
  groupingName: travel.name,
});

function renderFilters(filter: ExpenseFilter = {}) {
  const onChange = vi.fn();
  const user = userEvent.setup();

  function Harness() {
    const [current, setCurrent] = useState(filter);

    return (
      <ExpenseFilters
        groupings={[everyday, travel]}
        categories={[groceries, rent, flights]}
        filter={current}
        onChange={(next) => {
          onChange(next);
          setCurrent(next);
        }}
      />
    );
  }

  render(<Harness />);

  return { onChange, user };
}

function groupingControl(): HTMLElement {
  return screen.getByRole('combobox', { name: 'Grouping' });
}

function categoryControl(): HTMLElement {
  return screen.getByRole('combobox', { name: 'Category' });
}

function statusControl(): HTMLElement {
  return screen.getByRole('combobox', { name: 'Status' });
}

function offeredCategoryNames(): string[] {
  return within(categoryControl())
    .getAllByRole('option')
    .map((option) => option.textContent ?? '');
}

describe('the filter controls', () => {
  it('offers every grouping and every category it was given, each by its accessible name', () => {
    renderFilters();

    const groupings = within(groupingControl());
    expect(groupings.getByRole('option', { name: 'Everyday' })).toBeInTheDocument();
    expect(groupings.getByRole('option', { name: 'Travel' })).toBeInTheDocument();

    const categories = within(categoryControl());
    expect(categories.getByRole('option', { name: 'Groceries' })).toBeInTheDocument();
    expect(categories.getByRole('option', { name: 'Rent' })).toBeInTheDocument();
    expect(categories.getByRole('option', { name: 'Flights' })).toBeInTheDocument();
  });

  it('narrows the offered categories to the grouping chosen, without a second call', async () => {
    const { onChange, user } = renderFilters();

    await user.selectOptions(groupingControl(), String(everyday.id));

    expect(offeredCategoryNames()).toContain('Groceries');
    expect(offeredCategoryNames()).toContain('Rent');
    expect(offeredCategoryNames()).not.toContain('Flights');
    expect(onChange).not.toHaveBeenCalled();
  });

  it('offers every category again when the grouping is cleared', async () => {
    const { onChange, user } = renderFilters({ categoryId: flights.id });

    await user.selectOptions(groupingControl(), String(everyday.id));

    expect(onChange).toHaveBeenCalledWith({});

    await user.selectOptions(groupingControl(), '');

    expect(offeredCategoryNames()).toContain('Groceries');
    expect(offeredCategoryNames()).toContain('Rent');
    expect(offeredCategoryNames()).toContain('Flights');
    expect(categoryControl()).toHaveValue('');
  });

  it('calls back with the chosen status, leaving the rest of the filter as it stood', async () => {
    const { onChange, user } = renderFilters({ categoryId: groceries.id, from: '2026-08-01' });

    await user.selectOptions(statusControl(), 'RECORDED');

    expect(onChange).toHaveBeenCalledWith({
      categoryId: groceries.id,
      from: '2026-08-01',
      status: 'RECORDED',
    });
  });

  it('calls back with both days of the period entered', async () => {
    const { onChange, user } = renderFilters();

    await user.type(screen.getByLabelText(/recorded from/i), '2026-08-01');
    await user.type(screen.getByLabelText(/recorded to/i), '2026-08-31');

    expect(onChange).toHaveBeenLastCalledWith({ from: '2026-08-01', to: '2026-08-31' });
  });

  it('calls back with the chosen category’s id', async () => {
    const { onChange, user } = renderFilters();

    await user.selectOptions(categoryControl(), String(groceries.id));

    expect(onChange).toHaveBeenCalledWith({ categoryId: groceries.id });
  });

  it('labels the period by when a row was recorded, not by when the money was spent', () => {
    renderFilters();

    expect(screen.getByLabelText(/recorded from/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/recorded to/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/spent/i)).not.toBeInTheDocument();
  });
});
