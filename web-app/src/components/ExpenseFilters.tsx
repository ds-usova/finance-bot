import { useTranslation } from 'react-i18next';
import type { Category, ExpenseFilter, ExpenseStatus, Grouping } from '../api/expenses';
import { CategoryFilter } from './CategoryFilter';
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

export function ExpenseFilters({ groupings, categories, filter, onChange }: ExpenseFiltersProps) {
  const { t } = useTranslation();

  const chooseCategory = (categoryId: number | undefined) => {
    onChange(withField(filter, 'categoryId', categoryId));
  };

  const chooseStatus = (value: string) => {
    const status = STATUSES.find((candidate) => candidate === value);
    onChange(withField(filter, 'status', status));
  };

  const choosePeriod = (period: Period) => {
    onChange(withField(withField(filter, 'from', period.from), 'to', period.to));
  };

  const narrowed =
    filter.status !== undefined || filter.categoryId !== undefined || filter.from !== undefined;

  const reset = () => {
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
      <CategoryFilter
        groupings={groupings}
        categories={categories}
        categoryId={filter.categoryId}
        onChange={chooseCategory}
      />
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
