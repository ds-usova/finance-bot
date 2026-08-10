import { useTranslation } from 'react-i18next';
import { Button } from './ui/button';

export type ExpenseActionBarProps = {
  /** How many are ticked. Zero hides the action, never the row itself. */
  count: number;
  onAccept: () => void;
  /** The call is out, so the action is disabled. */
  busy: boolean;
};

// The row that always stands, whether or not the action is in it, holding the accept action only once
// something is ticked, naming how many in words and disabled while the call is out.
export function ExpenseActionBar({ count, onAccept, busy }: ExpenseActionBarProps) {
  const { t } = useTranslation();

  return (
    <div className="flex h-10 items-center justify-end">
      {count > 0 && (
        // Outlined rather than filled: the action stands beside the listing's own badges, and a second
        // accent-filled block there competes with them for the same attention.
        <Button variant="outline" onClick={onAccept} disabled={busy}>
          {t('listing.acceptAction', { count })}
        </Button>
      )}
    </div>
  );
}
