import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import {
  acceptExpenses,
  changeCategory,
  listCategories,
  listExpenses,
  listGroupings,
  type Category,
  type Expense,
  type ExpenseFilter,
  type ExpensePage,
  type Grouping,
} from '../api/expenses';
import { useAuth } from '../auth/useAuth';
import { ErrorBanner } from '../components/ErrorBanner';
import { ExpenseActionBar } from '../components/ExpenseActionBar';
import {
  mergeDay,
  replaceEntry,
  ticksStillOnPage,
  touchedDaysOf,
  utcDayOf,
} from '../components/expenseDays';
import { ExpenseFilters } from '../components/ExpenseFilters';
import { ExpenseList } from '../components/ExpenseList';
import { Pager } from '../components/Pager';

// How many ids the acceptance endpoint takes in one request, and so the cap on the tick set — a merged day
// can hold more than a page's worth of pending entries, so the listing's page size does not bound it.
const ACCEPTANCE_BOUND = 100;

export function ExpensesPage() {
  const { t } = useTranslation();
  const { sessionExpired } = useAuth();
  const [filter, setFilter] = useState<ExpenseFilter>({});
  const [page, setPage] = useState<ExpensePage | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [groupings, setGroupings] = useState<Grouping[]>([]);
  const [failure, setFailure] = useState<string | null>(null);
  const [tickedIds, setTickedIds] = useState<ReadonlySet<number>>(new Set());
  const [accepting, setAccepting] = useState(false);
  const [missingMessage, setMissingMessage] = useState<string | null>(null);
  const [changingKey, setChangingKey] = useState<string | null>(null);
  const [changeFailure, setChangeFailure] = useState<{ key: string; message: string } | null>(
    null,
  );
  const [focusDay, setFocusDay] = useState<string | null>(null);

  // The filter and the page an acceptance's read back must use the values on screen when the answer arrives,
  // not the ones the call left with — a ref rather than the closed-over state keeps them current.
  const filterRef = useRef(filter);
  useEffect(() => {
    filterRef.current = filter;
  }, [filter]);
  const pageRef = useRef(page);
  useEffect(() => {
    pageRef.current = page;
  }, [page]);

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

  const onTick = useCallback((id: number, ticked: boolean) => onTickDay([id], ticked), [onTickDay]);

  const tickHeadroom = ACCEPTANCE_BOUND - tickedIds.size;

  // Re-reads the days an acceptance touched, spanning from the earliest to the latest, carrying the filter on
  // screen now rather than the one the acceptance call left with, and merges each back into the page.
  const rereadTouchedDays = useCallback(
    (days: Set<string>) => {
      if (days.size === 0) {
        return;
      }
      const sorted = Array.from(days).sort();
      const currentFilter = filterRef.current;

      listExpenses({
        status: currentFilter.status,
        categoryId: currentFilter.categoryId,
        from: sorted[0],
        to: sorted[sorted.length - 1],
        offset: undefined,
        limit: ACCEPTANCE_BOUND,
      })
        .then((fresh) => {
          const current = pageRef.current;
          if (!current) {
            return;
          }
          setPage(sorted.reduce((merged, day) => mergeDay(merged, day, fresh), current));
        })
        .catch(report);
    },
    [report],
  );

  // Re-reads the single day an entry sits on, carrying the filter on screen now, merges it back into the page,
  // drops any tick a refile carried off the page, and moves focus to that day's header when the entry named is
  // no longer on it — the row's own control just left with it.
  const rereadChangedDay = useCallback(
    (entry: Expense) => {
      const day = utcDayOf(entry.createdAt);
      const currentFilter = filterRef.current;

      listExpenses({
        status: currentFilter.status,
        categoryId: currentFilter.categoryId,
        from: day,
        to: day,
        offset: undefined,
        limit: ACCEPTANCE_BOUND,
      })
        .then((fresh) => {
          const current = pageRef.current;
          if (!current) {
            return;
          }
          const merged = mergeDay(current, day, fresh);
          setPage(merged);
          setTickedIds((prev) => ticksStillOnPage(merged, prev));
          const stillOnPage = merged.items.some(
            (item) => item.status === entry.status && item.id === entry.id,
          );
          setFocusDay(stillOnPage ? null : day);
        })
        .catch(report);
    },
    [report],
  );

  const onChangeCategory = useCallback(
    (entry: Expense, categoryId: number) => {
      if (categoryId === entry.categoryId || changingKey !== null) {
        return;
      }
      const key = `${entry.status}-${entry.id}`;
      setChangingKey(key);
      setChangeFailure(null);

      changeCategory(entry, categoryId)
        .then((answered) => {
          setChangingKey(null);
          if (filterRef.current.categoryId !== undefined) {
            // A category filter can no longer match the row's new category, so only a fresh read tells whether
            // it still belongs on the page.
            rereadChangedDay(answered);
            return;
          }
          setPage((current) => (current ? replaceEntry(current, answered) : current));
        })
        .catch((error: unknown) => {
          setChangingKey(null);
          if (error instanceof ApiError && error.status === 401) {
            sessionExpired();
            return;
          }
          setChangeFailure({
            key,
            message: error instanceof Error ? error.message : 'That change was not answered.',
          });
          if (error instanceof ApiError && error.status === 404) {
            // The row has moved on under the ledger; only a fresh read can tell it apart from the page.
            rereadChangedDay(entry);
          }
        });
    },
    [changingKey, rereadChangedDay, sessionExpired],
  );

  const onAccept = useCallback(() => {
    if (accepting || tickedIds.size === 0) {
      return;
    }
    const ids = Array.from(tickedIds);
    const currentPage = pageRef.current;
    const days = currentPage ? touchedDaysOf(currentPage, ids) : new Set<string>();

    setAccepting(true);
    acceptExpenses(ids)
      .then((acceptance) => {
        setAccepting(false);
        setTickedIds(new Set());
        setFailure(null);
        setMissingMessage(
          acceptance.missing > 0
            ? t('listing.acceptanceMissing', { count: acceptance.missing })
            : null,
        );
        rereadTouchedDays(days);
      })
      .catch((error: unknown) => {
        setAccepting(false);
        report(error);
      });
  }, [accepting, tickedIds, rereadTouchedDays, report, t]);

  return (
    <div className="flex flex-col gap-6">
      {failure && <ErrorBanner message={failure} />}
      <ExpenseFilters
        groupings={groupings}
        categories={categories}
        filter={filter}
        onChange={narrow}
      />
      <ExpenseActionBar count={tickedIds.size} onAccept={onAccept} busy={accepting} />
      {missingMessage && <p className="text-sm text-muted-foreground">{missingMessage}</p>}
      {page && (
        <>
          <ExpenseList
            page={page}
            categoryNames={categoryNames}
            categories={categories}
            groupings={groupings}
            tickedIds={tickedIds}
            onTick={onTick}
            onTickDay={onTickDay}
            tickHeadroom={tickHeadroom}
            changingKey={changingKey}
            changeFailure={changeFailure}
            focusDay={focusDay}
            onChangeCategory={onChangeCategory}
          />
          <Pager page={page} onOffset={(offset) => setFilter({ ...filter, offset })} />
        </>
      )}
    </div>
  );
}
