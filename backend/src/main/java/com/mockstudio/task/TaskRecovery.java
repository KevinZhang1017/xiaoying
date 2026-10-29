package com.mockstudio.task;

import com.mockstudio.common.Times;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * 服务重启边界：本项目不做断点续跑。启动时（Web 服务对外开放之前）
 * 把上次进程遗留的 PENDING / RUNNING 任务标记为失败，避免前端永远轮询一个不会结束的任务。
 */
@Component
public class TaskRecovery implements SmartInitializingSingleton {

    public static final String CODE = "SERVICE_RESTARTED";

    private static final Logger log = LoggerFactory.getLogger(TaskRecovery.class);

    private final TaskMapper mapper;
    private final Clock clock;

    public TaskRecovery(TaskMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Instant now = Times.now(clock);
        int steps = mapper.failAllRunningSteps("服务重启导致中断", now);
        int tasks = mapper.failAllUnfinished(CODE, "服务重启导致任务中断，请重新提交", now);
        if (tasks > 0 || steps > 0) {
            log.warn("Marked {} unfinished task(s) as failed after restart", tasks);
        }
    }
}
