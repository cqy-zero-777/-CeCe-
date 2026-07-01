package com.example.demo.exception;

/**
 * 测试用例生成异常
 */
public class TestCaseGenerationException extends RuntimeException {
    private final String errorCode;
    
    public TestCaseGenerationException(String message) {
        super(message);
        this.errorCode = "TESTCASE_GEN_ERROR";
    }
    
    public TestCaseGenerationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public TestCaseGenerationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}