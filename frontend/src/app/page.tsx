import Link from 'next/link';
import { ArrowRight, ArrowUpRight, BadgeCheck, Check, ChevronRight, FileLock2, Files, Fingerprint, HeartHandshake, Landmark, LockKeyhole, ShieldCheck, UserRoundCheck, Users, Workflow } from 'lucide-react';

const capabilities = [
  {
    icon: Landmark,
    number: '01',
    title: 'Organise your estate',
    description: 'Keep assets, beneficiary details and allocation instructions in one structured workspace.',
    href: '/estate',
    link: 'Open estate workspace',
    tone: 'green',
  },
  {
    icon: FileLock2,
    number: '02',
    title: 'Protect important documents',
    description: 'Upload and retrieve estate documents through the backend’s encrypted document workflow.',
    href: '/documents',
    link: 'Manage documents',
    tone: 'blue',
  },
  {
    icon: UserRoundCheck,
    number: '03',
    title: 'Verify with trusted contacts',
    description: 'Submit a verification token when you have been given one. The server records the confirmation.',
    href: '/verify',
    link: 'Verify a token',
    tone: 'violet',
  },
];

const trustPoints = [
  { icon: LockKeyhole, title: 'Private by design', copy: 'Authenticated estate actions' },
  { icon: Fingerprint, title: 'Integrity checks', copy: 'Auditable activity history' },
  { icon: HeartHandshake, title: 'Controlled disclosure', copy: 'Beneficiary-scoped packages' },
];

export default function Home() {
  return (
    <div className="space-y-16 md:space-y-24">
      <section className="grid items-center gap-10 lg:grid-cols-[1.04fr_.96fr] lg:gap-14">
        <div className="space-y-7">
          <span className="eyebrow"><ShieldCheck className="h-4 w-4" /> A clearer way to plan ahead</span>
          <div className="space-y-5">
            <h1 className="max-w-3xl text-[clamp(2.9rem,7vw,5.2rem)] font-extrabold leading-[.99] tracking-[-.065em] text-[#15392c]">
              Your legacy.<br />Organised with <span className="text-emerald-700">care.</span>
            </h1>
            <p className="max-w-xl text-base leading-8 text-slate-600 sm:text-lg">
              A guided workspace for recording your estate, choosing beneficiaries, protecting documents and setting up trusted-contact verification—all in one place.
            </p>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Link href="/estate" className="primary-button !min-h-12 !rounded-[14px] !px-5">Open my estate <ArrowRight className="h-4 w-4" /></Link>
            <Link href="/auth" className="secondary-button !min-h-12 !rounded-[14px] !px-5">Create a secure account <ArrowUpRight className="h-4 w-4" /></Link>
          </div>
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2 pt-1 text-xs font-medium text-slate-500">
            <span className="inline-flex items-center gap-1.5"><Check className="h-3.5 w-3.5 text-emerald-700" /> Server-checked permissions</span>
            <span className="inline-flex items-center gap-1.5"><Check className="h-3.5 w-3.5 text-emerald-700" /> Guided setup</span>
            <span className="inline-flex items-center gap-1.5"><Check className="h-3.5 w-3.5 text-emerald-700" /> Clear status and feedback</span>
          </div>
        </div>

        <div className="relative mx-auto w-full max-w-[570px]">
          <div className="absolute -inset-4 rounded-[32px] bg-gradient-to-br from-emerald-100/80 via-white to-blue-50/80 blur-2xl" />
          <div className="surface surface-raised relative overflow-hidden p-5 sm:p-7">
            <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-5">
              <div className="flex items-center gap-3">
                <span className="grid h-11 w-11 place-items-center rounded-[15px] bg-emerald-700 text-white"><Workflow className="h-5 w-5" /></span>
                <div>
                  <p className="text-sm font-extrabold text-slate-800">Estate workspace</p>
                  <p className="mt-0.5 text-xs text-slate-500">One place for the details that matter</p>
                </div>
              </div>
              <span className="rounded-full border border-emerald-100 bg-emerald-50 px-2.5 py-1 text-[10px] font-bold uppercase tracking-[.09em] text-emerald-800">Guided</span>
            </div>

            <div className="grid grid-cols-3 gap-3 py-5">
              <div className="rounded-2xl bg-[#f5f9f6] p-3 sm:p-4"><span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Assets</span><div className="mt-2 flex items-center gap-2"><Landmark className="h-4 w-4 text-emerald-700" /><span className="text-sm font-bold text-slate-800">Catalogue</span></div></div>
              <div className="rounded-2xl bg-[#f5f8fc] p-3 sm:p-4"><span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">People</span><div className="mt-2 flex items-center gap-2"><Users className="h-4 w-4 text-blue-700" /><span className="text-sm font-bold text-slate-800">Trusted</span></div></div>
              <div className="rounded-2xl bg-[#f8f5fc] p-3 sm:p-4"><span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Records</span><div className="mt-2 flex items-center gap-2"><Files className="h-4 w-4 text-violet-700" /><span className="text-sm font-bold text-slate-800">Protected</span></div></div>
            </div>

            <div className="rounded-2xl border border-slate-100 bg-white p-4 sm:p-5">
              <div className="flex items-center justify-between gap-4">
                <div><p className="text-sm font-bold text-slate-800">A carefully controlled process</p><p className="mt-1 text-xs leading-5 text-slate-500">Progress is determined by the backend—not by a frontend toggle.</p></div>
                <BadgeCheck className="h-6 w-6 shrink-0 text-emerald-700" />
              </div>
              <div className="mt-5 space-y-3">
                {[
                  ['01', 'Record assets and instructions', 'Your estate information'],
                  ['02', 'Add beneficiaries and trusted contacts', 'People and allocations'],
                  ['03', 'Review readiness and audit history', 'Server-validated status'],
                ].map(([n, title, sub]) => (
                  <div key={n} className="flex items-center gap-3">
                    <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full border border-emerald-100 bg-emerald-50 text-[11px] font-extrabold text-emerald-800">{n}</span>
                    <div className="min-w-0 flex-1"><p className="text-xs font-bold text-slate-700">{title}</p><p className="mt-0.5 text-[11px] text-slate-500">{sub}</p></div>
                    <ChevronRight className="h-4 w-4 shrink-0 text-slate-300" />
                  </div>
                ))}
              </div>
            </div>
            <div className="mt-4 flex items-center gap-2 rounded-xl bg-emerald-50/70 px-4 py-3 text-xs leading-5 text-emerald-900"><ShieldCheck className="h-4 w-4 shrink-0" /><span>Owner authorization and state transitions remain enforced by your existing backend.</span></div>
          </div>
        </div>
      </section>

      <section className="space-y-6">
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
          <div className="space-y-3"><span className="eyebrow">Your workspace, simplified</span><h2 className="text-2xl font-extrabold tracking-tight text-[#17382c] sm:text-3xl">Everything important, clearly separated.</h2></div>
          <p className="max-w-lg text-sm leading-6 text-slate-500">Use the section that matches the task you need to complete. Each action sends requests to the existing API.</p>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          {capabilities.map(({ icon: Icon, number, title, description, href, link, tone }) => (
            <Link key={href} href={href} className="group surface block p-6 transition-all duration-200 hover:-translate-y-1 hover:border-emerald-200 hover:shadow-[0_16px_38px_rgba(23,53,43,.07)] sm:p-7">
              <div className="flex items-center justify-between"><span className={`grid h-12 w-12 place-items-center rounded-2xl ${tone === 'green' ? 'bg-emerald-50 text-emerald-800' : tone === 'blue' ? 'bg-blue-50 text-blue-800' : 'bg-violet-50 text-violet-800'}`}><Icon className="h-5 w-5" /></span><span className="font-mono text-xs font-semibold text-slate-300">{number}</span></div>
              <h3 className="mt-6 text-lg font-extrabold tracking-tight text-slate-800">{title}</h3>
              <p className="mt-2 min-h-[72px] text-sm leading-6 text-slate-500">{description}</p>
              <span className="mt-5 inline-flex items-center gap-2 text-sm font-bold text-emerald-800">{link}<ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" /></span>
            </Link>
          ))}
        </div>
      </section>

      <section className="surface overflow-hidden">
        <div className="grid gap-0 md:grid-cols-[.9fr_1.1fr]">
          <div className="bg-[#123b2c] p-7 text-white sm:p-9 lg:p-11">
            <span className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-3 py-1.5 text-xs font-semibold text-emerald-50"><ShieldCheck className="h-4 w-4" /> Security foundation retained</span>
            <h2 className="mt-5 text-2xl font-extrabold leading-tight tracking-tight sm:text-3xl">Clarity at every step.<br />Control where it counts.</h2>
            <p className="mt-4 max-w-md text-sm leading-7 text-emerald-50/75">The frontend helps you understand the workflow without replacing the backend’s authorization, encryption, verification or audit controls.</p>
            <Link href="/estate" className="mt-7 inline-flex items-center gap-2 text-sm font-bold text-white underline decoration-white/40 underline-offset-4 hover:decoration-white">Go to estate workspace <ArrowRight className="h-4 w-4" /></Link>
          </div>
          <div className="grid gap-0 sm:grid-cols-3">
            {trustPoints.map(({ icon: Icon, title, copy }, index) => (
              <div key={title} className={`p-6 sm:p-7 ${index ? 'border-t border-slate-100 sm:border-l sm:border-t-0' : ''}`}>
                <span className="grid h-10 w-10 place-items-center rounded-xl bg-emerald-50 text-emerald-800"><Icon className="h-5 w-5" /></span>
                <h3 className="mt-5 text-sm font-extrabold text-slate-800">{title}</h3>
                <p className="mt-2 text-sm leading-6 text-slate-500">{copy}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
