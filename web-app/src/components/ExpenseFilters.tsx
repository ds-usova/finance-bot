import { useState } from 'react';
import type { Category, ExpenseFilter, ExpenseStatus, Grouping } from '../api/expenses';

export type ExpenseFiltersProps = {
  groupings: Grouping[];
  categories: Category[];
  filter: ExpenseFilter;
  onChange: (filter: ExpenseFilter) => void;
};

const STATUSES: ExpenseStatus[] = ['PENDING', 'RECORDED'];

function withField<K extends keyof ExpenseFilter>(
  filter: ExpenseFilter,
  key: K,
  value: ExpenseFilter[K],
): ExpenseFilter {
  const next = { ...filter };
  if (value === undefined) {
    delete next[key];
  } else {
    next[key] = value;
  }
  return next;
}

function under(categories: Category[], groupingId: number | undefined): Category[] {
  if (groupingId === undefined) {
    return categories;
  }
  return categories.filter((category) => category.groupingId === groupingId);
}

export function ExpenseFilters({ groupings, categories, filter, onChange }: ExpenseFiltersProps) {
  const [groupingId, setGroupingId] = useState<number | undefined>(undefined);

  const chooseGrouping = (value: string) => {
    const chosen = value === '' ? undefined : Number(value);
    setGroupingId(chosen);
    const selected = filter.categoryId;
    const stays = under(categories, chosen).some((category) => category.id === selected);
    if (selected !== undefined && !stays) {
      onChange(withField(filter, 'categoryId', undefined));
    }
  };

  const chooseCategory = (value: string) => {
    onChange(withField(filter, 'categoryId', value === '' ? undefined : Number(value)));
  };

  const chooseStatus = (value: string) => {
    const status = STATUSES.find((candidate) => candidate === value);
    onChange(withField(filter, 'status', status));
  };

  const chooseDay = (key: 'from' | 'to', value: string) => {
    onChange(withField(filter, key, value === '' ? undefined : value));
  };

  return (
    <div className="expense-filters">
      <div className="filter">
        <label htmlFor="filter-grouping">Grouping</label>
        <select
          id="filter-grouping"
          value={groupingId === undefined ? '' : String(groupingId)}
          onChange={(event) => chooseGrouping(event.target.value)}
        >
          <option value="">All</option>
          {groupings.map((grouping) => (
            <option key={grouping.id} value={String(grouping.id)}>
              {grouping.name}
            </option>
          ))}
        </select>
      </div>
      <div className="filter">
        <label htmlFor="filter-category">Category</label>
        <select
          id="filter-category"
          value={filter.categoryId === undefined ? '' : String(filter.categoryId)}
          onChange={(event) => chooseCategory(event.target.value)}
        >
          <option value="">All</option>
          {under(categories, groupingId).map((category) => (
            <option key={category.id} value={String(category.id)}>
              {category.name}
            </option>
          ))}
        </select>
      </div>
      <div className="filter">
        <label htmlFor="filter-status">Status</label>
        <select
          id="filter-status"
          value={filter.status ?? ''}
          onChange={(event) => chooseStatus(event.target.value)}
        >
          <option value="">All</option>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {status}
            </option>
          ))}
        </select>
      </div>
      <div className="filter">
        <label htmlFor="filter-from">Recorded from</label>
        <input
          id="filter-from"
          type="date"
          value={filter.from ?? ''}
          onChange={(event) => chooseDay('from', event.target.value)}
        />
      </div>
      <div className="filter">
        <label htmlFor="filter-to">Recorded to</label>
        <input
          id="filter-to"
          type="date"
          value={filter.to ?? ''}
          onChange={(event) => chooseDay('to', event.target.value)}
        />
      </div>
    </div>
  );
}
