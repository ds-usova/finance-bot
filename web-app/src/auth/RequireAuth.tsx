import { Navigate, Outlet } from 'react-router';
import { useAuth } from './useAuth';

/** Renders nothing while the session is still being read, so a reload never flashes the sign-in page. */
export function RequireAuth() {
  const { status } = useAuth();

  if (status === 'loading') {
    return <p role="status">Checking your session…</p>;
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
