package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF文档解析器
 * 职责：读取PDF文档，按真实页码提取内容
 */
@Slf4j
public class PdfParser {

    /**
     * 读取PDF全文（不分页）
     */
    public static String readPdf(String filePath) {
        log.info("正在读取PDF文档: {}", filePath);
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            log.info("PDF读取完成，共 {} 页，字符数: {}", document.getNumberOfPages(), text.length());
            return text;
        } catch (IOException e) {
            log.error("读取PDF失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 读取PDF文档（按页码范围）
     * PDF的页码是真实的物理页码，完全精确
     *
     * @param filePath  文件路径
     * @param startPage 起始页码（从1开始）
     * @param endPage   结束页码（0表示读到最后一页）
     */
    public static String readPdf(String filePath, int startPage, int endPage) {
        log.info("正在读取PDF文档: {}，页码范围: {} - {}", filePath,
                startPage > 0 ? startPage : "开始", endPage > 0 ? endPage : "结束");

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            int totalPages = document.getNumberOfPages();
            int actualStart = Math.max(1, Math.min(startPage > 0 ? startPage : 1, totalPages));
            int actualEnd = endPage > 0 ? Math.min(endPage, totalPages) : totalPages;

            log.info("PDF共 {} 页，实际读取范围: {} - {}（精确页码）", totalPages, actualStart, actualEnd);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(actualStart);
            stripper.setEndPage(actualEnd);
            // 保留段落间的换行

            String text = stripper.getText(document);
            log.info("PDF读取完成，字符数: {}", text.length());
            return text;

        } catch (IOException e) {
            log.error("读取PDF失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 从PDF中提取接口定义（按方法+路径模式匹配）
     */
    public static List<String> extractInterfaces(String text) {
        if (text == null || text.isEmpty()) return new ArrayList<>();

        List<String> interfaces = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder currentApi = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 匹配 HTTP 方法 + 路径模式
            if (line.matches("^(GET|POST|PUT|DELETE|PATCH)\\s+/.*")) {
                if (currentApi.length() > 0) {
                    interfaces.add(currentApi.toString());
                }
                currentApi = new StringBuilder(line);
            } else {
                currentApi.append("\n").append(line);
            }
        }
        if (currentApi.length() > 0) {
            interfaces.add(currentApi.toString());
        }

        // 过滤和去重
        interfaces.removeIf(api -> api.length() < 5 || !api.contains("/"));
        List<String> unique = new ArrayList<>();
        for (String api : interfaces) {
            if (!unique.stream().anyMatch(api::contains)) {
                unique.add(api);
            }
        }
        return unique;
    }

    /**
     * 获取PDF总页数
     */
    public static int getPageCount(String filePath) {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            log.error("获取PDF页数失败: {}", e.getMessage(), e);
            return 0;
        }
    }
}
