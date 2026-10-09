import {
  ApiErrorResponse,
  Asset,
  AssetAllocation,
  AuditLogDto,
  AuditVerificationResult,
  AuthResponse,
  Beneficiary,
  DisclosureResponse,
  DocumentResponse,
  User,
  VerificationConfirmationResult,
  WillContactDetailed,
  WillResponse,
  WillReview,
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

// Prefer an explicitly configured public API origin. Without one, use same-origin /api
// requests so Next.js can proxy through rewrites in development/deployment.
const API_BASE = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '');
const TOKEN_STORAGE_KEY = 'digital_will_auth_token';

export function getAuthToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function setAuthToken(token: string): void {
  if (typeof window === 'undefined') return;
  localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

export function clearAuthToken(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_STORAGE_KEY);
}

function getAuthHeaders(): HeadersInit {
  const token = getAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

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
  // --- Authentication ---
  async register(email: string, password: string, fullName: string): Promise<AuthResponse> {
    const res = await fetch(`${API_BASE}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, fullName }),
    });
    const data = await handleResponse<AuthResponse>(res);
    setAuthToken(data.token);
    return data;
  },

  async login(email: string, password: string): Promise<AuthResponse> {
    const res = await fetch(`${API_BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    const data = await handleResponse<AuthResponse>(res);
    setAuthToken(data.token);
    return data;
  },

  async getMe(): Promise<User> {
    const res = await fetch(`${API_BASE}/api/auth/me`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<User>(res);
  },

  async logout(): Promise<void> {
    try {
      await fetch(`${API_BASE}/api/auth/logout`, {
        method: 'POST',
        headers: getAuthHeaders(),
      });
    } finally {
      clearAuthToken();
    }
  },

  // --- Wills / Estate ---
  async getMyWill(): Promise<WillResponse | null> {
    try {
      const res = await fetch(`${API_BASE}/api/wills/my`, {
        headers: getAuthHeaders(),
      });
      if (res.status === 404) return null;
      return await handleResponse<WillResponse>(res);
    } catch (err) {
      if (err instanceof ApiException && err.status === 404) {
        return null;
      }
      throw err;
    }
  },

  async getWill(willId: string): Promise<WillResponse> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<WillResponse>(res);
  },

  async createWill(title: string): Promise<WillResponse> {
    const res = await fetch(`${API_BASE}/api/wills`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ title }),
    });
    return handleResponse<WillResponse>(res);
  },

  async checkIn(willId: string): Promise<WillResponse> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/check-in`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });
    return handleResponse<WillResponse>(res);
  },

  async cancelWill(willId: string, reason?: string): Promise<WillResponse> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/cancel`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ reason }),
    });
    return handleResponse<WillResponse>(res);
  },

  async scheduleRelease(willId: string): Promise<WillResponse> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/schedule-release`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });
    return handleResponse<WillResponse>(res);
  },

  async executeRelease(willId: string): Promise<unknown> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/execute-release`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });
    return handleResponse<unknown>(res);
  },

  // --- Assets ---
  async listAssets(willId: string): Promise<Asset[]> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/assets`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<Asset[]>(res);
  },

  async addAsset(willId: string, data: Partial<Asset>): Promise<Asset> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/assets`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse<Asset>(res);
  },

  async deleteAsset(willId: string, assetId: string): Promise<void> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/assets/${encodeURIComponent(assetId)}`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
    });
    await handleResponse<{ message: string }>(res);
  },

  // --- Beneficiaries ---
  async listBeneficiaries(willId: string): Promise<Beneficiary[]> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/beneficiaries`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<Beneficiary[]>(res);
  },

  async addBeneficiary(willId: string, data: Partial<Beneficiary>): Promise<Beneficiary> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/beneficiaries`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse<Beneficiary>(res);
  },

  async deleteBeneficiary(willId: string, beneficiaryId: string): Promise<void> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/beneficiaries/${encodeURIComponent(beneficiaryId)}`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
    });
    await handleResponse<{ message: string }>(res);
  },

  // --- Allocations ---
  async listAllocations(willId: string): Promise<AssetAllocation[]> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/allocations`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<AssetAllocation[]>(res);
  },

  async allocateAsset(
    willId: string,
    assetId: string,
    beneficiaryId: string,
    sharePercentage: number,
    instructions?: string
  ): Promise<AssetAllocation> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/allocations`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ assetId, beneficiaryId, sharePercentage, instructions }),
    });
    return handleResponse<AssetAllocation>(res);
  },

  async deleteAllocation(willId: string, allocationId: string): Promise<void> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/allocations/${encodeURIComponent(allocationId)}`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
    });
    await handleResponse<{ message: string }>(res);
  },

  // --- Trusted Contacts ---
  async listContacts(willId: string): Promise<WillContactDetailed[]> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/contacts`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<WillContactDetailed[]>(res);
  },

  async addContact(willId: string, name: string, email: string): Promise<WillContactDetailed> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/contacts`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ name, email }),
    });
    return handleResponse<WillContactDetailed>(res);
  },

  async deactivateContact(willId: string, contactId: string): Promise<void> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/contacts/${encodeURIComponent(contactId)}`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
    });
    await handleResponse<{ message: string }>(res);
  },

  // --- Review & Audit ---
  async getWillReview(willId: string): Promise<WillReview> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/review`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<WillReview>(res);
  },

  async getWillAuditLogs(willId: string): Promise<AuditLogDto[]> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/audit`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<AuditLogDto[]>(res);
  },

  async verifyAuditChain(willId: string): Promise<AuditVerificationResult> {
    const res = await fetch(`${API_BASE}/api/wills/${encodeURIComponent(willId)}/audit/verify`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<AuditVerificationResult>(res);
  },

  // --- Documents ---
  async uploadDocument(willId: string, file: File): Promise<DocumentResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('willId', willId);

    const token = getAuthToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const res = await fetch(`${API_BASE}/api/documents/upload`, {
      method: 'POST',
      headers,
      body: formData,
    });
    return handleResponse<DocumentResponse>(res);
  },

  async listDocuments(willId: string): Promise<DocumentResponse[]> {
    const res = await fetch(`${API_BASE}/api/documents/will/${encodeURIComponent(willId)}`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<DocumentResponse[]>(res);
  },

  async downloadDocument(documentId: string, willId: string, fileName: string): Promise<void> {
    const token = getAuthToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const res = await fetch(`${API_BASE}/api/documents/${encodeURIComponent(documentId)}/download?willId=${encodeURIComponent(willId)}`, {
      headers,
    });

    if (!res.ok) {
      return handleResponse(res);
    }

    const blob = await res.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
  },

  // --- Public Contact Verification ---
  async confirmVerification(token: string): Promise<VerificationConfirmationResult> {
    const res = await fetch(`${API_BASE}/api/verification/confirm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
    return handleResponse<VerificationConfirmationResult>(res);
  },

  // --- Public Controlled Disclosure ---
  async getDisclosure(token: string): Promise<DisclosureResponse> {
    const res = await fetch(`${API_BASE}/api/disclosure/${encodeURIComponent(token)}`);
    return handleResponse<DisclosureResponse>(res);
  },

  async downloadDisclosedDocument(token: string, documentId: string, fileName: string): Promise<void> {
    const res = await fetch(`${API_BASE}/api/disclosure/${encodeURIComponent(token)}/document/${encodeURIComponent(documentId)}`);
    if (!res.ok) {
      return handleResponse(res);
    }
    const blob = await res.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
  },
};
