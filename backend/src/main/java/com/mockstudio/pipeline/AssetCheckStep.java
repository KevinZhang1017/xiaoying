package com.mockstudio.pipeline;

import com.mockstudio.asset.Asset;
import com.mockstudio.asset.AssetService;
import com.mockstudio.storage.ImageTypes;
import com.mockstudio.storage.StorageService;
import com.mockstudio.task.TaskStage;
import com.mockstudio.template.Template;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** 第 1 步：从磁盘重新检查素材（存在、数量、大小、真实类型、可解码），并把解码后的图片交给下一步。 */
@Component
public class AssetCheckStep implements TaskStep {

    private final StorageService storage;

    public AssetCheckStep(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public TaskStage stage() {
        return TaskStage.ASSET_CHECK;
    }

    @Override
    public void execute(TaskContext ctx) throws Exception {
        Template t = ctx.template();
        if (ctx.assets().size() != t.getRequiredImageCount()) {
            throw new StepFailure("IMAGE_COUNT_MISMATCH",
                    "素材数量不符：需要 " + t.getRequiredImageCount() + " 张，实际 " + ctx.assets().size() + " 张");
        }
        ctx.images().clear();
        int index = 0;
        for (Asset asset : ctx.assets()) {
            index++;
            Path file = storage.resolve(asset.getStoragePath());
            if (!Files.isRegularFile(file)) {
                throw new StepFailure("ASSET_MISSING", "第 " + index + " 张素材文件不存在或已被删除");
            }
            long size = Files.size(file);
            if (size != asset.getSizeBytes()) {
                throw new StepFailure("ASSET_CORRUPTED", "第 " + index + " 张素材文件已损坏");
            }
            if (size > t.getMaxFileSizeBytes()) {
                throw new StepFailure("FILE_TOO_LARGE",
                        "第 " + index + " 张素材超过 " + AssetService.formatSize(t.getMaxFileSizeBytes()));
            }
            String mime = ImageTypes.detect(file);
            if (mime == null || !t.accepts(mime)) {
                throw new StepFailure("UNSUPPORTED_MIME", "第 " + index + " 张素材格式不被该模板接受");
            }
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) {
                throw new StepFailure("ASSET_UNREADABLE", "第 " + index + " 张图片无法解码");
            }
            ctx.images().add(img);
        }
    }
}
