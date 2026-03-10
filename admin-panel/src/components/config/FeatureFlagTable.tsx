import { useState } from 'react';
import { useFeatureFlags, useUpdateFeatureFlag } from '@/api/config';
import type { FeatureFlag } from '@/types/config';
import { cn } from '@/lib/utils';

function ToggleSwitch({ checked, onChange, disabled, label }: { checked: boolean; onChange: (v: boolean) => void; disabled?: boolean; label?: string }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      aria-label={label ?? (checked ? 'Disable' : 'Enable')}
      onClick={() => !disabled && onChange(!checked)}
      disabled={disabled}
      className={cn(
        'relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 focus:ring-offset-surface-card',
        checked ? 'bg-brand-500' : 'bg-slate-600',
        disabled && 'opacity-50 cursor-not-allowed',
      )}
    >
      <span
        className={cn(
          'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
          checked ? 'translate-x-6' : 'translate-x-1',
        )}
      />
    </button>
  );
}

function FlagRow({ flag }: { flag: FeatureFlag }) {
  const { mutate: updateFlag, isPending } = useUpdateFeatureFlag();
  const [optimisticEnabled, setOptimisticEnabled] = useState(flag.enabled);

  const handleChange = (enabled: boolean) => {
    setOptimisticEnabled(enabled);
    updateFlag({ key: flag.key, enabled }, {
      onError: () => setOptimisticEnabled(!enabled),
    });
  };

  return (
    <div className="flex items-start justify-between gap-4 py-4 border-b border-surface-border last:border-0">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-medium text-slate-100">{flag.name}</span>
          <span className="px-1.5 py-0.5 text-[10px] font-mono bg-surface-elevated rounded text-slate-400">
            {flag.key}
          </span>
          {flag.editionsAvailable.map((ed) => (
            <span key={ed} className="px-1.5 py-0.5 text-[10px] bg-brand-500/10 text-brand-400 rounded">
              {ed}
            </span>
          ))}
        </div>
        <p className="mt-0.5 text-xs text-slate-500">{flag.description}</p>
        <p className="mt-0.5 text-[10px] text-slate-500">
          Modified {new Date(flag.lastModified).toLocaleDateString()} by {flag.modifiedBy}
        </p>
      </div>
      <div className="flex-shrink-0 flex items-center min-h-[44px]">
        <ToggleSwitch
          checked={optimisticEnabled}
          onChange={handleChange}
          disabled={isPending}
          label={optimisticEnabled ? `Disable ${flag.name}` : `Enable ${flag.name}`}
        />
      </div>
    </div>
  );
}

export function FeatureFlagTable() {
  const { data: flags, isLoading, error } = useFeatureFlags();
  const [search, setSearch] = useState('');

  if (isLoading) {
    return (
      <div className="space-y-4">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="h-16 bg-surface-elevated rounded animate-pulse" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-8 text-slate-400">
        Failed to load feature flags
      </div>
    );
  }

  const filtered = (flags ?? []).filter(
    (f) =>
      f.name.toLowerCase().includes(search.toLowerCase()) ||
      f.key.toLowerCase().includes(search.toLowerCase()) ||
      f.category.toLowerCase().includes(search.toLowerCase()),
  );

  const byCategory = filtered.reduce<Record<string, FeatureFlag[]>>((acc, f) => {
    if (!acc[f.category]) acc[f.category] = [];
    acc[f.category].push(f);
    return acc;
  }, {});

  return (
    <div className="space-y-6">
      <input
        type="search"
        placeholder="Search flags..."
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        className="w-full h-10 px-3 bg-surface-elevated border border-surface-border rounded-lg text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
      />
      {Object.entries(byCategory).map(([category, items]) => (
        <div key={category} className="bg-surface-card border border-surface-border rounded-xl p-4">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-3">
            {category.replace(/_/g, ' ')}
          </p>
          <div>
            {items.map((flag) => (
              <FlagRow key={flag.key} flag={flag} />
            ))}
          </div>
        </div>
      ))}
      {filtered.length === 0 && (
        <div className="text-center py-8 text-slate-400">No flags match your search</div>
      )}
    </div>
  );
}
