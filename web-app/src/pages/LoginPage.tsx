import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router';
import { ErrorBanner } from '../components/ErrorBanner';
import { Rosette } from '../components/Rosette';
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
    // The closed passbook: the cover fills the screen, and the sign-in is a slip tucked into it.
    <div className="relative mx-auto flex w-full max-w-3xl flex-1 flex-col justify-center gap-10 overflow-hidden px-5 py-10 sm:grid sm:grid-cols-[1fr_minmax(0,22rem)] sm:items-center sm:gap-12 sm:px-4">
      <Rosette className="pointer-events-none absolute -top-24 -right-28 h-80 w-80 text-foil/20 sm:top-auto sm:-right-6 sm:-bottom-40 sm:h-[28rem] sm:w-[28rem] sm:text-foil/15" />

      <p className="relative max-w-[18ch] text-[1.75rem] leading-[1.15] font-semibold tracking-[-0.015em] text-balance text-foil sm:text-[2.25rem]">
        {t('signIn.pitch')}
      </p>

      <div className="relative rounded-sm border border-cover-edge px-6 pt-6 pb-7 slip">
        <h2 className="text-xl font-bold tracking-[-0.01em]">{t('signIn.heading')}</h2>
        <p className="mt-1.5 text-sm text-pencil">{t('signIn.invitation')}</p>

        <div className="mt-6 mb-5 flex items-center gap-3">
          <span className="column-head shrink-0">{t('signIn.continueWith')}</span>
          <span
            aria-hidden="true"
            className="h-px flex-1 border-t border-dashed border-rule-strong"
          />
        </div>

        {/* Telegram serves the widget in an iframe of its own, so it cannot be styled from here — only
            given room and centred. */}
        <div className="flex min-h-12 justify-center">
          <TelegramLoginButton onAuth={handleAuth} />
        </div>

        {failed && (
          <div className="mt-5">
            <ErrorBanner message={t('signIn.refused')} />
          </div>
        )}
      </div>
    </div>
  );
}
