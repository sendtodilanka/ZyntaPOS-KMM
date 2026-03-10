import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { KeyRound, Monitor, Globe, Clock } from 'lucide-react';
import { useCurrentUser, useChangePassword, useListSessions } from '@/api/auth';
import { useRevokeSessions } from '@/api/users';
import { useTimezone } from '@/hooks/use-timezone';

export const Route = createFileRoute('/settings/profile')({
  component: ProfileSettingsPage,
});

function ProfileSettingsPage() {
  const { data: user } = useCurrentUser();
  const changePassword = useChangePassword();
  const { data: sessions, refetch: refetchSessions } = useListSessions(user?.id);
  const revokeSessions = useRevokeSessions();
  const { formatDateTime } = useTimezone();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError(null);

    if (newPassword.length < 8) {
      setPasswordError('New password must be at least 8 characters.');
      return;
    }
    if (newPassword.length > 128) {
      setPasswordError('New password must be 128 characters or fewer.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match.');
      return;
    }

    await changePassword.mutateAsync({ currentPassword, newPassword });
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
  };

  const handleRevokeAll = () => {
    if (user) {
      revokeSessions.mutate(user.id, { onSuccess: () => refetchSessions() });
    }
  };

  return (
    <div className="p-6 space-y-8 max-w-2xl">
      <div>
        <h1 className="text-2xl font-bold text-slate-100">Profile & Security</h1>
        <p className="text-slate-400 mt-1">Manage your password and active sessions.</p>
      </div>

      {/* User info */}
      <section className="bg-surface-card border border-surface-border rounded-xl p-6">
        <div className="flex items-center gap-4">
          <div className="w-14 h-14 rounded-full bg-brand-500/20 flex items-center justify-center text-xl font-bold text-brand-400">
            {user?.name.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2) ?? '?'}
          </div>
          <div>
            <p className="text-slate-100 font-semibold text-lg">{user?.name}</p>
            <p className="text-slate-400 text-sm">{user?.email}</p>
            <p className="text-slate-500 text-xs mt-0.5 capitalize">{user?.role?.toLowerCase()}</p>
          </div>
        </div>
      </section>

      {/* Change password */}
      <section className="bg-surface-card border border-surface-border rounded-xl p-6">
        <div className="flex items-center gap-2 mb-5">
          <KeyRound className="w-4 h-4 text-brand-400" />
          <h2 className="text-slate-200 font-semibold">Change Password</h2>
        </div>

        <form onSubmit={handleChangePassword} className="space-y-4">
          <div>
            <label className="block text-slate-400 text-sm mb-1.5">Current password</label>
            <input
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
              autoComplete="current-password"
              className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2.5 text-slate-100 text-sm focus:outline-none focus:border-brand-500"
            />
          </div>
          <div>
            <label className="block text-slate-400 text-sm mb-1.5">New password</label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
              minLength={8}
              maxLength={128}
              autoComplete="new-password"
              className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2.5 text-slate-100 text-sm focus:outline-none focus:border-brand-500"
            />
          </div>
          <div>
            <label className="block text-slate-400 text-sm mb-1.5">Confirm new password</label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              autoComplete="new-password"
              className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2.5 text-slate-100 text-sm focus:outline-none focus:border-brand-500"
            />
          </div>
          {passwordError && (
            <p className="text-red-400 text-sm">{passwordError}</p>
          )}
          <button
            type="submit"
            disabled={changePassword.isPending}
            className="px-4 py-2 rounded-lg bg-brand-700 hover:bg-brand-800 text-white text-sm font-medium disabled:opacity-50 transition-colors"
          >
            {changePassword.isPending ? 'Changing…' : 'Change Password'}
          </button>
        </form>
      </section>

      {/* Active sessions */}
      <section className="bg-surface-card border border-surface-border rounded-xl p-6">
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-2">
            <Monitor className="w-4 h-4 text-brand-400" />
            <h2 className="text-slate-200 font-semibold">Active Sessions</h2>
          </div>
          <button
            onClick={handleRevokeAll}
            disabled={revokeSessions.isPending || !sessions?.length}
            className="px-3 py-1.5 rounded-lg bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 text-red-400 text-xs font-medium disabled:opacity-40 transition-colors"
          >
            Revoke All
          </button>
        </div>

        {!sessions || sessions.length === 0 ? (
          <p className="text-slate-500 text-sm">No active sessions.</p>
        ) : (
          <ul className="space-y-3">
            {sessions.map((session) => (
              <li key={session.id} className="flex items-start gap-3 p-3 bg-surface-elevated rounded-lg border border-surface-border">
                <Monitor className="w-4 h-4 text-slate-400 mt-0.5 flex-shrink-0" />
                <div className="min-w-0 flex-1">
                  <p className="text-slate-300 text-sm font-medium truncate">
                    {session.userAgent ?? 'Unknown browser'}
                  </p>
                  <div className="flex items-center gap-3 mt-0.5">
                    {session.ipAddress && (
                      <span className="flex items-center gap-1 text-slate-500 text-xs">
                        <Globe className="w-3 h-3" /> {session.ipAddress}
                      </span>
                    )}
                    <span className="flex items-center gap-1 text-slate-500 text-xs">
                      <Clock className="w-3 h-3" /> Started {formatDateTime(session.createdAt)}
                    </span>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
