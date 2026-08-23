import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, stubFetch } from '../testing/fetchStub';
import { readPreferences, replacePreferences } from './preferences';

describe('the preferences calls', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe('reading preferences', () => {
    it('requests /api/v1/preferences with GET, and answers the code the body carried', async () => {
      const fetchMock = stubFetch(jsonResponse({ defaultCurrency: 'EUR' }));

      await expect(readPreferences()).resolves.toEqual({ defaultCurrency: 'EUR' });

      expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/preferences');
      const [, init] = fetchMock.mock.calls[0]!;
      expect(init?.method ?? 'GET').toBe('GET');
    });

    it('answers preferences carrying no currency rather than throwing', async () => {
      const fetchMock = stubFetch(jsonResponse({ defaultCurrency: null }));

      await expect(readPreferences()).resolves.toEqual({ defaultCurrency: null });

      expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/preferences');
    });

    it('rejects with an ApiError carrying 401', async () => {
      stubFetch(new Response('', { status: 401 }));

      await expect(readPreferences()).rejects.toMatchObject({
        name: 'ApiError',
        status: 401,
      });
    });
  });

  describe('replacing preferences', () => {
    beforeEach(() => {
      document.cookie = 'XSRF-TOKEN=csrf-token-value; path=/';
    });

    afterEach(() => {
      document.cookie = 'XSRF-TOKEN=; path=/; max-age=0';
    });

    it('sends PUT to /api/v1/preferences with defaultCurrency EUR in the body and the CSRF token in the header', async () => {
      const fetchMock = stubFetch(jsonResponse({ defaultCurrency: 'EUR' }));

      await replacePreferences('EUR');

      expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/preferences');
      const [, init] = fetchMock.mock.calls[0]!;
      expect(init).toMatchObject({ method: 'PUT', credentials: 'include' });
      expect(JSON.parse(String(init?.body))).toEqual({ defaultCurrency: 'EUR' });
      const headers = new Headers(init?.headers);
      expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token-value');
    });

    it('rejects with an ApiError carrying 400 and the message the body named', async () => {
      stubFetch(jsonResponse({ message: 'defaultCurrency must be an ISO 4217 code' }, 400));

      await expect(replacePreferences('XXX')).rejects.toMatchObject({
        name: 'ApiError',
        status: 400,
        message: 'defaultCurrency must be an ISO 4217 code',
      });
    });
  });
});
