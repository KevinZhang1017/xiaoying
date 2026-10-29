package com.mockstudio.pipeline;

import com.mockstudio.simulation.Simulator;
import com.mockstudio.task.TaskStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 多步骤后台流水线：按 {@link TaskStage} 声明顺序依次执行步骤。
 * 任一步骤失败即停止，后续步骤不会执行；失败阶段与原因写回数据库。
 */
@Component
public class TaskPipeline {

    private static final Logger log = LoggerFactory.getLogger(TaskPipeline.class);

    private final List<TaskStep> steps;
    private final TaskStateStore store;
    private final Simulator simulator;

    public TaskPipeline(List<TaskStep> steps, TaskStateStore store, Simulator simulator) {
        List<TaskStep> ordered = new ArrayList<>(steps);
        ordered.sort(Comparator.comparing(TaskStep::stage));
        Set<TaskStage> seen = EnumSet.noneOf(TaskStage.class);
        for (TaskStep s : ordered) {
            if (!seen.add(s.stage())) {
                throw new IllegalStateException("Duplicate step for stage " + s.stage());
            }
        }
        if (!seen.equals(EnumSet.allOf(TaskStage.class))) {
            throw new IllegalStateException("Pipeline must define every stage, got " + seen);
        }
        this.steps = List.copyOf(ordered);
        this.store = store;
        this.simulator = simulator;
    }

    public List<TaskStage> stages() {
        return steps.stream().map(TaskStep::stage).toList();
    }

    public void run(long taskId) {
        Optional<TaskContext> claimed;
        try {
            claimed = store.claim(taskId);
        } catch (RuntimeException e) {
            log.error("Task {} could not be claimed", taskId, e);
            store.fail(taskId, null, "INTERNAL_ERROR", "任务初始化失败");
            return;
        }
        if (claimed.isEmpty()) {
            log.info("Task {} is not pending, skip", taskId);
            return;
        }
        TaskContext ctx = claimed.get();

        for (TaskStep step : steps) {
            TaskStage stage = step.stage();
            store.enterStage(taskId, stage);
            log.info("Task {} entered stage {}", taskId, stage);
            try {
                simulator.beforeStep(ctx.simulation(), stage);
                step.execute(ctx);
            } catch (StepFailure f) {
                log.info("Task {} failed at {}: {}", taskId, stage, f.getMessage());
                store.fail(taskId, stage, f.code(), f.getMessage());
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                store.fail(taskId, stage, "INTERRUPTED", "任务执行被中断");
                return;
            } catch (Exception e) {
                log.error("Task {} crashed at {}", taskId, stage, e);
                store.fail(taskId, stage, "INTERNAL_ERROR", "「" + stage.label() + "」阶段发生内部错误");
                return;
            }
            store.completeStage(taskId, stage);
        }
        store.succeed(taskId, ctx.resultPath(), ctx.resultUrl());
        log.info("Task {} succeeded", taskId);
    }
}
