import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Category, ExpenseFilter, ExpenseStatus, Grouping } from '../api/expenses';
import { Input } from './ui/input';
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
  // Held locally so the field stays typeable while the pair is incomplete — the callback below only fires
  // once both days are set or both are cleared, so the filter prop alone cannot echo a lone keystroke back.
  const [fromValue, setFromValue] = useState(filter.from ?? '');
  const [toValue, setToValue] = useState(filter.to ?? '');

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

  // Calls back only once the period is a complete pair or fully empty; a lone day never reaches the ledger.
  const chooseDay = (key: 'from' | 'to', value: string) => {
    const nextFrom = key === 'from' ? value : fromValue;
    const nextTo = key === 'to' ? value : toValue;
    if (key === 'from') {
      setFromValue(value);
    } else {
      setToValue(value);
    }

    const bothSet = nextFrom !== '' && nextTo !== '';
    const neitherSet = nextFrom === '' && nextTo === '';
    if (bothSet || neitherSet) {
      onChange(
        withField(
          withField(filter, 'from', nextFrom === '' ? undefined : nextFrom),
          'to',
          nextTo === '' ? undefined : nextTo,
        ),
      );
    }
  };

  return (
    <div className="flex flex-wrap items-end gap-4">
      <div className="flex flex-col gap-1">
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
      <div className="flex flex-col gap-1">
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
      <div className="flex flex-col gap-1">
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
      <div className="flex flex-col gap-1">
        <Label htmlFor="filter-from">{t('filters.from')}</Label>
        <Input
          id="filter-from"
          type="date"
          value={fromValue}
          onChange={(event) => chooseDay('from', event.target.value)}
        />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="filter-to">{t('filters.to')}</Label>
        <Input
          id="filter-to"
          type="date"
          value={toValue}
          onChange={(event) => chooseDay('to', event.target.value)}
        />
      </div>
    </div>
  );
}
