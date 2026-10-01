import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, stubFetch } from '../testing/fetchStub';
import { ApiError, MalformedResponseError, request } from './client';

describe('the API client', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=csrf-token-value; path=/';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; path=/; max-age=0';
  });

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

    const rejection = request('/api/session');

    await expect(rejection).rejects.toMatchObject({
      name: 'ApiError',
      status: 401,
    });
    await expect(rejection).rejects.toThrow('GET /api/session answered 401');
  });

  it('reports the message the ledger sent, rather than a status code of its own', async () => {
    stubFetch(jsonResponse({ message: 'from must not be after to' }, 400));

    const rejection = request('/api/v1/expenses');

    await expect(rejection).rejects.toMatchObject({ name: 'ApiError', status: 400 });
    await expect(rejection).rejects.toThrow('from must not be after to');
  });

  it('reports its own wording when the body is not JSON', async () => {
    stubFetch(
      new Response('<html>Service Unavailable</html>', {
        status: 503,
        headers: { 'Content-Type': 'text/html' },
      }),
    );

    const rejection = request('/api/v1/expenses');

    await expect(rejection).rejects.toMatchObject({ name: 'ApiError', status: 503 });
    await expect(rejection).rejects.toThrow('GET /api/v1/expenses answered 503');
  });

  it('reports its own wording when the JSON body names no message', async () => {
    stubFetch(jsonResponse({ detail: 'nothing the page can read' }, 400));

    const rejection = request('/api/v1/expenses');

    await expect(rejection).rejects.toMatchObject({ name: 'ApiError', status: 400 });
    await expect(rejection).rejects.toThrow('GET /api/v1/expenses answered 400');
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

  it('is an Error, and reads back the key and message it was given', () => {
    const error = new MalformedResponseError(
      'listing.acceptanceMalformed',
      'the acceptance answered with no counts',
    );

    expect(error).toBeInstanceOf(Error);
    expect(error.key).toBe('listing.acceptanceMalformed');
    expect(error.message).toBe('the acceptance answered with no counts');
  });
});
