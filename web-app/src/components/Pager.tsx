import type { ExpensePage } from '../api/expenses';

export type PagerProps = {
  page: ExpensePage;
  /** Called with the offset of the page asked for. */
  onOffset: (offset: number) => void;
};

export function Pager({ page, onOffset }: PagerProps) {
  const shown = page.items.length;
  const first = page.offset === 0;
  const last = page.offset + shown >= page.total;

  if (first && last) {
    return null;
  }

  // The step is the page size the ledger applied, not one the page chose.
  return (
    <nav className="pager" aria-label="Pages">
      <button
        type="button"
        disabled={first}
        onClick={() => onOffset(Math.max(0, page.offset - page.limit))}
      >
        Previous
      </button>
      {shown > 0 && (
        <span className="pager-range">{`Showing ${page.offset + 1}–${page.offset + shown} of ${page.total}.`}</span>
      )}
      <button type="button" disabled={last} onClick={() => onOffset(page.offset + page.limit)}>
        Next
      </button>
    </nav>
  );
}
