"use client";

import { type ReactNode, FormEvent, useState } from "react";
import { useAuth } from "@/lib/auth/auth-provider";

/**
 * Auth Boundary — shows login when anonymous, shows children when authenticated.
 * Also handles INITIALIZING, EXPIRED, and ERROR states.
 * The login form is rendered inline (not imported from /app/login) to avoid
 * SSR issues with useAuth.
 *
 * Login is email+password only — no Tenant UUID required.
 * Tenant context is derived automatically from the authenticated session.
 */
export function AuthBoundary({ children }: { children: ReactNode }) {
  const { state, error, login } = useAuth();

  if (state === "AUTHENTICATED") {
    return <>{children}</>;
  }

  if (state === "INITIALIZING") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#0f2d2a]" dir="rtl">
        <p className="text-sm font-bold text-teal-200">جارٍ التحقق من الجلسة…</p>
      </div>
    );
  }

  // ANONYMOUS, AUTHENTICATING, ERROR, EXPIRED, LOGGING_OUT → show login form
  return <LoginForm login={login} state={state} error={error} />;
}

function LoginForm({
  login,
  state,
  error,
}: {
  login: (req: { email: string; password: string }) => Promise<void>;
  state: string;
  error: { title: string; message: string } | null;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const busy = state === "AUTHENTICATING";

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    await login({ email: email.trim(), password });
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#0f2d2a] px-4" dir="rtl">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-6 text-center">
          <p className="text-xs font-black tracking-[0.18em] text-teal-700">SANAD BUSINESS OPERATING SYSTEM</p>
          <h1 className="mt-2 text-2xl font-black text-slate-900">تسجيل الدخول</h1>
          <p className="mt-1 text-sm text-slate-500">أدخل بريدك الإلكتروني وكلمة المرور</p>
        </div>

        {error && (
          <div className="mb-4 rounded-xl bg-rose-50 p-3 ring-1 ring-rose-600/20" role="alert">
            <p className="text-sm font-black text-rose-700">{error.title}</p>
            <p className="mt-1 text-sm text-rose-600">{error.message}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <label className="grid gap-1 text-sm font-bold text-slate-700">
            البريد الإلكتروني
            <input
              dir="ltr"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="snad@app.com"
              className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-600"
            />
          </label>
          <label className="grid gap-1 text-sm font-bold text-slate-700">
            كلمة المرور
            <input
              dir="ltr"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-teal-600"
            />
          </label>
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-xl bg-teal-800 px-4 py-3 font-black text-white disabled:opacity-50"
          >
            {busy ? "جارٍ تسجيل الدخول…" : "تسجيل الدخول"}
          </button>
        </form>
      </div>
    </div>
  );
}
