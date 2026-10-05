import Link from 'next/link';
import { Shield, Key, FileCheck, CheckCircle2, Lock, ArrowRight } from 'lucide-react';

export default function Home() {
  return (
    <div className="space-y-12">
      {/* Hero */}
      <section className="space-y-4">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-emerald-500/30 bg-emerald-500/10 text-emerald-400 text-xs font-medium">
          <Shield className="w-3.5 h-3.5" />
          <span>Deterministic State Engine • Invariant Protected</span>
        </div>
        <h1 className="text-4xl font-extrabold tracking-tight sm:text-5xl text-zinc-100">
          Digital Will & Succession Platform
        </h1>
        <p className="text-lg text-zinc-400 max-w-2xl">
          Secure, envelope-encrypted digital estate management. Handles succession workflows deterministically,
          guaranteeing no silent state skips and failure recovery.
        </p>
      </section>

      {/* Feature Cards */}
      <section className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Link
          href="/estate"
          className="group block p-6 rounded-xl border border-zinc-800 bg-zinc-900/40 hover:border-emerald-500/50 hover:bg-zinc-900/80 transition-all"
        >
          <div className="w-10 h-10 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 mb-4 group-hover:scale-105 transition-transform">
            <CheckCircle2 className="w-5 h-5" />
          </div>
          <h2 className="text-lg font-semibold text-zinc-100 mb-2 flex items-center justify-between">
            <span>Estate & Succession</span>
            <ArrowRight className="w-4 h-4 text-zinc-500 group-hover:text-emerald-400 group-hover:translate-x-0.5 transition-all" />
          </h2>
          <p className="text-sm text-zinc-400">
            View authoritative succession states, record owner activity check-ins, manage assets, beneficiaries, and asset allocations.
          </p>
        </Link>

        <Link
          href="/documents"
          className="group block p-6 rounded-xl border border-zinc-800 bg-zinc-900/40 hover:border-blue-500/50 hover:bg-zinc-900/80 transition-all"
        >
          <div className="w-10 h-10 rounded-lg bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400 mb-4 group-hover:scale-105 transition-transform">
            <Lock className="w-5 h-5" />
          </div>
          <h2 className="text-lg font-semibold text-zinc-100 mb-2 flex items-center justify-between">
            <span>Encrypted Documents</span>
            <ArrowRight className="w-4 h-4 text-zinc-500 group-hover:text-blue-400 group-hover:translate-x-0.5 transition-all" />
          </h2>
          <p className="text-sm text-zinc-400">
            Upload and download documents with AES-256-GCM envelope encryption. Plaintext is never stored on disk or database.
          </p>
        </Link>

        <Link
          href="/verify"
          className="group block p-6 rounded-xl border border-zinc-800 bg-zinc-900/40 hover:border-purple-500/50 hover:bg-zinc-900/80 transition-all"
        >
          <div className="w-10 h-10 rounded-lg bg-purple-500/10 border border-purple-500/20 flex items-center justify-center text-purple-400 mb-4 group-hover:scale-105 transition-transform">
            <Key className="w-5 h-5" />
          </div>
          <h2 className="text-lg font-semibold text-zinc-100 mb-2 flex items-center justify-between">
            <span>Contact Verification</span>
            <ArrowRight className="w-4 h-4 text-zinc-500 group-hover:text-purple-400 group-hover:translate-x-0.5 transition-all" />
          </h2>
          <p className="text-sm text-zinc-400">
            Trusted contacts submit single-use cryptographic tokens to reach the 2-of-3 quorum required for succession release.
          </p>
        </Link>
      </section>

      {/* Security Architecture Guarantees */}
      <section className="p-6 rounded-xl border border-zinc-800 bg-zinc-900/20 space-y-4">
        <h3 className="text-base font-semibold text-zinc-200 flex items-center gap-2">
          <FileCheck className="w-4 h-4 text-emerald-400" />
          Authoritative Security Architecture
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 text-xs text-zinc-400">
          <div className="p-3 rounded-lg bg-zinc-900/60 border border-zinc-800/80 space-y-1">
            <span className="font-semibold text-zinc-200">State Transitions</span>
            <p>Guarded by State Engine. Linear progression from ACTIVE to terminal EXECUTED.</p>
          </div>
          <div className="p-3 rounded-lg bg-zinc-900/60 border border-zinc-800/80 space-y-1">
            <span className="font-semibold text-zinc-200">Envelope Encryption</span>
            <p>AES-256-GCM with RFC 3394 wrapped DEKs. SHA-256 integrity verification.</p>
          </div>
          <div className="p-3 rounded-lg bg-zinc-900/60 border border-zinc-800/80 space-y-1">
            <span className="font-semibold text-zinc-200">Audit Trail</span>
            <p>Tamper-evident linear hash chain with genesis initialization and sequence validation.</p>
          </div>
          <div className="p-3 rounded-lg bg-zinc-900/60 border border-zinc-800/80 space-y-1">
            <span className="font-semibold text-zinc-200">Controlled Disclosure</span>
            <p>Separate scoped packages per beneficiary. Zero cross-asset privacy leakage.</p>
          </div>
        </div>
      </section>
    </div>
  );
}
