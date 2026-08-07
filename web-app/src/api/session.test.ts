import { afterEach, describe, expect, it, vi } from 'vitest';
import type { components } from './generated/ledger-api';
import { createSession, deleteSession, readSession } from './session';

describe('the session calls', () => {
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

  it('opens a session by posting the payload as JSON', async () => {
    const fetchMock = stubFetch(jsonResponse({ externalId: '42' }));

    const session = await createSession({ id: '42', hash: 'abc' });

    expect(session).toEqual({ externalId: '42' });
    const [path, init] = fetchMock.mock.calls[0] ?? [];
    expect(path).toBe('/api/v1/session');
    expect(init?.method).toBe('POST');
    expect(init?.body).toBe(JSON.stringify({ id: '42', hash: 'abc' }));
  });

  it('refuses a sign-in that answered with no session, rather than reporting one', async () => {
    stubFetch(new Response(null, { status: 204 }));

    await expect(createSession({ id: '42' })).rejects.toThrow(/no session/);
  });

  it('reads the session with a plain GET', async () => {
    const fetchMock = stubFetch(jsonResponse({ externalId: '987654321' }));

    await expect(readSession()).resolves.toEqual({ externalId: '987654321' });
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('GET');
  });

  it('reads the current session from the versioned path', async () => {
    const fetchMock = stubFetch(jsonResponse({ externalId: '987654321' }));

    const session: components['schemas']['Session'] | null = await readSession();

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/session');
    expect(session).toEqual({ externalId: '987654321' });
  });

  it('ends the session with a DELETE', async () => {
    const fetchMock = stubFetch(new Response(null, { status: 204 }));

    await deleteSession();

    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('DELETE');
  });
});
