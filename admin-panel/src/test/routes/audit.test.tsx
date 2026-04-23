import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import React from 'react';
import type { AuditEntry } from '@/types/audit';
import type { PagedResponse } from '@/types/api';

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (opts: Record<string, unknown>) => opts,
  useNavigate: () => vi.fn(),
}));

vi.mock('@/hooks/use-debounce', () => ({
  useDebounce: (val: unknown) => val,
}));

// G-002: AuditPage now requires `audit:read` permission. Tests assume the
// user has it so they exercise the content path, not the access-denied page.
vi.mock('@/hooks/use-auth', () => ({
  useAuth: () => ({
    user: { id: 'test-admin', role: 'ADMIN', email: 'admin@test', name: 'Admin' },
    isAuthenticated: true,
    hasPermission: () => true,
    isAdmin: true,
  }),
}));

// Mock complex sub-components
vi.mock('@/components/audit/AuditLogTable', () => ({
  AuditLogTable: ({ data }: { data: AuditEntry[] }) => (
    <div data-testid="audit-log-table">
      {data.map((entry) => (
        <div key={entry.id}>
          <span>{entry.eventType}</span>
          <span>{entry.userName}</span>
          <span>{entry.storeName}</span>
        </div>
      ))}
    </div>
  ),
}));

vi.mock('@/components/audit/AuditFilterPanel', () => ({
  AuditFilterPanel: () => <div data-testid="audit-filter-panel" />,
}));

vi.mock('@/components/shared/SearchInput', () => ({
  SearchInput: ({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder?: string }) => (
    <input
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
    />
  ),
}));

vi.mock('@/components/shared/ExportButton', () => ({
  ExportButton: ({ onExportCsv }: { onExportCsv: () => void }) => (
    <button onClick={onExportCsv}>Export CSV</button>
  ),
}));

vi.mock('@/api/audit');
vi.mock('@/stores/ui-store', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { useAuditLogs, exportAuditLogs } from '@/api/audit';
import { Route } from '@/routes/audit/index';

const AuditPage = (Route as unknown as { component: React.FC }).component;

const mockEntry: AuditEntry = {
  id: 'audit-1',
  eventType: 'PRODUCT_CREATED',
  category: 'INVENTORY',
  userId: 'user-1',
  userName: 'admin@zyntapos.com',
  storeId: 'store-1',
  storeName: 'Colombo Store',
  entityType: 'product',
  entityId: 'prod-1',
  previousValues: null,
  newValues: {},
  ipAddress: '192.168.1.1',
  userAgent: 'Mozilla/5.0',
  success: true,
  errorMessage: null,
  hashChain: 'abc123',
  createdAt: new Date().toISOString(),
};

const mockPage: PagedResponse<AuditEntry> = {
  data: [mockEntry],
  total: 1,
  page: 0,
  size: 50,
  totalPages: 1,
};

describe('AuditPage', () => {
  beforeEach(() => {
    vi.mocked(useAuditLogs).mockReturnValue({
      data: mockPage,
      isLoading: false,
    } as unknown as ReturnType<typeof useAuditLogs>);

    vi.mocked(exportAuditLogs).mockResolvedValue(undefined);
  });

  it('renders page heading', () => {
    render(<AuditPage />);
    expect(screen.getByText('Audit Log')).toBeInTheDocument();
  });

  it('renders total count in subtitle', () => {
    render(<AuditPage />);
    expect(screen.getByText(/1 total entries/i)).toBeInTheDocument();
  });

  it('renders hash chain label in subtitle', () => {
    render(<AuditPage />);
    expect(screen.getByText(/immutable hash chain/i)).toBeInTheDocument();
  });

  it('renders search input', () => {
    render(<AuditPage />);
    expect(screen.getByPlaceholderText(/search events/i)).toBeInTheDocument();
  });

  it('renders export button', () => {
    render(<AuditPage />);
    expect(screen.getByRole('button', { name: /export csv/i })).toBeInTheDocument();
  });

  it('renders audit log table', () => {
    render(<AuditPage />);
    expect(screen.getByTestId('audit-log-table')).toBeInTheDocument();
  });

  it('renders audit log table with entries', () => {
    render(<AuditPage />);
    expect(screen.getByText('PRODUCT_CREATED')).toBeInTheDocument();
  });

  it('renders actor email in table', () => {
    render(<AuditPage />);
    expect(screen.getByText('admin@zyntapos.com')).toBeInTheDocument();
  });

  it('renders filter panel', () => {
    render(<AuditPage />);
    expect(screen.getByTestId('audit-filter-panel')).toBeInTheDocument();
  });

  it('updates search value on input', () => {
    render(<AuditPage />);
    const input = screen.getByPlaceholderText(/search events/i);
    fireEvent.change(input, { target: { value: 'PRODUCT' } });
    expect(input).toHaveValue('PRODUCT');
  });

  it('shows 0 total entries when data is undefined', () => {
    vi.mocked(useAuditLogs).mockReturnValue({
      data: undefined,
      isLoading: false,
    } as unknown as ReturnType<typeof useAuditLogs>);
    render(<AuditPage />);
    expect(screen.getByText(/0 total entries/i)).toBeInTheDocument();
  });

  it('calls exportAuditLogs when export button clicked', async () => {
    render(<AuditPage />);
    const exportBtn = screen.getByRole('button', { name: /export csv/i });
    fireEvent.click(exportBtn);
    await vi.waitFor(() => {
      expect(vi.mocked(exportAuditLogs)).toHaveBeenCalled();
    });
  });
});
