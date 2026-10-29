"use client";

import type { Template } from "@/lib/api/types";
import { CreatePanel } from "./CreatePanel";
import { TaskBoard } from "./TaskBoard";

/** 详情/制作页：左侧模板信息与上传位，右侧该模板下的个人历史任务（UI-02 / UI-03）。 */
export function TemplateWorkspace({ template }: { template: Template }) {
  return (
    <div className="workspace">
      <CreatePanel template={template} />
      <TaskBoard templateId={template.id} title="历史记录" />
    </div>
  );
}
