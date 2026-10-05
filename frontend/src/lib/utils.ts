import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { SuccessionState, UIState } from './types';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatDate(isoString?: string): string {
  if (!isoString) return 'N/A';
  try {
    const d = new Date(isoString);
    return d.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return isoString;
  }
}

export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

export function getStateBadgeColor(state: SuccessionState): string {
  switch (state) {
    case 'ACTIVE':
      return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
    case 'INACTIVITY_WARNING':
      return 'bg-amber-500/10 text-amber-400 border-amber-500/30';
    case 'FINAL_WARNING':
      return 'bg-orange-500/10 text-orange-400 border-orange-500/30';
    case 'VERIFICATION_PENDING':
      return 'bg-purple-500/10 text-purple-400 border-purple-500/30';
    case 'VERIFIED':
      return 'bg-blue-500/10 text-blue-400 border-blue-500/30';
    case 'RELEASE_PENDING':
      return 'bg-indigo-500/10 text-indigo-400 border-indigo-500/30';
    case 'EXECUTING':
      return 'bg-yellow-500/10 text-yellow-400 border-yellow-500/30 animate-pulse';
    case 'EXECUTED':
      return 'bg-gray-500/10 text-gray-400 border-gray-500/30';
    default:
      return 'bg-zinc-500/10 text-zinc-400 border-zinc-500/30';
  }
}

export function mapErrorToUIState(err: unknown): { state: UIState; message: string } {
  if (err instanceof Error && 'code' in err) {
    const apiErr = err as unknown as { code: string; status: number; message: string };
    switch (apiErr.code) {
      case 'INVALID_VERIFICATION_TOKEN':
      case 'INVALID_DISCLOSURE_TOKEN':
      case 'INVALID_ARGUMENT':
        return { state: 'validation_error', message: apiErr.message };
      case 'VERIFICATION_TOKEN_EXPIRED':
      case 'DISCLOSURE_TOKEN_EXPIRED':
        return { state: 'expired', message: 'The access token has expired.' };
      case 'VERIFICATION_TOKEN_REVOKED':
      case 'DISCLOSURE_TOKEN_REVOKED':
        return { state: 'conflict', message: 'The access token has been revoked.' };
      case 'VERIFICATION_TOKEN_ALREADY_USED':
      case 'ALREADY_CONFIRMED':
        return { state: 'conflict', message: 'This token has already been confirmed.' };
      case 'INVALID_STATE_TRANSITION':
      case 'INVALID_STATE':
      case 'DISCLOSURE_NOT_READY':
        return { state: 'conflict', message: apiErr.message };
      case 'DOCUMENT_NOT_FOUND':
        return { state: 'not_found', message: 'Requested resource not found.' };
      case 'FORBIDDEN':
        return { state: 'forbidden', message: 'Access denied.' };
      default:
        if (apiErr.status >= 500) {
          return { state: 'server_error', message: 'A secure backend error occurred. Please try again.' };
        }
        return { state: 'server_error', message: apiErr.message };
    }
  }
  return { state: 'network_error', message: 'Network connection error. Please check your connection and retry.' };
}
