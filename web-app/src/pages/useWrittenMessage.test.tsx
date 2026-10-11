import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { fileMessage, listReadingMessages, readMessage } from '../api/messages';
import { AuthContext } from '../auth/authContext';
import { en } from '../i18n/en';
import { anAuthContext, aWrittenMessage } from '../testing/fixtures';
import { inFlight } from '../testing/inFlight';
import { useWrittenMessage } from './useWrittenMessage';

vi.mock('../api/messages', () => ({
  fileMessage: vi.fn(),
  readMessage: vi.fn(),
  listReadingMessages: vi.fn(),
}));

const fileMessageMock = vi.mocked(fileMessage);
const readMessageMock = vi.mocked(readMessage);
const listReadingMessagesMock = vi.mocked(listReadingMessages);

const FILED = 'coffee 3.40, taxi home 14';

function mountHook(
  options: { onEntriesFiled?: (days: string[]) => boolean; sessionExpired?: () => void } = {},
) {
  const onEntriesFiled = options.onEntriesFiled ?? vi.fn(() => true);
  const value = anAuthContext({ sessionExpired: options.sessionExpired ?? (() => {}) });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
  );

  return renderHook(() => useWrittenMessage(onEntriesFiled), { wrapper });
}

type Mounted = ReturnType<typeof mountHook>;

/** Lets every settled promise run its continuation, and moves the clock on by `ms`. */
async function pass(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

/** Types the text and files it, then lets the answer land. */
async function typeAndFile(mounted: Mounted, text: string = FILED) {
  act(() => mounted.result.current.onTextChange(text));
  act(() => mounted.result.current.onFile());
  await pass(0);
}

/** A mounted hook whose load-time ask answered nothing being read, so the line is editable. */
async function editableLine(options: Parameters<typeof mountHook>[0] = {}) {
  listReadingMessagesMock.mockResolvedValueOnce([]);
  const mounted = mountHook(options);
  await pass(0);
  return mounted;
}

/** An editable line whose filing was answered as message 42, with its reading standing. */
async function filedAsMessage42(options: Parameters<typeof mountHook>[0] = {}) {
  fileMessageMock.mockResolvedValueOnce(aWrittenMessage({ id: 42, text: FILED }));
  const mounted = await editableLine(options);
  await typeAndFile(mounted);
  return mounted;
}

function done(
  outcome: 'RECORDED' | 'PARTIAL' | 'NOTHING_IDENTIFIED' | 'ANSWERED' | 'FAILED',
  days: string[] = [],
) {
  return aWrittenMessage({ id: 42, state: 'DONE', text: FILED, outcome, days });
}

describe('useWrittenMessage()', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    listReadingMessagesMock.mockResolvedValue([]);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.resetAllMocks();
  });

  describe('on load', () => {
    it('holds the line, with no reading line, while the ask for the text being read is out', () => {
      listReadingMessagesMock.mockReset();
      listReadingMessagesMock.mockReturnValueOnce(inFlight<never>().promise);

      const { result } = mountHook();

      expect(result.current.held).toBe(true);
      expect(result.current.reading).toBeNull();
    });

    it('shows the text being read, keeps the line held and asks what became of it, when a text is still being read', async () => {
      listReadingMessagesMock.mockReset();
      listReadingMessagesMock.mockResolvedValueOnce([
        aWrittenMessage({ id: 7, text: 'lunch 12.50' }),
      ]);
      readMessageMock.mockResolvedValue(aWrittenMessage({ id: 7, text: 'lunch 12.50' }));

      const { result } = mountHook();
      await pass(5000);

      expect(result.current.reading).toBe('lunch 12.50');
      expect(result.current.held).toBe(true);
      expect(readMessageMock).toHaveBeenCalledWith(7);
    });

    it('makes the line editable once nothing is being read, and reads nothing', async () => {
      const ask = inFlight<never[]>();
      listReadingMessagesMock.mockReset();
      listReadingMessagesMock.mockReturnValueOnce(ask.promise);

      const { result } = mountHook();
      expect(result.current.held).toBe(true);

      await act(async () => {
        ask.answer([]);
      });
      await pass(5000);

      expect(result.current.held).toBe(false);
      expect(readMessageMock).not.toHaveBeenCalled();
    });

    it.each([
      ['503', new ApiError(503, 'unavailable')],
      ['404', new ApiError(404, 'no such message')],
      ['a network failure', new Error('the network is unreachable')],
    ])(
      'makes the line editable, with no reading line and no note, when the ask is refused with %s',
      async (_name, refusal) => {
        const ask = inFlight<never[]>();
        listReadingMessagesMock.mockReset();
        listReadingMessagesMock.mockReturnValueOnce(ask.promise);

        const { result } = mountHook();
        expect(result.current.held).toBe(true);

        await act(async () => {
          ask.refuse(refusal);
        });

        expect(result.current.held).toBe(false);
        expect(result.current.reading).toBeNull();
        expect(result.current.note).toBeNull();
      },
    );

    it('reports an expired session when the ask is refused with 401', async () => {
      const sessionExpired = vi.fn();
      listReadingMessagesMock.mockReset();
      listReadingMessagesMock.mockRejectedValueOnce(new ApiError(401, 'no session'));

      mountHook({ sessionExpired });
      await pass(0);

      expect(sessionExpired).toHaveBeenCalledOnce();
    });
  });

  describe('filing', () => {
    it('hands the text over, clears the line and holds it, and shows the text being read, when the filing is accepted', async () => {
      fileMessageMock.mockResolvedValueOnce(aWrittenMessage({ id: 42, text: FILED }));
      const mounted = await editableLine();

      await typeAndFile(mounted);

      expect(fileMessageMock).toHaveBeenCalledWith(FILED);
      expect(mounted.result.current.text).toBe('');
      expect(mounted.result.current.held).toBe(true);
      expect(mounted.result.current.reading).toBe(FILED);
    });

    it('holds the line with its text and files once when asked again while the filing is out', async () => {
      fileMessageMock.mockReturnValueOnce(inFlight<never>().promise);
      const mounted = await editableLine();

      await typeAndFile(mounted);
      act(() => mounted.result.current.onFile());
      await pass(0);

      expect(mounted.result.current.held).toBe(true);
      expect(mounted.result.current.text).toBe(FILED);
      expect(fileMessageMock).toHaveBeenCalledOnce();
    });

    it.each([
      ['files nothing from an editable line holding only spaces', false, '   ', 0],
      ['files nothing from a held line holding a text', true, FILED, 0],
      ['files the text of an editable line holding one', false, FILED, 1],
    ])('%s', async (_name, held, text, calls) => {
      fileMessageMock.mockReturnValue(inFlight<never>().promise);
      let mounted: Mounted;
      if (held) {
        listReadingMessagesMock.mockReset();
        listReadingMessagesMock.mockReturnValueOnce(inFlight<never>().promise);
        mounted = mountHook();
      } else {
        mounted = await editableLine();
      }

      await typeAndFile(mounted, text);

      expect(fileMessageMock).toHaveBeenCalledTimes(calls);
    });

    it.each([
      ['400', new ApiError(400, 'blank'), en.writeIn.filingRefused],
      ['403', new ApiError(403, 'forbidden'), en.writeIn.filingRefused],
      ['404', new ApiError(404, 'unknown'), en.writeIn.filingRefused],
      ['503', new ApiError(503, 'unavailable'), en.writeIn.filingRefused],
      ['a network failure', new Error('the network is unreachable'), 'the network is unreachable'],
    ])(
      'keeps the text in the editable line and refuses with the right words when the filing fails with %s',
      async (_name, refusal, message) => {
        fileMessageMock.mockRejectedValueOnce(refusal);
        const mounted = await editableLine();

        await typeAndFile(mounted);

        expect(mounted.result.current.text).toBe(FILED);
        expect(mounted.result.current.held).toBe(false);
        expect(mounted.result.current.note).toEqual({ tone: 'refusal', message });
        expect(mounted.result.current.reading).toBeNull();
      },
    );

    it('shows the text already being read, keeping the new text in the held line, when the filing is refused with 429', async () => {
      fileMessageMock.mockRejectedValueOnce(new ApiError(429, 'a text is being read'));
      listReadingMessagesMock.mockResolvedValueOnce([]);
      listReadingMessagesMock.mockResolvedValueOnce([aWrittenMessage({ id: 9, text: 'taxi 14' })]);
      readMessageMock.mockResolvedValue(aWrittenMessage({ id: 9, text: 'taxi 14' }));
      const mounted = mountHook();
      await pass(0);

      await typeAndFile(mounted, 'lunch 5');
      await pass(5000);

      expect(mounted.result.current.reading).toBe('taxi 14');
      expect(mounted.result.current.text).toBe('lunch 5');
      expect(mounted.result.current.held).toBe(true);
      expect(readMessageMock).toHaveBeenCalledWith(9);
    });

    it.each([
      ['nothing being read', () => listReadingMessagesMock.mockResolvedValueOnce([])],
      [
        'a refusal',
        () => listReadingMessagesMock.mockRejectedValueOnce(new ApiError(503, 'unavailable')),
      ],
    ])(
      'keeps the new text in an editable line, with no reading line and no note, when a 429 is followed by %s',
      async (_name, secondAsk) => {
        fileMessageMock.mockRejectedValueOnce(new ApiError(429, 'a text is being read'));
        listReadingMessagesMock.mockResolvedValueOnce([]);
        secondAsk();
        const mounted = mountHook();
        await pass(0);

        await typeAndFile(mounted, 'lunch 5');

        expect(mounted.result.current.text).toBe('lunch 5');
        expect(mounted.result.current.held).toBe(false);
        expect(mounted.result.current.reading).toBeNull();
        expect(mounted.result.current.note).toBeNull();
      },
    );

    it('leaves the new text in the line and notes that nothing was found, when the text resumed behind a 429 comes to nothing', async () => {
      fileMessageMock.mockRejectedValueOnce(new ApiError(429, 'a text is being read'));
      listReadingMessagesMock.mockResolvedValueOnce([]);
      listReadingMessagesMock.mockResolvedValueOnce([
        aWrittenMessage({ id: 9, text: 'lunch 12.50' }),
      ]);
      readMessageMock.mockResolvedValue(
        aWrittenMessage({
          id: 9,
          state: 'DONE',
          text: 'lunch 12.50',
          outcome: 'NOTHING_IDENTIFIED',
        }),
      );
      const mounted = mountHook();
      await pass(0);
      await typeAndFile(mounted, 'taxi 14');

      await pass(5000);

      expect(mounted.result.current.text).toBe('taxi 14');
      expect(mounted.result.current.held).toBe(false);
      expect(mounted.result.current.note).toEqual({
        tone: 'pencil',
        message: en.writeIn.nothingFound,
      });
    });

    it('reports an expired session when the filing is refused with 401', async () => {
      const sessionExpired = vi.fn();
      fileMessageMock.mockRejectedValueOnce(new ApiError(401, 'no session'));
      const mounted = await editableLine({ sessionExpired });

      await typeAndFile(mounted);

      expect(sessionExpired).toHaveBeenCalledOnce();
    });

    it('removes a standing note when a new text is filed', async () => {
      readMessageMock.mockResolvedValueOnce(done('NOTHING_IDENTIFIED'));
      const mounted = await filedAsMessage42();
      await pass(5000);
      expect(mounted.result.current.note).not.toBeNull();
      fileMessageMock.mockResolvedValueOnce(aWrittenMessage({ id: 43, text: FILED }));

      act(() => mounted.result.current.onFile());

      expect(mounted.result.current.note).toBeNull();
    });
  });

  describe('asking what became of a filed text', () => {
    it('asks for message 42 every 5 seconds until it is no longer being read, and then stops', async () => {
      readMessageMock
        .mockResolvedValueOnce(aWrittenMessage({ id: 42, state: 'READING' }))
        .mockResolvedValueOnce(aWrittenMessage({ id: 42, state: 'READING' }))
        .mockResolvedValueOnce(done('RECORDED', ['2026-10-01']));
      await filedAsMessage42();

      await pass(4999);
      expect(readMessageMock).not.toHaveBeenCalled();
      await pass(1);
      expect(readMessageMock).toHaveBeenCalledTimes(1);
      await pass(5000);
      expect(readMessageMock).toHaveBeenCalledTimes(2);
      await pass(5000);
      expect(readMessageMock).toHaveBeenCalledTimes(3);
      await pass(5000);

      expect(readMessageMock).toHaveBeenCalledTimes(3);
      expect(readMessageMock.mock.calls.every(([id]) => id === 42)).toBe(true);
    });

    it('keeps the reading line standing and goes on asking when an ask fails', async () => {
      readMessageMock
        .mockRejectedValueOnce(new ApiError(503, 'unavailable'))
        .mockRejectedValueOnce(new Error('the network is unreachable'))
        .mockResolvedValueOnce(aWrittenMessage({ id: 42, state: 'READING' }));
      const mounted = await filedAsMessage42();

      await pass(5000);
      expect(mounted.result.current.reading).toBe(FILED);
      await pass(5000);
      expect(mounted.result.current.reading).toBe(FILED);
      await pass(5000);

      expect(mounted.result.current.reading).toBe(FILED);
      expect(readMessageMock).toHaveBeenCalledTimes(3);
    });

    it('reads back both days, clears the line and announces the entries, when they were recorded and the list shows them', async () => {
      const days = ['2026-09-30', '2026-10-01'];
      readMessageMock.mockResolvedValueOnce(done('RECORDED', days));
      const onEntriesFiled = vi.fn(() => true);
      const mounted = await filedAsMessage42({ onEntriesFiled });

      await pass(5000);

      expect(onEntriesFiled).toHaveBeenCalledWith(days);
      expect(mounted.result.current.reading).toBeNull();
      expect(mounted.result.current.text).toBe('');
      expect(mounted.result.current.held).toBe(false);
      expect(mounted.result.current.note).toBeNull();
      expect(mounted.result.current.announcement).toBe('Filed. The new entries await a decision.');
    });

    it('notes that the list does not show the days when they were recorded and the list cannot show them', async () => {
      readMessageMock.mockResolvedValueOnce(done('RECORDED', ['2026-09-30', '2026-10-01']));
      const mounted = await filedAsMessage42({ onEntriesFiled: vi.fn(() => false) });

      await pass(5000);

      expect(mounted.result.current.note).toEqual({
        tone: 'pencil',
        message: 'Filed, but the list on screen does not show those days.',
      });
    });

    it('reads back the day and notes that the list may be incomplete when the reading was partial and the list shows it', async () => {
      readMessageMock.mockResolvedValueOnce(done('PARTIAL', ['2026-10-01']));
      const onEntriesFiled = vi.fn(() => true);
      const mounted = await filedAsMessage42({ onEntriesFiled });

      await pass(5000);

      expect(onEntriesFiled).toHaveBeenCalledWith(['2026-10-01']);
      expect(mounted.result.current.note).toEqual({
        tone: 'pencil',
        message: en.writeIn.listIncomplete,
      });
    });

    it('notes only that the list may be incomplete when the reading was partial and the list cannot show the day', async () => {
      readMessageMock.mockResolvedValueOnce(done('PARTIAL', ['2026-10-01']));
      const mounted = await filedAsMessage42({ onEntriesFiled: vi.fn(() => false) });

      await pass(5000);

      expect(mounted.result.current.note).toEqual({
        tone: 'pencil',
        message: en.writeIn.listIncomplete,
      });
    });

    it('returns the text with a note, and reads nothing back, when nothing to file was identified', async () => {
      readMessageMock.mockResolvedValueOnce(done('NOTHING_IDENTIFIED'));
      const onEntriesFiled = vi.fn(() => true);
      const mounted = await filedAsMessage42({ onEntriesFiled });

      await pass(5000);

      expect(mounted.result.current.reading).toBeNull();
      expect(mounted.result.current.text).toBe(FILED);
      expect(mounted.result.current.note).toEqual({
        tone: 'pencil',
        message: en.writeIn.nothingFound,
      });
      expect(onEntriesFiled).not.toHaveBeenCalled();
    });

    it('returns the text with a note that only spending is filed here when the text was a question', async () => {
      readMessageMock.mockResolvedValueOnce(done('ANSWERED'));
      const mounted = await filedAsMessage42();

      await pass(5000);

      expect(mounted.result.current.text).toBe(FILED);
      expect(mounted.result.current.note).toEqual({
        tone: 'pencil',
        message: en.writeIn.onlyQuestion,
      });
    });

    it.each([
      ['the reading failed', () => readMessageMock.mockResolvedValueOnce(done('FAILED'))],
      [
        'the ask is refused with 404',
        () => readMessageMock.mockRejectedValueOnce(new ApiError(404, 'no such message')),
      ],
    ])(
      'returns the text with a refusal that it could not be read when %s',
      async (_name, arrange) => {
        arrange();
        const mounted = await filedAsMessage42();

        await pass(5000);

        expect(mounted.result.current.text).toBe(FILED);
        expect(mounted.result.current.note).toEqual({
          tone: 'refusal',
          message: en.writeIn.couldNotBeRead,
        });
      },
    );

    it('reports an expired session when the ask is refused with 401', async () => {
      const sessionExpired = vi.fn();
      readMessageMock.mockRejectedValueOnce(new ApiError(401, 'no session'));
      await filedAsMessage42({ sessionExpired });

      await pass(5000);

      expect(sessionExpired).toHaveBeenCalledOnce();
    });

    it('stops asking once the hook unmounts', async () => {
      readMessageMock.mockResolvedValue(aWrittenMessage({ id: 42, state: 'READING' }));
      const mounted = await filedAsMessage42();
      await pass(5000);
      expect(readMessageMock).toHaveBeenCalledTimes(1);

      mounted.unmount();
      await pass(10000);

      expect(readMessageMock).toHaveBeenCalledTimes(1);
    });
  });
});
