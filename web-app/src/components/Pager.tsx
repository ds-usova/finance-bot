import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { ExpensePage } from '../api/expenses';
import { Button } from './ui/button';

export type PagerProps = {
  page: ExpensePage;
  /** Called with the offset of the page asked for. */
  onOffset: (offset: number) => void;
};

// The page's folio: turn back, where this page sits in the book, turn on.
export function Pager({ page, onOffset }: PagerProps) {
  const { t } = useTranslation();
  const shown = page.items.length;
  const first = page.offset === 0;
  const last = page.offset + shown >= page.total;

  if (first && last) {
    return null;
  }

  // The step is the page size the ledger applied, not one the page chose.
  return (
    <nav
      className="mt-6 grid grid-cols-[auto_1fr_auto] items-center gap-2 border-t-2 border-double border-rule-strong pt-3"
      aria-label="Pages"
    >
      <Button
        type="button"
        variant="ghost"
        className="-ml-2 px-2"
        disabled={first}
        onClick={() => onOffset(Math.max(0, page.offset - page.limit))}
      >
        <ChevronLeft aria-hidden="true" className="h-4 w-4" />
        {t('paging.previous')}
      </Button>
      {shown > 0 ? (
        <span className="text-center font-mono text-xs text-pencil tabular-nums">
          {t('paging.range', { from: page.offset + 1, to: page.offset + shown, total: page.total })}
        </span>
      ) : (
        <span />
      )}
      <Button
        type="button"
        variant="ghost"
        className="-mr-2 px-2"
        disabled={last}
        onClick={() => onOffset(page.offset + page.limit)}
      >
        {t('paging.next')}
        <ChevronRight aria-hidden="true" className="h-4 w-4" />
      </Button>
    </nav>
  );
}
