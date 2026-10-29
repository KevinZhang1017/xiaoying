package com.mockstudio.simulation;

import com.mockstudio.pipeline.StepFailure;
import com.mockstudio.task.TaskStage;
import org.springframework.stereotype.Component;

/** 在每个阶段执行前模拟耗时，并按配置注入确定性的失败。 */
@Component
public class Simulator {

    public static final String SIMULATED_FAILURE = "SIMULATED_FAILURE";

    public void beforeStep(SimulationConfig config, TaskStage stage) throws InterruptedException {
        long delay = config.delayFor(stage);
        if (delay > 0) {
            Thread.sleep(delay);
        }
        if (config.failStage() == stage) {
            throw new StepFailure(SIMULATED_FAILURE, "模拟失败：已配置在「" + stage.label() + "」阶段失败");
        }
    }
}
