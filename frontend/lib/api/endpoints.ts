import { apiFetch } from "./client";
import type { Task, TaskPage, User } from "./types";

export const getMe = (signal?: AbortSignal) => apiFetch<User>("/api/auth/me", { signal });

export const login = (email: string, password: string) =>
  apiFetch<User>("/api/auth/login", { method: "POST", json: { email, password } });

export const register = (email: string, password: string, displayName: string) =>
  apiFetch<User>("/api/auth/register", { method: "POST", json: { email, password, displayName } });

export const logout = () => apiFetch<void>("/api/auth/logout", { method: "POST" });

export function listTasks(params: { templateId?: string; limit?: number }, signal?: AbortSignal) {
  const qs = new URLSearchParams();
  if (params.templateId) qs.set("templateId", params.templateId);
  qs.set("limit", String(params.limit ?? 50));
  return apiFetch<TaskPage>(`/api/tasks?${qs.toString()}`, { signal });
}

/** 批量查询未结束任务的最新状态（轮询用）。 */
export function getTasksByIds(ids: number[], signal?: AbortSignal) {
  return apiFetch<TaskPage>(`/api/tasks?ids=${ids.join(",")}`, { signal });
}

export const createTask = (templateId: string, assetIds: number[]) =>
  apiFetch<Task>("/api/tasks", { method: "POST", json: { templateId, assetIds } });
