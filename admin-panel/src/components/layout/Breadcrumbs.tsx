import { Link, useRouterState } from '@tanstack/react-router';
import { ChevronRight, Home } from 'lucide-react';

const ROUTE_LABELS: Record<string, string> = {
  licenses: 'Licenses',
  stores: 'Stores',
  users: 'Users',
  audit: 'Audit Log',
  sync: 'Sync',
  config: 'Configuration',
  reports: 'Reports',
  health: 'Health',
  alerts: 'Alerts',
};

export function Breadcrumbs() {
  const routerState = useRouterState();
  const pathname = routerState.location.pathname;
  const segments = pathname.split('/').filter(Boolean);

  if (segments.length === 0) {
    return (
      <div className="flex items-center gap-1 text-sm">
        <Home className="w-4 h-4 text-slate-400" />
        <span className="text-slate-100 font-medium">Dashboard</span>
      </div>
    );
  }

  return (
    <nav className="flex items-center gap-1 text-sm min-w-0" aria-label="Breadcrumb">
      <Link to="/" aria-label="Home" className="text-slate-400 hover:text-slate-100 transition-colors flex-shrink-0">
        <Home className="w-4 h-4" aria-hidden="true" />
      </Link>
      {segments.map((segment, index) => {
        const isLast = index === segments.length - 1;
        const href = '/' + segments.slice(0, index + 1).join('/');
        const label = ROUTE_LABELS[segment] ?? (
          segment.length > 12 ? `${segment.slice(0, 10)}…` : segment
        );

        return (
          <div key={href} className="flex items-center gap-1 min-w-0">
            <ChevronRight className="w-3.5 h-3.5 text-slate-500 flex-shrink-0" aria-hidden="true" />
            {isLast ? (
              <span className="text-slate-100 font-medium truncate">{label}</span>
            ) : (
              <Link
                to={href}
                className="text-slate-400 hover:text-slate-100 transition-colors truncate"
              >
                {label}
              </Link>
            )}
          </div>
        );
      })}
    </nav>
  );
}
