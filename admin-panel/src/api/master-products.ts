import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { toast } from '@/stores/ui-store';
import type {
  MasterProduct,
  StoreProductAssignment,
  CreateMasterProductRequest,
  UpdateMasterProductRequest,
  AssignToStoreRequest,
  BulkAssignRequest,
  MasterProductFilter,
} from '@/types/master-product';
import type { PagedResponse } from '@/types/api';

const QUERY_KEY = 'master-products';

export function useMasterProducts(filters: MasterProductFilter = {}) {
  const qs = new URLSearchParams();
  if (filters.page !== undefined) qs.set('page', String(filters.page));
  if (filters.size !== undefined) qs.set('size', String(filters.size ?? 20));
  if (filters.search) qs.set('search', filters.search);
  return useQuery({
    queryKey: [QUERY_KEY, filters],
    queryFn: () => apiClient.get(`admin/master-products?${qs}`).json<PagedResponse<MasterProduct>>(),
  });
}

export function useMasterProduct(id: string) {
  return useQuery({
    queryKey: [QUERY_KEY, id],
    queryFn: () => apiClient.get(`admin/master-products/${id}`).json<MasterProduct>(),
    enabled: !!id,
  });
}

export function useCreateMasterProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateMasterProductRequest) =>
      apiClient.post('admin/master-products', { json: data }).json<MasterProduct>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [QUERY_KEY] });
      toast.success('Product created', 'Master product has been added to the catalog.');
    },
    onError: () => toast.error('Failed to create product'),
  });
}

export function useUpdateMasterProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateMasterProductRequest }) =>
      apiClient.put(`admin/master-products/${id}`, { json: data }).json<MasterProduct>(),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: [QUERY_KEY, id] });
      qc.invalidateQueries({ queryKey: [QUERY_KEY] });
      toast.success('Product updated', 'Master product has been updated.');
    },
    onError: () => toast.error('Failed to update product'),
  });
}

export function useDeleteMasterProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`admin/master-products/${id}`).json(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [QUERY_KEY] });
      toast.success('Product deleted', 'Master product has been removed.');
    },
    onError: () => toast.error('Failed to delete product'),
  });
}

export function useMasterProductStores(masterProductId: string) {
  return useQuery({
    queryKey: [QUERY_KEY, masterProductId, 'stores'],
    queryFn: () =>
      apiClient.get(`admin/master-products/${masterProductId}/stores`).json<StoreProductAssignment[]>(),
    enabled: !!masterProductId,
  });
}

export function useAssignToStore() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      masterProductId,
      storeId,
      data,
    }: {
      masterProductId: string;
      storeId: string;
      data?: AssignToStoreRequest;
    }) =>
      apiClient
        .post(`admin/master-products/${masterProductId}/stores/${storeId}`, { json: data ?? {} })
        .json(),
    onSuccess: (_, { masterProductId }) => {
      qc.invalidateQueries({ queryKey: [QUERY_KEY, masterProductId, 'stores'] });
      qc.invalidateQueries({ queryKey: [QUERY_KEY, masterProductId] });
      toast.success('Store assigned', 'Product assigned to store.');
    },
    onError: () => toast.error('Failed to assign to store'),
  });
}

export function useRemoveFromStore() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ masterProductId, storeId }: { masterProductId: string; storeId: string }) =>
      apiClient.delete(`admin/master-products/${masterProductId}/stores/${storeId}`).json(),
    onSuccess: (_, { masterProductId }) => {
      qc.invalidateQueries({ queryKey: [QUERY_KEY, masterProductId, 'stores'] });
      qc.invalidateQueries({ queryKey: [QUERY_KEY, masterProductId] });
      toast.success('Store removed', 'Product removed from store.');
    },
    onError: () => toast.error('Failed to remove from store'),
  });
}

export function useUpdateStoreOverride() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      masterProductId,
      storeId,
      data,
    }: {
      masterProductId: string;
      storeId: string;
      data: AssignToStoreRequest;
    }) =>
      apiClient
        .put(`admin/master-products/${masterProductId}/stores/${storeId}`, { json: data })
        .json(),
    onSuccess: (_, { masterProductId }) => {
      qc.invalidateQueries({ queryKey: [QUERY_KEY, masterProductId, 'stores'] });
      toast.success('Override updated', 'Store override has been saved.');
    },
    onError: () => toast.error('Failed to update override'),
  });
}

export function useBulkAssign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ masterProductId, data }: { masterProductId: string; data: BulkAssignRequest }) =>
      apiClient.post(`admin/master-products/${masterProductId}/bulk-assign`, { json: data }).json(),
    onSuccess: (_, { masterProductId }) => {
      qc.invalidateQueries({ queryKey: [QUERY_KEY, masterProductId, 'stores'] });
      qc.invalidateQueries({ queryKey: [QUERY_KEY, masterProductId] });
      toast.success('Bulk assign complete', 'Product assigned to multiple stores.');
    },
    onError: () => toast.error('Failed to bulk assign'),
  });
}
