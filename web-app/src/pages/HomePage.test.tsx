import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AuthContext, type AuthContextValue } from '../auth/authContext';
import { HomePage } from './HomePage';

function renderHome(signOut: AuthContextValue['signOut']) {
  const value: AuthContextValue = {
    status: 'authenticated',
    session: { externalId: '987654321' },
    signIn: async () => {},
    signOut,
  };

  return render(
    <AuthContext.Provider value={value}>
      <HomePage />
    </AuthContext.Provider>,
  );
}

describe('the home page', () => {
  it('names the signed-in account', () => {
    renderHome(async () => {});

    expect(screen.getByText('Signed in as 987654321')).toBeInTheDocument();
  });

  it('ends the session when the sign-out control is used', async () => {
    const signOut = vi.fn().mockResolvedValue(undefined);
    renderHome(signOut);

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(signOut).toHaveBeenCalledOnce();
  });
});
