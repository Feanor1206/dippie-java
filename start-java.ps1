[CmdletBinding()]
param(
    [ValidateSet("demo", "mysql")]
    [string]$Profile = "demo",
    [switch]$WithAi
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$frontendDir = Join-Path $root "frontend"
$distDir = Join-Path $frontendDir "dist"
$indexFile = Join-Path $distDir "index.html"
$fastapiDir = Join-Path $root "backend\fastapi"
$springbootDir = Join-Path $root "backend\springboot"
$mvnw = Join-Path $springbootDir "mvnw.cmd"
$javaDataDir = Join-Path $root "target\dippie-java"
$aiDataDir = Join-Path $javaDataDir "ai"

if (!(Test-Path -LiteralPath $distDir -PathType Container) -or !(Test-Path -LiteralPath $indexFile -PathType Leaf)) {
    Push-Location $root
    try {
        & npm.cmd --prefix frontend ci
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
        & npm.cmd --prefix frontend run build
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
}

$aiProcess = $null
$javaProcess = $null

function New-RandomBytes {
    param([int]$ByteCount)
    $bytes = New-Object byte[] $ByteCount
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return ,$bytes
}

function New-RandomHex {
    param([int]$ByteCount)
    return (-join ((New-RandomBytes -ByteCount $ByteCount) | ForEach-Object { $_.ToString("x2") }))
}

function New-RandomBase64 {
    param([int]$ByteCount)
    return [Convert]::ToBase64String((New-RandomBytes -ByteCount $ByteCount))
}

function Stop-ChildProcess {
    param([System.Diagnostics.Process]$Process)
    if ($null -ne $Process -and !$Process.HasExited) {
        try {
            & taskkill.exe /T /F /PID $Process.Id | Out-Null
            $Process.WaitForExit(5000) | Out-Null
        } catch {
        }
    }
}

try {
    if ($WithAi) {
        $env:CATALOG_SYNC_TOKEN = New-RandomHex -ByteCount 32
        if ([string]::IsNullOrWhiteSpace($env:APP_JWT_SECRET)) {
            $env:APP_JWT_SECRET = New-RandomBase64 -ByteCount 32
        }

        New-Item -ItemType Directory -Path $aiDataDir -Force | Out-Null
        $aiDatabase = (Join-Path $aiDataDir "ai.sqlite3").Replace("\", "/")

        $venvPython = Join-Path $root ".venv\Scripts\python.exe"
        $pythonExe = if (Test-Path -LiteralPath $venvPython -PathType Leaf) {
            $venvPython
        } else {
            (Get-Command python).Source
        }

        $previousAiOnly = $env:DIPPIE_AI_ONLY
        $previousDatabaseUrl = $env:DATABASE_URL
        $previousSyncState = $env:SYNC_STATE_PATH
        try {
            $env:DIPPIE_AI_ONLY = "true"
            $env:DATABASE_URL = "sqlite:///$aiDatabase"
            $env:SYNC_STATE_PATH = Join-Path $aiDataDir "catalog-sync.json"
            $aiProcess = Start-Process `
                -FilePath $pythonExe `
                -ArgumentList @("-m", "uvicorn", "main:app", "--host", "127.0.0.1", "--port", "8000", "--no-proxy-headers") `
                -WorkingDirectory $fastapiDir `
                -NoNewWindow `
                -PassThru
        } finally {
            if ($null -eq $previousAiOnly) {
                Remove-Item Env:DIPPIE_AI_ONLY -ErrorAction SilentlyContinue
            } else {
                $env:DIPPIE_AI_ONLY = $previousAiOnly
            }
            if ($null -eq $previousDatabaseUrl) {
                Remove-Item Env:DATABASE_URL -ErrorAction SilentlyContinue
            } else {
                $env:DATABASE_URL = $previousDatabaseUrl
            }
            if ($null -eq $previousSyncState) {
                Remove-Item Env:SYNC_STATE_PATH -ErrorAction SilentlyContinue
            } else {
                $env:SYNC_STATE_PATH = $previousSyncState
            }
        }
    }

    $env:SPRING_PROFILES_ACTIVE = $Profile
    $env:DIPPIE_DATA_DIR = $javaDataDir
    $javaProcess = Start-Process `
        -FilePath $mvnw `
        -ArgumentList @("-q", "spring-boot:run", "-Dspring-boot.run.profiles=$Profile") `
        -WorkingDirectory $springbootDir `
        -NoNewWindow `
        -PassThru

    Wait-Process -Id $javaProcess.Id
} finally {
    Stop-ChildProcess -Process $javaProcess
    Stop-ChildProcess -Process $aiProcess
}
