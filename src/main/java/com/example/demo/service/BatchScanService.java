package com.example.demo.service;

import com.example.demo.DocumentProcessor;
import com.example.demo.SwaggerParser;
import com.example.demo.TestCase;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * 批量扫描服务
 * 支持扫描整个文件夹，自动找到所有.docx、.swagger.json、.yaml文件
 */
@Slf4j
public class BatchScanService {

    /**
     * 扫描文件夹，返回所有符合条件的文件
     */
    public static List<FileInfo> scanFolder(String folderPath) throws IOException {
        List<FileInfo> files = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(Paths.get(folderPath))) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        FileInfo.FileType type = identifyFileType(fileName);
                        if (type != FileInfo.FileType.UNKNOWN) {
                            files.add(new FileInfo(path.toString(), type));
                        }
                    });
        }

        log.info("扫描完成，共找到 {} 个文件", files.size());
        return files;
    }

    /**
     * 识别文件类型
     */
    private static FileInfo.FileType identifyFileType(String fileName) {
        if (fileName.endsWith(".docx")) {
            return FileInfo.FileType.DOCX;
        } else if (fileName.endsWith(".swagger.json") || fileName.endsWith("-api.json")) {
            return FileInfo.FileType.SWAGGER_JSON;
        } else if (fileName.endsWith(".json")) {
            // 判断是否是Swagger JSON
            return FileInfo.FileType.JSON;
        } else if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            return FileInfo.FileType.YAML;
        }
        return FileInfo.FileType.UNKNOWN;
    }

    /**
     * 批量处理所有文件
     */
    public static BatchResult processBatch(String folderPath) throws IOException {
        DocumentProcessor.initModel();

        BatchResult result = new BatchResult();
        result.setStartTime(LocalDateTime.now());
        result.setFolderPath(folderPath);

        List<FileInfo> files = scanFolder(folderPath);
        result.setTotalFiles(files.size());

        log.info("开始批量处理 {} 个文件", files.size());

        int processedCount = 0;
        int successCount = 0;
        int failedCount = 0;
        for (FileInfo file : files) {
            processedCount++;
            ModuleResult moduleResult = processFile(file);
            result.getModuleResults().add(moduleResult);
            result.setTotalInterfaces(result.getTotalInterfaces() + moduleResult.getInterfaceCount());
            result.setTotalTestCases(result.getTotalTestCases() + moduleResult.getTestCases().size());

            if (moduleResult.getError() != null) {
                failedCount++;
                String fileName = new File(file.getFilePath()).getName();
                result.getErrorFiles().add(new ErrorFile(fileName, file.getFilePath(), moduleResult.getError()));
            } else {
                successCount++;
            }

            printProgress(processedCount, files.size());
        }

        result.setSuccessFiles(successCount);
        result.setFailedFiles(failedCount);
        result.setEndTime(LocalDateTime.now());

        log.info("批量处理完成：成功 {} 个，失败 {} 个，共生成 {} 条测试用例",
                successCount, failedCount, result.getTotalTestCases());

        if (failedCount > 0) {
            log.warn("失败文件列表：");
            for (ErrorFile errorFile : result.getErrorFiles()) {
                log.warn("  - {}: {}", errorFile.getFileName(), errorFile.getErrorMessage());
            }
        }

        return result;
    }

    private static void printProgress(int current, int total) {
        int percentage = (current * 100) / total;
        StringBuilder progressBar = new StringBuilder();
        int barLength = 30;
        int filled = (percentage * barLength) / 100;

        progressBar.append("\r[");
        for (int i = 0; i < barLength; i++) {
            progressBar.append(i < filled ? "█" : "░");
        }
        progressBar.append("] ").append(String.format("%3d%%", percentage))
                .append(" (").append(current).append("/").append(total).append(")");

        System.out.print(progressBar.toString());
        if (current == total) {
            System.out.println();
        }
    }

    /**
     * 处理单个文件
     */
    private static ModuleResult processFile(FileInfo file) {
        ModuleResult result = new ModuleResult();
        result.setFileName(file.getFilePath());
        result.setFileType(file.getFileType().name());

        try {
            List<String> interfaces;

            switch (file.getFileType()) {
                case DOCX:
                    interfaces = DocumentProcessor.extractFromDocx(file.getFilePath());
                    break;
                case SWAGGER_JSON:
                case JSON:
                case YAML:
                    interfaces = SwaggerParser.parseSwagger(file.getFilePath());
                    break;
                default:
                    interfaces = new ArrayList<>();
            }

            result.setInterfaceCount(interfaces.size());

            // 生成测试用例
            List<TestCase> testCases = new ArrayList<>();
            for (String api : interfaces) {
                List<TestCase> tc = DocumentProcessor.generateTestCases(api);
                testCases.addAll(tc);
            }
            result.setTestCases(testCases);

            log.debug("处理完成: {} - 接口数: {} - 用例数: {}",
                    file.getFilePath(), interfaces.size(), testCases.size());

        } catch (Exception e) {
            result.setError(e.getMessage());
            log.error("处理失败: {} - {}", file.getFilePath(), e.getMessage(), e);
        }

        return result;
    }

    /**
     * 批量处理所有文件（带输出目录）
     */
    public static BatchResult processBatch(String folderPath, String outputDir) throws IOException {
        BatchResult result = processBatch(folderPath);

        // 导出结果到文件
        try {
            for (ModuleResult moduleResult : result.getModuleResults()) {
                if (!moduleResult.getTestCases().isEmpty()) {
                    String fileName = new File(moduleResult.getFileName()).getName();
                    fileName = fileName.substring(0, fileName.lastIndexOf(".")) + "_测试用例.xlsx";
                    String outputPath = Paths.get(outputDir, fileName).toString();
                    ExcelExporter.exportToExcel(moduleResult.getTestCases(), outputPath);
                }
            }

            if (!result.getModuleResults().isEmpty()) {
                String summaryPath = Paths.get(outputDir, "批量处理汇总.xlsx").toString();
                exportSummary(result, summaryPath);
            }
        } catch (Exception e) {
            log.error("导出批量处理结果失败: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 导出汇总结果
     */
    private static void exportSummary(BatchResult result, String filePath) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("批量处理汇总");

            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"文件名", "文件类型", "接口数量", "用例数量", "状态"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                sheet.setColumnWidth(i, 6000);
            }

            // 填充数据
            int rowNum = 1;
            for (ModuleResult moduleResult : result.getModuleResults()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(moduleResult.getFileName());
                row.createCell(1).setCellValue(moduleResult.getFileType());
                row.createCell(2).setCellValue(moduleResult.getInterfaceCount());
                row.createCell(3).setCellValue(moduleResult.getTestCases().size());
                row.createCell(4).setCellValue(moduleResult.getError() != null ? "失败" : "成功");
            }

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        }
    }
    public static class FileInfo {
        private String filePath;
        private FileType fileType;

        public enum FileType {
            DOCX, SWAGGER_JSON, JSON, YAML, UNKNOWN
        }

        public FileInfo(String filePath, FileType fileType) {
            this.filePath = filePath;
            this.fileType = fileType;
        }

        public String getFilePath() { return filePath; }
        public FileType getFileType() { return fileType; }
    }

    /**
     * 批量处理结果
     */
    public static class BatchResult {
        private String folderPath;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int totalFiles;
        private int successFiles;
        private int failedFiles;
        private int totalInterfaces;
        private int totalTestCases;
        private List<ModuleResult> moduleResults = new ArrayList<>();
        private List<ErrorFile> errorFiles = new ArrayList<>();

        // Getters and Setters
        public String getFolderPath() { return folderPath; }
        public void setFolderPath(String folderPath) { this.folderPath = folderPath; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
        public int getSuccessFiles() { return successFiles; }
        public void setSuccessFiles(int successFiles) { this.successFiles = successFiles; }
        public int getFailedFiles() { return failedFiles; }
        public void setFailedFiles(int failedFiles) { this.failedFiles = failedFiles; }
        public int getTotalInterfaces() { return totalInterfaces; }
        public void setTotalInterfaces(int totalInterfaces) { this.totalInterfaces = totalInterfaces; }
        public int getTotalTestCases() { return totalTestCases; }
        public void setTotalTestCases(int totalTestCases) { this.totalTestCases = totalTestCases; }
        public List<ModuleResult> getModuleResults() { return moduleResults; }
        public void setModuleResults(List<ModuleResult> moduleResults) { this.moduleResults = moduleResults; }
        public List<ErrorFile> getErrorFiles() { return errorFiles; }
        public void setErrorFiles(List<ErrorFile> errorFiles) { this.errorFiles = errorFiles; }

        public long getDurationSeconds() {
            return java.time.Duration.between(startTime, endTime).getSeconds();
        }
    }

    /**
     * 失败文件信息
     */
    public static class ErrorFile {
        private String fileName;
        private String filePath;
        private String errorMessage;

        public ErrorFile(String fileName, String filePath, String errorMessage) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.errorMessage = errorMessage;
        }

        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * 模块处理结果
     */
    public static class ModuleResult {
        private String fileName;
        private String fileType;
        private int interfaceCount;
        private List<TestCase> testCases = new ArrayList<>();
        private String error;

        // Getters and Setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public int getInterfaceCount() { return interfaceCount; }
        public void setInterfaceCount(int interfaceCount) { this.interfaceCount = interfaceCount; }
        public List<TestCase> getTestCases() { return testCases; }
        public void setTestCases(List<TestCase> testCases) { this.testCases = testCases; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public int getTestCasesCount() { return testCases.size(); }
    }
}