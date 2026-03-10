import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { TableSkeleton } from './LoadingState';
import { EmptyState } from './EmptyState';

export interface Column<T> {
  key: string;
  header: string;
  cell: (row: T) => React.ReactNode;
  className?: string;
  headerClassName?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  isLoading?: boolean;
  page?: number;
  totalPages?: number;
  total?: number;
  onPageChange?: (page: number) => void;
  emptyTitle?: string;
  emptyDescription?: string;
  rowKey: (row: T) => string;
  onRowClick?: (row: T) => void;
}

export function DataTable<T>({
  columns,
  data,
  isLoading = false,
  page = 0,
  totalPages = 1,
  total = 0,
  onPageChange,
  emptyTitle = 'No data',
  emptyDescription,
  rowKey,
  onRowClick,
}: DataTableProps<T>) {
  if (isLoading) return <TableSkeleton rows={5} />;

  if (data.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  return (
    <div className="space-y-3">
      {/* Scrollable table wrapper */}
      <div className="overflow-x-auto rounded-lg border border-surface-border" tabIndex={0}>
        <table className="w-full text-sm min-w-[640px]">
          <thead>
            <tr className="border-b border-surface-border bg-surface-elevated">
              {columns.map((col) => (
                <th
                  key={col.key}
                  className={cn(
                    'px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-wide text-slate-400',
                    col.headerClassName,
                  )}
                >
                  {col.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-surface-border">
            {data.map((row) => (
              <tr
                key={rowKey(row)}
                className={cn(
                  'transition-colors',
                  onRowClick
                    ? 'cursor-pointer hover:bg-surface-elevated'
                    : 'hover:bg-surface-elevated/50',
                )}
                onClick={() => onRowClick?.(row)}
              >
                {columns.map((col) => (
                  <td
                    key={col.key}
                    className={cn('px-4 py-3 text-slate-300', col.className)}
                    onClick={col.key === 'actions' ? (e) => e.stopPropagation() : undefined}
                  >
                    {col.cell(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && onPageChange && (
        <div className="flex items-center justify-between px-1">
          <p className="text-xs text-slate-400">
            {total.toLocaleString()} total records
          </p>
          <div className="flex items-center gap-1">
            <PaginationButton onClick={() => onPageChange(0)} disabled={page === 0} aria-label="First page">
              <ChevronsLeft className="w-4 h-4" />
            </PaginationButton>
            <PaginationButton onClick={() => onPageChange(page - 1)} disabled={page === 0} aria-label="Previous page">
              <ChevronLeft className="w-4 h-4" />
            </PaginationButton>
            <span className="px-3 py-1 text-xs text-slate-300">
              {page + 1} / {totalPages}
            </span>
            <PaginationButton onClick={() => onPageChange(page + 1)} disabled={page >= totalPages - 1} aria-label="Next page">
              <ChevronRight className="w-4 h-4" />
            </PaginationButton>
            <PaginationButton onClick={() => onPageChange(totalPages - 1)} disabled={page >= totalPages - 1} aria-label="Last page">
              <ChevronsRight className="w-4 h-4" />
            </PaginationButton>
          </div>
        </div>
      )}
    </div>
  );
}

function PaginationButton({
  children, onClick, disabled, 'aria-label': ariaLabel,
}: {
  children: React.ReactNode;
  onClick: () => void;
  disabled: boolean;
  'aria-label': string;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      aria-label={ariaLabel}
      className="p-1.5 rounded-md text-slate-400 hover:bg-surface-elevated hover:text-slate-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors min-w-[36px] min-h-[36px] flex items-center justify-center"
    >
      {children}
    </button>
  );
}
