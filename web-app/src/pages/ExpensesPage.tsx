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
import { ExpenseFilters } from '../components/ExpenseFilters';
import { ExpenseList } from '../components/ExpenseList';
import { Pager } from '../components/Pager';

export function ExpensesPage() {
  const { sessionExpired } = useAuth();
  const [filter, setFilter] = useState<ExpenseFilter>({});
  const [page, setPage] = useState<ExpensePage | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [groupings, setGroupings] = useState<Grouping[]>([]);
  const [failure, setFailure] = useState<string | null>(null);

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

  return (
    <div className="flex flex-col gap-6">
      {failure && <ErrorBanner message={failure} />}
      <ExpenseFilters
        groupings={groupings}
        categories={categories}
        filter={filter}
        onChange={narrow}
      />
      {page && (
        <>
          <ExpenseList page={page} categoryNames={categoryNames} />
          <Pager page={page} onOffset={(offset) => setFilter({ ...filter, offset })} />
        </>
      )}
    </div>
  );
}
