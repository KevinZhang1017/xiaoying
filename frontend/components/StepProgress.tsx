import { STAGE_LABEL, STAGE_ORDER, type Task, type TaskStage } from "@/lib/api/types";

type StepState = "done" | "active" | "failed" | "todo";

export function stepState(task: Task, stage: TaskStage): StepState {
  const log = task.steps.find((s) => s.stage === stage);
  if (log?.status === "DONE") return "done";
  if (log?.status === "FAILED" || (task.status === "FAILED" && task.stage === stage)) return "failed";
  if (task.status === "RUNNING" && task.stage === stage) return "active";
  return "todo";
}

const STATE_TEXT: Record<StepState, string> = {
  done: "已完成",
  active: "进行中",
  failed: "失败",
  todo: "未开始",
};

/** 后台三个阶段的进度，状态来自服务端的步骤记录。 */
export function StepProgress({ task }: { task: Task }) {
  return (
    <ol className="steps" aria-label="处理阶段">
      {STAGE_ORDER.map((stage) => {
        const state = stepState(task, stage);
        return (
          <li key={stage} className={`step step-${state}`}>
            <span className="step-dot" aria-hidden />
            <span className="step-label">{STAGE_LABEL[stage]}</span>
            <span className="sr-only">：{STATE_TEXT[state]}</span>
          </li>
        );
      })}
    </ol>
  );
}
