# new-app.ps1 — scaffold a new application for help-chat (Phase B helper)
# Usage:  .\scripts\new-app.ps1 -AppKey myapp -AppName "My App" [-ThemeColor "#0d7377"]
param(
    [Parameter(Mandatory = $true)] [string]$AppKey,
    [Parameter(Mandatory = $true)] [string]$AppName,
    [string]$ThemeColor = "#0d7377"
)

$root = Split-Path $PSScriptRoot -Parent
$docsDir = Join-Path $root "backend\src\main\resources\docs"
$docsFile = Join-Path $docsDir "$AppKey.md"

if (Test-Path $docsFile) {
    Write-Host "Docs file already exists: $docsFile" -ForegroundColor Yellow
} else {
    @"
# $AppName help documentation

Each ``## Heading`` section below is one answer. Write headings the way
users would ask the question — the matching works off those words.

## How do I get started?
Describe the first steps for a new user of $AppName here.

## How do I reset my password?
Describe the password reset flow of $AppName here.

## How do I contact support?
Describe how to reach the $AppName support team here.
"@ | Out-File -FilePath $docsFile -Encoding utf8
    Write-Host "Created docs file: $docsFile" -ForegroundColor Green
}

Write-Host ""
Write-Host "Now add this entry inside AppConfigStore.java (backend/src/main/java/com/helpchat/store/):" -ForegroundColor Cyan
Write-Host ""
@"
        apps.put("$AppKey", new AppConfig(
                "$AppKey",
                "$AppName",
                "$ThemeColor",
                "Hi! I'm the $AppName help assistant. Ask me anything.",
                List.of("How do I get started?",
                        "How do I reset my password?",
                        "How do I contact support?"),
                "You are a friendly, concise help assistant for $AppName. "
                        + "Answer ONLY from the provided help documentation. "
                        + "If the answer is not in the docs, say you don't know and suggest contacting support.",
                "docs/$AppKey.md"
        ));
"@
Write-Host ""
Write-Host "Then restart the backend and embed the widget with data-app-key=""$AppKey""." -ForegroundColor Cyan
Write-Host "Verify: http://localhost:8090/chat/config/$AppKey"
