package com.example.demo.service;

import com.example.demo.TestCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 闭环反馈系统
 * 记录修正的用例，作为Few-shot learning的示例
 */
@Slf4j
public class FeedbackSystem {

    private static final String FEEDBACK_FILE = System.getProperty("user.dir") +
            File.separator + "feedback" + File.separator + "corrected_cases.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    /**
     * 保存修正的测试用例
     */
    public static void saveCorrectedCases(List<TestCase> correctedCases) throws IOException {
        ensureFeedbackDir();

        List<FeedbackRecord> records = loadRecords();

        for (TestCase tc : correctedCases) {
            FeedbackRecord record = new FeedbackRecord();
            record.setTestCase(tc);
            record.setCorrectedAt(LocalDateTime.now());
            record.setVersion("v3.0");
            records.add(record);
        }

        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(FEEDBACK_FILE), records);
        log.info("修正用例已保存到反馈系统");
    }

    /**
     * 获取用于Few-shot的示例用例
     */
    public static String getFewShotExamples(int count) {
        try {
            List<FeedbackRecord> records = loadRecords();
            if (records.isEmpty()) {
                return "";
            }

            // 取最近的count个记录
            List<FeedbackRecord> recentRecords = records.stream()
                    .sorted((a, b) -> b.getCorrectedAt().compareTo(a.getCorrectedAt()))
                    .limit(count)
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("以下是之前人工修正过的优秀用例，请参考这种风格：\n");
            sb.append("============================================\n");

            for (FeedbackRecord record : recentRecords) {
                TestCase tc = record.getTestCase();
                sb.append("用例编号: ").append(tc.getId()).append("\n");
                sb.append("用例标题: ").append(tc.getTitle()).append("\n");
                sb.append("接口路径: ").append(tc.getInterfacePath()).append("\n");
                sb.append("请求方法: ").append(tc.getMethod()).append("\n");
                sb.append("请求参数: ").append(tc.getParams()).append("\n");
                sb.append("预期结果: ").append(tc.getExpectedResult()).append("\n");
                sb.append("--------------------------------------------\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("读取反馈记录失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 计算用例质量分数
     */
    public static QualityScore calculateQualityScore(List<TestCase> testCases) {
        QualityScore score = new QualityScore();
        score.setTotalCount(testCases.size());

        int completeCount = 0;
        int formatCorrectCount = 0;

        for (TestCase tc : testCases) {
            boolean isComplete = isCaseComplete(tc);
            boolean isFormatCorrect = isFormatCorrect(tc);

            if (isComplete) completeCount++;
            if (isFormatCorrect) formatCorrectCount++;
        }

        score.setCompleteCount(completeCount);
        score.setFormatCorrectCount(formatCorrectCount);
        score.setCompletionRate(score.getTotalCount() > 0 ?
                (double) completeCount / score.getTotalCount() * 100 : 0);
        score.setFormatCorrectRate(score.getTotalCount() > 0 ?
                (double) formatCorrectCount / score.getTotalCount() * 100 : 0);

        return score;
    }

    /**
     * 判断用例是否完整
     */
    private static boolean isCaseComplete(TestCase tc) {
        return tc.getId() != null && !tc.getId().isEmpty() &&
                tc.getTitle() != null && !tc.getTitle().isEmpty() &&
                tc.getInterfacePath() != null && !tc.getInterfacePath().isEmpty() &&
                tc.getExpectedResult() != null && !tc.getExpectedResult().isEmpty();
    }

    /**
     * 判断格式是否正确
     */
    private static boolean isFormatCorrect(TestCase tc) {
        // 用例编号格式：TCxxx
        if (tc.getId() != null && !tc.getId().matches("TC\\d{3,}")) {
            return false;
        }
        // 请求方法格式：GET/POST/PUT/DELETE等
        if (tc.getMethod() != null &&
                !tc.getMethod().matches("(GET|POST|PUT|DELETE|PATCH|OPTIONS)")) {
            return false;
        }
        // 接口路径格式：以/开头
        if (tc.getInterfacePath() != null && !tc.getInterfacePath().startsWith("/")) {
            return false;
        }
        return true;
    }

    /**
     * 获取反馈记录数量
     */
    public static int getFeedbackCount() {
        try {
            return loadRecords().size();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 加载反馈记录
     */
    private static List<FeedbackRecord> loadRecords() throws IOException {
        File file = new File(FEEDBACK_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        return OBJECT_MAPPER.readValue(file,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, FeedbackRecord.class));
    }

    /**
     * 确保反馈目录存在
     */
    private static void ensureFeedbackDir() {
        File dir = new File(FEEDBACK_FILE).getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 反馈记录
     */
    public static class FeedbackRecord {
        private TestCase testCase;
        private LocalDateTime correctedAt;
        private String version;

        public TestCase getTestCase() { return testCase; }
        public void setTestCase(TestCase testCase) { this.testCase = testCase; }
        public LocalDateTime getCorrectedAt() { return correctedAt; }
        public void setCorrectedAt(LocalDateTime correctedAt) { this.correctedAt = correctedAt; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    /**
     * 质量分数
     */
    public static class QualityScore {
        private int totalCount;
        private int completeCount;
        private int formatCorrectCount;
        private double completionRate;
        private double formatCorrectRate;

        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public int getCompleteCount() { return completeCount; }
        public void setCompleteCount(int completeCount) { this.completeCount = completeCount; }
        public int getFormatCorrectCount() { return formatCorrectCount; }
        public void setFormatCorrectCount(int formatCorrectCount) { this.formatCorrectCount = formatCorrectCount; }
        public double getCompletionRate() { return completionRate; }
        public void setCompletionRate(double completionRate) { this.completionRate = completionRate; }
        public double getFormatCorrectRate() { return formatCorrectRate; }
        public void setFormatCorrectRate(double formatCorrectRate) { this.formatCorrectRate = formatCorrectRate; }

        @Override
        public String toString() {
            return String.format("质量分数: 总数=%d, 完整率=%.1f%%, 格式正确率=%.1f%%",
                    totalCount, completionRate, formatCorrectRate);
        }
    }
}