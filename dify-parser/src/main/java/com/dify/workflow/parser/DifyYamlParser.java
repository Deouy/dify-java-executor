package com.dify.workflow.parser;

import com.dify.workflow.model.DifyDslModel;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Parser for Dify YAML workflow definitions.
 * Uses SnakeYAML for YAML parsing and FastJSON2 for JSON serialization.
 */
public class DifyYamlParser {

    private static final Logger log = LoggerFactory.getLogger(DifyYamlParser.class);
    private final Yaml yaml;

    public DifyYamlParser() {
        this.yaml = new Yaml();
    }

    /**
     * Parse Dify YAML from a string.
     *
     * @param yamlContent YAML content as string
     * @return Parsed DifyDslModel
     * @throws DifyParseException if parsing fails
     */
    public DifyDslModel parse(String yamlContent) throws DifyParseException {
        try {
            log.debug("Parsing Dify YAML content");
            Map<String, Object> yamlMap = yaml.load(yamlContent);
            String jsonStr = JSON.toJSONString(yamlMap);
            DifyDslModel model = JSON.parseObject(jsonStr, DifyDslModel.class);
            mergeAdditionalProperties(model, jsonStr);
            validate(model);
            return model;
        } catch (Exception e) {
            throw new DifyParseException("Failed to parse Dify YAML", e);
        }
    }

    /**
     * Parse Dify YAML from a file.
     *
     * @param filePath Path to YAML file
     * @return Parsed DifyDslModel
     * @throws DifyParseException if parsing fails
     */
    public DifyDslModel parse(Path filePath) throws DifyParseException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            log.debug("Parsing Dify YAML from file: {}", filePath);
            Map<String, Object> yamlMap = yaml.load(inputStream);
            String jsonStr = JSON.toJSONString(yamlMap);
            DifyDslModel model = JSON.parseObject(jsonStr, DifyDslModel.class);
            mergeAdditionalProperties(model, jsonStr);
            validate(model);
            return model;
        } catch (IOException e) {
            throw new DifyParseException("Failed to parse Dify YAML file: " + filePath, e);
        }
    }

    /**
     * Parse Dify YAML from an input stream.
     *
     * @param inputStream Input stream containing YAML content
     * @return Parsed DifyDslModel
     * @throws DifyParseException if parsing fails
     */
    public DifyDslModel parse(InputStream inputStream) throws DifyParseException {
        try {
            log.debug("Parsing Dify YAML from input stream");
            Map<String, Object> yamlMap = yaml.load(inputStream);
            String jsonStr = JSON.toJSONString(yamlMap);
            DifyDslModel model = JSON.parseObject(jsonStr, DifyDslModel.class);
            mergeAdditionalProperties(model, jsonStr);
            validate(model);
            return model;
        } catch (Exception e) {
            throw new DifyParseException("Failed to parse Dify YAML from input stream", e);
        }
    }

    /**
     * 回填每个节点的 additionalProperties。
     * FastJSON2 默认丢弃未匹配字段，所以 structured_output_enabled 等顶层兄弟字段不会进入 DifyNodeData。
     * 这里从原始 JSON 中把每个 node.data 的所有 key 重新过一遍，把未在白名单中的 key 填到 additionalProperties。
     */
    private void mergeAdditionalProperties(DifyDslModel model, String jsonStr) {
        if (model == null || model.workflow() == null
                || model.workflow().graph() == null
                || model.workflow().graph().nodes() == null) {
            return;
        }
        try {
            com.alibaba.fastjson2.JSONObject root = com.alibaba.fastjson2.JSON.parseObject(jsonStr);
            com.alibaba.fastjson2.JSONObject workflow = root.getJSONObject("workflow");
            if (workflow == null) return;
            com.alibaba.fastjson2.JSONObject graph = workflow.getJSONObject("graph");
            if (graph == null) return;
            com.alibaba.fastjson2.JSONArray nodesArr = graph.getJSONArray("nodes");
            if (nodesArr == null) return;
            java.util.Map<String, com.alibaba.fastjson2.JSONObject> nodeDataById = new java.util.HashMap<>();
            for (int i = 0; i < nodesArr.size(); i++) {
                com.alibaba.fastjson2.JSONObject node = nodesArr.getJSONObject(i);
                if (node == null) continue;
                String id = node.getString("id");
                com.alibaba.fastjson2.JSONObject data = node.getJSONObject("data");
                if (id != null && data != null) {
                    nodeDataById.put(id, data);
                }
            }
            for (com.dify.workflow.model.DifyNode node : model.workflow().graph().nodes()) {
                com.alibaba.fastjson2.JSONObject rawData = nodeDataById.get(node.id());
                if (rawData != null && node.data() != null) {
                    node.data().mergeAdditionalProperties(rawData);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to merge additionalProperties: {}", e.getMessage());
        }
    }

    /**
     * Validate the parsed model.
     */
    private void validate(DifyDslModel model) throws DifyParseException {
        if (model == null) {
            throw new DifyParseException("Parsed model is null");
        }
        if (!"app".equals(model.kind())) {
            throw new DifyParseException("Invalid kind: expected 'app', got '" + model.kind() + "'");
        }
        if (model.workflow() == null || model.workflow().graph() == null) {
            throw new DifyParseException("Missing workflow graph definition");
        }
        if (model.workflow().graph().nodes() == null || model.workflow().graph().nodes().isEmpty()) {
            throw new DifyParseException("Workflow graph has no nodes");
        }
    }

    /**
     * 从 YAML 内容中提取 app.name。
     * 用于子工作流注册时自动提取 key。
     *
     * @param yamlContent YAML 内容
     * @return app.name 值
     * @throws DifyParseException 如果提取失败
     */
    public String extractAppName(String yamlContent) throws DifyParseException {
        try {
            Map<String, Object> yamlMap = yaml.load(yamlContent);
            @SuppressWarnings("unchecked")
            Map<String, Object> appMap = (Map<String, Object>) yamlMap.get("app");
            if (appMap == null) {
                throw new DifyParseException("Missing 'app' section in YAML");
            }
            Object name = appMap.get("name");
            if (name == null) {
                throw new DifyParseException("Missing 'app.name' in YAML");
            }
            return name.toString();
        } catch (DifyParseException e) {
            throw e;
        } catch (Exception e) {
            throw new DifyParseException("Failed to extract app.name from YAML", e);
        }
    }

    /**
     * Serialize a DifyDslModel to YAML string.
     *
     * @param model The model to serialize
     * @return YAML string
     */
    public String toYaml(DifyDslModel model) throws DifyParseException {
        try {
            return JSON.toJSONString(model, JSONWriter.Feature.PrettyFormat);
        } catch (Exception e) {
            throw new DifyParseException("Failed to serialize to YAML", e);
        }
    }
}
