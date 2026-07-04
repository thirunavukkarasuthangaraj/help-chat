# start-dev.ps1 — start the backend and the widget demo (Windows)
# Usage:  .\scripts\start-dev.ps1
$root = Split-Path $PSScriptRoot -Parent

Write-Host "Starting help-chat backend on http://localhost:8090 ..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\backend'; mvn spring-boot:run"

Write-Host "Starting widget demo on http://localhost:3000/demo.html ..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\widget'; python -m http.server 3000"

Start-Sleep -Seconds 2
Write-Host ""
Write-Host "Backend : http://localhost:8090/chat/config/demo"
Write-Host "Demo    : http://localhost:3000/demo.html"
Start-Process "http://localhost:3000/demo.html"
