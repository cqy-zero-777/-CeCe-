package com.example.demo;

import com.example.demo.service.AiService;
import lombok.extern.slf4j.Slf4j;

/**
 * 测试配置加载
 */
@Slf4j
public class ConfigTest {
    public static void main(String[] args) {
        try {
            AiService.initModel();
            log.info("✅ 配置加载测试成功");
            log.info("AI模型已初始化");
        } catch (Exception e) {
            log.error("❌ 配置加载测试失败: {}", e.getMessage(), e);
        }
    }
}