package com.example.demo.service;

import com.example.demo.TestCase;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel导出器
 * 职责：测试用例的Excel导入导出、文本导出、失败模拟数据导出
 */
@Slf4j
public class ExcelExporter {

    /**
     * 导出测试用例到Excel文件
     */
    public static void exportToExcel(List<TestCase> testCases, String filePath) {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            exportToExcel(testCases, fos);
        } catch (Exception e) {
            log.error("导出Excel失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 导出测试用例到输出流（后端接口自动化用例完整版）
     */
    public static void exportToExcel(List<TestCase> testCases, OutputStream outputStream) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("测试用例");

            // 创建表头（24列：API测试用例完整版）
            Row headerRow = sheet.createRow(0);
            String[] headers = {"用例编号", "测试类型", "测试模块", "测试模块代码", "测试点", "用例标题", "前置条件", "接口路径", "请求方法", "请求头", "请求参数", "预期状态码", "预期业务码", "预期响应JSON", "断言规则", "数据库校验", "优先级", "测试环境", "版本", "测试人", "缺陷ID", "测试日期", "关联UI用例ID", "实际结果"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                sheet.setColumnWidth(i, i == 0 ? 3500 : i == 1 ? 2500 : i == 2 ? 3000 : i == 3 ? 2500 : i == 4 ? 3000 : i == 5 ? 5000 : i == 6 ? 4000 : i == 7 ? 4500 : i == 8 ? 2000 : i == 9 ? 4000 : i == 10 ? 5000 : i == 11 ? 2000 : i == 12 ? 2000 : i == 13 ? 5000 : i == 14 ? 5000 : i == 15 ? 4000 : i == 16 ? 2000 : i == 17 ? 2500 : i == 18 ? 2000 : i == 19 ? 2000 : i == 20 ? 2000 : i == 21 ? 2500 : i == 22 ? 3000 : 3000);
            }

            // 填充数据
            int rowNum = 1;
            for (TestCase tc : testCases) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(tc.getId());
                row.createCell(1).setCellValue(tc.getTestCaseType() != null ? tc.getTestCaseType() : "API");
                row.createCell(2).setCellValue(tc.getTestModule());
                row.createCell(3).setCellValue(tc.getTestModuleCode());
                row.createCell(4).setCellValue(tc.getTestPoint());
                row.createCell(5).setCellValue(tc.getTitle());
                row.createCell(6).setCellValue(tc.getPrecondition());
                row.createCell(7).setCellValue(tc.getInterfacePath());
                row.createCell(8).setCellValue(tc.getMethod());
                row.createCell(9).setCellValue(tc.getCompleteHeaders());
                row.createCell(10).setCellValue(tc.getParams());
                row.createCell(11).setCellValue(tc.getExpectedStatusCode() != null ? tc.getExpectedStatusCode().toString() : "");
                row.createCell(12).setCellValue(tc.getExpectedBusinessCode() != null ? tc.getExpectedBusinessCode().toString() : "");
                row.createCell(13).setCellValue(tc.getExpectedResponseJson());
                row.createCell(14).setCellValue(tc.getAssertionRules());
                row.createCell(15).setCellValue(tc.getDbValidation());
                row.createCell(16).setCellValue(tc.getPriority());
                row.createCell(17).setCellValue(tc.getTestEnvironment());
                row.createCell(18).setCellValue(tc.getVersion());
                row.createCell(19).setCellValue(tc.getTester());
                row.createCell(20).setCellValue(tc.getDefectId());
                row.createCell(21).setCellValue(tc.getTestDate());
                row.createCell(22).setCellValue(tc.getApiRelatedUiCaseId());
                row.createCell(23).setCellValue(tc.getActualResult());
            }

            workbook.write(outputStream);
        } catch (Exception e) {
            log.error("导出Excel失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 导出需求文档测试用例到Excel文件（UI业务功能用例完整版）
     */
    public static void exportRequirementTestCasesToExcel(List<TestCase> testCases, String filePath) {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            exportRequirementTestCasesToExcel(testCases, fos);
        } catch (Exception e) {
            log.error("导出需求文档测试用例Excel失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 导出需求文档测试用例到输出流（UI业务功能用例完整版）
     */
    public static void exportRequirementTestCasesToExcel(List<TestCase> testCases, OutputStream outputStream) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("测试用例");

            // 创建表头（15列：UI业务功能用例完整版）
            Row headerRow = sheet.createRow(0);
            String[] headers = {"用例编号", "测试大模块", "子模块", "测试点分类", "用例标题", "前置条件", "预置测试数据", "操作步骤", "量化预期结果", "优先级", "测试环境", "版本", "实际结果", "缺陷单号", "测试日期"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                sheet.setColumnWidth(i, i == 0 ? 3500 : i == 1 ? 3000 : i == 2 ? 3000 : i == 3 ? 2500 : i == 4 ? 4500 : i == 5 ? 4000 : i == 6 ? 4000 : i == 7 ? 6000 : i == 8 ? 5000 : i == 9 ? 1500 : i == 10 ? 2000 : i == 11 ? 1500 : i == 12 ? 2500 : i == 13 ? 2000 : 2000);
            }

            // 填充数据
            int rowNum = 1;
            for (TestCase tc : testCases) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(tc.getId());
                row.createCell(1).setCellValue(tc.getParentModule());
                row.createCell(2).setCellValue(tc.getSubModule());
                row.createCell(3).setCellValue(tc.getTestCategory());
                row.createCell(4).setCellValue(tc.getTitle());
                row.createCell(5).setCellValue(tc.getPrecondition());
                row.createCell(6).setCellValue(tc.getPresetData());
                row.createCell(7).setCellValue(tc.getSteps());
                row.createCell(8).setCellValue(tc.getExpectedResult());
                row.createCell(9).setCellValue(tc.getPriority());
                row.createCell(10).setCellValue(tc.getTestEnvironment());
                row.createCell(11).setCellValue(tc.getVersion());
                row.createCell(12).setCellValue(tc.getActualResult());
                row.createCell(13).setCellValue(tc.getDefectId());
                row.createCell(14).setCellValue(tc.getTestDate());
            }

            workbook.write(outputStream);
        } catch (Exception e) {
            log.error("导出需求文档测试用例Excel失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 导出失败模拟数据到Excel（带红色警告）
     */
    public static void exportFailedMockToExcel(String filePath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("测试用例");

            // 创建红色警告样式
            CellStyle warningStyle = workbook.createCellStyle();
            Font warningFont = workbook.createFont();
            warningFont.setColor(IndexedColors.RED.getIndex());
            warningFont.setBold(true);
            warningFont.setFontHeightInPoints((short) 14);
            warningStyle.setFont(warningFont);

            // 创建第一行红色警告
            Row warningRow = sheet.createRow(0);
            Cell warningCell = warningRow.createCell(0);
            warningCell.setCellValue("⚠️ 此文件为AI调用失败时的模拟数据，非真实生成！请检查API Key和网络连接后重新运行！");
            warningCell.setCellStyle(warningStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 5));
            sheet.setColumnWidth(0, 15000);

            // 创建表头
            Row headerRow = sheet.createRow(2);
            String[] headers = {"用例编号", "用例标题", "接口路径", "请求方法", "请求参数", "预期结果"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                sheet.setColumnWidth(i, 6000);
            }

            // 填充模拟数据
            List<TestCase> mockCases = getMockTestCases();
            int rowNum = 3;
            for (TestCase tc : mockCases) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(tc.getId());
                row.createCell(1).setCellValue(tc.getTitle());
                row.createCell(2).setCellValue(tc.getInterfacePath());
                row.createCell(3).setCellValue(tc.getMethod());
                row.createCell(4).setCellValue(tc.getParams());
                row.createCell(5).setCellValue(tc.getExpectedResult());
            }

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        } catch (Exception e) {
            log.error("导出失败模拟文件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存到文本文件
     */
    public static void saveToText(List<TestCase> testCases, String filePath) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("用例编号\t测试模块\t测试点\t用例标题\t测试接口\t请求方法\t前置条件\t具体操作\t请求参数\t预期状态码\t预期业务码\t预期结果\t优先级\t实际结果");
            for (TestCase tc : testCases) {
                lines.add(tc.getId() + "\t" + tc.getTestModule() + "\t" + tc.getTestPoint() + "\t" +
                        tc.getTitle() + "\t" + tc.getInterfacePath() + "\t" + tc.getMethod() + "\t" +
                        tc.getPrecondition() + "\t" + tc.getSteps() + "\t" + tc.getParams() + "\t" +
                        tc.getExpectedStatusCode() + "\t" + tc.getExpectedBusinessCode() + "\t" +
                        tc.getExpectedResult() + "\t" + tc.getPriority() + "\t" + tc.getActualResult());
            }
            Files.write(Paths.get(filePath), lines);
        } catch (Exception e) {
            log.error("保存文本失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从Excel读取测试用例
     */
    public static List<TestCase> readTestCasesFromExcel(String filePath) throws Exception {
        List<TestCase> testCases = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(filePath))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                TestCase tc = new TestCase();
                tc.setId(getCellValue(row.getCell(0)));
                tc.setTestModule(getCellValue(row.getCell(1)));
                tc.setTestPoint(getCellValue(row.getCell(2)));
                tc.setTitle(getCellValue(row.getCell(3)));
                tc.setInterfacePath(getCellValue(row.getCell(4)));
                tc.setMethod(getCellValue(row.getCell(5)));
                tc.setPrecondition(getCellValue(row.getCell(6)));
                tc.setSteps(getCellValue(row.getCell(7)));
                tc.setParams(getCellValue(row.getCell(8)));

                String statusCodeStr = getCellValue(row.getCell(9));
                if (!statusCodeStr.isEmpty()) {
                    try {
                        tc.setExpectedStatusCode(Integer.parseInt(statusCodeStr));
                    } catch (NumberFormatException e) {
                    }
                }

                String businessCodeStr = getCellValue(row.getCell(10));
                if (!businessCodeStr.isEmpty()) {
                    try {
                        tc.setExpectedBusinessCode(Integer.parseInt(businessCodeStr));
                    } catch (NumberFormatException e) {
                    }
                }

                tc.setExpectedResult(getCellValue(row.getCell(11)));
                tc.setPriority(getCellValue(row.getCell(12)));
                tc.setActualResult(getCellValue(row.getCell(13)));

                if (tc.getInterfacePath() != null && !tc.getInterfacePath().isEmpty()) {
                    testCases.add(tc);
                }
            }
        }

        return testCases;
    }

    /**
     * 获取单元格值
     */
    private static String getCellValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    /**
     * 从Excel读取测试用例（别名方法，兼容旧调用）
     */
    public static List<TestCase> importFromExcel(String filePath) throws Exception {
        return readTestCasesFromExcel(filePath);
    }

    /**
     * 导出执行结果到Excel
     */
    public static void exportExecutionResult(com.example.demo.service.TestCaseExecutor.ExecutionResult result, String filePath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("执行结果");

            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"用例编号", "用例标题", "接口路径", "请求方法", "状态", "HTTP状态码", "响应时间(ms)", "错误信息"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                sheet.setColumnWidth(i, 6000);
            }

            // 填充数据
            int rowNum = 1;
            for (com.example.demo.service.TestCaseExecutor.ExecutionRecord record : result.getRecords()) {
                TestCase tc = record.getTestCase();
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(tc != null ? tc.getId() : "");
                row.createCell(1).setCellValue(tc != null ? tc.getTitle() : "");
                row.createCell(2).setCellValue(tc != null ? tc.getInterfacePath() : "");
                row.createCell(3).setCellValue(tc != null ? tc.getMethod() : "");
                row.createCell(4).setCellValue(record.getStatus().name());
                row.createCell(5).setCellValue(record.getResponseStatusCode());
                row.createCell(6).setCellValue(record.getResponseTime());
                row.createCell(7).setCellValue(record.getErrorMessage() != null ? record.getErrorMessage() : "");
            }

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        } catch (Exception e) {
            log.error("导出执行结果失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取模拟测试用例
     */
    private static List<TestCase> getMockTestCases() {
        List<TestCase> testCases = new ArrayList<>();

        TestCase tc1 = new TestCase();
        tc1.setId("TC001");
        tc1.setTitle("正常场景-个人数据小量传输");
        tc1.setInterfacePath("/api/data/transfer");
        tc1.setMethod("POST");
        tc1.setParams("sourceDept=财务部&targetDept=人事部&dataType=PERSONAL&volumeMB=100");
        tc1.setExpectedResult("返回200，数据传输成功");
        testCases.add(tc1);

        TestCase tc2 = new TestCase();
        tc2.setId("TC002");
        tc2.setTitle("边界值-sourceDept最小长度");
        tc2.setInterfacePath("/api/data/transfer");
        tc2.setMethod("POST");
        tc2.setParams("sourceDept=AB&targetDept=人事部&dataType=ENTERPRISE&volumeMB=512");
        tc2.setExpectedResult("返回200，数据传输成功");
        testCases.add(tc2);

        TestCase tc3 = new TestCase();
        tc3.setId("TC003");
        tc3.setTitle("边界值-volumeMB最大值");
        tc3.setInterfacePath("/api/data/transfer");
        tc3.setMethod("POST");
        tc3.setParams("sourceDept=财务部&targetDept=人事部&dataType=GOVERNMENT&volumeMB=10240");
        tc3.setExpectedResult("返回200，数据传输成功");
        testCases.add(tc3);

        TestCase tc4 = new TestCase();
        tc4.setId("TC004");
        tc4.setTitle("异常-sourceDept为空");
        tc4.setInterfacePath("/api/data/transfer");
        tc4.setMethod("POST");
        tc4.setParams("sourceDept=&targetDept=人事部&dataType=PERSONAL&volumeMB=100");
        tc4.setExpectedResult("返回400，提示sourceDept不能为空");
        testCases.add(tc4);

        TestCase tc5 = new TestCase();
        tc5.setId("TC005");
        tc5.setTitle("异常-dataType非法值");
        tc5.setInterfacePath("/api/data/transfer");
        tc5.setMethod("POST");
        tc5.setParams("sourceDept=财务部&targetDept=人事部&dataType=INVALID&volumeMB=100");
        tc5.setExpectedResult("返回400，提示dataType值无效");
        testCases.add(tc5);

        TestCase tc6 = new TestCase();
        tc6.setId("TC006");
        tc6.setTitle("异常-volumeMB超出范围");
        tc6.setInterfacePath("/api/data/transfer");
        tc6.setMethod("POST");
        tc6.setParams("sourceDept=财务部&targetDept=人事部&dataType=PERSONAL&volumeMB=10241");
        tc6.setExpectedResult("返回400，提示volumeMB超出范围");
        testCases.add(tc6);

        return testCases;
    }
}