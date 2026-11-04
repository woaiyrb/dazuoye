#Requires -Version 5.1
param(
  [switch]$NoBrowser,
  [int]$BackendPort = 8080,
  [int]$FrontendPort = 3020
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$LogDir = Join-Path $Root ".local-dev\logs"
$PidDir = Join-Path $Root ".local-dev\pids"
$BackendDir = Join-Path $Root "backend"
$FrontendDir = Join-Path $Root "frontend"
New-Item -ItemType Directory -Force -Path $LogDir, $PidDir | Out-Null

function Test-Port {
  param([int]$Port)
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $pending = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
    if (-not $pending.AsyncWaitHandle.WaitOne(400)) { return $false }
    $client.EndConnect($pending)
    return $true
  } catch {
    return $false
  } finally {
    $client.Close()
  }
}

function Wait-Port {
  param([int]$Port, [int]$TimeoutSeconds)
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-Port $Port) { return $true }
    Start-Sleep -Milliseconds 500
  }
  return $false
}

Write-Host ""
Write-Host "=== 初中作业自动批改系统 ===" -ForegroundColor Cyan

Write-Host "[1/3] MySQL / Redis"
if (-not (Test-Port 6001)) {
  Push-Location $Root
  try {
    docker compose up -d mysql redis
  } catch {
    Write-Host "Docker 启动失败。请确认 Docker Desktop 已启动，或自行启动 6001 端口的 MySQL。" -ForegroundColor Yellow
  } finally {
    Pop-Location
  }
}
if (Wait-Port -Port 6001 -TimeoutSeconds 45) {
  Write-Host "  MySQL 已就绪：127.0.0.1:6001" -ForegroundColor Green
} else {
  throw "MySQL 未在 6001 端口启动。"
}

if (Test-Port 6379) {
  Write-Host "  Redis 已就绪：127.0.0.1:6379" -ForegroundColor Green
} else {
  Write-Host "  Redis 未启动；当前作业核心流程不依赖 Redis。" -ForegroundColor DarkGray
}

Write-Host "[2/3] Java 后端"
if (Test-Port $BackendPort) {
  Write-Host "  端口 $BackendPort 已有服务，跳过启动。" -ForegroundColor DarkGray
} else {
  $jar = Join-Path $BackendDir "target\mark-api-java-0.1.0.jar"
  if (-not (Test-Path $jar)) {
    Push-Location $BackendDir
    try {
      mvn -q -DskipTests package
    } finally {
      Pop-Location
    }
  }
  $java = @(
    (Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue),
    "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe"
  ) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
  if (-not $java) { throw "未找到 Java 17，请先安装并配置 PATH。" }
  $env:JAVA_API_PORT = "$BackendPort"
  $backendLog = Join-Path $LogDir "java-api.log"
  $process = Start-Process -FilePath $java `
    -ArgumentList @("-jar", $jar) `
    -WorkingDirectory $BackendDir `
    -WindowStyle Hidden `
    -RedirectStandardOutput $backendLog `
    -RedirectStandardError (Join-Path $LogDir "java-api.err.log") `
    -PassThru
  $process.Id | Set-Content (Join-Path $PidDir "java-api.pid")
  if (-not (Wait-Port -Port $BackendPort -TimeoutSeconds 90)) {
    throw "Java 后端启动失败，请查看 $backendLog"
  }
  Write-Host "  Java 已就绪：http://127.0.0.1:$BackendPort" -ForegroundColor Green
}

Write-Host "[3/3] Vue 前端"
if (Test-Port $FrontendPort) {
  Write-Host "  端口 $FrontendPort 已有服务，跳过启动。" -ForegroundColor DarkGray
} else {
  if (-not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
    Push-Location $FrontendDir
    try { npm install } finally { Pop-Location }
  }
  $frontendLog = Join-Path $LogDir "vue.log"
  $target = "http://127.0.0.1:$BackendPort"
  $command = "cd '$FrontendDir'; `$env:VITE_PROXY_TARGET='$target'; npm run dev:http -- --port $FrontendPort *>&1 | Out-File -FilePath '$frontendLog' -Encoding utf8"
  $process = Start-Process -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-Command", $command) `
    -WindowStyle Hidden `
    -PassThru
  $process.Id | Set-Content (Join-Path $PidDir "vue.pid")
  if (-not (Wait-Port -Port $FrontendPort -TimeoutSeconds 120)) {
    throw "Vue 前端启动失败，请查看 $frontendLog"
  }
  Write-Host "  Vue 已就绪：http://127.0.0.1:$FrontendPort" -ForegroundColor Green
}

if (Test-Port 8001) {
  Write-Host "DeepTutor 已就绪：http://127.0.0.1:8001" -ForegroundColor Green
} else {
  Write-Host "DeepTutor 未启动；作业与判分可用，AI 出题/讲评会提示服务不可用。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "打开：http://127.0.0.1:$FrontendPort" -ForegroundColor Green
Write-Host "教师 teacher01 / 学生 S2024013 / 教研 research01，密码 123456" -ForegroundColor DarkGray
Write-Host "日志：$LogDir" -ForegroundColor DarkGray

if (-not $NoBrowser) {
  Start-Process "http://127.0.0.1:$FrontendPort"
}
