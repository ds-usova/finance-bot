import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { aCategory, aGrouping, anExpense, anExpensePage } from './testing/fixtures';

describe('the wired application', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.history.pushState({}, '', '/');
  });

  function stubSession(response: Response) {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(response));
  }

  function jsonResponse(body: unknown) {
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  // A signed-in visitor costs four reads, and a body can only be consumed once, so each call is answered
  // by the path it asked for, with a Response built on the spot.
  function stubSignedIn(externalId: string) {
    const bodies: [string, unknown][] = [
      ['/expenses', anExpensePage([anExpense({ description: 'lunch' })])],
      ['/categories', [aCategory()]],
      ['/groupings', [aGrouping()]],
      ['/session', { externalId }],
    ];

    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockImplementation((input) => {
        const path =
          typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
        const body = bodies.find(([prefix]) => path.includes(prefix))?.[1];

        return Promise.resolve(body ? jsonResponse(body) : new Response(null, { status: 404 }));
      }),
    );
  }

  it('sends a visitor with no session to the sign-in page', async () => {
    stubSession(new Response(null, { status: 401 }));

    render(<App />);

    expect(await screen.findByRole('region', { name: 'Telegram sign-in' })).toBeInTheDocument();
  });

  it('shows a visitor whose session is already open the expenses they recorded', async () => {
    stubSignedIn('987654321');

    render(<App />);

    expect(await screen.findByText('lunch')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Expenses' })).toBeInTheDocument();
  });

  it('sends an unknown address to the home route rather than showing nothing', async () => {
    window.history.pushState({}, '', '/no-such-page');
    stubSignedIn('42');

    render(<App />);

    expect(await screen.findByText('lunch')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Expenses' })).toBeInTheDocument();
  });
});
