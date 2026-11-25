<#
.SYNOPSIS
Sao lưu cơ sở dữ liệu MySQL của Dippie qua docker compose exec, nén gzip và giữ lại N bản gần nhất.

.EXAMPLE
.\scripts\backup-mysql.ps1 -BackupDir .\backups -Keep 14
#>
[CmdletBinding()]
param(
    [string]$BackupDir = (Join-Path (Split-Path -Parent $PSScriptRoot) 'backups'),
    [ValidateRange(1, 3650)]
    [int]$Keep = 14,
    [string]$ComposeFile = (Join-Path (Split-Path -Parent $PSScriptRoot) 'infrastructure\docker-compose.yml'),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) 'infrastructure\.env')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-DotEnvValue {
    param(
        [string]$Path,
        [string]$Key,
        [string]$DefaultValue
    )
    $matched = Get-Content -LiteralPath $Path | Where-Object { $_ -match "^\s*$([regex]::Escape($Key))=" } | Select-Object -Last 1
    if (!$matched) {
        return $DefaultValue
    }
    $val = ($matched -split '=', 2)[1].Trim()
    if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
        $val = $val.Substring(1, $val.Length - 2)
    }
    if ([string]::IsNullOrWhiteSpace($val)) {
        return $DefaultValue
    }
    return $val
}

if (!(Test-Path -LiteralPath $ComposeFile -PathType Leaf)) {
    throw "Không tìm thấy tệp Compose: $ComposeFile"
}
if (!(Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    throw "Không tìm thấy tệp môi trường: $EnvFile"
}
if (!(Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Không tìm thấy lệnh docker trong PATH.'
}

$mysqlDb = Get-DotEnvValue -Path $EnvFile -Key 'MYSQL_DB' -DefaultValue 'movie_rcm_db'
New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null

$timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss')
$outFile = Join-Path $BackupDir "dippie-mysql-$timestamp.sql.gz"
$tmpFile = "$outFile.tmp"

Write-Host "[dippie-backup] Bắt đầu sao lưu schema '$mysqlDb'..."

$dumpCommand = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --user=root --default-character-set=utf8mb4 --single-transaction --quick --routines --triggers --events --no-tablespaces --set-gtid-purged=OFF "$1"'

$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName = 'docker'
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

# Ghép chuỗi đối số có dấu ngoặc kép để chạy được trên cả Windows PowerShell 5.1 lẫn PowerShell 7
$escapedArgs = @(
    'compose',
    '--env-file', $EnvFile,
    '-f', $ComposeFile,
    'exec', '-T', 'mysql',
    'sh', '-c', $dumpCommand, '_', $mysqlDb
) | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }
$psi.Arguments = $escapedArgs -join ' '

$proc = [System.Diagnostics.Process]::new()
$proc.StartInfo = $psi

try {
    [void]$proc.Start()
    $stderrTask = $proc.StandardError.ReadToEndAsync()

    $fileStream = [System.IO.File]::Create($tmpFile)
    try {
        $gzipStream = [System.IO.Compression.GZipStream]::new($fileStream, [System.IO.Compression.CompressionLevel]::Optimal)
        try {
            $proc.StandardOutput.BaseStream.CopyTo($gzipStream)
        } finally {
            $gzipStream.Dispose()
        }
    } finally {
        $fileStream.Dispose()
    }

    $proc.WaitForExit()
    $stderr = $stderrTask.GetAwaiter().GetResult()

    if ($proc.ExitCode -ne 0) {
        throw "Sao lưu MySQL thất bại (mã $($proc.ExitCode)): $stderr"
    }

    $info = Get-Item -LiteralPath $tmpFile
    if ($info.Length -le 20) {
        throw 'Tệp sao lưu tạo ra bị rỗng.'
    }

    Move-Item -LiteralPath $tmpFile -Destination $outFile -Force
} catch {
    if (Test-Path -LiteralPath $tmpFile) {
        Remove-Item -LiteralPath $tmpFile -Force -ErrorAction SilentlyContinue
    }
    throw
} finally {
    $proc.Dispose()
}

$hash = (Get-FileHash -LiteralPath $outFile -Algorithm SHA256).Hash
Write-Host "[dippie-backup] Đã tạo bản sao lưu: $outFile"
Write-Host "[dippie-backup] SHA-256: $hash"

# Chỉ giữ $Keep bản mới nhất dựa theo tên tệp gắn giờ UTC
$oldBackups = Get-ChildItem -LiteralPath $BackupDir -Filter 'dippie-mysql-*.sql.gz' -File |
    Sort-Object -Property Name -Descending |
    Select-Object -Skip $Keep

foreach ($old in $oldBackups) {
    Write-Host "[dippie-backup] Xóa bản sao lưu cũ: $($old.Name)"
    Remove-Item -LiteralPath $old.FullName -Force
}
