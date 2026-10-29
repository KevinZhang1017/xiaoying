package com.mockstudio.task;

/** 后台处理阶段，按声明顺序执行：素材检查 → 模拟生成 → 结果整理。 */
public enum TaskStage {
    ASSET_CHECK("素材检查"),
    GENERATE("模拟生成"),
    FINALIZE("结果整理");

    private final String label;

    TaskStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
