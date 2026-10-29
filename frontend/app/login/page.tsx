"use client";

import { Suspense, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/client";
import { login, register } from "@/lib/api/endpoints";
import { meKey } from "@/lib/tasks/keys";

const DEMO_ACCOUNTS = [
  { email: "alice@demo.local", name: "Alice" },
  { email: "bob@demo.local", name: "Bob" },
];

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const qc = useQueryClient();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const next = (() => {
    const n = params.get("next");
    return n && n.startsWith("/") && !n.startsWith("//") ? n : "/";
  })();

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      const user = mode === "login" ? await login(email, password) : await register(email, password, displayName);
      // 切换用户：取消并清空上一个用户的一切缓存
      await qc.cancelQueries();
      qc.clear();
      qc.setQueryData(meKey, user);
      router.replace(next);
      router.refresh();
    } catch (err) {
      if (err instanceof ApiError) {
        const fields = (err.details?.fields ?? null) as Record<string, string> | null;
        setError(fields ? Object.values(fields).join("；") : err.message);
      } else {
        setError("登录失败，请稍后重试");
      }
      setPending(false);
    }
  };

  return (
    <div className="auth-wrap">
      <form className="panel auth-card" onSubmit={onSubmit} noValidate>
        <div className="auth-tabs" role="tablist" aria-label="登录方式">
          <button
            type="button"
            role="tab"
            aria-selected={mode === "login"}
            className={mode === "login" ? "active" : ""}
            onClick={() => setMode("login")}
          >
            登录
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === "register"}
            className={mode === "register" ? "active" : ""}
            onClick={() => setMode("register")}
          >
            注册
          </button>
        </div>

        <label className="field">
          <span>邮箱</span>
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        {mode === "register" && (
          <label className="field">
            <span>昵称（可选）</span>
            <input value={displayName} maxLength={64} onChange={(e) => setDisplayName(e.target.value)} />
          </label>
        )}
        <label className="field">
          <span>密码</span>
          <input
            type="password"
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            required
            minLength={6}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>

        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="btn btn-primary btn-block" disabled={pending || !email || !password}>
          {pending ? "请稍候…" : mode === "login" ? "登录" : "注册并登录"}
        </button>

        {mode === "login" && (
          <div className="demo-accounts">
            <p className="muted small">演示账号（密码 demo1234）：</p>
            <div className="demo-buttons">
              {DEMO_ACCOUNTS.map((a) => (
                <button
                  key={a.email}
                  type="button"
                  className="btn btn-small"
                  onClick={() => {
                    setEmail(a.email);
                    setPassword("demo1234");
                  }}
                >
                  填入 {a.name}
                </button>
              ))}
            </div>
          </div>
        )}
      </form>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
