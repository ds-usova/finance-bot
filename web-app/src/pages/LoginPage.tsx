import { useState } from 'react';
import { Navigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { ErrorBanner } from '../components/ErrorBanner';
import { TelegramLoginButton } from '../components/TelegramLoginButton';
import type { TelegramAuthPayload } from '../auth/types';
import { useAuth } from '../auth/useAuth';

export function LoginPage() {
  const { status, signIn } = useAuth();
  const { t } = useTranslation();
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
      <p>{t('signIn.invitation')}</p>
      <TelegramLoginButton onAuth={handleAuth} />
      {failed && <ErrorBanner message={t('signIn.refused')} />}
    </>
  );
}
