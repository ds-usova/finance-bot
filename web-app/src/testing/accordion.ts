import { fireEvent, screen, waitFor } from '@testing-library/react';

/**
 * The day sections' headers, open or closed. Told apart from the other controls that carry `aria-expanded` —
 * the period popover's trigger above the listing, and a row's own category control — by the popup they open,
 * which an accordion header does not have.
 */
export function dayHeaders(): HTMLElement[] {
  return [...headersWithState(false), ...headersWithState(true)];
}

/** The day sections still closed — what `expandDays` clicks, and what a test asserts arrives collapsed. */
export function collapsedDayHeaders(): HTMLElement[] {
  return headersWithState(false);
}

/** The day sections currently open — how a test reaches a header it has already expanded. */
export function openDayHeaders(): HTMLElement[] {
  return headersWithState(true);
}

function headersWithState(expanded: boolean): HTMLElement[] {
  return screen
    .queryAllByRole('button', { expanded })
    .filter((header) => !header.hasAttribute('aria-haspopup'));
}

/**
 * Opens every day section currently on screen. Sections arrive collapsed, so a test about what an entry
 * shows has to open them first. Clicks with `fireEvent` rather than `userEvent`: several of these tests run
 * under fake timers, which `userEvent` needs configuring for and `fireEvent` does not.
 */
export function expandDays(): void {
  for (const header of collapsedDayHeaders()) {
    fireEvent.click(header);
  }
}

/** Waits for the listing to arrive, which is its first day header appearing. */
export async function listingArrives(): Promise<void> {
  await waitFor(() => {
    if (dayHeaders().length === 0) {
      throw new Error('no day section is on screen yet');
    }
  });
}
