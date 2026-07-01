package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class WordParser {

    private static final int CHUNK_SIZE = 2000;

    public static List<String> extractFromDocx(String docxPath) {
        String docContent = readDocx(docxPath);
        if (docContent == null || docContent.length() < 50) {
            return getExampleInterfaces();
        }
        return parseInterfacesFromText(docContent);
    }

    public static List<String> extractFromDocx(String docxPath, int startPage, int endPage) {
        String docContent = readDocx(docxPath, startPage, endPage);
        if (docContent == null || docContent.length() < 50) {
            return getExampleInterfaces();
        }
        return parseInterfacesFromText(docContent);
    }

    public static List<String> extractFromDocxByChapter(String docxPath, List<String> chapterTitles) {
        String docContent = readDocxByChapter(docxPath, chapterTitles);
        if (docContent == null || docContent.length() < 50) {
            return getExampleInterfaces();
        }
        return parseInterfacesFromText(docContent);
    }

    public static String readDocx(String filePath) {
        log.info("步骤1：正在读取文档: {}", filePath);
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText().trim();
                if (!text.isEmpty()) {
                    content.append(text).append("\n");
                }
            }
            for (XWPFTable table : document.getTables()) {
                content.append("\n[表格]\n");
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowText = new StringBuilder();
                    row.getTableCells().forEach(cell ->
                            rowText.append(cell.getText()).append(" | "));
                    content.append(rowText).append("\n");
                }
                content.append("[/表格]\n");
            }
            log.info("文档读取完成，字符数: {}", content.length());
        } catch (Exception e) {
            log.error("读取文档失败: {}", e.getMessage(), e);
        }
        return content.toString();
    }

    // =========================================================
    //  核心：按页码读取（支持显式分页符 + 字符密度估算双模式）
    // =========================================================

    public static String readDocx(String filePath, int startPage, int endPage) {
        ContentPageInfo pageInfo = analyzeDocument(filePath);
        int contentOffset = pageInfo.firstContentPage - 1;

        log.info("步骤1：正在读取文档: {}，页码范围: {} - {}，内容偏移: {} 页",
                filePath,
                startPage > 0 ? startPage : "开始",
                endPage > 0 ? endPage : "结束",
                contentOffset);

        StringBuilder content = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            if ("character_based".equals(pageInfo.estimateMode)) {
                // ==== 字符密度估算模式（适用于无显式分页符的大文档）====
                int targetStart = Math.max(1, (startPage > 0 ? startPage : 1));
                int targetEnd = (endPage > 0 ? endPage : pageInfo.paragraphCounts.size());
                targetStart = Math.max(1, Math.min(targetStart, pageInfo.paragraphCounts.size()));
                targetEnd = Math.max(targetStart, Math.min(targetEnd, pageInfo.paragraphCounts.size()));

                // 映射页码 → 段落索引范围
                int startPara = 0;
                for (int p = 0; p < targetStart - 1 && p < pageInfo.paragraphCounts.size(); p++) {
                    startPara += pageInfo.paragraphCounts.get(p)[0];
                }
                int endPara = startPara;
                for (int p = targetStart - 1; p < targetEnd && p < pageInfo.paragraphCounts.size(); p++) {
                    endPara += pageInfo.paragraphCounts.get(p)[0];
                }
                log.info("估算页面范围 {} - {} → 段落 {} - {}（共{}组）",
                        targetStart, targetEnd, startPara, endPara,
                        targetEnd - targetStart + 1);

                int paraIdx = 0;
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    String text = paragraph.getText().trim();
                    if (!text.isEmpty() && paraIdx >= startPara && paraIdx < endPara) {
                        content.append(text).append("\n");
                    }
                    paraIdx++;
                }

            } else {
                // ==== 显式分页符模式 ====
                int totalPages = pageInfo.totalPages;
                int physicalStart = (startPage > 0 ? startPage : 1) + contentOffset;
                int physicalEnd = (endPage > 0 ? endPage : totalPages - contentOffset) + contentOffset;
                physicalStart = Math.max(1, Math.min(physicalStart, totalPages));
                physicalEnd = Math.max(physicalStart, Math.min(physicalEnd, totalPages));

                log.info("物理页码范围: {} - {}（总页数: {}）", physicalStart, physicalEnd, totalPages);

                int currentPage = 1;
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    String text = paragraph.getText().trim();
                    boolean hasPageBreak = isPageBreak(paragraph);
                    if (!text.isEmpty() && currentPage >= physicalStart && currentPage <= physicalEnd) {
                        content.append(text).append("\n");
                    }
                    if (hasPageBreak) {
                        currentPage++;
                        if (currentPage > physicalEnd) break;
                    }
                }
            }

            log.info("文档读取完成，字符数: {}", content.length());

        } catch (Exception e) {
            log.error("读取文档失败: {}", e.getMessage(), e);
        }
        return content.toString();
    }

    // =========================================================
    //  文档分析：估算总页数 + 查找内容起始页
    // =========================================================

    private static ContentPageInfo analyzeDocument(String filePath) {
        ContentPageInfo info = new ContentPageInfo();

        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            // --- 阶段1：统计显式分页符和总字符数 ---
            int explicitBreakCount = 0;
            int totalChars = 0;
            int totalParas = 0;
            List<int[]> groups = new ArrayList<>();
            int groupParas = 0, groupChars = 0;

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                int len = text != null ? text.trim().length() : 0;
                totalChars += len;
                totalParas++;
                if (isPageBreak(paragraph)) {
                    explicitBreakCount++;
                    groups.add(new int[]{groupParas, groupChars});
                    groupParas = 0;
                    groupChars = 0;
                } else if (len > 0) {
                    groupParas++;
                    groupChars += len;
                }
            }
            groups.add(new int[]{groupParas, groupChars}); // 最后一组

            // --- 阶段2：确定估算方式 ---
            if (explicitBreakCount >= 3) {
                info.totalPages = groups.size();
                info.estimateMode = "explicit_breaks";
                info.paragraphCounts = groups;
                log.info("检测到 {} 个显式分页符，总页数: {}", explicitBreakCount, info.totalPages);
            } else {
                // 字符密度估算
                int cpp = totalParas > 0 && (totalChars / totalParas) > 200 ? 2000 : 1500;
                if (totalParas > 0 && (totalChars / totalParas) < 50) cpp = 1000;
                info.totalPages = Math.max(1, (totalChars + cpp - 1) / cpp);
                info.totalChars = totalChars;
                info.charsPerPage = cpp;
                info.estimateMode = "character_based";

                // 按字符重建分组
                List<int[]> charGroups = new ArrayList<>();
                int cParas = 0, cChars = 0;
                for (XWPFParagraph p : document.getParagraphs()) {
                    String t = p.getText();
                    int l = t != null ? t.trim().length() : 0;
                    if (l > 0) {
                        cChars += l;
                        cParas++;
                        if (cChars >= cpp) {
                            charGroups.add(new int[]{cParas, cChars});
                            cParas = 0;
                            cChars = 0;
                        }
                    }
                }
                if (cParas > 0) charGroups.add(new int[]{cParas, cChars});
                info.paragraphCounts = charGroups;

                log.info("字符密度估算：{} 字符 × {} 页/字符 → {} 页, {} 段落组",
                        totalChars, cpp, info.totalPages, charGroups.size());
            }

            // --- 阶段3：查找内容起始页（跳过封面/目录）---
            if ("explicit_breaks".equals(info.estimateMode)) {
                int pg = 1;
                StringBuilder sb = new StringBuilder();
                for (XWPFParagraph p : document.getParagraphs()) {
                    String t = p.getText().trim();
                    if (!t.isEmpty()) sb.append(t).append("\n");
                    if (isPageBreak(p)) {
                        if (info.firstContentPage == 0 && hasSubstantialContent(sb.toString())) {
                            info.firstContentPage = pg;
                        }
                        pg++;
                        sb = new StringBuilder();
                    }
                }
                if (info.firstContentPage == 0 && hasSubstantialContent(sb.toString())) {
                    info.firstContentPage = pg;
                }
            } else {
                info.firstContentPage = 1;
            }
            if (info.firstContentPage == 0) info.firstContentPage = 1;

            log.info("文档分析完成：总页数={}，内容起始页={}，模式={}",
                    info.totalPages, info.firstContentPage, info.estimateMode);

        } catch (Exception e) {
            log.error("分析文档结构失败: {}", e.getMessage(), e);
            info.totalPages = 1;
            info.firstContentPage = 1;
        }
        return info;
    }

    private static boolean hasSubstantialContent(String pageText) {
        if (pageText == null || pageText.trim().isEmpty()) return false;
        String text = pageText.trim();
        if (text.length() < 30) return false;
        String[] keywords = {"第", "章", "节", "接口", "需求", "功能", "模块",
                "/api", "GET ", "POST ", "PUT ", "DELETE ",
                "用户", "系统", "管理", "数据", "服务",
                "目录", "概述", "介绍", "说明",
                "1.", "2.", "3.", "4.", "5.",
                "一、", "二、", "三、"};
        int count = 0;
        for (String kw : keywords) { if (text.contains(kw)) count++; }
        if (count >= 3) return true;
        String[] lines = text.split("\n");
        for (String line : lines) { if (line.trim().length() > 50) return true; }
        if (text.matches("(?s)^第[一二三四五六七八九十\\d]+[章节部].*")) return true;
        if (text.matches("(?s)^\\d+\\.\\d+.*")) return true;
        if (text.matches("(?s)^[一二三四五六七八九十]、.*")) return true;
        if (text.contains("接口地址") || text.contains("请求路径") || text.contains("参数名")) return true;
        return false;
    }

    private static class ContentPageInfo {
        int totalPages = 1;
        int firstContentPage = 0;
        String estimateMode = "explicit_breaks";
        int totalChars = 0;
        int charsPerPage = 1500;
        List<int[]> paragraphCounts = new ArrayList<>();
    }

    // =========================================================
    //  章节识别
    // =========================================================

    public static List<String> getChapterTitles(String filePath) {
        List<String> chapterTitles = new ArrayList<>();
        Pattern chapterPattern = Pattern.compile("^第[一二三四五六七八九十\\d]+章\\s*.+");
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText().trim();
                if (text.matches("^第[一二三四五六七八九十\\d]+章\\s*.+")) {
                    chapterTitles.add(text);
                }
            }
        } catch (Exception e) {
            log.error("获取章节标题失败: {}", e.getMessage(), e);
        }
        return chapterTitles;
    }

    public static String readDocxByChapter(String filePath, List<String> chapterTitles) {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            Pattern chapterPattern = Pattern.compile("^第[一二三四五六七八九十\\d]+章\\s*.+");
            boolean inTargetChapter = false;
            int chapterIndex = 0;
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText().trim();
                if (text.matches("^第[一二三四五六七八九十\\d]+章\\s*.+")) {
                    if (chapterTitles.contains(text) ||
                        chapterTitles.stream().anyMatch(text::contains)) {
                        inTargetChapter = true;
                        chapterIndex++;
                    } else {
                        inTargetChapter = false;
                    }
                }
                if (inTargetChapter && !text.isEmpty()) {
                    content.append(text).append("\n");
                }
            }
        } catch (Exception e) {
            log.error("读取文档失败: {}", e.getMessage(), e);
        }
        return content.toString();
    }

    // =========================================================
    //  分页符检测
    // =========================================================

    private static int estimatePageCount(XWPFDocument document) {
        int pageCount = 1;
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (isPageBreak(paragraph)) pageCount++;
        }
        return pageCount;
    }

    private static boolean isPageBreak(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.getText(0);
            if (text != null && text.contains("\f")) return true;
        }
        try {
            for (XWPFRun run : paragraph.getRuns()) {
                if (run.getCTR() != null && run.getCTR().getBrList() != null) {
                    for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr br : run.getCTR().getBrList()) {
                        if (br.getType() != null &&
                            br.getType().intValue() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType.INT_PAGE) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        try {
            if (paragraph.getCTP() != null &&
                paragraph.getCTP().getPPr() != null &&
                paragraph.getCTP().getPPr().isSetPageBreakBefore()) {
                return true;
            }
        } catch (Exception e) { /* ignore */ }
        return false;
    }

    private static void logProgress(int current, int total, String unit) {
        if (current % 10 == 0 || current == total) {
            log.info("📖 正在读取第 {}/{} {}...", current, total, unit);
        }
    }

    // =========================================================
    //  文本解析 & 示例
    // =========================================================

    public static List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String[] sections = text.split("(?m)^第[一二三四五六七八九十\\d]+章\\s*");
        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) continue;
            if (section.length() > CHUNK_SIZE) {
                for (int i = 0; i < section.length(); i += CHUNK_SIZE) {
                    int end = Math.min(i + CHUNK_SIZE, section.length());
                    chunks.add(section.substring(i, end));
                }
            } else {
                chunks.add(section);
            }
        }
        return chunks;
    }

    public static boolean containsInterfaceKeywords(String docContent) {
        if (docContent == null || docContent.isEmpty()) return false;
        String[] keywords = {"/api/", "GET ", "POST ", "PUT ", "DELETE ", "PATCH ",
                "接口地址", "请求地址", "API地址", "endpoint"};
        for (String kw : keywords) { if (docContent.contains(kw)) return true; }
        return false;
    }

    public static List<String> parseInterfacesFromText(String text) {
        List<String> allInterfaces = new ArrayList<>();
        if (text == null || text.isEmpty()) return allInterfaces;
        String[] lines = text.split("\n");
        StringBuilder currentApi = new StringBuilder();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.matches("^(GET|POST|PUT|DELETE|PATCH)\\s+/.*")) {
                if (currentApi.length() > 0) allInterfaces.add(currentApi.toString());
                currentApi = new StringBuilder(line);
            } else {
                currentApi.append("\n").append(line);
            }
        }
        if (currentApi.length() > 0) allInterfaces.add(currentApi.toString());
        allInterfaces.removeIf(api -> api.length() < 5 || !api.contains("/"));
        List<String> unique = new ArrayList<>();
        for (String api : allInterfaces) {
            if (!unique.stream().anyMatch(api::contains)) unique.add(api);
        }
        return unique;
    }

    public static List<String> getExampleInterfaces() {
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

    // =========================================================
    //  按章节分段（用于需求文档）
    // =========================================================

    public static List<SectionContent> readDocxBySections(String filePath) {
        return readDocxBySections(filePath, 0, 0);
    }

    public static List<SectionContent> readDocxBySections(String filePath, int startPage, int endPage) {
        ContentPageInfo pageInfo = analyzeDocument(filePath);
        int contentOffset = pageInfo.firstContentPage - 1;

        log.info("按章节分段读取文档: {}，页码范围: {} - {}，内容偏移: {} 页",
                filePath, startPage > 0 ? startPage : "全部",
                endPage > 0 ? endPage : "全部", contentOffset);

        List<SectionContent> sections = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            int currentPage = 1;
            int totalPages = pageInfo.totalPages;
            int actualStart = startPage > 0 ? startPage + contentOffset : 1;
            int actualEnd = endPage > 0 ? endPage + contentOffset : totalPages;
            actualStart = Math.max(1, Math.min(actualStart, totalPages));
            actualEnd = Math.max(actualStart, Math.min(actualEnd, totalPages));

            String currentSectionTitle = "文档开头";
            StringBuilder currentContent = new StringBuilder();

            Pattern sectionPattern = Pattern.compile(
                "^(第[一二三四五六七八九十\\d]+[章节部]|\\d+\\.\\d+(\\.\\d+)*[\\s\\.、]|\\d+[\\s\\.、][^\\d]|一、|二、|三、|四、|五、|六、|七、|八、|九、|十、).+"
            );

            for (int ei = 0; ei < document.getBodyElements().size(); ei++) {
                IBodyElement element = document.getBodyElements().get(ei);
                if (element instanceof XWPFParagraph) {
                    XWPFParagraph paragraph = (XWPFParagraph) element;
                    String text = paragraph.getText().trim();
                    boolean hasPageBreak = isPageBreak(paragraph);
                    boolean inRange = (currentPage >= actualStart && currentPage <= actualEnd);

                    if (hasPageBreak) {
                        currentPage++;
                        if (currentPage > actualEnd) break;
                    }
                    if (!inRange) continue;

                    if (!text.isEmpty() && sectionPattern.matcher(text).matches()) {
                        if (currentContent.length() > 50) {
                            sections.add(new SectionContent(currentSectionTitle, currentContent.toString()));
                        }
                        currentSectionTitle = text;
                        currentContent = new StringBuilder();
                    } else if (!text.isEmpty()) {
                        currentContent.append(text).append("\n");
                    }
                }
            }

            if (currentContent.length() > 50) {
                sections.add(new SectionContent(currentSectionTitle, currentContent.toString()));
            }

            if (sections.isEmpty()) {
                String fullContent = readDocx(filePath, startPage, endPage);
                if (fullContent.length() > 50) {
                    int maxSize = 5000;
                    String[] lines = fullContent.split("\n");
                    StringBuilder para = new StringBuilder();
                    int idx = 1;
                    for (String line : lines) {
                        line = line.trim();
                        if (para.length() + line.length() > maxSize && para.length() > 100) {
                            sections.add(new SectionContent("段落" + (idx++), para.toString()));
                            para = new StringBuilder();
                        }
                        para.append(line).append("\n");
                    }
                    if (para.length() > 100) sections.add(new SectionContent("段落" + idx, para.toString()));
                }
            }

            log.info("文档分段完成，共 {} 个段落/章节", sections.size());

        } catch (Exception e) {
            log.error("按章节分段读取失败: {}", e.getMessage(), e);
        }
        return sections;
    }

    public static class SectionContent {
        private final String title;
        private final String content;
        public SectionContent(String title, String content) {
            this.title = title;
            this.content = content;
        }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        @Override
        public String toString() { return "【" + title + "】\n" + content; }
    }
}