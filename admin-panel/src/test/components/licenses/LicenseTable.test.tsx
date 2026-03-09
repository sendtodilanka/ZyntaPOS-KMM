import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../../utils';
import { LicenseTable } from '@/components/licenses/LicenseTable';
import type { License } from '@/types/license';

const mockNavigate = vi.fn();
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => mockNavigate }));

const mockRevokeMutate = vi.fn();
vi.mock('@/api/licenses', () => ({
  useRevokeLicense: () => ({ mutate: mockRevokeMutate, isPending: false }),
}));

const mockLicense: License = {
  id: 'lic-1',
  key: 'ZYNTA-ABCD-1234-EFGH',
  customerId: 'cust_abc123',
  customerName: 'Test Customer',
  edition: 'PROFESSIONAL',
  status: 'ACTIVE',
  maxDevices: 5,
  activeDevices: 3,
  activatedAt: new Date().toISOString(),
  expiresAt: '2026-12-31T00:00:00Z',
  lastHeartbeatAt: new Date().toISOString(),
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

const defaultProps = {
  data: [mockLicense],
  isLoading: false,
  page: 0,
  totalPages: 1,
  total: 1,
  onPageChange: vi.fn(),
  onEdit: vi.fn(),
};

describe('LicenseTable', () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    mockRevokeMutate.mockClear();
  });

  it('renders masked license key', () => {
    render(<LicenseTable {...defaultProps} />);
    // maskLicenseKey should produce something like ZYNTA-****-****-EFGH
    expect(screen.getByText(/ZYNTA-/)).toBeInTheDocument();
    expect(screen.getByText(/EFGH/)).toBeInTheDocument();
  });

  it('renders customer name', () => {
    render(<LicenseTable {...defaultProps} />);
    expect(screen.getByText('Test Customer')).toBeInTheDocument();
  });

  it('renders edition', () => {
    render(<LicenseTable {...defaultProps} />);
    expect(screen.getByText('PROFESSIONAL')).toBeInTheDocument();
  });

  it('renders status badge for ACTIVE status', () => {
    render(<LicenseTable {...defaultProps} />);
    // StatusBadge renders a capitalised/formatted label for ACTIVE
    expect(screen.getByText(/active/i)).toBeInTheDocument();
  });

  it('shows device count', () => {
    render(<LicenseTable {...defaultProps} />);
    // activeDevices/maxDevices rendered as "3/5"
    expect(screen.getByText('3/5')).toBeInTheDocument();
  });

  it('shows empty state when data is empty', () => {
    render(<LicenseTable {...defaultProps} data={[]} />);
    expect(screen.getByText('No licenses found')).toBeInTheDocument();
  });

  it('actions dropdown contains Revoke option after opening', () => {
    render(<LicenseTable {...defaultProps} />);
    // Open the actions dropdown
    const actionsButton = screen.getByLabelText('Actions');
    fireEvent.click(actionsButton);
    expect(screen.getByText('Revoke')).toBeInTheDocument();
  });

  it('clicking Revoke shows confirmation dialog', () => {
    render(<LicenseTable {...defaultProps} />);
    const actionsButton = screen.getByLabelText('Actions');
    fireEvent.click(actionsButton);
    fireEvent.click(screen.getByText('Revoke'));
    // ConfirmDialog heading appears — query by heading role to disambiguate from the button
    expect(screen.getByRole('heading', { name: 'Revoke License' })).toBeInTheDocument();
  });
});
