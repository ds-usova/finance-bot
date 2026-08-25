const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

type MalformedResponseKey =
  | 'listing.acceptanceMalformed'
  | 'listing.categoryChangeMalformed'
  | 'settings.preferencesMalformed';

// The message is diagnostic only — a page shows the catalogue's wording for `key` instead.
export class MalformedResponseError extends Error {
  readonly key: MalformedResponseKey;

  constructor(key: MalformedResponseKey, message: string) {
    super(message);
    this.name = 'MalformedResponseError';
    this.key = key;
  }
}

/** Answers the body a call needs, or throws when the response carried none it can use. */
export function requireBody<T>(body: T | null, key: MalformedResponseKey, message: string): T {
  if (!body) {
    throw new MalformedResponseError(key, message);
  }
  return body;
}

/**
 * The only place the application calls `fetch`. Every request carries cookies, and every write carries the
 * CSRF token the ledger handed out, read back from the cookie it set.
 */
export async function request<T>(path: string, init: RequestInit = {}): Promise<T | null> {
  const method = init.method ?? 'GET';
  const headers = new Headers(init.headers);

  if (method !== 'GET') {
    const token = readCookie(CSRF_COOKIE);
    if (token) {
      headers.set(CSRF_HEADER, token);
    }
  }

  const response = await fetch(path, { ...init, method, headers, credentials: 'include' });

  if (!response.ok) {
    const reported = await readProblemMessage(response);
    throw new ApiError(
      response.status,
      reported ?? `${method} ${path} answered ${response.status}`,
    );
  }
  if (response.status === 204) {
    return null;
  }
  return (await response.json()) as T;
}

async function readProblemMessage(response: Response): Promise<string | null> {
  const body: unknown = await response.json().catch(() => null);
  if (typeof body !== 'object' || body === null || !('message' in body)) {
    return null;
  }
  return typeof body.message === 'string' ? body.message : null;
}

export function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((entry) => entry.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}
