"use client";

import { STAGE_LABEL, type Task } from "@/lib/api/types";
import { StepProgress } from "./StepProgress";

const timeFmt = new Intl.DateTimeFormat("zh-CN", {
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});

export function formatTime(iso: string | null) {
  if (!iso) return "";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "" : timeFmt.format(d);
}

export function TaskCard({
  task,
  showTemplate,
  onPreview,
}: {
  task: Task;
  showTemplate?: boolean;
  onPreview: (task: Task) => void;
}) {
  const thumb = task.inputs[0]?.url;

  return (
    <article className={`task-card task-${task.status.toLowerCase()}`} data-testid={`task-${task.id}`}>
      <div className="task-visual">
        {task.status === "SUCCEEDED" && task.resultUrl ? (
          <button
            type="button"
            className="task-result"
            onClick={() => onPreview(task)}
            aria-label={`预览任务 #${task.id} 的制作结果`}
          >
            <img src={task.resultUrl} alt={`任务 #${task.id} 的制作结果`} loading="lazy" />
          </button>
        ) : (
          <>
            {thumb && <img className="task-thumb-bg" src={thumb} alt="" aria-hidden loading="lazy" />}
            <div className="task-state" role="status" aria-live="polite">
              {task.status === "PENDING" && (
                <>
                  <span className="spinner" aria-hidden />
                  <strong>排队中</strong>
                  <span className="muted">等待后台处理</span>
                </>
              )}
              {task.status === "RUNNING" && (
                <>
                  <span className="spinner" aria-hidden />
                  <strong>处理中</strong>
                  <span className="muted">{task.stage ? `${STAGE_LABEL[task.stage]}…` : "准备中…"}</span>
                </>
              )}
              {task.status === "FAILED" && (
                <>
                  <strong className="text-danger">制作失败</strong>
                  {task.stage && <span className="muted">失败阶段：{STAGE_LABEL[task.stage]}</span>}
                  <span className="task-error">{task.errorMessage ?? "未知原因"}</span>
                </>
              )}
            </div>
          </>
        )}
      </div>
      <div className="task-meta">
        <StepProgress task={task} />
        <div className="task-meta-row">
          <span className="task-title">{showTemplate ? task.templateTitle : `任务 #${task.id}`}</span>
          <time dateTime={task.createdAt} className="muted">
            {formatTime(task.createdAt)}
          </time>
        </div>
      </div>
    </article>
  );
}
