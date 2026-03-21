import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import React from 'react';
import type { WarehouseStockDto } from '@/api/inventory';

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (opts: Record<string, unknown>) => opts,
  useNavigate: () => vi.fn(),
}));

vi.mock('@/hooks/use-debounce', () => ({
  useDebounce: (val: unknown) => val,
}));

vi.mock('@/api/inventory');
import { useGlobalInventory } from '@/api/inventory';
import { Route } from '@/routes/inventory/index';

const InventoryPage = (Route as { component: React.FC }).component;

const mockInventoryItem: WarehouseStockDto = {
  id: 'wh-1',
  productId: 'prod-1',
  storeId: 'store-1',
  warehouseId: 'wh-main',
  quantity: 5.0,
  minQuantity: 10.0,
  isLowStock: true,
  updatedAt: Date.now(),
};

describe('InventoryPage', () => {
  beforeEach(() => {
    vi.mocked(useGlobalInventory).mockReturnValue({
      data: { items: [mockInventoryItem], total: 1, lowStock: 1 },
      isLoading: false,
    } as ReturnType<typeof useGlobalInventory>);
  });

  it('renders page heading', () => {
    render(<InventoryPage />);
    expect(screen.getByText('Warehouse Inventory')).toBeInTheDocument();
  });

  it('renders total stock row count', () => {
    render(<InventoryPage />);
    expect(screen.getByText(/1 stock rows/i)).toBeInTheDocument();
  });

  it('shows low stock count in subtitle', () => {
    render(<InventoryPage />);
    expect(screen.getByText(/1 low stock/i)).toBeInTheDocument();
  });

  it('renders product filter input', () => {
    render(<InventoryPage />);
    expect(screen.getByPlaceholderText(/filter by product id/i)).toBeInTheDocument();
  });

  it('renders store filter input', () => {
    render(<InventoryPage />);
    expect(screen.getByPlaceholderText(/filter by store id/i)).toBeInTheDocument();
  });

  it('renders low stock toggle button', () => {
    render(<InventoryPage />);
    expect(screen.getByRole('button', { name: /low stock/i })).toBeInTheDocument();
  });

  it('shows 0 stock rows when data is undefined', () => {
    vi.mocked(useGlobalInventory).mockReturnValue({
      data: undefined,
      isLoading: false,
    } as ReturnType<typeof useGlobalInventory>);
    render(<InventoryPage />);
    expect(screen.getByText(/0 stock rows/i)).toBeInTheDocument();
  });

  it('does not show low stock count in subtitle when lowStock is 0', () => {
    vi.mocked(useGlobalInventory).mockReturnValue({
      data: { items: [], total: 0, lowStock: 0 },
      isLoading: false,
    } as ReturnType<typeof useGlobalInventory>);
    render(<InventoryPage />);
    expect(screen.queryByText(/· 0 low stock/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/· \d+ low stock/)).not.toBeInTheDocument();
  });

  it('filters by product id on input change', () => {
    render(<InventoryPage />);
    const input = screen.getByPlaceholderText(/filter by product id/i);
    fireEvent.change(input, { target: { value: 'prod-123' } });
    expect(input).toHaveValue('prod-123');
  });

  it('filters by store id on input change', () => {
    render(<InventoryPage />);
    const input = screen.getByPlaceholderText(/filter by store id/i);
    fireEvent.change(input, { target: { value: 'store-abc' } });
    expect(input).toHaveValue('store-abc');
  });

  it('toggles low stock filter on button click', () => {
    render(<InventoryPage />);
    const btn = screen.getByRole('button', { name: /low stock/i });
    fireEvent.click(btn);
    // After toggle, only low stock items should show (our mock item is low stock)
    expect(screen.getByText('prod-1')).toBeInTheDocument();
  });

  it('shows item productId in table', () => {
    render(<InventoryPage />);
    expect(screen.getByText('prod-1')).toBeInTheDocument();
  });

  it('calls useGlobalInventory with filters', () => {
    render(<InventoryPage />);
    expect(vi.mocked(useGlobalInventory)).toHaveBeenCalledWith(
      expect.objectContaining({ productId: undefined, storeId: undefined }),
    );
  });
});
