import { vi } from 'vitest';

/**
 * Answers every call with the same `Response` instance. A body can only be consumed once, so a test
 * expecting more than one read builds its own stub.
 */
export function stubFetch(response: Response) {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
