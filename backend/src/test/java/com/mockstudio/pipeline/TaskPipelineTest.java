package com.mockstudio.pipeline;

import com.mockstudio.simulation.SimulationConfig;
import com.mockstudio.simulation.Simulator;
import com.mockstudio.task.TaskStage;
import com.mockstudio.template.Template;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 纯单元测试：用内存中的 {@link TaskStateStore} 与记录调用的假步骤，
 * 验证步骤顺序、失败中断、以及不可重复执行。
 */
class TaskPipelineTest {

    /** 记录所有对状态存储的调用，形如 "enter:GENERATE"、"fail:GENERATE:CODE"。 */
    static class RecordingStore implements TaskStateStore {
        final List<String> events = new ArrayList<>();
        boolean claimable = true;
        SimulationConfig sim = SimulationConfig.instant();

        @Override
        public Optional<TaskContext> claim(long taskId) {
            events.add("claim");
            if (!claimable) {
                return Optional.empty();
            }
            Template t = new Template();
            t.setId("demo");
            t.setRequiredImageCount(1);
            return Optional.of(new TaskContext(taskId, 1L, t, List.of(), sim));
        }

        @Override
        public void enterStage(long taskId, TaskStage stage) {
            events.add("enter:" + stage);
        }

        @Override
        public void completeStage(long taskId, TaskStage stage) {
            events.add("done:" + stage);
        }

        @Override
        public void fail(long taskId, TaskStage stage, String code, String message) {
            events.add("fail:" + stage + ":" + code);
        }

        @Override
        public void succeed(long taskId, String resultPath, String resultUrl) {
            events.add("succeed:" + resultUrl);
        }
    }

    static class FakeStep implements TaskStep {
        final TaskStage stage;
        final List<String> calls;
        final RuntimeException toThrow;

        FakeStep(TaskStage stage, List<String> calls, RuntimeException toThrow) {
            this.stage = stage;
            this.calls = calls;
            this.toThrow = toThrow;
        }

        @Override
        public TaskStage stage() {
            return stage;
        }

        @Override
        public void execute(TaskContext ctx) {
            calls.add(stage.name());
            if (toThrow != null) {
                throw toThrow;
            }
            if (stage == TaskStage.FINALIZE) {
                ctx.setResult("results/1.jpg", "/api/tasks/1/result");
            }
        }
    }

    private final Simulator simulator = new Simulator();

    @Test
    void runsStepsInDeclaredOrderRegardlessOfRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        RecordingStore store = new RecordingStore();
        // 故意乱序注册
        TaskPipeline pipeline = new TaskPipeline(List.of(
                new FakeStep(TaskStage.FINALIZE, calls, null),
                new FakeStep(TaskStage.ASSET_CHECK, calls, null),
                new FakeStep(TaskStage.GENERATE, calls, null)), store, simulator);

        pipeline.run(1L);

        assertThat(calls).containsExactly("ASSET_CHECK", "GENERATE", "FINALIZE");
        assertThat(store.events).containsExactly(
                "claim",
                "enter:ASSET_CHECK", "done:ASSET_CHECK",
                "enter:GENERATE", "done:GENERATE",
                "enter:FINALIZE", "done:FINALIZE",
                "succeed:/api/tasks/1/result");
    }

    @Test
    void stepFailureStopsSubsequentStepsAndRecordsStage() {
        List<String> calls = new ArrayList<>();
        RecordingStore store = new RecordingStore();
        TaskPipeline pipeline = new TaskPipeline(List.of(
                new FakeStep(TaskStage.ASSET_CHECK, calls, null),
                new FakeStep(TaskStage.GENERATE, calls, new StepFailure("BOOM", "生成失败")),
                new FakeStep(TaskStage.FINALIZE, calls, null)), store, simulator);

        pipeline.run(1L);

        assertThat(calls).containsExactly("ASSET_CHECK", "GENERATE");
        assertThat(store.events).containsExactly(
                "claim",
                "enter:ASSET_CHECK", "done:ASSET_CHECK",
                "enter:GENERATE", "fail:GENERATE:BOOM");
        assertThat(store.events).noneMatch(e -> e.startsWith("succeed") || e.contains("FINALIZE"));
    }

    @Test
    void simulatedFailureAtConfiguredStageSkipsThatStepAndLaterOnes() {
        List<String> calls = new ArrayList<>();
        RecordingStore store = new RecordingStore();
        store.sim = new SimulationConfig(Map.of(), TaskStage.ASSET_CHECK);
        TaskPipeline pipeline = new TaskPipeline(List.of(
                new FakeStep(TaskStage.ASSET_CHECK, calls, null),
                new FakeStep(TaskStage.GENERATE, calls, null),
                new FakeStep(TaskStage.FINALIZE, calls, null)), store, simulator);

        pipeline.run(1L);

        assertThat(calls).isEmpty();
        assertThat(store.events).containsExactly("claim", "enter:ASSET_CHECK",
                "fail:ASSET_CHECK:" + Simulator.SIMULATED_FAILURE);
    }

    @Test
    void unexpectedExceptionIsReportedAsInternalErrorOfThatStage() {
        List<String> calls = new ArrayList<>();
        RecordingStore store = new RecordingStore();
        TaskPipeline pipeline = new TaskPipeline(List.of(
                new FakeStep(TaskStage.ASSET_CHECK, calls, null),
                new FakeStep(TaskStage.GENERATE, calls, null),
                new FakeStep(TaskStage.FINALIZE, calls, new IllegalStateException("disk full"))), store, simulator);

        pipeline.run(1L);

        assertThat(calls).containsExactly("ASSET_CHECK", "GENERATE", "FINALIZE");
        assertThat(store.events).last().isEqualTo("fail:FINALIZE:INTERNAL_ERROR");
    }

    @Test
    void taskThatCannotBeClaimedIsNotExecutedAgain() {
        List<String> calls = new ArrayList<>();
        RecordingStore store = new RecordingStore();
        store.claimable = false; // 例如已是终态或已被其它线程领取
        TaskPipeline pipeline = new TaskPipeline(List.of(
                new FakeStep(TaskStage.ASSET_CHECK, calls, null),
                new FakeStep(TaskStage.GENERATE, calls, null),
                new FakeStep(TaskStage.FINALIZE, calls, null)), store, simulator);

        pipeline.run(1L);

        assertThat(calls).isEmpty();
        assertThat(store.events).containsExactly("claim");
    }

    @Test
    void pipelineRequiresEveryStageExactlyOnce() {
        List<String> calls = new ArrayList<>();
        RecordingStore store = new RecordingStore();
        assertThatThrownBy(() -> new TaskPipeline(List.of(
                new FakeStep(TaskStage.ASSET_CHECK, calls, null),
                new FakeStep(TaskStage.GENERATE, calls, null)), store, simulator))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TaskPipeline(List.of(
                new FakeStep(TaskStage.ASSET_CHECK, calls, null),
                new FakeStep(TaskStage.ASSET_CHECK, calls, null),
                new FakeStep(TaskStage.GENERATE, calls, null),
                new FakeStep(TaskStage.FINALIZE, calls, null)), store, simulator))
                .isInstanceOf(IllegalStateException.class);
    }
}
