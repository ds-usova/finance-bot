import type { Session, TelegramAuthPayload } from '../auth/types';
import { request } from './client';

const SESSION_PATH = '/api/v1/session';

export async function createSession(payload: TelegramAuthPayload): Promise<Session> {
  const session = await request<Session>(SESSION_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!session) {
    throw new Error('the sign-in answered with no session');
  }
  return session;
}

export async function readSession(): Promise<Session | null> {
  return request<Session>(SESSION_PATH);
}

export async function deleteSession(): Promise<void> {
  await request<void>(SESSION_PATH, { method: 'DELETE' });
}
