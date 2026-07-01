package com.example.demo.config;

import com.example.demo.dto.ErrorResponse;
import com.example.demo.exception.AIServiceException;
import com.example.demo.exception.DocumentParseException;
import com.example.demo.exception.ScriptGenerationException;
import com.example.demo.exception.TestCaseGenerationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<ErrorResponse> handleAiServiceException(AIServiceException e) {
        log.error("AI服务调用失败: {} - {}", e.getErrorCode(), e.getMessage(), e);
        return ResponseEntity.status(503)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.aiServiceError("AI服务调用失败", e.getMessage()));
    }

    @ExceptionHandler(DocumentParseException.class)
    public ResponseEntity<ErrorResponse> handleDocumentParseException(DocumentParseException e) {
        log.warn("文档解析错误: {} - {}", e.getErrorCode(), e.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.badRequest(e.getErrorCode(), "文档解析失败", e.getMessage()));
    }

    @ExceptionHandler({TestCaseGenerationException.class, ScriptGenerationException.class})
    public ResponseEntity<ErrorResponse> handleGenerationException(RuntimeException e) {
        log.error("生成失败: {}", e.getMessage(), e);
        return ResponseEntity.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.serverError("生成失败", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.badRequest("文件过大", "上传文件超过大小限制，请缩小文档或调整 spring.servlet.multipart 配置"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.badRequest(e.getMessage()));
    }
}
