import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Settings2, Flag, Percent } from 'lucide-react';
import { FeatureFlagTable } from '@/components/config/FeatureFlagTable';
import { ConfigEditor } from '@/components/config/ConfigEditor';
import { TaxRatesTable } from '@/components/config/TaxRatesTable';
import { useAuth } from '@/hooks/use-auth';
import { cn } from '@/lib/utils';

export const Route = createFileRoute('/config/')({
  component: ConfigPage,
});

type TabId = 'flags' | 'tax-rates' | 'system';

function ConfigPage() {
  const { hasPermission } = useAuth();

  // G-004: the Tax Rates tab is only shown to roles that have
  // `config:tax_rates:read` (ADMIN, FINANCE, AUDITOR). HELPDESK / OPERATOR
  // used to see it by virtue of the backend check being a bare
  // `Authenticated`; now the UI matches the tightened backend policy.
  const canReadTaxRates = hasPermission('config:tax_rates:read');

  const tabs: { id: TabId; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
    { id: 'flags', label: 'Feature Flags', icon: Flag },
    ...(canReadTaxRates ? [{ id: 'tax-rates' as const, label: 'Tax Rates', icon: Percent }] : []),
    { id: 'system', label: 'System', icon: Settings2 },
  ];

  const [activeTab, setActiveTab] = useState<TabId>('flags');

  return (
    <div className="p-4 md:p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-100">Configuration</h1>
        <p className="text-sm text-slate-400 mt-1">Manage feature flags, tax rates, and system settings</p>
      </div>
      <div className="flex gap-1 bg-surface-elevated rounded-xl p-1 overflow-x-auto">
        {tabs.map((tab) => (
          <button key={tab.id} onClick={() => setActiveTab(tab.id)} className={cn('flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors whitespace-nowrap min-h-[44px] flex-1 justify-center', activeTab===tab.id?'bg-surface-card text-slate-100 shadow-sm':'text-slate-400 hover:text-slate-100')}>
            <tab.icon className="w-4 h-4"/>{tab.label}
          </button>
        ))}
      </div>
      {activeTab==='flags' && <FeatureFlagTable/>}
      {activeTab==='tax-rates' && canReadTaxRates && <TaxRatesTable/>}
      {activeTab==='system' && <ConfigEditor/>}
    </div>
  );
}
