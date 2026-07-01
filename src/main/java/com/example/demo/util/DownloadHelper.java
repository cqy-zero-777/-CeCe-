package com.example.demo.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件下载响应工具（解决中文文件名导致 Tomcat 响应头编码失败的问题）
 */
public final class DownloadHelper {

    private DownloadHelper() {
    }

    public static String buildContentDisposition(String fileName) {
        String safeName = fileName == null || fileName.isBlank() ? "download.bin" : fileName.trim();
        String asciiFallback = safeName.replaceAll("[^\\x20-\\x7E]", "_");
        if (asciiFallback.isBlank() || asciiFallback.chars().allMatch(ch -> ch == '_' || ch == '.')) {
            asciiFallback = "download.bin";
        }
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
    }

    public static ResponseEntity<byte[]> fileDownload(byte[] data, String fileName, MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(fileName));
        headers.setContentLength(data.length);
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
