@echo off
chcp 65001 >nul
echo ========================================
echo   🚀 AI测试用例生成工具 - 启动脚本
echo ========================================
echo.

REM 检查API密钥是否配置
set API_KEY_CONFIGURED=0
if defined DEEPSEEK_API_KEY set API_KEY_CONFIGURED=1
if defined AI_API_KEY set API_KEY_CONFIGURED=1
if defined DASHSCOPE_API_KEY set API_KEY_CONFIGURED=1

if %API_KEY_CONFIGURED%==0 (
    echo ⚠️  警告: 未检测到 API 密钥环境变量!
    echo    请设置以下任一环境变量:
    echo    - DEEPSEEK_API_KEY (DeepSeek)
    echo    - AI_API_KEY (通用)
    echo    - DASHSCOPE_API_KEY (阿里云)
    echo.
    echo    仍然可以启动，但AI功能将不可用。
    echo.
)

echo 📍 项目端口: 8886
echo 📍 访问地址: http://localhost:8886
echo 📍 Swagger文档: http://localhost:8886/swagger-ui.html
echo.
echo ========================================
echo 正在启动项目...
echo ========================================
echo.

cd /d "%~dp0demo"

REM 启动Spring Boot项目
call mvn.cmd spring-boot:run

echo.
echo ========================================
echo 项目已停止运行
echo ========================================
pause