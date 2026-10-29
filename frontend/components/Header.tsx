"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { IconUser } from "./Icons";
import { Modal } from "./Modal";

export function Header() {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  if (pathname === "/login") {
    return (
      <header className="topbar">
        <span className="brand">
          <span className="brand-mark" aria-hidden />
          MockStudio
        </span>
      </header>
    );
  }

  return (
    <header className="topbar">
      <Link href="/" className="brand">
        <span className="brand-mark" aria-hidden />
        MockStudio
      </Link>
      <nav className="topnav" aria-label="主导航">
        <Link href="/" aria-current={pathname === "/" ? "page" : undefined}>
          模板
        </Link>
        <Link href="/history" aria-current={pathname === "/history" ? "page" : undefined}>
          我的记录
        </Link>
      </nav>
      <button type="button" className="account-btn" onClick={() => setOpen(true)} aria-label="账户设置">
        <IconUser />
        <span className="account-name">{user?.displayName ?? "…"}</span>
      </button>

      <Modal open={open} onClose={() => setOpen(false)} title="账户设置">
        {user ? (
          <div className="account">
            <div className="account-card">
              <div className="avatar" aria-hidden>
                <IconUser width={32} height={32} />
              </div>
              <div>
                <div className="account-display">{user.displayName}</div>
                <div className="muted">用户 ID：{user.id}</div>
              </div>
            </div>
            <dl className="account-fields">
              <dt>邮箱</dt>
              <dd>{user.email}</dd>
              <dt>记录范围</dt>
              <dd>仅显示该账号创建的素材与制作任务</dd>
            </dl>
            <button
              type="button"
              className="btn btn-secondary btn-block"
              disabled={busy}
              onClick={async () => {
                setBusy(true);
                setOpen(false);
                await logout();
                setBusy(false);
              }}
            >
              {busy ? "正在退出…" : "退出登录 / 切换账号"}
            </button>
          </div>
        ) : (
          <p className="muted">正在加载账户信息…</p>
        )}
      </Modal>
    </header>
  );
}
