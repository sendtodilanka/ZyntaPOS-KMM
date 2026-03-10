import { useState, useRef, useEffect } from 'react';
import { Download, FileText, FileSpreadsheet } from 'lucide-react';

interface ExportButtonProps {
  onExportCsv: () => void;
  onExportPdf?: () => void;
  isLoading?: boolean;
}

export function ExportButton({ onExportCsv, onExportPdf, isLoading = false }: ExportButtonProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        disabled={isLoading}
        aria-label="Export"
        className="flex items-center gap-2 px-3 py-2 bg-surface-elevated border border-surface-border rounded-lg text-sm text-slate-300 hover:text-slate-100 hover:bg-surface-card transition-colors min-h-[44px] disabled:opacity-50"
      >
        <Download className="w-4 h-4" />
        <span className="hidden sm:inline">Export</span>
      </button>
      {open && (
        <div className="absolute right-0 top-full mt-1 w-40 bg-surface-card border border-surface-border rounded-lg shadow-xl z-20 py-1">
          <button
            onClick={() => { onExportCsv(); setOpen(false); }}
            className="flex items-center gap-2.5 w-full px-3 py-2.5 text-sm text-slate-300 hover:bg-surface-elevated hover:text-slate-100 transition-colors min-h-[44px]"
          >
            <FileSpreadsheet className="w-4 h-4 text-emerald-400" />
            Export CSV
          </button>
          {onExportPdf && (
            <button
              onClick={() => { onExportPdf(); setOpen(false); }}
              className="flex items-center gap-2.5 w-full px-3 py-2.5 text-sm text-slate-300 hover:bg-surface-elevated hover:text-slate-100 transition-colors min-h-[44px]"
            >
              <FileText className="w-4 h-4 text-red-400" />
              Export PDF
            </button>
          )}
        </div>
      )}
    </div>
  );
}
