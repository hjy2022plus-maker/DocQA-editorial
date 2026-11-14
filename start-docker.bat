@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem 等 Docker 引擎就绪；未启动时先拉起 Docker Desktop
docker info >nul 2>&1
if not errorlevel 1 goto ready

echo Docker Desktop 未运行，正在启动...
if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" (
    start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
)

:wait
timeout /t 3 /nobreak >nul
docker info >nul 2>&1
if errorlevel 1 goto wait

:ready
echo 正在启动 PaiSmart 全栈服务...
docker compose up -d --build
if errorlevel 1 (
    echo.
    echo 启动失败，请查看上方错误信息。
    pause
    exit /b 1
)

echo.
echo 启动完成：http://localhost:9527
start "" "http://localhost:9527"
pause
