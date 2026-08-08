import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { anAuthContext } from '../testing/fixtures';
import { AuthContext } from './authContext';
import { RequireAuth } from './RequireAuth';
import type { AuthStatus } from './types';

function renderAt(status: AuthStatus) {
  const value = anAuthContext({
    status,
    session: status === 'authenticated' ? { externalId: '42' } : null,
  });

  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/login" element={<p>the sign-in page</p>} />
          <Route element={<RequireAuth />}>
            <Route path="/" element={<p>the protected page</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

describe('the authenticated-route guard', () => {
  it('shows neither page while the session is still being read', () => {
    renderAt('loading');

    expect(screen.queryByText('the sign-in page')).not.toBeInTheDocument();
    expect(screen.queryByText('the protected page')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('redirects to the sign-in page when nobody is signed in', () => {
    renderAt('anonymous');

    expect(screen.getByText('the sign-in page')).toBeInTheDocument();
  });

  it('renders the protected page when a session is open', () => {
    renderAt('authenticated');

    expect(screen.getByText('the protected page')).toBeInTheDocument();
  });
});
