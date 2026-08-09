import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthContext, type AuthContextValue } from '../auth/authContext';
import { en } from '../i18n/en';
import { substituteCatalogue } from '../testing/catalogue';
import { anAuthContext } from '../testing/fixtures';
import { THEME_STORAGE_KEY } from '../theme/theme';
import { AppShell } from './AppShell';

function renderShell(
  context: Partial<AuthContextValue> = {},
  path = '/',
  content: ReactNode = <p>routed content</p>,
) {
  const value = anAuthContext(context);

  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<AppShell />}>
            <Route path={path} element={content} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

describe('the shell', () => {
  afterEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
  });

  it('shows the product name and the theme control but no sign-out control for an anonymous visitor', () => {
    renderShell({ status: 'anonymous', session: null }, '/login');

    expect(
      screen.getByRole('heading', { level: 1, name: en.shell.productName }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: en.shell.themeToggle })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: en.shell.signOut })).not.toBeInTheDocument();
  });

  it('shows the product name, the theme control and a sign-out control for a signed-in person', () => {
    renderShell({ status: 'authenticated', session: { externalId: '42' } });

    expect(
      screen.getByRole('heading', { level: 1, name: en.shell.productName }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: en.shell.themeToggle })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: en.shell.signOut })).toBeInTheDocument();
  });

  it('calls signOut on the context when the sign-out control is used', async () => {
    const signOut = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderShell({ status: 'authenticated', session: { externalId: '42' }, signOut });

    await user.click(screen.getByRole('button', { name: en.shell.signOut }));

    expect(signOut).toHaveBeenCalledOnce();
  });

  it('marks the document light and stores light when the theme control is used from dark', async () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');
    const user = userEvent.setup();
    renderShell();

    await user.click(screen.getByRole('button', { name: en.shell.themeToggle }));

    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  it('renders the theme control as a button carrying an accessible name', () => {
    renderShell();

    expect(screen.getByRole('button', { name: en.shell.themeToggle })).toBeInTheDocument();
  });

  it("renders the wrapped route's own content beneath the header", () => {
    renderShell({}, '/', <p>the routed content</p>);

    const heading = screen.getByRole('heading', { name: en.shell.productName });
    const content = screen.getByText('the routed content');

    expect(
      heading.compareDocumentPosition(content) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("shows the catalogue's text for the product name, the theme control and the sign-out control", () => {
    substituteCatalogue();

    renderShell({ status: 'authenticated', session: { externalId: '42' } });

    expect(screen.getByRole('heading', { name: `‹${en.shell.productName}›` })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: `‹${en.shell.themeToggle}›` })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: `‹${en.shell.signOut}›` })).toBeInTheDocument();
  });
});
