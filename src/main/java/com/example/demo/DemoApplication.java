package com.example.demo;

import com.example.demo.service.AiService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		AiService.initModel();
		showStartupInfo();
	}

	private void showStartupInfo() {
		System.out.println("\n");
		System.out.println("╔════════════════════════════════════════════════════════════════╗");
		System.out.println("║                  🎉 AI测试用例生成工具已启动！                    ║");
		System.out.println("╠════════════════════════════════════════════════════════════════╣");
		System.out.println("║  📍 访问地址: http://localhost:8886/api/                              ║");
		System.out.println("║  📖 Swagger文档: http://localhost:8886/swagger-ui.html            ║");
		System.out.println("║                                                                ║");
		System.out.println("║  📌 功能说明:                                                   ║");
		System.out.println("║     • Tab1: 从接口文档生成用例                                  ║");
		System.out.println("║     • Tab2: 从需求文档生成用例                                  ║");
		System.out.println("║     • Tab3: 生成自动化测试脚本                                  ║");
		System.out.println("║     • Tab4: 文件上传                                           ║");
		System.out.println("║     • Tab5: URL方式                                            ║");
		System.out.println("║     • Tab6: 自然语言                                           ║");
		System.out.println("║     • Tab7: 批量扫描                                           ║");
		System.out.println("╚════════════════════════════════════════════════════════════════╝");
		System.out.println("\n");
	}
}