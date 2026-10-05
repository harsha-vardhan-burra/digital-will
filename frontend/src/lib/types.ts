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
  description: string;
  encryptedAccessData?: string;
  instructions?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Beneficiary {
  id: string;
  willId: string;
  name: string;
  email: string;
  relationship: string;
  createdAt: string;
}

export interface AssetAllocation {
  id: string;
  assetId: string;
  beneficiaryId: string;
  sharePercentage: number;
  instructions?: string;
  allocatedAt: string;
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

export interface AuditVerificationResult {
  valid: boolean;
  totalEntries: number;
  lastSequenceNumber: number;
  tipHash: string;
  errorMessage?: string;
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
  instructions: string;
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
