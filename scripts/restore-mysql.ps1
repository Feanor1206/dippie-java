<#
.SYNOPSIS
Khôi phục cơ sở dữ liệu MySQL của Dippie từ tệp .sql.gz hoặc .sql qua docker compose exec.

.EXAMPLE
.\scripts\restore-mysql.ps1 -BackupFile .\backups\dippie-mysql-20261125-120000.sql.gz
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$BackupFile,
    [string]$ComposeFile = (Join-Path (Split-Path -Parent $PSScriptRoot) 'infrastructure\docker-compose.yml'),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) 'infrastructure\.env'),
    [switch]$Force
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

if (!(Test-Path -LiteralPath $BackupFile -PathType Leaf)) {
    throw "Không tìm thấy tệp sao lưu: $BackupFile"
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

$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
$mysqlDb = Get-DotEnvValue -Path $EnvFile -Key 'MYSQL_DB' -DefaultValue 'movie_rcm_db'

if (!$Force) {
    Write-Warning "Thao tác này sẽ ghi đè toàn bộ dữ liệu hiện tại trong schema '$mysqlDb'."
    Write-Host "Tệp khôi phục: $resolvedBackup"
    $answer = Read-Host 'Nhập YES để xác nhận khôi phục'
    if ($answer -cne 'YES') {
        Write-Host 'Đã hủy khôi phục.'
        exit 1
    }
}

Write-Host "[dippie-restore] Đang khôi phục schema '$mysqlDb' từ '$resolvedBackup'..."

$restoreCommand = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --default-character-set=utf8mb4 "$1"'

$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName = 'docker'
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

$escapedArgs = @(
    'compose',
    '--env-file', $EnvFile,
    '-f', $ComposeFile,
    'exec', '-T', 'mysql',
    'sh', '-c', $restoreCommand, '_', $mysqlDb
) | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }
$psi.Arguments = $escapedArgs -join ' '

$proc = [System.Diagnostics.Process]::new()
$proc.StartInfo = $psi

try {
    [void]$proc.Start()
    $stdoutTask = $proc.StandardOutput.ReadToEndAsync()
    $stderrTask = $proc.StandardError.ReadToEndAsync()

    $fileStream = [System.IO.File]::OpenRead($resolvedBackup)
    try {
        if ($resolvedBackup.EndsWith('.gz', [System.StringComparison]::OrdinalIgnoreCase)) {
            $gzipStream = [System.IO.Compression.GZipStream]::new($fileStream, [System.IO.Compression.CompressionMode]::Decompress)
            try {
                $gzipStream.CopyTo($proc.StandardInput.BaseStream)
            } finally {
                $gzipStream.Dispose()
            }
        } else {
            $fileStream.CopyTo($proc.StandardInput.BaseStream)
        }
    } finally {
        $fileStream.Dispose()
        $proc.StandardInput.Close()
    }

    $proc.WaitForExit()
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()

    if ($proc.ExitCode -ne 0) {
        throw "Khôi phục MySQL thất bại (mã $($proc.ExitCode)): $stderr $stdout"
    }
} finally {
    $proc.Dispose()
}

Write-Host '[dippie-restore] Khôi phục hoàn tất. Hãy kiểm tra /api/health và khởi động lại app nếu cần nạp lại bộ nhớ đệm.'
