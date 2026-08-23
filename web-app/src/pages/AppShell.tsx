import { Moon, Settings, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/useAuth';
import { Button } from '../components/ui/button';
import { useTheme } from '../theme/useTheme';

// The layout element every route renders inside. Reads the session from the context to decide whether the
// sign-out control is shown, and carries the theme control.
export function AppShell() {
  const { t } = useTranslation();
  const { status, signOut } = useAuth();
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-10 border-b border-border bg-surface/80 backdrop-blur">
        <div className="mx-auto flex h-14 w-full max-w-5xl items-center justify-between gap-4 px-4 sm:px-6">
          <h1 className="text-base font-semibold tracking-tight">
            <Link
              to="/"
              className="rounded-md outline-none transition-colors hover:bg-muted focus-visible:ring-1 focus-visible:ring-accent"
            >
              {t('shell.productName')}
            </Link>
          </h1>
          <div className="flex items-center gap-1">
            {status === 'authenticated' && (
              <Button variant="ghost" size="icon" aria-label={t('shell.settings')} asChild>
                <NavLink to="/settings">
                  <Settings aria-hidden="true" />
                </NavLink>
              </Button>
            )}
            <Button
              variant="ghost"
              size="icon"
              aria-label={t('shell.themeToggle')}
              onClick={toggleTheme}
            >
              {theme === 'dark' ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
            </Button>
            {status === 'authenticated' && (
              <Button variant="ghost" onClick={() => void signOut()}>
                {t('shell.signOut')}
              </Button>
            )}
          </div>
        </div>
      </header>
      {/* `flex-1` so a route that wants the rest of the viewport — the sign-in card — can centre in it. */}
      <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col px-4 py-8 sm:px-6 sm:py-10">
        <Outlet />
      </main>
    </div>
  );
}
