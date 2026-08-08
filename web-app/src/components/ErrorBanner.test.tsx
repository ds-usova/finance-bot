import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ErrorBanner } from './ErrorBanner';

describe('the error banner', () => {
  it('announces the message it is given', () => {
    render(<ErrorBanner message="from must be a date" />);

    expect(screen.getByRole('alert')).toHaveTextContent('from must be a date');
  });
});
