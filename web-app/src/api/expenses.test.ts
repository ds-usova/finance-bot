import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, stubFetch } from '../testing/fetchStub';
import { aCategory, aGrouping, anAcceptance, anExpense, anExpensePage } from '../testing/fixtures';
import {
  acceptExpenses,
  changeCategory,
  listCategories,
  listExpenses,
  listGroupings,
} from './expenses';

describe('the expense calls', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function requestedUrl(fetchMock: ReturnType<typeof stubFetch>) {
    return new URL(String(fetchMock.mock.calls[0]?.[0]), 'http://localhost');
  }

  it('asks for the listing with no query string when the filter names nothing', async () => {
    const fetchMock = stubFetch(jsonResponse(anExpensePage()));

    await listExpenses({});

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/expenses');
  });

  it('carries every filter field it was given, and nothing else', async () => {
    const fetchMock = stubFetch(jsonResponse(anExpensePage()));

    await listExpenses({
      status: 'RECORDED',
      categoryId: 10,
      from: '2026-08-01',
      to: '2026-08-31',
      limit: 25,
      offset: 50,
    });

    const url = requestedUrl(fetchMock);
    expect(url.pathname).toBe('/api/v1/expenses');
    expect(Object.fromEntries(url.searchParams)).toEqual({
      status: 'RECORDED',
      categoryId: '10',
      from: '2026-08-01',
      to: '2026-08-31',
      limit: '25',
      offset: '50',
    });
  });

  it('answers the page the ledger sent, as it stands', async () => {
    const page = anExpensePage([anExpense({ id: 7 }), anExpense({ id: 8 })], {
      limit: 25,
      offset: 50,
      total: 120,
    });
    stubFetch(jsonResponse(page));

    await expect(listExpenses({})).resolves.toEqual(page);
  });

  it('lets a refusal reach the caller rather than answering an empty page', async () => {
    stubFetch(jsonResponse({ message: 'from must not be after to' }, 400));

    await expect(listExpenses({ from: '2026-08-31', to: '2026-08-01' })).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
    });
  });

  it('asks for the categories with no query string when no grouping is named', async () => {
    const fetchMock = stubFetch(jsonResponse([aCategory()]));

    await listCategories();

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/categories');
  });

  it('names the grouping it was given as the query parameter', async () => {
    const fetchMock = stubFetch(jsonResponse([aCategory()]));

    await listCategories(100);

    const url = requestedUrl(fetchMock);
    expect(url.pathname).toBe('/api/v1/categories');
    expect(Object.fromEntries(url.searchParams)).toEqual({ groupingId: '100' });
  });

  it('answers every grouping the ledger sent', async () => {
    const groupings = [aGrouping(), aGrouping({ id: 200, name: 'Travel' })];
    const fetchMock = stubFetch(jsonResponse(groupings));

    await expect(listGroupings()).resolves.toEqual(groupings);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/groupings');
  });

  describe('accepting expenses', () => {
    beforeEach(() => {
      document.cookie = 'XSRF-TOKEN=csrf-token-value; path=/';
    });

    afterEach(() => {
      document.cookie = 'XSRF-TOKEN=; path=/; max-age=0';
    });

    it('posts the ids to the acceptances endpoint, carrying the CSRF header and the cookies', async () => {
      const fetchMock = stubFetch(jsonResponse(anAcceptance()));

      await acceptExpenses([1, 2, 3]);

      expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/expenses/acceptances');
      const [, init] = fetchMock.mock.calls[0]!;
      expect(init).toMatchObject({ method: 'POST', credentials: 'include' });
      expect(JSON.parse(String(init?.body))).toEqual({ ids: [1, 2, 3] });
      const headers = new Headers(init?.headers);
      expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token-value');
    });

    it('answers both counts the ledger sent back', async () => {
      stubFetch(jsonResponse(anAcceptance({ accepted: 2, missing: 1 })));

      await expect(acceptExpenses([1, 2, 3])).resolves.toEqual({ accepted: 2, missing: 1 });
    });

    it('rejects with the message and status the ledger answered on a 503', async () => {
      stubFetch(jsonResponse({ message: 'ledger unavailable' }, 503));

      await expect(acceptExpenses([1])).rejects.toMatchObject({
        name: 'ApiError',
        status: 503,
        message: 'ledger unavailable',
      });
    });

    it('rejects with a 401 status so a page can tell an expiry from any other failure', async () => {
      stubFetch(new Response('', { status: 401 }));

      await expect(acceptExpenses([1])).rejects.toMatchObject({
        name: 'ApiError',
        status: 401,
      });
    });
  });

  describe('changing an expense category', () => {
    beforeEach(() => {
      document.cookie = 'XSRF-TOKEN=csrf-token-value; path=/';
    });

    afterEach(() => {
      document.cookie = 'XSRF-TOKEN=; path=/; max-age=0';
    });

    it('patches the entry by its id, replacing /categoryId, with the CSRF header and the cookies', async () => {
      const entry = anExpense({ id: 12 });
      const fetchMock = stubFetch(jsonResponse(anExpense({ id: 12, categoryId: 42 })));

      await changeCategory(entry, 42);

      expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/expenses/12');
      const [, init] = fetchMock.mock.calls[0]!;
      expect(init).toMatchObject({ method: 'PATCH', credentials: 'include' });
      expect(JSON.parse(String(init?.body))).toEqual([
        { op: 'replace', path: '/categoryId', value: 42 },
      ]);
      const headers = new Headers(init?.headers);
      expect(headers.get('Content-Type')).toBe('application/json-patch+json');
      expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token-value');
    });

    it('answers the entry as the ledger now holds it', async () => {
      const entry = anExpense({ id: 12, categoryId: 10 });
      const updated = anExpense({ id: 12, categoryId: 42 });
      stubFetch(jsonResponse(updated));

      await expect(changeCategory(entry, 42)).resolves.toEqual(updated);
    });

    it('rejects with the message and status the ledger answered on a 503', async () => {
      const entry = anExpense({ id: 12 });
      stubFetch(jsonResponse({ message: 'ledger unavailable' }, 503));

      await expect(changeCategory(entry, 42)).rejects.toMatchObject({
        name: 'ApiError',
        status: 503,
        message: 'ledger unavailable',
      });
    });

    it('rejects with a 401 status so a page can tell an expiry from an entry that moved on', async () => {
      const entry = anExpense({ id: 12 });
      stubFetch(new Response('', { status: 401 }));

      await expect(changeCategory(entry, 42)).rejects.toMatchObject({
        name: 'ApiError',
        status: 401,
      });
    });

    it('rejects with a 404 status so a page can tell an expiry from an entry that moved on', async () => {
      const entry = anExpense({ id: 12 });
      stubFetch(new Response('', { status: 404 }));

      await expect(changeCategory(entry, 42)).rejects.toMatchObject({
        name: 'ApiError',
        status: 404,
      });
    });
  });
});
