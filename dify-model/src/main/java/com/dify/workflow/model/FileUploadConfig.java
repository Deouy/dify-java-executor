package com.dify.workflow.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 文件上传配置，对应 YAML 中 features.file_upload。
 */
public class FileUploadConfig {

    private final boolean enabled;
    private final List<String> allowedFileExtensions;
    private final List<String> allowedFileTypes;
    private final List<String> allowedFileUploadMethods;
    private final int fileSizeLimit;
    private final int workflowFileUploadLimit;
    private final int imageFileSizeLimit;
    private final int numberLimits;

    private FileUploadConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.allowedFileExtensions = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(builder.allowedFileExtensions));
        this.allowedFileTypes = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(builder.allowedFileTypes));
        this.allowedFileUploadMethods = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(builder.allowedFileUploadMethods));
        this.fileSizeLimit = builder.fileSizeLimit;
        this.workflowFileUploadLimit = builder.workflowFileUploadLimit;
        this.imageFileSizeLimit = builder.imageFileSizeLimit;
        this.numberLimits = builder.numberLimits;
    }

    /**
     * 从 YAML 的 features.file_upload Map 解析配置。
     */
    @SuppressWarnings("unchecked")
    public static FileUploadConfig fromFeatures(Map<String, Object> features) {
        if (features == null) {
            return FileUploadConfig.disabled();
        }

        Object fileUploadObj = features.get("file_upload");
        if (!(fileUploadObj instanceof Map)) {
            return FileUploadConfig.disabled();
        }

        Map<String, Object> fu = (Map<String, Object>) fileUploadObj;
        Boolean enabled = (Boolean) fu.getOrDefault("enabled", false);
        if (enabled == null || !enabled) {
            return FileUploadConfig.disabled();
        }

        Builder builder = new Builder().enabled(true);

        // 允许的文件扩展名
        Object exts = fu.get("allowed_file_extensions");
        if (exts instanceof List) {
            ((List<String>) exts).forEach(builder::addAllowedExtension);
        }

        // 允许的文件类型
        Object types = fu.get("allowed_file_types");
        if (types instanceof List) {
            ((List<String>) types).forEach(builder::addAllowedType);
        }

        // 允许的上传方式
        Object methods = fu.get("allowed_file_upload_methods");
        if (methods instanceof List) {
            ((List<String>) methods).forEach(builder::addAllowedUploadMethod);
        }

        // 文件大小配置
        Object fileUploadCfg = fu.get("fileUploadConfig");
        if (fileUploadCfg instanceof Map) {
            Map<String, Object> cfg = (Map<String, Object>) fileUploadCfg;
            builder.fileSizeLimit(intVal(cfg, "file_size_limit", 15));
            builder.workflowFileUploadLimit(intVal(cfg, "workflow_file_upload_limit", 10));
            builder.imageFileSizeLimit(intVal(cfg, "image_file_size_limit", 10));
        }

        // 上传数量限制
        builder.numberLimits(intVal(fu, "number_limits", 3));

        return builder.build();
    }

    public static FileUploadConfig disabled() {
        return new Builder().enabled(false).build();
    }

    private static int intVal(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return defaultValue;
    }

    // -- getters --

    public boolean isEnabled() { return enabled; }
    public List<String> getAllowedFileExtensions() { return allowedFileExtensions; }
    public List<String> getAllowedFileTypes() { return allowedFileTypes; }
    public List<String> getAllowedFileUploadMethods() { return allowedFileUploadMethods; }
    public int getFileSizeLimit() { return fileSizeLimit; }
    public int getWorkflowFileUploadLimit() { return workflowFileUploadLimit; }
    public int getImageFileSizeLimit() { return imageFileSizeLimit; }
    public int getNumberLimits() { return numberLimits; }

    /**
     * 验证文件扩展名是否允许。
     */
    public boolean isExtensionAllowed(String filename) {
        if (!enabled || allowedFileExtensions.isEmpty()) {
            return true; // 未配置则允许所有
        }
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return allowedFileExtensions.stream().anyMatch(ext -> lower.endsWith(ext.toLowerCase()));
    }

    /**
     * 验证上传方式是否允许。
     */
    public boolean isUploadMethodAllowed(String method) {
        if (!enabled || allowedFileUploadMethods.isEmpty()) {
            return true;
        }
        return allowedFileUploadMethods.contains(method);
    }

    public static class Builder {
        private boolean enabled = false;
        private List<String> allowedFileExtensions = Collections.emptyList();
        private List<String> allowedFileTypes = Collections.emptyList();
        private List<String> allowedFileUploadMethods = Collections.emptyList();
        private int fileSizeLimit = 15;
        private int workflowFileUploadLimit = 10;
        private int imageFileSizeLimit = 10;
        private int numberLimits = 3;

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder addAllowedExtension(String ext) {
            if (allowedFileExtensions.isEmpty()) allowedFileExtensions = new java.util.ArrayList<>();
            allowedFileExtensions.add(ext);
            return this;
        }
        public Builder addAllowedType(String type) {
            if (allowedFileTypes.isEmpty()) allowedFileTypes = new java.util.ArrayList<>();
            allowedFileTypes.add(type);
            return this;
        }
        public Builder addAllowedUploadMethod(String method) {
            if (allowedFileUploadMethods.isEmpty()) allowedFileUploadMethods = new java.util.ArrayList<>();
            allowedFileUploadMethods.add(method);
            return this;
        }
        public Builder fileSizeLimit(int val) { this.fileSizeLimit = val; return this; }
        public Builder workflowFileUploadLimit(int val) { this.workflowFileUploadLimit = val; return this; }
        public Builder imageFileSizeLimit(int val) { this.imageFileSizeLimit = val; return this; }
        public Builder numberLimits(int val) { this.numberLimits = val; return this; }
        public FileUploadConfig build() { return new FileUploadConfig(this); }
    }
}
