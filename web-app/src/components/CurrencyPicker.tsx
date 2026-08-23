import { ChevronsUpDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from './ui/button';

export type CurrencyPickerProps = {
  /** The stored code, offered or not; `undefined` where nothing is stored. */
  currencyCode: string | undefined;
  /** The picked code — never `undefined`, since a choice cannot be cleared. */
  onChange: (code: string) => void;
  /** The field's label, read from the catalogue by the page. */
  label: string;
};

// TODO: opens on the trigger, offering a searchable list of the catalogue's currencies to pick from.
export function CurrencyPicker({
  currencyCode: _currencyCode,
  onChange: _onChange,
  label,
}: CurrencyPickerProps) {
  const { t } = useTranslation();

  return (
    <Button variant="outline" size="field" aria-label={label} className="font-normal">
      <span className="truncate">{t('settings.currencyUnset')}</span>
      <ChevronsUpDown aria-hidden="true" className="h-4 w-4 shrink-0 opacity-50" />
    </Button>
  );
}
