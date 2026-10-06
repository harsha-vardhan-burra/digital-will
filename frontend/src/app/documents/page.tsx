'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { api, getAuthToken } from '@/lib/api';
import { DocumentResponse, UIState, WillResponse } from '@/lib/types';
import { formatBytes, formatDate, mapErrorToUIState } from '@/lib/utils';
import { Lock, Upload, Download, CheckCircle2, AlertCircle, FileText, Shield, RotateCcw, ArrowRight } from 'lucide-react';

export default function DocumentsPage() {
  const [will, setWill] = useState<WillResponse | null>(null);
  const [willId, setWillId] = useState('');
  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);

  const [uiState, setUiState] = useState<UIState>('idle');
  const [uploadState, setUploadState] = useState<UIState>('idle');
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const loadDocuments = useCallback(async (id: string) => {
    if (!id.trim()) return;
    setUiState('loading');
    setErrorMessage(null);
    try {
      const docs = await api.listDocuments(id.trim());
      setDocuments(docs);
      setUiState('success');
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setUiState(state);
      setErrorMessage(message);
    }
  }, []);

  const loadInitial = useCallback(async () => {
    const token = getAuthToken();
    if (!token) {
      setUiState('unauthorized');
      return;
    }

    try {
      const myWill = await api.getMyWill();
      if (myWill) {
        setWill(myWill);
        setWillId(myWill.id);
        await loadDocuments(myWill.id);
      }
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setUiState(state);
      setErrorMessage(message);
    }
  }, [loadDocuments]);

  useEffect(() => {
    loadInitial();
  }, [loadInitial]);

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!willId.trim() || !selectedFile) {
      setUploadState('validation_error');
      setErrorMessage('Please provide a Will ID and select a file to upload.');
      return;
    }

    setUploadState('loading');
    setErrorMessage(null);
    setSuccessMessage(null);
    try {
      await api.uploadDocument(willId.trim(), selectedFile);
      setUploadState('success');
      setSelectedFile(null);
      setSuccessMessage('Document uploaded and envelope-encrypted (AES-256-GCM).');
      await loadDocuments(willId.trim());
    } catch (err: unknown) {
      const { state, message } = mapErrorToUIState(err);
      setUploadState(state);
      setErrorMessage(message);
    }
  };

  const handleDownload = async (doc: DocumentResponse) => {
    setDownloadingId(doc.id);
    setErrorMessage(null);
    try {
      await api.downloadDocument(doc.id, willId || doc.willId, doc.fileName);
    } catch (err: unknown) {
      const { message } = mapErrorToUIState(err);
      setErrorMessage(message);
    } finally {
      setDownloadingId(null);
    }
  };

  if (uiState === 'unauthorized') {
    return (
      <div className="max-w-md mx-auto py-16 text-center space-y-6">
        <div className="w-14 h-14 rounded-2xl bg-zinc-900 border border-zinc-800 flex items-center justify-center mx-auto text-blue-400">
          <Lock className="w-7 h-7" />
        </div>
        <div className="space-y-2">
          <h2 className="text-xl font-bold text-zinc-100">Authentication Required</h2>
          <p className="text-sm text-zinc-400">
            Encrypted documents require authentication to verify owner authorization.
          </p>
        </div>
        <Link
          href="/auth"
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors"
        >
          <span>Sign In or Register</span>
          <ArrowRight className="w-4 h-4" />
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-zinc-100 flex items-center gap-2">
          <Lock className="w-6 h-6 text-blue-400" />
          Encrypted Document Vault
        </h1>
        <p className="text-sm text-zinc-400 mt-1">
          Store supporting deeds, certificates, and instructions. Files are envelope-encrypted with AES-256-GCM before persistent storage.
        </p>
      </div>

      {/* Security Info Card */}
      <div className="p-4 rounded-xl border border-blue-500/30 bg-blue-500/10 text-blue-300 text-xs flex items-center gap-3">
        <Shield className="w-5 h-5 text-blue-400 shrink-0" />
        <div>
          <span className="font-semibold text-blue-200">Envelope Encryption Model</span>
          <p className="text-blue-300/80 mt-0.5">
            Every document is encrypted with a unique 256-bit AES Data Encryption Key (DEK) wrapped under the platform Master Key.
            Plaintext is never written to disk or database. SHA-256 integrity checksums are verified on retrieval.
          </p>
        </div>
      </div>

      {/* Messages */}
      {errorMessage && (
        <div className="p-4 rounded-xl border border-red-500/30 bg-red-500/10 text-red-300 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}
      {successMessage && (
        <div className="p-4 rounded-xl border border-emerald-500/30 bg-emerald-500/10 text-emerald-300 text-xs flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Upload and Will Selection Panel */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Upload Form */}
        <div className="p-6 rounded-xl border border-zinc-800 bg-zinc-900/40 space-y-4">
          <h2 className="text-sm font-semibold text-zinc-200 flex items-center gap-2">
            <Upload className="w-4 h-4 text-zinc-400" />
            Upload Envelope-Encrypted Document
          </h2>
          <form onSubmit={handleUpload} className="space-y-3">
            <div>
              <label className="block text-xs text-zinc-400 mb-1">Target Will ID</label>
              <input
                type="text"
                value={willId}
                onChange={(e) => setWillId(e.target.value)}
                placeholder="Enter Will ID (UUID)..."
                className="w-full bg-zinc-950 border border-zinc-800 rounded-lg px-3 py-2 text-xs text-zinc-100 placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
                required
              />
            </div>
            <div>
              <label className="block text-xs text-zinc-400 mb-1">Select File</label>
              <input
                type="file"
                onChange={(e) => setSelectedFile(e.target.files ? e.target.files[0] : null)}
                className="w-full text-xs text-zinc-400 file:mr-3 file:py-1.5 file:px-3 file:rounded file:border-0 file:text-xs file:font-semibold file:bg-zinc-800 file:text-zinc-200 hover:file:bg-zinc-700"
                required
              />
            </div>
            <button
              type="submit"
              disabled={uploadState === 'loading' || !selectedFile}
              className="w-full px-4 py-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-white rounded-lg text-xs font-semibold flex items-center justify-center gap-2 transition-colors"
            >
              {uploadState === 'loading' ? (
                <>
                  <RotateCcw className="w-4 h-4 animate-spin" />
                  <span>Encrypting & Uploading...</span>
                </>
              ) : (
                <span>Upload & Encrypt (AES-256-GCM)</span>
              )}
            </button>
          </form>
        </div>

        {/* Load Documents by Will ID */}
        <div className="p-6 rounded-xl border border-zinc-800 bg-zinc-900/40 space-y-4">
          <h2 className="text-sm font-semibold text-zinc-200 flex items-center gap-2">
            <FileText className="w-4 h-4 text-zinc-400" />
            Query Documents
          </h2>
          <div className="flex gap-2">
            <input
              type="text"
              value={willId}
              onChange={(e) => setWillId(e.target.value)}
              placeholder="Will ID..."
              className="flex-1 bg-zinc-950 border border-zinc-800 rounded-lg px-3 py-2 text-xs text-zinc-100 placeholder-zinc-600"
            />
            <button
              onClick={() => loadDocuments(willId)}
              disabled={uiState === 'loading'}
              className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded-lg text-xs font-medium transition-colors"
            >
              {uiState === 'loading' ? 'Loading...' : 'Fetch'}
            </button>
          </div>
          <p className="text-xs text-zinc-500">
            Fetch all document metadata associated with this will. Downloads are streamed and decrypted on-the-fly using the authorized session.
          </p>
        </div>
      </div>

      {/* Documents Table */}
      <div className="p-6 rounded-xl border border-zinc-800 bg-zinc-900/30 space-y-4">
        <h3 className="text-sm font-semibold text-zinc-200 flex items-center justify-between">
          <span>Encrypted Documents ({documents.length})</span>
        </h3>

        {documents.length === 0 ? (
          <p className="text-xs text-zinc-500 py-6 text-center">No documents loaded or available for this Will.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-zinc-400">
              <thead className="border-b border-zinc-800 text-[11px] font-semibold text-zinc-400 uppercase tracking-wider">
                <tr>
                  <th className="py-2.5">File Name</th>
                  <th className="py-2.5">Size</th>
                  <th className="py-2.5">Type</th>
                  <th className="py-2.5">SHA-256 Checksum</th>
                  <th className="py-2.5">Uploaded</th>
                  <th className="py-2.5 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60">
                {documents.map((doc) => (
                  <tr key={doc.id} className="hover:bg-zinc-900/60 transition-colors">
                    <td className="py-3 font-semibold text-zinc-200 flex items-center gap-2">
                      <Lock className="w-3.5 h-3.5 text-blue-400 shrink-0" />
                      <span>{doc.fileName}</span>
                    </td>
                    <td className="py-3">{formatBytes(doc.fileSize)}</td>
                    <td className="py-3 font-mono text-[11px] text-zinc-500">{doc.contentType}</td>
                    <td className="py-3 font-mono text-[10px] text-zinc-500 truncate max-w-[150px]" title={doc.checksumSha256}>
                      {doc.checksumSha256}
                    </td>
                    <td className="py-3">{formatDate(doc.createdAt)}</td>
                    <td className="py-3 text-right">
                      <button
                        onClick={() => handleDownload(doc)}
                        disabled={downloadingId === doc.id}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-zinc-800 hover:bg-zinc-700 text-zinc-200 font-medium transition-colors disabled:opacity-50"
                      >
                        <Download className="w-3 h-3" />
                        <span>{downloadingId === doc.id ? 'Decrypting...' : 'Download'}</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
