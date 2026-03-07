export interface FeatureFlag {
  id: string;
  name: string;
  key: string;
  description: string;
  enabled: boolean;
  scope: 'global' | 'per-store';
  storeIds: string[] | null;
  updatedAt: string;
}

export interface RemoteConfig {
  key: string;
  value: unknown;
  description: string;
  dataType: 'string' | 'number' | 'boolean' | 'json';
  storeIds: string[] | null;
  updatedAt: string;
}

export interface ConfigPushRequest {
  storeIds: string[];
  configPayload: Record<string, unknown>;
}

export interface ConfigPushResult {
  storeId: string;
  success: boolean;
  errorMessage: string | null;
}
