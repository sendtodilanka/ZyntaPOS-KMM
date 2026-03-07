import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Settings2, Flag, DollarSign } from 'lucide-react';
import { FeatureFlagTable } from '@/components/config/FeatureFlagTable';
import { TaxRateEditor } from '@/components/config/TaxRateEditor';
import { ConfigEditor } from '@/components/config/ConfigEditor';
import { cn } from '@/lib/utils';

export const Route = createFileRoute('/config/')({
  component: ConfigPage,
});

type TabId = 'flags' | 'tax' | 'system';

const TABS: { id: TabId; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
  { id: 'flags', label: 'Feature Flags', icon: Flag },
  { id: 'tax', label: 'Tax Rates', icon: DollarSign },
  { id: 'system', label: 'System', icon: Settings2 },
];

function ConfigPage() {
  const [activeTab, setActiveTab] = useState<TabId>('flags');
  return (
    <div className="p-4 md:p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-100">Configuration</h1>
        <p className="text-sm text-slate-400 mt-1">Manage feature flags, tax rates, and system settings</p>
      </div>
      <div className="flex gap-1 bg-surface-elevated rounded-xl p-1 overflow-x-auto">
        {TABS.map((tab) => (
          <button key={tab.id} onClick={() => setActiveTab(tab.id)} className={cn('flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors whitespace-nowrap min-h-[44px] flex-1 justify-center', activeTab===tab.id?'bg-surface-card text-slate-100 shadow-sm':'text-slate-400 hover:text-slate-100')}>
            <tab.icon className="w-4 h-4"/>{tab.label}
          </button>
        ))}
      </div>
      {activeTab==='flags' && <FeatureFlagTable/>}
      {activeTab==='tax' && <TaxRateEditor/>}
      {activeTab==='system' && <ConfigEditor/>}
    </div>
  );
}
