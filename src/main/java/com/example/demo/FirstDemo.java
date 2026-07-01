package com.example.demo;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;

/**
 * @deprecated 请使用 {@link DocumentProcessor} 作为主入口
 * 该类为早期演示代码，已被 DocumentProcessor 覆盖
 */
@Deprecated(since = "v3.0", forRemoval = true)
public class FirstDemo {
    public static void main(String[] args) {
        // 通义千问配置（复用 DocumentProcessor 的安全密钥获取方式）
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(DocumentProcessor.getApiKey())
                .modelName("qwen-plus")  // qwen-turbo 便宜，qwen-plus 更强
                .build();

        String interfaceDoc = """
            POST /api/data/transfer
            参数: 
              sourceDept: 字符串, 2-50字符, 来源部门
              targetDept: 字符串, 2-50字符, 目标部门  
              dataType: 枚举(PERSONAL/ENTERPRISE/GOVERNMENT), 数据类型
              volumeMB: 整数, 1-10240, 数据量MB
            """;

        String prompt = """
            你是一位政务数据测试专家。请为以下接口设计测试用例，
            要求覆盖：等价类划分、边界值分析、异常场景。
            输出格式：用例编号 | 用例标题 | 前置条件 | 测试步骤 | 预期结果
            
            接口文档：
            """ + interfaceDoc;

        try {
            String testCases = model.generate(prompt);
            System.out.println("======== AI生成的测试用例 ========");
            System.out.println(testCases);
        } catch (Exception e) {
            System.out.println("======== API调用失败（余额不足或网络问题）========");
            System.out.println("错误信息: " + e.getMessage());
            System.out.println();
            System.out.println("======== 本地测试输出（模拟AI生成的测试用例）========");
            System.out.println(getMockTestCases());
        }
    }

    private static String getMockTestCases() {
        return """
            用例编号 | 用例标题 | 前置条件 | 测试步骤 | 预期结果
            TC001 | 正常场景-个人数据小量传输 | 系统正常运行，sourceDept和targetDept已存在 | 1.调用POST /api/data/transfer，参数sourceDept="财务部",targetDept="人事部",dataType="PERSONAL",volumeMB=100 | 返回200，数据传输成功
            TC002 | 边界值-sourceDept最小长度 | 系统正常运行 | 1.调用接口，sourceDept="AB",targetDept="人事部",dataType="ENTERPRISE",volumeMB=512 | 返回200，数据传输成功
            TC003 | 边界值-volumeMB最大值 | 系统正常运行 | 1.调用接口，sourceDept="财务部",targetDept="人事部",dataType="GOVERNMENT",volumeMB=10240 | 返回200，数据传输成功
            TC004 | 异常-sourceDept为空 | 系统正常运行 | 1.调用接口，sourceDept="",targetDept="人事部",dataType="PERSONAL",volumeMB=100 | 返回400，提示"sourceDept不能为空"
            TC005 | 异常-dataType非法值 | 系统正常运行 | 1.调用接口，sourceDept="财务部",targetDept="人事部",dataType="INVALID",volumeMB=100 | 返回400，提示"dataType值无效"
            TC006 | 异常-volumeMB超出范围 | 系统正常运行 | 1.调用接口，sourceDept="财务部",targetDept="人事部",dataType="PERSONAL",volumeMB=10241 | 返回400，提示"volumeMB超出范围"
            """;
    }
}