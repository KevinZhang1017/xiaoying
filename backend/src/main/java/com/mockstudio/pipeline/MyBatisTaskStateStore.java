package com.mockstudio.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockstudio.asset.Asset;
import com.mockstudio.common.Times;
import com.mockstudio.simulation.SimulationConfig;
import com.mockstudio.task.Task;
import com.mockstudio.task.TaskMapper;
import com.mockstudio.task.TaskStage;
import com.mockstudio.task.TaskStepLog;
import com.mockstudio.template.Template;
import com.mockstudio.template.TemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class MyBatisTaskStateStore implements TaskStateStore {

    private static final Logger log = LoggerFactory.getLogger(MyBatisTaskStateStore.class);

    private final TaskMapper taskMapper;
    private final TemplateMapper templateMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    public MyBatisTaskStateStore(TaskMapper taskMapper, TemplateMapper templateMapper, ObjectMapper objectMapper,
                                 Clock clock, PlatformTransactionManager txManager) {
        this.taskMapper = taskMapper;
        this.templateMapper = templateMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        // 可能在受理事务的 AFTER_COMMIT 回调中被调用，必须开启独立事务
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Optional<TaskContext> claim(long taskId) {
        if (taskMapper.markRunning(taskId, Times.now(clock)) == 0) {
            return Optional.empty();
        }
        Task task = taskMapper.findById(taskId);
        Template template = templateMapper.findById(task.getTemplateId());
        List<Asset> assets = taskMapper.findAssetsOfTask(taskId);
        return Optional.of(new TaskContext(taskId, task.getUserId(), template, assets, parseSim(task)));
    }

    @Override
    public void enterStage(long taskId, TaskStage stage) {
        tx.executeWithoutResult(s -> {
            Instant now = Times.now(clock);
            if (taskMapper.updateStage(taskId, stage, now) > 0) {
                taskMapper.insertStepStarted(taskId, stage, now);
            }
        });
    }

    @Override
    public void completeStage(long taskId, TaskStage stage) {
        taskMapper.finishStep(taskId, stage, TaskStepLog.DONE, null, Times.now(clock));
    }

    @Override
    public void fail(long taskId, TaskStage stage, String code, String message) {
        tx.executeWithoutResult(s -> {
            Instant now = Times.now(clock);
            int rows = taskMapper.markFailed(taskId, stage, code, message, now);
            if (rows > 0 && stage != null) {
                taskMapper.finishStep(taskId, stage, TaskStepLog.FAILED, message, now);
            }
        });
    }

    @Override
    public void succeed(long taskId, String resultPath, String resultUrl) {
        taskMapper.markSucceeded(taskId, resultPath, resultUrl, Times.now(clock));
    }

    private SimulationConfig parseSim(Task task) {
        if (task.getSimConfig() == null || task.getSimConfig().isBlank()) {
            return SimulationConfig.instant();
        }
        try {
            return objectMapper.readValue(task.getSimConfig(), SimulationConfig.class);
        } catch (JsonProcessingException e) {
            log.warn("Task {} has unreadable sim_config, falling back to instant", task.getId(), e);
            return SimulationConfig.instant();
        }
    }
}
