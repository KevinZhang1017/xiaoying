package com.mockstudio.template;

import com.mockstudio.common.ApiException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemplateService {

    private final TemplateMapper mapper;

    public TemplateService(TemplateMapper mapper) {
        this.mapper = mapper;
    }

    public List<Category> categories() {
        return mapper.findCategories();
    }

    public List<Template> list(String categoryId) {
        return mapper.findAll(categoryId);
    }

    public Template require(String id) {
        Template t = id == null ? null : mapper.findById(id);
        if (t == null) {
            throw ApiException.notFound("TEMPLATE_NOT_FOUND", "模板不存在或已下线");
        }
        return t;
    }
}
