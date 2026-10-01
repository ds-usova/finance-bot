import type { useTranslation } from 'react-i18next';
import { ApiError, MalformedResponseError } from '../api/client';

/** A caught call's own message, translated where the catalogue owns the wording: a `MalformedResponseError`'s
 * key, or `refusedKey` for anything else but a genuine network failure, which keeps its own words. The 401
 * case is each caller's own, since only the caller holds the session context to act on it. */
export function refusalText(
  error: unknown,
  t: ReturnType<typeof useTranslation>['t'],
  refusedKey: 'settings.refused' | 'listing.refused' | 'listing.categoryChangeRefused',
): string {
  if (error instanceof MalformedResponseError) {
    return t(error.key);
  }
  return error instanceof ApiError || !(error instanceof Error) ? t(refusedKey) : error.message;
}
