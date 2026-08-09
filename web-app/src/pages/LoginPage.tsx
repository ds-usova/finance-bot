import { Wallet } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router';
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
    <div className="flex flex-1 items-center justify-center py-6">
      <div className="w-full max-w-sm rounded-2xl border border-border bg-card p-8 shadow-sm">
        <div className="flex flex-col items-center gap-2 text-center">
          <span className="mb-2 flex h-12 w-12 items-center justify-center rounded-2xl bg-accent/10 text-accent">
            <Wallet aria-hidden="true" className="h-6 w-6" />
          </span>
          <h2 className="text-xl font-semibold tracking-tight">{t('signIn.heading')}</h2>
          <p className="text-sm text-muted-foreground">{t('signIn.invitation')}</p>
        </div>
        {/* Telegram serves the widget in an iframe of its own, so it cannot be styled from here — only
            given room and centred. */}
        <div className="mt-7 flex justify-center">
          <TelegramLoginButton onAuth={handleAuth} />
        </div>
        {failed && (
          <div className="mt-6">
            <ErrorBanner message={t('signIn.refused')} />
          </div>
        )}
      </div>
    </div>
  );
}
