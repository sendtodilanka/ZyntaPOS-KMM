import { createRootRouteWithContext, Outlet, useNavigate, useRouterState } from '@tanstack/react-router';
import type { QueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Loader2 } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { useCurrentUser } from '@/api/auth';
import { useAuth } from '@/hooks/use-auth';

interface RouterContext {
  queryClient: QueryClient;
}

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
});

function RootLayout() {
  const navigate = useNavigate();
  const routerState = useRouterState();
  const isLoginPage = routerState.location.pathname.startsWith('/login');

  // Hydrate auth state on every page load via GET /admin/auth/me
  const { isLoading: queryLoading } = useCurrentUser();
  const { isAuthenticated, isLoading: storeLoading } = useAuth();

  const loading = queryLoading || storeLoading;

  useEffect(() => {
    if (loading) return;
    if (!isAuthenticated && !isLoginPage) {
      navigate({ to: '/login' });
    }
    if (isAuthenticated && isLoginPage) {
      navigate({ to: '/' });
    }
  }, [isAuthenticated, isLoginPage, loading, navigate]);

  // Full-screen spinner while /me is resolving
  if (loading && !isLoginPage) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
      </div>
    );
  }

  // Login page renders without AppShell (no sidebar/header)
  if (isLoginPage) {
    return <Outlet />;
  }

  // Not yet authenticated — blank while redirect fires
  if (!isAuthenticated) {
    return null;
  }

  return <AppShell />;
}
