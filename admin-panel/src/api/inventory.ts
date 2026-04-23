import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';

export interface WarehouseStockDto {
  id: string;
  storeId: string;
  warehouseId: string;
  productId: string;
  quantity: number;
  minQuantity: number;
  isLowStock: boolean;
  updatedAt: number;
}

export interface GlobalInventoryResponse {
  total: number;
  lowStock: number;
  page: number;
  size: number;
  totalPages: number;
  items: WarehouseStockDto[];
}

// F-001: pagination params. Backend clamps size to 1..200, defaults to 50.
export interface GlobalInventoryFilter {
  productId?: string;
  storeId?: string;
  page?: number;
  size?: number;
}

const QUERY_KEY = 'inventory-global';

export function useGlobalInventory(filters: GlobalInventoryFilter = {}) {
  const qs = new URLSearchParams();
  if (filters.productId) qs.set('productId', filters.productId);
  if (filters.storeId)   qs.set('storeId', filters.storeId);
  if (filters.page !== undefined) qs.set('page', String(filters.page));
  if (filters.size !== undefined) qs.set('size', String(filters.size));
  const query = qs.toString();
  return useQuery({
    queryKey: [QUERY_KEY, filters],
    queryFn: () =>
      apiClient
        .get(`admin/inventory/global${query ? `?${query}` : ''}`)
        .json<GlobalInventoryResponse>(),
  });
}
