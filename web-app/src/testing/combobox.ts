import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/**
 * Opens a `combobox` trigger by its accessible name and clicks the option named, awaiting the primitive's
 * own open state — the option is only mounted once the listbox opens — rather than sleeping for it.
 */
export async function chooseOption(
  comboboxName: string | RegExp,
  optionName: string | RegExp,
): Promise<void> {
  await userEvent.click(screen.getByRole('combobox', { name: comboboxName }));
  await userEvent.click(await screen.findByRole('option', { name: optionName }));
}

/**
 * The same, for a list opened from a button rather than from a `combobox` — the searchable category list,
 * whose own `combobox` is the search field inside it and not the control that opens it.
 */
export async function chooseFromList(
  triggerName: string | RegExp,
  optionName: string | RegExp,
): Promise<void> {
  await userEvent.click(screen.getByRole('button', { name: triggerName }));
  await userEvent.click(await screen.findByRole('option', { name: optionName }));
}
