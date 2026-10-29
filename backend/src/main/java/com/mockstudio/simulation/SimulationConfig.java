package com.mockstudio.simulation;

import com.mockstudio.task.TaskStage;

import java.util.EnumMap;
import java.util.Map;

/**
 * 模拟配置。任务创建时会把当时的配置快照写入 task.sim_config，
 * 之后修改配置不会影响已受理的任务，演示结果可复现。
 *
 * @param delaysMs  各阶段耗时（毫秒）
 * @param failStage 在该阶段注入失败；null 表示全部成功
 */
public record SimulationConfig(Map<TaskStage, Long> delaysMs, TaskStage failStage) {

    public SimulationConfig {
        EnumMap<TaskStage, Long> copy = new EnumMap<>(TaskStage.class);
        if (delaysMs != null) {
            delaysMs.forEach((k, v) -> {
                if (k != null && v != null) {
                    copy.put(k, Math.max(0, v));
                }
            });
        }
        delaysMs = Map.copyOf(copy);
    }

    public long delayFor(TaskStage stage) {
        return delaysMs.getOrDefault(stage, 0L);
    }

    public static SimulationConfig instant() {
        return new SimulationConfig(Map.of(), null);
    }
}
