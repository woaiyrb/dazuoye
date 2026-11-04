#Requires -Version 5.1

$ErrorActionPreference = "Stop"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $OutputEncoding
$env:PYTHONUTF8 = "1"
$env:PYTHONIOENCODING = "utf-8"
$env:LANG = "zh_CN.UTF-8"
$env:LC_ALL = "zh_CN.UTF-8"

$Root = Split-Path -Parent $PSScriptRoot
$LogDir = Join-Path $Root ".local-dev\logs"
$StateDir = Split-Path -Parent $LogDir
$JavaDir = Join-Path $Root "apps\api-java"
$VueDir = Join-Path $Root "apps\web-vue"
$env:JAVA_API_PID_FILE = Join-Path $StateDir "java-api.pid"
$env:VUE_PID_FILE = Join-Path $StateDir "web-vue.pid"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

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
        Start-Sleep -Milliseconds 500
    }
    return $false
}

Write-Host ""
Write-Host "=== Vue + Java preview ===" -ForegroundColor Cyan

Write-Host "[1/4] DeepTutor (8001)"
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
    if (-not $deepTutorExe -or -not $deepTutorHome) {
        Write-Host "  DeepTutor not found; AI review remains in manual mode" -ForegroundColor Yellow
    }
    else {
        $deepTutorLog = Join-Path $LogDir "deeptutor.log"
        Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -ArgumentList @(
            "-NoProfile",
            "-Command",
            "cd '$deepTutorHome'; & '$deepTutorExe' serve --host 127.0.0.1 --port 8001 *>&1 | Out-File -FilePath '$deepTutorLog' -Encoding utf8"
        ) | Out-Null
        if (Wait-ForPort -Port 8001 -TimeoutSeconds 90) {
            Write-Host "  ready (port 8001)" -ForegroundColor Green
        }
        else {
            Write-Host "  AI unavailable; manual review remains available" -ForegroundColor Yellow
        }
    }
}

Write-Host "[2/4] Existing teaching services"
& (Join-Path $PSScriptRoot "start-local.ps1") -NoBrowser

Write-Host "[3/4] Java gateway (8080)"
if (Test-Port 8080) {
    Write-Host "  already running" -ForegroundColor DarkGray
}
else {
    $jar = Join-Path $JavaDir "target\mark-api-java-0.1.0.jar"
    if (-not (Test-Path $jar)) {
        Write-Host "  building Java gateway..."
        Push-Location $JavaDir
        try {
            & mvn -s .mvn/settings.xml -q -DskipTests package
        }
        finally {
            Pop-Location
        }
    }
    $javaLog = Join-Path $LogDir "java-api.log"
    Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -ArgumentList @(
        "-NoProfile",
        "-Command",
        "cd '$JavaDir'; java -jar '$jar' *>&1 | Out-File -FilePath '$javaLog' -Encoding utf8"
    ) | Out-Null
    if (Wait-ForPort -Port 8080 -TimeoutSeconds 90) {
        Write-Host "  ready (port 8080)" -ForegroundColor Green
    }
    else {
        Write-Host "  failed - see $javaLog" -ForegroundColor Red
        exit 1
    }
}

Write-Host "[4/4] Vue frontend (3020)"
if (Test-Port 3020) {
    Write-Host "  already running" -ForegroundColor DarkGray
}
else {
    $vueLog = Join-Path $LogDir "web-vue.log"
    Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -ArgumentList @(
        "-NoProfile",
        "-Command",
        "cd '$VueDir'; npm run dev *>&1 | Out-File -FilePath '$vueLog' -Encoding utf8"
    ) | Out-Null
    if (Wait-ForPort -Port 3020 -TimeoutSeconds 120) {
        Write-Host "  ready (port 3020)" -ForegroundColor Green
    }
    else {
        Write-Host "  failed - see $vueLog" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "Vue preview: http://localhost:3020/" -ForegroundColor Green
Write-Host "Java health: http://localhost:8080/health" -ForegroundColor Green
