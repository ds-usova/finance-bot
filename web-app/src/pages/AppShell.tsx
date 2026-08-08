import { Outlet } from 'react-router';

// The layout element every route renders inside. Reads the session from the context to decide whether the
// sign-out control is shown, and carries the theme control — neither exists yet, so only the frame is here.
export function AppShell() {
  return (
    <main>
      <Outlet />
    </main>
  );
}
