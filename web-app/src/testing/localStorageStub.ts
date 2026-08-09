import { vi } from 'vitest';

/**
 * A `Storage` backed by a map, seeded with `initial`, alongside the spy standing in for its `setItem` so a
 * test can assert what was written and how often.
 */
export function stubLocalStorage(initial: Record<string, string> = {}) {
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
