import { Alert } from './ui/alert';

export type ErrorBannerProps = {
  message: string;
};

export function ErrorBanner({ message }: ErrorBannerProps) {
  return <Alert>{message}</Alert>;
}
