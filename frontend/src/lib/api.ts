import {
  ApiErrorResponse,
  Asset,
  AssetAllocation,
  AuditVerificationResult,
  Beneficiary,
  DisclosureResponse,
  DocumentResponse,
  VerificationConfirmationResult,
  WillResponse,
} from './types';

export class ApiException extends Error {
  public status: number;
  public code: string;
  public path: string;
  public details?: Record<string, string>;

  constructor(errorResponse: ApiErrorResponse) {
    super(errorResponse.message);
    this.name = 'ApiException';
    this.status = errorResponse.status;
    this.code = errorResponse.code;
    this.path = errorResponse.path;
    this.details = errorResponse.details;
  }
}

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let errorData: ApiErrorResponse;
    try {
      errorData = await res.json();
    } catch {
      errorData = {
        timestamp: new Date().toISOString(),
        status: res.status,
        code: res.status >= 500 ? 'INTERNAL_SERVER_ERROR' : 'HTTP_ERROR',
        message: res.statusText || 'An unexpected error occurred',
        path: res.url,
      };
    }
    throw new ApiException(errorData);
  }
  return res.json() as Promise<T>;
}

export const api = {
  // --- Wills / Estate ---
  async getWill(willId: string): Promise<WillResponse> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}`);
    return handleResponse<WillResponse>(res);
  },

  async createWill(title: string, ownerId?: string): Promise<WillResponse> {
    const res = await fetch(`${API_BASE}/api/wills`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, ownerId }),
    });
    return handleResponse<WillResponse>(res);
  },

  async checkIn(willId: string): Promise<WillResponse> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}/check-in`, {
      method: 'POST',
    });
    return handleResponse<WillResponse>(res);
  },

  async cancelWill(willId: string, reason?: string): Promise<WillResponse> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}/cancel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    });
    return handleResponse<WillResponse>(res);
  },

  async scheduleRelease(willId: string): Promise<WillResponse> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}/schedule-release`, {
      method: 'POST',
    });
    return handleResponse<WillResponse>(res);
  },

  async executeRelease(willId: string): Promise<unknown> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}/execute-release`, {
      method: 'POST',
    });
    return handleResponse<unknown>(res);
  },

  async listAssets(willId: string): Promise<Asset[]> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}/assets`);
    return handleResponse<Asset[]>(res);
  },

  async addAsset(willId: string, data: Partial<Asset>): Promise<Asset> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}/assets`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<Asset>(res);
  },

  async listBeneficiaries(willId: string): Promise<Beneficiary[]> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}/beneficiaries`);
    return handleResponse<Beneficiary[]>(res);
  },

  async addBeneficiary(willId: string, data: Partial<Beneficiary>): Promise<Beneficiary> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}/beneficiaries`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<Beneficiary>(res);
  },

  async listAllocations(willId: string): Promise<AssetAllocation[]> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}/allocations`);
    return handleResponse<AssetAllocation[]>(res);
  },

  async allocateAsset(
    willId: string,
    assetId: string,
    beneficiaryId: string,
    sharePercentage: number,
    instructions?: string
  ): Promise<AssetAllocation> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}/allocations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ assetId, beneficiaryId, sharePercentage, instructions }),
    });
    return handleResponse<AssetAllocation>(res);
  },

  async verifyAuditChain(willId: string): Promise<AuditVerificationResult> {
    const res = await fetch(`${API_BASE}/api/wills/${willId}/audit/verify`);
    return handleResponse<AuditVerificationResult>(res);
  },

  // --- Documents ---
  async uploadDocument(willId: string, file: File, ownerId?: string): Promise<DocumentResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('willId', willId);
    if (ownerId) {
      formData.append('ownerId', ownerId);
    }

    const res = await fetch(`${API_BASE}/api/documents/upload`, {
      method: 'POST',
      body: formData,
    });
    return handleResponse<DocumentResponse>(res);
  },

  async listDocuments(willId: string): Promise<DocumentResponse[]> {
    const res = await fetch(`${API_BASE}/api/documents/will/${willId}`);
    return handleResponse<DocumentResponse[]>(res);
  },

  getDownloadUrl(documentId: string, willId: string): string {
    return `${API_BASE}/api/documents/${documentId}/download?willId=${encodeURIComponent(willId)}`;
  },

  // --- Verification ---
  async confirmVerification(token: string): Promise<VerificationConfirmationResult> {
    const res = await fetch(`${API_BASE}/api/verification/confirm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
    return handleResponse<VerificationConfirmationResult>(res);
  },

  // --- Controlled Disclosure ---
  async getDisclosure(token: string): Promise<DisclosureResponse> {
    const res = await fetch(`${API_BASE}/api/disclosure/${encodeURIComponent(token)}`);
    return handleResponse<DisclosureResponse>(res);
  },

  getDisclosedDocumentDownloadUrl(token: string, documentId: string): string {
    return `${API_BASE}/api/disclosure/${encodeURIComponent(token)}/document/${encodeURIComponent(documentId)}`;
  },
};
