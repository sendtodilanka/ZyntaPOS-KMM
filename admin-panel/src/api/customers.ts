import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';

export interface CustomerSummaryDto {
  id: string;
  storeId: string | null;
  name: string;
  email: string | null;
  phone: string | null;
  loyaltyPoints: number;
}

export interface GlobalCustomersResponse {
  total: number;
  page: number;
  size: number;
  items: CustomerSummaryDto[];
}

export interface GlobalCustomersFilter {
  search?: string;
  storeId?: string;
  page?: number;
  size?: number;
}

const QUERY_KEY = 'customers-global';

export function useGlobalCustomers(filters: GlobalCustomersFilter = {}) {
  const qs = new URLSearchParams();
  if (filters.search)  qs.set('search',  filters.search);
  if (filters.storeId) qs.set('storeId', filters.storeId);
  if (filters.page != null) qs.set('page', String(filters.page));
  if (filters.size != null) qs.set('size', String(filters.size));
  const query = qs.toString();
  return useQuery({
    queryKey: [QUERY_KEY, filters],
    queryFn: () =>
      apiClient
        .get(`admin/customers/global${query ? `?${query}` : ''}`)
        .json<GlobalCustomersResponse>(),
  });
}
