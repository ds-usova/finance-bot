import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';
import { en } from './en';

// Initializes the default i18next instance as a side effect of import, so a component rendered on its own in
// a test reads the same catalogue the app does. No language detector and no language control, per D2 and D16.
void i18next.use(initReactI18next).init({
  lng: 'en',
  fallbackLng: 'en',
  resources: {
    en: { translation: en },
  },
  interpolation: {
    escapeValue: false,
  },
});

export const i18n = i18next;
