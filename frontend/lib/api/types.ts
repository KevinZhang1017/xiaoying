export type TaskStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED";
export type TaskStage = "ASSET_CHECK" | "GENERATE" | "FINALIZE";
export type StepStatus = "RUNNING" | "DONE" | "FAILED";

export interface User {
  id: number;
  email: string;
  displayName: string;
}

export interface Category {
  id: string;
  name: string;
}

export interface Template {
  id: string;
  title: string;
  categoryId: string;
  categoryName: string;
  coverUrl: string;
  description: string;
  requiredImageCount: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
}

export interface Asset {
  id: number;
  url: string;
  mimeType: string;
  sizeBytes: number;
  originalFilename: string;
}

export interface TaskStep {
  stage: TaskStage;
  status: StepStatus;
  message: string | null;
  startedAt: string;
  endedAt: string | null;
}

export interface TaskInput {
  slotIndex: number;
  assetId: number;
  url: string;
}

export interface Task {
  id: number;
  templateId: string;
  templateTitle: string;
  status: TaskStatus;
  stage: TaskStage | null;
  errorCode: string | null;
  errorMessage: string | null;
  resultUrl: string | null;
  inputs: TaskInput[];
  steps: TaskStep[];
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  updatedAt: string;
}

export interface TaskPage {
  items: Task[];
  nextCursor: number | null;
}

export const STAGE_ORDER: TaskStage[] = ["ASSET_CHECK", "GENERATE", "FINALIZE"];

export const STAGE_LABEL: Record<TaskStage, string> = {
  ASSET_CHECK: "素材检查",
  GENERATE: "模拟生成",
  FINALIZE: "结果整理",
};
