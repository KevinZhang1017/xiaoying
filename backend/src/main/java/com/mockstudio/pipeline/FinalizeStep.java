package com.mockstudio.pipeline;

import com.mockstudio.storage.StorageService;
import com.mockstudio.task.TaskStage;
import org.springframework.stereotype.Component;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 第 3 步：结果整理。把临时产物移动到结果目录并生成可预览地址。 */
@Component
public class FinalizeStep implements TaskStep {

    private final StorageService storage;

    public FinalizeStep(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public TaskStage stage() {
        return TaskStage.FINALIZE;
    }

    @Override
    public void execute(TaskContext ctx) throws Exception {
        Path work = ctx.workFile();
        if (work == null || !Files.isRegularFile(work) || Files.size(work) == 0) {
            throw new StepFailure("RESULT_MISSING", "未找到生成产物，无法整理结果");
        }
        Path result = storage.resultFile(ctx.taskId());
        Files.createDirectories(result.getParent());
        try {
            Files.move(work, result, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(work, result, StandardCopyOption.REPLACE_EXISTING);
        }
        ctx.setResult(storage.relativize(result), resultUrl(ctx.taskId()));
    }

    public static String resultUrl(long taskId) {
        return "/api/tasks/" + taskId + "/result";
    }
}
