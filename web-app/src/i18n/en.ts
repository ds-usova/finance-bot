/** The English catalogue. Every user-visible string the redesigned surfaces show lives here, namespaced by
 * the surface that reads it. `i18next.d.ts` derives the key union from this file's shape. */
export const en = {
  shell: {
    productName: 'Finance Bot',
    themeToggle: 'Toggle color theme',
    signOut: 'Sign out',
  },
  listing: {
    today: 'Today',
    yesterday: 'Yesterday',
    entryCount_one: '{{count}} entry',
    entryCount_other: '{{count}} entries',
    awaitingCount_one: '{{count}} entry awaits a decision',
    awaitingCount_other: '{{count}} entries await a decision',
    statusPending: 'Pending',
    empty: 'No expenses to show.',
  },
  filters: {
    title: 'Filters',
    grouping: 'Grouping',
    category: 'Category',
    status: 'Status',
    period: 'Recorded period',
    periodAny: 'Any time',
    periodClear: 'Clear the recorded period',
    presetThisMonth: 'This month',
    presetLastThirtyDays: 'Last 30 days',
    presetThisYear: 'This year',
    presetAllTime: 'All time',
    reset: 'Reset filters',
    all: 'All',
    statusRecorded: 'Recorded',
    statusPending: 'Pending',
  },
  paging: {
    previous: 'Previous',
    next: 'Next',
    range: 'Showing {{from}}–{{to}} of {{total}}.',
  },
  signIn: {
    invitation: 'Sign in with the Telegram account you use for the bot.',
    refused: 'That sign-in was not accepted. Please try again.',
  },
} as const;
