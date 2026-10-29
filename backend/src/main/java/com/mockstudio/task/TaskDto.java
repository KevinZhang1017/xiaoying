package com.mockstudio.task;

import java.time.Instant;
import java.util.List;

public record TaskDto(
        long id,
        String templateId,
        String templateTitle,
        TaskStatus status,
        TaskStage stage,
        String errorCode,
        String errorMessage,
        String resultUrl,
        List<InputDto> inputs,
        List<StepDto> steps,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Instant updatedAt
) {
    public record InputDto(int slotIndex, long assetId, String url) {
    }

    public record StepDto(TaskStage stage, String status, String message, Instant startedAt, Instant endedAt) {
    }

    public record Page(List<TaskDto> items, Long nextCursor) {
    }
}
