package com.example.demo.exception;

/**
 * AI服务调用异常
 */
public class AIServiceException extends RuntimeException {
    private final String errorCode;
    
    public AIServiceException(String message) {
        super(message);
        this.errorCode = "AI_SERVICE_ERROR";
    }
    
    public AIServiceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public AIServiceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}