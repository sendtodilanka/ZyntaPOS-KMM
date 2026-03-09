export type TicketStatus = 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'PENDING_CUSTOMER' | 'RESOLVED' | 'CLOSED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TicketCategory = 'HARDWARE' | 'SOFTWARE' | 'SYNC' | 'BILLING' | 'OTHER';

export interface Ticket {
  id: string;
  ticketNumber: string;
  storeId: string | null;
  licenseId: string | null;
  createdBy: string;
  createdByName: string;
  customerName: string;
  customerEmail: string | null;
  customerPhone: string | null;
  assignedTo: string | null;
  assignedToName: string | null;
  assignedAt: number | null;
  title: string;
  description: string;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  resolvedBy: string | null;
  resolvedAt: number | null;
  resolutionNote: string | null;
  timeSpentMin: number | null;
  slaDueAt: number | null;
  slaBreached: boolean;
  createdAt: number;
  updatedAt: number;
  comments?: TicketComment[];
}

export interface TicketComment {
  id: string;
  ticketId: string;
  authorId: string;
  authorName: string;
  body: string;
  isInternal: boolean;
  createdAt: number;
}

export interface TicketsPage {
  items: Ticket[];
  total: number;
  page: number;
  size: number;
}

export interface CreateTicketRequest {
  storeId?: string;
  licenseId?: string;
  customerName: string;
  customerEmail?: string;
  customerPhone?: string;
  title: string;
  description: string;
  category: TicketCategory;
  priority: TicketPriority;
}

export interface UpdateTicketRequest {
  title?: string;
  description?: string;
  priority?: TicketPriority;
  status?: TicketStatus;
}

export interface AssignTicketRequest {
  assigneeId: string;
}

export interface ResolveTicketRequest {
  resolutionNote: string;
  timeSpentMin: number;
}

export interface AddCommentRequest {
  body: string;
  isInternal: boolean;
}

export interface TicketFilter {
  status?: TicketStatus;
  priority?: TicketPriority;
  category?: TicketCategory;
  assignedTo?: string;
  storeId?: string;
  search?: string;
  page?: number;
  size?: number;
}
