export interface MasterProduct {
  id: string;
  sku: string | null;
  barcode: string | null;
  name: string;
  description: string | null;
  base_price: number;
  cost_price: number;
  category_id: string | null;
  unit_id: string | null;
  tax_group_id: string | null;
  image_url: string | null;
  is_active: boolean;
  created_at: number;
  updated_at: number;
  store_count: number;
}

export interface StoreProductAssignment {
  store_id: string;
  store_name: string;
  local_price: number | null;
  local_cost_price: number | null;
  local_stock_qty: number;
  min_stock_qty: number;
  is_active: boolean;
}

export interface CreateMasterProductRequest {
  sku?: string;
  barcode?: string;
  name: string;
  description?: string;
  base_price: number;
  cost_price?: number;
  category_id?: string;
  unit_id?: string;
  tax_group_id?: string;
  image_url?: string;
}

export interface UpdateMasterProductRequest {
  sku?: string;
  barcode?: string;
  name?: string;
  description?: string;
  base_price?: number;
  cost_price?: number;
  category_id?: string;
  unit_id?: string;
  tax_group_id?: string;
  image_url?: string;
  is_active?: boolean;
}

export interface AssignToStoreRequest {
  local_price?: number;
  local_cost_price?: number;
  local_stock_qty?: number;
  min_stock_qty?: number;
}

export interface BulkAssignRequest {
  store_ids: string[];
  local_price?: number;
  local_cost_price?: number;
}

export interface MasterProductFilter {
  page?: number;
  size?: number;
  search?: string;
}
