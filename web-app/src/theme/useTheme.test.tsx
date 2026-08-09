import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { THEME_STORAGE_KEY } from './theme';
import { useTheme } from './useTheme';

/** Mirrors `vitest.setup.ts`'s `fakeMatchMedia`, which is not exported for reuse here. */
function stubMatchMedia(matches: boolean): void {
  window.matchMedia = ((query: string) =>
    ({
      matches,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList) as typeof window.matchMedia;
}

function stubLocalStorage(initial: Record<string, string> = {}) {
  const store = new Map(Object.entries(initial));
  const setItemSpy = vi.fn((key: string, value: string) => {
    store.set(key, value);
  });
  const storage: Storage = {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: setItemSpy,
    removeItem: (key: string) => {
      store.delete(key);
    },
    clear: () => store.clear(),
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size;
    },
  };
  return { storage, setItemSpy };
}

function Probe() {
  const { theme, toggleTheme } = useTheme();
  return (
    <div>
      <p>theme: {theme}</p>
      <button type="button" onClick={toggleTheme}>
        toggle theme
      </button>
    </div>
  );
}

function renderProbe() {
  return render(<Probe />);
}

afterEach(() => {
  vi.unstubAllGlobals();
  document.documentElement.classList.remove('dark');
  stubMatchMedia(false);
});

describe('the theme hook', () => {
  it('reports dark and has already marked the document at the very first render, painting no light frame first', () => {
    stubMatchMedia(true);
    vi.stubGlobal('localStorage', stubLocalStorage().storage);

    renderProbe();

    expect(screen.getByText('theme: dark')).toBeInTheDocument();
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('flips to light, drops the document marker and stores light when toggled from dark', async () => {
    stubMatchMedia(true);
    const { storage, setItemSpy } = stubLocalStorage();
    vi.stubGlobal('localStorage', storage);

    renderProbe();
    await userEvent.click(screen.getByRole('button', { name: 'toggle theme' }));

    expect(screen.getByText('theme: light')).toBeInTheDocument();
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(setItemSpy).toHaveBeenCalledWith(THEME_STORAGE_KEY, 'light');
  });

  it('keeps a toggled choice for a fresh mount, because it lives in storage rather than memory', async () => {
    stubMatchMedia(true);
    vi.stubGlobal('localStorage', stubLocalStorage({ [THEME_STORAGE_KEY]: 'light' }).storage);

    renderProbe();
    await userEvent.click(screen.getByRole('button', { name: 'toggle theme' }));

    const secondContainer = document.body.appendChild(document.createElement('div'));
    render(<Probe />, { container: secondContainer, baseElement: secondContainer });

    expect(within(secondContainer).getByText('theme: dark')).toBeInTheDocument();
  });

  it('returns to dark after toggling twice, the control never landing on a third value', async () => {
    stubMatchMedia(true);
    const { storage, setItemSpy } = stubLocalStorage();
    vi.stubGlobal('localStorage', storage);

    renderProbe();
    await userEvent.click(screen.getByRole('button', { name: 'toggle theme' }));
    await userEvent.click(screen.getByRole('button', { name: 'toggle theme' }));

    expect(screen.getByText('theme: dark')).toBeInTheDocument();
    expect(setItemSpy).toHaveBeenLastCalledWith(THEME_STORAGE_KEY, 'dark');
  });
});
