import { afterEach, describe, expect, it, vi } from 'vitest';
import { aCategory, aGrouping, anExpense, anExpensePage } from '../testing/fixtures';
import { listCategories, listExpenses, listGroupings } from './expenses';

describe('the expense calls', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function stubFetch(response: Response) {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response);
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  function jsonResponse(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  }

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
});
