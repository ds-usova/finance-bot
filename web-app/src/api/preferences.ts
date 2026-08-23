import { request } from './client';
import type { components } from './generated/ledger-api';

export type Preferences = components['schemas']['Preferences'];
export type PreferencesUpdate = components['schemas']['PreferencesUpdate'];

const PREFERENCES_PATH = '/api/v1/preferences';

export async function readPreferences(): Promise<Preferences> {
  const preferences = await request<Preferences>(PREFERENCES_PATH);
  if (!preferences) {
    throw new Error('the preferences answered with no body');
  }
  return preferences;
}

export async function replacePreferences(defaultCurrency: string): Promise<Preferences> {
  const update: PreferencesUpdate = { defaultCurrency };
  const preferences = await request<Preferences>(PREFERENCES_PATH, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(update),
  });
  if (!preferences) {
    throw new Error('the preferences answered with no body');
  }
  return preferences;
}
