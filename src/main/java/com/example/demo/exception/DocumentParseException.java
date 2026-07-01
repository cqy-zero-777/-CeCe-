package com.example.demo.exception;

/**
 * 文档解析异常
 */
public class DocumentParseException extends RuntimeException {
    private final String errorCode;
    
    public DocumentParseException(String message) {
        super(message);
        this.errorCode = "DOC_PARSE_ERROR";
    }
    
    public DocumentParseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public DocumentParseException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}