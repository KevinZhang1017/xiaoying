import type { Task, TaskStage, TaskStatus, Template } from "@/lib/api/types";

let clock = Date.parse("2026-01-01T00:00:00.000Z");
/** 单调递增的时间戳，模拟服务端 updatedAt。 */
export const tick = () => new Date((clock += 1000)).toISOString();

export function makeTask(id: number, status: TaskStatus, extra: Partial<Task> = {}): Task {
  const now = tick();
  return {
    id,
    templateId: "demo-postcard",
    templateTitle: "旅行明信片",
    status,
    stage: (status === "PENDING" ? null : "ASSET_CHECK") as TaskStage | null,
    errorCode: null,
    errorMessage: null,
    resultUrl: status === "SUCCEEDED" ? `/api/tasks/${id}/result` : null,
    inputs: [],
    steps: [],
    createdAt: now,
    startedAt: null,
    finishedAt: null,
    updatedAt: now,
    ...extra,
  };
}

export const postcardTemplate: Template = {
  id: "demo-postcard",
  title: "旅行明信片",
  categoryId: "travel",
  categoryName: "旅行风景",
  coverUrl: "/demo/covers/demo-postcard.jpg",
  description: "上传两张旅途照片，模拟生成一张双联明信片。",
  requiredImageCount: 2,
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 5 * 1024 * 1024,
};

export function deferred<T>() {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

export const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
