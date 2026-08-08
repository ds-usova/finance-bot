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
