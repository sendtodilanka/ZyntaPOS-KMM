// ── Diagnostic Session Types (TODO-006 Remote Diagnostic Access) ──────────────

export type DiagnosticSessionStatus = 'PENDING_CONSENT' | 'ACTIVE' | 'EXPIRED' | 'REVOKED';
export type DiagnosticDataScope = 'READ_ONLY_DIAGNOSTICS' | 'FULL_READ_ONLY';
export type DiagnosticVisitType = 'REMOTE' | 'ON_SITE';

export interface DiagnosticSession {
  id: string;
  storeId: string;
  technicianId: string;
  requestedBy: string;
  dataScope: DiagnosticDataScope;
  status: DiagnosticSessionStatus;
  visitType: DiagnosticVisitType;
  hardwareScope: string | null;
  consentGrantedAt: number | null;
  expiresAt: number;
  revokedAt: number | null;
  revokedBy: string | null;
  createdAt: number;
  /** Raw JIT token — only present in the creation response, never stored. */
  token?: string;
}

export interface CreateDiagnosticSessionRequest {
  storeId: string;
  technicianId: string;
  dataScope: DiagnosticDataScope;
  visitType?: DiagnosticVisitType;
}
