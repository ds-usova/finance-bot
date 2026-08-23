import { Navigate, Route, Routes } from 'react-router';
import { RequireAuth } from './auth/RequireAuth';
import { AppShell } from './pages/AppShell';
import { ExpensesPage } from './pages/ExpensesPage';
import { LoginPage } from './pages/LoginPage';
import { SettingsPage } from './pages/SettingsPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth />}>
          <Route path="/" element={<ExpensesPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
