import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { aCategory, aGrouping } from '../testing/fixtures';
import { CategoryPicker, type CategoryPickerProps } from './CategoryPicker';

/** The picker under test, with the props a case says nothing about left at sensible defaults. */
function renderPicker(props: Partial<CategoryPickerProps> = {}) {
  const onChange = vi.fn();
  const user = userEvent.setup();

  render(
    <CategoryPicker
      groupings={[]}
      categories={[]}
      categoryId={undefined}
      onChange={onChange}
      trigger={<button>Open</button>}
      withAll={false}
      width="own"
      searchPlaceholder="Search categories"
      emptyText="No category found."
      {...props}
    />,
  );

  return { onChange, user };
}

async function open(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'Open' }));
}

describe('the picker', () => {
  it('offers every category under its grouping’s heading, in the groupings’ own order', async () => {
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

    const { user } = renderPicker({
      groupings: [everyday, travel],
      categories: [groceries, rent, flights],
    });
    await open(user);

    expect(await screen.findByRole('option', { name: 'Groceries' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Rent' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Flights' })).toBeInTheDocument();

    // The headings and their options in document order, so the groupings' own order is proved rather than
    // merely which options exist.
    const text = document.body.textContent!;
    expect(text.indexOf('Everyday')).toBeLessThan(text.indexOf('Groceries'));
    expect(text.indexOf('Groceries')).toBeLessThan(text.indexOf('Travel'));
    expect(text.indexOf('Travel')).toBeLessThan(text.indexOf('Flights'));
  });

  it('offers no entry meaning "no category" when withAll is false, so every option names a real category', async () => {
    const groceries = aCategory({ id: 10, name: 'Groceries' });

    const { user } = renderPicker({
      categories: [groceries],
      groupings: [aGrouping()],
      withAll: false,
    });
    await open(user);

    expect(await screen.findByRole('option', { name: 'Groceries' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'All' })).not.toBeInTheDocument();
    expect(screen.getAllByRole('option')).toHaveLength(1);
  });

  it('offers an entry meaning "no category" when withAll is true, and answers undefined when it is chosen', async () => {
    const groceries = aCategory({ id: 10, name: 'Groceries' });

    const { user, onChange } = renderPicker({
      categories: [groceries],
      groupings: [aGrouping()],
      withAll: true,
    });
    await open(user);

    await user.click(await screen.findByRole('option', { name: 'All' }));

    expect(onChange).toHaveBeenCalledWith(undefined);
  });

  it('narrows to a grouping’s categories when its name is typed, so typing a grouping finds what it holds', async () => {
    const everyday = aGrouping({ id: 100, name: 'Everyday' });
    const travel = aGrouping({ id: 200, name: 'Travel' });
    const groceries = aCategory({
      id: 10,
      name: 'Groceries',
      groupingId: everyday.id,
      groupingName: everyday.name,
    });
    const flights = aCategory({
      id: 20,
      name: 'Flights',
      groupingId: travel.id,
      groupingName: travel.name,
    });

    const { user } = renderPicker({
      groupings: [everyday, travel],
      categories: [groceries, flights],
    });
    await open(user);
    await user.type(screen.getByPlaceholderText('Search categories'), 'Travel');

    expect(screen.getByRole('option', { name: 'Flights' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Groceries' })).not.toBeInTheDocument();
  });

  it('shows the caller’s own empty text when the search matches nothing', async () => {
    const groceries = aCategory({ id: 10, name: 'Groceries' });

    const { user } = renderPicker({
      categories: [groceries],
      groupings: [aGrouping()],
      emptyText: 'Nothing matches that search.',
    });
    await open(user);
    await user.type(screen.getByPlaceholderText('Search categories'), 'zzz');

    expect(screen.getByText('Nothing matches that search.')).toBeInTheDocument();
  });

  it('offers two groupings’ category of the same name as two separate options', async () => {
    const everyday = aGrouping({ id: 100, name: 'Everyday' });
    const travel = aGrouping({ id: 200, name: 'Travel' });
    const everydayUtilities = aCategory({
      id: 10,
      name: 'Utilities',
      groupingId: everyday.id,
      groupingName: everyday.name,
    });
    const travelUtilities = aCategory({
      id: 20,
      name: 'Utilities',
      groupingId: travel.id,
      groupingName: travel.name,
    });

    const { user } = renderPicker({
      groupings: [everyday, travel],
      categories: [everydayUtilities, travelUtilities],
    });
    await open(user);

    expect(await screen.findAllByRole('option', { name: 'Utilities' })).toHaveLength(2);
  });

  it('offers a category whose grouping the tree did not answer, under a heading of its own name', async () => {
    const orphan = aCategory({
      id: 30,
      name: 'Orphan',
      groupingId: 999,
      groupingName: 'Misc',
    });

    const { user } = renderPicker({ groupings: [], categories: [orphan] });
    await open(user);

    expect(await screen.findByRole('option', { name: 'Orphan' })).toBeInTheDocument();
    expect(screen.getByText('Misc')).toBeInTheDocument();
  });
});
