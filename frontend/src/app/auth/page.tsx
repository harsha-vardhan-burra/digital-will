'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import { mapErrorToUIState } from '@/lib/utils';
import { ArrowRight, CheckCircle2, Eye, EyeOff, Fingerprint, LockKeyhole, Mail, ShieldCheck, UserRound, UserRoundCheck } from 'lucide-react';

export default function AuthPage() {
  const router = useRouter();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const switchMode = (nextMode: 'login' | 'register') => {
    setMode(nextMode);
    setErrorMessage(null);
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (loading) return;
    setErrorMessage(null);
    const cleanEmail = email.trim();
    const cleanName = fullName.trim();
    if (!cleanEmail || !password || (mode === 'register' && !cleanName)) {
      setErrorMessage('Complete each required field to continue.');
      return;
    }
    if (password.length < 8) {
      setErrorMessage('Use a password with at least 8 characters.');
      return;
    }

    setLoading(true);
    try {
      if (mode === 'register') await api.register(cleanEmail, password, cleanName);
      else await api.login(cleanEmail, password);
      router.replace('/estate');
      router.refresh();
    } catch (error: unknown) {
      const { message } = mapErrorToUIState(error);
      setErrorMessage(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto grid max-w-5xl overflow-hidden rounded-[28px] border border-[#dfe9e2] bg-white shadow-[0_22px_80px_rgba(19,54,40,.08)] lg:grid-cols-[.9fr_1.1fr]">
      <aside className="relative hidden overflow-hidden bg-[#123b2c] p-9 text-white lg:flex lg:flex-col lg:justify-between xl:p-12">
        <div className="absolute -right-28 -top-24 h-80 w-80 rounded-full border border-white/10" />
        <div className="absolute -right-12 -top-8 h-48 w-48 rounded-full border border-white/10" />
        <div className="relative">
          <span className="grid h-12 w-12 place-items-center rounded-2xl border border-white/15 bg-white/10"><ShieldCheck className="h-6 w-6" /></span>
          <p className="mt-9 text-xs font-bold uppercase tracking-[.2em] text-emerald-200">Digital Will</p>
          <h1 className="mt-4 text-4xl font-extrabold leading-[1.08] tracking-[-.04em]">Make your wishes easier to organise.</h1>
          <p className="mt-5 max-w-sm text-sm leading-7 text-emerald-50/75">Build an estate record, organise who receives what, and keep important documents protected.</p>
        </div>
        <div className="relative mt-12 space-y-4">
          {[
            ['Your details stay yours', 'Every estate request is checked by the backend.'],
            ['Steps stay understandable', 'See what is configured and what still needs attention.'],
            ['Important actions are recorded', 'Review the estate audit history when you need it.'],
          ].map(([title, detail]) => (
            <div key={title} className="flex gap-3">
              <span className="mt-0.5 grid h-6 w-6 shrink-0 place-items-center rounded-full bg-emerald-300/15 text-emerald-200"><CheckCircle2 className="h-4 w-4" /></span>
              <div><p className="text-sm font-bold text-white">{title}</p><p className="mt-1 text-xs leading-5 text-emerald-50/65">{detail}</p></div>
            </div>
          ))}
        </div>
        <p className="relative mt-12 text-[11px] text-emerald-50/50">A secure workspace for planning ahead.</p>
      </aside>

      <section className="p-5 sm:p-8 lg:p-10 xl:p-12">
        <div className="mx-auto max-w-md">
          <div className="mb-8">
            <span className="eyebrow"><LockKeyhole className="h-3.5 w-3.5" /> Private account access</span>
            <h2 className="mt-5 text-3xl font-extrabold tracking-tight text-[#18392d]">{mode === 'login' ? 'Welcome back' : 'Create your account'}</h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">{mode === 'login' ? 'Sign in to manage your estate workspace.' : 'Start with your account details. You can set up your estate next.'}</p>
          </div>

          <div className="mb-6 grid grid-cols-2 rounded-xl bg-slate-100 p-1" role="tablist" aria-label="Account action">
            <button type="button" role="tab" aria-selected={mode === 'login'} onClick={() => switchMode('login')} className={`rounded-[9px] px-4 py-2.5 text-sm font-bold transition ${mode === 'login' ? 'bg-white text-emerald-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}>Sign in</button>
            <button type="button" role="tab" aria-selected={mode === 'register'} onClick={() => switchMode('register')} className={`rounded-[9px] px-4 py-2.5 text-sm font-bold transition ${mode === 'register' ? 'bg-white text-emerald-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}>Create account</button>
          </div>

          {errorMessage && <div className="status-message status-error mb-5" role="alert">{errorMessage}</div>}

          <form onSubmit={handleSubmit} className="space-y-5">
            {mode === 'register' && (
              <div>
                <label htmlFor="fullName" className="field-label">Full name</label>
                <div className="relative"><UserRound className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" /><input id="fullName" name="name" type="text" autoComplete="name" value={fullName} onChange={(event) => setFullName(event.target.value)} placeholder="Your full name" className="field-control !pl-10" maxLength={160} required /></div>
              </div>
            )}
            <div>
              <label htmlFor="email" className="field-label">Email address</label>
              <div className="relative"><Mail className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" /><input id="email" name="email" type="email" autoComplete="email" inputMode="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" className="field-control !pl-10" maxLength={254} required /></div>
            </div>
            <div>
              <div className="mb-1.5 flex items-center justify-between gap-3"><label htmlFor="password" className="field-label !mb-0">Password</label>{mode === 'register' && <span className="text-[11px] text-slate-400">8 characters minimum</span>}</div>
              <div className="relative"><LockKeyhole className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" /><input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder={mode === 'login' ? 'Enter your password' : 'Create a strong password'} className="field-control !pr-12 !pl-10" minLength={8} required /><button type="button" onClick={() => setShowPassword((visible) => !visible)} className="absolute right-2 top-1/2 grid h-9 w-9 -translate-y-1/2 place-items-center rounded-lg text-slate-400 hover:bg-slate-50 hover:text-slate-700" aria-label={showPassword ? 'Hide password' : 'Show password'}>{showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</button></div>
            </div>
            <button type="submit" disabled={loading} className="primary-button mt-2 w-full !min-h-12 !rounded-xl disabled:opacity-60">{loading ? (mode === 'login' ? 'Signing in…' : 'Creating account…') : (mode === 'login' ? 'Sign in securely' : 'Create account')} {!loading && <ArrowRight className="h-4 w-4" />}</button>
          </form>

          <div className="mt-7 rounded-2xl border border-slate-100 bg-[#f8fbf9] p-4">
            <div className="flex gap-3"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-white text-emerald-800 shadow-sm"><Fingerprint className="h-4 w-4" /></span><div><p className="text-xs font-extrabold text-slate-700">Your account is checked server-side</p><p className="mt-1 text-xs leading-5 text-slate-500">The existing backend handles authentication and authorizes access to each estate resource.</p></div></div>
          </div>
          <p className="mt-6 text-center text-xs text-slate-500">Need to verify a trusted-contact token? <Link href="/verify" className="font-bold text-emerald-800 hover:underline">Go to verification</Link></p>
        </div>
      </section>
    </div>
  );
}
