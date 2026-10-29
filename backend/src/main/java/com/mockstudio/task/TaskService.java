package com.mockstudio.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockstudio.asset.Asset;
import com.mockstudio.asset.AssetController;
import com.mockstudio.asset.AssetMapper;
import com.mockstudio.asset.AssetService;
import com.mockstudio.auth.AuthUser;
import com.mockstudio.common.ApiException;
import com.mockstudio.common.Times;
import com.mockstudio.simulation.SimulationSettings;
import com.mockstudio.storage.StorageService;
import com.mockstudio.template.Template;
import com.mockstudio.template.TemplateService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskService {

    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_BATCH_IDS = 100;

    private final TaskMapper taskMapper;
    private final AssetMapper assetMapper;
    private final TemplateService templateService;
    private final SimulationSettings simulationSettings;
    private final TaskDispatcher dispatcher;
    private final ApplicationEventPublisher events;
    private final StorageService storage;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TaskService(TaskMapper taskMapper, AssetMapper assetMapper, TemplateService templateService,
                       SimulationSettings simulationSettings, TaskDispatcher dispatcher,
                       ApplicationEventPublisher events, StorageService storage, ObjectMapper objectMapper,
                       Clock clock) {
        this.taskMapper = taskMapper;
        this.assetMapper = assetMapper;
        this.templateService = templateService;
        this.simulationSettings = simulationSettings;
        this.dispatcher = dispatcher;
        this.events = events;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 受理：只做参数校验与任务记录创建，随即返回；实际处理在事务提交后由后台线程执行。
     */
    @Transactional
    public TaskDto create(AuthUser user, String templateId, List<Long> assetIds) {
        Template template = templateService.require(templateId);
        List<Long> ids = assetIds == null ? List.of() : assetIds;
        if (ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "素材 ID 不能为空");
        }
        if (ids.size() != template.getRequiredImageCount()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_COUNT_MISMATCH",
                    "该模板需要 " + template.getRequiredImageCount() + " 张图片，当前提交了 " + ids.size() + " 张",
                    Map.of("required", template.getRequiredImageCount(), "actual", ids.size()));
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw ApiException.badRequest("DUPLICATE_ASSET", "同一张图片不能重复提交");
        }
        Map<Long, Asset> owned = assetMapper.findByIdsForUser(user.id(), ids).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));
        for (int i = 0; i < ids.size(); i++) {
            Asset a = owned.get(ids.get(i));
            if (a == null) {
                throw ApiException.badRequest("ASSET_NOT_FOUND", "第 " + (i + 1) + " 张图片不存在，请重新上传");
            }
            if (!template.accepts(a.getMimeType())) {
                throw ApiException.badRequest("UNSUPPORTED_MIME", "第 " + (i + 1) + " 张图片格式不被该模板接受");
            }
            if (a.getSizeBytes() > template.getMaxFileSizeBytes()) {
                throw ApiException.badRequest("FILE_TOO_LARGE",
                        "第 " + (i + 1) + " 张图片超过 " + AssetService.formatSize(template.getMaxFileSizeBytes()));
            }
        }
        if (dispatcher.isSaturated()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_BUSY", "当前排队任务过多，请稍后再试");
        }

        Instant now = Times.now(clock);
        Task task = new Task();
        task.setUserId(user.id());
        task.setTemplateId(template.getId());
        task.setStatus(TaskStatus.PENDING);
        task.setSimConfig(toJson(simulationSettings.current()));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        for (int i = 0; i < ids.size(); i++) {
            taskMapper.insertAssetRef(task.getId(), i, ids.get(i));
        }
        events.publishEvent(new TaskCreatedEvent(task.getId()));
        return toDtos(List.of(taskMapper.findById(task.getId()))).get(0);
    }

    public TaskDto get(AuthUser user, long id) {
        return toDtos(List.of(requireOwned(user, id))).get(0);
    }

    public TaskDto.Page list(AuthUser user, String templateId, Long beforeId, Integer limit) {
        int size = limit == null ? 20 : Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        List<Task> rows = taskMapper.list(user.id(), templateId, beforeId, size + 1);
        Long next = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            next = rows.get(rows.size() - 1).getId();
        }
        return new TaskDto.Page(toDtos(rows), next);
    }

    /** 批量查询（供前端轮询未结束任务）。不属于当前用户的 ID 会被静默忽略。 */
    public TaskDto.Page byIds(AuthUser user, Collection<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>(ids);
        unique.remove(null);
        if (unique.isEmpty()) {
            return new TaskDto.Page(List.of(), null);
        }
        if (unique.size() > MAX_BATCH_IDS) {
            throw ApiException.badRequest("TOO_MANY_IDS", "一次最多查询 " + MAX_BATCH_IDS + " 个任务");
        }
        return new TaskDto.Page(toDtos(taskMapper.findByIdsForUser(user.id(), unique)), null);
    }

    public Path resultFile(AuthUser user, long id) {
        Task task = requireOwned(user, id);
        if (task.getStatus() != TaskStatus.SUCCEEDED || task.getResultPath() == null) {
            throw ApiException.notFound("RESULT_NOT_READY", "结果尚未生成");
        }
        return storage.resolve(task.getResultPath());
    }

    private Task requireOwned(AuthUser user, long id) {
        Task task = taskMapper.findByIdForUser(id, user.id());
        if (task == null) {
            // 不区分「不存在」与「属于其他用户」，避免泄露资源存在性
            throw ApiException.notFound("TASK_NOT_FOUND", "任务不存在");
        }
        return task;
    }

    private List<TaskDto> toDtos(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Long> ids = tasks.stream().map(Task::getId).toList();
        Map<Long, List<TaskDto.InputDto>> inputs = new HashMap<>();
        for (TaskAssetRef ref : taskMapper.findAssetRefs(ids)) {
            inputs.computeIfAbsent(ref.getTaskId(), k -> new ArrayList<>())
                    .add(new TaskDto.InputDto(ref.getSlotIndex(), ref.getAssetId(),
                            AssetController.contentUrl(ref.getAssetId())));
        }
        Map<Long, List<TaskDto.StepDto>> steps = new HashMap<>();
        for (TaskStepLog log : taskMapper.findStepLogs(ids)) {
            steps.computeIfAbsent(log.getTaskId(), k -> new ArrayList<>())
                    .add(new TaskDto.StepDto(log.getStage(), log.getStatus(), log.getMessage(),
                            log.getStartedAt(), log.getEndedAt()));
        }
        return tasks.stream().map(t -> new TaskDto(
                t.getId(), t.getTemplateId(), t.getTemplateTitle(), t.getStatus(), t.getStage(),
                t.getErrorCode(), t.getErrorMessage(), t.getResultUrl(),
                inputs.getOrDefault(t.getId(), List.of()), steps.getOrDefault(t.getId(), List.of()),
                t.getCreatedAt(), t.getStartedAt(), t.getFinishedAt(), t.getUpdatedAt()
        )).toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
