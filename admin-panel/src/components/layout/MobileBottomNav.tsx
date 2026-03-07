import { Link, useRouterState } from '@tanstack/react-router';
import { LayoutDashboard, Key, Store, Activity, MoreHorizontal } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useUiStore } from '@/stores/ui-store';

const BOTTOM_NAV_ITEMS = [
  { label: 'Dashboard', icon: LayoutDashboard, href: '/' },
  { label: 'Licenses', icon: Key, href: '/licenses' },
  { label: 'Stores', icon: Store, href: '/stores' },
  { label: 'Health', icon: Activity, href: '/health' },
];

export function MobileBottomNav() {
  const routerState = useRouterState();
  const currentPath = routerState.location.pathname;
  const { setMobileSidebarOpen } = useUiStore();

  const isActive = (href: string) => {
    if (href === '/') return currentPath === '/';
    return currentPath.startsWith(href);
  };

  return (
    <nav className="fixed bottom-0 inset-x-0 z-30 bg-surface-card border-t border-surface-border">
      <div className="flex items-stretch">
        {BOTTOM_NAV_ITEMS.map((item) => {
          const active = isActive(item.href);
          return (
            <Link
              key={item.href}
              to={item.href}
              className={cn(
                'flex flex-1 flex-col items-center justify-center gap-1 py-2 min-h-[56px] text-xs font-medium transition-colors',
                active ? 'text-brand-400' : 'text-slate-400',
              )}
            >
              <item.icon className={cn('w-5 h-5', active && 'text-brand-400')} />
              <span className="text-[10px]">{item.label}</span>
            </Link>
          );
        })}

        {/* More button — opens full sidebar */}
        <button
          onClick={() => setMobileSidebarOpen(true)}
          className="flex flex-1 flex-col items-center justify-center gap-1 py-2 min-h-[56px] text-xs font-medium text-slate-400 active:bg-surface-elevated"
        >
          <MoreHorizontal className="w-5 h-5" />
          <span className="text-[10px]">More</span>
        </button>
      </div>
    </nav>
  );
}
