import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import './src/i18n/config';
import { stubMatchMedia } from './src/testing/matchMediaStub';

// jsdom implements none of the below, and the accessible primitives from radix-ui — the combobox, the
// accordion — reach for all four.

// Reports *not* dark by default; a test driving the system preference replaces this for itself.
stubMatchMedia(false);

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
