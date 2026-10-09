'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { api, getAuthToken } from '@/lib/api';
import { DocumentResponse, UIState, WillResponse } from '@/lib/types';
import { formatBytes, formatDate, mapErrorToUIState } from '@/lib/utils';
import { AlertCircle, ArrowRight, CheckCircle2, Download, FileCheck2, FileText, Files, Fingerprint, LockKeyhole, RefreshCw, Search, ShieldCheck, UploadCloud } from 'lucide-react';

export default function DocumentsPage() {
  const [will, setWill] = useState<WillResponse | null>(null);
  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uiState, setUiState] = useState<UIState>('idle');
  const [uploading, setUploading] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  const loadDocuments = useCallback(async (willId: string) => {
    setUiState('loading');
    setErrorMessage(null);
    try {
      const items = await api.listDocuments(willId);
      setDocuments(items);
      setUiState('success');
    } catch (error: unknown) {
      const { state, message } = mapErrorToUIState(error);
      setUiState(state);
      setErrorMessage(message);
    }
  }, []);

  const loadInitial = useCallback(async () => {
    setErrorMessage(null);
    if (!getAuthToken()) {
      setUiState('unauthorized');
      return;
    }
    setUiState('loading');
    try {
      const myWill = await api.getMyWill();
      setWill(myWill);
      if (!myWill) {
        setDocuments([]);
        setUiState('not_found');
        return;
      }
      await loadDocuments(myWill.id);
    } catch (error: unknown) {
      const { state, message } = mapErrorToUIState(error);
      setUiState(state);
      setErrorMessage(message);
    }
  }, [loadDocuments]);

  useEffect(() => { void loadInitial(); }, [loadInitial]);

  const visibleDocuments = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return documents;
    return documents.filter((document) => document.fileName.toLowerCase().includes(term) || document.contentType.toLowerCase().includes(term));
  }, [documents, search]);

  const handleUpload = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!will || !selectedFile || uploading) return;
    setUploading(true);
    setErrorMessage(null);
    setSuccessMessage(null);
    try {
      await api.uploadDocument(will.id, selectedFile);
      setSelectedFile(null);
      const input = document.getElementById('document-file') as HTMLInputElement | null;
      if (input) input.value = '';
      setSuccessMessage('Document uploaded. The server has processed it through the configured encrypted-storage workflow.');
      await loadDocuments(will.id);
    } catch (error: unknown) {
      const { message } = mapErrorToUIState(error);
      setErrorMessage(message);
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (item: DocumentResponse) => {
    if (!will || downloadingId) return;
    setDownloadingId(item.id);
    setErrorMessage(null);
    setSuccessMessage(null);
    try {
      await api.downloadDocument(item.id, will.id, item.fileName);
      setSuccessMessage(`Download started for “${item.fileName}”.`);
    } catch (error: unknown) {
      const { message } = mapErrorToUIState(error);
      setErrorMessage(message);
    } finally {
      setDownloadingId(null);
    }
  };

  if (uiState === 'unauthorized') {
    return <div className="mx-auto max-w-xl py-12"><div className="surface p-8 text-center sm:p-10"><span className="icon-tile mx-auto"><LockKeyhole className="h-5 w-5" /></span><h1 className="mt-5 text-2xl font-extrabold text-[#18392d]">Sign in to manage documents</h1><p className="mt-2 text-sm leading-6 text-slate-500">Document access requires an authenticated owner session.</p><Link href="/auth" className="primary-button mt-6">Sign in or create account <ArrowRight className="h-4 w-4" /></Link></div></div>;
  }

  if (uiState === 'not_found' || (!will && uiState === 'success')) {
    return <div className="mx-auto max-w-xl py-12"><div className="surface p-8 text-center sm:p-10"><span className="icon-tile mx-auto"><Files className="h-5 w-5" /></span><h1 className="mt-5 text-2xl font-extrabold text-[#18392d]">Create your estate first</h1><p className="mt-2 text-sm leading-6 text-slate-500">Documents are attached to your Digital Will. Create an estate workspace, then return here to upload or retrieve files.</p><Link href="/estate" className="primary-button mt-6">Open estate workspace <ArrowRight className="h-4 w-4" /></Link></div></div>;
  }

  if (uiState === 'loading' && !will) {
    return <div className="mx-auto max-w-xl py-20 text-center"><RefreshCw className="mx-auto h-6 w-6 animate-spin text-emerald-700" /><p className="mt-4 text-sm text-slate-500">Loading your secure document workspace…</p></div>;
  }

  if (!will && (uiState === 'server_error' || uiState === 'network_error')) {
    return <div className="mx-auto max-w-xl py-12"><div className="surface p-8 text-center sm:p-10"><span className="icon-tile mx-auto"><AlertCircle className="h-5 w-5" /></span><h1 className="mt-5 text-2xl font-extrabold text-[#18392d]">Could not reach your estate</h1><p className="mt-2 text-sm leading-6 text-slate-500">{errorMessage || 'The document workspace could not load from the backend.'}</p><button type="button" onClick={() => void loadInitial()} className="primary-button mt-6"><RefreshCw className="h-4 w-4" /> Retry connection</button></div></div>;
  }

  return (
    <div className="space-y-8">
      <section className="flex flex-col justify-between gap-5 md:flex-row md:items-end">
        <div className="space-y-3"><span className="eyebrow"><LockKeyhole className="h-3.5 w-3.5" /> Document vault</span><h1 className="page-title">Your important files,<br className="hidden sm:block" /> in one protected place.</h1><p className="body-copy max-w-2xl text-sm sm:text-base">Upload documents for your estate and retrieve them when needed. The frontend sends files to your existing backend; encryption and authorization remain server-controlled.</p></div>
        {will && <div className="surface flex items-center gap-3 px-4 py-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-emerald-50 text-emerald-800"><FileCheck2 className="h-5 w-5" /></span><div><p className="text-xs font-bold text-slate-500">Current estate</p><p className="mt-0.5 max-w-56 truncate text-sm font-extrabold text-slate-800">{will.title}</p></div></div>}
      </section>

      {errorMessage && <div className="status-message status-error flex items-start gap-2.5" role="alert"><AlertCircle className="mt-0.5 h-4 w-4 shrink-0" /><span>{errorMessage}</span><button type="button" onClick={() => setErrorMessage(null)} className="ml-auto text-xs font-bold">Dismiss</button></div>}
      {successMessage && <div className="status-message status-success flex items-start gap-2.5" role="status"><CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" /><span>{successMessage}</span><button type="button" onClick={() => setSuccessMessage(null)} className="ml-auto text-xs font-bold">Dismiss</button></div>}

      <div className="grid gap-6 lg:grid-cols-[.85fr_1.15fr]">
        <section className="surface p-5 sm:p-7">
          <div className="flex items-start gap-3"><span className="icon-tile"><UploadCloud className="h-5 w-5" /></span><div><h2 className="text-lg font-extrabold text-slate-800">Add a document</h2><p className="mt-1 text-sm leading-6 text-slate-500">Choose the file you want associated with this estate.</p></div></div>
          <form onSubmit={handleUpload} className="mt-6 space-y-4">
            <label htmlFor="document-file" className="flex min-h-48 cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed border-[#cfe1d5] bg-[#f8fbf9] px-5 py-7 text-center transition hover:border-emerald-400 hover:bg-emerald-50/50">
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-white text-emerald-800 shadow-sm"><UploadCloud className="h-5 w-5" /></span>
              <span className="mt-4 max-w-full break-all text-sm font-extrabold text-slate-700">{selectedFile ? selectedFile.name : 'Choose a file to upload'}</span>
              <span className="mt-1 text-xs text-slate-500">{selectedFile ? `${formatBytes(selectedFile.size)} · ${selectedFile.type || 'Unknown file type'}` : 'Select a file from your device'}</span>
              <span className="mt-3 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-bold text-slate-700">Browse files</span>
              <input id="document-file" type="file" className="sr-only" onChange={(event) => setSelectedFile(event.target.files?.[0] ?? null)} />
            </label>
            <button type="submit" disabled={!selectedFile || uploading || !will} className="primary-button w-full disabled:opacity-50">{uploading ? <><RefreshCw className="h-4 w-4 animate-spin" /> Uploading…</> : <><LockKeyhole className="h-4 w-4" /> Upload document <ArrowRight className="h-4 w-4" /></>}</button>
          </form>
          <div className="mt-5 rounded-xl border border-slate-100 bg-slate-50 p-4"><p className="flex items-center gap-2 text-xs font-extrabold text-slate-700"><ShieldCheck className="h-4 w-4 text-emerald-700" /> Server-controlled protection</p><p className="mt-1 text-xs leading-5 text-slate-500">Files are uploaded over the authenticated API. The backend owns encryption, ownership validation and download authorization.</p></div>
        </section>

        <section className="surface min-w-0 p-5 sm:p-7">
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start"><div className="flex items-start gap-3"><span className="icon-tile"><Files className="h-5 w-5" /></span><div><h2 className="text-lg font-extrabold text-slate-800">Estate documents</h2><p className="mt-1 text-sm text-slate-500">Files currently returned for your estate.</p></div></div><button type="button" onClick={() => will && loadDocuments(will.id)} disabled={!will || uiState === 'loading'} className="secondary-button !min-h-10 !text-xs"><RefreshCw className={`h-3.5 w-3.5 ${uiState === 'loading' ? 'animate-spin' : ''}`} /> Refresh list</button></div>

          <div className="mt-5 grid grid-cols-2 gap-3"><div className="rounded-2xl bg-[#f6faf7] p-4"><p className="text-xs font-semibold text-slate-500">Total documents</p><p className="mt-1 text-2xl font-extrabold tracking-tight text-slate-800">{documents.length}</p></div><div className="rounded-2xl bg-[#f6faf7] p-4"><p className="text-xs font-semibold text-slate-500">Stored file size</p><p className="mt-1 text-2xl font-extrabold tracking-tight text-slate-800">{formatBytes(documents.reduce((sum, item) => sum + item.fileSize, 0))}</p></div></div>

          <div className="relative mt-5"><Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" /><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search by file name or type…" className="field-control !pl-10" /></div>

          <div className="mt-4 space-y-2">
            {uiState === 'loading' && <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-500"><RefreshCw className="h-4 w-4 animate-spin" /> Loading documents…</div>}
            {uiState !== 'loading' && visibleDocuments.length === 0 && <div className="rounded-2xl border border-dashed border-slate-200 px-5 py-10 text-center"><span className="mx-auto grid h-11 w-11 place-items-center rounded-xl bg-slate-50 text-slate-500"><FileText className="h-5 w-5" /></span><p className="mt-3 text-sm font-bold text-slate-700">{documents.length === 0 ? 'No documents yet' : 'No matching files'}</p><p className="mt-1 text-xs leading-5 text-slate-500">{documents.length === 0 ? 'Upload your first document using the panel on the left.' : 'Try a different search phrase.'}</p></div>}
            {visibleDocuments.map((item) => <article key={item.id} className="flex min-w-0 flex-col gap-3 rounded-2xl border border-slate-100 bg-white p-4 transition hover:border-emerald-100 sm:flex-row sm:items-center sm:justify-between"><div className="flex min-w-0 items-start gap-3"><span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-blue-50 text-blue-800"><FileText className="h-4 w-4" /></span><div className="min-w-0"><p className="break-words text-sm font-extrabold text-slate-800">{item.fileName}</p><p className="mt-1 text-xs text-slate-500">{formatBytes(item.fileSize)} <span className="px-1">·</span> {item.contentType}</p><p className="mt-1 truncate font-mono text-[10px] text-slate-400" title={item.checksumSha256}>SHA-256 · {item.checksumSha256}</p><p className="mt-1 text-[11px] text-slate-400">Added {formatDate(item.createdAt)}</p></div></div><button type="button" onClick={() => handleDownload(item)} disabled={downloadingId !== null} className="secondary-button shrink-0 !min-h-9 !text-xs"><Download className="h-3.5 w-3.5" /> {downloadingId === item.id ? 'Preparing…' : 'Download'}</button></article>)}
          </div>
        </section>
      </div>

      <div className="flex items-start gap-3 rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4 sm:p-5"><Fingerprint className="mt-0.5 h-5 w-5 shrink-0 text-emerald-800" /><div><p className="text-sm font-extrabold text-emerald-950">Integrity and access</p><p className="mt-1 text-xs leading-5 text-emerald-900/75">The checksum shown above comes from document metadata returned by the backend. It is informational here; access checks and decryption are performed by the server.</p></div></div>
    </div>
  );
}
