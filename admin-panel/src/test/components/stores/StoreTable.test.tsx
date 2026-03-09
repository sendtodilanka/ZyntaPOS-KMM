import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../../utils';
import { StoreTable } from '@/components/stores/StoreTable';
import type { Store } from '@/types/store';

const mockNavigate = vi.fn();
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => mockNavigate }));

const mockStore: Store = {
  id: 'store-1',
  name: 'Colombo Store',
  location: 'Colombo, Sri Lanka',
  licenseKey: 'ZYNTA-ABCD-1234-EFGH',
  edition: 'PROFESSIONAL',
  status: 'HEALTHY',
  activeUsers: 3,
  lastSyncAt: new Date().toISOString(),
  lastHeartbeatAt: new Date().toISOString(),
  appVersion: '1.0.0',
  createdAt: new Date().toISOString(),
};

const defaultProps = {
  data: [mockStore],
  isLoading: false,
  page: 1,
  totalPages: 1,
  total: 1,
  onPageChange: vi.fn(),
};

describe('StoreTable', () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  it('renders store name', () => {
    render(<StoreTable {...defaultProps} />);
    expect(screen.getByText('Colombo Store')).toBeInTheDocument();
  });

  it('renders store location', () => {
    render(<StoreTable {...defaultProps} />);
    expect(screen.getByText('Colombo, Sri Lanka')).toBeInTheDocument();
  });

  it('renders status badge for HEALTHY status', () => {
    render(<StoreTable {...defaultProps} />);
    expect(screen.getByText('Healthy')).toBeInTheDocument();
  });

  it('renders edition text', () => {
    render(<StoreTable {...defaultProps} />);
    expect(screen.getByText('PROFESSIONAL')).toBeInTheDocument();
  });

  it('shows active users count', () => {
    render(<StoreTable {...defaultProps} />);
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('shows app version', () => {
    render(<StoreTable {...defaultProps} />);
    expect(screen.getByText('1.0.0')).toBeInTheDocument();
  });

  it('shows empty state when data is empty', () => {
    render(<StoreTable {...defaultProps} data={[]} />);
    expect(screen.getByText('No stores found')).toBeInTheDocument();
  });

  it('clicking a row navigates to store detail', () => {
    render(<StoreTable {...defaultProps} />);
    const row = screen.getByText('Colombo Store').closest('tr');
    if (row) {
      fireEvent.click(row);
    }
    expect(mockNavigate).toHaveBeenCalledWith({
      to: '/stores/$storeId',
      params: { storeId: 'store-1' },
    });
  });
});
