import i18next from 'i18next';
import { en } from '../i18n/en';

type Catalogue = typeof en;

/** Deep-maps every string leaf of a catalogue-shaped value through `transform`, keeping its structure intact
 * — including the `_one`/`_other` plural keys and the `{{…}}` interpolation placeholders inside a value. */
function mapStrings<T>(value: T, transform: (text: string) => string): T {
  if (typeof value === 'string') {
    return transform(value) as T;
  }
  if (Array.isArray(value)) {
    return value.map((entry: unknown) => mapStrings(entry, transform)) as T;
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, entry]) => [key, mapStrings(entry, transform)]),
    ) as T;
  }
  return value;
}

const substituted: Catalogue = mapStrings(en, (text) => `‹${text}›`);

/**
 * Swaps the `en` catalogue for one whose text is distinguishable from the English wording — every string
 * wrapped in `‹…›`, placeholders left alone so interpolation still works — so a test can prove a surface
 * reads its text from the catalogue rather than from a literal. Returns a function that restores the English
 * catalogue; call it once the test is done, typically from `afterEach`.
 */
export function substituteCatalogue(): () => void {
  i18next.addResourceBundle('en', 'translation', substituted, true, true);
  return () => {
    i18next.addResourceBundle('en', 'translation', en, true, true);
  };
}
