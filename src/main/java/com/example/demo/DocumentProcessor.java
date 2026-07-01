package com.example.demo;

import com.example.demo.service.AiService;
import com.example.demo.service.ExcelExporter;
import com.example.demo.service.TestCaseExecutor;
import com.example.demo.service.WordParser;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * AI测试用例生成工具主入口
 * 职责：命令行参数解析、流程控制、交互模式
 */
@Slf4j
public class DocumentProcessor {

    public static void main(String[] args) {
        String mode = "interactive";
        String inputPath = null;
        String outputPath = null;
        String baseUrl = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--batch":
                    mode = "batch";
                    if (i + 1 < args.length) inputPath = args[++i];
                    break;
                case "--server":
                    mode = "server";
                    break;
                case "--execute":
                    mode = "execute";
                    if (i + 1 < args.length) inputPath = args[++i];
                    if (i + 1 < args.length && !args[i+1].startsWith("--")) baseUrl = args[++i];
                    break;
                case "--output":
                    if (i + 1 < args.length) outputPath = args[++i];
                    break;
                case "--help":
                    printHelp();
                    return;
                default:
                    if (!args[i].startsWith("--")) inputPath = args[i];
            }
        }

        AiService.initModel();

        log.info("========================================");
        log.info(" AI测试用例生成工具 By 陈麒钰-v4.0");
        log.info("========================================");

        String outputDir = System.getProperty("user.dir") + File.separator + "output";
        new File(outputDir).mkdirs();
        log.info("📁 输出目录: {}", outputDir);

        try {
            if ("server".equals(mode)) {
                com.example.demo.DemoApplication.main(args);
                return;
            }

            if ("execute".equals(mode)) {
                if (inputPath == null) {
                    log.error("❌ 请指定测试用例文件路径");
                    return;
                }
                executeTestCases(inputPath, baseUrl);
                return;
            }

            if ("batch".equals(mode)) {
                if (inputPath == null) {
                    log.error("❌ 请指定文档目录或文件路径");
                    return;
                }
                com.example.demo.service.BatchScanService.processBatch(inputPath, outputDir);
                return;
            }

            runInteractiveMode(outputDir);

        } catch (RuntimeException e) {
            log.error("❌ {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ 发生错误: {}", e.getMessage(), e);

            new File(outputDir).mkdirs();
            String mockPath = outputDir + File.separator + "FAILED_MOCK_测试用例_" + System.currentTimeMillis() + ".xlsx";
            ExcelExporter.exportFailedMockToExcel(mockPath);
            System.out.println("⚠️ 已生成失败模拟文件: " + mockPath);
        }
    }

    /**
     * 交互模式运行
     */
    private static void runInteractiveMode(String outputDir) {
        Scanner scanner = new Scanner(System.in);

        log.info("请选择输入类型：");
        log.info("1. 📄 Word文档 (.docx)");
        log.info("2. 📡 Swagger/OpenAPI URL");
        log.info("3. 📁 Swagger/OpenAPI文件 (.json/.yaml/.yml)");
        log.info("请输入序号: ");

        int choice = scanner.nextInt();
        scanner.nextLine();

        List<String> interfaces = new ArrayList<>();

        switch (choice) {
            case 1:
                log.info("请输入Word文档路径: ");
                String docPath = scanner.nextLine().trim();
                log.info("正在读取Word文档: {}", docPath);

                log.info("请选择读取方式：");
                log.info("1. 📖 读取全部内容");
                log.info("2. 📄 指定页码范围");
                log.info("3. 📑 指定章节");
                log.info("4. ⚡ 默认读取前50页（推荐，避免内存溢出）");
                log.info("请输入序号: ");
                
                int readMode = scanner.nextInt();
                scanner.nextLine();

                switch (readMode) {
                    case 1:
                        interfaces = WordParser.extractFromDocx(docPath);
                        break;
                    
                    case 2:
                        log.info("请输入起始页码（从1开始）: ");
                        int startPage = scanner.nextInt();
                        log.info("请输入结束页码（0表示读到最后）: ");
                        int endPage = scanner.nextInt();
                        scanner.nextLine();
                        interfaces = WordParser.extractFromDocx(docPath, startPage, endPage);
                        break;
                    
                    case 3:
                        List<String> allChapters = WordParser.getChapterTitles(docPath);
                        if (allChapters.isEmpty()) {
                            log.warn("未识别到章节，默认读取前3章");
                            interfaces = WordParser.extractFromDocx(docPath, 1, 50);
                        } else {
                            log.info("文档中的章节列表：");
                            for (int i = 0; i < allChapters.size(); i++) {
                                log.info("  {}. {}", i + 1, allChapters.get(i));
                            }
                            log.info("请输入要读取的章节序号（多个用逗号分隔，如：1,3,5）: ");
                            String chapterInput = scanner.nextLine().trim();
                            String[] chapterIndexes = chapterInput.split(",");
                            List<String> selectedChapters = new ArrayList<>();
                            for (String idx : chapterIndexes) {
                                try {
                                    int index = Integer.parseInt(idx.trim()) - 1;
                                    if (index >= 0 && index < allChapters.size()) {
                                        selectedChapters.add(allChapters.get(index));
                                    }
                                } catch (NumberFormatException e) {
                                    log.warn("无效的章节序号: {}", idx);
                                }
                            }
                            if (selectedChapters.isEmpty()) {
                                log.warn("未选择任何章节，默认读取前3章");
                                selectedChapters = allChapters.subList(0, Math.min(3, allChapters.size()));
                            }
                            interfaces = WordParser.extractFromDocxByChapter(docPath, selectedChapters);
                        }
                        break;
                    
                    case 4:
                    default:
                        log.info("⚡ 默认模式：读取前50页");
                        interfaces = WordParser.extractFromDocx(docPath, 1, 50);
                        break;
                }

                if (interfaces.isEmpty()) {
                    log.warn("WordParser未提取到接口，使用AI直接提取");
                    String docContent = WordParser.readDocx(docPath);
                    if (docContent != null && docContent.length() >= 50) {
                        interfaces = AiService.extractAllInterfaces(docContent);
                    }
                }
                break;

            case 2:
                log.info("请输入Swagger URL: ");
                String url = scanner.nextLine().trim();
                interfaces = SwaggerParser.parseSwagger(url);
                break;

            case 3:
                log.info("请输入Swagger文件路径: ");
                String filePath = scanner.nextLine().trim();
                interfaces = SwaggerParser.parseSwagger(filePath);
                break;

            default:
                log.error("❌ 无效的选择");
                return;
        }

        if (interfaces.isEmpty()) {
            log.error("❌ 未提取到任何接口");
            return;
        }

        log.info("✅ 共提取到 {} 个接口", interfaces.size());
        log.info("正在为每个接口生成测试用例...");

        List<TestCase> allTestCases = new ArrayList<>();
        int interfaceIndex = 1;

        for (String api : interfaces) {
            log.info("正在处理第 {}/{} 个接口...", interfaceIndex, interfaces.size());
            List<TestCase> testCases = AiService.generateTestCases(api);

            if (testCases.isEmpty()) {
                log.warn("接口 {} 生成了 0 条用例，跳过", api);
                interfaceIndex++;
                continue;
            }

            allTestCases.addAll(testCases);

            interfaceIndex++;
            log.info("  → 生成了 {} 条用例", testCases.size());

            if (interfaceIndex % 10 == 0) {
                ExcelExporter.exportToExcel(allTestCases,
                        outputDir + File.separator + "测试用例_临时_" + interfaceIndex + ".xlsx");
                log.info("📝 临时保存：已处理 {} 个接口", interfaceIndex);
            }
        }

        if (allTestCases.isEmpty()) {
            log.error("所有接口都未能生成测试用例");
            throw new RuntimeException("AI返回了空的测试用例列表，请检查接口文档格式或AI服务状态");
        }

        log.info("======== 生成的所有测试用例 ========");
        for (TestCase tc : allTestCases) {
            log.info("{}", tc);
        }
        log.info("📊 总计：{} 条测试用例", allTestCases.size());

        String excelPath = outputDir + File.separator + "测试用例_" + System.currentTimeMillis() + ".xlsx";
        ExcelExporter.exportToExcel(allTestCases, excelPath);
        log.info("✅ 测试用例已导出到: {}", excelPath);

        String txtPath = outputDir + File.separator + "测试用例_" + System.currentTimeMillis() + ".txt";
        ExcelExporter.saveToText(allTestCases, txtPath);
        log.info("✅ 测试用例已保存到文本文件: {}", txtPath);
    }

    /**
     * 执行测试用例
     */
    private static void executeTestCases(String excelPath, String baseUrl) {
        List<TestCase> testCases;
        try {
            testCases = ExcelExporter.importFromExcel(excelPath);
        } catch (Exception e) {
            log.error("读取测试用例失败: {}", e.getMessage(), e);
            return;
        }

        if (testCases.isEmpty()) {
            log.warn("未读取到测试用例");
            return;
        }

        log.info("共 {} 条测试用例", testCases.size());

        TestCaseExecutor.ExecutionResult result = TestCaseExecutor.executeAll(testCases, baseUrl);

        log.info("执行完成:");
        log.info("  总数: {}", result.getTotalCount());
        log.info("  通过: {}", result.getPassCount());
        log.info("  失败: {}", result.getFailCount());
        log.info("  耗时: {}ms", (result.getEndTime() - result.getStartTime()));

        String resultPath = excelPath.replace(".xlsx", "_执行结果.xlsx");
        ExcelExporter.exportExecutionResult(result, resultPath);
        log.info("执行结果已导出到: {}", resultPath);
    }

    /**
     * 打印帮助信息
     */
    private static void printHelp() {
        log.info("用法:");
        log.info("  交互模式: java -jar demo.jar");
        log.info("  批量模式: java -jar demo.jar --batch <目录或文件>");
        log.info("  执行用例: java -jar demo.jar --execute <测试用例.xlsx> [baseUrl]");
        log.info("  服务器模式: java -jar demo.jar --server");
        log.info("");
        log.info("环境变量:");
        log.info("  DASHSCOPE_API_KEY 或 AI_API_KEY - AI API密钥");
        log.info("  AI_MODEL_PROVIDER - 模型提供商 (qwen/deepseek)，默认 qwen");
    }

    /**
     * 读取docx文档
     */
    public static String readDocx(String filePath) {
        log.info("正在读取文档: {}", filePath);
        return WordParser.readDocx(filePath);
    }

    /**
     * 从docx文档中提取接口定义（入口方法，供外部调用）
     */
    public static List<String> extractFromDocx(String docxPath) {
        String docContent = readDocx(docxPath);

        if (docContent == null || docContent.length() < 50) {
            log.warn("文档内容过短，返回示例接口");
            return getExampleInterfaces();
        }

        // AI提取优先，失败时用正则兜底
        List<String> aiResult = AiService.extractAllInterfaces(docContent);
        if (!aiResult.isEmpty()) {
            return aiResult;
        }
        log.warn("AI未能提取接口，使用正则解析兜底");
        return WordParser.parseInterfacesFromText(docContent);
    }

    /**
     * 从docx文档中提取接口定义（指定页码范围）
     */
    public static List<String> extractFromDocx(String docxPath, int startPage, int endPage) {
        String docContent = WordParser.readDocx(docxPath, startPage, endPage);

        if (docContent == null || docContent.length() < 50) {
            log.warn("文档内容过短，返回示例接口");
            return getExampleInterfaces();
        }

        // AI提取优先，失败时用正则兜底
        List<String> aiResult = AiService.extractAllInterfaces(docContent);
        if (!aiResult.isEmpty()) {
            return aiResult;
        }
        log.warn("AI未能提取接口，使用正则解析兜底");
        return WordParser.parseInterfacesFromText(docContent);
    }

    /**
     * 从docx文档中提取接口定义（指定章节）
     */
    public static List<String> extractFromDocxByChapter(String docxPath, List<String> chapterTitles) {
        String docContent = WordParser.readDocxByChapter(docxPath, chapterTitles);

        if (docContent == null || docContent.length() < 50) {
            log.warn("文档内容过短，返回示例接口");
            return getExampleInterfaces();
        }

        // AI提取优先，失败时用正则兜底
        List<String> aiResult = AiService.extractAllInterfaces(docContent);
        if (!aiResult.isEmpty()) {
            return aiResult;
        }
        log.warn("AI未能提取接口，使用正则解析兜底");
        return WordParser.parseInterfacesFromText(docContent);
    }

    /**
     * 获取示例接口（兜底数据）
     */
    private static List<String> getExampleInterfaces() {
        List<String> interfaces = new ArrayList<>();
        interfaces.add("""
            POST /api/data/transfer
            接口名称：数据传输
            接口描述：将数据从一个部门传输到另一个部门
            参数：
              sourceDept: string:必填,来源部门名称
              targetDept: string:必填,目标部门名称
              dataType: 枚举(PERSONAL/ENTERPRISE/GOVERNMENT):必填,数据类型
              volumeMB: integer:必填,数据量(MB)
            响应：
              200：传输成功
              400：参数错误
              500：服务器错误
            """);
        interfaces.add("""
            GET /api/user/list
            接口名称：用户列表查询
            接口描述：分页查询用户列表
            参数：
              page: integer:可选,默认1,页码
              size: integer:可选,默认10,每页大小
              keyword: string:可选,搜索关键词
            响应：
              200：查询成功
            """);
        return interfaces;
    }

    /**
     * 生成测试用例（供外部调用，委托给AiService）
     */
    public static List<TestCase> generateTestCases(String interfaceDoc) {
        return AiService.generateTestCases(interfaceDoc);
    }

    /**
     * 导出到Excel（供外部调用，委托给ExcelExporter）
     */
    public static void exportToExcel(List<TestCase> testCases, String filePath) {
        ExcelExporter.exportToExcel(testCases, filePath);
    }

    /**
     * 导出到Excel（输出流，供外部调用，委托给ExcelExporter）
     */
    public static void exportToExcel(List<TestCase> testCases, java.io.OutputStream outputStream) {
        ExcelExporter.exportToExcel(testCases, outputStream);
    }

    /**
     * 初始化AI模型（供外部调用，委托给AiService）
     */
    public static void initModel() {
        AiService.initModel();
    }

    /**
     * 获取API密钥（供外部调用，委托给AiService）
     */
    public static String getApiKey() {
        return AiService.getApiKey();
    }
}
