import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Check, Globe } from 'lucide-react';
import { useTimezoneStore, TIMEZONE_OPTIONS } from '@/stores/timezone-store';
import { tzOffsetLabel } from '@/lib/utils';
import { toast } from '@/stores/ui-store';

export const Route = createFileRoute('/settings/')({
  component: SettingsPage,
});

function SettingsPage() {
  const { timezone, setTimezone } = useTimezoneStore();
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState(timezone);

  const filtered = TIMEZONE_OPTIONS.filter(([id, label]) =>
    label.toLowerCase().includes(search.toLowerCase()) ||
    id.toLowerCase().includes(search.toLowerCase()),
  );

  const handleSave = () => {
    setTimezone(selected);
    toast.success('Preferences saved', `Timezone set to ${selected}`);
  };

  const isDirty = selected !== timezone;

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="panel-title">Preferences</h1>
        <p className="panel-subtitle">
          These settings apply to your browser session and are stored locally.
        </p>
      </div>

      {/* Timezone card */}
      <div className="bg-surface-card border border-surface-border rounded-xl p-6 space-y-4">
        <div className="flex items-center gap-2.5 mb-1">
          <Globe className="w-4 h-4 text-brand-400" />
          <h2 className="text-sm font-semibold text-slate-100">Display Timezone</h2>
        </div>
        <p className="text-xs text-slate-400">
          All timestamps in the admin panel (last login, audit log, reports, etc.) are
          displayed in this timezone. The underlying data is always stored in UTC.
        </p>

        {/* Current value pill */}
        <div className="flex items-center gap-2">
          <span className="text-xs text-slate-500">Current:</span>
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-brand-500/10 border border-brand-500/30 rounded-lg text-xs font-medium text-brand-400">
            {timezone}
            <span className="text-brand-300/70">{tzOffsetLabel(timezone)}</span>
          </span>
        </div>

        {/* Search */}
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search timezone…"
          className="w-full h-9 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-200 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
        />

        {/* Timezone list */}
        <div className="max-h-64 overflow-y-auto rounded-lg border border-surface-border divide-y divide-surface-border">
          {filtered.length === 0 && (
            <p className="px-4 py-3 text-sm text-slate-500">No timezones match "{search}"</p>
          )}
          {filtered.map(([id, label]) => {
            const isActive = id === selected;
            return (
              <button
                key={id}
                onClick={() => setSelected(id)}
                className={[
                  'w-full flex items-center justify-between px-4 py-2.5 text-left transition-colors min-h-[44px]',
                  isActive
                    ? 'bg-brand-500/10 text-brand-400'
                    : 'text-slate-300 hover:bg-surface-elevated hover:text-slate-100',
                ].join(' ')}
              >
                <div>
                  <span className="text-sm font-medium">{label}</span>
                  <span className="ml-2 text-xs text-slate-500 font-mono">{id}</span>
                </div>
                {isActive && <Check className="w-4 h-4 flex-shrink-0" />}
              </button>
            );
          })}
        </div>

        <div className="flex justify-end pt-1">
          <button
            onClick={handleSave}
            disabled={!isDirty}
            className="px-5 py-2 rounded-lg text-sm font-medium bg-brand-500 text-white hover:bg-brand-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors min-h-[40px]"
          >
            Save Preferences
          </button>
        </div>
      </div>
    </div>
  );
}
