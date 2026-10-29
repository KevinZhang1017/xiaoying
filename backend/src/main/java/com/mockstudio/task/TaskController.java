package com.mockstudio.task;

import com.mockstudio.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    public record CreateTaskRequest(@NotBlank(message = "请选择模板") String templateId,
                                    @NotNull(message = "请上传图片") List<Long> assetIds) {
    }

    /** 受理后立即返回 202 与任务信息（status=PENDING），不等待制作完成。 */
    @PostMapping
    public ResponseEntity<TaskDto> create(AuthUser user, @Valid @RequestBody CreateTaskRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.create(user, req.templateId(), req.assetIds()));
    }

    /**
     * 个人历史：
     * - ids=1,2,3 批量查询（前端轮询未结束任务）；
     * - 否则按 templateId 过滤并以 before（上一页最后一个 ID）分页。
     */
    @GetMapping
    public TaskDto.Page list(AuthUser user,
                             @RequestParam(name = "ids", required = false) List<Long> ids,
                             @RequestParam(name = "templateId", required = false) String templateId,
                             @RequestParam(name = "before", required = false) Long before,
                             @RequestParam(name = "limit", required = false) Integer limit) {
        if (ids != null) {
            return service.byIds(user, ids);
        }
        return service.list(user, templateId, before, limit);
    }

    @GetMapping("/{id}")
    public TaskDto get(AuthUser user, @PathVariable("id") long id) {
        return service.get(user, id);
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<FileSystemResource> result(AuthUser user, @PathVariable("id") long id) {
        Path file = service.resultFile(user, id);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(new FileSystemResource(file));
    }
}
