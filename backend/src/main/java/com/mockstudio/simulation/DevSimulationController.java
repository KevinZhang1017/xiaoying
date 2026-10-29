package com.mockstudio.simulation;

import com.mockstudio.common.ApiException;
import com.mockstudio.task.TaskStage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.Map;

/**
 * 开发/演示接口：调整模拟耗时、指定失败阶段。仅当 app.dev-api.enabled=true 时注册。
 * 新配置只影响之后创建的任务。
 */
@RestController
@RequestMapping("/api/dev/simulation")
@ConditionalOnProperty(prefix = "app.dev-api", name = "enabled", havingValue = "true")
public class DevSimulationController {

    private static final long MAX_DELAY_MS = 120_000;

    private final SimulationSettings settings;

    public DevSimulationController(SimulationSettings settings) {
        this.settings = settings;
    }

    /**
     * @param delaysMs  需要修改的阶段耗时，未出现的阶段保持不变
     * @param failStage 失败阶段：不传（或 null）保持不变；传 "" 或 "NONE" 取消失败注入
     */
    public record UpdateRequest(Map<TaskStage, Long> delaysMs, String failStage) {
    }

    @GetMapping
    public SimulationConfig get() {
        return settings.current();
    }

    @PutMapping
    public SimulationConfig update(@RequestBody UpdateRequest req) {
        SimulationConfig cur = settings.current();
        Map<TaskStage, Long> delays = new EnumMap<>(TaskStage.class);
        delays.putAll(cur.delaysMs());
        if (req.delaysMs() != null) {
            req.delaysMs().forEach((stage, ms) -> {
                if (ms == null || ms < 0 || ms > MAX_DELAY_MS) {
                    throw ApiException.badRequest("INVALID_DELAY", "耗时需在 0-" + MAX_DELAY_MS + " 毫秒之间");
                }
                delays.put(stage, ms);
            });
        }
        TaskStage failStage = cur.failStage();
        if (req.failStage() != null) {
            String raw = req.failStage().trim();
            if (raw.isEmpty() || raw.equalsIgnoreCase("NONE")) {
                failStage = null;
            } else {
                try {
                    failStage = TaskStage.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw ApiException.badRequest("INVALID_STAGE",
                            "failStage 只能是 ASSET_CHECK / GENERATE / FINALIZE / NONE");
                }
            }
        }
        return settings.update(new SimulationConfig(delays, failStage));
    }

    @PostMapping("/reset")
    public SimulationConfig reset() {
        return settings.reset();
    }
}
