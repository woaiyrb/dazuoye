#Requires -Version 5.1

$ErrorActionPreference = "SilentlyContinue"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $OutputEncoding

$Root = Split-Path -Parent $PSScriptRoot
$LocalDev = Join-Path $Root ".local-dev"
$Drive = (Get-Item $Root).PSDrive.Name
$Junction = "$($Drive):\mark-mysql"

function Get-ListeningProcessIds {
    param([int]$Port)

    $ids = @()
    foreach ($line in (netstat -ano -p tcp)) {
        if ($line -match "^\s*TCP\s+\S+:$Port\s+\S+\s+LISTENING\s+(\d+)\s*$") {
            $ids += [int]$Matches[1]
        }
    }
    return $ids | Sort-Object -Unique
}

function Stop-AppPort {
    param([string]$Name, [int]$Port)

    $ids = @(Get-ListeningProcessIds -Port $Port)
    if ($ids.Count -eq 0) {
        Write-Host "  $Name not running"
        return
    }
    foreach ($processId in $ids) {
        if ($processId -le 4) { continue }
        $target = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if (-not $target) { continue }
        Write-Host "  stopping $Name (PID $processId)"
        Stop-Process -Id $processId -Force
    }
}

function Stop-FromPidFile {
    param([string]$Name, [string]$PidFile)

    if (-not (Test-Path $PidFile)) { return }
    $pidValue = Get-Content $PidFile -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($pidValue -match "^\d+$" -and [int]$pidValue -gt 4) {
        $target = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
        if ($target) {
            Write-Host "  stopping $Name (PID $pidValue)"
            Stop-Process -Id ([int]$pidValue) -Force
        }
    }
    Remove-Item -LiteralPath $PidFile -Force
}

Write-Host "[1/6] stopping app servers"
Stop-FromPidFile -Name "Vue preview" -PidFile (Join-Path $LocalDev "web-vue.pid")
Stop-FromPidFile -Name "Java gateway" -PidFile (Join-Path $LocalDev "java-api.pid")
Stop-AppPort -Name "Vue preview" -Port 3020
Stop-AppPort -Name "Java gateway" -Port 8080
Stop-AppPort -Name "web" -Port 3010
Stop-AppPort -Name "API gateway" -Port 8000
Stop-AppPort -Name "API" -Port 4222

Write-Host "[2/6] stopping jobs worker"
$jobsPidFile = Join-Path $LocalDev "jobs-worker.pid"
$jobsStopped = $false
if (Test-Path $jobsPidFile) {
    $jobsPid = Get-Content $jobsPidFile -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($jobsPid -match "^\d+$") {
        $jobsProcess = Get-Process -Id ([int]$jobsPid) -ErrorAction SilentlyContinue
        if ($jobsProcess) {
            Write-Host "  stopping worker (PID $jobsPid)"
            Stop-Process -Id ([int]$jobsPid) -Force
        }
        $jobsStopped = $true
    }
    Remove-Item -LiteralPath $jobsPidFile -Force
}
if (-not $jobsStopped) {
    $jobsProcesses = Get-CimInstance Win32_Process -Filter "Name = 'node.exe'" |
        Where-Object {
            $_.CommandLine -like "*$Root\apps\jobs*" -and
            $_.CommandLine -like "*src/main.ts*"
        }
    foreach ($jobsProcess in $jobsProcesses) {
        Write-Host "  stopping worker (PID $($jobsProcess.ProcessId))"
        Stop-Process -Id $jobsProcess.ProcessId -Force
    }
}

Write-Host "[3/6] stopping DeepTutor"
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
    $arguments = @("stop")
    if ($deepTutorHome) { $arguments += @("--home", $deepTutorHome) }
    & $deepTutorExe @arguments *> $null
}
# A detached launcher can lose its state file while its child processes are
# still alive (for example after a crash). Fall back to the two ports this
# project's DeepTutor launcher owns.
Start-Sleep -Milliseconds 500
Stop-AppPort -Name "DeepTutor backend" -Port 8001
Stop-AppPort -Name "DeepTutor frontend" -Port 3782

Write-Host "[4/6] stopping Redis"
$redisCli = Join-Path $LocalDev "redis5\redis-cli.exe"
if (Test-Path $redisCli) {
    & $redisCli -p 6379 shutdown nosave *> $null
}

Write-Host "[5/6] stopping MySQL"
$mysqlAdmin = Join-Path $Junction "bin\mysqladmin.exe"
if (Test-Path $mysqlAdmin) {
    & $mysqlAdmin --port=6001 --protocol=TCP -u root shutdown *> $null
}

Write-Host "[6/6] done"
Write-Host ""
Write-Host "All homework auto-grading services stopped."
