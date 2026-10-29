package com.mockstudio.simulation;

import com.mockstudio.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** 当前生效的模拟配置：初始值来自配置文件，可通过开发接口在运行时修改。 */
@Component
public class SimulationSettings {

    private final SimulationConfig defaults;
    private final AtomicReference<SimulationConfig> current;

    public SimulationSettings(AppProperties props) {
        AppProperties.Simulation sim = props.simulation();
        this.defaults = sim == null ? SimulationConfig.instant()
                : new SimulationConfig(sim.delaysMs(), sim.failStage());
        this.current = new AtomicReference<>(defaults);
    }

    public SimulationConfig current() {
        return current.get();
    }

    public SimulationConfig update(SimulationConfig next) {
        current.set(next);
        return next;
    }

    public SimulationConfig reset() {
        current.set(defaults);
        return defaults;
    }
}
