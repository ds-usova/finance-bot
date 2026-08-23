import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { readPreferences, replacePreferences } from '../api/preferences';
import { useAuth } from '../auth/useAuth';
import { CurrencyPicker } from '../components/CurrencyPicker';
import { ErrorBanner } from '../components/ErrorBanner';
import { Button } from '../components/ui/button';

export function SettingsPage() {
  const { t } = useTranslation();
  const { sessionExpired } = useAuth();
  const [storedCurrency, setStoredCurrency] = useState<string | undefined>();
  const [pickedCurrency, setPickedCurrency] = useState<string | undefined>();
  const [readFailure, setReadFailure] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveFailure, setSaveFailure] = useState<string | null>(null);

  const report = useCallback(
    (error: unknown, setFailure: (message: string | null) => void) => {
      if (error instanceof ApiError && error.status === 401) {
        sessionExpired();
        return;
      }
      setFailure(error instanceof Error ? error.message : 'That call was not answered.');
    },
    [sessionExpired],
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
    setSaved(false);
    setSaveFailure(null);
  };

  const onSave = () => {
    if (saving || pickedCurrency === undefined) {
      return;
    }
    setSaving(true);
    setSaveFailure(null);

    replacePreferences(pickedCurrency)
      .then(() => {
        setSaving(false);
        setSaved(true);
      })
      .catch((error: unknown) => {
        setSaving(false);
        report(error, setSaveFailure);
      });
  };

  if (readFailure) {
    return <ErrorBanner message={readFailure} />;
  }

  if (!loaded) {
    return null;
  }

  const currencyOnScreen = pickedCurrency ?? storedCurrency;
  const offerSave = pickedCurrency !== undefined && pickedCurrency !== storedCurrency;

  return (
    <div className="flex flex-col gap-6">
      <h2 className="text-xl font-semibold tracking-tight">{t('settings.heading')}</h2>
      <CurrencyPicker
        currencyCode={currencyOnScreen}
        onChange={onPick}
        label={t('settings.defaultCurrency')}
      />
      {offerSave && (
        <div className="flex items-center gap-3">
          <Button onClick={onSave} disabled={saving}>
            {t('settings.save')}
          </Button>
          {saved && <span>{t('settings.saved')}</span>}
          {saveFailure && <ErrorBanner message={saveFailure} />}
        </div>
      )}
    </div>
  );
}
