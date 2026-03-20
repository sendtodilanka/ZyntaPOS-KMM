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
  items: WarehouseStockDto[];
}

export interface GlobalInventoryFilter {
  productId?: string;
  storeId?: string;
}

const QUERY_KEY = 'inventory-global';

export function useGlobalInventory(filters: GlobalInventoryFilter = {}) {
  const qs = new URLSearchParams();
  if (filters.productId) qs.set('productId', filters.productId);
  if (filters.storeId)   qs.set('storeId', filters.storeId);
  const query = qs.toString();
  return useQuery({
    queryKey: [QUERY_KEY, filters],
    queryFn: () =>
      apiClient
        .get(`admin/inventory/global${query ? `?${query}` : ''}`)
        .json<GlobalInventoryResponse>(),
  });
}
