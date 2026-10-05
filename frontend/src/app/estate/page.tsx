'use client';

import React, { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import {
  Asset,
  AssetAllocation,
  AssetCategory,
  AuditVerificationResult,
  Beneficiary,
  SuccessionState,
  UIState,
  WillResponse,
} from '@/lib/types';
import { formatDate, getStateBadgeColor, mapErrorToUIState } from '@/lib/utils';
import {
  CheckCircle2,
  AlertCircle,
  Shield,
  RotateCcw,
  Plus,
  UserCheck,
  Ban,
  CalendarClock,
  Play,
  FileCheck2,
} from 'lucide-react';

const STATE_ORDER: SuccessionState[] = [
  'ACTIVE',
  'INACTIVITY_WARNING',
  'FINAL_WARNING',
  'VERIFICATION_PENDING',
  'VERIFIED',
  'RELEASE_PENDING',
  'EXECUTING',
  'EXECUTED',
];

export default function EstatePage() {
  const [willId, setWillId] = useState<string>('');
  const [will, setWill] = useState<WillResponse | null>(null);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [beneficiaries, setBeneficiaries] = useState<Beneficiary[]>([]);
  const [allocations, setAllocations] = useState<AssetAllocation[]>([]);
  const [auditResult, setAuditResult] = useState<AuditVerificationResult | null>(null);

  const [uiState, setUiState] = useState<UIState>('idle');
  const [actionState, setActionState] = useState<UIState>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Forms state
  const [showCreateWill, setShowCreateWill] = useState(false);
  const [newWillTitle, setNewWillTitle] = useState('');
  const [showAddAsset, setShowAddAsset] = useState(false);
  const [newAsset, setNewAsset] = useState<{ title: string; category: AssetCategory; description: string; instructions: string }>({
    title: '',
    category: 'REAL_ESTATE',
    description: '',
    instructions: '',
  });
  const [showAddBeneficiary, setShowAddBeneficiary] = useState(false);
  const [newBeneficiary, setNewBeneficiary] = useState({ name: '', email: '', relationship: '' });
  const [showAllocate, setShowAllocate] = useState(false);
  const [newAllocation, setNewAllocation] = useState({ assetId: '', beneficiaryId: '', sharePercentage: 100, instructions: '' });

  const loadWill = async (id: string) => {
    if (!id.trim()) return;
    setUiState('loading');
    setErrorMessage(null);
    try {
      const willData = await api.getWill(id);
      setWill(willData);
      setWillId(willData.id);

      const [assetList, beneList, allocList] = await Promise.all([
        api.listAssets(willData.id),
        api.listBeneficiaries(willData.id),
        api.listAllocations(willData.id),
      ]);
      setAssets(assetList);
      setBeneficiaries(beneList);
      setAllocations(allocList);

      setUiState('success');
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setUiState(state);
      setErrorMessage(message);
    }
  };

  const handleCreateWill = async () => {
    if (!newWillTitle.trim()) return;
    setActionState('loading');
    try {
      const created = await api.createWill(newWillTitle.trim());
      setShowCreateWill(false);
      setNewWillTitle('');
      await loadWill(created.id);
      setActionState('success');
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setActionState(state);
      setErrorMessage(message);
    }
  };

  const handleCheckIn = async () => {
    if (!will) return;
    setActionState('loading');
    try {
      const updated = await api.checkIn(will.id);
      setWill(updated);
      setActionState('success');
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setActionState(state);
      setErrorMessage(message);
      // Reload authoritative state in case of conflict
      await loadWill(will.id);
    }
  };

  const handleCancelSuccession = async () => {
    if (!will) return;
    setActionState('loading');
    try {
      const updated = await api.cancelWill(will.id, 'Owner explicit cancellation');
      setWill(updated);
      setActionState('success');
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setActionState(state);
      setErrorMessage(message);
      await loadWill(will.id);
    }
  };

  const handleScheduleRelease = async () => {
    if (!will) return;
    setActionState('loading');
    try {
      const updated = await api.scheduleRelease(will.id);
      setWill(updated);
      setActionState('success');
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setActionState(state);
      setErrorMessage(message);
      await loadWill(will.id);
    }
  };

  const handleExecuteRelease = async () => {
    if (!will) return;
    setActionState('loading');
    try {
      await api.executeRelease(will.id);
      await loadWill(will.id);
      setActionState('success');
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setActionState(state);
      setErrorMessage(message);
      await loadWill(will.id);
    }
  };

  const handleVerifyAudit = async () => {
    if (!will) return;
    setActionState('loading');
    try {
      const result = await api.verifyAuditChain(will.id);
      setAuditResult(result);
      setActionState('success');
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setActionState(state);
      setErrorMessage(message);
    }
  };

  const handleAddAsset = async () => {
    if (!will || !newAsset.title) return;
    try {
      await api.addAsset(will.id, newAsset);
      setShowAddAsset(false);
      setNewAsset({ title: '', category: 'REAL_ESTATE', description: '', instructions: '' });
      const assetList = await api.listAssets(will.id);
      setAssets(assetList);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to add asset');
    }
  };

  const handleAddBeneficiary = async () => {
    if (!will || !newBeneficiary.name || !newBeneficiary.email) return;
    try {
      await api.addBeneficiary(will.id, newBeneficiary);
      setShowAddBeneficiary(false);
      setNewBeneficiary({ name: '', email: '', relationship: '' });
      const beneList = await api.listBeneficiaries(will.id);
      setBeneficiaries(beneList);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to add beneficiary');
    }
  };

  const handleAllocate = async () => {
    if (!will || !newAllocation.assetId || !newAllocation.beneficiaryId) return;
    try {
      await api.allocateAsset(
        will.id,
        newAllocation.assetId,
        newAllocation.beneficiaryId,
        newAllocation.sharePercentage,
        newAllocation.instructions
      );
      setShowAllocate(false);
      setNewAllocation({ assetId: '', beneficiaryId: '', sharePercentage: 100, instructions: '' });
      const allocList = await api.listAllocations(will.id);
      setAllocations(allocList);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to allocate asset');
    }
  };

  return (
    <div className="space-y-8">
      {/* Top Header & Search/Create */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100 flex items-center gap-2">
            <Shield className="w-6 h-6 text-emerald-400" />
            Estate Succession Console
          </h1>
          <p className="text-sm text-zinc-400 mt-1">
            Authoritative estate state management, activity check-ins, allocations, and audit chain verification.
          </p>
        </div>
        <div className="flex gap-2 w-full sm:w-auto">
          <input
            type="text"
            placeholder="Load Will ID..."
            value={willId}
            onChange={(e) => setWillId(e.target.value)}
            className="bg-zinc-950 border border-zinc-800 rounded-lg px-3 py-1.5 text-xs text-zinc-100 placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
          <button
            onClick={() => loadWill(willId)}
            className="px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded-lg text-xs font-medium transition-colors"
          >
            Load
          </button>
          <button
            onClick={() => setShowCreateWill(true)}
            className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-medium transition-colors flex items-center gap-1"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>New Will</span>
          </button>
        </div>
      </div>

      {/* Create Will Modal / Dropdown */}
      {showCreateWill && (
        <div className="p-4 rounded-xl border border-zinc-700 bg-zinc-900/90 space-y-3">
          <h2 className="text-sm font-semibold text-zinc-200">Create New Digital Will</h2>
          <div className="flex gap-2">
            <input
              type="text"
              placeholder="Will Title (e.g. Master Succession Plan)"
              value={newWillTitle}
              onChange={(e) => setNewWillTitle(e.target.value)}
              className="flex-1 bg-zinc-950 border border-zinc-800 rounded-lg px-3 py-1.5 text-xs text-zinc-100"
            />
            <button
              onClick={handleCreateWill}
              disabled={actionState === 'loading'}
              className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-medium"
            >
              Create
            </button>
            <button
              onClick={() => setShowCreateWill(false)}
              className="px-3 py-1.5 bg-zinc-800 text-zinc-300 rounded-lg text-xs"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Error / Feedback banners */}
      {errorMessage && (
        <div className="p-4 rounded-xl border border-red-500/30 bg-red-500/10 text-red-300 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {uiState === 'loading' && (
        <div className="p-12 text-center text-zinc-500 text-sm flex items-center justify-center gap-2">
          <RotateCcw className="w-4 h-4 animate-spin text-emerald-400" />
          <span>Querying authoritative estate state...</span>
        </div>
      )}

      {!will && uiState !== 'loading' && (
        <div className="p-12 rounded-xl border border-dashed border-zinc-800 text-center space-y-3">
          <Shield className="w-8 h-8 text-zinc-600 mx-auto" />
          <p className="text-sm text-zinc-400">No Will loaded. Enter a Will ID or create a new one above.</p>
        </div>
      )}

      {will && uiState !== 'loading' && (
        <div className="space-y-8">
          {/* Will Overview Header */}
          <div className="p-6 rounded-xl border border-zinc-800 bg-zinc-900/40 space-y-4">
            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
              <div>
                <span className="text-xs font-mono text-zinc-500">ID: {will.id}</span>
                <h2 className="text-xl font-bold text-zinc-100">{will.title}</h2>
                <div className="text-xs text-zinc-400 mt-1 flex items-center gap-4">
                  <span>Last Activity: <strong>{formatDate(will.lastVerifiedActivityAt)}</strong></span>
                  <span>Created: {formatDate(will.createdAt)}</span>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className={`px-3 py-1 rounded-full text-xs font-semibold border ${getStateBadgeColor(will.state)}`}>
                  {will.state}
                </span>
              </div>
            </div>

            {/* Authoritative State Machine Stepper */}
            <div className="pt-4 border-t border-zinc-800">
              <span className="text-xs font-semibold uppercase tracking-wider text-zinc-500 block mb-2">
                Authoritative Succession State
              </span>
              <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-2">
                {STATE_ORDER.map((s, idx) => {
                  const currentIdx = STATE_ORDER.indexOf(will.state);
                  const isCurrent = s === will.state;
                  const isPast = idx < currentIdx;
                  return (
                    <div
                      key={s}
                      className={`p-2 rounded border text-center transition-all ${
                        isCurrent
                          ? 'border-emerald-500 bg-emerald-500/10 text-emerald-300 font-semibold ring-1 ring-emerald-500/30'
                          : isPast
                          ? 'border-zinc-800 bg-zinc-900/60 text-zinc-400'
                          : 'border-zinc-800/40 bg-zinc-950 text-zinc-600'
                      }`}
                    >
                      <div className="text-[10px] font-mono mb-1">{idx + 1}</div>
                      <div className="text-[11px] leading-tight truncate">{s}</div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* State Transition Action Controls */}
            <div className="pt-4 border-t border-zinc-800 flex flex-wrap gap-2">
              <button
                onClick={handleCheckIn}
                disabled={actionState === 'loading' || will.state === 'EXECUTED' || will.state === 'EXECUTING'}
                className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-40 text-white rounded text-xs font-medium flex items-center gap-1.5 transition-colors"
              >
                <UserCheck className="w-3.5 h-3.5" />
                <span>Record Activity Check-in</span>
              </button>

              {(will.state === 'INACTIVITY_WARNING' || will.state === 'FINAL_WARNING' || will.state === 'VERIFICATION_PENDING') && (
                <button
                  onClick={handleCancelSuccession}
                  disabled={actionState === 'loading'}
                  className="px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 text-amber-300 border border-amber-500/30 rounded text-xs font-medium flex items-center gap-1.5 transition-colors"
                >
                  <Ban className="w-3.5 h-3.5" />
                  <span>Cancel Succession Workflow</span>
                </button>
              )}

              {will.state === 'VERIFIED' && (
                <button
                  onClick={handleScheduleRelease}
                  disabled={actionState === 'loading'}
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white rounded text-xs font-medium flex items-center gap-1.5 transition-colors"
                >
                  <CalendarClock className="w-3.5 h-3.5" />
                  <span>Schedule Release</span>
                </button>
              )}

              {will.state === 'RELEASE_PENDING' && (
                <button
                  onClick={handleExecuteRelease}
                  disabled={actionState === 'loading'}
                  className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded text-xs font-medium flex items-center gap-1.5 transition-colors"
                >
                  <Play className="w-3.5 h-3.5" />
                  <span>Execute Release Claim</span>
                </button>
              )}

              <button
                onClick={handleVerifyAudit}
                disabled={actionState === 'loading'}
                className="px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded text-xs font-medium flex items-center gap-1.5 transition-colors ml-auto"
              >
                <FileCheck2 className="w-3.5 h-3.5 text-emerald-400" />
                <span>Verify Audit Chain</span>
              </button>
            </div>
          </div>

          {/* Audit Verification Modal / Badge */}
          {auditResult && (
            <div className="p-4 rounded-xl border border-zinc-800 bg-zinc-900/60 flex items-center justify-between text-xs">
              <div className="flex items-center gap-2">
                <CheckCircle2 className={`w-4 h-4 ${auditResult.valid ? 'text-emerald-400' : 'text-red-400'}`} />
                <span className="font-semibold text-zinc-200">
                  {auditResult.valid ? 'Audit Log Chain Verified Intact' : 'Tamper Detected in Audit Chain'}
                </span>
                <span className="text-zinc-500">|</span>
                <span className="text-zinc-400">Entries: {auditResult.totalEntries}</span>
                <span className="text-zinc-500">|</span>
                <span className="text-zinc-400">Last Sequence: #{auditResult.lastSequenceNumber}</span>
              </div>
              <span className="font-mono text-[10px] text-zinc-500 truncate max-w-xs">
                Tip: {auditResult.tipHash}
              </span>
            </div>
          )}

          {/* Estate Data Grid: Assets, Beneficiaries, Allocations */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Assets */}
            <div className="p-5 rounded-xl border border-zinc-800 bg-zinc-900/30 space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-zinc-200">Assets ({assets.length})</h3>
                <button
                  onClick={() => setShowAddAsset(true)}
                  className="px-2.5 py-1 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded text-xs flex items-center gap-1"
                >
                  <Plus className="w-3 h-3" /> Add Asset
                </button>
              </div>

              {showAddAsset && (
                <div className="p-3 rounded-lg bg-zinc-900 border border-zinc-700 space-y-2 text-xs">
                  <input
                    type="text"
                    placeholder="Asset Title"
                    value={newAsset.title}
                    onChange={(e) => setNewAsset({ ...newAsset, title: e.target.value })}
                    className="w-full bg-zinc-950 border border-zinc-800 rounded p-1.5 text-zinc-100"
                  />
                  <select
                    value={newAsset.category}
                    onChange={(e) => setNewAsset({ ...newAsset, category: e.target.value as AssetCategory })}
                    className="w-full bg-zinc-950 border border-zinc-800 rounded p-1.5 text-zinc-100"
                  >
                    <option value="REAL_ESTATE">Real Estate</option>
                    <option value="BANK_ACCOUNT">Bank Account</option>
                    <option value="INVESTMENT">Investment</option>
                    <option value="DIGITAL_ACCOUNT">Digital Account</option>
                    <option value="INTELLECTUAL_PROPERTY">Intellectual Property</option>
                    <option value="PHYSICAL_ASSET">Physical Asset</option>
                    <option value="OTHER">Other</option>
                  </select>
                  <textarea
                    placeholder="Description / Instructions"
                    value={newAsset.description}
                    onChange={(e) => setNewAsset({ ...newAsset, description: e.target.value })}
                    className="w-full bg-zinc-950 border border-zinc-800 rounded p-1.5 text-zinc-100 h-16"
                  />
                  <div className="flex gap-2">
                    <button onClick={handleAddAsset} className="px-3 py-1 bg-emerald-600 text-white rounded font-medium">Save</button>
                    <button onClick={() => setShowAddAsset(false)} className="px-3 py-1 bg-zinc-800 text-zinc-400 rounded">Cancel</button>
                  </div>
                </div>
              )}

              {assets.length === 0 ? (
                <p className="text-xs text-zinc-500 py-4 text-center">No assets recorded yet.</p>
              ) : (
                <div className="space-y-2">
                  {assets.map((asset) => (
                    <div key={asset.id} className="p-3 rounded-lg bg-zinc-900/60 border border-zinc-800/80 text-xs space-y-1">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-zinc-200">{asset.title}</span>
                        <span className="text-[10px] px-2 py-0.5 rounded bg-zinc-800 text-zinc-400">{asset.category}</span>
                      </div>
                      <p className="text-zinc-400">{asset.description}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Beneficiaries */}
            <div className="p-5 rounded-xl border border-zinc-800 bg-zinc-900/30 space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-zinc-200">Beneficiaries ({beneficiaries.length})</h3>
                <button
                  onClick={() => setShowAddBeneficiary(true)}
                  className="px-2.5 py-1 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded text-xs flex items-center gap-1"
                >
                  <Plus className="w-3 h-3" /> Add Beneficiary
                </button>
              </div>

              {showAddBeneficiary && (
                <div className="p-3 rounded-lg bg-zinc-900 border border-zinc-700 space-y-2 text-xs">
                  <input
                    type="text"
                    placeholder="Full Name"
                    value={newBeneficiary.name}
                    onChange={(e) => setNewBeneficiary({ ...newBeneficiary, name: e.target.value })}
                    className="w-full bg-zinc-950 border border-zinc-800 rounded p-1.5 text-zinc-100"
                  />
                  <input
                    type="email"
                    placeholder="Email Address"
                    value={newBeneficiary.email}
                    onChange={(e) => setNewBeneficiary({ ...newBeneficiary, email: e.target.value })}
                    className="w-full bg-zinc-950 border border-zinc-800 rounded p-1.5 text-zinc-100"
                  />
                  <input
                    type="text"
                    placeholder="Relationship (e.g. Spouse, Daughter)"
                    value={newBeneficiary.relationship}
                    onChange={(e) => setNewBeneficiary({ ...newBeneficiary, relationship: e.target.value })}
                    className="w-full bg-zinc-950 border border-zinc-800 rounded p-1.5 text-zinc-100"
                  />
                  <div className="flex gap-2">
                    <button onClick={handleAddBeneficiary} className="px-3 py-1 bg-emerald-600 text-white rounded font-medium">Save</button>
                    <button onClick={() => setShowAddBeneficiary(false)} className="px-3 py-1 bg-zinc-800 text-zinc-400 rounded">Cancel</button>
                  </div>
                </div>
              )}

              {beneficiaries.length === 0 ? (
                <p className="text-xs text-zinc-500 py-4 text-center">No beneficiaries designated yet.</p>
              ) : (
                <div className="space-y-2">
                  {beneficiaries.map((b) => (
                    <div key={b.id} className="p-3 rounded-lg bg-zinc-900/60 border border-zinc-800/80 text-xs space-y-1">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-zinc-200">{b.name}</span>
                        <span className="text-zinc-500 text-[10px]">{b.relationship}</span>
                      </div>
                      <p className="text-zinc-400 font-mono text-[11px]">{b.email}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Allocations Matrix */}
          <div className="p-5 rounded-xl border border-zinc-800 bg-zinc-900/30 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-zinc-200">Asset Allocations ({allocations.length})</h3>
              <button
                onClick={() => setShowAllocate(true)}
                disabled={assets.length === 0 || beneficiaries.length === 0}
                className="px-2.5 py-1 bg-zinc-800 hover:bg-zinc-700 disabled:opacity-40 text-zinc-200 rounded text-xs flex items-center gap-1"
              >
                <Plus className="w-3 h-3" /> Allocate Asset
              </button>
            </div>

            {showAllocate && (
              <div className="p-3 rounded-lg bg-zinc-900 border border-zinc-700 space-y-2 text-xs">
                <select
                  value={newAllocation.assetId}
                  onChange={(e) => setNewAllocation({ ...newAllocation, assetId: e.target.value })}
                  className="w-full bg-zinc-950 border border-zinc-800 rounded p-1.5 text-zinc-100"
                >
                  <option value="">Select Asset...</option>
                  {assets.map((a) => (
                    <option key={a.id} value={a.id}>{a.title} ({a.category})</option>
                  ))}
                </select>
                <select
                  value={newAllocation.beneficiaryId}
                  onChange={(e) => setNewAllocation({ ...newAllocation, beneficiaryId: e.target.value })}
                  className="w-full bg-zinc-950 border border-zinc-800 rounded p-1.5 text-zinc-100"
                >
                  <option value="">Select Beneficiary...</option>
                  {beneficiaries.map((b) => (
                    <option key={b.id} value={b.id}>{b.name} ({b.relationship})</option>
                  ))}
                </select>
                <div className="flex items-center gap-2">
                  <label className="text-zinc-400">Share %:</label>
                  <input
                    type="number"
                    min="1"
                    max="100"
                    value={newAllocation.sharePercentage}
                    onChange={(e) => setNewAllocation({ ...newAllocation, sharePercentage: parseInt(e.target.value) || 100 })}
                    className="w-20 bg-zinc-950 border border-zinc-800 rounded p-1.5 text-zinc-100"
                  />
                </div>
                <div className="flex gap-2">
                  <button onClick={handleAllocate} className="px-3 py-1 bg-emerald-600 text-white rounded font-medium">Save</button>
                  <button onClick={() => setShowAllocate(false)} className="px-3 py-1 bg-zinc-800 text-zinc-400 rounded">Cancel</button>
                </div>
              </div>
            )}

            {allocations.length === 0 ? (
              <p className="text-xs text-zinc-500 py-4 text-center">No allocations assigned yet.</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {allocations.map((alloc) => {
                  const asset = assets.find((a) => a.id === alloc.assetId);
                  const bene = beneficiaries.find((b) => b.id === alloc.beneficiaryId);
                  return (
                    <div key={alloc.id} className="p-3 rounded-lg bg-zinc-900/60 border border-zinc-800/80 text-xs space-y-1">
                      <div className="flex items-center justify-between font-semibold">
                        <span className="text-zinc-200">{asset ? asset.title : 'Asset'}</span>
                        <span className="text-emerald-400 font-mono">{alloc.sharePercentage}%</span>
                      </div>
                      <p className="text-zinc-400">Heir: <strong className="text-zinc-300">{bene ? bene.name : 'Beneficiary'}</strong></p>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
