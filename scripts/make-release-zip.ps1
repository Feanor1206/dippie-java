[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

try {
    [Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
} catch {
}

$root = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $root "frontend"
$distIndex = Join-Path $frontendDir "dist\index.html"
$releaseDir = Join-Path $root "dist-release"
$timestamp = Get-Date -Format "yyyyMMdd-HHmm"
$zipPath = Join-Path $releaseDir ("Dippie-{0}.zip" -f $timestamp)

function Resolve-NpmCommand {
    $cmd = Get-Command "npm.cmd" -ErrorAction SilentlyContinue
    if ($null -ne $cmd) {
        return $cmd.Source
    }
    $localNpm = Join-Path $root ".tools\node\npm.cmd"
    if (Test-Path -LiteralPath $localNpm -PathType Leaf) {
        return $localNpm
    }
    throw "Không tìm thấy npm.cmd. Hãy cài Node.js 20+ (khuyến nghị 22 LTS) trước khi đóng gói."
}

Write-Host "[1/3] Build giao diện (frontend\dist)..." -ForegroundColor Cyan
$npmCmd = Resolve-NpmCommand
Push-Location -LiteralPath $frontendDir
try {
    Write-Host "  Chạy npm ci..."
    & $npmCmd ci
    if ($LASTEXITCODE -ne 0) {
        throw "Lệnh 'npm ci' thất bại (mã lỗi $LASTEXITCODE)."
    }

    Write-Host "  Chạy npm run build..."
    & $npmCmd run build
    if ($LASTEXITCODE -ne 0) {
        throw "Lệnh 'npm run build' thất bại (mã lỗi $LASTEXITCODE)."
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $distIndex -PathType Leaf)) {
    throw "Không tìm thấy frontend\dist\index.html sau khi build."
}

# Kiểm tra các tệp bắt buộc trước khi nén gói phát hành.
$requiredItems = @(
    "CHAY_DIPPIE.bat",
    "HUONG_DAN_CHAY.md",
    "run-all.sh",
    "frontend\dist\index.html",
    "data\catalog-by-series",
    "backend\springboot\mvnw",
    "backend\springboot\mvnw.cmd",
    "backend\springboot\.mvn\wrapper\maven-wrapper.jar",
    "backend\springboot\.mvn\wrapper\maven-wrapper.properties",
    "desktop\mvnw.cmd",
    "desktop\.mvn\wrapper\maven-wrapper.jar",
    "desktop\.mvn\wrapper\maven-wrapper.properties"
)
foreach ($rel in $requiredItems) {
    $full = Join-Path $root $rel
    if (-not (Test-Path -LiteralPath $full)) {
        throw "Thiếu thành phần bắt buộc để đóng gói: $rel"
    }
}

Write-Host "[2/3] Quét tệp dự án và tạo gói ZIP..." -ForegroundColor Cyan

# Các thư mục luôn bỏ qua khi đóng gói.
$excludedDirNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($d in @(
    ".git",
    "node_modules",
    "target",
    ".tools",
    ".worktrees",
    "thamkhao",
    "graphify-out",
    "dist-release",
    ".idea",
    ".vscode",
    ".vs",
    ".eclipse",
    ".settings",
    ".metadata",
    ".gradle",
    "mysql_data",
    "backups",
    "__pycache__"
)) {
    [void]$excludedDirNames.Add($d)
}

function Test-ExcludedRelativePath {
    param([string]$RelativeSlashPath)

    $segments = $RelativeSlashPath -split "/"
    for ($i = 0; $i -lt ($segments.Count - 1); $i++) {
        if ($excludedDirNames.Contains($segments[$i])) {
            return $true
        }
    }

    if ($RelativeSlashPath -match "(?i)^storage/videos/") {
        return $true
    }
    if ($RelativeSlashPath -match "(?i)^desktop/desktop\.properties$") {
        return $true
    }

    $fileName = $segments[$segments.Count - 1]
    if ($excludedDirNames.Contains($fileName)) {
        return $true
    }

    # Bỏ qua các tệp bí mật (.env*), chỉ giữ lại .env.example.
    if ($fileName -match "(?i)^\.env(\..+)?$" -and $fileName -notmatch "(?i)^\.env\.example$") {
        return $true
    }
    if ($fileName -match "(?i)\.env\.local$") {
        return $true
    }

    if ($fileName -match "(?i)^(\.DS_Store|Thumbs\.db|desktop\.ini|\.classpath|\.project)$") {
        return $true
    }
    if ($fileName -match "(?i)\.(iml|mv\.db|trace\.db|db|sql\.gz|tsbuildinfo|class|war|ear|mp4|mkv|mov|avi)$") {
        return $true
    }
    if ($fileName -match "(?i)^(npm-debug\.log|yarn-debug\.log|yarn-error\.log)") {
        return $true
    }

    return $false
}

function Collect-ReleaseFiles {
    param(
        [string]$BaseDir,
        [string]$CurrentDir,
        [System.Collections.Generic.List[object]]$Results
    )

    $entries = Get-ChildItem -LiteralPath $CurrentDir -Force
    foreach ($entry in $entries) {
        $rel = $entry.FullName.Substring($BaseDir.Length).TrimStart('\', '/') -replace '\\', '/'
        if ($entry.PSIsContainer) {
            if ($excludedDirNames.Contains($entry.Name)) {
                continue
            }
            if ($rel -match "(?i)^storage/videos$") {
                continue
            }
            Collect-ReleaseFiles -BaseDir $BaseDir -CurrentDir $entry.FullName -Results $Results
        } else {
            if (Test-ExcludedRelativePath -RelativeSlashPath $rel) {
                continue
            }
            $Results.Add([PSCustomObject]@{
                FullName     = $entry.FullName
                RelativePath = $rel
                FileName     = $entry.Name
            })
        }
    }
}

$filesToPack = New-Object 'System.Collections.Generic.List[object]'
Collect-ReleaseFiles -BaseDir $root -CurrentDir $root -Results $filesToPack

if (-not (Test-Path -LiteralPath $releaseDir -PathType Container)) {
    New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
}
if (Test-Path -LiteralPath $zipPath -PathType Leaf) {
    Remove-Item -LiteralPath $zipPath -Force
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$archive = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($item in $filesToPack) {
        # Giữ xuống dòng LF cho script Unix và mvnw để giải nén ra chạy được ngay trên macOS/Linux.
        if ($item.FileName -match "(?i)\.sh$" -or $item.FileName -ceq "mvnw") {
            $entry = $archive.CreateEntry($item.RelativePath, [System.IO.Compression.CompressionLevel]::Optimal)
            $bytes = [System.IO.File]::ReadAllBytes($item.FullName)
            $text = [System.Text.Encoding]::UTF8.GetString($bytes) -replace "`r`n", "`n"
            $lfBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($text)
            $stream = $entry.Open()
            try {
                $stream.Write($lfBytes, 0, $lfBytes.Length)
            } finally {
                $stream.Dispose()
            }
        } else {
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $archive,
                $item.FullName,
                $item.RelativePath,
                [System.IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
    }
} finally {
    $archive.Dispose()
}

$zipInfo = Get-Item -LiteralPath $zipPath
$sizeMb = [Math]::Round($zipInfo.Length / 1MB, 2)

Write-Host "[3/3] Đóng gói hoàn tất!" -ForegroundColor Green
Write-Host ("  Tệp ZIP : {0}" -f $zipInfo.FullName)
Write-Host ("  Số tệp  : {0}" -f $filesToPack.Count)
Write-Host ("  Dung lượng: {0} MB ({1:N0} byte)" -f $sizeMb, $zipInfo.Length)
