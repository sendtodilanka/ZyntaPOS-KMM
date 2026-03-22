export type ConfigCategory = 'feature_flags' | 'tax_rates' | 'system' | 'notifications' | 'security';

export interface FeatureFlag {
  key: string;
  name: string;
  description: string;
  enabled: boolean;
  category: string;
  editionsAvailable: string[];
  lastModified: string;
  modifiedBy: string;
}

export interface SystemConfig {
  key: string;
  value: string | number | boolean;
  type: 'string' | 'number' | 'boolean' | 'json';
  description: string;
  category: ConfigCategory;
  editable: boolean;
  sensitive: boolean;
}

export interface ConfigUpdatePayload {
  key: string;
  value: string | number | boolean;
}
