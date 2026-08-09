import type { en } from './en';

// Derives the translation key union from the English catalogue itself, so a key that does not exist in `en`
// fails `npm run typecheck` rather than falling back silently at runtime.
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation';
    resources: {
      translation: typeof en;
    };
  }
}
