import { createRootRouteWithContext, Outlet, useNavigate, useRouterState } from '@tanstack/react-router';
import type { QueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Loader2 } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { useCurrentUser, useAdminStatus } from '@/api/auth';
import { useUiStore } from '@/stores/ui-store';

interface RouterContext {
  queryClient: QueryClient;
}

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
});

function RootLayout() {
  const navigate = useNavigate();
  const routerState = useRouterState();
  const pathname = routerState.location.pathname;
  const theme = useUiStore((s) => s.theme);

  // Keep <html> class in sync with persisted theme preference
  useEffect(() => {
    const html = document.documentElement;
    if (theme === 'dark') {
      html.classList.add('dark');
    } else {
      html.classList.remove('dark');
    }
  }, [theme]);
  const isLoginPage = pathname.startsWith('/login');
  const isSetupPage = pathname.startsWith('/setup');
  const isPublicPage = isLoginPage || isSetupPage;

  // Check if bootstrap is needed (first-run)
  const { data: statusData, isLoading: statusLoading } = useAdminStatus();

  // Hydrate auth state on every page load via GET /admin/auth/me
  // S1-7: Only use query loading — removed storeLoading (Zustand isLoading removed)
  // Derive isAuthenticated from query data directly (not from Zustand store).
  // useCurrentUser's useEffect syncs query.data → Zustand asynchronously, but
  // the auth guard useEffect below captures isAuthenticated during render. If we
  // read from the store, the guard sees the stale `null` value and redirects to
  // /login before the sync effect fires. Using query data avoids this race.
  const { isLoading: queryLoading, data: currentUser } = useCurrentUser();
  const isAuthenticated = !!currentUser;

  const loading = statusLoading || queryLoading;

  useEffect(() => {
    if (loading) return;

    // First-run: redirect to setup regardless of current page
    if (statusData?.needsBootstrap && !isSetupPage) {
      navigate({ to: '/setup' });
      return;
    }

    // Bootstrap done but on setup page — go to login
    if (!statusData?.needsBootstrap && isSetupPage) {
      navigate({ to: '/login' });
      return;
    }

    if (!isAuthenticated && !isPublicPage) {
      navigate({ to: '/login' });
    }
    if (isAuthenticated && isPublicPage) {
      navigate({ to: '/' });
    }
  }, [isAuthenticated, isPublicPage, isSetupPage, loading, navigate, statusData]);

  // S1-8: Show spinner on public pages while statusLoading (prevents flash before bootstrap redirect)
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
      </div>
    );
  }

  // Public pages (login / setup) render without AppShell
  if (isPublicPage) {
    return <Outlet />;
  }

  // Not yet authenticated — blank while redirect fires
  if (!isAuthenticated) {
    return null;
  }

  return <AppShell />;
}
