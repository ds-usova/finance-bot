import type { Theme } from './theme';

export type UseThemeResult = {
  theme: Theme;
  toggleTheme: () => void;
};

// Reports the theme in force, resolved before the first paint so no light frame shows before an effect runs,
// and a two-value flip that applies and stores the opposite theme.
export function useTheme(): UseThemeResult {
  throw new Error('not implemented');
}
