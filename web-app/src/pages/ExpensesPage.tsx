import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError } from '../api/client';
import {
  listCategories,
  listExpenses,
  listGroupings,
  type Category,
  type ExpenseFilter,
  type ExpensePage,
  type Grouping,
} from '../api/expenses';
import { useAuth } from '../auth/useAuth';
import { ErrorBanner } from '../components/ErrorBanner';
import { ExpenseActionBar } from '../components/ExpenseActionBar';
import { ExpenseFilters } from '../components/ExpenseFilters';
import { ExpenseList } from '../components/ExpenseList';
import { Pager } from '../components/Pager';

// The endpoint's own page-size bound (D4), and the tick set's own cap once a merged day can hold more than a
// page's worth of pending entries (Q1).
const ACCEPTANCE_BOUND = 100;

export function ExpensesPage() {
  const { sessionExpired } = useAuth();
  const [filter, setFilter] = useState<ExpenseFilter>({});
  const [page, setPage] = useState<ExpensePage | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [groupings, setGroupings] = useState<Grouping[]>([]);
  const [failure, setFailure] = useState<string | null>(null);
  const [tickedIds, setTickedIds] = useState<ReadonlySet<number>>(new Set());

  const report = useCallback(
    (error: unknown) => {
      if (error instanceof ApiError && error.status === 401) {
        sessionExpired();
        return;
      }
      setFailure(error instanceof Error ? error.message : 'That read was not answered.');
    },
    [sessionExpired],
  );

  useEffect(() => {
    let cancelled = false;

    listExpenses(filter)
      .then((answered) => {
        if (!cancelled) {
          setPage(answered);
          setFailure(null);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          report(error);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [filter, report]);

  useEffect(() => {
    listCategories().then(setCategories).catch(report);
    listGroupings().then(setGroupings).catch(report);
  }, [report]);

  const categoryNames = useMemo(
    () => new Map(categories.map((category) => [category.id, category.name])),
    [categories],
  );

  // A narrower filter matches other rows, so the offset the previous one reached means nothing under it.
  const narrow = (next: ExpenseFilter) => setFilter({ ...next, offset: undefined });

  const onTick = useCallback((id: number, ticked: boolean) => {
    setTickedIds((prev) => {
      const next = new Set(prev);
      if (ticked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }, []);

  const onTickDay = useCallback((ids: number[], ticked: boolean) => {
    setTickedIds((prev) => {
      const next = new Set(prev);
      for (const id of ids) {
        if (ticked) {
          next.add(id);
        } else {
          next.delete(id);
        }
      }
      return next;
    });
  }, []);

  const atBound = tickedIds.size >= ACCEPTANCE_BOUND;

  // Stub: GU06 wires this to acceptExpenses, clears the ticks, and reads the touched days back with the
  // day merge.
  const onAccept = useCallback(() => {}, []);

  return (
    <div className="flex flex-col gap-6">
      {failure && <ErrorBanner message={failure} />}
      <ExpenseFilters
        groupings={groupings}
        categories={categories}
        filter={filter}
        onChange={narrow}
      />
      <ExpenseActionBar count={tickedIds.size} onAccept={onAccept} busy={false} />
      {page && (
        <>
          <ExpenseList
            page={page}
            categoryNames={categoryNames}
            tickedIds={tickedIds}
            onTick={onTick}
            onTickDay={onTickDay}
            atBound={atBound}
          />
          <Pager page={page} onOffset={(offset) => setFilter({ ...filter, offset })} />
        </>
      )}
    </div>
  );
}
