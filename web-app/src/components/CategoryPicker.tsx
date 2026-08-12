import { Check } from 'lucide-react';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Category, Grouping } from '../api/expenses';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from './ui/command';
import { Popover, PopoverContent, PopoverTrigger } from './ui/popover';

export type CategoryPickerProps = {
  /** Only for the order the sections appear in; every category already names its own grouping. */
  groupings: Grouping[];
  categories: Category[];
  categoryId: number | undefined;
  onChange: (categoryId: number | undefined) => void;
  /** The control that opens the list, rendered as the popover's own trigger. */
  trigger: ReactNode;
  /** Whether an entry meaning "no category" is offered — the filter's, never a row's. */
  withAll: boolean;
  /** What the popup is sized by: the trigger's width for the filter, a width of its own for a row. */
  width: 'trigger' | 'own';
  searchPlaceholder: string;
  emptyText: string;
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

export function CategoryPicker({
  groupings,
  categories,
  categoryId,
  onChange,
  trigger,
  withAll,
  width,
  searchPlaceholder,
  emptyText,
}: CategoryPickerProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  const chosen = categories.find((category) => category.id === categoryId);
  const sections = toSections(groupings, categories);

  const choose = (next: number | undefined) => {
    onChange(next);
    setOpen(false);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>{trigger}</PopoverTrigger>
      <PopoverContent
        className={width === 'trigger' ? 'w-(--radix-popover-trigger-width) p-0' : 'w-64 p-0'}
      >
        <Command>
          <CommandInput placeholder={searchPlaceholder} />
          <CommandList>
            <CommandEmpty>{emptyText}</CommandEmpty>
            {withAll && (
              <CommandGroup>
                <CommandItem value={t('filters.all')} onSelect={() => choose(undefined)}>
                  <Check
                    aria-hidden="true"
                    className={`h-4 w-4 ${chosen ? 'opacity-0' : 'opacity-100'}`}
                  />
                  {t('filters.all')}
                </CommandItem>
              </CommandGroup>
            )}
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
  );
}
