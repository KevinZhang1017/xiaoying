"use client";

import { useMemo, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getTasksByIds, listTasks } from "@/lib/api/endpoints";
import type { Task, TaskPage, User } from "@/lib/api/types";
import { activeTaskIds, mergeTask, mergeTaskList } from "./mergeTask";
import { meKey, taskKeys } from "./keys";

export const DEFAULT_POLL_INTERVAL_MS = 1500;
const MAX_BACKOFF_MS = 15_000;

export interface TaskBoardOptions {
  pollIntervalMs?: number;
}

/**
 * 某个范围（某模板 / 全部）的个人任务列表 + 自动跟踪未结束任务。
 *
 * - 列表从 Java 读取，刷新页面后自然恢复；
 * - 只对未结束任务轮询，用一个批量请求查询，结果按 id 合并回所有列表缓存；
 * - 没有未结束任务时 query 被禁用，不再发请求；组件卸载时 TanStack Query 通过 AbortSignal 取消在途请求；
 * - 轮询失败只表现为「同步异常」，任务本身保持最后一次已知状态，并以指数退避继续重试。
 */
export function useTaskBoard(userId: number | undefined, templateId?: string, opts: TaskBoardOptions = {}) {
  const qc = useQueryClient();
  const interval = opts.pollIntervalMs ?? DEFAULT_POLL_INTERVAL_MS;
  const consecutiveFailures = useRef(0);

  const list = useQuery({
    queryKey: userId != null ? taskKeys.list(userId, templateId) : ["tasks", "anonymous"],
    enabled: userId != null,
    queryFn: async ({ signal }) => {
      const page = await listTasks({ templateId, limit: 50 }, signal);
      // 与现有缓存合并，避免较早发出的列表请求覆盖轮询拿到的更新状态
      const prev = qc.getQueryData<TaskPage>(taskKeys.list(userId!, templateId));
      if (!prev) return page;
      const prevById = new Map(prev.items.map((t) => [t.id, t]));
      return { ...page, items: page.items.map((t) => mergeTask(prevById.get(t.id), t)) };
    },
  });

  const items: Task[] = useMemo(() => list.data?.items ?? [], [list.data]);
  const activeIds = useMemo(() => activeTaskIds(items), [items]);

  const poll = useQuery({
    queryKey: userId != null ? taskKeys.poll(userId, activeIds) : ["tasks", "anonymous", "poll"],
    enabled: userId != null && activeIds.length > 0,
    retry: false,
    gcTime: 0,
    refetchIntervalInBackground: false,
    refetchInterval: () =>
      consecutiveFailures.current === 0
        ? interval
        : Math.min(interval * 2 ** consecutiveFailures.current, MAX_BACKOFF_MS),
    queryFn: async ({ signal }) => {
      let page: TaskPage;
      try {
        page = await getTasksByIds(activeIds, signal);
      } catch (e) {
        consecutiveFailures.current += 1;
        throw e;
      }
      consecutiveFailures.current = 0;
      // 用户已切换或请求已被取消：丢弃旧响应，避免覆盖当前用户的数据
      const me = qc.getQueryData<User>(meKey);
      if (signal.aborted || (me && me.id !== userId)) return page;
      qc.setQueriesData<TaskPage>({ queryKey: taskKeys.lists(userId!) }, (old) =>
        old ? { ...old, items: mergeTaskList(old.items, page.items) } : old,
      );
      return page;
    },
  });

  return {
    items,
    activeIds,
    isLoading: list.isPending && userId != null,
    /** 历史列表首次加载失败（无任何数据可展示） */
    loadError: list.isError && !list.data ? (list.error as Error) : null,
    /** 已有数据但同步失败：列表刷新失败或轮询失败 */
    syncError: (poll.isError ? (poll.error as Error) : null) ?? (list.isError && list.data ? (list.error as Error) : null),
    isSyncing: poll.isFetching || list.isFetching,
    refresh: () => {
      void list.refetch();
      if (activeIds.length > 0) void poll.refetch();
    },
    retrySync: () => {
      consecutiveFailures.current = 0;
      if (poll.isError) void poll.refetch();
      if (list.isError) void list.refetch();
    },
  };
}
