'use client';

import React, { useState, useEffect, use } from 'react';
import { api } from '@/lib/api';
import { BeneficiaryDisclosurePackage, UIState } from '@/lib/types';
import { formatDate, mapErrorToUIState } from '@/lib/utils';
import { ShieldCheck, Lock, AlertTriangle, Clock, XCircle, RotateCcw, Building, Landmark, HardDrive } from 'lucide-react';

export default function DisclosurePage({ params }: { params: Promise<{ token: string }> }) {
  const resolvedParams = use(params);
  const token = resolvedParams.token;

  const [uiState, setUiState] = useState<UIState>('loading');
  const [disclosurePackage, setDisclosurePackage] = useState<BeneficiaryDisclosurePackage | null>(null);
  const [willId, setWillId] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchDisclosure = async () => {
    setUiState('loading');
    setErrorMessage(null);
    try {
      const res = await api.getDisclosure(token);
      setWillId(res.willId);
      const parsed = JSON.parse(res.packagePayloadJson) as BeneficiaryDisclosurePackage;
      setDisclosurePackage(parsed);
      setUiState('success');
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setUiState(state);
      setErrorMessage(message);
    }
  };

  useEffect(() => {
    fetchDisclosure();
  }, [token]);

  const getCategoryIcon = (category: string) => {
    switch (category) {
      case 'REAL_ESTATE':
        return <Building className="w-5 h-5 text-emerald-400" />;
      case 'BANK_ACCOUNT':
      case 'INVESTMENT':
        return <Landmark className="w-5 h-5 text-blue-400" />;
      default:
        return <HardDrive className="w-5 h-5 text-purple-400" />;
    }
  };

  return (
    <div className="max-w-2xl mx-auto space-y-8">
      <div>
        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-blue-500/30 bg-blue-500/10 text-blue-400 text-xs font-medium mb-3">
          <ShieldCheck className="w-3.5 h-3.5" />
          <span>Controlled Estate Disclosure • Verified Beneficiary</span>
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-zinc-100">
          Estate Succession Disclosure Package
        </h1>
        <p className="text-sm text-zinc-400 mt-1">
          Confidential estate distribution instructions released upon verified succession execution.
        </p>
      </div>

      {/* Loading state */}
      {uiState === 'loading' && (
        <div className="p-12 text-center text-zinc-500 text-sm flex items-center justify-center gap-2">
          <RotateCcw className="w-4 h-4 animate-spin text-blue-400" />
          <span>Authenticating token and decrypting disclosure package...</span>
        </div>
      )}

      {/* Expired state */}
      {uiState === 'expired' && (
        <div className="p-6 rounded-xl border border-amber-500/30 bg-amber-500/10 text-amber-300 space-y-3">
          <div className="flex items-center gap-2 font-semibold text-amber-400">
            <Clock className="w-5 h-5" />
            <span>Disclosure Token Expired</span>
          </div>
          <p className="text-xs text-amber-200/80">
            This disclosure access token has exceeded its 30-day time-to-live window. Please contact the estate administrator.
          </p>
        </div>
      )}

      {/* Conflict / Revoked / Not Ready */}
      {uiState === 'conflict' && (
        <div className="p-6 rounded-xl border border-orange-500/30 bg-orange-500/10 text-orange-300 space-y-3">
          <div className="flex items-center gap-2 font-semibold text-orange-400">
            <AlertTriangle className="w-5 h-5" />
            <span>Disclosure Unavailable</span>
          </div>
          <p className="text-xs text-orange-200/80">{errorMessage}</p>
        </div>
      )}

      {/* Validation / Not Found Error */}
      {(uiState === 'validation_error' || uiState === 'not_found') && (
        <div className="p-6 rounded-xl border border-red-500/30 bg-red-500/10 text-red-300 space-y-3">
          <div className="flex items-center gap-2 font-semibold text-red-400">
            <XCircle className="w-5 h-5" />
            <span>Invalid Disclosure Token</span>
          </div>
          <p className="text-xs text-red-200/80">
            The provided disclosure link is invalid or unrecognized. Ensure you clicked the exact link received.
          </p>
        </div>
      )}

      {/* Server / Network Error with retry */}
      {(uiState === 'server_error' || uiState === 'network_error') && (
        <div className="p-6 rounded-xl border border-red-500/30 bg-red-500/10 text-red-300 space-y-3">
          <div className="flex items-center gap-2 font-semibold text-red-400">
            <XCircle className="w-5 h-5" />
            <span>{uiState === 'network_error' ? 'Connection Error' : 'System Error'}</span>
          </div>
          <p className="text-xs text-red-200/80">{errorMessage}</p>
          <button
            onClick={fetchDisclosure}
            className="px-3 py-1.5 bg-red-600 hover:bg-red-500 text-white rounded text-xs font-medium flex items-center gap-1.5 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Retry Connection</span>
          </button>
        </div>
      )}

      {/* Success View */}
      {uiState === 'success' && disclosurePackage && (
        <div className="space-y-6">
          {/* Header Card */}
          <div className="p-6 rounded-xl border border-zinc-800 bg-zinc-900/60 space-y-2">
            <span className="text-xs text-zinc-500 font-mono">Will ID: {willId}</span>
            <h2 className="text-lg font-bold text-zinc-100">
              Beneficiary: <span className="text-emerald-400">{disclosurePackage.name}</span>
            </h2>
            <div className="text-xs text-zinc-400 flex items-center gap-4 pt-1">
              <span>Released At: <strong>{formatDate(disclosurePackage.disclosedAt)}</strong></span>
              <span>Allocated Items: <strong>{disclosurePackage.allocations.length}</strong></span>
            </div>
          </div>

          {/* Allocated Assets List */}
          <div className="space-y-4">
            <h3 className="text-sm font-semibold text-zinc-300 uppercase tracking-wider text-xs">
              Your Allocated Assets & Succession Instructions
            </h3>

            {disclosurePackage.allocations.length === 0 ? (
              <div className="p-6 rounded-xl border border-zinc-800 bg-zinc-900/20 text-center text-xs text-zinc-500">
                No specific assets allocated under this package.
              </div>
            ) : (
              <div className="space-y-3">
                {disclosurePackage.allocations.map((item, idx) => (
                  <div
                    key={item.assetId || idx}
                    className="p-5 rounded-xl border border-zinc-800 bg-zinc-900/40 space-y-3 hover:border-zinc-700 transition-colors"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div className="p-2 rounded-lg bg-zinc-800/80 border border-zinc-700/60">
                          {getCategoryIcon(item.category)}
                        </div>
                        <div>
                          <h4 className="text-sm font-semibold text-zinc-100">{item.title}</h4>
                          <span className="text-[10px] text-zinc-500 font-mono uppercase">{item.category}</span>
                        </div>
                      </div>
                      <span className="px-2.5 py-1 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 text-xs font-bold font-mono">
                        {item.sharePercentage}% Share
                      </span>
                    </div>

                    {item.instructions && (
                      <div className="p-3 rounded-lg bg-zinc-950/60 border border-zinc-800/80 text-xs text-zinc-300">
                        <span className="text-[10px] uppercase font-semibold text-zinc-500 block mb-1">Instructions</span>
                        <p>{item.instructions}</p>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Privacy Guarantee Note */}
          <div className="p-4 rounded-lg bg-zinc-900/30 border border-zinc-800/60 text-xs text-zinc-500 flex items-start gap-2">
            <Lock className="w-4 h-4 text-zinc-400 shrink-0 mt-0.5" />
            <p>
              In accordance with privacy and controlled disclosure policies, this package only displays the assets and instructions
              specifically allocated to you. Other estate allocations remain confidential.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
