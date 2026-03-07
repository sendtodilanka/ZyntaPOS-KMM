import { useRouterState } from '@tanstack/react-router';
import { Menu, Bell } from 'lucide-react';
import { Breadcrumbs } from './Breadcrumbs';
import { UserMenu } from './UserMenu';
import { useUiStore } from '@/stores/ui-store';
import { useIsMobile } from '@/hooks/use-media-query';

export function Header() {
  const { setMobileSidebarOpen } = useUiStore();
  const isMobile = useIsMobile();
  const routerState = useRouterState();

  // Extract page title from pathname
  const pathname = routerState.location.pathname;
  const segments = pathname.split('/').filter(Boolean);
  const pageTitle = segments[0]
    ? segments[0].charAt(0).toUpperCase() + segments[0].slice(1)
    : 'Dashboard';

  return (
    <header className="flex h-14 items-center gap-3 border-b border-surface-border bg-surface-card px-4 lg:px-6 flex-shrink-0">
      {/* Mobile hamburger */}
      {isMobile && (
        <button
          onClick={() => setMobileSidebarOpen(true)}
          className="p-2 -ml-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center"
          aria-label="Open menu"
        >
          <Menu className="w-5 h-5" />
        </button>
      )}

      {/* Breadcrumbs or page title */}
      <div className="flex-1 min-w-0">
        {isMobile ? (
          <h1 className="text-base font-semibold text-slate-100 truncate">{pageTitle}</h1>
        ) : (
          <Breadcrumbs />
        )}
      </div>

      {/* Right actions */}
      <div className="flex items-center gap-1 sm:gap-2">
        <button
          className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center relative"
          aria-label="Notifications"
        >
          <Bell className="w-5 h-5" />
          <span className="absolute top-2.5 right-2.5 w-2 h-2 bg-red-500 rounded-full" />
        </button>
        <UserMenu />
      </div>
    </header>
  );
}
