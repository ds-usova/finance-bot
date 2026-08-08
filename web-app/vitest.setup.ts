import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import './src/i18n/config';

// jsdom implements none of the below, and the accessible primitives from radix-ui — the combobox, the
// accordion — reach for all four.

/** Answers `matches` for any query, faithful enough to the real contract for the primitives that call it. */
function fakeMatchMedia(matches: boolean): typeof window.matchMedia {
  return (query: string) =>
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

// Reports *not* dark by default; RU01 and RU02 replace this per test to drive the system preference both ways.
window.matchMedia = fakeMatchMedia(false);

class StubResizeObserver implements ResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}
window.ResizeObserver = StubResizeObserver;

Element.prototype.hasPointerCapture = () => false;
Element.prototype.scrollIntoView = () => {};

afterEach(() => {
  cleanup();
});
