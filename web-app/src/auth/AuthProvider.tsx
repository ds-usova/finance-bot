import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { createSession, deleteSession, readSession } from '../api/session';
import { AuthContext } from './authContext';
import type { AuthState, Session, TelegramAuthPayload } from './types';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading', session: null });

  useEffect(() => {
    let cancelled = false;

    function settle(session: Session | null) {
      if (!cancelled) {
        setState(
          session ? { status: 'authenticated', session } : { status: 'anonymous', session: null },
        );
      }
    }

    readSession()
      .then(settle)
      .catch(() => settle(null));

    return () => {
      cancelled = true;
    };
  }, []);

  const signIn = useCallback(async (payload: TelegramAuthPayload) => {
    const session = await createSession(payload);
    setState({ status: 'authenticated', session });
  }, []);

  const signOut = useCallback(async () => {
    await deleteSession();
    setState({ status: 'anonymous', session: null });
  }, []);

  const value = useMemo(() => ({ ...state, signIn, signOut }), [state, signIn, signOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
