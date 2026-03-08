// Admin panel roles (Zynta Solutions internal staff — not POS store users)
export type AdminRole = 'ADMIN' | 'OPERATOR' | 'FINANCE' | 'AUDITOR' | 'HELPDESK';

export type UserStatus = 'ACTIVE' | 'INACTIVE';

// Matches AdminUserResponse from Ktor backend
export interface AdminUser {
  id: string;
  email: string;
  name: string;
  role: AdminRole;
  mfaEnabled: boolean;
  isActive: boolean;
  lastLoginAt: number | null;   // epoch-ms
  createdAt: number;            // epoch-ms
}

export interface CreateUserRequest {
  email: string;
  name: string;
  role: AdminRole;
  password: string;
}

export interface UpdateUserRequest {
  name?: string;
  role?: AdminRole;
  isActive?: boolean;
}

export interface UserFilter {
  role?: AdminRole;
  status?: UserStatus;
  search?: string;
  page?: number;
  size?: number;
}
