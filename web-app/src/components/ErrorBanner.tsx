import { CircleAlert } from 'lucide-react';
import { Alert } from './ui/alert';

export type ErrorBannerProps = {
  message: string;
};

// A refusal, written in red ink between two red rules.
export function ErrorBanner({ message }: ErrorBannerProps) {
  return (
    <Alert className="border-refusal/45 text-refusal">
      <CircleAlert aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
      <span className="font-medium">{message}</span>
    </Alert>
  );
}
