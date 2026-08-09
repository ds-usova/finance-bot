import { Moon, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Outlet } from 'react-router';
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
    <>
      <header className="flex items-center justify-between border-b border-border px-4 py-3">
        <h1 className="text-lg font-semibold">{t('shell.productName')}</h1>
        <div className="flex items-center gap-2">
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
      </header>
      <main>
        <Outlet />
      </main>
    </>
  );
}
