package com.mockstudio.pipeline;

import com.mockstudio.task.TaskStage;

/** 流水线中的一个步骤。抛出 {@link StepFailure} 表示可预期的业务失败，其它异常视为内部错误。 */
public interface TaskStep {

    TaskStage stage();

    void execute(TaskContext ctx) throws Exception;
}
