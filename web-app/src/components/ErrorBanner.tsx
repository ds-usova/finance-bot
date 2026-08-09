import { AlertTriangle } from 'lucide-react';
import { Alert } from './ui/alert';

export type ErrorBannerProps = {
  message: string;
};

export function ErrorBanner({ message }: ErrorBannerProps) {
  return (
    <Alert className="border-danger/25 bg-danger-surface text-danger">
      <AlertTriangle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
      <span>{message}</span>
    </Alert>
  );
}
