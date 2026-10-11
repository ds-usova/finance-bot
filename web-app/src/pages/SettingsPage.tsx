import { Check } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { readPreferences, replacePreferences } from '../api/preferences';
import { useAuth } from '../auth/useAuth';
import { CurrencyPicker } from '../components/CurrencyPicker';
import { ErrorBanner } from '../components/ErrorBanner';
import { Sheet } from '../components/Sheet';
import { Button } from '../components/ui/button';
import { refusalText } from './errorText';

export function SettingsPage() {
  const { t } = useTranslation();
  const { sessionExpired } = useAuth();
  const [storedCurrency, setStoredCurrency] = useState<string | undefined>();
  const [pickedCurrency, setPickedCurrency] = useState<string | undefined>();
  const [readFailure, setReadFailure] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved'>('idle');
  const [saveFailure, setSaveFailure] = useState<string | null>(null);

  const report = useCallback(
    (error: unknown, setFailure: (message: string | null) => void) => {
      if (error instanceof ApiError && error.status === 401) {
        sessionExpired();
        return;
      }
      setFailure(refusalText(error, t, 'settings.refused'));
    },
    [sessionExpired, t],
  );

  useEffect(() => {
    let cancelled = false;

    readPreferences()
      .then((preferences) => {
        if (!cancelled) {
          setStoredCurrency(preferences.defaultCurrency ?? undefined);
          setReadFailure(null);
          setLoaded(true);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          report(error, setReadFailure);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [report]);

  const onPick = (code: string) => {
    setPickedCurrency(code);
    setSaveState('idle');
    setSaveFailure(null);
  };

  const onSave = () => {
    if (saveState === 'saving' || pickedCurrency === undefined) {
      return;
    }
    setSaveState('saving');
    setSaveFailure(null);

    replacePreferences(pickedCurrency)
      .then(() => {
        setSaveState('saved');
      })
      .catch((error: unknown) => {
        setSaveState('idle');
        report(error, setSaveFailure);
      });
  };

  if (readFailure) {
    return (
      <Sheet>
        <ErrorBanner message={readFailure} />
      </Sheet>
    );
  }

  if (!loaded) {
    return <Sheet>{null}</Sheet>;
  }

  const currencyOnScreen = pickedCurrency ?? storedCurrency;
  const offerSave = pickedCurrency !== undefined && pickedCurrency !== storedCurrency;

  return (
    <Sheet>
      <h2 className="border-b-2 border-double border-rule-strong pb-2 text-[1.375rem] font-bold tracking-[-0.01em]">
        {t('settings.heading')}
      </h2>
      <CurrencyPicker
        currencyCode={currencyOnScreen}
        onChange={onPick}
        label={t('settings.defaultCurrency')}
      />
      {(offerSave || saveState === 'saved') && (
        <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-center">
          {offerSave && (
            <Button onClick={onSave} disabled={saveState === 'saving'} className="sm:min-w-28">
              {t('settings.save')}
            </Button>
          )}
          {saveState === 'saved' && (
            <span className="inline-flex items-center gap-1.5 text-sm font-medium text-ink">
              <Check aria-hidden="true" className="h-4 w-4 text-cover dark:text-foil" />
              {t('settings.saved')}
            </span>
          )}
          {saveFailure && <ErrorBanner message={saveFailure} />}
        </div>
      )}
    </Sheet>
  );
}
