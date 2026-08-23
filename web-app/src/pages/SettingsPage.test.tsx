import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { readPreferences, replacePreferences } from '../api/preferences';
import { AuthContext, type AuthContextValue } from '../auth/authContext';
import { en } from '../i18n/en';
import { substituteCatalogue } from '../testing/catalogue';
import { chooseFromList } from '../testing/combobox';
import { anAuthContext } from '../testing/fixtures';
import { inFlight } from '../testing/inFlight';
import { SettingsPage } from './SettingsPage';

vi.mock('../api/preferences', () => ({
  readPreferences: vi.fn(),
  replacePreferences: vi.fn(),
}));

const readPreferencesMock = vi.mocked(readPreferences);
const replacePreferencesMock = vi.mocked(replacePreferences);

function renderPage(context: Partial<AuthContextValue> = {}) {
  const value = anAuthContext(context);

  return render(
    <AuthContext.Provider value={value}>
      <SettingsPage />
    </AuthContext.Provider>,
  );
}

/** Opens the currency list from the field's own trigger and picks the option named. */
async function pickCurrency(optionName: string | RegExp) {
  await chooseFromList(en.settings.defaultCurrency, optionName);
}

describe('the settings page', () => {
  afterEach(() => {
    vi.resetAllMocks();
  });

  it('stands the picker on Euro (EUR) and offers no save control once the read answers a stored currency', async () => {
    readPreferencesMock.mockResolvedValue({ defaultCurrency: 'EUR' });

    renderPage();

    expect(await screen.findByText('Euro (EUR)')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: en.settings.save })).not.toBeInTheDocument();
  });

  it('stands the picker on its unset wording and offers no save control once the read answers no stored currency', async () => {
    readPreferencesMock.mockResolvedValue({ defaultCurrency: null });

    renderPage();

    await waitFor(() => expect(readPreferencesMock).toHaveBeenCalledOnce());
    expect(await screen.findByText(en.settings.currencyUnset)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: en.settings.save })).not.toBeInTheDocument();
  });

  it('reads the trigger as DEM at first, offering the save control only once a different currency is picked', async () => {
    readPreferencesMock.mockResolvedValue({ defaultCurrency: 'DEM' });

    renderPage();

    expect(await screen.findByText('DEM')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: en.settings.save })).not.toBeInTheDocument();

    await pickCurrency('Euro (EUR)');

    expect(screen.getByRole('button', { name: en.settings.save })).toBeInTheDocument();
  });

  it('shows the failure at the top of the page and renders no picker when the read fails for a reason other than a refused session', async () => {
    readPreferencesMock.mockRejectedValue(
      new ApiError(503, 'the ledger is temporarily unavailable'),
    );

    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'the ledger is temporarily unavailable',
    );
    expect(
      screen.queryByRole('button', { name: en.settings.defaultCurrency }),
    ).not.toBeInTheDocument();
  });

  it('reports an expired session and leaves no failure banner behind when the read is refused with 401', async () => {
    readPreferencesMock.mockRejectedValue(new ApiError(401, 'no session'));
    const sessionExpired = vi.fn();

    renderPage({ sessionExpired });

    await waitFor(() => expect(sessionExpired).toHaveBeenCalledOnce());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('sends the picked code through replacePreferences and reports the save once the save control is used', async () => {
    readPreferencesMock.mockResolvedValue({ defaultCurrency: 'EUR' });
    replacePreferencesMock.mockResolvedValue({ defaultCurrency: 'USD' });
    renderPage();
    await screen.findByText('Euro (EUR)');
    await pickCurrency('US Dollar (USD)');

    await userEvent.click(screen.getByRole('button', { name: en.settings.save }));

    await waitFor(() => expect(replacePreferencesMock).toHaveBeenCalledWith('USD'));
    expect(await screen.findByText(en.settings.saved)).toBeInTheDocument();
  });

  it('disables the save control while a save is out, so a second use sends nothing further', async () => {
    readPreferencesMock.mockResolvedValue({ defaultCurrency: 'EUR' });
    const outstanding = inFlight<{ defaultCurrency: string | null }>();
    replacePreferencesMock.mockReturnValueOnce(outstanding.promise);
    renderPage();
    await screen.findByText('Euro (EUR)');
    await pickCurrency('US Dollar (USD)');

    const save = screen.getByRole('button', { name: en.settings.save });
    await userEvent.click(save);
    await userEvent.click(save);

    expect(replacePreferencesMock).toHaveBeenCalledOnce();
  });

  it('drops the saved confirmation and offers the save control again once a different currency is picked', async () => {
    readPreferencesMock.mockResolvedValue({ defaultCurrency: 'EUR' });
    replacePreferencesMock.mockResolvedValue({ defaultCurrency: 'USD' });
    renderPage();
    await screen.findByText('Euro (EUR)');
    await pickCurrency('US Dollar (USD)');
    await userEvent.click(screen.getByRole('button', { name: en.settings.save }));
    await screen.findByText(en.settings.saved);

    await pickCurrency('British Pound (GBP)');

    expect(screen.queryByText(en.settings.saved)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: en.settings.save })).toBeInTheDocument();
  });

  it('shows the refusal beside the save control and leaves the picker standing on what was picked when the write is refused for a reason other than a session', async () => {
    readPreferencesMock.mockResolvedValue({ defaultCurrency: 'EUR' });
    replacePreferencesMock.mockRejectedValue(
      new ApiError(503, 'the ledger is temporarily unavailable'),
    );
    renderPage();
    await screen.findByText('Euro (EUR)');
    await pickCurrency('US Dollar (USD)');

    await userEvent.click(screen.getByRole('button', { name: en.settings.save }));

    expect(await screen.findByText('the ledger is temporarily unavailable')).toBeInTheDocument();
    expect(screen.getByText('US Dollar (USD)')).toBeInTheDocument();
  });

  it('reports an expired session when the write is refused with 401', async () => {
    readPreferencesMock.mockResolvedValue({ defaultCurrency: 'EUR' });
    replacePreferencesMock.mockRejectedValue(new ApiError(401, 'no session'));
    const sessionExpired = vi.fn();
    renderPage({ sessionExpired });
    await screen.findByText('Euro (EUR)');
    await pickCurrency('US Dollar (USD)');

    await userEvent.click(screen.getByRole('button', { name: en.settings.save }));

    await waitFor(() => expect(sessionExpired).toHaveBeenCalledOnce());
  });

  it('reads the heading, the field label and the save control from the catalogue', async () => {
    readPreferencesMock.mockResolvedValue({ defaultCurrency: 'EUR' });
    substituteCatalogue();
    renderPage();
    await screen.findByText('Euro (EUR)');
    await chooseFromList(`‹${en.settings.defaultCurrency}›`, /USD/);

    expect(screen.getByRole('heading', { name: `‹${en.settings.heading}›` })).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: `‹${en.settings.defaultCurrency}›` }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: `‹${en.settings.save}›` })).toBeInTheDocument();
  });
});
