export type AdminRole = 'ADMIN' | 'SUPPORT' | 'VIEWER';
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

export interface AdminUser {
  id: string;
  username: string;
  email: string;
  name: string;
  role: AdminRole;
  storeId: string | null;
  storeName: string | null;
  status: UserStatus;
  lastLoginAt: string | null;
  createdAt: string;
  avatarUrl?: string;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  name: string;
  password: string;
  role: AdminRole;
  storeId?: string;
}

export interface UpdateUserRequest {
  name?: string;
  role?: AdminRole;
  status?: UserStatus;
  storeId?: string | null;
}

export interface UserFilter {
  role?: AdminRole;
  status?: UserStatus;
  storeId?: string;
  search?: string;
  page?: number;
  size?: number;
}
