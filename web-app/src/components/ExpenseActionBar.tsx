export type ExpenseActionBarProps = {
  /** How many are ticked. Zero hides the action, never the row itself (D30). */
  count: number;
  onAccept: () => void;
  /** The call is out, so the action is disabled. */
  busy: boolean;
};

// Stub: the row that always stands, whether or not the action is in it (D30), holding the accept action only
// once something is ticked, naming how many in words and disabled while the call is out. RU04 covers the
// behaviour; this placeholder always renders the empty row.
export function ExpenseActionBar({ count, onAccept, busy }: ExpenseActionBarProps) {
  void count;
  void onAccept;
  void busy;
  return <div className="flex h-10 items-center justify-end" />;
}
