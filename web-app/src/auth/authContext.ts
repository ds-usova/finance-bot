import { createContext } from 'react';
import type { AuthState, TelegramAuthPayload } from './types';

export type AuthContextValue = AuthState & {
  signIn: (payload: TelegramAuthPayload) => Promise<void>;
  signOut: () => Promise<void>;
  /** The ledger refused a read the session should have carried. Distinct from `signOut`, which ends a session that is still there. */
  sessionExpired: () => void;
};

export const AuthContext = createContext<AuthContextValue | null>(null);
