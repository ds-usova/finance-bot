import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { substituteCatalogue } from '../testing/catalogue';
import { ErrorBanner } from './ErrorBanner';

describe('the error banner', () => {
  it('announces the message it is given', () => {
    render(<ErrorBanner message="from must be a date" />);

    expect(screen.getByRole('alert')).toHaveTextContent('from must be a date');
  });

  it('shows the message it was handed exactly as given, with no catalogue wording anywhere in it, even once the catalogue is swapped', () => {
    const restore = substituteCatalogue();
    try {
      render(<ErrorBanner message="from must be a date" />);

      const banner = screen.getByRole('alert');
      expect(banner).toHaveTextContent('from must be a date');
      expect(banner).not.toHaveTextContent('‹');
    } finally {
      restore();
    }
  });
});
