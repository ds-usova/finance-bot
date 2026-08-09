import i18next from 'i18next';
import { onTestFinished } from 'vitest';
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

// config.ts registers the catalogue by reference (`resources: { en: { translation: en } }`), and i18next's
// ResourceStore keeps whatever object it is given rather than cloning it — so the store's English bundle is
// the very object this module's `en` import points to. A snapshot taken here, before any substitution runs,
// is what the restore step hands back; it never becomes the mutable object the store holds.
const original: Catalogue = JSON.parse(JSON.stringify(en));

/**
 * Swaps the `en` catalogue for one whose text is distinguishable from the English wording — every string
 * wrapped in `‹…›`, placeholders left alone so interpolation still works — so a test can prove a surface
 * reads its text from the catalogue rather than from a literal. The English catalogue is put back when the
 * calling test finishes, however it finishes, so only a test may call this.
 */
export function substituteCatalogue(): void {
  // `deep: true` would deep-extend the bundle currently stored in the instance *in place* — and that stored
  // bundle is the live `en` export, so it would rewrite `en` itself. `deep: false` instead builds a new
  // object (`{ ...pack, ...resources }`) and swaps it in, leaving whatever object was stored before
  // untouched. Both calls below pass a complete catalogue, so the shallow merge fully replaces every
  // namespace and never leaves stale keys behind.
  i18next.addResourceBundle('en', 'translation', substituted, false, true);
  onTestFinished(() => {
    i18next.addResourceBundle('en', 'translation', original, false, true);
  });
}
