import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AuthContext, type AuthContextValue } from '../auth/authContext';
import type { AuthStatus, TelegramAuthPayload } from '../auth/types';
import { anAuthContext } from '../testing/fixtures';
import { LoginPage } from './LoginPage';

function renderLogin(status: AuthStatus, signIn: AuthContextValue['signIn']) {
  const value = anAuthContext({
    status,
    session: status === 'authenticated' ? { externalId: '42' } : null,
    signIn,
  });

  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>the home page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

function fireWidgetCallback(payload: TelegramAuthPayload) {
  const onAuth = document.querySelector('script[data-telegram-login]')?.getAttribute('data-onauth');
  const callbackName = (onAuth ?? '').replace('(user)', '');
  const globals = window as unknown as Record<string, (payload: TelegramAuthPayload) => void>;
  globals[callbackName]?.(payload);
}

describe('the sign-in page', () => {
  it('offers the Telegram widget to an anonymous visitor', () => {
    renderLogin('anonymous', async () => {});

    expect(screen.getByRole('region', { name: 'Telegram sign-in' })).toBeInTheDocument();
  });

  it('leaves for the home page once a session is open', () => {
    renderLogin('authenticated', async () => {});

    expect(screen.getByText('the home page')).toBeInTheDocument();
  });

  it('hands the widget’s payload to the sign-in', async () => {
    const signIn = vi.fn().mockResolvedValue(undefined);
    renderLogin('anonymous', signIn);

    fireWidgetCallback({ id: 42 });

    await waitFor(() => expect(signIn).toHaveBeenCalledWith({ id: 42 }));
  });

  it('reports a refused sign-in instead of failing silently', async () => {
    const signIn = vi.fn().mockRejectedValue(new Error('refused'));
    renderLogin('anonymous', signIn);

    fireWidgetCallback({ id: 42 });

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
