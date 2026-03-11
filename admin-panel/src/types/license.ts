export type LicenseEdition = 'STARTER' | 'PROFESSIONAL' | 'ENTERPRISE';
export type LicenseStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'SUSPENDED' | 'EXPIRING_SOON';

export interface License {
  id: string;
  key: string;
  customerId: string;
  customerName: string;
  edition: LicenseEdition;
  status: LicenseStatus;
  maxDevices: number;
  activeDevices: number;
  activatedAt: string;
  expiresAt: string | null;
  lastHeartbeatAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface LicenseDevice {
  id: string;
  licenseKey: string;
  deviceId: string;
  deviceName: string;
  appVersion: string;
  os: string;
  osVersion: string;
  firstSeenAt: string;
  lastSeenAt: string;
}

/** Response from GET /admin/licenses/{key} — includes both license and its registered devices. */
export interface LicenseWithDevices {
  license: License;
  devices: LicenseDevice[];
}

export interface LicenseStats {
  total: number;
  active: number;
  expired: number;
  revoked: number;
  suspended: number;
  expiringSoon: number;
  byEdition: Record<LicenseEdition, number>;
}

export interface CreateLicenseRequest {
  customerId: string;
  customerName?: string;
  edition: LicenseEdition;
  maxDevices: number;
  expiresAt?: string;
}

export interface UpdateLicenseRequest {
  edition?: LicenseEdition;
  maxDevices?: number;
  expiresAt?: string;
  clearExpiry?: boolean;
  status?: LicenseStatus;
  forceSync?: boolean;
}

export interface LicenseFilter {
  status?: LicenseStatus;
  edition?: LicenseEdition;
  search?: string;
  page?: number;
  size?: number;
}
