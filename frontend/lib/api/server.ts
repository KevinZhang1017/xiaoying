import type { Category, Template } from "./types";

/**
 * 仅在服务端组件中使用：直接请求 Java 内网地址（模板为公开数据，不需要会话）。
 */
const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

async function serverGet<T>(path: string): Promise<T | null> {
  const res = await fetch(`${API}${path}`, { cache: "no-store" });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`请求 ${path} 失败（HTTP ${res.status}）`);
  return (await res.json()) as T;
}

export async function fetchCategories(): Promise<Category[]> {
  return (await serverGet<Category[]>("/api/categories")) ?? [];
}

export async function fetchTemplates(category?: string): Promise<Template[]> {
  const qs = category ? `?category=${encodeURIComponent(category)}` : "";
  return (await serverGet<Template[]>(`/api/templates${qs}`)) ?? [];
}

export function fetchTemplate(id: string): Promise<Template | null> {
  return serverGet<Template>(`/api/templates/${encodeURIComponent(id)}`);
}
