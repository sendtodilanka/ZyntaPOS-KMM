import { LogOut, User, Shield } from 'lucide-react';
import { useAuth } from '@/hooks/use-auth';
import { cn } from '@/lib/utils';
import { useState, useRef, useEffect } from 'react';

const ROLE_COLORS = {
  ADMIN: 'text-red-400 bg-red-400/10',
  SUPPORT: 'text-yellow-400 bg-yellow-400/10',
  VIEWER: 'text-slate-400 bg-slate-400/10',
} as const;

export function UserMenu() {
  const { user, signOut } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  if (!user) return null;

  const initials = user.name
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-2 rounded-lg p-1.5 hover:bg-surface-elevated transition-colors min-h-[44px] min-w-[44px]"
        aria-label="User menu"
        aria-expanded={open}
      >
        <div className="w-8 h-8 rounded-full bg-brand-500/20 border border-brand-500/40 flex items-center justify-center text-xs font-semibold text-brand-400">
          {user.avatarUrl ? (
            <img src={user.avatarUrl} alt={user.name} className="w-full h-full rounded-full object-cover" />
          ) : (
            initials
          )}
        </div>
        <span className="text-sm text-slate-100 font-medium hidden sm:block max-w-[120px] truncate">
          {user.name}
        </span>
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-1 w-56 bg-surface-card border border-surface-border rounded-lg shadow-xl z-50 py-1">
          <div className="px-3 py-2.5 border-b border-surface-border">
            <p className="text-sm font-medium text-slate-100 truncate">{user.name}</p>
            <p className="text-xs text-slate-400 truncate">{user.email}</p>
            <span className={cn(
              'inline-flex items-center gap-1 mt-1.5 px-2 py-0.5 rounded text-[10px] font-semibold uppercase',
              ROLE_COLORS[user.role],
            )}>
              <Shield className="w-3 h-3" />
              {user.role.replace('_', ' ')}
            </span>
          </div>
          <div className="py-1">
            <button
              onClick={() => { setOpen(false); }}
              className="flex items-center gap-2.5 w-full px-3 py-2 text-sm text-slate-300 hover:bg-surface-elevated hover:text-slate-100 transition-colors min-h-[44px]"
            >
              <User className="w-4 h-4" />
              Profile
            </button>
            <button
              onClick={() => { setOpen(false); signOut(); }}
              className="flex items-center gap-2.5 w-full px-3 py-2 text-sm text-red-400 hover:bg-red-400/10 transition-colors min-h-[44px]"
            >
              <LogOut className="w-4 h-4" />
              Sign out
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
