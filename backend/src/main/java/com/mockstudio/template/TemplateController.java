package com.mockstudio.template;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TemplateController {

    private final TemplateService service;

    public TemplateController(TemplateService service) {
        this.service = service;
    }

    public record CategoryDto(String id, String name) {
    }

    public record TemplateDto(String id, String title, String categoryId, String categoryName, String coverUrl,
                              String description, int requiredImageCount, List<String> acceptedMimeTypes,
                              long maxFileSizeBytes) {
        static TemplateDto of(Template t) {
            return new TemplateDto(t.getId(), t.getTitle(), t.getCategoryId(), t.getCategoryName(), t.getCoverUrl(),
                    t.getDescription(), t.getRequiredImageCount(), t.acceptedMimeTypeList(), t.getMaxFileSizeBytes());
        }
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return service.categories().stream().map(c -> new CategoryDto(c.getId(), c.getName())).toList();
    }

    @GetMapping("/templates")
    public List<TemplateDto> templates(@RequestParam(name = "category", required = false) String category) {
        return service.list(category).stream().map(TemplateDto::of).toList();
    }

    @GetMapping("/templates/{id}")
    public TemplateDto template(@PathVariable("id") String id) {
        return TemplateDto.of(service.require(id));
    }
}
