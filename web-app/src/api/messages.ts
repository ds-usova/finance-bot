import { request } from './client';
import type { components } from './generated/ledger-api';

export type WrittenMessage = components['schemas']['WrittenMessage'];
export type WrittenMessageOutcome = components['schemas']['WrittenMessageOutcome'];

const MESSAGES_PATH = '/api/v1/messages';

function requireMessageBody<T>(body: T | null): T {
  if (!body) {
    throw new Error('the messages endpoint answered with no body');
  }
  return body;
}

export async function fileMessage(text: string): Promise<WrittenMessage> {
  const filed = await request<WrittenMessage>(MESSAGES_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
  });
  return requireMessageBody(filed);
}

export async function readMessage(id: number): Promise<WrittenMessage> {
  const message = await request<WrittenMessage>(`${MESSAGES_PATH}/${id}`);
  return requireMessageBody(message);
}

export async function listReadingMessages(): Promise<WrittenMessage[]> {
  const messages = await request<WrittenMessage[]>(`${MESSAGES_PATH}?state=READING`);
  return requireMessageBody(messages);
}
