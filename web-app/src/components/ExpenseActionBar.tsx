import { Stamp } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from './ui/button';

export type ExpenseActionBarProps = {
  /** How many are ticked. Zero hides the action, never the row itself. */
  count: number;
  onAccept: () => void;
  /** The call is out, so the action is disabled. */
  busy: boolean;
};

// The teller's stamp, docked under the thumb once something is ticked: the row always stands, holding the
// action only while there is something to post, naming how many in words and disabled while the call is out.
export function ExpenseActionBar({ count, onAccept, busy }: ExpenseActionBarProps) {
  const { t } = useTranslation();

  return (
    <div className="pointer-events-none sticky bottom-0 z-20 -mx-4 flex justify-end px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:-mx-8 sm:px-8">
      {count > 0 && (
        <Button
          variant="stamp"
          size="key"
          onClick={onAccept}
          disabled={busy}
          className="pointer-events-auto w-full sm:w-auto"
        >
          <Stamp aria-hidden="true" className="h-[18px] w-[18px]" />
          {t('listing.acceptAction', { count })}
        </Button>
      )}
    </div>
  );
}
