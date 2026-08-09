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
      <div className="grid w-full overflow-hidden rounded-2xl border border-border bg-card shadow-sm sm:grid-cols-2">
        {/* Hidden on a narrow screen: half a split screen on a phone is a stripe rather than a panel. The
            product's name is the header's and appears here only as the mark, never twice as words. */}
        <div className="hidden flex-col justify-between gap-10 bg-[linear-gradient(140deg,var(--panel-from),var(--panel-to))] p-8 text-panel-foreground sm:flex">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/15">
            <Wallet aria-hidden="true" className="h-5 w-5" />
          </span>
          <p className="text-lg leading-snug font-medium text-balance">{t('signIn.pitch')}</p>
        </div>

        <div className="p-8">
          <h2 className="text-xl font-semibold tracking-tight">{t('signIn.heading')}</h2>
          <p className="mt-2 text-sm text-muted-foreground">{t('signIn.invitation')}</p>

          <div className="my-7 flex items-center gap-3">
            <span aria-hidden="true" className="h-px flex-1 bg-border" />
            <span className="text-xs text-muted-foreground">{t('signIn.continueWith')}</span>
            <span aria-hidden="true" className="h-px flex-1 bg-border" />
          </div>

          {/* Telegram serves the widget in an iframe of its own, so it cannot be styled from here — only
              given room and centred. */}
          <div className="flex justify-center">
            <TelegramLoginButton onAuth={handleAuth} />
          </div>

          {failed && (
            <div className="mt-6">
              <ErrorBanner message={t('signIn.refused')} />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
