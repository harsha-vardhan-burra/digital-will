import type { Metadata } from 'next';
import './globals.css';
import { Navbar } from '@/components/Navbar';

export const metadata: Metadata = {
  title: 'Digital Will | Secure Estate Planning',
  description: 'Organise estate assets, trusted contacts and beneficiary instructions in one secure, auditable workspace.',
  applicationName: 'Digital Will',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen flex flex-col antialiased">
        <Navbar />
        <main className="app-main flex-1 w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 md:py-10">
          {children}
        </main>
        <footer className="site-footer mt-auto px-4 py-6 text-center">
          <div className="mx-auto flex max-w-6xl flex-col items-center justify-center gap-2 text-xs text-slate-500 sm:flex-row sm:gap-3">
            <span className="inline-flex items-center gap-1.5 font-semibold text-slate-600"><span className="h-1.5 w-1.5 rounded-full bg-emerald-600" /> Digital Will</span>
            <span className="hidden sm:inline">·</span>
            <span>Encrypted document storage</span>
            <span className="hidden sm:inline">·</span>
            <span>Owner-authorized estate actions</span>
            <span className="hidden sm:inline">·</span>
            <span>Auditable succession workflow</span>
          </div>
        </footer>
      </body>
    </html>
  );
}
