import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { substituteCatalogue } from '../testing/catalogue';
import { WriteInLine, type WriteInLineProps } from './WriteInLine';

/** The line under test, with the props a case says nothing about left inert. */
function line(props: Partial<WriteInLineProps> = {}) {
  return (
    <WriteInLine
      text=""
      onTextChange={vi.fn()}
      onFile={vi.fn()}
      held={false}
      reading={null}
      note={null}
      announcement={null}
      {...props}
    />
  );
}

function renderLine(props: Partial<WriteInLineProps> = {}) {
  return render(line(props));
}

/** A line that keeps what is typed into it, as the page's loop does. */
function TypingLine({
  initial,
  onTextChange,
  onFile,
}: {
  initial: string;
  onTextChange: (text: string) => void;
  onFile: () => void;
}) {
  const [text, setText] = useState(initial);
  return (
    <WriteInLine
      text={text}
      onTextChange={(next) => {
        onTextChange(next);
        setText(next);
      }}
      onFile={onFile}
      held={false}
      reading={null}
      note={null}
      announcement={null}
    />
  );
}

function field() {
  return screen.getByLabelText('Write what you spent');
}

describe('the write-in line', () => {
  describe('the field', () => {
    it('hands what the person types to onTextChange, and shows the placeholder while empty', async () => {
      const user = userEvent.setup();
      const onTextChange = vi.fn();
      render(<TypingLine initial="" onTextChange={onTextChange} onFile={vi.fn()} />);

      expect(field()).toHaveAttribute('placeholder', 'e.g. lunch 12.50, cinema 2×11');

      await user.type(field(), 'tea 3');

      expect(onTextChange).toHaveBeenLastCalledWith('tea 3');
    });

    it('files once on Enter, without a line break reaching onTextChange', async () => {
      const user = userEvent.setup();
      const onTextChange = vi.fn();
      const onFile = vi.fn();
      renderLine({ text: 'coffee 3.40', onTextChange, onFile });

      await user.type(field(), '{Enter}');

      expect(onFile).toHaveBeenCalledTimes(1);
      expect(onTextChange).not.toHaveBeenCalled();
    });

    it('files once when the file control is clicked', async () => {
      const user = userEvent.setup();
      const onFile = vi.fn();
      renderLine({ text: 'coffee 3.40', onFile });

      await user.click(screen.getByRole('button', { name: 'File' }));

      expect(onFile).toHaveBeenCalledTimes(1);
    });

    it('takes a line break on Shift+Enter without filing', async () => {
      const user = userEvent.setup();
      const onTextChange = vi.fn();
      const onFile = vi.fn();
      renderLine({ text: 'coffee 3.40', onTextChange, onFile });

      await user.type(field(), '{Shift>}{Enter}{/Shift}');

      expect(onTextChange).toHaveBeenCalledWith('coffee 3.40\n');
      expect(onFile).not.toHaveBeenCalled();
    });

    it('does not file on an Enter pressed while an input method is composing, and files on one after it ends', () => {
      const onFile = vi.fn();
      renderLine({ text: 'coffee 3.40', onFile });

      fireEvent.compositionStart(field());
      fireEvent.keyDown(field(), { key: 'Enter', isComposing: true });

      expect(onFile).not.toHaveBeenCalled();

      fireEvent.compositionEnd(field());
      fireEvent.keyDown(field(), { key: 'Enter' });

      expect(onFile).toHaveBeenCalledTimes(1);
    });

    it('holds at most 1000 characters, and never hands more to onTextChange', async () => {
      const user = userEvent.setup();
      const onTextChange = vi.fn();
      render(<TypingLine initial={'a'.repeat(999)} onTextChange={onTextChange} onFile={vi.fn()} />);

      await user.type(field(), 'bc', { initialSelectionStart: 999, initialSelectionEnd: 999 });

      expect(field()).toHaveValue('a'.repeat(999) + 'b');
      expect((field() as HTMLTextAreaElement).value).toHaveLength(1000);
      for (const [text] of onTextChange.mock.calls) {
        expect((text as string).length).toBeLessThanOrEqual(1000);
      }
    });

    it('disables the file control for a line of only spaces, and never files from a held line', async () => {
      const user = userEvent.setup();
      const onFile = vi.fn();
      const { rerender } = renderLine({ text: '   ', onFile });

      expect(screen.getByRole('button', { name: 'File' })).toBeDisabled();
      await user.type(field(), '{Enter}');
      expect(onFile).not.toHaveBeenCalled();

      rerender(line({ text: 'coffee 3.40', held: true, onFile }));

      expect(screen.getByRole('button', { name: 'File' })).toBeDisabled();
      await user.type(field(), '{Enter}');
      expect(onFile).not.toHaveBeenCalled();
    });

    it('turns read-only when held, keeps focus, and is described as a text being read', () => {
      const { rerender } = renderLine({ text: 'coffee 3.40' });
      field().focus();
      expect(field()).toHaveFocus();
      expect(field()).not.toHaveAttribute('readonly');

      rerender(line({ text: 'coffee 3.40', held: true }));

      expect(field()).toHaveAttribute('readonly');
      expect(field()).toHaveFocus();
      expect(field()).toHaveAccessibleDescription('A text is being read');
    });
  });

  describe('beneath the line', () => {
    it('stands a reading line named for the text being read, showing the reading mark', () => {
      renderLine({ held: true, reading: 'coffee 3.40, taxi home 14' });

      const reading = screen.getByLabelText('Reading your text: coffee 3.40, taxi home 14');
      expect(reading).toHaveTextContent('Reading…');
    });

    it('shows a pencil note without making it an alert, and carries it in the status region', () => {
      renderLine({ note: { tone: 'pencil', message: 'Nothing to file was found in this text.' } });

      expect(screen.getByText('Nothing to file was found in this text.')).toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      expect(screen.getByRole('status')).toHaveTextContent(
        'Nothing to file was found in this text.',
      );
    });

    it('shows a refusal as an alert, with no reading line standing', () => {
      renderLine({
        note: { tone: 'refusal', message: 'This text could not be read. Try filing it again.' },
      });

      expect(screen.getByRole('alert')).toHaveTextContent(
        'This text could not be read. Try filing it again.',
      );
      expect(screen.queryByLabelText(/^Reading your text/)).not.toBeInTheDocument();
    });

    it('carries the announcement in the status region when there is no note', () => {
      renderLine({ announcement: 'Filed. The new entries await a decision.' });

      expect(screen.getByRole('status')).toHaveTextContent(
        'Filed. The new entries await a decision.',
      );
    });

    it('reads the label, placeholder, file control, hint and reading mark from the catalogue', () => {
      substituteCatalogue();

      renderLine({ text: 'abc', held: true, reading: 'abc' });

      expect(screen.getByLabelText('‹Write what you spent›')).toHaveAttribute(
        'placeholder',
        '‹e.g. lunch 12.50, cinema 2×11›',
      );
      expect(screen.getByRole('button', { name: '‹File›' })).toBeInTheDocument();
      expect(screen.getByText('‹Enter to file · Shift+Enter for a new line›')).toBeInTheDocument();
      expect(screen.getByLabelText('‹Reading your text: abc›')).toHaveTextContent('‹Reading…›');
    });
  });
});
