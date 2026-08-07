import { BrowserRouter } from 'react-router';
import { AuthProvider } from './auth/AuthProvider';
import { AppRoutes } from './routes';

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}
