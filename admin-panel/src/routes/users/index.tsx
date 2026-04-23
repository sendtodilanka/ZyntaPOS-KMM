import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Plus } from 'lucide-react';
import { UserTable } from '@/components/users/UserTable';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { UserCreateForm } from '@/components/users/UserCreateForm';
import { SearchInput } from '@/components/shared/SearchInput';
import { useAdminUsers } from '@/api/users';
import { useDebounce } from '@/hooks/use-debounce';
import { useAuth } from '@/hooks/use-auth';
import type { AdminUser, AdminRole, UserStatus } from '@/types/user';

export const Route = createFileRoute('/users/')({
  component: UsersPage,
});

function UsersPage() {
  const { hasPermission } = useAuth();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<AdminRole | ''>('');
  const [statusFilter, setStatusFilter] = useState<UserStatus | ''>('');
  const [formOpen, setFormOpen] = useState(false);
  const [editUser, setEditUser] = useState<AdminUser | undefined>();

  const debouncedSearch = useDebounce(search, 300);
  const { data, isLoading, isError, refetch } = useAdminUsers({
    page, size: 20,
    search: debouncedSearch || undefined,
    role: roleFilter || undefined,
    status: statusFilter || undefined,
  });

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
          value={search}
          onChange={(v) => { setSearch(v); setPage(0); }}
          placeholder="Search users…"
          className="flex-1 min-w-[200px] max-w-sm"
        />
        <select
          aria-label="Filter by role"
          value={roleFilter}
          onChange={(e) => { setRoleFilter(e.target.value as AdminRole | ''); setPage(0); }}
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
          onChange={(e) => { setStatusFilter(e.target.value as UserStatus | ''); setPage(0); }}
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
          onPageChange={setPage}
          onEdit={handleEdit}
        />
      )}

      <UserCreateForm open={formOpen} onClose={handleClose} editUser={editUser} />
    </div>
  );
}
