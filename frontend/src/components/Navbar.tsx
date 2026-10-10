'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { api, ApiException, clearAuthToken, getAuthToken } from '@/lib/api';
import { User } from '@/lib/types';
import { ArrowUpRight, FileKey2, Files, LayoutDashboard, LogIn, LogOut, Menu, ShieldCheck, UserCircle2, X } from 'lucide-react';

const links = [
  { href: '/estate', label: 'My estate', icon: LayoutDashboard },
  { href: '/documents', label: 'Documents', icon: Files },
  { href: '/verify', label: 'Verify a token', icon: FileKey2 },
];

export function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);
  const [logoutLoading, setLogoutLoading] = useState(false);

  useEffect(() => {
    let active = true;
    const token = getAuthToken();
    if (!token) {
      setUser(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    api.getMe()
      .then((data) => { if (active) setUser(data); })
      .catch((error: unknown) => {
        if (error instanceof ApiException && error.status === 401) clearAuthToken();
        if (active) setUser(null);
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [pathname]);

  useEffect(() => { setMenuOpen(false); }, [pathname]);

  const handleLogout = async () => {
    if (logoutLoading) return;
    setLogoutLoading(true);
    try {
      await api.logout();
    } catch {
      // The API client clears the local token in its finally block even if
      // the revocation request cannot reach the server.
    } finally {
      setUser(null);
      setLogoutLoading(false);
      router.replace('/auth');
      router.refresh();
    }
  };

  const renderLinks = (mobile = false) => links.map(({ href, label, icon: Icon }) => {
    const active = pathname === href || (href === '/estate' && pathname.startsWith('/estate'));
    return (
      <Link key={href} href={href} className={`nav-link ${active ? 'nav-link-active' : ''} ${mobile ? 'w-full justify-start' : ''}`}>
        <Icon className="h-4 w-4" strokeWidth={1.8} />
        <span>{label}</span>
      </Link>
    );
  });

  return (
    <header className="sticky top-0 z-50 border-b border-emerald-950/5 bg-white/90 backdrop-blur-xl">
      <div className="mx-auto flex h-[72px] max-w-[1320px] items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
        <Link href="/" className="group flex min-w-0 items-center gap-3" aria-label="Digital Will home">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-[14px] bg-emerald-700 text-white shadow-sm shadow-emerald-900/15 transition-transform group-hover:scale-[1.03]">
            <ShieldCheck className="h-5 w-5" strokeWidth={2} />
          </span>
          <span className="min-w-0">
            <span className="block truncate text-[15px] font-extrabold tracking-tight text-[#17382c]">Digital Will</span>
            <span className="hidden text-[11px] font-medium text-slate-500 sm:block">Your estate, clearly organised</span>
          </span>
        </Link>

        <nav className="hidden items-center gap-1 lg:flex" aria-label="Main navigation">
          {renderLinks()}
        </nav>

        <div className="hidden items-center gap-3 md:flex">
          {loading ? (
            <span className="h-8 w-28 animate-pulse rounded-xl bg-slate-100" aria-label="Checking session" />
          ) : user ? (
            <>
              <div className="flex items-center gap-2.5 rounded-xl border border-slate-100 bg-slate-50/70 px-3 py-2">
                <span className="grid h-8 w-8 place-items-center rounded-full bg-emerald-100 text-emerald-800"><UserCircle2 className="h-4 w-4" /></span>
                <span className="max-w-36 truncate text-xs font-semibold text-slate-700">{user.fullName || user.email}</span>
              </div>
              <button type="button" onClick={handleLogout} disabled={logoutLoading} className="secondary-button !min-h-10 !px-3 !text-xs" title="Sign out">
                <LogOut className="h-4 w-4" /> {logoutLoading ? 'Signing out…' : 'Sign out'}
              </button>
            </>
          ) : (
            <Link href="/auth" className="primary-button !min-h-10 !px-4 !text-xs">Sign in <ArrowUpRight className="h-4 w-4" /></Link>
          )}
        </div>

        <button type="button" className="mobile-menu ml-auto lg:hidden" aria-label={menuOpen ? 'Close navigation' : 'Open navigation'} aria-expanded={menuOpen} onClick={() => setMenuOpen((open) => !open)}>
          {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </div>

      {menuOpen && (
        <div className="border-t border-slate-100 bg-white px-4 py-4 shadow-lg lg:hidden">
          <nav className="mx-auto flex max-w-[1320px] flex-col gap-1" aria-label="Mobile navigation">
            <Link href="/" className={`nav-link ${pathname === '/' ? 'nav-link-active' : ''}`}><ShieldCheck className="h-4 w-4" /> Overview</Link>
            {renderLinks(true)}
            <div className="my-2 border-t border-slate-100" />
            {!loading && (user ? (
              <>
                <div className="px-3 py-2 text-xs text-slate-500">Signed in as <strong className="text-slate-700">{user.fullName || user.email}</strong></div>
                <button type="button" onClick={handleLogout} disabled={logoutLoading} className="nav-link w-full text-left"><LogOut className="h-4 w-4" /> {logoutLoading ? 'Signing out…' : 'Sign out'}</button>
              </>
            ) : (
              <Link href="/auth" className="nav-link nav-link-active"><LogIn className="h-4 w-4" /> Sign in or create account</Link>
            ))}
          </nav>
        </div>
      )}
    </header>
  );
}
