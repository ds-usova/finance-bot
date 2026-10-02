import type { WriteInNote } from '../components/WriteInLine';

/** What the write-in line takes, owned by the page. */
export type WriteIn = {
  text: string;
  onTextChange: (text: string) => void;
  held: boolean;
  reading: string | null;
  note: WriteInNote | null;
  announcement: string | null;
  onFile: () => void;
};

export function useWrittenMessage(_onEntriesFiled: (days: string[]) => boolean): WriteIn {
  // stub-intent: the write-in loop. On load the line is held while the person's text being read is asked for;
  // filing holds the line, a 202 clears it and shows the text being read, a 429 asks for the text being read and
  // keeps the new text; every 5 seconds it asks what became of the message until it is no longer READING. Each
  // outcome has its row: RECORDED reads back the days through `onEntriesFiled`, which answers whether the
  // listing could show any of them, PARTIAL adds a note, NOTHING_IDENTIFIED, ANSWERED and FAILED return the text
  // with their note or refusal, and a refused filing keeps the text with the refusal. A 401 drops the session.
  return {
    text: '',
    onTextChange: () => {},
    held: false,
    reading: null,
    note: null,
    announcement: null,
    onFile: () => {},
  };
}
