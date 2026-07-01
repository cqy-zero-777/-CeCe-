package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一错误响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private int code;
    private String errorCode;
    private String message;
    private String detail;
    private LocalDateTime timestamp;

    public static ErrorResponse of(int code, String message) {
        return new ErrorResponse(code, null, message, null, LocalDateTime.now());
    }

    public static ErrorResponse of(int code, String message, String detail) {
        return new ErrorResponse(code, null, message, detail, LocalDateTime.now());
    }
    
    public static ErrorResponse of(int code, String errorCode, String message, String detail) {
        return new ErrorResponse(code, errorCode, message, detail, LocalDateTime.now());
    }

    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse(400, "BAD_REQUEST", message, null, LocalDateTime.now());
    }

    public static ErrorResponse badRequest(String message, String detail) {
        return new ErrorResponse(400, "BAD_REQUEST", message, detail, LocalDateTime.now());
    }
    
    public static ErrorResponse badRequest(String errorCode, String message, String detail) {
        return new ErrorResponse(400, errorCode, message, detail, LocalDateTime.now());
    }

    public static ErrorResponse serverError(String message) {
        return new ErrorResponse(500, "SERVER_ERROR", message, null, LocalDateTime.now());
    }

    public static ErrorResponse serverError(String message, String detail) {
        return new ErrorResponse(500, "SERVER_ERROR", message, detail, LocalDateTime.now());
    }
    
    public static ErrorResponse serverError(String errorCode, String message, String detail) {
        return new ErrorResponse(500, errorCode, message, detail, LocalDateTime.now());
    }
    
    public static ErrorResponse docParseError(String message) {
        return new ErrorResponse(400, "DOC_PARSE_ERROR", message, null, LocalDateTime.now());
    }
    
    public static ErrorResponse docParseError(String message, String detail) {
        return new ErrorResponse(400, "DOC_PARSE_ERROR", message, detail, LocalDateTime.now());
    }
    
    public static ErrorResponse aiServiceError(String message) {
        return new ErrorResponse(500, "AI_SERVICE_ERROR", message, null, LocalDateTime.now());
    }
    
    public static ErrorResponse aiServiceError(String message, String detail) {
        return new ErrorResponse(500, "AI_SERVICE_ERROR", message, detail, LocalDateTime.now());
    }
    
    public static ErrorResponse testCaseGenError(String message) {
        return new ErrorResponse(500, "TESTCASE_GEN_ERROR", message, null, LocalDateTime.now());
    }
    
    public static ErrorResponse testCaseGenError(String message, String detail) {
        return new ErrorResponse(500, "TESTCASE_GEN_ERROR", message, detail, LocalDateTime.now());
    }
    
    public static ErrorResponse scriptGenError(String message) {
        return new ErrorResponse(500, "SCRIPT_GEN_ERROR", message, null, LocalDateTime.now());
    }
    
    public static ErrorResponse scriptGenError(String message, String detail) {
        return new ErrorResponse(500, "SCRIPT_GEN_ERROR", message, detail, LocalDateTime.now());
    }
    
    public static ErrorResponse noInterfacesFound() {
        return new ErrorResponse(400, "NO_INTERFACES", "未提取到接口定义", "文档中未识别到API接口定义，请检查文档内容", LocalDateTime.now());
    }
    
    public static ErrorResponse docContentTooShort() {
        return new ErrorResponse(400, "DOC_TOO_SHORT", "文档内容为空或太短", "请上传包含有效内容的文档", LocalDateTime.now());
    }
}