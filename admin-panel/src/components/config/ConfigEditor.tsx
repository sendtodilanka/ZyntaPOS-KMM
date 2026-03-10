import { useState } from 'react';
import { Save, Eye, EyeOff } from 'lucide-react';
import { useSystemConfig, useUpdateSystemConfig } from '@/api/config';
import type { SystemConfig } from '@/types/config';
import { cn } from '@/lib/utils';

function ConfigRow({ config }: { config: SystemConfig }) {
  const { mutate: update, isPending } = useUpdateSystemConfig();
  const [localValue, setLocalValue] = useState(String(config.value));
  const [showValue, setShowValue] = useState(false);
  const isDirty = localValue !== String(config.value);
  const handleSave = () => {
    let v: string|number|boolean = localValue;
    if (config.type==='number') v=Number(localValue);
    if (config.type==='boolean') v=localValue==='true';
    update({ key: config.key, value: v });
  };
  return (
    <div className="py-4 border-b border-surface-border last:border-0">
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium text-slate-100 font-mono">{config.key}</span>
            <span className={cn('px-1.5 py-0.5 text-[10px] rounded', config.type==='boolean'?'bg-purple-500/10 text-purple-400':config.type==='number'?'bg-blue-500/10 text-blue-400':config.type==='json'?'bg-amber-500/10 text-amber-400':'bg-slate-700 text-slate-400')}>{config.type}</span>
            {!config.editable && <span className="px-1.5 py-0.5 text-[10px] bg-red-500/10 text-red-400 rounded">read-only</span>}
          </div>
          <p className="text-xs text-slate-500 mt-0.5">{config.description}</p>
        </div>
        {config.sensitive && <button onClick={()=>setShowValue(!showValue)} className="p-2 text-slate-400 hover:text-slate-100 min-h-[44px] min-w-[44px] flex items-center justify-center">{showValue?<EyeOff className="w-4 h-4"/>:<Eye className="w-4 h-4"/>}</button>}
      </div>
      {config.editable ? (
        <div className="mt-2 flex gap-2">
          {config.type==='boolean' ? (
            <select value={localValue} onChange={e=>setLocalValue(e.target.value)} className="flex-1 h-10 px-3 bg-surface-card border border-surface-border rounded-lg text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500"><option value="true">true</option><option value="false">false</option></select>
          ) : (
            <input type={config.type==='number'?'number':'text'} value={config.sensitive&&!showValue?'••••••••••••':localValue} onChange={e=>setLocalValue(e.target.value)} readOnly={config.sensitive&&!showValue} className="flex-1 h-10 px-3 bg-surface-card border border-surface-border rounded-lg text-sm text-slate-100 font-mono focus:outline-none focus:ring-2 focus:ring-brand-500"/>
          )}
          <button onClick={handleSave} disabled={!isDirty||isPending} className={cn('flex items-center gap-2 px-3 min-h-[44px] rounded-lg text-sm font-medium transition-colors', isDirty?'bg-brand-700 hover:bg-brand-800 text-white':'bg-surface-elevated text-slate-500 cursor-not-allowed')}><Save className="w-4 h-4"/>{isPending?'Saving...':'Save'}</button>
        </div>
      ) : (
        <div className="mt-2 px-3 py-2 bg-surface-elevated rounded-lg text-sm text-slate-400 font-mono">{config.sensitive&&!showValue?'••••••••••••':localValue}</div>
      )}
    </div>
  );
}

export function ConfigEditor() {
  const { data: configs, isLoading, error } = useSystemConfig();
  const [search, setSearch] = useState('');
  if (isLoading) return <div className="space-y-4">{[1,2,3,4,5].map(i=><div key={i} className="h-20 bg-surface-elevated rounded animate-pulse"/>)}</div>;
  if (error) return <div className="text-center py-8 text-slate-400">Failed to load system config</div>;
  const filtered = (configs??[]).filter(c=>c.key.toLowerCase().includes(search.toLowerCase())||c.description.toLowerCase().includes(search.toLowerCase()));
  const byCategory = filtered.reduce<Record<string,SystemConfig[]>>((acc,c)=>{ if(!acc[c.category])acc[c.category]=[]; acc[c.category].push(c); return acc; },{});
  return (
    <div className="space-y-6">
      <input type="search" placeholder="Search config keys..." value={search} onChange={e=>setSearch(e.target.value)} className="w-full h-10 px-3 bg-surface-elevated border border-surface-border rounded-lg text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-brand-500"/>
      {Object.entries(byCategory).map(([cat,items])=>(
        <div key={cat} className="bg-surface-card border border-surface-border rounded-xl p-4">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-3">{cat.replace(/_/g,' ')}</p>
          {items.map(c=><ConfigRow key={c.key} config={c}/>)}
        </div>
      ))}
      {filtered.length===0 && <div className="text-center py-8 text-slate-400">No config keys match your search</div>}
    </div>
  );
}
