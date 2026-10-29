package com.mockstudio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

@Configuration
public class CoreConfig {

    public static final String PIPELINE_EXECUTOR = "pipelineExecutor";

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * 后台流水线线程池：有界队列，满时在受理阶段返回 503，而不是无限堆积。
     */
    @Bean(name = PIPELINE_EXECUTOR)
    public ThreadPoolTaskExecutor pipelineExecutor(AppProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.executor().corePoolSize());
        executor.setMaxPoolSize(props.executor().maxPoolSize());
        executor.setQueueCapacity(props.executor().queueCapacity());
        executor.setThreadNamePrefix("task-pipeline-");
        // 关闭时不等待：未完成的任务会在下次启动时被标记为「服务重启中断」
        executor.setWaitForTasksToCompleteOnShutdown(false);
        // 不手动 initialize()：Spring 会通过 afterPropertiesSet 初始化，手动调用会创建两个线程池
        return executor;
    }
}
