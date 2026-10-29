"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/client";
import { getMe, logout as apiLogout } from "@/lib/api/endpoints";
import type { User } from "@/lib/api/types";
import { meKey } from "@/lib/tasks/keys";

interface AuthState {
  user: User | null;
  status: "loading" | "authenticated" | "unauthenticated" | "error";
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();
  const onLoginPage = pathname === "/login";

  const me = useQuery({
    queryKey: meKey,
    queryFn: ({ signal }) => getMe(signal),
    enabled: !onLoginPage,
    staleTime: 5 * 60_000,
    retry: (count, err) => !(err instanceof ApiError && err.kind === "unauthorized") && count < 2,
  });

  const unauthorized = me.error instanceof ApiError && me.error.kind === "unauthorized";

  useEffect(() => {
    if (unauthorized && !onLoginPage) {
      // 会话失效：清空所有用户数据，避免残留到下一个登录用户
      void qc.cancelQueries();
      qc.clear();
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [unauthorized, onLoginPage, pathname, qc, router]);

  const logout = useCallback(async () => {
    try {
      await apiLogout();
    } finally {
      // 先取消在途请求再清空缓存：旧用户的响应不会写回
      await qc.cancelQueries();
      qc.clear();
      router.replace("/login");
      router.refresh();
    }
  }, [qc, router]);

  const value = useMemo<AuthState>(
    () => ({
      user: me.data ?? null,
      status: me.data
        ? "authenticated"
        : unauthorized || onLoginPage
          ? "unauthenticated"
          : me.isError
            ? "error"
            : "loading",
      logout,
    }),
    [me.data, me.isError, unauthorized, onLoginPage, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
