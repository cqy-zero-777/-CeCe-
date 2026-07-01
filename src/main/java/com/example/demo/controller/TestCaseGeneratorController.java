package com.example.demo.controller;

import com.example.demo.DocumentProcessor;
import com.example.demo.SwaggerParser;
import com.example.demo.TestCase;
import com.example.demo.dto.ErrorResponse;
import com.example.demo.service.AiService;
import com.example.demo.service.BatchScanService;
import com.example.demo.service.ExcelExporter;
import com.example.demo.service.HtmlReportGenerator;
import com.example.demo.service.ScriptGenerator;
import com.example.demo.service.WordParser;
import com.example.demo.util.DownloadHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 测试用例生成器Web控制器
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class TestCaseGeneratorController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 首页 - 返回前端页面（自动重定向到静态资源）
     */
    @GetMapping("/")
    public ResponseEntity<Void> index() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/index.html")).build();
    }

    /**
     * 上传文件并生成测试用例（Excel下载）
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "readRangeType", defaultValue = "all") String readRangeType,
            @RequestParam(value = "startPage", defaultValue = "1") int startPage,
            @RequestParam(value = "endPage", defaultValue = "50") int endPage,
            @RequestParam(value = "chapterRange", required = false) String chapterRange,
            @RequestParam(value = "outputPath", required = false) String outputPath) {
        try {
            log.info("开始处理文件: {}, 读取范围: {}, 页码: {}-{}, 章节: {}", 
                    file.getOriginalFilename(), readRangeType, startPage, endPage, chapterRange);
            DocumentProcessor.initModel();
            AiService.resetCounter(); // 重置计数器，确保编号从001开始不重置
            if (AiService.getModel() == null) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ErrorResponse.badRequest("AI模型未初始化", "请配置 DASHSCOPE_API_KEY / DEEPSEEK_API_KEY / AI_API_KEY 环境变量"));
            }

            String tempDir = System.getProperty("java.io.tmpdir");
            String tempPath = tempDir + File.separator + "testcase_upload_" + System.currentTimeMillis();
            Files.createDirectories(Paths.get(tempPath));

            String filePath = tempPath + File.separator + file.getOriginalFilename();
            file.transferTo(new File(filePath));

            List<String> interfaces;
            String fileName = file.getOriginalFilename().toLowerCase();

            if (fileName.endsWith(".docx")) {
                if ("page".equals(readRangeType)) {
                    interfaces = DocumentProcessor.extractFromDocx(filePath, startPage, endPage);
                } else if ("chapter".equals(readRangeType) && chapterRange != null && !chapterRange.isEmpty()) {
                    List<String> chapterTitles = parseChapterRange(chapterRange);
                    interfaces = DocumentProcessor.extractFromDocxByChapter(filePath, chapterTitles);
                } else {
                    interfaces = DocumentProcessor.extractFromDocx(filePath);
                }
            } else if (fileName.endsWith(".json") || fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                interfaces = SwaggerParser.parseSwagger(filePath);
            } else if (fileName.endsWith(".pdf")) {
                String pdfContent = "page".equals(readRangeType) ?
                        com.example.demo.service.PdfParser.readPdf(filePath, startPage, endPage) :
                        com.example.demo.service.PdfParser.readPdf(filePath);
                interfaces = com.example.demo.service.PdfParser.extractInterfaces(pdfContent);
            } else {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ErrorResponse.badRequest("不支持的文件格式", "仅支持 .docx、.pdf、.json、.yaml、.yml 格式"));
            }

            if (interfaces.isEmpty()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ErrorResponse.noInterfacesFound());
            }

            List<TestCase> allTestCases = new ArrayList<>();
            for (String api : interfaces) {
                try {
                    List<TestCase> testCases = DocumentProcessor.generateTestCases(api);
                    allTestCases.addAll(testCases);
                } catch (Exception e) {
                    log.warn("生成用例失败: {}", e.getMessage());
                }
            }

            if (allTestCases.isEmpty()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ErrorResponse.testCaseGenError("测试用例生成失败", "未能生成任何测试用例，请检查接口文档格式"));
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            DocumentProcessor.exportToExcel(allTestCases, outputStream);

            Files.walk(Paths.get(tempPath))
                    .map(Path::toFile)
                    .forEach(File::delete);

            log.info("文件处理完成，生成 {} 条测试用例", allTestCases.size());

            String outputFileName = "测试用例_" + LocalDateTime.now().format(DATE_FORMATTER) + ".xlsx";
            
            if (outputPath != null && !outputPath.trim().isEmpty()) {
                Path path = Paths.get(outputPath);
                if (Files.isDirectory(path)) {
                    path = path.resolve(outputFileName);
                } else if (!outputPath.toLowerCase().endsWith(".xlsx")) {
                    path = Paths.get(outputPath + ".xlsx");
                }
                Files.write(path, outputStream.toByteArray());
                Map<String, Object> result = new HashMap<>();
                result.put("message", "测试用例已生成");
                result.put("path", path.toString());
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(result);
            }

            return DownloadHelper.fileDownload(
                    outputStream.toByteArray(),
                    outputFileName,
                    MediaType.APPLICATION_OCTET_STREAM);

        } catch (com.example.demo.exception.DocumentParseException e) {
            log.warn("文档解析错误: {} - {}", e.getErrorCode(), e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.badRequest(e.getErrorCode(), "文档解析失败", e.getMessage()));
        } catch (com.example.demo.exception.AIServiceException e) {
            log.error("AI服务调用失败: {} - {}", e.getErrorCode(), e.getMessage(), e);
            return ResponseEntity.status(503)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.aiServiceError("AI服务调用失败", e.getMessage()));
        } catch (com.example.demo.exception.TestCaseGenerationException e) {
            log.error("测试用例生成失败: {} - {}", e.getErrorCode(), e.getMessage(), e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.testCaseGenError("测试用例生成失败", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.badRequest(e.getMessage()));
        } catch (IOException e) {
            log.error("文件操作失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.serverError("文件操作失败", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("运行时错误: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.serverError("系统运行错误", e.getMessage()));
        } catch (Exception e) {
            log.error("未知错误: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.serverError("服务器内部错误", e.getMessage()));
        }
    }

    /**
     * 通过Swagger URL生成测试用例
     */
    @PostMapping("/generate-from-url")
    public ResponseEntity<?> generateFromUrl(@RequestParam("url") String url) {
        try {
            log.info("开始处理URL: {}", url);
            DocumentProcessor.initModel();
            AiService.resetCounter(); // 重置计数器，确保编号从001开始不重置
            if (AiService.getModel() == null) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ErrorResponse.badRequest("AI模型未初始化", "请配置 DASHSCOPE_API_KEY / DEEPSEEK_API_KEY / AI_API_KEY 环境变量"));
            }

            List<String> interfaces = SwaggerParser.parseSwagger(url);

            if (interfaces.isEmpty()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ErrorResponse.badRequest("未提取到接口定义", "无法从URL解析出API接口定义"));
            }

            List<TestCase> allTestCases = new ArrayList<>();
            for (String api : interfaces) {
                List<TestCase> testCases = DocumentProcessor.generateTestCases(api);
                allTestCases.addAll(testCases);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            DocumentProcessor.exportToExcel(allTestCases, outputStream);

            log.info("URL处理完成，生成 {} 条测试用例", allTestCases.size());

            String fileName = "测试用例_" + LocalDateTime.now().format(DATE_FORMATTER) + ".xlsx";
            return DownloadHelper.fileDownload(
                    outputStream.toByteArray(),
                    fileName,
                    MediaType.APPLICATION_OCTET_STREAM);

        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.badRequest(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("生成测试用例失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.serverError("生成失败", e.getMessage()));
        } catch (Exception e) {
            log.error("未知错误: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.serverError("服务器内部错误", e.getMessage()));
        }
    }

    /**
     * 通过自然语言或上传文件生成测试用例（支持自定义测试要求）
     */
    @PostMapping("/generate-from-text")
    public ResponseEntity<?> generateFromText(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "interfaceDoc", required = false) String interfaceDoc,
            @RequestParam(value = "requirements", required = false) String requirements,
            @RequestParam(value = "outputPath", required = false) String outputPath,
            @RequestParam(value = "docType", defaultValue = "interface") String docType,
            @RequestParam(value = "readRangeType", defaultValue = "all") String readRangeType,
            @RequestParam(value = "startPage", defaultValue = "1") int startPage,
            @RequestParam(value = "endPage", defaultValue = "50") int endPage,
            @RequestParam(value = "chapterRange", required = false) String chapterRange) {
        try {
            log.info("开始处理自然语言请求，文件: {}，接口描述长度: {}，自定义要求: {}，文档类型: {}，读取范围: {}", 
                    file != null ? file.getOriginalFilename() : "无", 
                    interfaceDoc != null ? interfaceDoc.length() : 0, 
                    requirements != null ? "有" : "无",
                    docType,
                    readRangeType);
            DocumentProcessor.initModel();

            if (AiService.getModel() == null) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ErrorResponse.badRequest("AI模型未初始化", "请配置 DASHSCOPE_API_KEY / DEEPSEEK_API_KEY / AI_API_KEY 环境变量"));
            }

            List<TestCase> allTestCases = new ArrayList<>();

            if ("requirement".equals(docType)) {
                log.info("需求文档生成测试用例模式");
                
                if (file != null && !file.isEmpty()) {
                    String tempDir = System.getProperty("java.io.tmpdir");
                    String tempPath = tempDir + File.separator + "testcase_upload_" + System.currentTimeMillis();
                    Files.createDirectories(Paths.get(tempPath));

                    String filePath = tempPath + File.separator + file.getOriginalFilename();
                    file.transferTo(new File(filePath));

                    // 按章节分段读取需求文档（支持指定页码范围和章节）
                    log.info("按章节分段读取需求文档... readRangeType={}", readRangeType);
                    List<WordParser.SectionContent> sections;
                    if ("page".equals(readRangeType)) {
                        log.info("使用页码范围: {} - {}", startPage, endPage);
                        sections = WordParser.readDocxBySections(filePath, startPage, endPage);
                    } else {
                        sections = WordParser.readDocxBySections(filePath);
                    }
                    
                    Files.walk(Paths.get(tempPath)).map(Path::toFile).forEach(File::delete);

                    if (sections.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(ErrorResponse.badRequest("文档内容为空或无法识别章节"));
                    }

                    log.info("识别到 {} 个章节/段落，开始逐段生成测试用例", sections.size());

                    // 按章节生成测试用例
                    allTestCases = AiService.generateTestCasesFromRequirementBySections(sections);
                    
                } else if (interfaceDoc != null && !interfaceDoc.trim().isEmpty()) {
                    // 自然语言输入的需求描述
                    log.info("使用自然语言需求描述生成测试用例");
                    WordParser.SectionContent singleSection = new WordParser.SectionContent("需求描述", interfaceDoc);
                    List<WordParser.SectionContent> sections = new ArrayList<>();
                    sections.add(singleSection);
                    allTestCases = AiService.generateTestCasesFromRequirementBySections(sections);
                } else if (requirements != null && !requirements.trim().isEmpty()) {
                    // 兼容旧的requirements参数
                    log.info("使用requirements参数生成测试用例");
                    WordParser.SectionContent singleSection = new WordParser.SectionContent("自定义要求", requirements);
                    List<WordParser.SectionContent> sections = new ArrayList<>();
                    sections.add(singleSection);
                    allTestCases = AiService.generateTestCasesFromRequirementBySections(sections);
                } else {
                    return ResponseEntity.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(ErrorResponse.badRequest("请上传需求文档或输入需求描述"));
                }
            } else {
                if (file != null && !file.isEmpty()) {
                    String tempDir = System.getProperty("java.io.tmpdir");
                    String tempPath = tempDir + File.separator + "testcase_upload_" + System.currentTimeMillis();
                    Files.createDirectories(Paths.get(tempPath));

                    String filePath = tempPath + File.separator + file.getOriginalFilename();
                    file.transferTo(new File(filePath));

                    List<String> interfaces;
                    String fileName = file.getOriginalFilename().toLowerCase();

                    if (fileName.endsWith(".docx")) {
                        if ("page".equals(readRangeType)) {
                            interfaces = DocumentProcessor.extractFromDocx(filePath, startPage, endPage);
                        } else if ("chapter".equals(readRangeType) && chapterRange != null && !chapterRange.isEmpty()) {
                            List<String> chapterTitles = parseChapterRange(chapterRange);
                            interfaces = DocumentProcessor.extractFromDocxByChapter(filePath, chapterTitles);
                        } else {
                            interfaces = DocumentProcessor.extractFromDocx(filePath);
                        }
                    } else if (fileName.endsWith(".json") || fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                        interfaces = SwaggerParser.parseSwagger(filePath);
                    } else if (fileName.endsWith(".pdf")) {
                        String pdfContent = "page".equals(readRangeType) ?
                                com.example.demo.service.PdfParser.readPdf(filePath, startPage, endPage) :
                                com.example.demo.service.PdfParser.readPdf(filePath);
                        interfaces = com.example.demo.service.PdfParser.extractInterfaces(pdfContent);
                    } else {
                        Files.walk(Paths.get(tempPath)).map(Path::toFile).forEach(File::delete);
                        return ResponseEntity.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(ErrorResponse.badRequest("不支持的文件格式", "仅支持 .docx、.pdf、.json、.yaml、.yml 格式"));
                    }

                    Files.walk(Paths.get(tempPath)).map(Path::toFile).forEach(File::delete);

                    if (interfaces.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(ErrorResponse.badRequest("未提取到接口定义", "文档中未识别到API接口定义"));
                    }

                    for (String api : interfaces) {
                        List<TestCase> testCases = AiService.generateTestCases(api, requirements);
                        allTestCases.addAll(testCases);
                    }
                } else if (interfaceDoc != null && !interfaceDoc.trim().isEmpty()) {
                    allTestCases = AiService.generateTestCases(interfaceDoc, requirements);
                } else {
                    return ResponseEntity.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(ErrorResponse.badRequest("请上传接口文档或输入接口描述"));
                }
            }

            if (allTestCases.isEmpty()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ErrorResponse.badRequest("未生成测试用例", "AI未能根据描述生成测试用例"));
            }

            String fileName = "测试用例_" + LocalDateTime.now().format(DATE_FORMATTER) + ".xlsx";
            
            if (outputPath != null && !outputPath.trim().isEmpty()) {
                Path path = Paths.get(outputPath);
                
                if (Files.isDirectory(path)) {
                    path = path.resolve(fileName);
                    outputPath = path.toString();
                } else if (!outputPath.toLowerCase().endsWith(".xlsx")) {
                    path = Paths.get(outputPath + ".xlsx");
                    outputPath = path.toString();
                }
                
                Files.createDirectories(path.getParent() != null ? path.getParent() : path.getRoot());
                
                // 根据文档类型选择导出方法
                if ("requirement".equals(docType)) {
                    ExcelExporter.exportRequirementTestCasesToExcel(allTestCases, path.toString());
                } else {
                    ExcelExporter.exportToExcel(allTestCases, path.toString());
                }
                
                log.info("测试用例已保存到指定路径: {}", outputPath);
                Map<String, Object> result = new HashMap<>();
                result.put("message", "测试用例生成成功");
                result.put("count", allTestCases.size());
                result.put("path", outputPath);
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(result);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            // 根据文档类型选择导出方法
            if ("requirement".equals(docType)) {
                ExcelExporter.exportRequirementTestCasesToExcel(allTestCases, outputStream);
            } else {
                ExcelExporter.exportToExcel(allTestCases, outputStream);
            }

            log.info("处理完成，生成 {} 条测试用例", allTestCases.size());

            return DownloadHelper.fileDownload(
                    outputStream.toByteArray(),
                    fileName,
                    MediaType.APPLICATION_OCTET_STREAM);

        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.badRequest(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("生成测试用例失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.serverError("生成失败", e.getMessage()));
        } catch (Exception e) {
            log.error("未知错误: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.serverError("服务器内部错误", e.getMessage()));
        }
    }

    /**
     * 批量扫描文件夹并生成报告
     */
    @PostMapping("/batch-scan")
    public ResponseEntity<?> batchScan(@RequestParam("folderPath") String folderPath) {
        try {
            log.info("开始批量扫描文件夹: {}", folderPath);

            BatchScanService.BatchResult result = BatchScanService.processBatch(folderPath);
            String html = HtmlReportGenerator.generateReport(result);

            log.info("批量扫描完成，处理 {} 个文件，生成 {} 条测试用例",
                    result.getTotalFiles(), result.getTotalTestCases());

            String reportName = "测试用例报告_" + LocalDateTime.now().format(DATE_FORMATTER) + ".html";
            return DownloadHelper.fileDownload(
                    html.getBytes("UTF-8"),
                    reportName,
                    MediaType.TEXT_HTML);

        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.badRequest(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("批量扫描失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.serverError("批量扫描失败", e.getMessage()));
        } catch (Exception e) {
            log.error("未知错误: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorResponse.serverError("服务器内部错误", e.getMessage()));
        }
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        DocumentProcessor.initModel();
        boolean aiReady = AiService.getModel() != null;
        Map<String, Object> result = new HashMap<>();
        result.put("message", "AI测试用例生成工具运行中");
        result.put("aiReady", aiReady);
        result.put("provider", aiReady ? AiService.getModelProviderName() : "未配置");
        return ResponseEntity.ok(result);
    }

    /**
     * 生成前端页面
     */
    private String generateHomePage() {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>AI TestCase Generator</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'SF Mono', 'Cascadia Code', 'JetBrains Mono', 'Fira Code', Menlo, monospace;
                        background: #0a0e1a;
                        min-height: 100vh;
                        color: #e0e0e0;
                    }
                    .bg-grid {
                        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
                        background-image:
                            linear-gradient(rgba(0, 200, 150, 0.03) 1px, transparent 1px),
                            linear-gradient(90deg, rgba(0, 200, 150, 0.03) 1px, transparent 1px);
                        background-size: 40px 40px;
                        pointer-events: none; z-index: 0;
                    }
                    .container { max-width: 960px; margin: 0 auto; padding: 30px 20px; position: relative; z-index: 1; }
                    .header {
                        text-align: center; margin-bottom: 32px;
                        border-bottom: 1px solid #1a2a3a; padding-bottom: 24px;
                    }
                    .header h1 {
                        font-size: 22px; font-weight: 600; color: #e0e0e0;
                        letter-spacing: 2px;
                    }
                    .header h1 span { color: #00c896; }
                    .header .sub {
                        font-size: 12px; color: #5a7a8a; margin-top: 6px;
                        letter-spacing: 1px;
                    }
                    .card {
                        background: #0f1a2a; border: 1px solid #1a2a3a; border-radius: 8px;
                        padding: 24px; margin-bottom: 20px;
                        box-shadow: 0 4px 24px rgba(0,0,0,0.3);
                    }
                    .nav-tabs {
                        display: flex; flex-wrap: wrap; gap: 2px;
                        border-bottom: 1px solid #1a2a3a; margin-bottom: 24px;
                        padding-bottom: 0;
                    }
                    .nav-tab {
                        padding: 10px 16px; font-size: 12px; color: #5a7a8a;
                        cursor: pointer; transition: all 0.2s; border-bottom: 2px solid transparent;
                        letter-spacing: 0.5px; user-select: none;
                    }
                    .nav-tab:hover { color: #b0c0d0; background: rgba(0,200,150,0.04); }
                    .nav-tab.active { color: #00c896; border-bottom-color: #00c896; }
                    .tab-content { display: none; }
                    .tab-content.active { display: block; }
                    .tab-content h2 {
                        font-size: 14px; color: #00c896; margin-bottom: 20px;
                        font-weight: 500; letter-spacing: 1px;
                    }
                    .form-group { margin-bottom: 18px; }
                    .form-group label {
                        display: block; font-size: 11px; color: #5a7a8a; margin-bottom: 6px;
                        text-transform: uppercase; letter-spacing: 1px;
                    }
                    .form-group input[type="file"] {
                        width: 100%; padding: 12px; background: #0a1525;
                        border: 1px solid #1a2a3a; border-radius: 4px; color: #b0c0d0;
                        font-size: 12px; cursor: pointer; font-family: inherit;
                        transition: border-color 0.2s;
                    }
                    .form-group input[type="file"]:hover { border-color: #00c896; }
                    .form-group input[type="text"],
                    .form-group input[type="number"],
                    .form-group input[type="url"],
                    .form-group textarea,
                    .form-group select {
                        width: 100%; padding: 10px 12px; background: #0a1525;
                        border: 1px solid #1a2a3a; border-radius: 4px; color: #e0e0e0;
                        font-size: 13px; transition: border-color 0.2s; font-family: inherit;
                    }
                    .form-group input:focus,
                    .form-group textarea:focus,
                    .form-group select:focus {
                        outline: none; border-color: #00c896; box-shadow: 0 0 0 1px rgba(0,200,150,0.15);
                    }
                    .form-group textarea { resize: vertical; }
                    .form-group select { cursor: pointer; }
                    .radio-group { display: flex; gap: 20px; flex-wrap: wrap; }
                    .radio-group label {
                        display: flex; align-items: center; gap: 6px; font-size: 12px;
                        color: #8a9aaa; text-transform: none; cursor: pointer; letter-spacing: 0;
                    }
                    .radio-group input[type="radio"] { accent-color: #00c896; }
                    .checkbox-group { display: flex; gap: 20px; flex-wrap: wrap; }
                    .checkbox-group label {
                        display: flex; align-items: center; gap: 6px; font-size: 12px;
                        color: #8a9aaa; text-transform: none; cursor: pointer; letter-spacing: 0;
                    }
                    .checkbox-group input[type="checkbox"] { accent-color: #00c896; }
                    .page-range { display: flex; gap: 10px; align-items: center; }
                    .page-range input { width: 80px; text-align: center; }
                    .page-range span { color: #5a7a8a; font-size: 14px; }
                    .btn {
                        width: 100%; padding: 12px 24px; background: #00c896;
                        border: none; border-radius: 4px; color: #0a0e1a; font-size: 13px;
                        font-weight: 600; cursor: pointer; font-family: inherit;
                        letter-spacing: 1px; transition: all 0.2s;
                    }
                    .btn:hover { background: #00e8a8; box-shadow: 0 0 20px rgba(0,200,150,0.2); }
                    .btn:active { transform: scale(0.98); }
                    .btn-secondary { background: transparent; border: 1px solid #00c896; color: #00c896; }
                    .btn-secondary:hover { background: rgba(0,200,150,0.1); }
                    .tips {
                        font-size: 11px; color: #4a6a7a; margin-top: 14px;
                        padding: 10px 12px; background: #0a1525; border-radius: 4px;
                        border: 1px solid #152535; line-height: 1.6;
                    }
                    .read-range-options { display: none; padding: 12px; background: #0a1525; border-radius: 4px; margin-bottom: 18px; }
                    .read-range-options.show { display: block; }
                    .loading {
                        display: none; text-align: center; padding: 16px; margin-top: 16px;
                        font-size: 12px; color: #00c896; letter-spacing: 1px;
                        border: 1px solid rgba(0,200,150,0.2); border-radius: 4px;
                    }
                    .loading.show { display: block; }
                    .loading::after {
                        content: ''; display: inline-block; width: 12px; height: 12px;
                        border: 2px solid rgba(0,200,150,0.2); border-top-color: #00c896;
                        border-radius: 50%; animation: spin 0.8s linear infinite;
                        margin-left: 8px; vertical-align: middle;
                    }
                    @keyframes spin { to { transform: rotate(360deg); } }
                    .error-box {
                        display: none; margin-top: 16px; padding: 14px;
                        background: rgba(255,60,60,0.08); border: 1px solid rgba(255,60,60,0.2);
                        border-radius: 4px; font-size: 12px;
                    }
                    .error-box.show { display: block; }
                    .error-box .error-title { color: #ff6b6b; font-weight: 600; margin-bottom: 4px; }
                    .error-box .error-detail { color: #cc5555; font-size: 11px; }
                    ::-webkit-scrollbar { width: 6px; }
                    ::-webkit-scrollbar-track { background: #0a1525; }
                    ::-webkit-scrollbar-thumb { background: #1a2a3a; border-radius: 3px; }
                    ::-webkit-scrollbar-thumb:hover { background: #2a3a4a; }
                </style>
            </head>
            <body>
                <div class="bg-grid"></div>
                <div class="container">
                    <div class="header">
                        <h1>&lt;AI <span>TestCase</span> Generator /&gt;</h1>
                        <div class="sub">~$ upload document &amp;&amp; generate test cases</div>
                    </div>

                    <div class="card">
                        <div class="nav-tabs">
                            <div class="nav-tab active" onclick="switchTab('interface-doc')">[1] 接口文档生成</div>
                            <div class="nav-tab" onclick="switchTab('requirement-doc')">[2] 需求文档生成</div>
                            <div class="nav-tab" onclick="switchTab('generate-script')">[3] 自动化脚本</div>
                            <div class="nav-tab" onclick="switchTab('file')">[4] 文件上传</div>
                            <div class="nav-tab" onclick="switchTab('url')">[5] URL方式</div>
                            <div class="nav-tab" onclick="switchTab('natural')">[6] 自然语言</div>
                            <div class="nav-tab" onclick="switchTab('batch')">[7] 批量扫描</div>
                        </div>

                        <!-- Tab1: 从接口文档生成用例 -->
                        <div id="interface-doc" class="tab-content active">
                            <h2># 从接口文档生成用例</h2>
                            <form id="interfaceDocForm" enctype="multipart/form-data">
                                <div class="form-group">
                                    <label>选择接口文档</label>
                                    <input type="file" name="file" accept=".docx,.json,.yaml,.yml" required>
                                </div>
                                <div class="form-group">
                                    <label>读取范围</label>
                                    <div class="radio-group">
                                        <label><input type="radio" name="readRangeType" value="all" checked onchange="toggleReadRangeOptions()">全部读取</label>
                                        <label><input type="radio" name="readRangeType" value="page" onchange="toggleReadRangeOptions()">指定页数</label>
                                        <label><input type="radio" name="readRangeType" value="chapter" onchange="toggleReadRangeOptions()">指定章节</label>
                                    </div>
                                </div>
                                <div id="readRangeOptions" class="read-range-options">
                                    <div class="form-group" id="pageRangeFields">
                                        <label>页码范围</label>
                                        <div class="page-range">
                                            <input type="number" name="startPage" placeholder="起始" value="1" min="1">
                                            <span>—</span>
                                            <input type="number" name="endPage" placeholder="结束" value="50" min="1">
                                        </div>
                                    </div>
                                    <div class="form-group" id="chapterRangeFields">
                                        <label>章节范围</label>
                                        <input type="text" name="chapterRange" placeholder="第1章-第3章">
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>输出路径（可选）</label>
                                    <input type="text" name="outputPath" placeholder="D:\\testcases\\接口测试.xlsx">
                                </div>
                                <button type="submit" class="btn">生成测试用例</button>
                            </form>
                            <div class="tips">[.docx] [.json] [.yaml] — 大型文档默认读取前50页，自动跳过封面/目录</div>
                        </div>

                        <!-- Tab2: 从需求文档生成用例 -->
                        <div id="requirement-doc" class="tab-content">
                            <h2># 从需求文档生成用例</h2>
                            <form id="requirementDocForm" enctype="multipart/form-data">
                                <div class="form-group">
                                    <label>选择需求文档</label>
                                    <input type="file" name="file" accept=".docx" required>
                                </div>
                                <div class="form-group">
                                    <label>读取范围</label>
                                    <div class="radio-group">
                                        <label><input type="radio" name="readRangeType" value="all" checked onchange="toggleReadRangeOptions2()">全部读取</label>
                                        <label><input type="radio" name="readRangeType" value="page" onchange="toggleReadRangeOptions2()">指定页数</label>
                                        <label><input type="radio" name="readRangeType" value="chapter" onchange="toggleReadRangeOptions2()">指定章节</label>
                                    </div>
                                </div>
                                <div id="readRangeOptions2" class="read-range-options">
                                    <div class="form-group" id="pageRangeFields2">
                                        <label>页码范围</label>
                                        <div class="page-range">
                                            <input type="number" name="startPage" placeholder="起始" value="1" min="1">
                                            <span>—</span>
                                            <input type="number" name="endPage" placeholder="结束" value="50" min="1">
                                        </div>
                                    </div>
                                    <div class="form-group" id="chapterRangeFields2">
                                        <label>章节范围</label>
                                        <input type="text" name="chapterRange" placeholder="第1章-第3章">
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>自定义测试要求（可选）</label>
                                    <textarea name="requirements" rows="3" placeholder="重点测试安全漏洞，增加SQL注入和XSS攻击测试场景"></textarea>
                                </div>
                                <div class="form-group">
                                    <label>输出路径（可选）</label>
                                    <input type="text" name="outputPath" placeholder="D:\\testcases\\需求测试.xlsx">
                                </div>
                                <button type="submit" class="btn">生成测试用例</button>
                            </form>
                            <div class="tips">[.docx] — 覆盖正常流程、异常流程、边界值、权限场景，自动识别章节</div>
                        </div>

                        <!-- Tab3: 生成自动化脚本 -->
                        <div id="generate-script" class="tab-content">
                            <h2># 生成自动化测试脚本</h2>
                            <form id="scriptGenForm" enctype="multipart/form-data">
                                <div class="form-group">
                                    <label>选择接口文档</label>
                                    <input type="file" name="file" accept=".docx,.json,.yaml,.yml" required>
                                </div>
                                <div class="form-group">
                                    <label>读取范围</label>
                                    <div class="radio-group">
                                        <label><input type="radio" name="readRangeType" value="all" checked onchange="toggleReadRangeOptions3()">全部读取</label>
                                        <label><input type="radio" name="readRangeType" value="page" onchange="toggleReadRangeOptions3()">指定页数</label>
                                        <label><input type="radio" name="readRangeType" value="chapter" onchange="toggleReadRangeOptions3()">指定章节</label>
                                    </div>
                                </div>
                                <div id="readRangeOptions3" class="read-range-options">
                                    <div class="form-group" id="pageRangeFields3">
                                        <label>页码范围</label>
                                        <div class="page-range">
                                            <input type="number" name="startPage" placeholder="起始" value="1" min="1">
                                            <span>—</span>
                                            <input type="number" name="endPage" placeholder="结束" value="50" min="1">
                                        </div>
                                    </div>
                                    <div class="form-group" id="chapterRangeFields3">
                                        <label>章节范围</label>
                                        <input type="text" name="chapterRange" placeholder="第1章-第3章">
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>API Base URL</label>
                                    <input type="text" name="baseUrl" placeholder="http://localhost:8080" value="http://localhost:8080">
                                </div>
                                <div class="form-group">
                                    <label>输出路径（可选）</label>
                                    <input type="text" name="outputPath" placeholder="D:\\scripts\\api-automation">
                                </div>
                                <button type="submit" class="btn btn-secondary">生成自动化脚本</button>
                            </form>
                            <div class="tips">[TestNG] + [RestAssured] + [Allure] — 自动打包为 ZIP 下载</div>
                        </div>

                        <!-- Tab4: 文件上传 -->
                        <div id="file" class="tab-content">
                            <h2># 快速文件上传</h2>
                            <form id="quickUploadForm" enctype="multipart/form-data">
                                <div class="form-group">
                                    <label>选择文档</label>
                                    <input type="file" name="file" accept=".docx,.json,.yaml,.yml" required>
                                </div>
                                <div class="form-group">
                                    <label>文档类型</label>
                                    <select name="docType">
                                        <option value="interface">接口文档</option>
                                        <option value="requirement">需求文档</option>
                                    </select>
                                </div>
                                <div class="form-group">
                                    <label>输出路径（可选）</label>
                                    <input type="text" name="outputPath" placeholder="D:\\testcases\\测试结果.xlsx">
                                </div>
                                <button type="submit" class="btn">快速生成</button>
                            </form>
                            <div class="tips">默认读取全部内容，大型文档请使用 [1] 或 [2] 指定范围</div>
                        </div>

                        <!-- Tab5: URL方式 -->
                        <div id="url" class="tab-content">
                            <h2># 通过URL生成测试用例</h2>
                            <form id="urlForm">
                                <div class="form-group">
                                    <label>Swagger / OpenAPI 文档 URL</label>
                                    <input type="url" name="url" placeholder="http://localhost:8080/v2/api-docs 或 https://petstore.swagger.io/v2/swagger.json" required style="width: 100%;">
                                </div>
                                <div class="form-group">
                                    <label>输出路径（可选）</label>
                                    <input type="text" name="outputPath" placeholder="D:\\testcases\\swagger测试.xlsx">
                                </div>
                                <button type="submit" class="btn">从URL生成</button>
                            </form>
                            <div class="tips">支持 Swagger JSON / OpenAPI YAML 在线文档，需可公开访问</div>
                        </div>

                        <!-- Tab6: 自然语言 -->
                        <div id="natural" class="tab-content">
                            <h2># 自然语言输入</h2>
                            <form id="naturalForm">
                                <div class="form-group">
                                    <label>文档类型</label>
                                    <select name="docType">
                                        <option value="interface">接口文档（输入接口描述）</option>
                                        <option value="requirement">需求文档（输入功能需求）</option>
                                    </select>
                                </div>
                                <div class="form-group">
                                    <label>输入内容</label>
                                    <textarea name="interfaceDoc" rows="8" placeholder="POST /api/user/login&#10;接口名称：用户登录&#10;参数：username(必填)、password(必填)"></textarea>
                                </div>
                                <div class="form-group">
                                    <label>自定义测试要求（可选）</label>
                                    <textarea name="requirements" rows="3" placeholder="重点测试安全漏洞，增加SQL注入和XSS攻击测试场景"></textarea>
                                </div>
                                <div class="form-group">
                                    <label>输出路径（可选）</label>
                                    <input type="text" name="outputPath" placeholder="D:\\testcases\\自定义测试.xlsx">
                                </div>
                                <button type="submit" class="btn">生成测试用例</button>
                            </form>
                            <div class="tips">输入接口描述或功能需求，AI 自动生成用例，适合快速验证</div>
                        </div>

                        <!-- Tab7: 批量扫描 -->
                        <div id="batch" class="tab-content">
                            <h2># 批量扫描文件夹</h2>
                            <form id="batchForm">
                                <div class="form-group">
                                    <label>文件夹路径</label>
                                    <input type="text" name="folderPath" placeholder="D:\\api-docs 或 C:\\project\\swagger" required style="width: 100%;">
                                </div>
                                <div class="form-group">
                                    <label>扫描文件类型</label>
                                    <div class="checkbox-group">
                                        <label><input type="checkbox" name="scanDocx" checked>.docx</label>
                                        <label><input type="checkbox" name="scanJson" checked>.json</label>
                                        <label><input type="checkbox" name="scanYaml" checked>.yaml/.yml</label>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>输出报告路径（可选）</label>
                                    <input type="text" name="outputPath" placeholder="D:\\reports\\批量测试报告.html">
                                </div>
                                <button type="submit" class="btn btn-secondary">批量扫描</button>
                            </form>
                            <div class="tips">扫描文件夹内所有文档，自动生成测试用例并输出 HTML 报告</div>
                        </div>

                        <div id="loading" class="loading">正在处理，请稍候</div>
                        <div id="errorBox" class="error-box">
                            <div class="error-title" id="errorTitle"></div>
                            <div class="error-detail" id="errorDetail"></div>
                        </div>
                    </div>
                </div>

                <script>
                    function switchTab(tabId) {
                        document.querySelectorAll('.nav-tab').forEach(el => el.classList.remove('active'));
                        document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
                        event.target.classList.add('active');
                        document.getElementById(tabId).classList.add('active');
                        document.getElementById('loading').classList.remove('show');
                        document.getElementById('errorBox').classList.remove('show');
                    }
                    function toggleReadRangeOptions() {
                        const options = document.getElementById('readRangeOptions');
                        const pageFields = document.getElementById('pageRangeFields');
                        const chapterFields = document.getElementById('chapterRangeFields');
                        const type = document.querySelector('input[name="readRangeType"]:checked').value;
                        if (type === 'all') { options.classList.remove('show'); }
                        else { options.classList.add('show'); pageFields.style.display = type === 'page' ? 'block' : 'none'; chapterFields.style.display = type === 'chapter' ? 'block' : 'none'; }
                    }
                    function toggleReadRangeOptions2() {
                        const options = document.getElementById('readRangeOptions2');
                        const pageFields = document.getElementById('pageRangeFields2');
                        const chapterFields = document.getElementById('chapterRangeFields2');
                        const type = document.querySelector('#requirementDocForm input[name="readRangeType"]:checked').value;
                        if (type === 'all') { options.classList.remove('show'); }
                        else { options.classList.add('show'); pageFields.style.display = type === 'page' ? 'block' : 'none'; chapterFields.style.display = type === 'chapter' ? 'block' : 'none'; }
                    }
                    function toggleReadRangeOptions3() {
                        const options = document.getElementById('readRangeOptions3');
                        const pageFields = document.getElementById('pageRangeFields3');
                        const chapterFields = document.getElementById('chapterRangeFields3');
                        const type = document.querySelector('#scriptGenForm input[name="readRangeType"]:checked').value;
                        if (type === 'all') { options.classList.remove('show'); }
                        else { options.classList.add('show'); pageFields.style.display = type === 'page' ? 'block' : 'none'; chapterFields.style.display = type === 'chapter' ? 'block' : 'none'; }
                    }
                    function showError(title, detail) {
                        document.getElementById('errorTitle').textContent = title;
                        document.getElementById('errorDetail').textContent = detail || '';
                        document.getElementById('errorBox').classList.add('show');
                    }
                    function hideError() { document.getElementById('errorBox').classList.remove('show'); }
                    function handleSubmit(formId, url) {
                        document.getElementById(formId).addEventListener('submit', async function(e) {
                            e.preventDefault(); hideError(); document.getElementById('loading').classList.add('show');
                            const formData = new FormData(this);
                            try {
                                const response = await fetch(url, { method: 'POST', body: formData });
                                if (response.headers.get('content-type')?.includes('application/json')) {
                                    const error = await response.json(); showError(error.message, error.detail); return;
                                }
                                if (!response.ok) { showError('请求失败', 'HTTP状态码: ' + response.status); return; }
                                const blob = await response.blob();
                                const dlUrl = window.URL.createObjectURL(blob);
                                const a = document.createElement('a'); a.href = dlUrl;
                                a.download = response.headers.get('Content-Disposition')?.split('filename=')[1] || 'result.xlsx';
                                document.body.appendChild(a); a.click(); window.URL.revokeObjectURL(dlUrl);
                            } catch (error) { showError('网络错误', error.message); }
                            finally { document.getElementById('loading').classList.remove('show'); }
                        });
                    }

                    document.getElementById('quickUploadForm').addEventListener('submit', async function(e) {
                        e.preventDefault(); hideError(); document.getElementById('loading').classList.add('show');
                        const formData = new FormData(this);
                        try {
                            const response = await fetch('/api/generate-from-text', { method: 'POST', body: formData });
                            if (response.headers.get('content-type')?.includes('application/json')) {
                                const data = await response.json();
                                if (data.message && !data.errorCode) { alert(data.message + (data.path ? '，已保存到：' + data.path : '')); }
                                else if (data.errorCode || data.code >= 400) { showError(data.message || '生成失败', data.detail || ''); }
                                document.getElementById('loading').classList.remove('show'); return;
                            }
                            if (!response.ok) { showError('请求失败', 'HTTP状态码: ' + response.status); document.getElementById('loading').classList.remove('show'); return; }
                            const blob = await response.blob(); const dlUrl = window.URL.createObjectURL(blob);
                            const a = document.createElement('a'); a.href = dlUrl;
                            a.download = response.headers.get('Content-Disposition')?.split('filename=')[1] || '测试用例.xlsx';
                            document.body.appendChild(a); a.click(); window.URL.revokeObjectURL(dlUrl);
                            alert('测试用例已生成！');
                        } catch (error) { showError('网络错误', error.message); }
                        finally { document.getElementById('loading').classList.remove('show'); }
                    });

                    document.getElementById('urlForm').addEventListener('submit', async function(e) {
                        e.preventDefault(); hideError(); document.getElementById('loading').classList.add('show');
                        const formData = new FormData(this);
                        try {
                            const response = await fetch('/api/generate-from-url', { method: 'POST', body: formData });
                            if (response.headers.get('content-type')?.includes('application/json')) {
                                const data = await response.json(); showError(data.message, data.detail || '');
                                document.getElementById('loading').classList.remove('show'); return;
                            }
                            if (!response.ok) { showError('请求失败', 'HTTP状态码: ' + response.status); document.getElementById('loading').classList.remove('show'); return; }
                            const blob = await response.blob(); const dlUrl = window.URL.createObjectURL(blob);
                            const a = document.createElement('a'); a.href = dlUrl;
                            a.download = response.headers.get('Content-Disposition')?.split('filename=')[1] || 'swagger测试用例.xlsx';
                            document.body.appendChild(a); a.click(); window.URL.revokeObjectURL(dlUrl);
                            alert('Swagger测试用例已生成！');
                        } catch (error) { showError('网络错误', error.message); }
                        finally { document.getElementById('loading').classList.remove('show'); }
                    });

                    document.getElementById('naturalForm').addEventListener('submit', async function(e) {
                        e.preventDefault(); hideError(); document.getElementById('loading').classList.add('show');
                        const formData = new FormData(this);
                        try {
                            const response = await fetch('/api/generate-from-text', { method: 'POST', body: formData });
                            if (response.headers.get('content-type')?.includes('application/json')) {
                                const data = await response.json();
                                if (data.message && !data.errorCode) { alert(data.message + (data.path ? '，已保存到：' + data.path : '')); }
                                else if (data.errorCode || data.code >= 400) { showError(data.message || '生成失败', data.detail || ''); }
                                document.getElementById('loading').classList.remove('show'); return;
                            }
                            if (!response.ok) { showError('请求失败', 'HTTP状态码: ' + response.status); document.getElementById('loading').classList.remove('show'); return; }
                            const blob = await response.blob(); const dlUrl = window.URL.createObjectURL(blob);
                            const a = document.createElement('a'); a.href = dlUrl;
                            a.download = response.headers.get('Content-Disposition')?.split('filename=')[1] || '测试用例.xlsx';
                            document.body.appendChild(a); a.click(); window.URL.revokeObjectURL(dlUrl);
                            alert('测试用例已生成！');
                        } catch (error) { showError('网络错误', error.message); }
                        finally { document.getElementById('loading').classList.remove('show'); }
                    });

                    document.getElementById('scriptGenForm').addEventListener('submit', async function(e) {
                        e.preventDefault(); hideError(); document.getElementById('loading').classList.add('show');
                        const formData = new FormData(this);
                        try {
                            const response = await fetch('/api/generate-script', { method: 'POST', body: formData });
                            if (response.headers.get('content-type')?.includes('application/json')) {
                                const data = await response.json();
                                if (data.message && !data.errorCode) { alert(data.message + (data.path ? '，已保存到：' + data.path : '')); }
                                else if (data.errorCode || data.code >= 400) { showError(data.message || '生成失败', data.detail || ''); }
                                document.getElementById('loading').classList.remove('show'); return;
                            }
                            if (!response.ok) { showError('生成脚本失败', 'HTTP状态码: ' + response.status); document.getElementById('loading').classList.remove('show'); return; }
                            const blob = await response.blob(); const dlUrl = window.URL.createObjectURL(blob);
                            const a = document.createElement('a'); a.href = dlUrl;
                            a.download = response.headers.get('Content-Disposition')?.split('filename=')[1] || 'api-automation.zip';
                            document.body.appendChild(a); a.click(); window.URL.revokeObjectURL(dlUrl);
                            alert('自动化测试脚本已生成！');
                        } catch (error) { showError('网络错误', error.message); }
                        finally { document.getElementById('loading').classList.remove('show'); }
                    });

                    document.getElementById('batchForm').addEventListener('submit', async function(e) {
                        e.preventDefault(); hideError(); document.getElementById('loading').classList.add('show');
                        const formData = new FormData(this);
                        try {
                            const response = await fetch('/api/batch-scan', { method: 'POST', body: formData });
                            if (response.headers.get('content-type')?.includes('application/json')) {
                                const data = await response.json(); showError(data.message || '批量扫描失败', data.detail || '');
                                document.getElementById('loading').classList.remove('show'); return;
                            }
                            if (!response.ok) { showError('批量扫描失败', 'HTTP状态码: ' + response.status); document.getElementById('loading').classList.remove('show'); return; }
                            const blob = await response.blob(); const dlUrl = window.URL.createObjectURL(blob);
                            const a = document.createElement('a'); a.href = dlUrl;
                            a.download = response.headers.get('Content-Disposition')?.split('filename=')[1] || '批量测试报告.html';
                            document.body.appendChild(a); a.click(); window.URL.revokeObjectURL(dlUrl);
                            alert('批量扫描报告已生成！');
                        } catch (error) { showError('网络错误', error.message); }
                        finally { document.getElementById('loading').classList.remove('show'); }
                    });

                    document.getElementById('interfaceDocForm').addEventListener('submit', async function(e) {
                        e.preventDefault(); hideError(); document.getElementById('loading').classList.add('show');
                        const formData = new FormData(this);
                        try {
                            const response = await fetch('/api/generate', { method: 'POST', body: formData });
                            if (response.headers.get('content-type')?.includes('application/json')) {
                                const data = await response.json();
                                if (data.message && !data.errorCode) { alert(data.message + (data.path ? '，已保存到：' + data.path : '')); }
                                else if (data.errorCode || data.code >= 400) { showError(data.message || '生成失败', data.detail || ''); }
                                document.getElementById('loading').classList.remove('show'); return;
                            }
                            if (!response.ok) { showError('请求失败', 'HTTP状态码: ' + response.status); return; }
                            const blob = await response.blob(); const dlUrl = window.URL.createObjectURL(blob);
                            const a = document.createElement('a'); a.href = dlUrl;
                            a.download = response.headers.get('Content-Disposition')?.split('filename=')[1] || '测试用例.xlsx';
                            document.body.appendChild(a); a.click(); window.URL.revokeObjectURL(dlUrl);
                        } catch (error) { showError('网络错误', error.message); }
                        finally { document.getElementById('loading').classList.remove('show'); }
                    });

                    document.getElementById('requirementDocForm').addEventListener('submit', async function(e) {
                        e.preventDefault(); hideError(); document.getElementById('loading').classList.add('show');
                        const formData = new FormData(this);
                        formData.append('docType', 'requirement');
                        try {
                            const response = await fetch('/api/generate-from-text', { method: 'POST', body: formData });
                            if (response.headers.get('content-type')?.includes('application/json')) {
                                const data = await response.json();
                                if (data.message && !data.errorCode) { alert(data.message + (data.path ? '，已保存到：' + data.path : '')); }
                                else if (data.errorCode || data.code >= 400) { showError(data.message || '生成失败', data.detail || ''); }
                                document.getElementById('loading').classList.remove('show'); return;
                            }
                            if (!response.ok) { showError('请求失败', 'HTTP状态码: ' + response.status); return; }
                            const blob = await response.blob(); const dlUrl = window.URL.createObjectURL(blob);
                            const a = document.createElement('a'); a.href = dlUrl;
                            a.download = response.headers.get('Content-Disposition')?.split('filename=')[1] || '测试用例.xlsx';
                            document.body.appendChild(a); a.click(); window.URL.revokeObjectURL(dlUrl);
                        } catch (error) { showError('网络错误', error.message); }
                        finally { document.getElementById('loading').classList.remove('show'); }
                    });

                    document.getElementById('scriptGenForm').addEventListener('submit', async function(e) {
                        e.preventDefault(); hideError(); document.getElementById('loading').classList.add('show');
                        const formData = new FormData(this);
                        try {
                            const response = await fetch('/api/generate-script', { method: 'POST', body: formData });
                            if (response.headers.get('content-type')?.includes('application/json')) {
                                const data = await response.json();
                                if (data.message && !data.errorCode) { alert(data.message + (data.path ? '，已保存到：' + data.path : '')); }
                                else if (data.errorCode || data.code >= 400) { showError(data.message || '生成失败', data.detail || ''); }
                                document.getElementById('loading').classList.remove('show'); return;
                            }
                            if (!response.ok) { showError('请求失败', 'HTTP状态码: ' + response.status); return; }
                            const blob = await response.blob(); const dlUrl = window.URL.createObjectURL(blob);
                            const a = document.createElement('a'); a.href = dlUrl;
                            a.download = response.headers.get('Content-Disposition')?.split('filename=')[1] || '测试脚本.zip';
                            document.body.appendChild(a); a.click(); window.URL.revokeObjectURL(dlUrl);
                        } catch (error) { showError('网络错误', error.message); }
                        finally { document.getElementById('loading').classList.remove('show'); }
                    });
                </script>
            </body>
            </html>
            """;
        }

    /**
     * 生成自动化测试脚本（返回ZIP文件下载）
     */
    @PostMapping("/generate-script")
    public ResponseEntity<?> generateScript(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "baseUrl", defaultValue = "http://localhost:8080") String baseUrl,
            @RequestParam(value = "readRangeType", defaultValue = "all") String readRangeType,
            @RequestParam(value = "startPage", defaultValue = "1") int startPage,
            @RequestParam(value = "endPage", defaultValue = "50") int endPage,
            @RequestParam(value = "chapterRange", required = false) String chapterRange,
            @RequestParam(value = "outputPath", required = false) String outputPath) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest("请上传文件"));
        }

        try {
            DocumentProcessor.initModel();
            AiService.resetCounter(); // 重置计数器，确保编号从001开始不重置
            if (AiService.getModel() == null) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ErrorResponse.badRequest("AI模型未初始化", "请配置 DASHSCOPE_API_KEY / DEEPSEEK_API_KEY / AI_API_KEY 环境变量"));
            }

            String fileName = file.getOriginalFilename();
            String tempFilePath = System.getProperty("java.io.tmpdir") + File.separator + fileName;
            
            file.transferTo(new File(tempFilePath));
            
            List<String> interfaces;
            String fileExt = fileName.toLowerCase();
            
            if (fileExt.endsWith(".docx")) {
                if ("page".equals(readRangeType)) {
                    interfaces = WordParser.extractFromDocx(tempFilePath, startPage, endPage);
                } else if ("chapter".equals(readRangeType) && chapterRange != null && !chapterRange.isEmpty()) {
                    List<String> chapterTitles = parseChapterRange(chapterRange);
                    interfaces = WordParser.extractFromDocxByChapter(tempFilePath, chapterTitles);
                } else {
                    interfaces = WordParser.extractFromDocx(tempFilePath);
                }
            } else if (fileExt.endsWith(".json") || fileExt.endsWith(".yaml") || fileExt.endsWith(".yml")) {
                interfaces = SwaggerParser.parseSwagger(tempFilePath);
            } else {
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("不支持的文件格式", "仅支持 .docx、.pdf、.json、.yaml、.yml 格式"));
            }
            
            log.info("共提取到 {} 个接口", interfaces.size());
            
            List<TestCase> allTestCases = new ArrayList<>();
            for (String api : interfaces) {
                List<TestCase> testCases = AiService.generateTestCases(api);
                allTestCases.addAll(testCases);
            }
            log.info("共生成 {} 个测试用例", allTestCases.size());
            
            byte[] zipBytes = ScriptGenerator.generateScriptAsZip(allTestCases, baseUrl);
            
            String downloadFileName = "api-automation-" + LocalDateTime.now().format(DATE_FORMATTER) + ".zip";
            
            if (outputPath != null && !outputPath.trim().isEmpty()) {
                Path path = Paths.get(outputPath);
                if (Files.isDirectory(path)) {
                    path = path.resolve(downloadFileName);
                } else if (!outputPath.toLowerCase().endsWith(".zip")) {
                    path = Paths.get(outputPath + ".zip");
                }
                Files.write(path, zipBytes);
                Map<String, Object> result = new HashMap<>();
                result.put("message", "自动化测试脚本已生成");
                result.put("path", path.toString());
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(result);
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, DownloadHelper.buildContentDisposition(downloadFileName));
            headers.setContentLength(zipBytes.length);
            
            return ResponseEntity.ok().headers(headers).body(zipBytes);
            
        } catch (IOException e) {
            log.error("生成脚本失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ErrorResponse.serverError("生成脚本失败", e.getMessage()));
        }
    }
    
    /**
     * 解析章节范围字符串，返回章节标题列表
     */
    private List<String> parseChapterRange(String chapterRange) {
        List<String> chapterTitles = new ArrayList<>();
        
        if (chapterRange == null || chapterRange.isEmpty()) {
            return chapterTitles;
        }
        
        String[] parts = chapterRange.split("-");
        if (parts.length == 2) {
            String start = parts[0].trim();
            String end = parts[1].trim();
            
            if (start.startsWith("第") && end.startsWith("第")) {
                try {
                    int startNum = Integer.parseInt(start.replace("第", "").replace("章", "").replace("节", ""));
                    int endNum = Integer.parseInt(end.replace("第", "").replace("章", "").replace("节", ""));
                    
                    for (int i = startNum; i <= endNum; i++) {
                        chapterTitles.add("第" + i + "章");
                    }
                } catch (NumberFormatException e) {
                    chapterTitles.add(start);
                    chapterTitles.add(end);
                }
            } else {
                chapterTitles.add(start);
                chapterTitles.add(end);
            }
        } else {
            chapterTitles.add(chapterRange.trim());
        }
        
        return chapterTitles;
    }
}