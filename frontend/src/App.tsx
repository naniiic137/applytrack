import { lazy, Suspense, type ReactElement } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { AppLayout } from './components/AppLayout';
import { Spinner } from './components/Feedback';
import { AuthPage } from './pages/AuthPage';

// Route pages are split into their own chunks, so the login page does not download
// the board (dnd-kit) or the dashboard (Recharts, the biggest dependency).
const BoardPage = lazy(() => import('./pages/BoardPage').then((m) => ({ default: m.BoardPage })));
const ListPage = lazy(() => import('./pages/ListPage').then((m) => ({ default: m.ListPage })));
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const FollowUpsPage = lazy(() => import('./pages/FollowUpsPage').then((m) => ({ default: m.FollowUpsPage })));

function RequireAuth({ children }: { children: ReactElement }) {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return children;
}

function Page({ children }: { children: ReactElement }) {
  return <Suspense fallback={<Spinner />}>{children}</Suspense>;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/board" element={<Page><BoardPage /></Page>} />
        <Route path="/applications" element={<Page><ListPage /></Page>} />
        <Route path="/dashboard" element={<Page><DashboardPage /></Page>} />
        <Route path="/follow-ups" element={<Page><FollowUpsPage /></Page>} />
      </Route>
      <Route path="*" element={<Navigate to="/board" replace />} />
    </Routes>
  );
}
