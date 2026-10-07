'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import { mapErrorToUIState } from '@/lib/utils';
import { Shield, Lock, Mail, User, ArrowRight, AlertCircle, CheckCircle2 } from 'lucide-react';

export default function AuthPage() {
  const router = useRouter();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!email || !password) {
      setErrorMessage('Please fill in all required fields.');
      return;
    }

    if (mode === 'register' && !fullName.trim()) {
      setErrorMessage('Please provide your full name.');
      return;
    }

    if (password.length < 8) {
      setErrorMessage('Password must be at least 8 characters.');
      return;
    }

    setLoading(true);

    try {
      if (mode === 'register') {
        await api.register(email.trim(), password, fullName.trim());
      } else {
        await api.login(email.trim(), password);
      }
      router.push('/estate');
    } catch (err) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md mx-auto py-12 px-4 space-y-8">
      <div className="text-center space-y-2">
        <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 mb-2">
          <Shield className="w-6 h-6" />
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-zinc-100">
          {mode === 'login' ? 'Sign in to Digital Will' : 'Create your Digital Will Account'}
        </h1>
        <p className="text-sm text-zinc-400">
          {mode === 'login'
            ? 'Access your encrypted digital estate and succession plans'
            : 'Establish a secure digital estate with envelope encryption'}
        </p>
      </div>

      <div className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-6 space-y-6">
        <div className="flex rounded-lg bg-zinc-950 p-1 border border-zinc-800">
          <button
            type="button"
            onClick={() => {
              setMode('login');
              setErrorMessage(null);
            }}
            className={`flex-1 py-1.5 text-xs font-medium rounded-md transition-all ${
              mode === 'login'
                ? 'bg-zinc-800 text-zinc-100 shadow-sm'
                : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => {
              setMode('register');
              setErrorMessage(null);
            }}
            className={`flex-1 py-1.5 text-xs font-medium rounded-md transition-all ${
              mode === 'register'
                ? 'bg-zinc-800 text-zinc-100 shadow-sm'
                : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Register
          </button>
        </div>

        {errorMessage && (
          <div className="p-3 rounded-lg bg-red-950/40 border border-red-500/30 text-red-300 text-xs flex items-start gap-2">
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-red-400" />
            <span>{errorMessage}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {mode === 'register' && (
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-zinc-300 flex items-center gap-1.5">
                <User className="w-3.5 h-3.5 text-zinc-500" />
                Full Name
              </label>
              <input
                type="text"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Eleanor Vance"
                className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-sm text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500 transition-colors"
                required
              />
            </div>
          )}

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-zinc-300 flex items-center gap-1.5">
              <Mail className="w-3.5 h-3.5 text-zinc-500" />
              Email Address
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="eleanor@example.com"
              className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-sm text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500 transition-colors"
              required
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-zinc-300 flex items-center gap-1.5">
              <Lock className="w-3.5 h-3.5 text-zinc-500" />
              Password
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Minimum 8 characters"
              className="w-full px-3 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-sm text-zinc-100 placeholder:text-zinc-600 focus:outline-none focus:border-emerald-500 transition-colors"
              required
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 px-4 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed mt-2"
          >
            {loading ? (
              <span>Authenticating...</span>
            ) : (
              <>
                <span>{mode === 'login' ? 'Sign In' : 'Create Account'}</span>
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </button>
        </form>
      </div>

      <div className="p-4 rounded-lg bg-zinc-900/20 border border-zinc-800/60 text-xs text-zinc-500 space-y-1">
        <div className="flex items-center gap-1.5 text-zinc-400 font-medium">
          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
          <span>Security Boundary Guarantee</span>
        </div>
        <p>
          Passwords are protected with BCrypt work factor 12. Sessions utilize 256-bit SHA-256 hashed bearer tokens.
          Ownership is enforced server-side on every request.
        </p>
      </div>
    </div>
  );
}
