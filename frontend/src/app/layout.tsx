import type { Metadata } from 'next';
import Link from 'next/link';
import './globals.css';

export const metadata: Metadata = {
  title: 'Digital Will — Estate Succession & Controlled Disclosure',
  description: 'Deterministic, envelope-encrypted digital estate management platform',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark">
      <body className="bg-zinc-950 text-zinc-100 antialiased min-h-screen flex flex-col">
        <header className="border-b border-zinc-800 bg-zinc-900/60 backdrop-blur-md sticky top-0 z-50">
          <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">
            <Link href="/" className="flex items-center gap-2 font-semibold text-lg tracking-tight">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 ring-4 ring-emerald-500/20" />
              <span>Digital Will</span>
              <span className="text-xs px-2 py-0.5 rounded bg-zinc-800 text-zinc-400 font-mono">Phase 2</span>
            </Link>
            <nav className="flex items-center gap-6 text-sm text-zinc-400">
              <Link href="/estate" className="hover:text-zinc-100 transition-colors">
                Estate
              </Link>
              <Link href="/documents" className="hover:text-zinc-100 transition-colors">
                Documents
              </Link>
              <Link href="/verify" className="hover:text-zinc-100 transition-colors">
                Verification
              </Link>
            </nav>
          </div>
        </header>
        <main className="flex-1 max-w-6xl w-full mx-auto px-4 py-8">
          {children}
        </main>
        <footer className="border-t border-zinc-800/80 py-6 text-center text-xs text-zinc-500">
          Deterministic State Engine • AES-256-GCM Envelope Encryption • Hash-Chained Audit Trail
        </footer>
      </body>
    </html>
  );
}
