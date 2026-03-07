export interface PagedResponse<T> {
  data: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface ErrorResponse {
  error: string;
  message: string;
  statusCode: number;
  timestamp: string;
}

export interface SuccessResponse<T = void> {
  data: T;
  message?: string;
}

export type SortDirection = 'asc' | 'desc';

export interface PaginationParams {
  page?: number;
  size?: number;
  sort?: string;
  direction?: SortDirection;
}

export interface ApiError extends Error {
  statusCode: number;
  response?: ErrorResponse;
}
