package com.example.demo.service;

import com.example.demo.TestCase;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HTML报告生成器
 */
public class HtmlReportGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 生成HTML报告字符串
     */
    public static String generateReport(BatchScanService.BatchResult result) {
        StringWriter writer = new StringWriter();

        writer.write("<!DOCTYPE html>\n");
        writer.write("<html lang=\"zh-CN\">\n");
        writer.write("<head>\n");
        writer.write("    <meta charset=\"UTF-8\">\n");
        writer.write("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        writer.write("    <title>测试用例生成报告</title>\n");
        writer.write("    <style>\n");
        writer.write(generateStyles());
        writer.write("    </style>\n");
        writer.write("</head>\n");
        writer.write("<body>\n");

        // 头部
        writer.write("<div class=\"header\">\n");
        writer.write("    <h1>🚀 AI测试用例生成报告</h1>\n");
        writer.write("    <p class=\"subtitle\">自动生成时间: " + result.getEndTime().format(DATE_FORMATTER) + "</p>\n");
        writer.write("</div>\n");

        // 统计卡片
        writer.write("<div class=\"stats\">\n");
        writer.write("    <div class=\"stat-card\">\n");
        writer.write("        <div class=\"stat-value\">" + result.getTotalFiles() + "</div>\n");
        writer.write("        <div class=\"stat-label\">处理文件数</div>\n");
        writer.write("    </div>\n");
        writer.write("    <div class=\"stat-card success\">\n");
        writer.write("        <div class=\"stat-value\">" + result.getSuccessFiles() + "</div>\n");
        writer.write("        <div class=\"stat-label\">成功文件数</div>\n");
        writer.write("    </div>\n");
        writer.write("    <div class=\"stat-card error\">\n");
        writer.write("        <div class=\"stat-value\">" + result.getFailedFiles() + "</div>\n");
        writer.write("        <div class=\"stat-label\">失败文件数</div>\n");
        writer.write("    </div>\n");
        writer.write("    <div class=\"stat-card\">\n");
        writer.write("        <div class=\"stat-value\">" + result.getTotalInterfaces() + "</div>\n");
        writer.write("        <div class=\"stat-label\">提取接口数</div>\n");
        writer.write("    </div>\n");
        writer.write("    <div class=\"stat-card\">\n");
        writer.write("        <div class=\"stat-value\">" + result.getTotalTestCases() + "</div>\n");
        writer.write("        <div class=\"stat-label\">生成用例数</div>\n");
        writer.write("    </div>\n");
        writer.write("    <div class=\"stat-card\">\n");
        writer.write("        <div class=\"stat-value\">" + result.getDurationSeconds() + "s</div>\n");
        writer.write("        <div class=\"stat-label\">耗时</div>\n");
        writer.write("    </div>\n");
        writer.write("</div>\n");

        // 失败文件汇总
        if (!result.getErrorFiles().isEmpty()) {
            writer.write("<div class=\"error-section\">\n");
            writer.write("    <h2>❌ 失败文件汇总</h2>\n");
            writer.write("    <div class=\"error-list\">\n");
            for (BatchScanService.ErrorFile errorFile : result.getErrorFiles()) {
                writer.write("        <div class=\"error-item\">\n");
                writer.write("            <div class=\"error-filename\">" + escapeHtml(errorFile.getFileName()) + "</div>\n");
                writer.write("            <div class=\"error-path\">" + escapeHtml(errorFile.getFilePath()) + "</div>\n");
                writer.write("            <div class=\"error-message\">" + escapeHtml(errorFile.getErrorMessage()) + "</div>\n");
                writer.write("        </div>\n");
            }
            writer.write("    </div>\n");
            writer.write("</div>\n");
        }

        // 模块详情
        writer.write("<div class=\"content\">\n");
        writer.write("    <h2>📋 模块详情</h2>\n");

        for (BatchScanService.ModuleResult module : result.getModuleResults()) {
            writer.write("    <div class=\"module-card\">\n");
            writer.write("        <div class=\"module-header\">\n");
            writer.write("            <span class=\"module-name\">" + getModuleName(module.getFileName()) + "</span>\n");
            writer.write("            <span class=\"module-type\">" + module.getFileType() + "</span>\n");
            writer.write("        </div>\n");

            if (module.getError() != null) {
                writer.write("        <div class=\"error-message\">❌ " + module.getError() + "</div>\n");
            } else {
                writer.write("        <div class=\"module-stats\">\n");
                writer.write("            <span>接口: " + module.getInterfaceCount() + "</span>\n");
                writer.write("            <span>用例: " + module.getTestCasesCount() + "</span>\n");
                writer.write("        </div>\n");

                // 用例表格
                if (!module.getTestCases().isEmpty()) {
                    writer.write("        <div class=\"testcase-table\">\n");
                    writer.write("            <table>\n");
                    writer.write("                <thead>\n");
                    writer.write("                    <tr>\n");
                    writer.write("                        <th>用例编号</th>\n");
                    writer.write("                        <th>测试模块</th>\n");
                    writer.write("                        <th>测试点</th>\n");
                    writer.write("                        <th>用例标题</th>\n");
                    writer.write("                        <th>测试接口</th>\n");
                    writer.write("                        <th>请求方法</th>\n");
                    writer.write("                        <th>前置条件</th>\n");
                    writer.write("                        <th>具体操作</th>\n");
                    writer.write("                        <th>请求参数</th>\n");
                    writer.write("                        <th>预期状态码</th>\n");
                    writer.write("                        <th>预期业务码</th>\n");
                    writer.write("                        <th>预期结果</th>\n");
                    writer.write("                        <th>实际结果</th>\n");
                    writer.write("                    </tr>\n");
                    writer.write("                </thead>\n");
                    writer.write("                <tbody>\n");

                    for (TestCase tc : module.getTestCases()) {
                        writer.write("                    <tr>\n");
                        writer.write("                        <td>" + tc.getId() + "</td>\n");
                        writer.write("                        <td>" + escapeHtml(tc.getTestModule()) + "</td>\n");
                        writer.write("                        <td>" + escapeHtml(tc.getTestPoint()) + "</td>\n");
                        writer.write("                        <td>" + escapeHtml(tc.getTitle()) + "</td>\n");
                        writer.write("                        <td>" + tc.getInterfacePath() + "</td>\n");
                        writer.write("                        <td>" + tc.getMethod() + "</td>\n");
                        writer.write("                        <td>" + escapeHtml(tc.getPrecondition()) + "</td>\n");
                        writer.write("                        <td>" + escapeHtml(tc.getSteps()) + "</td>\n");
                        writer.write("                        <td>" + escapeHtml(tc.getParams()) + "</td>\n");
                        writer.write("                        <td>" + (tc.getExpectedStatusCode() != null ? tc.getExpectedStatusCode() : "") + "</td>\n");
                        writer.write("                        <td>" + (tc.getExpectedBusinessCode() != null ? tc.getExpectedBusinessCode() : "") + "</td>\n");
                        writer.write("                        <td>" + escapeHtml(tc.getExpectedResult()) + "</td>\n");
                        writer.write("                        <td>" + escapeHtml(tc.getActualResult()) + "</td>\n");
                        writer.write("                    </tr>\n");
                    }

                    writer.write("                </tbody>\n");
                    writer.write("            </table>\n");
                    writer.write("        </div>\n");
                }
            }
            writer.write("    </div>\n");
        }

        writer.write("</div>\n");

        // 页脚
        writer.write("<div class=\"footer\">\n");
        writer.write("    <p>Generated by AI测试用例生成工具 v3.0</p>\n");
        writer.write("</div>\n");

        writer.write("</body>\n");
        writer.write("</html>");

        return writer.toString();
    }

    /**
     * 保存HTML报告到文件
     */
    public static void saveReport(BatchScanService.BatchResult result, String outputPath) throws IOException {
        String html = generateReport(result);
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(html);
        }
        System.out.println("✅ HTML报告已保存到: " + outputPath);
    }

    /**
     * 生成CSS样式
     */
    private static String generateStyles() {
        return """
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f7fa; padding: 20px; }
            .header { text-align: center; margin-bottom: 30px; }
            .header h1 { color: #2c3e50; font-size: 28px; margin-bottom: 10px; }
            .header .subtitle { color: #7f8c8d; font-size: 14px; }
            .stats { display: flex; justify-content: center; gap: 20px; margin-bottom: 30px; flex-wrap: wrap; }
            .stat-card { background: white; border-radius: 10px; padding: 20px 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); text-align: center; }
            .stat-card.success .stat-value { color: #27ae60; }
            .stat-card.error .stat-value { color: #e74c3c; }
            .stat-value { font-size: 36px; font-weight: bold; color: #3498db; }
            .stat-label { font-size: 14px; color: #7f8c8d; margin-top: 5px; }
            .error-section { max-width: 1400px; margin: 0 auto 30px; background: #fff5f5; border: 1px solid #ffd6d6; border-radius: 10px; padding: 20px; }
            .error-section h2 { color: #c00; margin-bottom: 15px; font-size: 20px; }
            .error-list { display: flex; flex-direction: column; gap: 10px; }
            .error-item { background: white; padding: 12px 15px; border-radius: 6px; border-left: 4px solid #e74c3c; }
            .error-filename { font-weight: bold; color: #2c3e50; font-size: 14px; }
            .error-path { color: #7f8c8d; font-size: 12px; margin-top: 3px; }
            .error-message { color: #e74c3c; font-size: 13px; margin-top: 5px; }
            .content { max-width: 1400px; margin: 0 auto; }
            .content h2 { color: #2c3e50; margin-bottom: 20px; font-size: 22px; }
            .module-card { background: white; border-radius: 10px; padding: 20px; margin-bottom: 20px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
            .module-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; }
            .module-name { font-size: 16px; font-weight: bold; color: #2c3e50; }
            .module-type { background: #3498db; color: white; padding: 4px 12px; border-radius: 15px; font-size: 12px; }
            .module-stats { display: flex; gap: 20px; margin-bottom: 15px; color: #7f8c8d; font-size: 14px; }
            .error-message { background: #fee; color: #c00; padding: 10px; border-radius: 5px; }
            .testcase-table { overflow-x: auto; }
            .testcase-table table { width: 100%; border-collapse: collapse; }
            .testcase-table th { background: #f8f9fa; padding: 12px; text-align: left; font-weight: 600; color: #2c3e50; border-bottom: 2px solid #ddd; }
            .testcase-table td { padding: 10px 12px; border-bottom: 1px solid #eee; font-size: 13px; }
            .testcase-table tr:hover { background: #f8f9fa; }
            .footer { text-align: center; margin-top: 40px; color: #95a5a6; font-size: 13px; }
        """;
    }

    /**
     * 获取模块名称（从文件路径中提取）
     */
    private static String getModuleName(String filePath) {
        int lastSeparator = filePath.lastIndexOf('/');
        if (lastSeparator == -1) lastSeparator = filePath.lastIndexOf('\\');
        String fileName = filePath.substring(lastSeparator + 1);

        // 去除扩展名
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileName = fileName.substring(0, dotIndex);
        }

        return fileName;
    }

    /**
     * HTML转义
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\n", "<br>");
    }
}