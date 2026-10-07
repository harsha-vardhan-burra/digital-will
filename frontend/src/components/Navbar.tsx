'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { api, clearAuthToken, getAuthToken } from '@/lib/api';
import { User } from '@/lib/types';
import { Shield, UserCircle, LogOut } from 'lucide-react';

export function Navbar() {
  const pathname = usePathname();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = getAuthToken();
    if (!token) {
      setUser(null);
      setLoading(false);
      return;
    }

    api
      .getMe()
      .then((data) => setUser(data))
      .catch(() => {
        clearAuthToken();
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, [pathname]);

  const handleLogout = async () => {
    await api.logout();
    setUser(null);
    window.location.href = '/auth';
  };

  return (
    <header className="border-b border-zinc-800 bg-zinc-900/60 backdrop-blur-md sticky top-0 z-50">
      <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-2 font-semibold text-lg tracking-tight">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 ring-4 ring-emerald-500/20" />
          <span>Digital Will</span>
          <span className="text-xs px-2 py-0.5 rounded bg-emerald-950/60 text-emerald-400 border border-emerald-500/30 font-mono">
            Phase 3 MVP
          </span>
        </Link>
        <nav className="flex items-center gap-6 text-sm text-zinc-400">
          <Link
            href="/estate"
            className={`transition-colors hover:text-zinc-100 ${
              pathname === '/estate' ? 'text-emerald-400 font-medium' : ''
            }`}
          >
            Estate & Review
          </Link>
          <Link
            href="/documents"
            className={`transition-colors hover:text-zinc-100 ${
              pathname === '/documents' ? 'text-emerald-400 font-medium' : ''
            }`}
          >
            Documents
          </Link>
          <Link
            href="/verify"
            className={`transition-colors hover:text-zinc-100 ${
              pathname === '/verify' ? 'text-emerald-400 font-medium' : ''
            }`}
          >
            Verification
          </Link>

          {!loading && (
            <div className="flex items-center gap-3 pl-4 border-l border-zinc-800">
              {user ? (
                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-1.5 text-xs text-zinc-300">
                    <UserCircle className="w-4 h-4 text-emerald-400" />
                    <span>{user.fullName || user.email}</span>
                  </div>
                  <button
                    onClick={handleLogout}
                    title="Log out"
                    className="p-1.5 rounded-md hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition-colors"
                  >
                    <LogOut className="w-4 h-4" />
                  </button>
                </div>
              ) : (
                <Link
                  href="/auth"
                  className="px-3 py-1.5 rounded-md text-xs font-medium bg-emerald-600 hover:bg-emerald-500 text-white transition-colors"
                >
                  Sign In
                </Link>
              )}
            </div>
          )}
        </nav>
      </div>
    </header>
  );
}
