import { createContext } from 'react';
import type { AuthState, TelegramAuthPayload } from './types';

export type AuthContextValue = AuthState & {
  signIn: (payload: TelegramAuthPayload) => Promise<void>;
  signOut: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);
