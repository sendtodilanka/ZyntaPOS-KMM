import { describe, it, expect } from 'vitest';
import { render, screen } from '../../utils';
import { StoreConfigForm } from '@/components/stores/StoreConfigForm';
import type { StoreConfig } from '@/types/store';

const mockConfig: StoreConfig = {
  storeId: 'store-1',
  taxRates: [],
  featureFlags: {},
  timezone: 'Asia/Colombo',
  currency: 'LKR',
  receiptFooter: 'Thank you!',
  syncIntervalSeconds: 60,
  updatedAt: new Date().toISOString(),
};

describe('StoreConfigForm', () => {
  it('renders timezone as read-only text', () => {
    render(<StoreConfigForm storeId="store-1" config={mockConfig} />);
    expect(screen.getByText('Asia/Colombo')).toBeInTheDocument();
  });

  it('renders currency as read-only text', () => {
    render(<StoreConfigForm storeId="store-1" config={mockConfig} />);
    expect(screen.getByText('LKR')).toBeInTheDocument();
  });

  it('renders sync interval as read-only text', () => {
    render(<StoreConfigForm storeId="store-1" config={mockConfig} />);
    expect(screen.getByText('60')).toBeInTheDocument();
  });

  it('renders receipt footer as read-only text', () => {
    render(<StoreConfigForm storeId="store-1" config={mockConfig} />);
    expect(screen.getByText('Thank you!')).toBeInTheDocument();
  });

  it('does not render a Save button (read-only per ADR-009)', () => {
    render(<StoreConfigForm storeId="store-1" config={mockConfig} />);
    expect(screen.queryByRole('button', { name: /save/i })).not.toBeInTheDocument();
  });

  it('shows read-only notice', () => {
    render(<StoreConfigForm storeId="store-1" config={mockConfig} />);
    expect(screen.getByText(/managed by the store owner/i)).toBeInTheDocument();
  });

  it('shows fallback when receipt footer is empty', () => {
    const configNoFooter = { ...mockConfig, receiptFooter: '' };
    render(<StoreConfigForm storeId="store-1" config={configNoFooter} />);
    expect(screen.getByText('Not configured')).toBeInTheDocument();
  });

  it('shows defaults when config is undefined', () => {
    render(<StoreConfigForm storeId="store-1" />);
    expect(screen.getByText('Asia/Colombo')).toBeInTheDocument();
    expect(screen.getByText('LKR')).toBeInTheDocument();
    expect(screen.getByText('300')).toBeInTheDocument();
  });
});
