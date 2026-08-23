import type { components } from './generated/ledger-api';

export type Preferences = components['schemas']['Preferences'];
export type PreferencesUpdate = components['schemas']['PreferencesUpdate'];

// TODO: GET /api/v1/preferences through `request` from `./client`, answering the preferences as read. A
// non-2xx reaches the caller as an `ApiError` carrying the status and the problem body's message.
export async function readPreferences(): Promise<Preferences> {
  return { defaultCurrency: null };
}

// TODO: PUT /api/v1/preferences through `request` from `./client`, sending `{ defaultCurrency }` as the body
// and the CSRF token in the header, answering the preferences as they now stand. A non-2xx reaches the caller
// as an `ApiError` carrying the status and the problem body's message.
export async function replacePreferences(defaultCurrency: string): Promise<Preferences> {
  return { defaultCurrency };
}
