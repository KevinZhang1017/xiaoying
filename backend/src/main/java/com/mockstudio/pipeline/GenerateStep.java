package com.mockstudio.pipeline;

import com.mockstudio.storage.StorageService;
import com.mockstudio.task.TaskStage;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** 第 2 步：模拟生成。把用户素材按模板拼成一张图，写入临时工作目录（不调用任何 AI 服务）。 */
@Component
public class GenerateStep implements TaskStep {

    private final StorageService storage;

    public GenerateStep(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public TaskStage stage() {
        return TaskStage.GENERATE;
    }

    @Override
    public void execute(TaskContext ctx) throws Exception {
        if (ctx.images().isEmpty()) {
            throw new StepFailure("NO_INPUT", "没有可用于生成的素材");
        }
        BufferedImage out = ImageComposer.compose(ctx.images(), ctx.template().getId());
        Path work = storage.workFile(ctx.taskId());
        Files.createDirectories(work.getParent());
        if (!ImageIO.write(out, "jpg", work.toFile())) {
            throw new StepFailure("GENERATE_FAILED", "生成结果写入失败");
        }
        ctx.setWorkFile(work);
    }
}
