package com.example.demo;

/**
 * 测试用例实体类
 * 支持两种类型：UI业务功能用例、后端接口自动化用例
 * 
 * UI用例字段（按顺序）：
 * 1.用例编号(规则：DOC-子模块-3位自增数字)
 * 2.测试大模块
 * 3.子模块
 * 4.测试点分类(仅正向/参数校验/边界/异常/UI校验/权限校验)
 * 5.用例标题(单一验证目标)
 * 6.前置条件
 * 7.预置测试数据
 * 8.操作步骤(带页面路径)
 * 9.量化预期结果
 * 10.优先级(P0/P1/P2)
 * 11.测试环境
 * 12.版本
 * 13.实际结果
 * 14.缺陷单号
 * 15.测试日期
 */
public class TestCase {
    private String id;
    private String testCaseType;
    
    private String parentModule;
    private String subModule;
    private String testCategory;
    private String testModule;
    private String testModuleCode;
    private String testPoint;
    private String title;
    
    private String interfacePath;
    private String method;
    private String precondition;
    private String presetData;
    private String steps;
    private String params;
    
    private Integer expectedStatusCode;
    private Integer expectedBusinessCode;
    private String expectedResult;
    private String actualResult;
    private String priority;
    
    private String testEnvironment;
    private String version;
    private String tester;
    private String defectId;
    private String testDate;
    
    private String expectedResponseJson;
    private String assertionRules;
    private String dbValidation;
    private String completeHeaders;
    
    private String uiRelatedApiCaseId;
    private String apiRelatedUiCaseId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTestCaseType() { return testCaseType; }
    public void setTestCaseType(String testCaseType) { this.testCaseType = testCaseType; }
    public String getParentModule() { return parentModule; }
    public void setParentModule(String parentModule) { this.parentModule = parentModule; }
    public String getSubModule() { return subModule; }
    public void setSubModule(String subModule) { this.subModule = subModule; }
    public String getTestCategory() { return testCategory; }
    public void setTestCategory(String testCategory) { this.testCategory = testCategory; }
    public String getTestModule() { return testModule; }
    public void setTestModule(String testModule) { this.testModule = testModule; }
    public String getTestModuleCode() { return testModuleCode; }
    public void setTestModuleCode(String testModuleCode) { this.testModuleCode = testModuleCode; }
    public String getTestPoint() { return testPoint; }
    public void setTestPoint(String testPoint) { this.testPoint = testPoint; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInterfacePath() { return interfacePath; }
    public void setInterfacePath(String interfacePath) { this.interfacePath = interfacePath; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPrecondition() { return precondition; }
    public void setPrecondition(String precondition) { this.precondition = precondition; }
    public String getPresetData() { return presetData; }
    public void setPresetData(String presetData) { this.presetData = presetData; }
    public String getSteps() { return steps; }
    public void setSteps(String steps) { this.steps = steps; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public Integer getExpectedStatusCode() { return expectedStatusCode; }
    public void setExpectedStatusCode(Integer expectedStatusCode) { this.expectedStatusCode = expectedStatusCode; }
    public Integer getExpectedBusinessCode() { return expectedBusinessCode; }
    public void setExpectedBusinessCode(Integer expectedBusinessCode) { this.expectedBusinessCode = expectedBusinessCode; }
    public String getExpectedResult() { return expectedResult; }
    public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
    public String getActualResult() { return actualResult; }
    public void setActualResult(String actualResult) { this.actualResult = actualResult; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getTestEnvironment() { return testEnvironment; }
    public void setTestEnvironment(String testEnvironment) { this.testEnvironment = testEnvironment; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getTester() { return tester; }
    public void setTester(String tester) { this.tester = tester; }
    public String getDefectId() { return defectId; }
    public void setDefectId(String defectId) { this.defectId = defectId; }
    public String getTestDate() { return testDate; }
    public void setTestDate(String testDate) { this.testDate = testDate; }
    public String getExpectedResponseJson() { return expectedResponseJson; }
    public void setExpectedResponseJson(String expectedResponseJson) { this.expectedResponseJson = expectedResponseJson; }
    public String getAssertionRules() { return assertionRules; }
    public void setAssertionRules(String assertionRules) { this.assertionRules = assertionRules; }
    public String getDbValidation() { return dbValidation; }
    public void setDbValidation(String dbValidation) { this.dbValidation = dbValidation; }
    public String getCompleteHeaders() { return completeHeaders; }
    public void setCompleteHeaders(String completeHeaders) { this.completeHeaders = completeHeaders; }
    public String getUiRelatedApiCaseId() { return uiRelatedApiCaseId; }
    public void setUiRelatedApiCaseId(String uiRelatedApiCaseId) { this.uiRelatedApiCaseId = uiRelatedApiCaseId; }
    public String getApiRelatedUiCaseId() { return apiRelatedUiCaseId; }
    public void setApiRelatedUiCaseId(String apiRelatedUiCaseId) { this.apiRelatedUiCaseId = apiRelatedUiCaseId; }

    @Override
    public String toString() {
        return id + " | " + testCaseType + " | " + parentModule + "/" + subModule + " | " + testCategory + " | " + title;
    }
}