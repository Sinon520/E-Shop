# Start E-Shop AI service (FastAPI + LangGraph) on port 8087.
# ASCII only: PowerShell 5.1 needs UTF-8 BOM for non-ASCII script files.

param(
    [switch]$NoReload
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$appDir = Join-Path $root "eshop-ai"
$python = "C:\Users\asus\AppData\Local\Python\bin\python.exe"

if (-not (Test-Path $python)) {
    Write-Error "Python not found: $python"
}
if (-not (Test-Path (Join-Path $appDir "app\main.py"))) {
    Write-Error "app/main.py not found under $appDir"
}

if (-not (Test-Path (Join-Path $appDir ".env"))) {
    Write-Host "[warn] .env not found, running in rules fallback mode." -ForegroundColor Yellow
}

Set-Location $appDir

$argv = @("-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8087")
if (-not $NoReload) { $argv += "--reload" }

Write-Host "[eshop-ai] starting on http://127.0.0.1:8087" -ForegroundColor Cyan
& $python @argv
