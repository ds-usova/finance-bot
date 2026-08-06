import { useEffect, useRef } from 'react';
import type { TelegramAuthPayload } from '../auth/types';

const WIDGET_SRC = 'https://telegram.org/js/telegram-widget.js?22';

type Props = {
  onAuth: (payload: TelegramAuthPayload) => void;
};

/**
 * Mounts Telegram's own Login Widget. The widget calls a global function by name, so one is registered under a
 * name unique to this mount and removed again on unmount.
 */
export function TelegramLoginButton({ onAuth }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const latestOnAuth = useRef(onAuth);
  latestOnAuth.current = onAuth;

  useEffect(() => {
    const node = container.current;
    if (!node) {
      return;
    }

    const callbackName = `onTelegramAuth_${Math.random().toString(36).slice(2)}`;
    const globals = window as unknown as Record<string, unknown>;
    globals[callbackName] = (payload: TelegramAuthPayload) => latestOnAuth.current(payload);

    const script = document.createElement('script');
    script.src = WIDGET_SRC;
    script.async = true;
    script.setAttribute('data-telegram-login', import.meta.env.VITE_TELEGRAM_BOT_USERNAME ?? '');
    script.setAttribute('data-size', 'large');
    script.setAttribute('data-userpic', 'true');
    script.setAttribute('data-onauth', `${callbackName}(user)`);
    node.appendChild(script);

    return () => {
      delete globals[callbackName];
      node.replaceChildren();
    };
  }, []);

  return <div ref={container} role="region" aria-label="Telegram sign-in" />;
}
