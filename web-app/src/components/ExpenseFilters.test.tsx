import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { ExpenseFilter } from '../api/expenses';
import { aCategory, aGrouping } from '../testing/fixtures';
import { substituteCatalogue } from '../testing/catalogue';
import { chooseOption } from '../testing/combobox';
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

describe('the filter controls', () => {
  it('offers every grouping and every category it was given, each by its accessible name', async () => {
    const { user } = renderFilters();

    await user.click(groupingControl());
    expect(await screen.findByRole('option', { name: 'Everyday' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Travel' })).toBeInTheDocument();
    await user.keyboard('{Escape}');

    await user.click(categoryControl());
    expect(await screen.findByRole('option', { name: 'Groceries' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Rent' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Flights' })).toBeInTheDocument();
  });

  it('narrows the offered categories to the grouping chosen, without a second call', async () => {
    const { onChange, user } = renderFilters();

    await chooseOption('Grouping', 'Everyday');
    await user.click(categoryControl());

    expect(await screen.findByRole('option', { name: 'Groceries' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Rent' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Flights' })).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('offers every category again when the grouping is cleared', async () => {
    const { onChange, user } = renderFilters({ categoryId: flights.id });

    await chooseOption('Grouping', 'Everyday');

    expect(onChange).toHaveBeenCalledWith({});

    await chooseOption('Grouping', 'All');
    expect(categoryControl()).toHaveTextContent('All');

    await user.click(categoryControl());

    expect(await screen.findByRole('option', { name: 'Groceries' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Rent' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Flights' })).toBeInTheDocument();
  });

  it('calls back with the chosen status, leaving the rest of the filter as it stood', async () => {
    const { onChange } = renderFilters({ categoryId: groceries.id, from: '2026-08-01' });

    await chooseOption('Status', 'Recorded');

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

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith({ from: '2026-08-01', to: '2026-08-31' });
  });

  it('calls back with the chosen category’s id', async () => {
    const { onChange } = renderFilters();

    await chooseOption('Category', 'Groceries');

    expect(onChange).toHaveBeenCalledWith({ categoryId: groceries.id });
  });

  it('labels the period by when a row was recorded, not by when the money was spent', () => {
    renderFilters();

    expect(screen.getByLabelText(/recorded from/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/recorded to/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/spent/i)).not.toBeInTheDocument();
  });

  it('sends no callback at all when only the first day of the period is set', async () => {
    const { onChange, user } = renderFilters();

    await user.type(screen.getByLabelText(/recorded from/i), '2026-08-01');

    expect(onChange).not.toHaveBeenCalled();
  });

  it('sends no callback at all when only the last day of the period is set', async () => {
    const { onChange, user } = renderFilters();

    await user.type(screen.getByLabelText(/recorded to/i), '2026-08-31');

    expect(onChange).not.toHaveBeenCalled();
  });

  it('sends no callback when the last day is cleared from a complete period', async () => {
    const { onChange, user } = renderFilters({ from: '2026-08-01', to: '2026-08-31' });

    await user.clear(screen.getByLabelText(/recorded to/i));

    expect(onChange).not.toHaveBeenCalled();
  });

  it('sends no callback when the first day is cleared from a complete period', async () => {
    const { onChange, user } = renderFilters({ from: '2026-08-01', to: '2026-08-31' });

    await user.clear(screen.getByLabelText(/recorded from/i));

    expect(onChange).not.toHaveBeenCalled();
  });

  it('calls back once with neither day when both days are cleared from a complete period', async () => {
    const { onChange, user } = renderFilters({ from: '2026-08-01', to: '2026-08-31' });

    await user.clear(screen.getByLabelText(/recorded from/i));
    await user.clear(screen.getByLabelText(/recorded to/i));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith({});
  });

  it('reads the status options as labels, Recorded and Pending, rather than the shouted wire value', async () => {
    const { user } = renderFilters();

    await user.click(statusControl());

    expect(await screen.findByRole('option', { name: 'Recorded' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Pending' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'RECORDED' })).not.toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'PENDING' })).not.toBeInTheDocument();
  });

  it('shows the catalogue’s substituted text for every label and every option', async () => {
    const restore = substituteCatalogue();
    try {
      const { user } = renderFilters();

      expect(screen.getByText('‹Grouping›')).toBeInTheDocument();
      expect(screen.getByText('‹Category›')).toBeInTheDocument();
      expect(screen.getByText('‹Status›')).toBeInTheDocument();
      expect(screen.getByLabelText('‹Recorded from›')).toBeInTheDocument();
      expect(screen.getByLabelText('‹Recorded to›')).toBeInTheDocument();

      await user.click(screen.getByRole('combobox', { name: '‹Grouping›' }));
      expect(await screen.findByRole('option', { name: '‹All›' })).toBeInTheDocument();
      await user.keyboard('{Escape}');

      await user.click(screen.getByRole('combobox', { name: '‹Status›' }));
      expect(await screen.findByRole('option', { name: '‹Recorded›' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: '‹Pending›' })).toBeInTheDocument();
    } finally {
      restore();
    }
  });
});
