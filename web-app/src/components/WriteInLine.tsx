import { useId, useRef, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { ErrorBanner } from './ErrorBanner';
import { Button } from './ui/button';

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

const MAX_LENGTH = 1000;

export function WriteInLine({
  text,
  onTextChange,
  onFile,
  held,
  reading,
  note,
  announcement,
}: WriteInLineProps) {
  const { t } = useTranslation();
  const fieldId = useId();
  const heldId = useId();
  const composing = useRef(false);
  const canFile = text.trim() !== '' && !held;

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== 'Enter' || event.shiftKey) {
      return;
    }
    if (composing.current || event.nativeEvent.isComposing) {
      return;
    }
    event.preventDefault();
    if (canFile) {
      onFile();
    }
  }

  const pencilMessage = note?.tone === 'pencil' ? note.message : null;
  const statusMessage = note?.message ?? announcement;

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={fieldId} className="sr-only">
        {t('writeIn.label')}
      </label>
      <textarea
        id={fieldId}
        value={text}
        maxLength={MAX_LENGTH}
        rows={3}
        readOnly={held}
        aria-describedby={held ? heldId : undefined}
        placeholder={t('writeIn.placeholder')}
        onChange={(event) => onTextChange(event.target.value)}
        onKeyDown={onKeyDown}
        onCompositionStart={() => {
          composing.current = true;
        }}
        onCompositionEnd={() => {
          composing.current = false;
        }}
        className="max-h-[12rem] min-h-[4.5rem] w-full resize-none overflow-y-auto border-b border-rule-strong bg-transparent px-1 py-2 text-base leading-6 text-ink outline-none [field-sizing:content] placeholder:text-pencil focus-visible:border-focus"
      />
      {held && (
        <span id={heldId} className="sr-only">
          {t('writeIn.heldSpoken')}
        </span>
      )}
      <div className="flex items-center justify-end gap-3">
        <span className="hidden text-xs text-pencil [@media(pointer:fine)]:inline">
          {t('writeIn.hint')}
        </span>
        <Button type="button" variant="outline" disabled={!canFile} onClick={onFile}>
          {t('writeIn.file')}
        </Button>
      </div>
      {note === null && reading !== null && (
        <div
          role="group"
          aria-label={t('writeIn.readingSpoken', { text: reading })}
          className="flex items-start gap-2 text-sm text-pencil"
        >
          <span aria-hidden="true" className="shrink-0 italic motion-safe:animate-pulse">
            {t('writeIn.readingMark')}
          </span>
          <span aria-hidden="true" className="line-clamp-3 min-w-0 [overflow-wrap:anywhere]">
            {reading}
          </span>
        </div>
      )}
      {note?.tone === 'refusal' && <ErrorBanner message={note.message} />}
      <div role="status" className={pencilMessage ? 'text-sm text-pencil' : 'sr-only'}>
        {statusMessage}
      </div>
    </div>
  );
}
