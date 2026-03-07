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

export interface TaxRate {
  id: string;
  name: string;
  rate: number; // percentage e.g. 15.0
  description: string;
  applicableTo: string[];
  isDefault: boolean;
  country: string;
  region?: string;
  active: boolean;
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

export interface TaxRateCreatePayload {
  name: string;
  rate: number;
  description: string;
  applicableTo: string[];
  isDefault: boolean;
  country: string;
  region?: string;
}
