import { useTranslation } from 'react-i18next';
import type { ExpensePage } from '../api/expenses';
import { Button } from './ui/button';

export type PagerProps = {
  page: ExpensePage;
  /** Called with the offset of the page asked for. */
  onOffset: (offset: number) => void;
};

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
    <nav className="flex items-center justify-between gap-4" aria-label="Pages">
      <Button
        type="button"
        variant="outline"
        disabled={first}
        onClick={() => onOffset(Math.max(0, page.offset - page.limit))}
      >
        {t('paging.previous')}
      </Button>
      {shown > 0 && (
        <span className="text-sm tabular-nums text-muted-foreground">
          {t('paging.range', { from: page.offset + 1, to: page.offset + shown, total: page.total })}
        </span>
      )}
      <Button
        type="button"
        variant="outline"
        disabled={last}
        onClick={() => onOffset(page.offset + page.limit)}
      >
        {t('paging.next')}
      </Button>
    </nav>
  );
}
