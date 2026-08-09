import { afterEach, describe, expect, it, vi } from 'vitest';
import { stubLocalStorage } from '../testing/localStorageStub';
import { stubMatchMedia } from '../testing/matchMediaStub';
import { THEME_STORAGE_KEY, applyTheme, resolveTheme, storeTheme, type Theme } from './theme';

function throwingLocalStorage(): Storage {
  const refuse = () => {
    throw new DOMException('refused');
  };
  return {
    getItem: refuse,
    setItem: refuse,
    removeItem: () => {},
    clear: () => {},
    key: () => null,
    length: 0,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  document.documentElement.classList.remove('dark');
  stubMatchMedia(false);
});

describe('resolveTheme()', () => {
  it('answers dark on a first visit when nothing is stored and the system prefers dark', () => {
    stubMatchMedia(true);
    vi.stubGlobal('localStorage', stubLocalStorage().storage);

    expect(resolveTheme()).toBe('dark');
  });

  it('answers light when nothing is stored and the system does not prefer dark', () => {
    stubMatchMedia(false);
    vi.stubGlobal('localStorage', stubLocalStorage().storage);

    expect(resolveTheme()).toBe('light');
  });

  it('answers the stored override even when the system disagrees', () => {
    stubMatchMedia(false);
    vi.stubGlobal('localStorage', stubLocalStorage({ [THEME_STORAGE_KEY]: 'dark' }).storage);

    expect(resolveTheme()).toBe('dark');
  });

  it('answers the system preference when the stored value is not a theme, exactly as if nothing were stored', () => {
    stubMatchMedia(true);

    vi.stubGlobal('localStorage', stubLocalStorage({ [THEME_STORAGE_KEY]: 'system' }).storage);
    expect(resolveTheme()).toBe('dark');

    vi.stubGlobal('localStorage', stubLocalStorage({ [THEME_STORAGE_KEY]: '' }).storage);
    expect(resolveTheme()).toBe('dark');
  });

  it('answers the system preference without rethrowing when the store cannot be read', () => {
    stubMatchMedia(true);
    vi.stubGlobal('localStorage', throwingLocalStorage());

    let result: Theme | undefined;
    expect(() => {
      result = resolveTheme();
    }).not.toThrow();
    expect(result).toBe('dark');
  });
});

describe('applyTheme()', () => {
  it('marks the document element with the dark marker when applied with dark', () => {
    applyTheme('dark');

    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('removes the marker when applied with light, since light is the marker’s absence', () => {
    document.documentElement.classList.add('dark');

    applyTheme('light');

    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });
});

describe('storeTheme()', () => {
  it('records dark under the module’s one key and writes nothing else', () => {
    const { storage, setItemSpy } = stubLocalStorage();
    vi.stubGlobal('localStorage', storage);

    storeTheme('dark');

    expect(setItemSpy).toHaveBeenCalledOnce();
    expect(setItemSpy).toHaveBeenCalledWith(THEME_STORAGE_KEY, 'dark');
  });

  it('swallows a write that throws, recording nothing, rethrowing nothing and logging nothing', () => {
    vi.stubGlobal('localStorage', throwingLocalStorage());
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});

    expect(() => storeTheme('dark')).not.toThrow();

    expect(errorSpy).not.toHaveBeenCalled();
    expect(warnSpy).not.toHaveBeenCalled();
    expect(logSpy).not.toHaveBeenCalled();
  });
});
