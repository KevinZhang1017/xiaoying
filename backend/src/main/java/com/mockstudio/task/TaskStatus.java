package com.mockstudio.task;

/** 任务整体状态。SUCCEEDED / FAILED 为终态，任何路径都不会把终态改回处理中。 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
