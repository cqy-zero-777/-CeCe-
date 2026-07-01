package com.example.demo.service;

import com.example.demo.TestCase;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

/**
 * 测试用例执行引擎
 * 支持并行执行HTTP请求，自动比对结果
 */
@Slf4j
public class TestCaseExecutor {

    private static final int DEFAULT_THREAD_POOL_SIZE = 10;

    /**
     * 执行所有测试用例
     */
    public static ExecutionResult executeAll(List<TestCase> testCases, String baseUrl) {
        return executeAll(testCases, baseUrl, DEFAULT_THREAD_POOL_SIZE);
    }

    /**
     * 执行所有测试用例（自定义线程池大小）
     */
    public static ExecutionResult executeAll(List<TestCase> testCases, String baseUrl, int threadPoolSize) {
        ExecutionResult result = new ExecutionResult();
        result.setBaseUrl(baseUrl);
        result.setTotalCount(testCases.size());
        result.setStartTime(System.currentTimeMillis());

        log.info("开始执行测试用例，共 {} 条，线程数: {}", testCases.size(), threadPoolSize);

        // 设置RestAssured基础URL
        RestAssured.baseURI = baseUrl;

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        List<CompletableFuture<ExecutionRecord>> futures = new ArrayList<>();

        // 提交任务
        for (TestCase tc : testCases) {
            CompletableFuture<ExecutionRecord> future = CompletableFuture.supplyAsync(
                    () -> executeTestCase(tc), executor);
            futures.add(future);
        }

        // 等待完成
        for (CompletableFuture<ExecutionRecord> future : futures) {
            try {
                ExecutionRecord record = future.get();
                result.getRecords().add(record);

                if (record.getStatus() == ExecutionRecord.Status.PASS) {
                    result.incrementPassCount();
                } else if (record.getStatus() == ExecutionRecord.Status.FAIL) {
                    result.incrementFailCount();
                } else {
                    result.incrementSkipCount();
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("执行中断: {}", e.getMessage(), e);
            }
        }

        executor.shutdown();

        result.setEndTime(System.currentTimeMillis());
        result.calculatePassRate();

        log.info("执行完成: 总计={}, 通过={}, 失败={}, 跳过={}",
                result.getTotalCount(), result.getPassCount(),
                result.getFailCount(), result.getSkipCount());

        return result;
    }

    /**
     * 执行单个测试用例
     */
    private static ExecutionRecord executeTestCase(TestCase tc) {
        ExecutionRecord record = new ExecutionRecord();
        record.setTestCase(tc);
        record.setStartTime(System.currentTimeMillis());

        try {
            // 构建请求
            String method = tc.getMethod() != null ? tc.getMethod().toUpperCase() : "GET";
            String path = tc.getInterfacePath();

            if (path == null || path.isEmpty()) {
                record.setStatus(ExecutionRecord.Status.SKIP);
                record.setErrorMessage("接口路径为空");
                record.setEndTime(System.currentTimeMillis());
                return record;
            }

            log.debug("执行中: {} {}", method, path);

            // 发送请求
            Response response;
            switch (method) {
                case "POST":
                    response = RestAssured.given()
                            .header("Content-Type", "application/json")
                            .body(tc.getParams())
                            .post(path);
                    break;
                case "PUT":
                    response = RestAssured.given()
                            .header("Content-Type", "application/json")
                            .body(tc.getParams())
                            .put(path);
                    break;
                case "DELETE":
                    response = RestAssured.given()
                            .header("Content-Type", "application/json")
                            .delete(path);
                    break;
                default: // GET
                    response = RestAssured.given()
                            .queryParams(parseParams(tc.getParams()))
                            .get(path);
            }

            // 记录响应
            record.setResponseStatusCode(response.getStatusCode());
            record.setResponseTime(response.getTime());
            record.setResponseBody(response.getBody().asString());

            // 判断结果
            if (isResponseMatch(response, tc)) {
                record.setStatus(ExecutionRecord.Status.PASS);
                log.debug("通过: {} {}", method, path);
            } else {
                record.setStatus(ExecutionRecord.Status.FAIL);
                record.setErrorMessage("响应与预期不符");
                log.debug("失败: {} {}", method, path);
            }

        } catch (Exception e) {
            record.setStatus(ExecutionRecord.Status.FAIL);
            record.setErrorMessage(e.getMessage());
            log.error("执行异常: {} - {}", tc.getInterfacePath(), e.getMessage(), e);
        }

        record.setEndTime(System.currentTimeMillis());
        return record;
    }

    /**
     * 判断响应是否匹配预期结果（支持结构化字段 + 字符串兼容）
     */
    private static boolean isResponseMatch(Response response, TestCase tc) {
        int actualStatusCode = response.getStatusCode();
        String responseBody = response.getBody().asString();

        // 1. 优先使用结构化字段进行校验
        if (tc.getExpectedStatusCode() != null) {
            if (actualStatusCode != tc.getExpectedStatusCode()) {
                return false;
            }
        }

        // 2. 校验业务码
        if (tc.getExpectedBusinessCode() != null) {
            Integer actualBusinessCode = extractBusinessCodeFromResponse(responseBody);
            if (actualBusinessCode != null && actualBusinessCode != tc.getExpectedBusinessCode()) {
                return false;
            }
        }

        // 3. 如果有预期描述，检查响应内容包含关系
        String expectedDescription = tc.getExpectedResult();
        if (expectedDescription != null && !expectedDescription.isEmpty() && expectedDescription.length() > 2) {
            // 兼容旧格式：如果预期描述中包含状态码，也进行校验
            Integer expectedStatusCodeFromDesc = extractStatusCode(expectedDescription);
            if (expectedStatusCodeFromDesc != null) {
                if (actualStatusCode != expectedStatusCodeFromDesc) {
                    return false;
                }
            }

            // 检查响应内容是否包含预期描述（移除状态码后）
            String contentToCheck = expectedDescription.replaceAll("返回\\s*\\d{3}", "")
                    .replaceAll("[，,]\\s*code[=:]\\d+", "")
                    .replaceAll("code[=:]\\d+", "")
                    .trim();

            if (!contentToCheck.isEmpty() && contentToCheck.length() > 2) {
                return responseBody.contains(contentToCheck) ||
                        expectedDescription.contains(String.valueOf(actualStatusCode));
            }
        }

        // 如果没有指定任何预期条件，默认通过
        if (tc.getExpectedStatusCode() == null && 
            tc.getExpectedBusinessCode() == null && 
            (tc.getExpectedResult() == null || tc.getExpectedResult().isEmpty())) {
            return actualStatusCode == 200;
        }

        return true;
    }

    /**
     * 从预期结果中提取HTTP状态码
     */
    private static Integer extractStatusCode(String expected) {
        if (expected == null) return null;

        // 匹配 "返回200"、"返回 200"、"status:200" 等格式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("返回\\s*(\\d{3})");
        java.util.regex.Matcher matcher = pattern.matcher(expected);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        // 直接包含3位数字也认为是状态码
        pattern = java.util.regex.Pattern.compile("\\b(200|201|400|401|403|404|500|502|503)\\b");
        matcher = pattern.matcher(expected);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return null;
    }

    /**
     * 从预期结果中提取业务码（如 code=0, code:0）
     */
    private static Integer extractBusinessCode(String expected) {
        if (expected == null) return null;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("code[=:]\\s*(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(expected);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return null;
    }

    /**
     * 从响应体中提取业务码
     */
    private static Integer extractBusinessCodeFromResponse(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) return null;

        try {
            // 尝试解析JSON响应中的code字段
            // 匹配 "code":0, "code": 0, "code": "0" 等格式
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"code\"\\s*:\\s*(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(responseBody);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }

            // 匹配 'code':0 格式（单引号）
            pattern = java.util.regex.Pattern.compile("'code'\\s*:\\s*(\\d+)");
            matcher = pattern.matcher(responseBody);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            // 解析失败，返回null
        }

        return null;
    }

    /**
     * 解析参数字符串
     */
    private static java.util.Map<String, String> parseParams(String params) {
        java.util.Map<String, String> result = new HashMap<>();
        if (params == null || params.isEmpty()) {
            return result;
        }

        // 简单解析：key1=value1, key2=value2
        String[] pairs = params.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0].trim(), kv[1].trim());
            }
        }
        return result;
    }

    /**
     * 执行结果
     */
    public static class ExecutionResult {
        private String baseUrl;
        private int totalCount;
        private int passCount;
        private int failCount;
        private int skipCount;
        private double passRate;
        private long startTime;
        private long endTime;
        private List<ExecutionRecord> records = new ArrayList<>();

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public int getPassCount() { return passCount; }
        public void incrementPassCount() { this.passCount++; }
        public int getFailCount() { return failCount; }
        public void incrementFailCount() { this.failCount++; }
        public int getSkipCount() { return skipCount; }
        public void incrementSkipCount() { this.skipCount++; }
        public double getPassRate() { return passRate; }
        public void calculatePassRate() {
            this.passRate = totalCount > 0 ? (double) passCount / totalCount * 100 : 0;
        }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public List<ExecutionRecord> getRecords() { return records; }
        public void setRecords(List<ExecutionRecord> records) { this.records = records; }

        public long getDurationMs() { return endTime - startTime; }
    }

    /**
     * 执行记录
     */
    public static class ExecutionRecord {
        private TestCase testCase;
        private Status status;
        private int responseStatusCode;
        private long responseTime;
        private String responseBody;
        private String errorMessage;
        private long startTime;
        private long endTime;

        public enum Status { PASS, FAIL, SKIP }

        public TestCase getTestCase() { return testCase; }
        public void setTestCase(TestCase testCase) { this.testCase = testCase; }
        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }
        public int getResponseStatusCode() { return responseStatusCode; }
        public void setResponseStatusCode(int responseStatusCode) { this.responseStatusCode = responseStatusCode; }
        public long getResponseTime() { return responseTime; }
        public void setResponseTime(long responseTime) { this.responseTime = responseTime; }
        public String getResponseBody() { return responseBody; }
        public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }

        public long getDurationMs() { return endTime - startTime; }
    }
}