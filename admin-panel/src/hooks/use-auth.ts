import { useAuthStore } from '@/stores/auth-store';
import type { AdminRole } from '@/types/user';

// 39 atomic permissions — mirrors AdminPermissions.kt on the backend
const PERMISSIONS: Record<AdminRole, string[]> = {
  ADMIN: [
    'dashboard:ops', 'dashboard:financial', 'dashboard:support',
    'license:read', 'license:write', 'license:revoke', 'license:export',
    'store:read', 'store:sync:manage', 'store:config:read',
    'diagnostics:access', 'diagnostics:read', 'config:push',
    'reports:financial', 'reports:operational', 'reports:support', 'reports:read', 'reports:export',
    'alerts:read', 'alerts:acknowledge', 'alerts:configure',
    'audit:read', 'audit:export',
    'tickets:read', 'tickets:create', 'tickets:update', 'tickets:assign',
    'tickets:resolve', 'tickets:close', 'tickets:comment',
    'users:read', 'users:write', 'users:deactivate', 'users:sessions:revoke',
    'system:settings', 'system:health', 'system:backup',
    'email:settings', 'email:logs',
    'inventory:read', 'transfers:read',
  ],
  OPERATOR: [
    'dashboard:ops', 'dashboard:support',
    'license:read',
    'store:read', 'store:sync:manage', 'store:config:read',
    'diagnostics:access', 'diagnostics:read',
    'reports:operational', 'reports:support', 'reports:read',
    'alerts:read', 'alerts:acknowledge',
    'tickets:read', 'tickets:create', 'tickets:update', 'tickets:assign',
    'tickets:resolve', 'tickets:close', 'tickets:comment',
    'system:health',
    'email:logs',
    'inventory:read', 'transfers:read',
  ],
  FINANCE: [
    'dashboard:financial',
    'license:read', 'license:export',
    'reports:financial', 'reports:read', 'reports:export',
    'inventory:read',
  ],
  AUDITOR: [
    'license:read',
    'reports:read',
    'audit:read', 'audit:export',
  ],
  HELPDESK: [
    'dashboard:support',
    'license:read',
    'store:read',
    'diagnostics:read',
    'reports:support', 'reports:read',
    'tickets:read', 'tickets:create', 'tickets:update', 'tickets:assign',
    'tickets:close', 'tickets:comment',
  ],
};

// S1-7: isLoading removed from store — query loading state used in __root.tsx instead
export function useAuth() {
  const { user } = useAuthStore();

  const hasPermission = (permission: string): boolean => {
    if (!user) return false;
    return PERMISSIONS[user.role]?.includes(permission) ?? false;
  };

  return {
    user,
    isAuthenticated: !!user,
    hasPermission,
    isAdmin: user?.role === 'ADMIN',
  };
}
