import type { ReactNode } from 'react';

export type SheetProps = {
  children: ReactNode;
};

/** One passbook page laid on the cover: edge to edge on a phone, a bound page on the cloth from `sm` up. */
export function Sheet({ children }: SheetProps) {
  return (
    <div className="passbook-sheet mx-auto flex w-full max-w-6xl flex-1 flex-col px-4 pt-8 pb-6 sm:flex-none sm:rounded-sm sm:border sm:border-cover-edge sm:px-10 sm:pt-10 sm:pb-8">
      {children}
    </div>
  );
}
