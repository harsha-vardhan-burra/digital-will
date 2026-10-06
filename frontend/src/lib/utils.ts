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
      return 'bg-zinc-500/10 text-zinc-400 border-zinc-500/30';
    default:
      return 'bg-zinc-500/10 text-zinc-400 border-zinc-500/30';
  }
}

export function mapErrorToUIState(err: unknown): { state: UIState; message: string } {
  if (err instanceof Error && 'status' in err) {
    const apiErr = err as unknown as { code?: string; status: number; message: string };
    if (apiErr.status === 401) {
      return { state: 'unauthorized', message: 'Authentication required. Please log in.' };
    }
    if (apiErr.status === 403) {
      return { state: 'forbidden', message: apiErr.message || 'Access denied. You do not own this resource.' };
    }
    if (apiErr.status === 404) {
      return { state: 'not_found', message: apiErr.message || 'Requested resource not found.' };
    }
    if (apiErr.status === 409) {
      return { state: 'conflict', message: apiErr.message || 'Conflict detected. Operation not permitted in current state.' };
    }
    if (apiErr.status === 410) {
      return { state: 'expired', message: apiErr.message || 'The token has expired or been revoked.' };
    }
    if (apiErr.status === 400 || apiErr.status === 422) {
      return { state: 'validation_error', message: apiErr.message || 'Validation error. Please verify input data.' };
    }
    if (apiErr.status === 429) {
      return { state: 'retrying', message: 'Rate limit exceeded. Please wait and try again.' };
    }
    if (apiErr.status >= 500) {
      return { state: 'server_error', message: 'Backend service error. Please try again later.' };
    }
    return { state: 'server_error', message: apiErr.message || 'An unexpected error occurred.' };
  }
  if (err instanceof Error) {
    return { state: 'network_error', message: err.message || 'Network connection error.' };
  }
  return { state: 'network_error', message: 'Network connection error. Please check your connection and retry.' };
}
