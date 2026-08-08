import type { components } from '../api/generated/ledger-api';

export type Session = components['schemas']['Session'];

/** What the Login Widget hands the page. Its exact field set is Telegram's, and it may grow. */
export type TelegramAuthPayload = Record<string, string | number>;

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export type AuthState = {
  status: AuthStatus;
  session: Session | null;
};
