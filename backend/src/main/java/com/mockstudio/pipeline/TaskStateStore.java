package com.mockstudio.pipeline;

import com.mockstudio.task.TaskStage;

import java.util.Optional;

/**
 * 流水线对任务状态的持久化端口。每个方法都是一次独立提交，
 * 因此每个阶段的进入、完成、失败在数据库中都可被单独观察。
 */
public interface TaskStateStore {

    /** PENDING → RUNNING 并加载上下文；任务不处于 PENDING 时返回 empty（不会重复执行）。 */
    Optional<TaskContext> claim(long taskId);

    /** 记录进入某阶段：更新 task.stage，并写入一条 RUNNING 的步骤日志。 */
    void enterStage(long taskId, TaskStage stage);

    /** 标记某阶段完成。 */
    void completeStage(long taskId, TaskStage stage);

    /** RUNNING/PENDING → FAILED，保存失败阶段与原因；stage 可为 null（尚未进入任何阶段）。 */
    void fail(long taskId, TaskStage stage, String code, String message);

    /** RUNNING → SUCCEEDED，保存结果地址。 */
    void succeed(long taskId, String resultPath, String resultUrl);
}
