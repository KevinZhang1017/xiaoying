package com.mockstudio.task;

import com.mockstudio.config.CoreConfig;
import com.mockstudio.pipeline.TaskPipeline;
import com.mockstudio.pipeline.TaskStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在受理事务提交之后再把任务投递到后台线程池，
 * 避免后台线程读到尚未提交的任务记录。
 */
@Component
public class TaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatcher.class);

    private final ThreadPoolTaskExecutor executor;
    private final TaskPipeline pipeline;
    private final TaskStateStore store;

    public TaskDispatcher(@Qualifier(CoreConfig.PIPELINE_EXECUTOR) ThreadPoolTaskExecutor executor,
                          TaskPipeline pipeline, TaskStateStore store) {
        this.executor = executor;
        this.pipeline = pipeline;
        this.store = store;
    }

    /** 队列已满时在受理阶段直接拒绝（503），而不是先建任务再失败。 */
    public boolean isSaturated() {
        return executor.getActiveCount() >= executor.getMaxPoolSize()
                && executor.getThreadPoolExecutor().getQueue().remainingCapacity() == 0;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCreated(TaskCreatedEvent event) {
        try {
            executor.execute(() -> pipeline.run(event.taskId()));
        } catch (TaskRejectedException e) {
            log.warn("Task {} rejected by executor", event.taskId());
            store.fail(event.taskId(), null, "SERVICE_BUSY", "系统繁忙，任务未能进入执行队列");
        }
    }
}
