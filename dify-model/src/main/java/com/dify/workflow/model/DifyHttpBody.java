package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dify HTTP request body configuration.
 *
 * <p>data 字段类型为 Object,真实 Dify 导出下形态多样:
 * <ul>
 *   <li>json / text / binary:String</li>
 *   <li>form-data / x-www-form-urlencoded:List&lt;Map&gt;,每项含 id / key / type / value</li>
 *   <li>none:空 List `[]`</li>
 * </ul>
 * 消费侧通过 {@link #dataAsString()} 或 {@link #formDataItems()} 访问对应形态。
 */
public final class DifyHttpBody {
    @JSONField(name = "type")
    private final String type;
    @JSONField(name = "data")
    private final Object data;
    @JSONField(name = "form_data")
    private final Map<String, String> formData;
    @JSONField(name = "binary")
    private final DifyHttpBinary binary;

    public DifyHttpBody(String type, Object data, Map<String, String> formData, DifyHttpBinary binary) {
        this.type = type;
        this.data = data;
        this.formData = formData;
        this.binary = binary;
    }

    public String type() { return type; }
    public Object data() { return data; }
    public Map<String, String> formData() { return formData; }
    public DifyHttpBinary binary() { return binary; }

    /**
     * 当 body.type 为 form-data / x-www-form-urlencoded 时,data 是 List<Map> 形式,
     * 每项含 id(忽略) / key / type(目前只支持 text) / value(可能含 {{#var#}})。
     * data 不是 List 时返回 null。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> formDataItems() {
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        return null;
    }

    /**
     * 当 body.type 为 json / text / binary 时,data 是 String。data 不是 String 时返回 null。
     */
    public String dataAsString() {
        return data instanceof String ? (String) data : null;
    }

    public String getType() { return type; }
    public Object getData() { return data; }
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
