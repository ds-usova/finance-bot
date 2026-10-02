import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { fileMessage, listReadingMessages, readMessage } from '../api/messages';
import type { WrittenMessage } from '../api/messages';
import { useAuth } from '../auth/useAuth';
import type { WriteInNote } from '../components/WriteInLine';
import { refusalText } from './errorText';

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

type Watched = { id: number; text: string };

const ASK_INTERVAL_MS = 5000;

function isStatus(error: unknown, status: number): boolean {
  return error instanceof ApiError && error.status === status;
}

export function useWrittenMessage(onEntriesFiled: (days: string[]) => boolean): WriteIn {
  const { t } = useTranslation();
  const { sessionExpired } = useAuth();
  const [text, setText] = useState('');
  const [held, setHeld] = useState(true);
  const [watched, setWatched] = useState<Watched | null>(null);
  const [note, setNote] = useState<WriteInNote | null>(null);
  const [announcement, setAnnouncement] = useState<string | null>(null);

  const latest = useRef({ onEntriesFiled, sessionExpired, t });
  useEffect(() => {
    latest.current = { onEntriesFiled, sessionExpired, t };
  });

  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const stopWatching = useRef<() => void>(() => {});
  useEffect(() => () => stopWatching.current(), []);

  const watch = useCallback((watched: Watched) => {
    stopWatching.current();
    setWatched(watched);
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    stopWatching.current = () => {
      cancelled = true;
      clearTimeout(timer);
    };

    const finish = (returned: string, outcomeNote: WriteInNote | null, spoken: string | null) => {
      setWatched(null);
      setHeld(false);
      setText((current) => (current === '' ? returned : current));
      setNote(outcomeNote);
      setAnnouncement(spoken);
    };

    const finishRead = (message: WrittenMessage) => {
      const { onEntriesFiled: readBack, t: translate } = latest.current;
      const days = message.days ?? [];
      switch (message.outcome) {
        case 'RECORDED': {
          const shown = readBack(days);
          finish(
            '',
            shown ? null : { tone: 'pencil', message: translate('writeIn.outOfView') },
            shown ? translate('writeIn.recordedSpoken') : null,
          );
          break;
        }
        case 'PARTIAL':
          readBack(days);
          finish('', { tone: 'pencil', message: translate('writeIn.listIncomplete') }, null);
          break;
        case 'NOTHING_IDENTIFIED':
          finish(watched.text, { tone: 'pencil', message: translate('writeIn.nothingFound') }, null);
          break;
        case 'ANSWERED':
          finish(watched.text, { tone: 'pencil', message: translate('writeIn.onlyQuestion') }, null);
          break;
        default:
          finish(watched.text, { tone: 'refusal', message: translate('writeIn.couldNotBeRead') }, null);
      }
    };

    const ask = () => {
      timer = setTimeout(() => {
        readMessage(watched.id)
          .then((message) => {
            if (cancelled) {
              return;
            }
            if (message.state === 'READING') {
              ask();
            } else {
              finishRead(message);
            }
          })
          .catch((error: unknown) => {
            if (cancelled) {
              return;
            }
            if (isStatus(error, 401)) {
              latest.current.sessionExpired();
            } else if (isStatus(error, 404)) {
              finish(
                watched.text,
                { tone: 'refusal', message: latest.current.t('writeIn.couldNotBeRead') },
                null,
              );
            } else {
              ask();
            }
          });
      }, ASK_INTERVAL_MS);
    };

    ask();
  }, []);

  const resume = useCallback(
    (messages: WrittenMessage[]) => {
      const first = messages[0];
      if (first) {
        watch({ id: first.id, text: first.text });
      } else {
        setHeld(false);
      }
    },
    [watch],
  );

  useEffect(() => {
    let cancelled = false;

    listReadingMessages()
      .then((messages) => {
        if (!cancelled) {
          resume(messages);
        }
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }
        if (isStatus(error, 401)) {
          latest.current.sessionExpired();
          return;
        }
        setHeld(false);
      });

    return () => {
      cancelled = true;
    };
  }, [resume]);

  const onFile = useCallback(() => {
    if (held || text.trim() === '') {
      return;
    }
    const filed = text;
    setHeld(true);
    setNote(null);
    setAnnouncement(null);

    fileMessage(filed)
      .then((message) => {
        if (mounted.current) {
          setText('');
          watch({ id: message.id, text: filed });
        }
      })
      .catch((error: unknown) => {
        if (!mounted.current) {
          return;
        }
        if (isStatus(error, 401)) {
          latest.current.sessionExpired();
        } else if (isStatus(error, 429)) {
          listReadingMessages()
            .then((messages) => {
              if (mounted.current) {
                resume(messages);
              }
            })
            .catch((askError: unknown) => {
              if (!mounted.current) {
                return;
              }
              if (isStatus(askError, 401)) {
                latest.current.sessionExpired();
              } else {
                setHeld(false);
              }
            });
        } else {
          setNote({ tone: 'refusal', message: refusalText(error, latest.current.t, 'writeIn.filingRefused') });
          setHeld(false);
        }
      });
  }, [held, text, resume, watch]);

  return {
    text,
    onTextChange: setText,
    held,
    reading: watched?.text ?? null,
    note,
    announcement,
    onFile,
  };
}
