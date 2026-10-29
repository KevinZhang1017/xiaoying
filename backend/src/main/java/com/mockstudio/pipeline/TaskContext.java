package com.mockstudio.pipeline;

import com.mockstudio.asset.Asset;
import com.mockstudio.simulation.SimulationConfig;
import com.mockstudio.template.Template;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 单个任务在流水线中传递的上下文，步骤之间通过它交接中间产物。 */
public class TaskContext {

    private final long taskId;
    private final long userId;
    private final Template template;
    private final List<Asset> assets;
    private final SimulationConfig simulation;

    private final List<BufferedImage> images = new ArrayList<>();
    private Path workFile;
    private String resultPath;
    private String resultUrl;

    public TaskContext(long taskId, long userId, Template template, List<Asset> assets, SimulationConfig simulation) {
        this.taskId = taskId;
        this.userId = userId;
        this.template = template;
        this.assets = List.copyOf(assets);
        this.simulation = simulation;
    }

    public long taskId() {
        return taskId;
    }

    public long userId() {
        return userId;
    }

    public Template template() {
        return template;
    }

    public List<Asset> assets() {
        return assets;
    }

    public SimulationConfig simulation() {
        return simulation;
    }

    public List<BufferedImage> images() {
        return images;
    }

    public Path workFile() {
        return workFile;
    }

    public void setWorkFile(Path workFile) {
        this.workFile = workFile;
    }

    public String resultPath() {
        return resultPath;
    }

    public String resultUrl() {
        return resultUrl;
    }

    public void setResult(String resultPath, String resultUrl) {
        this.resultPath = resultPath;
        this.resultUrl = resultUrl;
    }
}
