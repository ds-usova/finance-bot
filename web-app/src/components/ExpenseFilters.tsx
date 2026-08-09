import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Category, ExpenseFilter, ExpenseStatus, Grouping } from '../api/expenses';
import { PeriodFilter, type Period } from './PeriodFilter';
import { Button } from './ui/button';
import { Label } from './ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './ui/select';

export type ExpenseFiltersProps = {
  groupings: Grouping[];
  categories: Category[];
  filter: ExpenseFilter;
  onChange: (filter: ExpenseFilter) => void;
};

const STATUSES: ExpenseStatus[] = ['PENDING', 'RECORDED'];
const ALL = 'all';

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
  const { t } = useTranslation();
  const [groupingId, setGroupingId] = useState<number | undefined>(undefined);

  const chooseGrouping = (value: string) => {
    const chosen = value === ALL ? undefined : Number(value);
    setGroupingId(chosen);
    const selected = filter.categoryId;
    const stays = under(categories, chosen).some((category) => category.id === selected);
    if (selected !== undefined && !stays) {
      onChange(withField(filter, 'categoryId', undefined));
    }
  };

  const chooseCategory = (value: string) => {
    onChange(withField(filter, 'categoryId', value === ALL ? undefined : Number(value)));
  };

  const chooseStatus = (value: string) => {
    const status = STATUSES.find((candidate) => candidate === value);
    onChange(withField(filter, 'status', status));
  };

  const choosePeriod = (period: Period) => {
    onChange(withField(withField(filter, 'from', period.from), 'to', period.to));
  };

  // Everything the person set, and nothing the page holds on its own: the grouping narrows the categories
  // offered rather than the listing, so it is reset here without ever having been part of the filter.
  const narrowed =
    filter.status !== undefined ||
    filter.categoryId !== undefined ||
    filter.from !== undefined ||
    groupingId !== undefined;

  const reset = () => {
    setGroupingId(undefined);
    onChange({});
  };

  return (
    <div className="grid grid-cols-1 gap-4 rounded-xl border border-border bg-card p-4 shadow-sm sm:grid-cols-3 sm:p-5">
      {/* Always in the flow, whether or not it holds the reset: a row that comes and goes moves every
          control under it the moment a filter is set. */}
      <div className="flex min-h-9 items-center justify-between gap-4 sm:col-span-3">
        <span className="text-sm font-medium text-muted-foreground">{t('filters.title')}</span>
        {narrowed && (
          <Button variant="ghost" className="h-auto px-2 py-1 text-sm font-normal" onClick={reset}>
            {t('filters.reset')}
          </Button>
        )}
      </div>
      <div className="flex min-w-0 flex-col gap-1.5">
        <Label htmlFor="filter-grouping">{t('filters.grouping')}</Label>
        <Select
          value={groupingId === undefined ? ALL : String(groupingId)}
          onValueChange={chooseGrouping}
        >
          <SelectTrigger id="filter-grouping">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('filters.all')}</SelectItem>
            {groupings.map((grouping) => (
              <SelectItem key={grouping.id} value={String(grouping.id)}>
                {grouping.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex min-w-0 flex-col gap-1.5">
        <Label htmlFor="filter-category">{t('filters.category')}</Label>
        <Select
          value={filter.categoryId === undefined ? ALL : String(filter.categoryId)}
          onValueChange={chooseCategory}
        >
          <SelectTrigger id="filter-category">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('filters.all')}</SelectItem>
            {under(categories, groupingId).map((category) => (
              <SelectItem key={category.id} value={String(category.id)}>
                {category.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex min-w-0 flex-col gap-1.5">
        <Label htmlFor="filter-status">{t('filters.status')}</Label>
        <Select value={filter.status ?? ALL} onValueChange={chooseStatus}>
          <SelectTrigger id="filter-status">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('filters.all')}</SelectItem>
            <SelectItem value="RECORDED">{t('filters.statusRecorded')}</SelectItem>
            <SelectItem value="PENDING">{t('filters.statusPending')}</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <PeriodFilter period={{ from: filter.from, to: filter.to }} onChange={choosePeriod} />
    </div>
  );
}
