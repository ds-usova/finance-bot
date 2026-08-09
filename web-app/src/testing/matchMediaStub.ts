/**
 * Replaces `window.matchMedia`, which jsdom does not implement at all, with one answering `matches` for
 * every query — faithful enough to the real contract for the primitives that call it. `vitest.setup.ts`
 * installs the not-dark answer for every test; a test driving the system preference calls this again.
 */
export function stubMatchMedia(matches: boolean): void {
  window.matchMedia = (query: string) =>
    ({
      matches,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList;
}
