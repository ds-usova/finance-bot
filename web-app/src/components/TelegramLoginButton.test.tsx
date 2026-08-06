import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { TelegramAuthPayload } from '../auth/types';
import { TelegramLoginButton } from './TelegramLoginButton';

function widgetScript(): HTMLScriptElement | null {
  return document.querySelector('script[data-telegram-login]');
}

function registeredCallbackName(): string {
  const onAuth = widgetScript()?.getAttribute('data-onauth') ?? '';
  return onAuth.replace('(user)', '');
}

describe('the Telegram login button', () => {
  it('injects Telegram’s own widget script', () => {
    render(<TelegramLoginButton onAuth={vi.fn()} />);

    expect(widgetScript()?.src).toBe('https://telegram.org/js/telegram-widget.js?22');
  });

  it('names the configured bot, so the widget knows which account it signs in for', () => {
    render(<TelegramLoginButton onAuth={vi.fn()} />);

    expect(widgetScript()?.getAttribute('data-telegram-login')).toBe(
      import.meta.env.VITE_TELEGRAM_BOT_USERNAME ?? '',
    );
  });

  it('hands the widget’s payload to onAuth when the widget calls back', () => {
    const onAuth = vi.fn();
    render(<TelegramLoginButton onAuth={onAuth} />);
    const payload: TelegramAuthPayload = { id: 42, hash: 'abc' };

    const globals = window as unknown as Record<string, (payload: TelegramAuthPayload) => void>;
    globals[registeredCallbackName()]?.(payload);

    expect(onAuth).toHaveBeenCalledWith(payload);
  });

  it('removes its global callback on unmount, leaving nothing behind on the page', () => {
    const { unmount } = render(<TelegramLoginButton onAuth={vi.fn()} />);
    const callbackName = registeredCallbackName();

    unmount();

    expect(window as unknown as Record<string, unknown>).not.toHaveProperty(callbackName);
  });

  it('exposes the widget as a labelled region', () => {
    render(<TelegramLoginButton onAuth={vi.fn()} />);

    expect(screen.getByRole('region', { name: 'Telegram sign-in' })).toBeInTheDocument();
  });
});
