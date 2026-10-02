import { useTranslation } from 'react-i18next';

export type WriteInNote = { tone: 'pencil' | 'refusal'; message: string };

export type WriteInLineProps = {
  text: string;
  onTextChange: (text: string) => void;
  onFile: () => void;
  held: boolean;
  /** The text being read, or `null` when none is. */
  reading: string | null;
  note: WriteInNote | null;
  /** A spoken outcome, or `null`. */
  announcement: string | null;
};

export function WriteInLine({ text, onTextChange }: WriteInLineProps) {
  const { t } = useTranslation();

  // stub-intent: the write-in line. A multi-line field of at most 1000 characters, three lines high and growing
  // to eight, then scrolling; read-only while held, described by the held wording. Enter files unless Shift is
  // held or an input method is composing. Beneath it one row that always stands: the hint, only where the
  // pointer is fine, and the file control, disabled while the text is blank or the line held. Beneath that, at
  // most one of the reading line, a pencil note, or a refusal banner, and a polite status region carrying the
  // note's message or the announcement.
  return (
    <label>
      {t('writeIn.label')}
      <textarea value={text} onChange={(event) => onTextChange(event.target.value)} />
    </label>
  );
}
