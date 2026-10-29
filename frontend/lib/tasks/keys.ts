/**
 * 所有任务相关的缓存 key 都以 userId 开头：切换用户后旧用户的数据天然不会被读到。
 */
export const taskKeys = {
  all: (userId: number) => ["tasks", userId] as const,
  lists: (userId: number) => ["tasks", userId, "list"] as const,
  list: (userId: number, templateId?: string) => ["tasks", userId, "list", templateId ?? "all"] as const,
  poll: (userId: number, ids: number[]) => ["tasks", userId, "poll", ids.join(",")] as const,
};

export const meKey = ["me"] as const;
