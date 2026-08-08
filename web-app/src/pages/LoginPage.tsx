import { useState } from 'react';
import { Navigate } from 'react-router';
import { ErrorBanner } from '../components/ErrorBanner';
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
    <>
      <p>Sign in with the Telegram account you use for the bot.</p>
      <TelegramLoginButton onAuth={handleAuth} />
      {failed && <ErrorBanner message="That sign-in was not accepted. Please try again." />}
    </>
  );
}
