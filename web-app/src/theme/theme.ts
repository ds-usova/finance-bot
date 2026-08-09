export type Theme = 'light' | 'dark';

/** The one key the stored choice lives under, shared with the blocking inline script in `index.html`. */
export const THEME_STORAGE_KEY = 'finance-bot-theme';

function systemPrefersDark(): boolean {
  return matchMedia('(prefers-color-scheme: dark)').matches;
}

// Answers the stored choice, or the system preference when nothing valid is stored — including when the
// store cannot be read at all.
export function resolveTheme(): Theme {
  try {
    const stored = localStorage.getItem(THEME_STORAGE_KEY);
    if (stored === 'dark' || stored === 'light') {
      return stored;
    }
  } catch {
    // Falls through to the system preference below.
  }
  return systemPrefersDark() ? 'dark' : 'light';
}

// Marks the document element with the given theme, so the stylesheet's dark variant turns on or off.
export function applyTheme(theme: Theme): void {
  document.documentElement.classList.toggle('dark', theme === 'dark');
}

// Records the choice under the module's one storage key, swallowing a refusal to write rather than throwing.
export function storeTheme(theme: Theme): void {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // A refused write leaves no trace, by this module's practice of logging nowhere in src/.
  }
}
