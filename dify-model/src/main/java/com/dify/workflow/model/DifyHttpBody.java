package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Map;
import java.util.Objects;

/**
 * Dify HTTP request body configuration.
 */
public final class DifyHttpBody {
    @JSONField(name = "type")
    private final String type;
    @JSONField(name = "data")
    private final String data;
    @JSONField(name = "form_data")
    private final Map<String, String> formData;
    @JSONField(name = "binary")
    private final DifyHttpBinary binary;

    public DifyHttpBody(String type, String data, Map<String, String> formData, DifyHttpBinary binary) {
        this.type = type;
        this.data = data;
        this.formData = formData;
        this.binary = binary;
    }

    public String type() { return type; }
    public String data() { return data; }
    public Map<String, String> formData() { return formData; }
    public DifyHttpBinary binary() { return binary; }

    public String getType() { return type; }
    public String getData() { return data; }
    public Map<String, String> getFormData() { return formData; }
    public DifyHttpBinary getBinary() { return binary; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyHttpBody)) return false;
        DifyHttpBody that = (DifyHttpBody) o;
        return Objects.equals(type, that.type)
                && Objects.equals(data, that.data)
                && Objects.equals(formData, that.formData)
                && Objects.equals(binary, that.binary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, data, formData, binary);
    }

    @Override
    public String toString() {
        return String.format("DifyHttpBody[type=%s, data=%s, formData=%s, binary=%s]", type, data, formData, binary);
    }

    /**
     * Dify HTTP binary file.
     */
    public static final class DifyHttpBinary {
        @JSONField(name = "file")
        private final String file;

        public DifyHttpBinary(String file) {
            this.file = file;
        }

        public String file() { return file; }
        public String getFile() { return file; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DifyHttpBinary)) return false;
            DifyHttpBinary that = (DifyHttpBinary) o;
            return Objects.equals(file, that.file);
        }

        @Override
        public int hashCode() { return Objects.hash(file); }

        @Override
        public String toString() { return String.format("DifyHttpBinary[file=%s]", file); }
    }
}
