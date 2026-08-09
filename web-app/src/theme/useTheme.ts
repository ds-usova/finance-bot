import { useCallback, useState } from 'react';
import { applyTheme, resolveTheme, storeTheme, type Theme } from './theme';

export type UseThemeResult = {
  theme: Theme;
  toggleTheme: () => void;
};

function initialTheme(): Theme {
  const theme = resolveTheme();
  applyTheme(theme);
  return theme;
}

// Reports the theme in force, resolved before the first paint so no light frame shows before an effect runs,
// and a two-value flip that applies and stores the opposite theme.
export function useTheme(): UseThemeResult {
  const [theme, setTheme] = useState<Theme>(initialTheme);

  const toggleTheme = useCallback(() => {
    setTheme((current) => {
      const next: Theme = current === 'dark' ? 'light' : 'dark';
      applyTheme(next);
      storeTheme(next);
      return next;
    });
  }, []);

  return { theme, toggleTheme };
}
