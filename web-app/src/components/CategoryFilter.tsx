import { Check, ChevronsUpDown } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Category, Grouping } from '../api/expenses';
import { Button } from './ui/button';
import { Label } from './ui/label';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from './ui/command';
import { Popover, PopoverContent, PopoverTrigger } from './ui/popover';

export type CategoryFilterProps = {
  /** Only for the order the sections appear in; every category already names its own grouping. */
  groupings: Grouping[];
  categories: Category[];
  categoryId: number | undefined;
  onChange: (categoryId: number | undefined) => void;
};

type Section = {
  name: string;
  categories: Category[];
};

/** The groupings' order first, then anything whose grouping the tree did not answer, in the order met. */
function toSections(groupings: Grouping[], categories: Category[]): Section[] {
  const sections: Section[] = groupings
    .map((grouping) => ({
      name: grouping.name,
      categories: categories.filter((category) => category.groupingId === grouping.id),
    }))
    .filter((section) => section.categories.length > 0);

  const placed = new Set(groupings.map((grouping) => grouping.id));
  for (const category of categories) {
    if (placed.has(category.groupingId)) {
      continue;
    }
    const existing = sections.find((section) => section.name === category.groupingName);
    if (existing) {
      existing.categories.push(category);
    } else {
      sections.push({ name: category.groupingName, categories: [category] });
    }
  }

  return sections;
}

export function CategoryFilter({
  groupings,
  categories,
  categoryId,
  onChange,
}: CategoryFilterProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  const chosen = categories.find((category) => category.id === categoryId);
  const sections = toSections(groupings, categories);

  const choose = (next: number | undefined) => {
    onChange(next);
    setOpen(false);
  };

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <Label id="filter-category-label">{t('filters.category')}</Label>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
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
        </PopoverTrigger>
        <PopoverContent className="w-(--radix-popover-trigger-width) p-0">
          <Command>
            <CommandInput placeholder={t('filters.searchCategories')} />
            <CommandList>
              <CommandEmpty>{t('filters.noCategory')}</CommandEmpty>
              <CommandGroup>
                <CommandItem value={t('filters.all')} onSelect={() => choose(undefined)}>
                  <Check
                    aria-hidden="true"
                    className={`h-4 w-4 ${chosen ? 'opacity-0' : 'opacity-100'}`}
                  />
                  {t('filters.all')}
                </CommandItem>
              </CommandGroup>
              {sections.map((section) => (
                <CommandGroup key={section.name} heading={section.name}>
                  {section.categories.map((category) => (
                    <CommandItem
                      key={category.id}
                      // The grouping rides along so that typing a grouping's name finds its categories,
                      // and so that two groupings holding the same category name stay distinct entries.
                      value={`${section.name} ${category.name}`}
                      onSelect={() => choose(category.id)}
                    >
                      <Check
                        aria-hidden="true"
                        className={`h-4 w-4 ${category.id === categoryId ? 'opacity-100' : 'opacity-0'}`}
                      />
                      {category.name}
                    </CommandItem>
                  ))}
                </CommandGroup>
              ))}
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
    </div>
  );
}
