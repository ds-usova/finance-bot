import i18next from 'i18next';
import { describe, expect, it } from 'vitest';
import { ApiError, MalformedResponseError } from '../api/client';
import { en } from '../i18n/en';
import { refusalText } from './errorText';

describe('refusalText', () => {
  it('reads a MalformedResponseError by its own key, ignoring refusedKey', () => {
    const error = new MalformedResponseError(
      'listing.acceptanceMalformed',
      'the acceptance answered with no counts',
    );

    expect(refusalText(error, i18next.t, 'settings.refused')).toBe(en.listing.acceptanceMalformed);
  });

  it("shows the surface's fixed wording for an ApiError", () => {
    const error = new ApiError(503, 'the ledger is temporarily unavailable');

    expect(refusalText(error, i18next.t, 'listing.refused')).toBe(en.listing.refused);
  });

  it('shows the same fixed wording for anything that is not an Error at all', () => {
    expect(refusalText('not an error', i18next.t, 'listing.refused')).toBe(en.listing.refused);
  });

  it("shows a plain Error's own message, unchanged", () => {
    const error = new Error('the network is unreachable');

    expect(refusalText(error, i18next.t, 'settings.refused')).toBe('the network is unreachable');
  });
});
