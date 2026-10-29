"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createTask } from "@/lib/api/endpoints";
import type { Task, TaskPage } from "@/lib/api/types";
import { insertTask } from "./mergeTask";
import { taskKeys } from "./keys";

/**
 * 提交制作任务。受理成功（202）后立即把任务插入相关列表缓存，
 * 卡片随即以「排队中」出现并被自动跟踪；按钮不会停留在「提交中」。
 */
export function useCreateTask(userId: number | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { templateId: string; assetIds: number[] }) => createTask(vars.templateId, vars.assetIds),
    onSuccess: (task: Task) => {
      if (userId == null) return;
      for (const [key, data] of qc.getQueriesData<TaskPage>({ queryKey: taskKeys.lists(userId) })) {
        const scope = key[3];
        if (!data || (scope !== "all" && scope !== task.templateId)) continue;
        qc.setQueryData<TaskPage>(key, { ...data, items: insertTask(data.items, task) });
      }
    },
  });
}
