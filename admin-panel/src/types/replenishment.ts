// ── Replenishment DTOs (mirror AdminReplenishmentRoutes.kt) ──────────────────

export interface ReplenishmentRule {
  id: string;
  productId: string;
  warehouseId: string;
  supplierId: string;
  reorderPoint: number;
  reorderQty: number;
  autoApprove: boolean;
  isActive: boolean;
  updatedAt: number; // epoch-ms
}

export interface ReplenishmentRulesResponse {
  total: number;
  rules: ReplenishmentRule[];
}

export interface UpsertReplenishmentRuleRequest {
  id: string;
  productId: string;
  warehouseId: string;
  supplierId: string;
  reorderPoint: number;
  reorderQty: number;
  autoApprove?: boolean;
  isActive?: boolean;
}

export interface ReplenishmentSuggestion {
  ruleId: string;
  productId: string;
  warehouseId: string;
  supplierId: string;
  currentStock: number;
  reorderPoint: number;
  reorderQty: number;
  autoApprove: boolean;
}

export interface ReplenishmentSuggestionsResponse {
  total: number;
  suggestions: ReplenishmentSuggestion[];
}
