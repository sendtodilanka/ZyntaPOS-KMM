import { useAuthStore } from '@/stores/auth-store';
import type { AdminRole } from '@/types/user';

// 41 atomic permissions — mirrors AdminPermissions.kt on the backend.
// Raised from 39 by G-004: config:tax_rates:read + config:tax_rates:write.
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
    // G-003: `inventory:read` is retained here (and on FINANCE / OPERATOR)
    // because the admin panel surfaces cross-store stock counts for
    // cost-of-goods-sold analysis only. Actual stock mutations happen in the
    // POS app per ADR-009. Revisit if COGS moves off the admin panel.
    'inventory:read', 'inventory:write', 'transfers:read',
    'customers:read',
    // G-004: Tax rates CRUD scopes (previously hidden behind "Authenticated").
    'config:tax_rates:read', 'config:tax_rates:write',
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
    'customers:read',
  ],
  FINANCE: [
    'dashboard:financial',
    'license:read', 'license:export',
    'reports:financial', 'reports:read', 'reports:export',
    // G-003: FINANCE keeps inventory:read for COGS analysis (see ADMIN row).
    'inventory:read',
    // G-004: Finance is the canonical role for tax configuration.
    'config:tax_rates:read', 'config:tax_rates:write',
  ],
  AUDITOR: [
    'license:read',
    'reports:read',
    'audit:read', 'audit:export',
    // G-004: AUDITOR can view tax rate configuration (read-only — mirrors
    // the read-only audit role mandate).
    'config:tax_rates:read',
  ],
  HELPDESK: [
    'dashboard:support',
    'license:read',
    'store:read',
    'diagnostics:read',
    'reports:support', 'reports:read',
    'tickets:read', 'tickets:create', 'tickets:update', 'tickets:assign',
    'tickets:close', 'tickets:comment',
    'customers:read',
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
