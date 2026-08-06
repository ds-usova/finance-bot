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
    throw new ApiError(response.status, `${method} ${path} answered ${response.status}`);
  }
  if (response.status === 204) {
    return null;
  }
  return (await response.json()) as T;
}

export function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((entry) => entry.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}
