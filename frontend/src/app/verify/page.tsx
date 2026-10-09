'use client';

import React, { Suspense, useEffect, useState } from 'react';
import { usePathname, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { api } from '@/lib/api';
import { UIState, VerificationConfirmationResult } from '@/lib/types';
import { mapErrorToUIState } from '@/lib/utils';
import { AlertTriangle, ArrowRight, CheckCircle2, Clock3, KeyRound, LockKeyhole, RotateCcw, ShieldCheck, XCircle } from 'lucide-react';

function VerifyContent() {
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const [token, setToken] = useState('');
  const [uiState, setUiState] = useState<UIState>('idle');
  const [result, setResult] = useState<VerificationConfirmationResult | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const queryToken = searchParams.get('token');
    if (queryToken) {
      setToken(queryToken);
      // Avoid leaving a single-use token sitting in the address bar/history after reading it.
      window.history.replaceState(null, '', pathname);
    }
  }, [searchParams, pathname]);

  const handleConfirm = async (event?: React.FormEvent<HTMLFormElement>) => {
    event?.preventDefault();
    if (uiState === 'loading') return;
    const submittedToken = token.trim();
    if (!submittedToken) {
      setUiState('validation_error');
      setErrorMessage('Paste the verification token from the trusted-contact message.');
      return;
    }

    setUiState('loading');
    setErrorMessage(null);
    setResult(null);
    try {
      const response = await api.confirmVerification(submittedToken);
      setResult(response);
      setUiState('success');
      setToken('');
    } catch (error: unknown) {
      const { state, message } = mapErrorToUIState(error);
      setUiState(state);
      setErrorMessage(message);
    }
  };

  const retry = () => {
    setErrorMessage(null);
    setUiState('idle');
  };

  return (
    <div className="mx-auto grid max-w-5xl gap-6 lg:grid-cols-[.85fr_1.15fr] lg:gap-8">
      <aside className="relative overflow-hidden rounded-[26px] bg-[#123b2c] p-7 text-white sm:p-9">
        <div className="absolute -right-20 -top-16 h-64 w-64 rounded-full border border-white/10" />
        <div className="relative">
          <span className="grid h-12 w-12 place-items-center rounded-2xl border border-white/15 bg-white/10"><ShieldCheck className="h-6 w-6" /></span>
          <p className="mt-7 text-xs font-bold uppercase tracking-[.18em] text-emerald-200">Trusted contact</p>
          <h1 className="mt-3 text-3xl font-extrabold leading-tight tracking-[-.04em] sm:text-4xl">Confirm carefully. The system records the result.</h1>
          <p className="mt-4 text-sm leading-7 text-emerald-50/75">This page submits a verification token to the existing service. A successful submission does not by itself guarantee that the required quorum has been reached.</p>
        </div>
        <div className="relative mt-9 rounded-2xl border border-white/10 bg-white/[.07] p-5">
          <p className="text-xs font-bold uppercase tracking-wider text-emerald-100">How it works</p>
          <div className="mt-4 space-y-4">
            {[
              ['Enter the token', 'Use the exact token you received.'],
              ['Submit once', 'The backend validates and records confirmation.'],
              ['Read the result', 'The response shows the authoritative state and quorum result.'],
            ].map(([title, detail], index) => <div key={title} className="flex gap-3"><span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-white/10 text-xs font-extrabold">{index + 1}</span><div><p className="text-sm font-bold">{title}</p><p className="mt-1 text-xs leading-5 text-emerald-50/65">{detail}</p></div></div>)}
          </div>
        </div>
        <p className="relative mt-6 flex items-start gap-2 text-xs leading-5 text-emerald-50/60"><LockKeyhole className="mt-0.5 h-4 w-4 shrink-0" /> Keep this token private. Do not forward it or post it publicly.</p>
      </aside>

      <section className="surface p-5 sm:p-8 lg:p-9">
        <span className="eyebrow"><KeyRound className="h-3.5 w-3.5" /> Token confirmation</span>
        <h2 className="mt-5 text-2xl font-extrabold tracking-tight text-[#18392d]">Verify a confirmation token</h2>
        <p className="mt-2 max-w-lg text-sm leading-6 text-slate-500">Paste the token you were given. The server will decide whether it is valid, previously used or expired.</p>

        {uiState === 'success' && result && (
          <div className="status-message status-success mt-6" role="status">
            <div className="flex items-start gap-3"><CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" /><div className="flex-1"><p className="font-extrabold">{result.alreadyConfirmed ? 'Confirmation was already recorded' : 'Confirmation submitted'}</p><p className="mt-1 text-sm">Current state reported by the server: <strong>{result.state}</strong></p><p className="mt-1 text-sm">Quorum reached: <strong>{result.quorumReached ? 'Yes' : 'Not yet'}</strong></p>{!result.quorumReached && <p className="mt-2 text-xs">Additional distinct trusted-contact confirmations may still be required.</p>}</div></div>
            <button type="button" onClick={retry} className="secondary-button mt-4 !min-h-9 !bg-white !text-xs">Submit another token</button>
          </div>
        )}

        {errorMessage && uiState !== 'success' && (
          <div className={`status-message mt-6 ${uiState === 'expired' ? 'status-info' : 'status-error'}`} role="alert">
            <div className="flex items-start gap-2.5">{uiState === 'expired' ? <Clock3 className="mt-0.5 h-4 w-4 shrink-0" /> : uiState === 'conflict' ? <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> : <XCircle className="mt-0.5 h-4 w-4 shrink-0" />}<div className="flex-1"><p className="font-bold">{uiState === 'expired' ? 'Token expired' : uiState === 'conflict' ? 'Token cannot be used' : uiState === 'network_error' ? 'Connection problem' : uiState === 'server_error' ? 'Service problem' : 'Unable to verify token'}</p><p className="mt-1">{errorMessage}</p></div></div>
            {(uiState === 'network_error' || uiState === 'server_error' || uiState === 'conflict' || uiState === 'expired') && <button type="button" onClick={retry} className="secondary-button mt-3 !min-h-9 !text-xs">Back to token form</button>}
          </div>
        )}

        {uiState !== 'success' && (
          <form onSubmit={(event) => handleConfirm(event)} className="mt-6 space-y-4">
            <div><label htmlFor="verification-token" className="field-label">Verification token</label><textarea id="verification-token" name="token" rows={4} autoComplete="off" autoCapitalize="none" spellCheck={false} value={token} onChange={(event) => { setToken(event.target.value); if (errorMessage) { setErrorMessage(null); setUiState('idle'); } }} placeholder="Paste the full token here" className="field-control min-h-28 resize-y font-mono text-sm" disabled={uiState === 'loading'} aria-describedby="token-help" required /><p id="token-help" className="mt-2 text-xs leading-5 text-slate-500">The token is sent directly to the configured backend endpoint. It is not saved in browser storage by this page.</p></div>
            <button type="submit" disabled={uiState === 'loading'} className="primary-button w-full !min-h-12 disabled:opacity-60">{uiState === 'loading' ? <><RotateCcw className="h-4 w-4 animate-spin" /> Verifying token…</> : <>Confirm verification <ArrowRight className="h-4 w-4" /></>}</button>
          </form>
        )}

        <div className="mt-7 rounded-2xl border border-slate-100 bg-slate-50/80 p-4"><div className="flex items-start gap-3"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-white text-emerald-800"><ShieldCheck className="h-4 w-4" /></span><div><p className="text-xs font-extrabold text-slate-700">A confirmation is not a release command</p><p className="mt-1 text-xs leading-5 text-slate-500">This form only calls the public confirmation API. Release progression remains controlled by the backend state machine and its configured jobs.</p></div></div></div>
        <p className="mt-5 text-center text-xs text-slate-500">Managing your own estate? <Link href="/estate" className="font-bold text-emerald-800 hover:underline">Open estate workspace <ArrowRight className="inline h-3 w-3" /></Link></p>
      </section>
    </div>
  );
}

export default function VerifyPage() {
  return <Suspense fallback={<div className="py-20 text-center text-sm text-slate-500">Preparing secure verification form…</div>}><VerifyContent /></Suspense>;
}
