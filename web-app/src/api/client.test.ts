import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request } from './client';

describe('the API client', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=csrf-token-value; path=/';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; path=/; max-age=0';
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

  it('sends cookies on every request, so the session cookie reaches the ledger', async () => {
    const fetchMock = stubFetch(jsonResponse({ externalId: '42' }));

    await request('/api/session');

    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ credentials: 'include' });
  });

  it('sends the CSRF token on a write', async () => {
    const fetchMock = stubFetch(jsonResponse({ externalId: '42' }));

    await request('/api/session', { method: 'POST' });

    const headers = new Headers(fetchMock.mock.calls[0]?.[1]?.headers);
    expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token-value');
  });

  it('sends no CSRF token on a read, which needs none', async () => {
    const fetchMock = stubFetch(jsonResponse({ externalId: '42' }));

    await request('/api/session');

    const headers = new Headers(fetchMock.mock.calls[0]?.[1]?.headers);
    expect(headers.get('X-XSRF-TOKEN')).toBeNull();
  });

  it('surfaces a refusal as an ApiError carrying the status', async () => {
    stubFetch(new Response('', { status: 401 }));

    await expect(request('/api/session')).rejects.toMatchObject({
      name: 'ApiError',
      status: 401,
    });
  });

  it('answers with null for a no-content response rather than failing to parse it', async () => {
    stubFetch(new Response(null, { status: 204 }));

    await expect(request('/api/session', { method: 'DELETE' })).resolves.toBeNull();
  });

  it('parses a JSON body into the answered value', async () => {
    stubFetch(jsonResponse({ externalId: '987654321' }));

    await expect(request('/api/session')).resolves.toEqual({ externalId: '987654321' });
  });

  it('is an Error, so an unhandled ApiError still reports a message', () => {
    expect(new ApiError(503, 'unavailable')).toBeInstanceOf(Error);
  });
});
