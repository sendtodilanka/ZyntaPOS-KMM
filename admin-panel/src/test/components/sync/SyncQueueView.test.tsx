import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '../../utils';
import { SyncQueueView } from '@/components/sync/SyncQueueView';
import type { SyncOperation } from '@/types/sync';

vi.mock('@/lib/utils', () => ({
  formatRelativeTime: (_s: string) => 'just now',
  truncate: (s: string, n: number) => (s.length > n ? s.slice(0, n) + '…' : s),
  cn: (...args: unknown[]) => args.filter(Boolean).join(' '),
}));

vi.mock('@/components/shared/LoadingState', () => ({
  TableSkeleton: ({ rows }: { rows?: number }) => (
    <div data-testid="table-skeleton" data-rows={rows}>Loading skeleton</div>
  ),
}));

vi.mock('@/components/shared/EmptyState', () => ({
  EmptyState: ({ title, description }: { title: string; description?: string }) => (
    <div data-testid="empty-state">
      <span>{title}</span>
      {description && <span>{description}</span>}
    </div>
  ),
}));

const mockOperation: SyncOperation = {
  id: 'op-1',
  storeId: 'store-1',
  entityType: 'product',
  entityId: 'prod-1',
  operationType: 'CREATE',
  payload: { name: 'Test Product' },
  clientTimestamp: new Date().toISOString(),
  retryCount: 0,
  lastErrorMessage: null,
  createdAt: new Date().toISOString(),
};

describe('SyncQueueView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders entity type', () => {
    render(<SyncQueueView operations={[mockOperation]} isLoading={false} />);
    expect(screen.getByText('product')).toBeInTheDocument();
  });

  it('renders truncated entity ID', () => {
    render(<SyncQueueView operations={[mockOperation]} isLoading={false} />);
    // entityId 'prod-1' is 6 chars, shorter than truncate limit 12, so shown as-is
    expect(screen.getByText('prod-1')).toBeInTheDocument();
  });

  it('renders operation type badge (CREATE)', () => {
    render(<SyncQueueView operations={[mockOperation]} isLoading={false} />);
    expect(screen.getByText('CREATE')).toBeInTheDocument();
  });

  it('renders retry count', () => {
    render(<SyncQueueView operations={[mockOperation]} isLoading={false} />);
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  it('shows non-zero retry count in amber color class', () => {
    const retriedOp: SyncOperation = { ...mockOperation, retryCount: 3 };
    render(<SyncQueueView operations={[retriedOp]} isLoading={false} />);
    const retryCell = screen.getByText('3');
    expect(retryCell).toHaveClass('text-amber-400');
  });

  it('shows empty state when no operations', () => {
    render(<SyncQueueView operations={[]} isLoading={false} />);
    expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    expect(screen.getByText('Queue empty')).toBeInTheDocument();
  });

  it('shows loading skeleton when isLoading is true', () => {
    render(<SyncQueueView operations={[]} isLoading={true} />);
    expect(screen.getByTestId('table-skeleton')).toBeInTheDocument();
  });

  it('shows error message with icon when operation has lastErrorMessage', () => {
    const errorOp: SyncOperation = {
      ...mockOperation,
      lastErrorMessage: 'Connection timeout',
    };
    render(<SyncQueueView operations={[errorOp]} isLoading={false} />);
    expect(screen.getByText('Connection timeout')).toBeInTheDocument();
  });

  it('shows dash placeholder when no error message', () => {
    render(<SyncQueueView operations={[mockOperation]} isLoading={false} />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('renders UPDATE operation type badge', () => {
    const updateOp: SyncOperation = { ...mockOperation, id: 'op-2', operationType: 'UPDATE' };
    render(<SyncQueueView operations={[updateOp]} isLoading={false} />);
    expect(screen.getByText('UPDATE')).toBeInTheDocument();
  });

  it('renders DELETE operation type badge', () => {
    const deleteOp: SyncOperation = { ...mockOperation, id: 'op-3', operationType: 'DELETE' };
    render(<SyncQueueView operations={[deleteOp]} isLoading={false} />);
    expect(screen.getByText('DELETE')).toBeInTheDocument();
  });

  it('renders multiple operations', () => {
    const op2: SyncOperation = {
      ...mockOperation,
      id: 'op-2',
      entityType: 'order',
      entityId: 'ord-99',
      operationType: 'UPDATE',
    };
    render(<SyncQueueView operations={[mockOperation, op2]} isLoading={false} />);
    expect(screen.getByText('product')).toBeInTheDocument();
    expect(screen.getByText('order')).toBeInTheDocument();
  });

  it('truncates long entity IDs', () => {
    const longIdOp: SyncOperation = {
      ...mockOperation,
      entityId: 'very-long-entity-id-that-exceeds-twelve',
    };
    render(<SyncQueueView operations={[longIdOp]} isLoading={false} />);
    // truncate(s, 12) → first 12 chars + ellipsis
    expect(screen.getByText('very-long-en…')).toBeInTheDocument();
  });
});
