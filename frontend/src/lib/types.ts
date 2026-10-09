export type SuccessionState =
  | 'ACTIVE'
  | 'INACTIVITY_WARNING'
  | 'FINAL_WARNING'
  | 'VERIFICATION_PENDING'
  | 'VERIFIED'
  | 'RELEASE_PENDING'
  | 'EXECUTING'
  | 'EXECUTED';

export type UIState =
  | 'idle'
  | 'loading'
  | 'success'
  | 'validation_error'
  | 'unauthorized'
  | 'forbidden'
  | 'not_found'
  | 'conflict'
  | 'expired'
  | 'server_error'
  | 'network_error'
  | 'retrying';

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  error?: string;
  details?: Record<string, string>;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  createdAt: string;
  willId: string | null;
}

// Mirrors AuthService.AuthResponse. The API does not nest a `user` object.
export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  fullName: string;
  willId: string | null;
}

// Mirrors EstateController.WillResponse exactly. Fields not returned by the API
// must not be treated as authoritative frontend state.
export interface WillResponse {
  id: string;
  ownerId: string;
  title: string;
  state: SuccessionState;
  lastVerifiedActivityAt: string;
  createdAt: string;
  updatedAt: string;
}

export type AssetCategory =
  | 'REAL_ESTATE'
  | 'BANK_ACCOUNT'
  | 'INVESTMENT'
  | 'DIGITAL_ACCOUNT'
  | 'INTELLECTUAL_PROPERTY'
  | 'PHYSICAL_ASSET'
  | 'OTHER';

export interface Asset {
  id: string;
  willId: string;
  title: string;
  category: AssetCategory;
  description: string | null;
  encryptedAccessData: string | null;
  instructions: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Beneficiary {
  id: string;
  willId: string;
  name: string;
  email: string;
  relationship: string | null;
  createdAt: string;
}

export interface AssetAllocation {
  id: string;
  assetId: string;
  beneficiaryId: string;
  sharePercentage: number;
  instructions: string | null;
  createdAt: string;
}

export interface WillContactDetailed {
  associationId: string;
  contactId: string;
  willId: string;
  name: string;
  email: string;
  isActive: boolean;
  addedAt: string;
  confirmedCurrentCycle: boolean;
}

export interface AllocationReviewItem {
  allocationId: string;
  assetId: string;
  assetTitle: string;
  beneficiaryId: string;
  beneficiaryName: string;
  sharePercentage: number;
  instructions: string | null;
}

export interface WillReview {
  willId: string;
  title: string;
  state: SuccessionState;
  createdAt: string;
  lastVerifiedActivityAt: string;
  assetCount: number;
  beneficiaryCount: number;
  allocationCount: number;
  documentCount: number;
  activeTrustedContactCount: number;
  hasAssets: boolean;
  hasBeneficiaries: boolean;
  allAssetsFullyAllocated: boolean;
  hasQuorumContacts: boolean;
  readyForActivation: boolean;
  warnings: string[];
  assets: Asset[];
  beneficiaries: Beneficiary[];
  allocations: AllocationReviewItem[];
}

// Mirrors EstateController.AuditLogDto; audit fields are display-only and are
// always sourced from the backend response.
export interface AuditLogDto {
  id: string;
  sequenceNumber: number;
  action: string;
  status: string;
  actorType: string;
  resourceType: string;
  resourceId: string | null;
  createdAt: string;
  prevHash: string | null;
  entryHash: string;
  detailsJson: string | null;
}

export interface DocumentResponse {
  id: string;
  willId: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  checksumSha256: string;
  createdAt: string;
}

// Mirrors audit.service.AuditVerificationResult exactly.
export interface AuditVerificationResult {
  valid: boolean;
  checkedEntries: number;
  failureReason: string | null;
  failedSequenceNumber: number | null;
}

export interface VerificationConfirmationResult {
  confirmed: boolean;
  quorumReached: boolean;
  alreadyConfirmed: boolean;
  state: string;
}

export interface DisclosedAssetItem {
  assetId: string;
  title: string;
  category: string;
  sharePercentage: number;
  instructions: string | null;
}

export interface BeneficiaryDisclosurePackage {
  beneficiaryId: string;
  name: string;
  disclosedAt: string;
  allocations: DisclosedAssetItem[];
}

export interface DisclosureResponse {
  willId: string;
  beneficiaryId: string;
  packagePayloadJson: string;
  accessedAt: string;
}
