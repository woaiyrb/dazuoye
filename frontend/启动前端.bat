@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo.
  echo 未检测到 Node.js，请先安装 Node.js 20.19 或更高版本。
  echo 下载地址：https://nodejs.org/
  echo.
  pause
  exit /b 1
)

if not exist "node_modules" (
  echo.
  echo 首次运行，正在安装前端依赖，请稍候...
  call npm install
  if errorlevel 1 (
    echo.
    echo 依赖安装失败，请检查网络后重试。
    echo.
    pause
    exit /b 1
  )
)

echo.
echo 正在启动错题星球前端，浏览器会自动打开。
echo 关闭此窗口即可停止服务器。
echo.
call npm run dev -- --open
echo.
echo 开发服务器已停止。
pause
