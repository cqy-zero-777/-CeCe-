package com.example.demo.exception;

/**
 * 脚本生成异常
 */
public class ScriptGenerationException extends RuntimeException {
    private final String errorCode;
    
    public ScriptGenerationException(String message) {
        super(message);
        this.errorCode = "SCRIPT_GEN_ERROR";
    }
    
    public ScriptGenerationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public ScriptGenerationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}