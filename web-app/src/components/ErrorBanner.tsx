export type ErrorBannerProps = {
  message: string;
};

export function ErrorBanner({ message }: ErrorBannerProps) {
  return (
    <p className="error" role="alert">
      {message}
    </p>
  );
}
