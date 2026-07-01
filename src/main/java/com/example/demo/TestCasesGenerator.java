package com.example.demo;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated 请使用 {@link DocumentProcessor} 作为主入口
 * 该类为早期演示代码，已被 DocumentProcessor + AiService 覆盖
 */
@Deprecated(since = "v3.0", forRemoval = true)
public class TestCasesGenerator {

    private static ChatLanguageModel model;

    public static void main(String[] args) {
        model = QwenChatModel.builder()
                .apiKey(DocumentProcessor.getApiKey())
                .modelName("qwen-plus")
                .build();

        String interfaceDoc = """
            POST /api/data/transfer
            参数: 
              sourceDept: 字符串, 2-50字符, 来源部门
              targetDept: 字符串, 2-50字符, 目标部门  
              dataType: 枚举(PERSONAL/ENTERPRISE/GOVERNMENT), 数据类型
              volumeMB: 整数, 1-10240, 数据量MB
            """;

        try {
            System.out.println("======== 开始分析接口文档 ========");
            List<String> testCases = generateTestCases(interfaceDoc);
            System.out.println();
            System.out.println("======== AI生成的测试用例 ========");
            testCases.forEach(System.out::println);
            saveToFile(testCases, "test_cases.txt");
            System.out.println();
            System.out.println("✅ 测试用例已保存到 test_cases.txt");
        } catch (Exception e) {
            System.out.println("❌ API调用失败: " + e.getMessage());
            System.out.println("📝 输出模拟测试用例...");
            List<String> mockCases = getMockTestCases();
            mockCases.forEach(System.out::println);
            saveToFile(mockCases, "test_cases.txt");
        }
    }

    private static List<String> generateTestCases(String interfaceDoc) {
        String prompt = """
            你是一位资深测试工程师。请为以下接口设计测试用例。
            要求：
            1. 覆盖等价类划分、边界值分析、异常场景
            2. 每个用例一行，格式：用例编号|用例标题|前置条件|测试步骤|预期结果
            3. 至少生成6个用例

            接口文档：
            """ + interfaceDoc;

        String result = model.generate(prompt);
        List<String> cases = new ArrayList<>();
        for (String line : result.split("\n")) {
            line = line.trim();
            if (line.contains("|") && line.matches(".*TC\\d+.*")) {
                cases.add(line);
            }
        }
        return cases;
    }

    private static void saveToFile(List<String> content, String filename) {
        try {
            Path path = Paths.get(filename);
            Files.write(path, content);
        } catch (IOException e) {
            System.out.println("保存文件失败: " + e.getMessage());
        }
    }

    private static List<String> getMockTestCases() {
        List<String> cases = new ArrayList<>();
        cases.add("TC001|正常场景-个人数据小量传输|系统正常运行，sourceDept和targetDept已存在|调用POST /api/data/transfer，参数sourceDept=\"财务部\",targetDept=\"人事部\",dataType=\"PERSONAL\",volumeMB=100|返回200，数据传输成功");
        cases.add("TC002|边界值-sourceDept最小长度|系统正常运行|调用接口，sourceDept=\"AB\",targetDept=\"人事部\",dataType=\"ENTERPRISE\",volumeMB=512|返回200，数据传输成功");
        cases.add("TC003|边界值-volumeMB最大值|系统正常运行|调用接口，sourceDept=\"财务部\",targetDept=\"人事部\",dataType=\"GOVERNMENT\",volumeMB=10240|返回200，数据传输成功");
        cases.add("TC004|异常-sourceDept为空|系统正常运行|调用接口，sourceDept=\"\",targetDept=\"人事部\",dataType=\"PERSONAL\",volumeMB=100|返回400，提示\"sourceDept不能为空\"");
        cases.add("TC005|异常-dataType非法值|系统正常运行|调用接口，sourceDept=\"财务部\",targetDept=\"人事部\",dataType=\"INVALID\",volumeMB=100|返回400，提示\"dataType值无效\"");
        cases.add("TC006|异常-volumeMB超出范围|系统正常运行|调用接口，sourceDept=\"财务部\",targetDept=\"人事部\",dataType=\"PERSONAL\",volumeMB=10241|返回400，提示\"volumeMB超出范围\"");
        return cases;
    }
}