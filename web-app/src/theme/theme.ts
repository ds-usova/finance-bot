export type Theme = 'light' | 'dark';

/** The one key the stored choice lives under, shared with the blocking inline script in `index.html`. */
export const THEME_STORAGE_KEY = 'finance-bot-theme';

// Answers the stored choice, or the system preference when nothing valid is stored — including when the
// store cannot be read at all.
export function resolveTheme(): Theme {
  throw new Error('not implemented');
}

// Marks the document element with the given theme, so the stylesheet's dark variant turns on or off.
export function applyTheme(theme: Theme): void {
  throw new Error(`not implemented: ${theme}`);
}

// Records the choice under the module's one storage key, swallowing a refusal to write rather than throwing.
export function storeTheme(theme: Theme): void {
  throw new Error(`not implemented: ${theme}`);
}
