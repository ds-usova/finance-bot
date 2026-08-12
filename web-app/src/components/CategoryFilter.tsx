import { ChevronsUpDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { Category, Grouping } from '../api/expenses';
import { CategoryPicker } from './CategoryPicker';
import { Button } from './ui/button';
import { Label } from './ui/label';

export type CategoryFilterProps = {
  /** Only for the order the sections appear in; every category already names its own grouping. */
  groupings: Grouping[];
  categories: Category[];
  categoryId: number | undefined;
  onChange: (categoryId: number | undefined) => void;
};

export function CategoryFilter({
  groupings,
  categories,
  categoryId,
  onChange,
}: CategoryFilterProps) {
  const { t } = useTranslation();

  const chosen = categories.find((category) => category.id === categoryId);

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <Label id="filter-category-label">{t('filters.category')}</Label>
      <CategoryPicker
        groupings={groupings}
        categories={categories}
        categoryId={categoryId}
        onChange={onChange}
        withAll
        width="trigger"
        searchPlaceholder={t('filters.searchCategories')}
        emptyText={t('filters.noCategory')}
        trigger={
          <Button
            variant="outline"
            size="field"
            aria-labelledby="filter-category-label filter-category-value"
            className="font-normal"
          >
            <span id="filter-category-value" className="truncate">
              {chosen ? chosen.name : t('filters.all')}
            </span>
            <ChevronsUpDown aria-hidden="true" className="h-4 w-4 shrink-0 opacity-50" />
          </Button>
        }
      />
    </div>
  );
}
