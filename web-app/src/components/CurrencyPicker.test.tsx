import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { en } from '../i18n/en';
import { CurrencyPicker, type CurrencyPickerProps } from './CurrencyPicker';

// A catalogue of the picker's own, so these tests keep meaning whichever codes `en.currencies` holds once the
// full ISO 4217 transcription lands — declared out of alphabetical order, and with a name beginning with a
// diacritic, so the ordering test below proves the picker sorts rather than merely echoes declaration order.
vi.mock('../i18n/en', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../i18n/en')>();
  return {
    en: {
      ...actual.en,
      currencies: {
        ZZZ: 'Zloty',
        EUR: 'Euro',
        BBB: 'Banana Bill',
        USD: 'US Dollar',
        EEE: 'Éclair Note',
      },
    },
  };
});

/** The picker under test, with the props a case says nothing about left at sensible defaults. */
function renderPicker(props: Partial<CurrencyPickerProps> = {}) {
  const onChange = vi.fn();
  const user = userEvent.setup();

  render(<CurrencyPicker currencyCode={undefined} onChange={onChange} label="Currency" {...props} />);

  return { onChange, user };
}

async function open(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button'));
}

describe('the trigger', () => {
  it('reads the stored currency’s name and code when the catalogue names it', () => {
    renderPicker({ currencyCode: 'EUR' });

    expect(screen.getByRole('button')).toHaveTextContent('Euro (EUR)');
  });

  it('reads the catalogue’s unset wording when no code is stored', () => {
    renderPicker({ currencyCode: undefined });

    expect(screen.getByRole('button')).toHaveTextContent(en.settings.currencyUnset);
  });

  it('reads the stored code alone when the catalogue does not name it', () => {
    renderPicker({ currencyCode: 'DEM' });

    expect(screen.getByRole('button')).toHaveTextContent('DEM');
  });
});

describe('the list', () => {
  it('reads each entry as its name then its code, in name order — a diacritic-led name standing among the plain-letter names', async () => {
    const { user } = renderPicker();
    await open(user);

    const options = await screen.findAllByRole('option');

    expect(options.map((option) => option.textContent)).toEqual([
      'Banana Bill (BBB)',
      'Éclair Note (EEE)',
      'Euro (EUR)',
      'US Dollar (USD)',
      'Zloty (ZZZ)',
    ]);
  });

  it('narrows to what a search names, keeping fewer entries than stood before it', async () => {
    const { user } = renderPicker();
    await open(user);
    const before = (await screen.findAllByRole('option')).length;

    await user.type(screen.getByPlaceholderText(en.settings.searchCurrencies), 'usd');

    const after = screen.getAllByRole('option');
    expect(after.map((option) => option.textContent)).toContain('US Dollar (USD)');
    expect(after.length).toBeLessThan(before);
  });

  it('shows the catalogue’s empty-list wording when a search matches nothing', async () => {
    const { user } = renderPicker();
    await open(user);

    await user.type(screen.getByPlaceholderText(en.settings.searchCurrencies), 'qqqqq');

    expect(screen.getByText(en.settings.noCurrency)).toBeInTheDocument();
  });

  it('calls onChange with the chosen entry’s code alone, and closes the list', async () => {
    const { user, onChange } = renderPicker();
    await open(user);

    await user.click(await screen.findByRole('option', { name: 'Euro (EUR)' }));

    expect(onChange).toHaveBeenCalledWith('EUR');
    expect(screen.queryByRole('option')).not.toBeInTheDocument();
  });
});
