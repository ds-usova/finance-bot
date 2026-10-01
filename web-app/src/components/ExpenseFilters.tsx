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
    // A passbook's form block: each filter one ruled line, side by side once the page is wide enough.
    <section aria-labelledby="filters-title" className="flex flex-col">
      {/* Always in the flow, whether or not it holds the reset: a row that comes and goes moves every
          control under it the moment a filter is set. */}
      <div className="flex min-h-9 items-center justify-between gap-4 border-b-2 border-double border-rule-strong pb-1">
        <h2 id="filters-title" className="column-head">
          {t('filters.title')}
        </h2>
        {narrowed && (
          <Button
            variant="ghost"
            className="-mr-2 h-8 px-2 text-sm font-medium underline decoration-rule-strong underline-offset-4"
            onClick={reset}
          >
            {t('filters.reset')}
          </Button>
        )}
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 sm:gap-x-6 sm:[&>*]:grid-cols-1 sm:[&>*]:gap-y-1 sm:[&>*]:pt-2.5 sm:[&>*]:pb-1">
        <CategoryFilter
          groupings={groupings}
          categories={categories}
          categoryId={filter.categoryId}
          onChange={chooseCategory}
        />
        <div className="form-line">
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
    </section>
  );
}
