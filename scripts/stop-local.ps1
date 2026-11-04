#Requires -Version 5.1
$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $PSScriptRoot
$PidDir = Join-Path $Root ".local-dev\pids"

function Stop-PidFile {
  param([string]$Name, [string]$Path)
  if (-not (Test-Path -LiteralPath $Path)) { return }
  $processId = Get-Content -LiteralPath $Path -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($processId -match "^\d+$" -and [int]$processId -gt 4) {
    $process = Get-Process -Id ([int]$processId) -ErrorAction SilentlyContinue
    if ($process) {
      Write-Host "停止 $Name（PID $processId）"
      Stop-Process -Id ([int]$processId) -Force
    }
  }
  Remove-Item -LiteralPath $Path -Force
}

Stop-PidFile -Name "Vue 前端" -Path (Join-Path $PidDir "vue.pid")
Stop-PidFile -Name "Java 后端" -Path (Join-Path $PidDir "java-api.pid")

Write-Host "MySQL 和 Redis 容器未删除，数据卷保持不变。"
Write-Host "如需停止数据库：docker compose stop mysql redis"
