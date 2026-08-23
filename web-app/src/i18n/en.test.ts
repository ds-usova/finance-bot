import { describe, expect, it } from 'vitest';
import { en } from './en';

describe('the currencies namespace', () => {
  it('offers none of the codes no amount can be recorded in', () => {
    const nonTransactable = [
      'XAU',
      'XDR',
      'XXX',
      'XAG',
      'XPT',
      'XPD',
      'XBA',
      'XBB',
      'XBC',
      'XBD',
      'XTS',
      'XSU',
      'XUA',
    ];
    const keys = Object.keys(en.currencies);

    for (const code of nonTransactable) {
      expect(keys).not.toContain(code);
    }
  });

  it('gives every key as three upper-case letters and every value as a non-empty name', () => {
    for (const [key, value] of Object.entries(en.currencies)) {
      expect(key).toMatch(/^[A-Z]{3}$/);
      expect(value.length).toBeGreaterThan(0);
    }
  });

  it('offers every common current code and holds well over a hundred codes rather than the handful stabilization left', () => {
    const common = ['EUR', 'USD', 'GBP', 'JPY', 'CHF', 'PLN', 'TRY', 'ZAR'];
    const keys = Object.keys(en.currencies);

    for (const code of common) {
      expect(keys).toContain(code);
    }
    expect(keys.length).toBeGreaterThan(100);
  });
});
