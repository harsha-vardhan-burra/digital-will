'use client';

import React, { useState, useEffect, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { api } from '@/lib/api';
import { UIState, VerificationConfirmationResult } from '@/lib/types';
import { mapErrorToUIState } from '@/lib/utils';
import { KeyRound, CheckCircle2, AlertTriangle, XCircle, RotateCcw, Clock, ShieldCheck } from 'lucide-react';

function VerifyContent() {
  const searchParams = useSearchParams();
  const [token, setToken] = useState('');
  const [uiState, setUiState] = useState<UIState>('idle');
  const [result, setResult] = useState<VerificationConfirmationResult | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const queryToken = searchParams.get('token');
    if (queryToken) {
      setToken(queryToken);
    }
  }, [searchParams]);

  const handleConfirm = async (overrideToken?: string) => {
    const tokenToSubmit = (overrideToken || token).trim();
    if (!tokenToSubmit) {
      setUiState('validation_error');
      setErrorMessage('Verification token cannot be empty.');
      return;
    }

    setUiState('loading');
    setErrorMessage(null);
    setResult(null);

    try {
      const res = await api.confirmVerification(tokenToSubmit);
      setResult(res);
      setUiState('success');
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setUiState(state);
      setErrorMessage(message);
    }
  };

  const handleRetry = () => {
    setUiState('retrying');
    setTimeout(() => {
      handleConfirm();
    }, 500);
  };

  return (
    <div className="max-w-xl mx-auto space-y-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-zinc-100 flex items-center gap-2">
          <KeyRound className="w-6 h-6 text-purple-400" />
          Trusted Contact Verification
        </h1>
        <p className="text-sm text-zinc-400 mt-1">
          Confirm that the testator is unreachable. 2-of-3 distinct confirmations transition the estate to verified state.
        </p>
      </div>

      {/* Confirmation Form */}
      <div className="p-6 rounded-xl border border-zinc-800 bg-zinc-900/50 space-y-4">
        <label className="block text-xs font-semibold uppercase tracking-wider text-zinc-400">
          Verification Action Token
        </label>
        <div className="flex gap-2">
          <input
            type="text"
            value={token}
            onChange={(e) => setToken(e.target.value)}
            placeholder="Enter secure verification token..."
            disabled={uiState === 'loading' || uiState === 'retrying'}
            className="flex-1 bg-zinc-950 border border-zinc-800 rounded-lg px-3 py-2 text-sm text-zinc-100 placeholder-zinc-600 focus:outline-none focus:ring-2 focus:ring-purple-500/50"
          />
          <button
            onClick={() => handleConfirm()}
            disabled={uiState === 'loading' || uiState === 'retrying'}
            className="px-4 py-2 bg-purple-600 hover:bg-purple-500 disabled:opacity-50 text-white rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
          >
            {uiState === 'loading' || uiState === 'retrying' ? (
              <>
                <RotateCcw className="w-4 h-4 animate-spin" />
                <span>Verifying...</span>
              </>
            ) : (
              <span>Confirm</span>
            )}
          </button>
        </div>
      </div>

      {/* Explicit UI State Feedbacks */}
      {uiState === 'success' && result && (
        <div className="p-5 rounded-xl border border-emerald-500/30 bg-emerald-500/10 text-emerald-300 space-y-3">
          <div className="flex items-center gap-2 font-semibold text-emerald-400">
            <CheckCircle2 className="w-5 h-5" />
            <span>Confirmation Successfully Recorded</span>
          </div>
          <div className="text-xs space-y-1 text-emerald-200/80">
            <p>• Resulting Estate State: <strong className="font-mono text-emerald-100">{result.state}</strong></p>
            <p>• Quorum Reached (2-of-3): <strong className="text-emerald-100">{result.quorumReached ? 'Yes (Verified)' : 'Pending additional confirmation'}</strong></p>
            {result.alreadyConfirmed && <p>• Note: This contact confirmation was already recorded previously in this cycle.</p>}
          </div>
        </div>
      )}

      {uiState === 'expired' && (
        <div className="p-5 rounded-xl border border-amber-500/30 bg-amber-500/10 text-amber-300 space-y-2">
          <div className="flex items-center gap-2 font-semibold text-amber-400">
            <Clock className="w-5 h-5" />
            <span>Verification Token Expired</span>
          </div>
          <p className="text-xs text-amber-200/80">
            This verification token has exceeded its validity window. If verification is still pending, request a reissued link.
          </p>
        </div>
      )}

      {uiState === 'conflict' && (
        <div className="p-5 rounded-xl border border-orange-500/30 bg-orange-500/10 text-orange-300 space-y-2">
          <div className="flex items-center gap-2 font-semibold text-orange-400">
            <AlertTriangle className="w-5 h-5" />
            <span>Token Already Used or Revoked</span>
          </div>
          <p className="text-xs text-orange-200/80">{errorMessage}</p>
        </div>
      )}

      {uiState === 'validation_error' && (
        <div className="p-4 rounded-xl border border-red-500/30 bg-red-500/10 text-red-300 space-y-1">
          <div className="flex items-center gap-2 font-semibold text-red-400">
            <XCircle className="w-4 h-4" />
            <span>Invalid Token</span>
          </div>
          <p className="text-xs text-red-200/80">{errorMessage}</p>
        </div>
      )}

      {(uiState === 'server_error' || uiState === 'network_error') && (
        <div className="p-5 rounded-xl border border-red-500/30 bg-red-500/10 text-red-300 space-y-3">
          <div className="flex items-center gap-2 font-semibold text-red-400">
            <XCircle className="w-5 h-5" />
            <span>{uiState === 'network_error' ? 'Connection Error' : 'Backend Unavailable'}</span>
          </div>
          <p className="text-xs text-red-200/80">{errorMessage}</p>
          <button
            onClick={handleRetry}
            className="px-3 py-1.5 bg-red-600 hover:bg-red-500 text-white rounded text-xs font-medium flex items-center gap-1.5 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Retry Verification</span>
          </button>
        </div>
      )}

      {/* Security Note */}
      <div className="p-4 rounded-lg bg-zinc-900/30 border border-zinc-800/60 text-xs text-zinc-500 flex items-start gap-2">
        <ShieldCheck className="w-4 h-4 text-zinc-400 shrink-0 mt-0.5" />
        <p>
          Tokens are cryptographically hashed using SHA-256 upon arrival and immediately invalidated after use.
          Raw token parameters are never retained in persistent storage.
        </p>
      </div>
    </div>
  );
}

export default function VerifyPage() {
  return (
    <Suspense fallback={<div className="text-center py-12 text-zinc-500 text-sm">Loading verification session...</div>}>
      <VerifyContent />
    </Suspense>
  );
}
