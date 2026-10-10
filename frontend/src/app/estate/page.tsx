'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { api, getAuthToken } from '@/lib/api';
import {
  Asset,
  AssetAllocation,
  AssetCategory,
  AuditLogDto,
  AuditVerificationResult,
  Beneficiary,
  SuccessionState,
  UIState,
  WillContactDetailed,
  WillResponse,
  WillReview,
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
  Trash2,
  Lock,
  Users,
  Briefcase,
  History,
  FileText,
  AlertTriangle,
  ArrowRight,
  ShieldAlert,
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

const ASSET_CATEGORIES: { label: string; value: AssetCategory }[] = [
  { label: 'Real Estate / Property', value: 'REAL_ESTATE' },
  { label: 'Bank Account', value: 'BANK_ACCOUNT' },
  { label: 'Investment Portfolio', value: 'INVESTMENT' },
  { label: 'Digital Account / Credentials', value: 'DIGITAL_ACCOUNT' },
  { label: 'Intellectual Property', value: 'INTELLECTUAL_PROPERTY' },
  { label: 'Physical Asset', value: 'PHYSICAL_ASSET' },
  { label: 'Other Asset', value: 'OTHER' },
];

export default function EstatePage() {
  const [token, setToken] = useState<string | null>(null);
  const [will, setWill] = useState<WillResponse | null>(null);
  const [review, setReview] = useState<WillReview | null>(null);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [beneficiaries, setBeneficiaries] = useState<Beneficiary[]>([]);
  const [allocations, setAllocations] = useState<AssetAllocation[]>([]);
  const [contacts, setContacts] = useState<WillContactDetailed[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogDto[]>([]);
  const [auditVerify, setAuditVerify] = useState<AuditVerificationResult | null>(null);

  const [activeTab, setActiveTab] = useState<'review' | 'assets' | 'beneficiaries' | 'allocations' | 'contacts' | 'audit'>('review');
  const [uiState, setUiState] = useState<UIState>('idle');
  const [actionLoading, setActionLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Modal / Form States
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

  const [showAddAllocation, setShowAddAllocation] = useState(false);
  const [newAllocation, setNewAllocation] = useState({ assetId: '', beneficiaryId: '', sharePercentage: 100, instructions: '' });

  const [showAddContact, setShowAddContact] = useState(false);
  const [newContact, setNewContact] = useState({ name: '', email: '' });

  const loadWillData = useCallback(async (targetWillId: string) => {
    try {
      const [willData, reviewData, assetList, beneList, allocList, contactList] = await Promise.all([
        api.getWill(targetWillId),
        api.getWillReview(targetWillId),
        api.listAssets(targetWillId),
        api.listBeneficiaries(targetWillId),
        api.listAllocations(targetWillId),
        api.listContacts(targetWillId),
      ]);
      setWill(willData);
      setReview(reviewData);
      setAssets(assetList);
      setBeneficiaries(beneList);
      setAllocations(allocList);
      setContacts(contactList);
      setUiState('success');
    } catch (err) {
      const { state, message } = mapErrorToUIState(err);
      setUiState(state);
      setErrorMessage(message);
    }
  }, []);

  const loadInitial = useCallback(async () => {
    const storedToken = getAuthToken();
    setToken(storedToken);

    if (!storedToken) {
      setUiState('unauthorized');
      return;
    }

    setUiState('loading');
    setErrorMessage(null);

    try {
      const myWill = await api.getMyWill();
      if (!myWill) {
        setWill(null);
        setUiState('not_found');
        return;
      }
      await loadWillData(myWill.id);
    } catch (err) {
      const { state, message } = mapErrorToUIState(err);
      setUiState(state);
      setErrorMessage(message);
    }
  }, [loadWillData]);

  useEffect(() => {
    loadInitial();
  }, [loadInitial]);

  const handleCreateWill = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newWillTitle.trim()) return;
    setActionLoading(true);
    setErrorMessage(null);
    try {
      const created = await api.createWill(newWillTitle.trim());
      setShowCreateWill(false);
      setNewWillTitle('');
      await loadWillData(created.id);
      setSuccessMessage('Digital Will created successfully!');
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleCheckIn = async () => {
    if (!will || !['ACTIVE', 'INACTIVITY_WARNING', 'FINAL_WARNING', 'VERIFICATION_PENDING', 'RELEASE_PENDING'].includes(will.state)) return;
    setActionLoading(true);
    setErrorMessage(null);
    try {
      const updated = await api.checkIn(will.id);
      setWill(updated);
      setSuccessMessage('Owner activity recorded. Inactivity clock reset to now.');
      await loadWillData(will.id);
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancelSuccession = async () => {
    if (!will || !['INACTIVITY_WARNING', 'FINAL_WARNING', 'VERIFICATION_PENDING', 'RELEASE_PENDING'].includes(will.state)) return;
    if (!confirm('Cancel the current succession workflow and return the estate to ACTIVE?')) return;
    setActionLoading(true);
    try {
      const updated = await api.cancelWill(will.id, 'Owner explicit cancellation');
      setWill(updated);
      setSuccessMessage('Succession workflow reset to ACTIVE.');
      await loadWillData(will.id);
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleAddAsset = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!will || !newAsset.title.trim()) return;
    setActionLoading(true);
    setErrorMessage(null);
    try {
      await api.addAsset(will.id, newAsset);
      setShowAddAsset(false);
      setNewAsset({ title: '', category: 'REAL_ESTATE', description: '', instructions: '' });
      await loadWillData(will.id);
      setSuccessMessage('Asset added to estate.');
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteAsset = async (assetId: string) => {
    if (!will || !confirm('Delete this asset and any associated allocations?')) return;
    setActionLoading(true);
    try {
      await api.deleteAsset(will.id, assetId);
      await loadWillData(will.id);
      setSuccessMessage('Asset deleted.');
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleAddBeneficiary = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!will || !newBeneficiary.name.trim() || !newBeneficiary.email.trim()) return;
    setActionLoading(true);
    setErrorMessage(null);
    try {
      await api.addBeneficiary(will.id, newBeneficiary);
      setShowAddBeneficiary(false);
      setNewBeneficiary({ name: '', email: '', relationship: '' });
      await loadWillData(will.id);
      setSuccessMessage('Beneficiary added to estate.');
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteBeneficiary = async (beneficiaryId: string) => {
    if (!will || !confirm('Delete this beneficiary?')) return;
    setActionLoading(true);
    try {
      await api.deleteBeneficiary(will.id, beneficiaryId);
      await loadWillData(will.id);
      setSuccessMessage('Beneficiary deleted.');
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleAddAllocation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!will || !newAllocation.assetId || !newAllocation.beneficiaryId) return;
    setActionLoading(true);
    setErrorMessage(null);
    try {
      await api.allocateAsset(
        will.id,
        newAllocation.assetId,
        newAllocation.beneficiaryId,
        Number(newAllocation.sharePercentage),
        newAllocation.instructions || undefined
      );
      setShowAddAllocation(false);
      setNewAllocation({ assetId: '', beneficiaryId: '', sharePercentage: 100, instructions: '' });
      await loadWillData(will.id);
      setSuccessMessage('Asset allocation saved.');
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteAllocation = async (allocationId: string) => {
    if (!will || !confirm('Remove this allocation?')) return;
    setActionLoading(true);
    try {
      await api.deleteAllocation(will.id, allocationId);
      await loadWillData(will.id);
      setSuccessMessage('Allocation removed.');
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleAddContact = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!will || !newContact.name.trim() || !newContact.email.trim()) return;
    setActionLoading(true);
    setErrorMessage(null);
    try {
      await api.addContact(will.id, newContact.name.trim(), newContact.email.trim());
      setShowAddContact(false);
      setNewContact({ name: '', email: '' });
      await loadWillData(will.id);
      setSuccessMessage('Trusted contact associated with Will.');
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeactivateContact = async (contactId: string) => {
    if (!will || !confirm('Deactivate this trusted contact?')) return;
    setActionLoading(true);
    try {
      await api.deactivateContact(will.id, contactId);
      await loadWillData(will.id);
      setSuccessMessage('Contact deactivated.');
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  const loadAuditLogs = async () => {
    if (!will) return;
    try {
      const logs = await api.getWillAuditLogs(will.id);
      setAuditLogs(logs);
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    }
  };

  const handleVerifyChain = async () => {
    if (!will) return;
    setActionLoading(true);
    try {
      const res = await api.verifyAuditChain(will.id);
      setAuditVerify(res);
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setActionLoading(false);
    }
  };

  // Loading state
  if (uiState === 'loading') {
    return (
      <div className="max-w-md mx-auto py-24 text-center space-y-4">
        <div className="w-12 h-12 rounded-2xl bg-zinc-900 border border-zinc-800 flex items-center justify-center mx-auto text-emerald-400">
          <RotateCcw className="w-6 h-6 animate-spin" />
        </div>
        <p className="text-sm text-zinc-400">Loading your Digital Will estate plan...</p>
      </div>
    );
  }

  // If not logged in
  if (uiState === 'unauthorized') {
    return (
      <div className="max-w-md mx-auto py-16 text-center space-y-6">
        <div className="w-14 h-14 rounded-2xl bg-zinc-900 border border-zinc-800 flex items-center justify-center mx-auto text-emerald-400">
          <Lock className="w-7 h-7" />
        </div>
        <div className="space-y-2">
          <h2 className="text-xl font-bold text-zinc-100">Authentication Required</h2>
          <p className="text-sm text-zinc-400">
            Digital Will requires authentication to protect your estate and enforce server-side ownership.
          </p>
        </div>
        <Link
          href="/auth"
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors"
        >
          <span>Sign In or Register</span>
          <ArrowRight className="w-4 h-4" />
        </Link>
      </div>
    );
  }

  // Forbidden state (403)
  if (uiState === 'forbidden') {
    return (
      <div className="max-w-md mx-auto py-16 text-center space-y-6">
        <div className="w-14 h-14 rounded-2xl bg-zinc-900 border border-zinc-800 flex items-center justify-center mx-auto text-red-400">
          <Lock className="w-7 h-7" />
        </div>
        <div className="space-y-2">
          <h2 className="text-xl font-bold text-zinc-100">Access Denied</h2>
          <p className="text-sm text-zinc-400">{errorMessage || 'You do not own this estate.'}</p>
        </div>
      </div>
    );
  }

  // Server error (5xx) or Network error
  if (uiState === 'server_error' || uiState === 'network_error') {
    return (
      <div className="max-w-md mx-auto py-16 text-center space-y-6">
        <div className="w-14 h-14 rounded-2xl bg-zinc-900 border border-zinc-800 flex items-center justify-center mx-auto text-red-400">
          <AlertCircle className="w-7 h-7" />
        </div>
        <div className="space-y-2">
          <h2 className="text-xl font-bold text-zinc-100">
            {uiState === 'network_error' ? 'Network Connection Error' : 'Service Unavailable'}
          </h2>
          <p className="text-sm text-zinc-400">{errorMessage || 'Failed to communicate with the estate server.'}</p>
        </div>
        <button
          onClick={() => loadInitial()}
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-white text-sm font-medium transition-colors"
        >
          <RotateCcw className="w-4 h-4" />
          <span>Retry Connection</span>
        </button>
      </div>
    );
  }

  // If user has no will yet
  if (uiState === 'not_found' || (!will && uiState === 'success')) {
    return (
      <div className="max-w-lg mx-auto py-16 space-y-6">
        <div className="text-center space-y-2">
          <div className="w-14 h-14 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center mx-auto text-emerald-400">
            <Shield className="w-7 h-7" />
          </div>
          <h2 className="text-2xl font-bold text-zinc-100">Create Your Digital Will</h2>
          <p className="text-sm text-zinc-400">
            You do not currently have an active Digital Will. Create one to begin cataloging assets and configuring succession.
          </p>
        </div>

        {errorMessage && (
          <div className="p-3 rounded-lg bg-red-950/40 border border-red-500/30 text-red-300 text-xs flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}

        <form onSubmit={handleCreateWill} className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-6 space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-zinc-300">Will Title</label>
            <input
              type="text"
              value={newWillTitle}
              onChange={(e) => setNewWillTitle(e.target.value)}
              placeholder="e.g., Primary Personal Estate Will"
              className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-sm text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500 transition-colors"
              required
            />
          </div>
          <button
            type="submit"
            disabled={actionLoading}
            className="w-full py-2.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors disabled:opacity-50"
          >
            {actionLoading ? 'Initializing...' : 'Initialize Digital Will'}
          </button>
        </form>
      </div>
    );
  }

  return (
    <div className="estate-page space-y-8">
      {/* Messages */}
      {errorMessage && (
        <div className="p-3 rounded-lg bg-red-950/40 border border-red-500/30 text-red-300 text-xs flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0 text-red-400" />
            <span>{errorMessage}</span>
          </div>
          <button onClick={() => setErrorMessage(null)} className="text-zinc-400 hover:text-zinc-200">✕</button>
        </div>
      )}
      {successMessage && (
        <div className="p-3 rounded-lg bg-emerald-950/40 border border-emerald-500/30 text-emerald-300 text-xs flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-400" />
            <span>{successMessage}</span>
          </div>
          <button onClick={() => setSuccessMessage(null)} className="text-zinc-400 hover:text-zinc-200">✕</button>
        </div>
      )}

      {/* Header & Authoritative State */}
      {will && (
        <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-6 space-y-6">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-3">
                <h1 className="text-2xl font-bold text-zinc-100">{will.title}</h1>
                <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${getStateBadgeColor(will.state)}`}>
                  {will.state}
                </span>
              </div>
              <p className="text-xs text-zinc-500 font-mono">Will ID: {will.id}</p>
            </div>

            <div className="flex items-center gap-2">
              {['ACTIVE', 'INACTIVITY_WARNING', 'FINAL_WARNING', 'VERIFICATION_PENDING', 'RELEASE_PENDING'].includes(will.state) && (
                <button
                  onClick={handleCheckIn}
                  disabled={actionLoading}
                  className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium flex items-center gap-1.5 transition-colors disabled:opacity-50"
                  title="Records owner activity and resets eligible succession progress"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Record Check-In</span>
                </button>
              )}

              {(['INACTIVITY_WARNING', 'FINAL_WARNING', 'VERIFICATION_PENDING', 'RELEASE_PENDING'].includes(will.state)) && (
                <button
                  onClick={handleCancelSuccession}
                  disabled={actionLoading}
                  className="px-3 py-2 rounded-lg bg-amber-600/20 hover:bg-amber-600/30 text-amber-300 border border-amber-500/30 text-xs font-medium flex items-center gap-1.5 transition-colors"
                >
                  <Ban className="w-3.5 h-3.5" />
                  <span>Cancel Succession</span>
                </button>
              )}
            </div>
          </div>

          {/* Activity telemetry */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 pt-4 border-t border-zinc-800 text-xs">
            <div>
              <span className="text-zinc-500">Last Verified Activity</span>
              <p className="text-zinc-200 font-medium">{formatDate(will.lastVerifiedActivityAt)}</p>
            </div>
            <div>
              <span className="text-zinc-500">Created</span>
              <p className="text-zinc-200 font-medium">{formatDate(will.createdAt)}</p>
            </div>
            <div>
              <span className="text-zinc-500">Verification</span>
              <p className="text-zinc-200 font-medium">Backend controlled</p>
            </div>
            <div>
              <span className="text-zinc-500">Quorum Requirement</span>
              <p className="text-zinc-200 font-medium">2-of-3 Distinct Contacts</p>
            </div>
          </div>

          {/* State Timeline */}
          <div className="pt-2">
            <span className="text-xs font-medium text-zinc-400 block mb-2">Authoritative Succession Pipeline</span>
            <div className="flex items-center gap-1 overflow-x-auto pb-2 text-[10px]">
              {STATE_ORDER.map((s, idx) => {
                const currentIdx = STATE_ORDER.indexOf(will.state);
                const isPassed = idx < currentIdx;
                const isCurrent = idx === currentIdx;

                return (
                  <div key={s} className="flex items-center gap-1 shrink-0">
                    <span
                      className={`px-2 py-1 rounded font-mono ${
                        isCurrent
                          ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 font-semibold'
                          : isPassed
                          ? 'bg-zinc-800 text-zinc-400'
                          : 'bg-zinc-950 text-zinc-600 border border-zinc-900'
                      }`}
                    >
                      {s}
                    </span>
                    {idx < STATE_ORDER.length - 1 && <span className="text-zinc-700">→</span>}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Tabs */}
      <div className="flex border-b border-zinc-800 gap-1 overflow-x-auto">
        <button
          onClick={() => setActiveTab('review')}
          className={`px-4 py-2 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 shrink-0 ${
            activeTab === 'review'
              ? 'border-emerald-500 text-emerald-400'
              : 'border-transparent text-zinc-400 hover:text-zinc-200'
          }`}
        >
          <FileCheck2 className="w-3.5 h-3.5" />
          <span>Review & Readiness</span>
        </button>
        <button
          onClick={() => setActiveTab('assets')}
          className={`px-4 py-2 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 shrink-0 ${
            activeTab === 'assets'
              ? 'border-emerald-500 text-emerald-400'
              : 'border-transparent text-zinc-400 hover:text-zinc-200'
          }`}
        >
          <Briefcase className="w-3.5 h-3.5" />
          <span>Assets ({assets.length})</span>
        </button>
        <button
          onClick={() => setActiveTab('beneficiaries')}
          className={`px-4 py-2 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 shrink-0 ${
            activeTab === 'beneficiaries'
              ? 'border-emerald-500 text-emerald-400'
              : 'border-transparent text-zinc-400 hover:text-zinc-200'
          }`}
        >
          <Users className="w-3.5 h-3.5" />
          <span>Beneficiaries ({beneficiaries.length})</span>
        </button>
        <button
          onClick={() => setActiveTab('allocations')}
          className={`px-4 py-2 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 shrink-0 ${
            activeTab === 'allocations'
              ? 'border-emerald-500 text-emerald-400'
              : 'border-transparent text-zinc-400 hover:text-zinc-200'
          }`}
        >
          <FileText className="w-3.5 h-3.5" />
          <span>Allocations ({allocations.length})</span>
        </button>
        <button
          onClick={() => setActiveTab('contacts')}
          className={`px-4 py-2 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 shrink-0 ${
            activeTab === 'contacts'
              ? 'border-emerald-500 text-emerald-400'
              : 'border-transparent text-zinc-400 hover:text-zinc-200'
          }`}
        >
          <UserCheck className="w-3.5 h-3.5" />
          <span>Trusted Contacts ({contacts.length})</span>
        </button>
        <button
          onClick={() => {
            setActiveTab('audit');
            loadAuditLogs();
          }}
          className={`px-4 py-2 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 shrink-0 ${
            activeTab === 'audit'
              ? 'border-emerald-500 text-emerald-400'
              : 'border-transparent text-zinc-400 hover:text-zinc-200'
          }`}
        >
          <History className="w-3.5 h-3.5" />
          <span>Audit Trail</span>
        </button>
      </div>

      {/* Tab 1: Review & Readiness */}
      {activeTab === 'review' && review && (
        <div className="space-y-6">
          <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-6 space-y-4">
            <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
              <FileCheck2 className="w-4 h-4 text-emerald-400" />
              <span>Estate Readiness Checklist</span>
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800 flex items-center justify-between">
                <span className="text-zinc-300">1. Assets Cataloged ({review.assetCount})</span>
                {review.hasAssets ? (
                  <span className="text-emerald-400 flex items-center gap-1 font-medium">
                    <CheckCircle2 className="w-4 h-4" /> Ready
                  </span>
                ) : (
                  <span className="text-amber-400 flex items-center gap-1 font-medium">
                    <AlertTriangle className="w-4 h-4" /> Required
                  </span>
                )}
              </div>

              <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800 flex items-center justify-between">
                <span className="text-zinc-300">2. Beneficiaries Configured ({review.beneficiaryCount})</span>
                {review.hasBeneficiaries ? (
                  <span className="text-emerald-400 flex items-center gap-1 font-medium">
                    <CheckCircle2 className="w-4 h-4" /> Ready
                  </span>
                ) : (
                  <span className="text-amber-400 flex items-center gap-1 font-medium">
                    <AlertTriangle className="w-4 h-4" /> Required
                  </span>
                )}
              </div>

              <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800 flex items-center justify-between">
                <span className="text-zinc-300">3. Assets 100% Allocated ({review.allocationCount})</span>
                {review.allAssetsFullyAllocated ? (
                  <span className="text-emerald-400 flex items-center gap-1 font-medium">
                    <CheckCircle2 className="w-4 h-4" /> Complete
                  </span>
                ) : (
                  <span className="text-amber-400 flex items-center gap-1 font-medium">
                    <AlertTriangle className="w-4 h-4" /> Incomplete
                  </span>
                )}
              </div>

              <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800 flex items-center justify-between">
                <span className="text-zinc-300">4. 2-of-3 Quorum Contacts ({review.activeTrustedContactCount})</span>
                {review.hasQuorumContacts ? (
                  <span className="text-emerald-400 flex items-center gap-1 font-medium">
                    <CheckCircle2 className="w-4 h-4" /> Quorum Met
                  </span>
                ) : (
                  <span className="text-amber-400 flex items-center gap-1 font-medium">
                    <AlertTriangle className="w-4 h-4" /> Need ≥ 3
                  </span>
                )}
              </div>
            </div>

            {review.warnings?.length > 0 && (
              <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4">
                <h3 className="flex items-center gap-2 text-sm font-semibold text-amber-800">
                  <AlertTriangle className="h-4 w-4" /> What needs attention
                </h3>
                <ul className="mt-2 space-y-1 pl-5 text-sm text-amber-900/80 list-disc">
                  {review.warnings.map((warning, index) => <li key={`${index}-${warning}`}>{warning}</li>)}
                </ul>
              </div>
            )}

            <div className={`p-4 rounded-lg border text-xs flex items-center justify-between ${
              review.readyForActivation
                ? 'bg-emerald-950/20 border-emerald-500/30 text-emerald-300'
                : 'bg-zinc-950 border-zinc-800 text-zinc-400'
            }`}>
              <div className="flex items-center gap-2">
                <Shield className="w-4 h-4 text-emerald-400" />
                <span className="font-medium">
                  {review.readyForActivation
                    ? 'Estate is fully configured and ready for succession.'
                    : 'Estate setup is incomplete. Complete all 4 checklist items above.'}
                </span>
              </div>
              <span className="px-2 py-0.5 rounded font-mono font-semibold text-[10px] bg-zinc-900 border border-zinc-800">
                {review.state}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Assets */}
      {activeTab === 'assets' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold text-zinc-100">Estate Assets</h2>
            <button
              onClick={() => setShowAddAsset(!showAddAsset)}
              className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium flex items-center gap-1.5 transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add Asset</span>
            </button>
          </div>

          {showAddAsset && (
            <form onSubmit={handleAddAsset} className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-medium text-zinc-300">Asset Title</label>
                  <input
                    type="text"
                    value={newAsset.title}
                    onChange={(e) => setNewAsset({ ...newAsset, title: e.target.value })}
                    placeholder="Downtown Apartment"
                    className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500"
                    required
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-zinc-300">Category</label>
                  <select
                    value={newAsset.category}
                    onChange={(e) => setNewAsset({ ...newAsset, category: e.target.value as AssetCategory })}
                    className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 focus:outline-none focus:border-emerald-500"
                  >
                    {ASSET_CATEGORIES.map((c) => (
                      <option key={c.value} value={c.value}>{c.label}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="space-y-1">
                <label className="text-xs font-medium text-zinc-300">Description</label>
                <input
                  type="text"
                  value={newAsset.description}
                  onChange={(e) => setNewAsset({ ...newAsset, description: e.target.value })}
                  placeholder="2-bedroom condo, deeds in safe"
                  className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500"
                />
              </div>
              <div className="space-y-1">
                <label className="text-xs font-medium text-zinc-300">Succession Instructions</label>
                <textarea
                  value={newAsset.instructions}
                  onChange={(e) => setNewAsset({ ...newAsset, instructions: e.target.value })}
                  placeholder="Instructions for successor/heir"
                  rows={2}
                  className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500"
                />
              </div>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowAddAsset(false)}
                  className="px-3 py-1.5 rounded-lg border border-zinc-800 text-zinc-400 hover:text-zinc-200 text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium"
                >
                  Save Asset
                </button>
              </div>
            </form>
          )}

          {assets.length === 0 ? (
            <div className="p-8 rounded-xl border border-zinc-800/80 bg-zinc-900/20 text-center text-xs text-zinc-500">
              No assets cataloged yet. Add properties, bank accounts, investments, or digital accounts.
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {assets.map((a) => (
                <div key={a.id} className="p-4 rounded-xl border border-zinc-800 bg-zinc-900/40 space-y-2">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="text-sm font-semibold text-zinc-100">{a.title}</h3>
                      <span className="text-[10px] font-mono text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded border border-emerald-500/20">
                        {a.category}
                      </span>
                    </div>
                    <button
                      onClick={() => handleDeleteAsset(a.id)}
                      className="p-1 text-zinc-500 hover:text-red-400 transition-colors"
                      title="Delete asset"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                  {a.description && <p className="text-xs text-zinc-400">{a.description}</p>}
                  {a.instructions && (
                    <p className="text-xs text-zinc-500 italic bg-zinc-950 p-2 rounded border border-zinc-900">
                      {a.instructions}
                    </p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab 3: Beneficiaries */}
      {activeTab === 'beneficiaries' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold text-zinc-100">Heirs & Beneficiaries</h2>
            <button
              onClick={() => setShowAddBeneficiary(!showAddBeneficiary)}
              className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium flex items-center gap-1.5 transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add Beneficiary</span>
            </button>
          </div>

          {showAddBeneficiary && (
            <form onSubmit={handleAddBeneficiary} className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-medium text-zinc-300">Full Name</label>
                  <input
                    type="text"
                    value={newBeneficiary.name}
                    onChange={(e) => setNewBeneficiary({ ...newBeneficiary, name: e.target.value })}
                    placeholder="Sophia Miller"
                    className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500"
                    required
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-zinc-300">Email Address</label>
                  <input
                    type="email"
                    value={newBeneficiary.email}
                    onChange={(e) => setNewBeneficiary({ ...newBeneficiary, email: e.target.value })}
                    placeholder="sophia@example.com"
                    className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500"
                    required
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-zinc-300">Relationship</label>
                  <input
                    type="text"
                    value={newBeneficiary.relationship}
                    onChange={(e) => setNewBeneficiary({ ...newBeneficiary, relationship: e.target.value })}
                    placeholder="Daughter / Spouse / Brother"
                    className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500"
                    required
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowAddBeneficiary(false)}
                  className="px-3 py-1.5 rounded-lg border border-zinc-800 text-zinc-400 hover:text-zinc-200 text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium"
                >
                  Save Beneficiary
                </button>
              </div>
            </form>
          )}

          {beneficiaries.length === 0 ? (
            <div className="p-8 rounded-xl border border-zinc-800/80 bg-zinc-900/20 text-center text-xs text-zinc-500">
              No beneficiaries added yet. Add designated heirs who will receive controlled disclosures.
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {beneficiaries.map((b) => (
                <div key={b.id} className="p-4 rounded-xl border border-zinc-800 bg-zinc-900/40 space-y-1.5">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="text-sm font-semibold text-zinc-100">{b.name}</h3>
                      <p className="text-xs text-zinc-400">{b.email}</p>
                    </div>
                    <button
                      onClick={() => handleDeleteBeneficiary(b.id)}
                      className="p-1 text-zinc-500 hover:text-red-400 transition-colors"
                      title="Delete beneficiary"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                  <span className="text-[10px] font-mono text-zinc-400 bg-zinc-800 px-1.5 py-0.5 rounded">
                    Relationship: {b.relationship}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab 4: Allocations */}
      {activeTab === 'allocations' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-zinc-100">Asset Allocations</h2>
              <p className="text-xs text-zinc-500">Assign percentage shares of each asset to designated beneficiaries.</p>
            </div>
            <button
              onClick={() => setShowAddAllocation(!showAddAllocation)}
              disabled={assets.length === 0 || beneficiaries.length === 0}
              className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium flex items-center gap-1.5 transition-colors disabled:opacity-50"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Define Allocation</span>
            </button>
          </div>

          {showAddAllocation && (
            <form onSubmit={handleAddAllocation} className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-medium text-zinc-300">Select Asset</label>
                  <select
                    value={newAllocation.assetId}
                    onChange={(e) => setNewAllocation({ ...newAllocation, assetId: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 focus:outline-none focus:border-emerald-500"
                    required
                  >
                    <option value="">-- Choose Asset --</option>
                    {assets.map((a) => (
                      <option key={a.id} value={a.id}>{a.title} ({a.category})</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-zinc-300">Select Beneficiary</label>
                  <select
                    value={newAllocation.beneficiaryId}
                    onChange={(e) => setNewAllocation({ ...newAllocation, beneficiaryId: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 focus:outline-none focus:border-emerald-500"
                    required
                  >
                    <option value="">-- Choose Beneficiary --</option>
                    {beneficiaries.map((b) => (
                      <option key={b.id} value={b.id}>{b.name} ({b.relationship})</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-zinc-300">Share Percentage (%)</label>
                  <input
                    type="number"
                    min={1}
                    max={100}
                    value={newAllocation.sharePercentage}
                    onChange={(e) => setNewAllocation({ ...newAllocation, sharePercentage: Number(e.target.value) })}
                    className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 focus:outline-none focus:border-emerald-500"
                    required
                  />
                </div>
              </div>
              <div className="space-y-1">
                <label className="text-xs font-medium text-zinc-300">Specific Allocation Instructions</label>
                <input
                  type="text"
                  value={newAllocation.instructions}
                  onChange={(e) => setNewAllocation({ ...newAllocation, instructions: e.target.value })}
                  placeholder="e.g., Transfer to daughter upon university completion"
                  className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500"
                />
              </div>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowAddAllocation(false)}
                  className="px-3 py-1.5 rounded-lg border border-zinc-800 text-zinc-400 hover:text-zinc-200 text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium"
                >
                  Save Allocation
                </button>
              </div>
            </form>
          )}

          {allocations.length === 0 ? (
            <div className="p-8 rounded-xl border border-zinc-800/80 bg-zinc-900/20 text-center text-xs text-zinc-500">
              No allocations defined yet. Link assets to beneficiaries.
            </div>
          ) : (
            <div className="space-y-3">
              {allocations.map((al) => {
                const asset = assets.find((a) => a.id === al.assetId);
                const bene = beneficiaries.find((b) => b.id === al.beneficiaryId);
                return (
                  <div key={al.id} className="p-4 rounded-xl border border-zinc-800 bg-zinc-900/40 flex items-center justify-between text-xs">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-zinc-100">{asset?.title ?? al.assetId}</span>
                        <span className="text-zinc-500">→</span>
                        <span className="font-medium text-emerald-400">{bene?.name ?? al.beneficiaryId}</span>
                        <span className="px-2 py-0.5 rounded bg-emerald-950 text-emerald-400 font-mono font-bold">
                          {al.sharePercentage}%
                        </span>
                      </div>
                      {al.instructions && <p className="text-zinc-400 italic">{al.instructions}</p>}
                    </div>
                    <button
                      onClick={() => handleDeleteAllocation(al.id)}
                      className="p-1.5 text-zinc-500 hover:text-red-400 transition-colors"
                      title="Remove allocation"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Tab 5: Trusted Contacts */}
      {activeTab === 'contacts' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-zinc-100">Trusted Contacts (2-of-3 Quorum)</h2>
              <p className="text-xs text-zinc-500">
                Trusted contacts participate in inactivity verification. They are distinct from beneficiaries.
              </p>
            </div>
            <button
              onClick={() => setShowAddContact(!showAddContact)}
              className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium flex items-center gap-1.5 transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add Contact</span>
            </button>
          </div>

          {showAddContact && (
            <form onSubmit={handleAddContact} className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-medium text-zinc-300">Contact Name</label>
                  <input
                    type="text"
                    value={newContact.name}
                    onChange={(e) => setNewContact({ ...newContact, name: e.target.value })}
                    placeholder="Alice Verifier"
                    className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500"
                    required
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-zinc-300">Contact Email</label>
                  <input
                    type="email"
                    value={newContact.email}
                    onChange={(e) => setNewContact({ ...newContact, email: e.target.value })}
                    placeholder="alice.verifier@example.com"
                    className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500"
                    required
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowAddContact(false)}
                  className="px-3 py-1.5 rounded-lg border border-zinc-800 text-zinc-400 hover:text-zinc-200 text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium"
                >
                  Add Trusted Contact
                </button>
              </div>
            </form>
          )}

          {contacts.length === 0 ? (
            <div className="p-8 rounded-xl border border-zinc-800/80 bg-zinc-900/20 text-center text-xs text-zinc-500">
              No trusted contacts added yet. Configure at least 3 contacts to enable quorum verification.
            </div>
          ) : (
            <div className="space-y-3">
              {contacts.map((c) => (
                <div key={c.associationId} className="p-4 rounded-xl border border-zinc-800 bg-zinc-900/40 flex items-center justify-between text-xs">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-zinc-100">{c.name}</span>
                      <span className="text-zinc-400">({c.email})</span>
                      <span className={`px-2 py-0.5 rounded text-[10px] font-mono ${
                        c.isActive ? 'bg-emerald-950 text-emerald-400 border border-emerald-500/30' : 'bg-zinc-800 text-zinc-500'
                      }`}>
                        {c.isActive ? 'Active' : 'Inactive'}
                      </span>
                      {c.confirmedCurrentCycle && (
                        <span className="px-2 py-0.5 rounded text-[10px] bg-blue-950 text-blue-400 border border-blue-500/30">
                          Confirmed this cycle
                        </span>
                      )}
                    </div>
                    <span className="text-zinc-500 text-[10px]">Added: {formatDate(c.addedAt)}{c.confirmedCurrentCycle ? ' · Confirmed in current cycle' : ''}</span>
                  </div>

                  {c.isActive && (
                    <button
                      onClick={() => handleDeactivateContact(c.contactId)}
                      className="px-2.5 py-1 rounded bg-zinc-800 hover:bg-red-950 text-zinc-400 hover:text-red-400 text-xs transition-colors"
                    >
                      Deactivate
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab 6: Audit Trail */}
      {activeTab === 'audit' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-zinc-100">Tamper-Evident Hash-Chained Audit Trail</h2>
              <p className="text-xs text-zinc-500">
                Cryptographic linear SHA-256 chain recording all state transitions, security events, and accesses.
              </p>
            </div>
            <button
              onClick={handleVerifyChain}
              disabled={actionLoading}
              className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium flex items-center gap-1.5 transition-colors disabled:opacity-50"
            >
              <Shield className="w-3.5 h-3.5" />
              <span>Verify Cryptographic Chain</span>
            </button>
          </div>

          {auditVerify && (
            <div className={`p-4 rounded-xl border text-xs flex items-center gap-3 ${
              auditVerify.valid
                ? 'bg-emerald-950/30 border-emerald-500/40 text-emerald-300'
                : 'bg-red-950/30 border-red-500/40 text-red-300'
            }`}>
              <CheckCircle2 className="w-5 h-5 shrink-0" />
              <div className="space-y-0.5">
                <p className="font-semibold">
                  {auditVerify.valid ? 'Cryptographic Hash-Chain Verification Passed' : 'Verification Failed'}
                </p>
                <p className="text-zinc-400 text-[11px]">
                  Entries checked: {auditVerify.checkedEntries}{!auditVerify.valid && auditVerify.failureReason ? ` · ${auditVerify.failureReason}` : ''}{auditVerify.failedSequenceNumber !== null ? ` · Failed sequence #${auditVerify.failedSequenceNumber}` : ''}
                </p>
              </div>
            </div>
          )}

          {auditLogs.length === 0 ? (
            <div className="p-8 rounded-xl border border-zinc-800/80 bg-zinc-900/20 text-center text-xs text-zinc-500">
              No audit records loaded.
            </div>
          ) : (
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 overflow-hidden">
              <table className="w-full text-left text-xs text-zinc-300">
                <thead className="bg-zinc-950 text-zinc-500 border-b border-zinc-800 font-mono text-[11px]">
                  <tr>
                    <th className="p-3">Seq #</th>
                    <th className="p-3">Action</th>
                    <th className="p-3">Resource</th>
                    <th className="p-3">Status</th>
                    <th className="p-3">Timestamp</th>
                    <th className="p-3">Entry Hash</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-800/60 font-mono text-[11px]">
                  {auditLogs.map((log) => (
                    <tr key={log.sequenceNumber} className="hover:bg-zinc-900/50">
                      <td className="p-3 text-zinc-400">#{log.sequenceNumber}</td>
                      <td className="p-3 font-semibold text-zinc-200">{log.action}</td>
                      <td className="p-3 text-zinc-400">{log.resourceType}</td>
                      <td className="p-3">
                        <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                          log.status === 'SUCCESS' ? 'bg-emerald-950 text-emerald-400' : 'bg-red-950 text-red-400'
                        }`}>
                          {log.status}
                        </span>
                      </td>
                      <td className="p-3 text-zinc-400">{formatDate(log.createdAt)}</td>
                      <td className="p-3 text-zinc-500 truncate max-w-[120px]" title={log.entryHash}>
                        {log.entryHash.substring(0, 12)}...
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
