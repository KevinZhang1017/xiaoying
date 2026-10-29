package com.mockstudio.config;

import com.mockstudio.task.TaskStage;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Storage storage,
        Executor executor,
        Auth auth,
        DemoUsers demoUsers,
        DevApi devApi,
        Simulation simulation
) {
    public record Storage(String root) {
    }

    public record Executor(int corePoolSize, int maxPoolSize, int queueCapacity) {
    }

    public record Auth(String cookieName, boolean cookieSecure, Duration sessionTtl) {
    }

    public record DemoUsers(boolean enabled, String password) {
    }

    public record DevApi(boolean enabled) {
    }

    /**
     * @param delaysMs  每个阶段的模拟耗时
     * @param failStage 指定失败的阶段，null 表示不注入失败
     */
    public record Simulation(Map<TaskStage, Long> delaysMs, TaskStage failStage) {
    }
}
