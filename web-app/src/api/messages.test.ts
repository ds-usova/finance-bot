import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, stubFetch } from '../testing/fetchStub';
import { aWrittenMessage } from '../testing/fixtures';
import { fileMessage, listReadingMessages, readMessage } from './messages';

describe('the written-message calls', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe('filing a message', () => {
    beforeEach(() => {
      document.cookie = 'XSRF-TOKEN=csrf-token-value; path=/';
    });

    afterEach(() => {
      document.cookie = 'XSRF-TOKEN=; path=/; max-age=0';
    });

    it('posts the text as JSON to the messages endpoint with the CSRF header, and answers the message the ledger sent', async () => {
      const filed = aWrittenMessage({ id: 42, text: 'coffee 3.40, taxi home 14' });
      const fetchMock = stubFetch(jsonResponse(filed, 202));

      await expect(fileMessage('coffee 3.40, taxi home 14')).resolves.toEqual(filed);

      expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/messages');
      const [, init] = fetchMock.mock.calls[0]!;
      expect(init).toMatchObject({ method: 'POST' });
      expect(JSON.parse(String(init?.body))).toEqual({ text: 'coffee 3.40, taxi home 14' });
      const headers = new Headers(init?.headers);
      expect(headers.get('Content-Type')).toContain('application/json');
      expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token-value');
    });

    it('rejects with an ApiError carrying 429 when the ledger answers a problem', async () => {
      stubFetch(jsonResponse({ message: 'too many messages' }, 429));

      await expect(fileMessage('coffee 3.40')).rejects.toMatchObject({
        name: 'ApiError',
        status: 429,
      });
    });
  });

  describe('reading a message', () => {
    it('requests the message by its id with GET, and answers it', async () => {
      const done = aWrittenMessage({
        id: 42,
        state: 'DONE',
        outcome: 'RECORDED',
        days: ['2026-08-01'],
      });
      const fetchMock = stubFetch(jsonResponse(done));

      await expect(readMessage(42)).resolves.toEqual(done);

      expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/messages/42');
      const [, init] = fetchMock.mock.calls[0]!;
      expect(init?.method ?? 'GET').toBe('GET');
    });

    it('rejects with an ApiError carrying 404 when the ledger does not know the message', async () => {
      stubFetch(new Response('', { status: 404 }));

      await expect(readMessage(42)).rejects.toMatchObject({
        name: 'ApiError',
        status: 404,
      });
    });
  });

  describe('listing the messages still being read', () => {
    it('requests the READING messages with GET, and answers the list', async () => {
      const reading = [aWrittenMessage({ id: 7 })];
      const fetchMock = stubFetch(jsonResponse(reading));

      await expect(listReadingMessages()).resolves.toEqual(reading);

      expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/messages?state=READING');
      const [, init] = fetchMock.mock.calls[0]!;
      expect(init?.method ?? 'GET').toBe('GET');
    });
  });
});
