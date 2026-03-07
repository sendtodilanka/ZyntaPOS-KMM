import { useState } from 'react';
import { StoreHealthCard } from './StoreHealthCard';
import { StoreConfigForm } from './StoreConfigForm';
import { useStoreHealth } from '@/api/stores';
import { cn } from '@/lib/utils';
import type { Store, StoreConfig } from '@/types/store';

type Tab = 'overview' | 'config' | 'sync';

interface StoreDetailPanelProps {
  store: Store;
  config?: StoreConfig;
}

const TABS: { id: Tab; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'config', label: 'Configuration' },
  { id: 'sync', label: 'Sync' },
];

export function StoreDetailPanel({ store, config }: StoreDetailPanelProps) {
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const { data: health, isLoading: healthLoading } = useStoreHealth(store.id);

  return (
    <div className="space-y-4">
      {/* Tabs */}
      <div className="flex gap-1 border-b border-surface-border overflow-x-auto">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={cn(
              'px-4 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap min-h-[44px]',
              activeTab === tab.id
                ? 'border-brand-400 text-brand-400'
                : 'border-transparent text-slate-400 hover:text-slate-100',
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {activeTab === 'overview' && (
        <StoreHealthCard health={health} isLoading={healthLoading} />
      )}

      {activeTab === 'config' && (
        <div className="panel-card">
          <h3 className="panel-title text-base mb-4">Store Configuration</h3>
          <StoreConfigForm storeId={store.id} config={config} />
        </div>
      )}

      {activeTab === 'sync' && (
        <div className="panel-card">
          <h3 className="panel-title text-base mb-4">Sync Status</h3>
          <div className="space-y-3">
            {[
              { label: 'Queue Depth', value: health?.syncQueueDepth ?? '—' },
              { label: 'Last Sync', value: store.lastSyncAt ? new Date(store.lastSyncAt).toLocaleString() : 'Never' },
            ].map(({ label, value }) => (
              <div key={label} className="flex items-center justify-between py-2 border-b border-surface-border last:border-0">
                <span className="text-sm text-slate-400">{label}</span>
                <span className="text-sm font-medium text-slate-200">{String(value)}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
