<div align="center">

# 🧪 测测 CeCe

**AI Testing Buddy** — 你的 AI 测试用例生成助手

[![JDK](https://img.shields.io/badge/JDK-17%2B-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.35.0-orange)](https://github.com/langchain4j/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> 上传接口文档或需求文档，AI 自动生成标准化测试用例，支持 Excel 导出和自动化脚本生成。

</div>

---

## 📸 快速预览

```
👋 欢迎使用 测测 CeCe
┌──────────────────────────────────────────┐
│  📄 我有接口文档  → 生成接口测试用例      │
│  📋 我有需求文档  → 生成 UI 测试用例      │
│  🧪 我想生成脚本  → 自动化测试脚本        │
│  🔗 我有 Swagger  → 从 URL 解析生成       │
│  💬 我想快速描述  → 自然语言输入生成       │
└──────────────────────────────────────────┘
```

---

## ✨ 功能特性

### 📄 接口文档 → 接口测试用例
| 功能 | 说明 |
|------|------|
| 支持格式 | `.docx` `.pdf` `.json` `.yaml` `.yml` + Swagger URL |
| 读取方式 | 全部读取 / 指定页码 / 指定章节 |
| 导出规格 | **24 列**完整版（用例编号～实际结果） |
| 分页算法 | 双模式：显式分页符 + 字符密度估算，适配千页大文档 |
| 质量校验 | 自动过滤占位符参数（`xxx`/`test`），强制量化预期结果 |

### 📋 需求文档 → UI 测试用例
| 功能 | 说明 |
|------|------|
| 支持格式 | `.docx` `.pdf` |
| 读取方式 | 全部读取 / 指定页码 / 指定章节 |
| 章节识别 | 自动识别 `第X章` / `1.1` / `一、` 等章节标题 |
| 导出规格 | **15 列**完整版（用例编号～测试日期） |
| 测试点分类 | 强制枚举值：正向/参数校验/边界/异常/UI校验/权限校验 |

### 🧪 自动化脚本生成
- **框架**：TestNG + RestAssured + Allure
- **输出**：完整 Maven 项目，打包为 `.zip` 下载
- **代码风格**：`given().when().then()` 链式结构

### 💬 自然语言输入
- 直接输入接口描述或功能需求
- 支持自定义测试要求（"重点测安全漏洞"）
- 适合快速验证单个接口或功能点

### 📦 批量扫描
- 扫描文件夹内所有文档
- 自动识别格式，统一生成 HTML 报告

---

## 🚀 快速开始

### 前置条件
- JDK 17+
- Maven 3.6+
- DeepSeek API Key（[申请地址](https://platform.deepseek.com/)）

### 1. 配置 API Key

```bash
# Windows
set DEEPSEEK_API_KEY=your-api-key

# 或直接修改 application.yml
```

```yaml
ai:
  api-key: ${DEEPSEEK_API_KEY:}
  model:
    provider: deepseek
```

### 2. 启动

```bash
# 方式一：双击运行
run.bat

# 方式二：Maven
mvn clean package -DskipTests
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

### 3. 访问

浏览器打开 **[http://localhost:8886](http://localhost:8886)**

---

## 🏗️ 技术栈

| 层 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3.0 |
| AI 集成 | LangChain4j 0.35.0 + DeepSeek API |
| AI 模型 | DeepSeek Chat / Qwen（可切换） |
| 文档解析 | Apache POI (DOCX) / PDFBox 3.0.2 (PDF) / Swagger Parser (JSON/YAML) |
| 报表导出 | Apache POI (Excel) / Allure + TestNG (自动化脚本) |
| 前端 | 原生 HTML + CSS + JavaScript（暗色技术风） |

## 📂 项目结构

```
src/main/java/com/example/demo/
├── controller/
│   └── TestCaseGeneratorController.java  # Web 接口
├── service/
│   ├── AiService.java          # AI 调用 + Prompt 管理
│   ├── WordParser.java         # DOCX 解析 + 分页算法
│   ├── PdfParser.java          # PDF 解析（精确页码）
│   ├── ExcelExporter.java      # Excel 导出（24列/15列）
│   ├── ScriptGenerator.java    # 自动化脚本生成
│   └── BatchScanService.java   # 批量扫描
├── DocumentProcessor.java      # 主逻辑入口
└── TestCase.java               # 测试用例实体
```

---

## ⚙️ 配置说明

### application.yml 关键配置

```yaml
server:
  port: 8886                     # 服务端口

ai:
  api-key: ${DEEPSEEK_API_KEY:}  # API Key（从环境变量读取）
  model:
    provider: deepseek           # deepseek / qwen
  prompts:
    # 可在此覆盖默认 Prompt（参见 AiService.java）
```

---

## 🐛 常见问题

**Q：生成用例为空？**  
A：检查 API Key 是否配置正确，或文档中是否包含接口定义关键词（`/api/`、`GET`、`POST` 等）。

**Q：页码不准确？**  
A：DOCX 没有物理页码，系统采用字符密度估算，误差约 ±3 页。如需精确页码，请使用 PDF 格式。

**Q：AI 生成的用例质量不高？**  
A：系统内置了质量校验管线，会自动过滤含占位符（`xxx`/`test`）的用例。也可在 `application.yml` 中调整 Prompt。

**Q：支持哪些 AI 模型？**  
A：DeepSeek Chat（默认）和通义千问 Qwen，可在 `application.yml` 中切换。

---

## 📜 License

[MIT License](LICENSE)

---

<div align="center">
Made with ❤️ for testers everywhere
</div>
