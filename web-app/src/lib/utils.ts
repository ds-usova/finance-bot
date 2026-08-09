import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** Merges class names, keeping the last conflicting Tailwind utility rather than emitting both. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
