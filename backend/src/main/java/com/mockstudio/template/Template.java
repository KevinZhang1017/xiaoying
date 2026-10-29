package com.mockstudio.template;

import java.util.Arrays;
import java.util.List;

public class Template {
    private String id;
    private String title;
    private String categoryId;
    private String categoryName;
    private String coverUrl;
    private String description;
    private int requiredImageCount;
    private String acceptedMimeTypes;
    private long maxFileSizeBytes;
    private int sortOrder;

    public List<String> acceptedMimeTypeList() {
        if (acceptedMimeTypes == null || acceptedMimeTypes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(acceptedMimeTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean accepts(String mimeType) {
        return acceptedMimeTypeList().contains(mimeType);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getRequiredImageCount() {
        return requiredImageCount;
    }

    public void setRequiredImageCount(int requiredImageCount) {
        this.requiredImageCount = requiredImageCount;
    }

    public String getAcceptedMimeTypes() {
        return acceptedMimeTypes;
    }

    public void setAcceptedMimeTypes(String acceptedMimeTypes) {
        this.acceptedMimeTypes = acceptedMimeTypes;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
