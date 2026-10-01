import { LogOut, Moon, Settings, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/useAuth';
import { Button } from '../components/ui/button';
import { useTheme } from '../theme/useTheme';

// The layout element every route renders inside: the passbook's cover. Reads the session from the context to
// decide whether settings and sign-out are shown, and carries the theme control.
export function AppShell() {
  const { t } = useTranslation();
  const { status, signOut } = useAuth();
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="cloth flex min-h-dvh flex-col">
      <header className="cloth sticky top-0 z-30 border-b border-cover-edge">
        {/* As wide as the page below it plus the cover showing either side, so the title and the controls
            stand over the page's own edges. */}
        <div className="mx-auto flex h-14 w-full max-w-[75rem] items-center justify-between gap-3 px-3 sm:px-6">
          <h1 className="min-w-0">
            {/* The foil title, set plainly on the cloth. */}
            <Link
              to="/"
              className="-ml-1.5 inline-flex items-center rounded-sm px-1.5 py-1 font-sans text-[1.0625rem] font-bold tracking-[-0.005em] text-foil transition-colors hover:bg-black/15 focus-visible:ring-2 focus-visible:ring-foil focus-visible:ring-offset-2 focus-visible:ring-offset-cover"
            >
              {t('shell.productName')}
            </Link>
          </h1>
          <div className="flex items-center">
            {status === 'authenticated' && (
              <Button
                variant="cover"
                size="icon"
                aria-label={t('shell.settings')}
                className="aria-[current=page]:bg-black/20 aria-[current=page]:text-foil"
                asChild
              >
                <NavLink to="/settings">
                  <Settings aria-hidden="true" className="h-[18px] w-[18px]" />
                </NavLink>
              </Button>
            )}
            <Button
              variant="cover"
              size="icon"
              aria-label={t('shell.themeToggle')}
              onClick={toggleTheme}
            >
              {theme === 'dark' ? (
                <Sun aria-hidden="true" className="h-[18px] w-[18px]" />
              ) : (
                <Moon aria-hidden="true" className="h-[18px] w-[18px]" />
              )}
            </Button>
            {status === 'authenticated' && (
              <>
                <span aria-hidden="true" className="mx-1.5 h-5 w-px bg-foil/25" />
                <Button variant="cover" className="px-2.5" onClick={() => void signOut()}>
                  <LogOut aria-hidden="true" className="h-4 w-4 sm:hidden" />
                  <span className="sr-only sm:not-sr-only">{t('shell.signOut')}</span>
                </Button>
              </>
            )}
          </div>
        </div>
      </header>
      {/* `flex-1` so a route that wants the rest of the viewport — the sign-in cover — can fill it. */}
      <main className="flex w-full flex-1 flex-col sm:px-6 sm:py-8">
        <Outlet />
      </main>
    </div>
  );
}
