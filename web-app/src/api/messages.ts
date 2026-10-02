import type { components } from './generated/ledger-api';

export type WrittenMessage = components['schemas']['WrittenMessage'];
export type WrittenMessageOutcome = components['schemas']['WrittenMessageOutcome'];

export function fileMessage(_text: string): Promise<WrittenMessage> {
  // stub-intent: POST /api/v1/messages through `request` from ./client, the text as the JSON body
  // `{ text }`, answering the filed message.
  return Promise.reject(new Error('not implemented'));
}

export function readMessage(_id: number): Promise<WrittenMessage> {
  // stub-intent: GET /api/v1/messages/{id} through `request` from ./client, answering that message.
  return Promise.reject(new Error('not implemented'));
}

export function listReadingMessages(): Promise<WrittenMessage[]> {
  // stub-intent: GET /api/v1/messages?state=READING through `request` from ./client, answering the
  // messages still being read.
  return Promise.resolve([]);
}
