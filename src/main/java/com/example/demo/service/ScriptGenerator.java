package com.example.demo.service;

import com.example.demo.TestCase;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 自动化测试脚本生成器
 * 职责：根据测试用例生成 TestNG + RestAssured + Allure 可运行的测试脚本
 */
@Slf4j
public class ScriptGenerator {

    private static final String POM_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
            
                <groupId>com.example</groupId>
                <artifactId>api-automation</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>
            
                <name>API Automation Tests</name>
                <description>自动生成的接口自动化测试脚本</description>
            
                <properties>
                    <maven.compiler.source>17</maven.compiler.source>
                    <maven.compiler.target>17</maven.compiler.target>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <testng.version>7.10.2</testng.version>
                    <rest-assured.version>5.4.0</rest-assured.version>
                    <allure.version>2.29.0</allure.version>
                </properties>
            
                <dependencies>
                    <!-- TestNG -->
                    <dependency>
                        <groupId>org.testng</groupId>
                        <artifactId>testng</artifactId>
                        <version>${testng.version}</version>
                        <scope>test</scope>
                    </dependency>
            
                    <!-- RestAssured -->
                    <dependency>
                        <groupId>io.rest-assured</groupId>
                        <artifactId>rest-assured</artifactId>
                        <version>${rest-assured.version}</version>
                        <scope>test</scope>
                    </dependency>
            
                    <!-- Allure TestNG -->
                    <dependency>
                        <groupId>io.qameta.allure</groupId>
                        <artifactId>allure-testng</artifactId>
                        <version>${allure.version}</version>
                        <scope>test</scope>
                    </dependency>
            
                    <!-- Jackson for JSON -->
                    <dependency>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-databind</artifactId>
                        <version>2.17.1</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
            
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-surefire-plugin</artifactId>
                            <version>3.2.5</version>
                            <configuration>
                                <suiteXmlFiles>
                                    <suiteXmlFile>testng.xml</suiteXmlFile>
                                </suiteXmlFiles>
                                <argLine>
                                    -javaagent:"${settings.localRepository}/org/aspectj/aspectjweaver/1.9.21/aspectjweaver-1.9.21.jar"
                                </argLine>
                            </configuration>
                            <dependencies>
                                <dependency>
                                    <groupId>org.aspectj</groupId>
                                    <artifactId>aspectjweaver</artifactId>
                                    <version>1.9.21</version>
                                </dependency>
                            </dependencies>
                        </plugin>
            
                        <plugin>
                            <groupId>io.qameta.allure</groupId>
                            <artifactId>allure-maven</artifactId>
                            <version>2.12.0</version>
                            <configuration>
                                <reportVersion>2.29.0</reportVersion>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;

    private static final String TESTNG_XML_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
            <suite name="API Automation Suite" verbose="1">
                <listeners>
                    <listener class-name="io.qameta.allure.testng.AllureTestNg"/>
                </listeners>
                <test name="API Tests">
                    <packages>
                        <package name="com.example.tests.*"/>
                    </packages>
                </test>
            </suite>
            """;

    private static final String CLASS_TEMPLATE = """
            package com.example.tests;
            
            import io.qameta.allure.*;
            import io.restassured.RestAssured;
            import io.restassured.response.Response;
            import io.restassured.specification.RequestSpecification;
            import org.testng.annotations.BeforeClass;
            import org.testng.annotations.AfterClass;
            import org.testng.annotations.Test;
            import java.util.HashMap;
            import java.util.Map;
            import static org.testng.Assert.*;
            import static io.restassured.RestAssured.given;
            
            @Epic("{EPIC}")
            @Feature("{FEATURE}")
            public class {CLASS_NAME} {
                private String baseUrl = "{BASE_URL}";
                private String token;
            
                @BeforeClass
                public void setup() {
                    RestAssured.baseURI = baseUrl;
                    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
                    // TODO: 如需认证，在此处初始化 token
                    // token = "your-auth-token";
                }
            
                @AfterClass
                public void teardown() {
                    // Cleanup if needed
                }
            
                private RequestSpecification givenWithHeaders() {
                    RequestSpecification spec = given()
                        .header("Content-Type", "application/json");
                    // TODO: 如需认证，添加 Authorization header
                    // if (token != null) spec.header("Authorization", "Bearer " + token);
                    return spec;
                }
            
                {TEST_METHODS}
            }
            """;

    private static final String TEST_METHOD_TEMPLATE = """
            
                @Test(description = "{DESCRIPTION}")
                @Story("{STORY}")
                @Severity(SeverityLevel.{SEVERITY})
                @Step("{STEP_DESCRIPTION}")
                public void {METHOD_NAME}() {
                    {REQUEST_CODE}
                    {ASSERTIONS}
                }
            """;

    /**
     * 生成测试脚本并打包成ZIP
     *
     * @param testCases 测试用例列表
     * @param baseUrl   接口基础URL
     * @param outputDir 输出目录
     * @return ZIP文件路径
     */
    public static String generateScript(List<TestCase> testCases, String baseUrl, String outputDir) throws IOException {
        log.info("开始生成自动化测试脚本，用例数量: {}", testCases.size());

        Path tempDir = Files.createTempDirectory("api-automation");
        Path srcDir = tempDir.resolve("src").resolve("test").resolve("java").resolve("com").resolve("example").resolve("tests");
        Files.createDirectories(srcDir);

        Map<String, List<TestCase>> groupedByInterface = testCases.stream()
                .collect(Collectors.groupingBy(tc -> extractInterfaceName(tc.getInterfacePath())));

        List<String> generatedClasses = new ArrayList<>();

        for (Map.Entry<String, List<TestCase>> entry : groupedByInterface.entrySet()) {
            String interfaceName = entry.getKey();
            List<TestCase> interfaceTestCases = entry.getValue();

            String className = interfaceName + "Test";
            String testMethods = generateTestMethods(interfaceTestCases);
            String epic = interfaceTestCases.get(0).getTestModule() != null ? interfaceTestCases.get(0).getTestModule() : "API测试";
            String feature = interfaceName;

            String classContent = CLASS_TEMPLATE
                    .replace("{EPIC}", epic)
                    .replace("{FEATURE}", feature)
                    .replace("{CLASS_NAME}", className)
                    .replace("{BASE_URL}", baseUrl != null ? baseUrl : "http://localhost:8080");

            classContent = classContent.replace("{TEST_METHODS}", testMethods);

            Path classFile = srcDir.resolve(className + ".java");
            Files.writeString(classFile, classContent, StandardCharsets.UTF_8);
            generatedClasses.add(className);
            log.info("生成测试类: {}", className);
        }

        Files.writeString(tempDir.resolve("pom.xml"), POM_TEMPLATE, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("testng.xml"), TESTNG_XML_TEMPLATE, StandardCharsets.UTF_8);

        Files.writeString(tempDir.resolve("README.md"), generateReadme(baseUrl, generatedClasses), StandardCharsets.UTF_8);

        String zipFilePath = outputDir + File.separator + "api-automation-" + System.currentTimeMillis() + ".zip";
        createZip(tempDir.toFile(), zipFilePath);

        deleteDirectory(tempDir.toFile());

        log.info("测试脚本已生成并打包到: {}", zipFilePath);
        return zipFilePath;
    }

    /**
     * 生成测试脚本到字节数组（用于下载）
     */
    public static byte[] generateScriptAsZip(List<TestCase> testCases, String baseUrl) throws IOException {
        Path tempDir = Files.createTempDirectory("api-automation");
        Path srcDir = tempDir.resolve("src").resolve("test").resolve("java").resolve("com").resolve("example").resolve("tests");
        Files.createDirectories(srcDir);

        Map<String, List<TestCase>> groupedByInterface = testCases.stream()
                .collect(Collectors.groupingBy(tc -> extractInterfaceName(tc.getInterfacePath())));

        List<String> generatedClasses = new ArrayList<>();

        for (Map.Entry<String, List<TestCase>> entry : groupedByInterface.entrySet()) {
            String interfaceName = entry.getKey();
            List<TestCase> interfaceTestCases = entry.getValue();

            String className = interfaceName + "Test";
            String testMethods = generateTestMethods(interfaceTestCases);
            String epic = interfaceTestCases.get(0).getTestModule() != null ? interfaceTestCases.get(0).getTestModule() : "API测试";
            String feature = interfaceName;

            String classContent = CLASS_TEMPLATE
                    .replace("{EPIC}", epic)
                    .replace("{FEATURE}", feature)
                    .replace("{CLASS_NAME}", className)
                    .replace("{BASE_URL}", baseUrl != null ? baseUrl : "http://localhost:8080");

            classContent = classContent.replace("{TEST_METHODS}", testMethods);

            Path classFile = srcDir.resolve(className + ".java");
            Files.writeString(classFile, classContent, StandardCharsets.UTF_8);
            generatedClasses.add(className);
        }

        Files.writeString(tempDir.resolve("pom.xml"), POM_TEMPLATE, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("testng.xml"), TESTNG_XML_TEMPLATE, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("README.md"), generateReadme(baseUrl, generatedClasses), StandardCharsets.UTF_8);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addFolderToZip(tempDir.toFile(), tempDir.toFile(), zos);
        }

        deleteDirectory(tempDir.toFile());

        return baos.toByteArray();
    }

    private static String generateTestMethods(List<TestCase> testCases) {
        StringBuilder methods = new StringBuilder();

        for (TestCase tc : testCases) {
            String methodName = generateMethodName(tc.getTitle());
            String requestCode = generateRequestCode(tc);
            String assertions = generateAssertions(tc);
            String severity = mapSeverity(tc.getTitle());
            String story = tc.getTestPoint() != null ? tc.getTestPoint() : tc.getTitle();

            String methodContent = TEST_METHOD_TEMPLATE
                    .replace("{DESCRIPTION}", tc.getTitle())
                    .replace("{STORY}", story)
                    .replace("{SEVERITY}", severity)
                    .replace("{STEP_DESCRIPTION}", tc.getSteps() != null ? tc.getSteps() : tc.getTitle())
                    .replace("{METHOD_NAME}", methodName)
                    .replace("{REQUEST_CODE}", requestCode)
                    .replace("{ASSERTIONS}", assertions);

            methods.append(methodContent);
        }

        return methods.toString();
    }

    private static String generateRequestCode(TestCase tc) {
        String method = tc.getMethod() != null ? tc.getMethod().toUpperCase() : "POST";
        String path = tc.getInterfacePath() != null ? tc.getInterfacePath() : "/api/unknown";
        String params = tc.getParams();

        StringBuilder code = new StringBuilder();

        // 根据请求方法生成不同的代码
        if ("GET".equals(method)) {
            code.append("        Response response = givenWithHeaders()\n");
            // 如果有参数，作为 query params 传递
            if (params != null && !params.isEmpty()) {
                Map<String, String> queryParams = parseParamsToMap(params);
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    code.append("            .param(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\")\n");
                }
            }
            code.append("            .get(\"").append(path).append("\");\n");
        } else if ("DELETE".equals(method)) {
            code.append("        Response response = givenWithHeaders()\n");
            if (params != null && !params.isEmpty()) {
                String body = parseParamsToJsonBody(params);
                code.append("            .body(\"").append(escapeJson(body)).append("\")\n");
            }
            code.append("            .delete(\"").append(path).append("\");\n");
        } else {
            // POST, PUT, PATCH 等使用 JSON body
            code.append("        Response response = givenWithHeaders()\n");
            String body = parseParamsToJsonBody(params);
            code.append("            .body(\"").append(escapeJson(body)).append("\")\n");
            code.append("            .").append(method.toLowerCase()).append("(\"").append(path).append("\");\n");
        }

        return code.toString();
    }

    /**
     * 解析参数字符串为 Map（用于 GET 请求的 query params）
     */
    private static Map<String, String> parseParamsToMap(String params) {
        Map<String, String> map = new HashMap<>();
        if (params == null || params.isEmpty()) {
            return map;
        }

        // 尝试解析 JSON 格式
        if (params.contains("{") && params.contains("}")) {
            String json = params;
            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
            
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length >= 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim().replace("\"", "");
                    map.put(key, value);
                }
            }
        } else {
            // 尝试解析 key=value 格式
            String[] pairs = params.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length >= 2) {
                    map.put(kv[0].trim(), kv[1].trim());
                }
            }
        }

        return map;
    }

    /**
     * 解析参数为 JSON body（用于 POST/PUT/PATCH/DELETE）
     */
    private static String parseParamsToJsonBody(String params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }

        // 如果已经是 JSON 格式，直接返回
        if (params.trim().startsWith("{") && params.trim().endsWith("}")) {
            return params.trim();
        }

        // 尝试转换为 JSON
        Map<String, String> map = parseParamsToMap(params);
        if (map.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (count > 0) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            count++;
        }
        json.append("}");

        return json.toString();
    }

    private static String generateAssertions(TestCase tc) {
        StringBuilder assertions = new StringBuilder();

        int expectedCode = tc.getExpectedStatusCode() > 0 ? tc.getExpectedStatusCode() : 200;
        assertions.append("        // 状态码断言\n");
        assertions.append("        assertEquals(response.getStatusCode(), ").append(expectedCode).append(", \"HTTP状态码不符合预期\");\n");

        // 业务码断言（如果有的话）
        if (tc.getExpectedBusinessCode() != null && tc.getExpectedBusinessCode() > 0) {
            assertions.append("\n        // 业务码断言\n");
            assertions.append("        Integer businessCode = response.jsonPath().get(\"code\");\n");
            assertions.append("        if (businessCode != null) {\n");
            assertions.append("            assertEquals(businessCode, ").append(tc.getExpectedBusinessCode()).append(", \"业务码不符合预期\");\n");
            assertions.append("        }\n");
        }

        // 响应体断言（改进版）
        assertions.append("\n        // 响应体基本断言\n");
        assertions.append("        assertNotNull(response.getBody(), \"响应体不应为空\");\n");
        assertions.append("        String responseBody = response.getBody().asString();\n");
        assertions.append("        assertNotNull(responseBody, \"响应内容不应为空\");\n");

        // 预期结果断言（灵活处理）
        if (tc.getExpectedResult() != null && !tc.getExpectedResult().isEmpty()) {
            String expectedMsg = extractExpectedMessage(tc.getExpectedResult());
            if (!expectedMsg.isEmpty()) {
                assertions.append("\n        // 业务消息断言（可选，根据实际接口响应结构调整）\n");
                assertions.append("        // TODO: 根据实际接口响应结构调整断言逻辑\n");
                assertions.append("        // String msg = response.jsonPath().getString(\"msg\");\n");
                assertions.append("        // if (msg != null) assertTrue(msg.contains(\"").append(expectedMsg).append("\"), \"消息不符合预期\");\n");
            }
        }

        return assertions.toString();
    }

    private static String extractInterfaceName(String interfacePath) {
        if (interfacePath == null || interfacePath.isEmpty()) {
            return "Unknown";
        }

        String path = interfacePath.replace("/api/", "").replace("/", "_");
        if (path.startsWith("_")) {
            path = path.substring(1);
        }
        if (path.endsWith("_")) {
            path = path.substring(0, path.length() - 1);
        }

        return toCamelCase(path);
    }

    private static String generateMethodName(String title) {
        if (title == null || title.isEmpty()) {
            return "testUnknown";
        }

        String cleaned = title.replaceAll("[^\\w\\u4e00-\\u9fa5]", "_");
        String camelCase = toCamelCase(cleaned);

        if (!camelCase.isEmpty() && Character.isLetter(camelCase.charAt(0))) {
            return "test" + camelCase.substring(0, 1).toUpperCase() + camelCase.substring(1);
        }
        return "test" + camelCase;
    }

    private static String toCamelCase(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (char c : input.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
        }

        if (!result.isEmpty() && Character.isUpperCase(result.charAt(0))) {
            result.setCharAt(0, Character.toLowerCase(result.charAt(0)));
        }

        return result.toString();
    }

    private static String mapSeverity(String title) {
        if (title == null) {
            return "NORMAL";
        }

        String lower = title.toLowerCase();
        if (lower.contains("成功") || lower.contains("正常")) {
            return "CRITICAL";
        }
        if (lower.contains("失败") || lower.contains("异常") || lower.contains("错误")) {
            return "HIGH";
        }
        if (lower.contains("边界") || lower.contains("参数")) {
            return "MEDIUM";
        }
        return "NORMAL";
    }

    private static String extractRequestBody(String params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }

        if (params.contains("{") && params.contains("}")) {
            int start = params.indexOf("{");
            int end = params.lastIndexOf("}");
            return params.substring(start, end + 1);
        }

        return "{}";
    }

    private static String extractExpectedMessage(String expectedResult) {
        if (expectedResult == null || expectedResult.isEmpty()) {
            return "";
        }

        if (expectedResult.contains("成功")) {
            return "操作成功";
        }
        if (expectedResult.contains("失败")) {
            return "操作失败";
        }
        if (expectedResult.contains("登录")) {
            return "登录成功";
        }
        if (expectedResult.contains("创建")) {
            return "创建成功";
        }
        if (expectedResult.contains("更新")) {
            return "更新成功";
        }
        if (expectedResult.contains("删除")) {
            return "删除成功";
        }

        return expectedResult.length() > 50 ? expectedResult.substring(0, 50) : expectedResult;
    }

    private static String escapeJson(String json) {
        if (json == null) {
            return "";
        }
        return json.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String generateReadme(String baseUrl, List<String> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("# API 自动化测试脚本\n\n");
        sb.append("## 项目说明\n");
        sb.append("本项目包含自动生成的接口自动化测试脚本，使用 TestNG + RestAssured + Allure 技术栈。\n\n");
        sb.append("## ⚠️ 重要说明\n");
        sb.append("### 当前支持的请求模式\n");
        sb.append("- **JSON 模式**：请求参数作为 JSON body 发送（POST/PUT/PATCH/DELETE）\n");
        sb.append("- **Query Params 模式**：请求参数作为 URL query params 发送（GET）\n\n");
        sb.append("### 当前不支持的场景（需手动调整）\n");
        sb.append("- Form-data 表单提交\n");
        sb.append("- 文件上传（multipart/form-data）\n");
        sb.append("- 嵌套 JSON 结构参数（需手动构建复杂的 JSON body）\n");
        sb.append("- 自定义认证方式（如 OAuth2、API Key 等）\n\n");
        sb.append("## 基础配置\n");
        sb.append("修改测试类中的 `baseUrl` 变量为实际的 API 地址：\n");
        sb.append("```java\n");
        sb.append("private String baseUrl = \"").append(baseUrl != null ? baseUrl : "http://localhost:8080").append("\";\n");
        sb.append("```\n\n");
        sb.append("## 认证配置\n");
        sb.append("如果接口需要认证，请修改 `givenWithHeaders()` 方法：\n");
        sb.append("```java\n");
        sb.append("private RequestSpecification givenWithHeaders() {\n");
        sb.append("    RequestSpecification spec = given()\n");
        sb.append("        .header(\"Content-Type\", \"application/json\")\n");
        sb.append("        .header(\"Authorization\", \"Bearer YOUR_TOKEN\");\n");
        sb.append("    return spec;\n");
        sb.append("}\n");
        sb.append("```\n\n");
        sb.append("## 运行命令\n\n");
        sb.append("### 运行测试\n");
        sb.append("```bash\n");
        sb.append("mvn clean test\n");
        sb.append("```\n\n");
        sb.append("### 生成 Allure 报告\n");
        sb.append("```bash\n");
        sb.append("mvn allure:serve\n");
        sb.append("```\n\n");
        sb.append("### 生成静态报告\n");
        sb.append("```bash\n");
        sb.append("mvn allure:report\n");
        sb.append("```\n\n");
        sb.append("## 生成的测试类\n\n");
        for (String cls : classes) {
            sb.append("- ").append(cls).append(".java\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static void createZip(File sourceDir, String zipFilePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            addFolderToZip(sourceDir, sourceDir, zos);
        }
    }

    private static void addFolderToZip(File folder, File baseDir, ZipOutputStream zos) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                addFolderToZip(file, baseDir, zos);
            } else {
                String entryName = baseDir.toURI().relativize(file.toURI()).getPath();
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private static void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteDirectory(file);
            }
        }
        directory.delete();
    }
}
