import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

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

  it('sends a visitor with no session to the sign-in page', async () => {
    stubSession(new Response(null, { status: 401 }));

    render(<App />);

    expect(await screen.findByRole('region', { name: 'Telegram sign-in' })).toBeInTheDocument();
  });

  it.skip('shows the shell to a visitor whose session is already open — RU07 rewrites it against ExpensesPage', async () => {
    stubSession(jsonResponse({ externalId: '987654321' }));

    render(<App />);

    expect(await screen.findByText('Signed in as 987654321')).toBeInTheDocument();
  });

  it.skip('sends an unknown address to the home route rather than showing nothing — RU07 rewrites it against ExpensesPage', async () => {
    window.history.pushState({}, '', '/no-such-page');
    stubSession(jsonResponse({ externalId: '42' }));

    render(<App />);

    expect(await screen.findByText('Signed in as 42')).toBeInTheDocument();
  });
});
