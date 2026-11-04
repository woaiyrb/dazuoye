#Requires -Version 5.1
<#
  One-shot launcher for the local stack, no Docker needed.

  DeepTutor (8001) -> MySQL (6001) -> Redis (6379) -> API (4222)
    -> Gateway (8000) -> Web (3010) -> opens the browser on / (sign-in)

  Every service is skipped when its port is already listening, so running this
  repeatedly is safe. Usage:

    .\scripts\start-local.ps1            # start everything, open the browser
    .\scripts\start-local.ps1 -NoBrowser # start everything, no browser
    .\scripts\start-local.ps1 -BuildApi  # recompile the API before starting it
                                         # (needed after backend changes — the
                                         # API runs from dist/, not from source)

  MySQL note: mysqld cannot parse a --basedir/--datadir that contains
  non-ASCII characters (it truncates the path), so the launcher exposes the
  bundled server under a short ASCII junction (e.g. D:\mark-mysql) and starts
  it from there. The data still lives in .local-dev\mysql\data.
#>
param([switch]$NoBrowser, [switch]$BuildApi)

$ErrorActionPreference = "Stop"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $OutputEncoding
$env:PYTHONUTF8 = "1"
$env:PYTHONIOENCODING = "utf-8"
$env:LANG = "zh_CN.UTF-8"
$env:LC_ALL = "zh_CN.UTF-8"

$Root = Split-Path -Parent $PSScriptRoot
$LocalDev = Join-Path $Root ".local-dev"
$LogDir = Join-Path $LocalDev "logs"
$MySqlDir = Join-Path $LocalDev "mysql"
$RedisExe = Join-Path $LocalDev "redis5\redis-server.exe"
$env:JOB_WORKER_PID_FILE = Join-Path $LocalDev "jobs-worker.pid"
$env:JOB_WORKER_LIVENESS_FILE = Join-Path $LocalDev "jobs-worker.heartbeat"

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# --- MySQL ASCII junction -----------------------------------------------------
$Drive = (Get-Item $Root).PSDrive.Name
$Junction = "$($Drive):\mark-mysql"
$MySqlExe = Join-Path $Junction "bin\mysqld.exe"

function Ensure-MySqlJunction {
    if (Test-Path $MySqlExe) { return $true }
    if (-not (Test-Path (Join-Path $MySqlDir "bin\mysqld.exe"))) { return $false }
    if (Test-Path $Junction) { cmd.exe /c "rmdir `"$Junction`"" | Out-Null }
    cmd.exe /c "mklink /J `"$Junction`" `"$MySqlDir`"" | Out-Null
    return (Test-Path $MySqlExe)
}

function Ensure-MySqlInitialised {
    $dataDir = Join-Path $Junction "data"
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    if (Test-Path (Join-Path $dataDir "mysql")) { return }
    Write-Host "  initialising MySQL data directory (first run)..."
    & (Join-Path $Junction "bin\mysqld.exe") `
        --initialize-insecure --basedir=$Junction --datadir=$dataDir 2>&1 |
        Out-File -FilePath (Join-Path $LogDir "mysql-init.log") -Encoding utf8
}

function Ensure-MySqlDatabase {
    $client = Join-Path $Junction "bin\mysql.exe"
    $sql = "CREATE DATABASE IF NOT EXISTS mark CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'mark'@'%' IDENTIFIED WITH mysql_native_password BY 'markpassword'; GRANT ALL PRIVILEGES ON mark.* TO 'mark'@'%'; FLUSH PRIVILEGES;"
    & $client -u root --port=6001 --protocol=TCP -e $sql 2>&1 |
        Out-File -FilePath (Join-Path $LogDir "mysql-createdb.log") -Encoding utf8
}

function Test-Port {
    param([int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $pending = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(300)) { return $false }
        $client.EndConnect($pending)
        return $true
    }
    catch {
        return $false
    }
    finally {
        $client.Close()
    }
}

function Wait-ForPort {
    param([int]$Port, [int]$TimeoutSeconds = 90)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port $Port) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Start-MarkService {
    param(
        [string]$Name,
        [int]$Port,
        [string]$WorkingDir,
        [string]$Command,
        [int]$TimeoutSeconds = 90
    )

    if (Test-Port $Port) {
        Write-Host "  $Name already running on port $Port" -ForegroundColor DarkGray
        return $true
    }

    Write-Host "  starting $Name..."
    $log = Join-Path $LogDir "$Name.log"
    Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -ArgumentList @(
        "-NoProfile",
        "-Command",
        "cd '$WorkingDir'; $Command *>&1 | Out-File -FilePath '$log' -Encoding utf8"
    ) | Out-Null

    if (Wait-ForPort -Port $Port -TimeoutSeconds $TimeoutSeconds) {
        Write-Host "  $Name ready (port $Port)" -ForegroundColor Green
        return $true
    }

    Write-Host "  $Name did not open port $Port in time - see $log" -ForegroundColor Yellow
    return $false
}

Write-Host ""
Write-Host "=== homework auto-grading stack ===" -ForegroundColor Cyan

Write-Host "[1/7] DeepTutor (8001)"
if (Test-Port 8001) {
    Write-Host "  already running" -ForegroundColor DarkGray
}
else {
    $deepTutorExe = $env:DEEPTUTOR_EXE
    if (-not $deepTutorExe) {
        $deepTutorExe = @(
            "D:\Python311\Scripts\deeptutor.exe",
            "D:\Python\Scripts\deeptutor.exe"
        ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    }
    $deepTutorHome = $env:DEEPTUTOR_HOME
    if (-not $deepTutorHome -and (Test-Path "D:\deeptutor")) {
        $deepTutorHome = "D:\deeptutor"
    }

    if ($deepTutorExe) {
        Write-Host "  starting DeepTutor backend..."
        $deepTutorArgs = @("start")
        if ($deepTutorHome) { $deepTutorArgs += @("--home", $deepTutorHome) }
        $deepTutorArgs += @("--detach", "--no-browser")
        $deepTutorArgumentLine = ($deepTutorArgs | ForEach-Object {
            if ($_ -match "\s") { "`"$_`"" } else { $_ }
        }) -join " "
        Start-Process -FilePath $deepTutorExe -WindowStyle Hidden `
            -ArgumentList $deepTutorArgumentLine | Out-Null
        if (Wait-ForPort -Port 8001 -TimeoutSeconds 120) {
            Write-Host "  ready (port 8001)" -ForegroundColor Green
        }
        else {
            Write-Host "  backend did not become ready; AI routes will report the error" -ForegroundColor Yellow
        }
    }
    else {
        Write-Host "  DeepTutor executable not found; set DEEPTUTOR_EXE to enable auto-start" -ForegroundColor Yellow
    }
}

Write-Host "[2/7] MySQL (6001)"
if (Test-Port 6001) {
    Write-Host "  already running" -ForegroundColor DarkGray
}
elseif (-not (Ensure-MySqlJunction)) {
    Write-Host "  MySQL binaries missing - expected $MySqlDir" -ForegroundColor Red
}
else {
    Ensure-MySqlInitialised
    Write-Host "  starting MySQL..."
    $mysqlErrorLog = Join-Path (Join-Path $Junction "data") "mysql.err.log"
    Start-Process -FilePath $MySqlExe -WindowStyle Hidden `
        -ArgumentList "--basedir=$Junction", "--datadir=$(Join-Path $Junction 'data')", "--port=6001", `
        "--sql-mode=STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION", `
        "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci", `
        "--log-error=$mysqlErrorLog" | Out-Null
    if (Wait-ForPort -Port 6001 -TimeoutSeconds 60) {
        Write-Host "  ready (port 6001)" -ForegroundColor Green
        Start-Sleep -Seconds 2
        Ensure-MySqlDatabase
    }
    else {
        Write-Host "  failed - see $mysqlErrorLog" -ForegroundColor Red
    }
}

Write-Host "[3/7] Redis (6379)"
if (Test-Port 6379) {
    Write-Host "  already running" -ForegroundColor DarkGray
}
else {
    Write-Host "  starting Redis 5..."
    Start-Process -FilePath $RedisExe -WindowStyle Hidden -ArgumentList "--port", "6379" | Out-Null
    if (Wait-ForPort -Port 6379 -TimeoutSeconds 30) { Write-Host "  ready (port 6379)" -ForegroundColor Green }
    else { Write-Host "  failed to start" -ForegroundColor Red }
}

Write-Host "[4/7] API (4222)"
# The API runs compiled output (dist/main.js). Source edits — new controllers,
# new Prisma models — only take effect after a build, so rebuild on request (or
# whenever dist is missing) instead of silently serving stale endpoints.
$apiDir = Join-Path $Root "apps\api"
$apiMain = Join-Path $apiDir "dist\main.js"
if ($BuildApi -or -not (Test-Path $apiMain)) {
    Write-Host "  building api (this takes a minute)..."
    Push-Location $apiDir
    try {
        & npm run build *>&1 | Out-File -FilePath (Join-Path $LogDir "api-build.log") -Encoding utf8
        Write-Host "  api built" -ForegroundColor Green
    }
    catch {
        Write-Host "  api build failed - see $LogDir\api-build.log" -ForegroundColor Red
    }
    finally {
        Pop-Location
    }
}
Start-MarkService -Name "api" -Port 4222 `
    -WorkingDir $apiDir `
    -Command "npx dotenv -e ./dev.env -- node dist/main" | Out-Null

Write-Host "[5/7] API Gateway (8000)"
Start-MarkService -Name "gateway" -Port 8000 `
    -WorkingDir (Join-Path $Root "apps\api-gateway") `
    -Command "npx dotenv -e ./dev.env -- node dist/main" | Out-Null

Write-Host "[6/7] Web (3010)"
$webOk = Start-MarkService -Name "web" -Port 3010 -TimeoutSeconds 180 `
    -WorkingDir (Join-Path $Root "apps\web") `
    -Command "npx dotenv -e ../dev.env -- npx next dev -p 3010"

Write-Host "[7/7] Async jobs worker"
# The worker has no listening port — it registers itself through a Redis
# heartbeat, which is why the API logs "Jobs worker unavailable" when it is
# missing. AI grading and other background jobs run here.
$jobsPidValue = Get-Content $env:JOB_WORKER_PID_FILE -ErrorAction SilentlyContinue |
    Select-Object -First 1
$jobsRunning = if ($jobsPidValue -match "^\d+$") {
    Get-Process -Id ([int]$jobsPidValue) -ErrorAction SilentlyContinue
}
if (-not $jobsRunning) {
    $jobsRunning = Get-CimInstance Win32_Process -Filter "Name = 'node.exe'" -ErrorAction SilentlyContinue |
        Where-Object {
            $_.CommandLine -like "*apps\jobs*" -or
            $_.CommandLine -like "*apps/jobs*"
        }
}
if ($jobsRunning) {
    Write-Host "  already running" -ForegroundColor DarkGray
}
else {
    Write-Host "  starting jobs worker..."
    $jobsLog = Join-Path $LogDir "jobs.log"
    Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -ArgumentList @(
        "-NoProfile",
        "-Command",
        "cd '$(Join-Path $Root 'apps\jobs')'; npx dotenv -e ../api/dev.env -- npx ts-node -r tsconfig-paths/register src/main.ts *>&1 | Out-File -FilePath '$jobsLog' -Encoding utf8"
    ) | Out-Null
}

Write-Host ""
if ($webOk) {
    Write-Host "Sign in     http://localhost:3010/" -ForegroundColor Green
    Write-Host "Dashboard   http://localhost:3010/dashboard" -ForegroundColor Green
    Write-Host "Classes     http://localhost:3010/dashboard/classes" -ForegroundColor Green
    Write-Host "KnowPoints  http://localhost:3010/dashboard/knowledge-points" -ForegroundColor Green
    Write-Host "Students    http://localhost:3010/dashboard/students" -ForegroundColor Green
    Write-Host "Author      http://localhost:3010/author/1" -ForegroundColor Green
    Write-Host "Learner     http://localhost:3010/learner/1" -ForegroundColor Green
    if (-not $NoBrowser) {
        Write-Host "  opening http://localhost:3010/ (sign-in)"
        Start-Process "http://localhost:3010/"
    }
}
else {
    Write-Host "Web did not come up - see $LogDir\web.log" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Logs: $LogDir    Stop with: .\stop-dev.bat" -ForegroundColor DarkGray
