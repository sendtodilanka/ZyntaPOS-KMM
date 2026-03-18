export type TicketStatus = 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'PENDING_CUSTOMER' | 'RESOLVED' | 'CLOSED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TicketCategory =
  | 'HARDWARE'
  | 'SOFTWARE'
  | 'SYNC'
  | 'INVENTORY'
  | 'BILLING'
  | 'ACCOUNT_SECURITY'
  | 'REPORTS'
  | 'SETUP'
  | 'OTHER';

export interface TicketCategoryMeta {
  label: string;
  subcategories: string[];
}

export const TICKET_CATEGORY_TREE: Record<TicketCategory, TicketCategoryMeta> = {
  HARDWARE: {
    label: 'Hardware & Devices',
    subcategories: [
      'Receipt Printer — Not Printing',
      'Receipt Printer — Paper Jam',
      'Receipt Printer — Connection Issue',
      'Barcode Scanner — Not Scanning',
      'Barcode Scanner — Misreads',
      'Cash Drawer — Won\'t Open',
      'Cash Drawer — Not Triggering',
      'Card Reader — Not Connecting',
      'Card Reader — PIN Pad Failure',
      'Touchscreen — Unresponsive',
      'Touchscreen — Calibration Issue',
      'Device Won\'t Power On',
      'Device Slow / Overheating',
      'Charging / Battery Issue',
      'Network / Router Down',
      'Wi-Fi Drops',
      'Other Hardware Issue',
    ],
  },
  SOFTWARE: {
    label: 'POS Application',
    subcategories: [
      'App Crash / Unexpected Error',
      'Slow Performance / Freezing',
      'Payment — Card Declined',
      'Payment — Split Payment Error',
      'Payment — Refund Failed',
      'Cart — Item Won\'t Add',
      'Cart — Discount Not Applying',
      'Cart — Incorrect Total',
      'Product — Wrong Price',
      'Product — Not Found / Missing',
      'Receipt — Wrong Details',
      'Receipt — Email Not Sent',
      'Barcode — Not Recognized',
      'Offline Mode Not Working',
      'Hold Order Issue',
      'UI / Screen Layout Broken',
      'Wrong Language / Currency',
      'Other POS Software Issue',
    ],
  },
  SYNC: {
    label: 'Sync & Data',
    subcategories: [
      'Data Not Syncing to Cloud',
      'Data Mismatch Between Devices',
      'Duplicate Orders / Records',
      'Missing or Lost Orders',
      'Missing or Lost Customers',
      'Inventory Out of Sync',
      'Cloud Backup Failed',
      'Restore / Recovery Failed',
      'Sync Stuck / Pending Forever',
      'Conflict Resolution Error',
      'Other Sync Issue',
    ],
  },
  INVENTORY: {
    label: 'Inventory Management',
    subcategories: [
      'Stock Count Incorrect',
      'Negative Stock Issue',
      'Stock Adjustment Error',
      'Low Stock Alert Not Triggering',
      'Too Many Low Stock Alerts',
      'Product Import — CSV Failed',
      'Product Import — Wrong Format',
      'Category Management Issue',
      'Supplier / Purchase Order Issue',
      'Barcode / SKU Mismatch',
      'Product Variant Issue',
      'Unit of Measure Problem',
      'Other Inventory Issue',
    ],
  },
  BILLING: {
    label: 'Billing & Licensing',
    subcategories: [
      'License Expired',
      'License Not Found / Invalid',
      'License Activation Failed',
      'Payment Failed / Declined',
      'Incorrect Invoice Amount',
      'Missing Invoice',
      'Subscription Upgrade Request',
      'Subscription Downgrade Request',
      'Refund Request',
      'Trial Expiry Issue',
      'Multiple Billing Charges',
      'Other Billing Issue',
    ],
  },
  ACCOUNT_SECURITY: {
    label: 'Account & Security',
    subcategories: [
      'Cannot Log In',
      'Forgot Password',
      'Password Reset Email Not Received',
      'PIN Not Working',
      'PIN Forgotten',
      'User Permission / Role Issue',
      'Can\'t Access Feature',
      'MFA Setup Issue',
      'MFA Recovery — Lost Authenticator',
      'Account Locked / Suspended',
      'Suspicious / Unauthorized Access',
      'Session Expiring Too Quickly',
      'Other Account Issue',
    ],
  },
  REPORTS: {
    label: 'Reports & Analytics',
    subcategories: [
      'Report Not Loading / Blank',
      'Incorrect Sales Totals',
      'Missing Transactions in Report',
      'Date / Time Filter Not Working',
      'CSV Export Failed',
      'PDF Export Failed',
      'Dashboard KPI Wrong',
      'Product Performance Report Issue',
      'Stock Report Wrong',
      'Staff / Shift Report Issue',
      'Other Report Issue',
    ],
  },
  SETUP: {
    label: 'Setup & Configuration',
    subcategories: [
      'Initial Store Setup / Onboarding',
      'Tax Rate Configuration',
      'Printer Setup / Not Found',
      'Network / Proxy Configuration',
      'Store Profile / Branding',
      'User Account Management',
      'Role & Permission Setup',
      'Multi-Store Configuration',
      'Backup & Restore Setup',
      'API / Integration Setup',
      'Other Setup Issue',
    ],
  },
  OTHER: {
    label: 'Other',
    subcategories: [
      'Feature Request',
      'Training / How-To Question',
      'General Inquiry',
      'Third-Party Integration Issue',
      'Performance Feedback',
      'Other',
    ],
  },
};

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
  replyToCustomer?: boolean;
}

export interface EmailThread {
  id: string;
  ticketId: string | null;
  messageId: string | null;
  inReplyTo: string | null;
  parentThreadId: string | null;
  fromAddress: string;
  fromName: string | null;
  toAddress: string;
  subject: string;
  bodyText: string | null;
  receivedAt: string;
  createdAt: string;
}

export interface BulkOperationResult {
  updated: number;
  failed: string[];
}

export interface BulkAssignRequest {
  ticketIds: string[];
  assigneeId: string;
}

export interface BulkResolveRequest {
  ticketIds: string[];
  resolutionNote: string;
}

export interface TicketMetrics {
  totalOpen: number;
  totalAssigned: number;
  totalResolved: number;
  totalClosed: number;
  slaBreached: number;
  avgResolutionTimeMin: number;
  openByPriority: Record<string, number>;
  openByCategory: Record<string, number>;
}

export interface TicketFilter {
  status?: TicketStatus;
  priority?: TicketPriority;
  category?: TicketCategory;
  assignedTo?: string;
  storeId?: string;
  search?: string;
  searchBody?: boolean;
  createdAfter?: number;
  createdBefore?: number;
  page?: number;
  size?: number;
}
