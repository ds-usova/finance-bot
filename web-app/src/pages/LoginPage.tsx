import { useState } from 'react';
import { Navigate } from 'react-router';
import { TelegramLoginButton } from '../components/TelegramLoginButton';
import type { TelegramAuthPayload } from '../auth/types';
import { useAuth } from '../auth/useAuth';

export function LoginPage() {
  const { status, signIn } = useAuth();
  const [failed, setFailed] = useState(false);

  if (status === 'authenticated') {
    return <Navigate to="/" replace />;
  }

  const handleAuth = (payload: TelegramAuthPayload) => {
    setFailed(false);
    signIn(payload).catch(() => setFailed(true));
  };

  return (
    <main>
      <h1>Finance Bot</h1>
      <p>Sign in with the Telegram account you use for the bot.</p>
      <TelegramLoginButton onAuth={handleAuth} />
      {failed && (
        <p className="error" role="alert">
          That sign-in was not accepted. Please try again.
        </p>
      )}
    </main>
  );
}
