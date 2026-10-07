import type { Metadata } from 'next';
import './globals.css';
import { Navbar } from '@/components/Navbar';

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
        <Navbar />
        <main className="flex-1 max-w-6xl w-full mx-auto px-4 py-8">
          {children}
        </main>
        <footer className="border-t border-zinc-800/80 py-6 text-center text-xs text-zinc-500">
          Deterministic State Engine • AES-256-GCM Envelope Encryption • Tamper-Evident Hash-Chained Audit Trail • Digital Will Phase 3
        </footer>
      </body>
    </html>
  );
}
