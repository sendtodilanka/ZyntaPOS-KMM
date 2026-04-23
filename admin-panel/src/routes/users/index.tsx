import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useState, useEffect } from 'react';
import { Plus } from 'lucide-react';
import { UserTable } from '@/components/users/UserTable';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { UserCreateForm } from '@/components/users/UserCreateForm';
import { SearchInput } from '@/components/shared/SearchInput';
import { useAdminUsers } from '@/api/users';
import { useDebounce } from '@/hooks/use-debounce';
import { useAuth } from '@/hooks/use-auth';
import type { AdminUser, AdminRole, UserStatus } from '@/types/user';

// H-001: filter state persisted in URL. Back/forward and deep links
// preserve role + status + search instead of resetting on navigation.
// Defaults are `undefined` so empty query params don't end up in the URL.
interface UsersSearch {
  page?: number;
  q?: string;
  role?: AdminRole;
  status?: UserStatus;
}

const VALID_ROLES: AdminRole[] = ['ADMIN', 'OPERATOR', 'FINANCE', 'AUDITOR', 'HELPDESK'];
const VALID_STATUSES: UserStatus[] = ['ACTIVE', 'INACTIVE'];

export const Route = createFileRoute('/users/')({
  component: UsersPage,
  validateSearch: (raw: Record<string, unknown>): UsersSearch => {
    const page = typeof raw.page === 'number' && raw.page >= 0 ? raw.page : undefined;
    const q = typeof raw.q === 'string' && raw.q.length ? raw.q : undefined;
    const roleRaw = typeof raw.role === 'string' ? raw.role : '';
    const statusRaw = typeof raw.status === 'string' ? raw.status : '';
    return {
      page,
      q,
      role: (VALID_ROLES as string[]).includes(roleRaw) ? (roleRaw as AdminRole) : undefined,
      status: (VALID_STATUSES as string[]).includes(statusRaw) ? (statusRaw as UserStatus) : undefined,
    };
  },
});

function UsersPage() {
  const { hasPermission } = useAuth();
  const navigate = useNavigate({ from: Route.fullPath });
  const search = Route.useSearch();

  const page = search.page ?? 0;
  const searchText = search.q ?? '';
  const roleFilter = search.role ?? '';
  const statusFilter = search.status ?? '';

  const [searchInput, setSearchInput] = useState(searchText);
  const debouncedSearch = useDebounce(searchInput, 300);

  const [formOpen, setFormOpen] = useState(false);
  const [editUser, setEditUser] = useState<AdminUser | undefined>();

  // Flush debounced search back to URL once it settles.
  useEffect(() => {
    if (debouncedSearch !== searchText) {
      void navigate({
        search: (prev) => ({ ...prev, q: debouncedSearch || undefined, page: undefined }),
        replace: true,
      });
    }
  }, [debouncedSearch, searchText, navigate]);

  const { data, isLoading, isError, refetch } = useAdminUsers({
    page,
    size: 20,
    search: debouncedSearch || undefined,
    role: roleFilter || undefined,
    status: statusFilter || undefined,
  });

  const updateSearch = (patch: Partial<UsersSearch>) => {
    void navigate({
      search: (prev) => ({ ...prev, ...patch, page: undefined }),
      replace: true,
    });
  };

  const handleEdit = (user: AdminUser) => { setEditUser(user); setFormOpen(true); };
  const handleClose = () => { setFormOpen(false); setEditUser(undefined); };

  return (
    <div className="space-y-6">
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Users</h1>
          <p className="panel-subtitle">{data?.total ?? 0} admin users</p>
        </div>
        {hasPermission('users:write') && (
          <button
            onClick={() => { setEditUser(undefined); setFormOpen(true); }}
            className="flex items-center gap-2 px-4 py-2 bg-brand-700 hover:bg-brand-800 text-white text-sm font-medium rounded-lg transition-colors min-h-[44px]"
          >
            <Plus className="w-4 h-4" />
            <span>New User</span>
          </button>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <SearchInput
          value={searchInput}
          onChange={(v) => setSearchInput(v)}
          placeholder="Search users…"
          className="flex-1 min-w-[200px] max-w-sm"
        />
        <select
          aria-label="Filter by role"
          value={roleFilter}
          onChange={(e) => updateSearch({ role: (e.target.value as AdminRole) || undefined })}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[130px]"
        >
          <option value="">All Roles</option>
          <option value="ADMIN">Admin</option>
          <option value="OPERATOR">Operator</option>
          <option value="FINANCE">Finance</option>
          <option value="AUDITOR">Auditor</option>
          <option value="HELPDESK">Helpdesk</option>
        </select>
        <select
          aria-label="Filter by status"
          value={statusFilter}
          onChange={(e) => updateSearch({ status: (e.target.value as UserStatus) || undefined })}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[130px]"
        >
          <option value="">All Statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>

      {isError ? (
        <ErrorBanner message="Failed to load users." onRetry={() => refetch()} />
      ) : (
        <UserTable
          data={data?.data ?? []}
          isLoading={isLoading}
          page={page}
          totalPages={data?.totalPages ?? 1}
          total={data?.total ?? 0}
          onPageChange={(p) => {
            void navigate({
              search: (prev) => ({ ...prev, page: p > 0 ? p : undefined }),
              replace: true,
            });
          }}
          onEdit={handleEdit}
        />
      )}

      <UserCreateForm open={formOpen} onClose={handleClose} editUser={editUser} />
    </div>
  );
}
