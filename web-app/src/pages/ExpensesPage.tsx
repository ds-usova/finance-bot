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

export function ExpensesPage() {
  const { signOut, sessionExpired } = useAuth();
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
    listExpenses(filter)
      .then((answered) => {
        setPage(answered);
        setFailure(null);
      })
      .catch(report);
  }, [filter, report]);

  useEffect(() => {
    listCategories().then(setCategories).catch(report);
    listGroupings().then(setGroupings).catch(report);
  }, [report]);

  const categoryNames = useMemo(
    () => new Map(categories.map((category) => [category.id, category.name])),
    [categories],
  );

  return (
    <main>
      <header>
        <h1>Expenses</h1>
        <button
          type="button"
          onClick={() => {
            void signOut();
          }}
        >
          Sign out
        </button>
      </header>
      {failure && <ErrorBanner message={failure} />}
      <ExpenseFilters
        groupings={groupings}
        categories={categories}
        filter={filter}
        onChange={setFilter}
      />
      {page && <ExpenseList page={page} categoryNames={categoryNames} />}
    </main>
  );
}
