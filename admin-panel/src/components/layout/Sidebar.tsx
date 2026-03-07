import { Link, useRouterState } from '@tanstack/react-router';
import {
  LayoutDashboard, Key, Store, Users, ClipboardList,
  RefreshCw, Settings2, BarChart3, Activity, Bell,
  ChevronLeft, ChevronRight, X,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useUiStore } from '@/stores/ui-store';
import { useIsDesktop } from '@/hooks/use-media-query';

interface NavItem {
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  href: string;
  permission?: string;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Overview',
    items: [
      { label: 'Dashboard', icon: LayoutDashboard, href: '/' },
    ],
  },
  {
    label: 'Management',
    items: [
      { label: 'Licenses', icon: Key, href: '/licenses', permission: 'license:read' },
      { label: 'Stores', icon: Store, href: '/stores', permission: 'store:read' },
      { label: 'Users', icon: Users, href: '/users', permission: 'user:read' },
    ],
  },
  {
    label: 'Monitoring',
    items: [
      { label: 'Sync', icon: RefreshCw, href: '/sync', permission: 'sync:read' },
      { label: 'Health', icon: Activity, href: '/health', permission: 'health:read' },
      { label: 'Alerts', icon: Bell, href: '/alerts', permission: 'alerts:read' },
    ],
  },
  {
    label: 'Intelligence',
    items: [
      { label: 'Audit Log', icon: ClipboardList, href: '/audit', permission: 'audit:read' },
      { label: 'Reports', icon: BarChart3, href: '/reports', permission: 'reports:read' },
      { label: 'Config', icon: Settings2, href: '/config', permission: 'config:read' },
    ],
  },
];

interface SidebarProps {
  mobile?: boolean;
}

export function Sidebar({ mobile = false }: SidebarProps) {
  const { sidebarCollapsed, toggleSidebar, setMobileSidebarOpen } = useUiStore();
  const isDesktop = useIsDesktop();
  const routerState = useRouterState();
  const currentPath = routerState.location.pathname;

  const collapsed = !mobile && sidebarCollapsed && isDesktop;

  const isActive = (href: string) => {
    if (href === '/') return currentPath === '/';
    return currentPath.startsWith(href);
  };

  return (
    <aside
      className={cn(
        'flex flex-col bg-surface-card border-r border-surface-border transition-all duration-200 h-full',
        collapsed ? 'w-14' : 'w-60',
        mobile && 'w-72',
      )}
    >
      {/* Logo */}
      <div className={cn(
        'flex items-center border-b border-surface-border',
        collapsed ? 'h-16 justify-center px-0' : 'h-16 px-4 gap-3',
        mobile && 'px-4 gap-3 justify-between',
      )}>
        {!collapsed && (
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="w-8 h-8 rounded-lg bg-brand-500 flex items-center justify-center flex-shrink-0">
              <span className="text-white font-bold text-sm">Z</span>
            </div>
            <span className="font-semibold text-slate-100 text-sm truncate">ZyntaPOS Admin</span>
          </div>
        )}
        {collapsed && (
          <div className="w-8 h-8 rounded-lg bg-brand-500 flex items-center justify-center">
            <span className="text-white font-bold text-sm">Z</span>
          </div>
        )}
        {mobile && (
          <button
            onClick={() => setMobileSidebarOpen(false)}
            className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center"
            aria-label="Close menu"
          >
            <X className="w-5 h-5" />
          </button>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto py-3 px-2 space-y-0.5">
        {NAV_GROUPS.map((group) => (
          <div key={group.label} className="mb-4">
            {!collapsed && (
              <p className="px-3 py-1 text-[10px] font-semibold uppercase tracking-wider text-slate-500 mb-1">
                {group.label}
              </p>
            )}
            {group.items.map((item) => {
              const active = isActive(item.href);
              return (
                <Link
                  key={item.href}
                  to={item.href}
                  className={cn(
                    'flex items-center gap-3 rounded-lg px-3 transition-colors min-h-[44px]',
                    collapsed ? 'justify-center px-0 w-full' : 'px-3',
                    active
                      ? 'bg-brand-500/15 text-brand-400'
                      : 'text-slate-400 hover:bg-surface-elevated hover:text-slate-100',
                  )}
                  title={collapsed ? item.label : undefined}
                >
                  <item.icon className={cn('flex-shrink-0', collapsed ? 'w-5 h-5' : 'w-4 h-4')} />
                  {!collapsed && (
                    <span className="text-sm font-medium truncate">{item.label}</span>
                  )}
                  {active && !collapsed && (
                    <span className="ml-auto w-1.5 h-1.5 rounded-full bg-brand-400" />
                  )}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      {/* Collapse toggle (desktop only) */}
      {!mobile && isDesktop && (
        <div className="border-t border-surface-border p-2">
          <button
            onClick={toggleSidebar}
            className={cn(
              'flex items-center justify-center w-full rounded-lg p-2.5 text-slate-400 hover:bg-surface-elevated hover:text-slate-100 transition-colors min-h-[44px]',
            )}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? (
              <ChevronRight className="w-4 h-4" />
            ) : (
              <div className="flex items-center gap-2 text-xs">
                <ChevronLeft className="w-4 h-4" />
                <span>Collapse</span>
              </div>
            )}
          </button>
        </div>
      )}
    </aside>
  );
}
