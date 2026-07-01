package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * Swagger/OpenAPI文档解析器
 * 支持JSON和YAML格式
 * 支持从URL和本地文件读取
 */
@Slf4j
public class SwaggerParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Yaml yaml = new Yaml();

    /**
     * 解析Swagger文档（支持URL和本地文件）
     */
    public static List<String> parseSwagger(String input) {
        List<String> interfaces = new ArrayList<>();

        try {
            JsonNode root;

            // 判断是URL还是本地文件
            if (input.startsWith("http://") || input.startsWith("https://")) {
                // 从URL读取
                root = parseFromUrl(input);
            } else {
                // 从本地文件读取
                root = parseFromFile(input);
            }

            // 提取接口定义
            interfaces = extractInterfaces(root);

        } catch (Exception e) {
            log.error("解析Swagger文档失败: {}", e.getMessage(), e);
        }

        return interfaces;
    }

    /**
     * 从URL解析Swagger文档
     */
    private static JsonNode parseFromUrl(String urlStr) throws Exception {
        log.info("正在从URL读取Swagger文档: {}", urlStr);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        try (InputStream is = conn.getInputStream()) {
            String contentType = conn.getContentType();

            // 判断是JSON还是YAML
            if (contentType != null && contentType.contains("yaml")) {
                Map<String, Object> map = yaml.load(is);
                return objectMapper.convertValue(map, JsonNode.class);
            } else {
                return objectMapper.readTree(is);
            }
        }
    }

    /**
     * 从本地文件解析Swagger文档
     */
    private static JsonNode parseFromFile(String filePath) throws Exception {
        log.info("正在从文件读取Swagger文档: {}", filePath);

        File file = new File(filePath);
        try (InputStream is = new FileInputStream(file)) {
            // 根据文件扩展名判断格式
            if (filePath.endsWith(".yaml") || filePath.endsWith(".yml")) {
                Map<String, Object> map = yaml.load(is);
                return objectMapper.convertValue(map, JsonNode.class);
            } else {
                return objectMapper.readTree(is);
            }
        }
    }

    /**
     * 从Swagger文档中提取接口定义
     */
    private static List<String> extractInterfaces(JsonNode root) {
        List<String> interfaces = new ArrayList<>();

        // 获取basePath
        String basePath = "";
        if (root.has("basePath")) {
            basePath = root.get("basePath").asText();
        }

        // 获取servers
        String serverUrl = "";
        if (root.has("servers")) {
            JsonNode servers = root.get("servers");
            if (servers.isArray() && servers.size() > 0) {
                serverUrl = servers.get(0).get("url").asText();
            }
        }

        // 获取components/schemas（用于解析$ref）
        JsonNode components = null;
        if (root.has("components") && root.get("components").has("schemas")) {
            components = root.get("components").get("schemas");
        } else if (root.has("definitions")) {
            // Swagger 2.0格式
            components = root.get("definitions");
        }

        // 获取paths
        JsonNode paths = root.get("paths");
        if (paths == null || !paths.isObject()) {
            log.warn("未找到paths节点");
            return interfaces;
        }

        // 遍历所有路径
        Iterator<String> pathIterator = paths.fieldNames();
        while (pathIterator.hasNext()) {
            String path = pathIterator.next();
            JsonNode pathNode = paths.get(path);

            // 遍历所有HTTP方法
            Iterator<String> methodIterator = pathNode.fieldNames();
            while (methodIterator.hasNext()) {
                String method = methodIterator.next().toUpperCase();
                if (!isValidMethod(method)) continue;

                JsonNode methodNode = pathNode.get(method.toLowerCase());

                // 获取接口信息
                String interfaceDef = buildInterfaceDefinition(basePath, serverUrl, path, method, methodNode, components);
                interfaces.add(interfaceDef);
            }
        }

        log.info("从Swagger文档中提取到 {} 个接口", interfaces.size());
        return interfaces;
    }

    /**
     * 判断是否是有效的HTTP方法
     */
    private static boolean isValidMethod(String method) {
        return Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD", "TRACE").contains(method);
    }

    /**
     * 构建接口定义字符串
     */
    private static String buildInterfaceDefinition(String basePath, String serverUrl,
                                                   String path, String method, JsonNode methodNode, JsonNode components) {
        StringBuilder sb = new StringBuilder();

        // 方法和路径
        String fullPath = basePath + path;
        sb.append(method).append(" ").append(fullPath).append("\n");

        // 接口名称
        if (methodNode.has("summary")) {
            sb.append("接口名称：").append(methodNode.get("summary").asText()).append("\n");
        }

        // 接口描述
        if (methodNode.has("description")) {
            sb.append("接口描述：").append(methodNode.get("description").asText()).append("\n");
        }

        // 提取参数
        List<Parameter> parameters = extractParameters(methodNode);
        if (!parameters.isEmpty()) {
            sb.append("参数：\n");
            for (Parameter param : parameters) {
                sb.append("  ").append(param.name).append(": ").append(param.type).append(": ")
                        .append(param.required ? "必填" : "可选");
                if (param.description != null && !param.description.isEmpty()) {
                    sb.append(", ").append(param.description);
                }
                if (param.example != null && !param.example.isEmpty()) {
                    sb.append(", 示例:").append(param.example);
                }
                sb.append("\n");
            }
        }

        // 提取请求体
        JsonNode requestBody = methodNode.get("requestBody");
        if (requestBody != null && requestBody.has("content")) {
            sb.append("请求体：\n");
            JsonNode content = requestBody.get("content");
            Iterator<String> contentTypeIterator = content.fieldNames();
            while (contentTypeIterator.hasNext()) {
                String contentType = contentTypeIterator.next();
                sb.append("  Content-Type: ").append(contentType).append("\n");

                JsonNode schema = content.get(contentType).get("schema");
                if (schema != null) {
                    List<Parameter> bodyParams = extractSchemaProperties(schema, components);
                    for (Parameter param : bodyParams) {
                        sb.append("    ").append(param.name).append(": ").append(param.type)
                                .append(param.required ? "(必填)" : "(可选)");
                        if (param.description != null) {
                            sb.append(", ").append(param.description);
                        }
                        sb.append("\n");
                    }
                }
            }
        }

        // 提取响应
        JsonNode responses = methodNode.get("responses");
        if (responses != null) {
            sb.append("响应：\n");
            Iterator<String> responseIterator = responses.fieldNames();
            while (responseIterator.hasNext()) {
                String statusCode = responseIterator.next();
                JsonNode responseNode = responses.get(statusCode);
                sb.append("  ").append(statusCode).append("：");
                if (responseNode.has("description")) {
                    sb.append(responseNode.get("description").asText());
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 提取参数列表
     */
    private static List<Parameter> extractParameters(JsonNode methodNode) {
        List<Parameter> parameters = new ArrayList<>();

        if (!methodNode.has("parameters")) {
            return parameters;
        }

        JsonNode paramsNode = methodNode.get("parameters");
        if (!paramsNode.isArray()) {
            return parameters;
        }

        for (JsonNode paramNode : paramsNode) {
            Parameter param = new Parameter();
            param.name = paramNode.get("name").asText();
            param.in = paramNode.get("in").asText();
            param.required = paramNode.has("required") && paramNode.get("required").asBoolean();

            // 获取类型
            if (paramNode.has("schema")) {
                param.type = extractTypeFromSchema(paramNode.get("schema"));
            } else if (paramNode.has("type")) {
                param.type = paramNode.get("type").asText();
            }

            // 获取描述
            if (paramNode.has("description")) {
                param.description = paramNode.get("description").asText();
            }

            // 获取示例
            if (paramNode.has("example")) {
                param.example = paramNode.get("example").asText();
            }

            parameters.add(param);
        }

        return parameters;
    }

    /**
     * 从schema中提取属性（支持allOf/anyOf/oneOf递归解析）
     */
    private static List<Parameter> extractSchemaProperties(JsonNode schema, JsonNode components) {
        return extractSchemaProperties(schema, components, 0);
    }

    /**
     * 从schema中提取属性（支持allOf/anyOf/oneOf递归解析，带深度限制）
     */
    private static List<Parameter> extractSchemaProperties(JsonNode schema, JsonNode components, int depth) {
        List<Parameter> parameters = new ArrayList<>();

        if (schema == null || depth >= 5) {
            return parameters;
        }

        // 处理$ref引用
        if (schema.has("$ref")) {
            JsonNode resolved = resolveRef(schema.get("$ref").asText(), components);
            if (resolved != null) {
                parameters.addAll(extractSchemaProperties(resolved, components, depth + 1));
            }
            return parameters;
        }

        // 处理allOf：合并所有子schema的属性
        if (schema.has("allOf") && schema.get("allOf").isArray()) {
            for (JsonNode subSchema : schema.get("allOf")) {
                parameters.addAll(extractSchemaProperties(subSchema, components, depth + 1));
            }
            return parameters;
        }

        // 处理anyOf：取第一个有属性的子schema（简化处理）
        if (schema.has("anyOf") && schema.get("anyOf").isArray()) {
            for (JsonNode subSchema : schema.get("anyOf")) {
                List<Parameter> subParams = extractSchemaProperties(subSchema, components, depth + 1);
                if (!subParams.isEmpty()) {
                    parameters.addAll(subParams);
                    break;
                }
            }
            return parameters;
        }

        // 处理oneOf：同anyOf，取第一个有属性的
        if (schema.has("oneOf") && schema.get("oneOf").isArray()) {
            for (JsonNode subSchema : schema.get("oneOf")) {
                List<Parameter> subParams = extractSchemaProperties(subSchema, components, depth + 1);
                if (!subParams.isEmpty()) {
                    parameters.addAll(subParams);
                    break;
                }
            }
            return parameters;
        }

        // 处理普通properties
        if (!schema.has("properties")) {
            return parameters;
        }

        JsonNode properties = schema.get("properties");
        Iterator<String> propIterator = properties.fieldNames();

        // 获取必填字段
        Set<String> requiredFields = new HashSet<>();
        if (schema.has("required") && schema.get("required").isArray()) {
            for (JsonNode req : schema.get("required")) {
                requiredFields.add(req.asText());
            }
        }

        while (propIterator.hasNext()) {
            String name = propIterator.next();
            JsonNode propNode = properties.get(name);

            Parameter param = new Parameter();
            param.name = name;
            param.required = requiredFields.contains(name);
            param.type = extractTypeFromSchema(propNode, components, depth + 1);

            if (propNode.has("description")) {
                param.description = propNode.get("description").asText();
            }

            if (propNode.has("example")) {
                param.example = propNode.get("example").asText();
            }

            parameters.add(param);
        }

        return parameters;
    }

    /**
     * 解析$ref引用，从components/schemas中查找定义
     */
    private static JsonNode resolveRef(String ref, JsonNode components) {
        if (components == null || ref == null || !ref.startsWith("#/")) {
            return null;
        }

        try {
            // 解析路径：#/components/schemas/User 或 #/definitions/User
            String[] parts = ref.split("/");
            JsonNode current = components;

            // 跳过第一个空字符串和#号，从components或definitions开始
            for (int i = 2; i < parts.length; i++) {
                if (current == null) return null;
                current = current.get(parts[i]);
            }

            return current;
        } catch (Exception e) {
            log.warn("解析$ref失败: {} - {}", ref, e.getMessage());
            return null;
        }
    }

    /**
     * 从schema中提取类型
     */
    private static String extractTypeFromSchema(JsonNode schema) {
        return extractTypeFromSchema(schema, null, 0);
    }

    /**
     * 从schema中提取类型（带深度限制）
     */
    private static String extractTypeFromSchema(JsonNode schema, JsonNode components, int depth) {
        if (depth >= 5) {
            return "object";
        }

        if (schema.has("type")) {
            String type = schema.get("type").asText();

            // 处理数组类型
            if ("array".equals(type) && schema.has("items")) {
                String itemType = extractTypeFromSchema(schema.get("items"), components, depth + 1);
                return "数组<" + itemType + ">";
            }

            // 处理引用类型
            if (schema.has("$ref")) {
                String ref = schema.get("$ref").asText();
                return "引用(" + ref.substring(ref.lastIndexOf("/") + 1) + ")";
            }

            // 处理枚举类型
            if (schema.has("enum") && schema.get("enum").isArray()) {
                StringBuilder enumStr = new StringBuilder("枚举(");
                for (JsonNode enumItem : schema.get("enum")) {
                    enumStr.append(enumItem.asText()).append(",");
                }
                enumStr.setLength(enumStr.length() - 1);
                enumStr.append(")");
                return enumStr.toString();
            }

            // 处理格式
            if (schema.has("format")) {
                return type + "(" + schema.get("format").asText() + ")";
            }

            return type;
        }

        // 处理引用
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asText();
            return "引用(" + ref.substring(ref.lastIndexOf("/") + 1) + ")";
        }

        return "object";
    }

    /**
     * 参数类
     */
    static class Parameter {
        String name;
        String in;
        String type;
        boolean required;
        String description;
        String example;
    }
}