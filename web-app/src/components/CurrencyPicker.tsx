import { Check, ChevronsUpDown } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { en } from '../i18n/en';
import { Button } from './ui/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from './ui/command';
import { Popover, PopoverContent, PopoverTrigger } from './ui/popover';

export type CurrencyPickerProps = {
  /** The stored code, offered or not; `undefined` where nothing is stored. */
  currencyCode: string | undefined;
  /** The picked code — never `undefined`, since a choice cannot be cleared. */
  onChange: (code: string) => void;
  /** The field's label, read from the catalogue by the page. */
  label: string;
};

const collator = new Intl.Collator('en');

const currencies = Object.entries(en.currencies).sort(([, a], [, b]) => collator.compare(a, b));

function nameOf(code: string): string | undefined {
  return (en.currencies as Record<string, string>)[code];
}

export function CurrencyPicker({ currencyCode, onChange, label }: CurrencyPickerProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  const storedName = currencyCode ? nameOf(currencyCode) : undefined;
  const triggerText = storedName
    ? `${storedName} (${currencyCode})`
    : (currencyCode ?? t('settings.currencyUnset'));

  const choose = (code: string) => {
    onChange(code);
    setOpen(false);
  };

  return (
    <div className="form-line sm:grid-cols-[10rem_minmax(0,1fr)]">
      {/* The trigger names itself through `aria-label`; this is the printed margin label sighted readers see. */}
      <span aria-hidden="true" className="column-head">
        {label}
      </span>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button variant="ghost" size="field" aria-label={label}>
            <span className="truncate">{triggerText}</span>
            <ChevronsUpDown aria-hidden="true" className="h-4 w-4 shrink-0 text-pencil" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-(--radix-popover-trigger-width) min-w-64 p-0">
          <Command>
            <CommandInput placeholder={t('settings.searchCurrencies')} />
            <CommandList>
              <CommandEmpty>{t('settings.noCurrency')}</CommandEmpty>
              <CommandGroup>
                {currencies.map(([code, name]) => (
                  <CommandItem key={code} value={`${name} ${code}`} onSelect={() => choose(code)}>
                    <Check
                      aria-hidden="true"
                      className={`h-4 w-4 ${code === currencyCode ? 'opacity-100' : 'opacity-0'}`}
                    />
                    {name} ({code})
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
    </div>
  );
}
