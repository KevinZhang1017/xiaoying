import type { Task, TaskStatus } from "@/lib/api/types";

export const isTerminal = (s: TaskStatus) => s === "SUCCEEDED" || s === "FAILED";

/**
 * 用服务端返回的新快照更新本地任务，保证：
 * 1. 已结束的任务不会被「处理中」的旧响应覆盖（终态不回退）；
 * 2. updatedAt 更早的乱序响应被丢弃；
 * 3. 只按 id 合并，绝不把 A 的数据写到 B 上。
 */
export function mergeTask(prev: Task | undefined, next: Task): Task {
  if (!prev) return next;
  if (prev.id !== next.id) return prev;
  if (isTerminal(prev.status) && !isTerminal(next.status)) return prev;
  const p = Date.parse(prev.updatedAt);
  const n = Date.parse(next.updatedAt);
  if (Number.isFinite(p) && Number.isFinite(n) && n < p) return prev;
  return next;
}

/** 把一批更新合并进列表；列表中不存在的任务被忽略。无变化时返回原数组（引用不变）。 */
export function mergeTaskList(list: Task[], updates: Task[]): Task[] {
  if (updates.length === 0) return list;
  const byId = new Map(updates.map((t) => [t.id, t]));
  let changed = false;
  const out = list.map((t) => {
    const u = byId.get(t.id);
    if (!u) return t;
    const merged = mergeTask(t, u);
    if (merged !== t) changed = true;
    return merged;
  });
  return changed ? out : list;
}

/** 新受理的任务插入列表头部（已存在则按合并规则更新）。 */
export function insertTask(list: Task[], task: Task): Task[] {
  if (list.some((t) => t.id === task.id)) return mergeTaskList(list, [task]);
  return [task, ...list];
}

/** 需要继续跟踪的任务 id（升序，便于作为稳定的查询 key）。 */
export function activeTaskIds(list: Task[]): number[] {
  return list
    .filter((t) => !isTerminal(t.status))
    .map((t) => t.id)
    .sort((a, b) => a - b);
}
