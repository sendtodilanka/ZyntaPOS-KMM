import { useState } from 'react';
import { Calendar, ChevronDown } from 'lucide-react';
import { format, subDays, startOfMonth, startOfWeek } from 'date-fns';
import { cn } from '@/lib/utils';

interface DateRange { from: string; to: string }

const PRESETS = [
  { label: 'Today', getRange: () => ({ from: format(new Date(), 'yyyy-MM-dd'), to: format(new Date(), 'yyyy-MM-dd') }) },
  { label: 'Last 7 days', getRange: () => ({ from: format(subDays(new Date(), 7), 'yyyy-MM-dd'), to: format(new Date(), 'yyyy-MM-dd') }) },
  { label: 'Last 30 days', getRange: () => ({ from: format(subDays(new Date(), 30), 'yyyy-MM-dd'), to: format(new Date(), 'yyyy-MM-dd') }) },
  { label: 'This week', getRange: () => ({ from: format(startOfWeek(new Date()), 'yyyy-MM-dd'), to: format(new Date(), 'yyyy-MM-dd') }) },
  { label: 'This month', getRange: () => ({ from: format(startOfMonth(new Date()), 'yyyy-MM-dd'), to: format(new Date(), 'yyyy-MM-dd') }) },
];

interface DateRangePickerProps {
  value?: DateRange;
  onChange: (range: DateRange | undefined) => void;
  className?: string;
}

export function DateRangePicker({ value, onChange, className }: DateRangePickerProps) {
  const [open, setOpen] = useState(false);
  const [customFrom, setCustomFrom] = useState(value?.from ?? '');
  const [customTo, setCustomTo] = useState(value?.to ?? '');

  const label = value
    ? `${value.from} → ${value.to}`
    : 'All time';

  return (
    <div className={cn('relative', className)}>
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-2 h-10 px-3 bg-surface-elevated border border-surface-border rounded-lg text-sm text-slate-300 hover:border-slate-500 transition-colors min-w-[180px] w-full"
      >
        <Calendar className="w-4 h-4 text-slate-400 flex-shrink-0" />
        <span className="flex-1 text-left truncate">{label}</span>
        <ChevronDown className="w-3.5 h-3.5 text-slate-400 flex-shrink-0" />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1 w-64 bg-surface-card border border-surface-border rounded-lg shadow-xl z-30 p-3">
          <div className="space-y-1 mb-3">
            {PRESETS.map((preset) => (
              <button
                key={preset.label}
                onClick={() => { onChange(preset.getRange()); setOpen(false); }}
                className="w-full text-left px-3 py-2 text-sm text-slate-300 hover:bg-surface-elevated rounded-lg transition-colors min-h-[40px]"
              >
                {preset.label}
              </button>
            ))}
            <button
              onClick={() => { onChange(undefined); setOpen(false); }}
              className="w-full text-left px-3 py-2 text-sm text-slate-500 hover:bg-surface-elevated rounded-lg transition-colors min-h-[40px]"
            >
              All time
            </button>
          </div>
          <div className="border-t border-surface-border pt-3 space-y-2">
            <p className="text-xs text-slate-500 mb-2">Custom range</p>
            <input
              type="date"
              value={customFrom}
              onChange={(e) => setCustomFrom(e.target.value)}
              className="w-full h-9 bg-surface-elevated border border-surface-border rounded-lg px-2 text-xs text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
            <input
              type="date"
              value={customTo}
              onChange={(e) => setCustomTo(e.target.value)}
              className="w-full h-9 bg-surface-elevated border border-surface-border rounded-lg px-2 text-xs text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
            <button
              onClick={() => {
                if (customFrom && customTo) { onChange({ from: customFrom, to: customTo }); setOpen(false); }
              }}
              disabled={!customFrom || !customTo}
              className="w-full px-3 py-2 bg-brand-700 text-white text-xs font-medium rounded-lg disabled:opacity-40 hover:bg-brand-600 transition-colors min-h-[36px]"
            >
              Apply
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
