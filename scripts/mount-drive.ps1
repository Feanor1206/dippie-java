[CmdletBinding()]
param(
    [ValidatePattern('^[^"\r\n]+$')]
    [string]$Remote = 'cinema-drive:',
    [ValidatePattern('^[^"\r\n]+$')]
    [string]$MountPoint = 'X:',
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$stateFile = Join-Path (Split-Path -Parent $PSScriptRoot) 'target\drive-mount.json'

function Stop-MountProcess {
    param([System.Diagnostics.Process]$Process)
    if ($Process -and !$Process.HasExited) {
        $taskkill = Join-Path $env:SystemRoot 'System32	askkill.exe'
        Start-Process $taskkill -ArgumentList "/T /F /PID $($Process.Id)" -WindowStyle Hidden -Wait | Out-Null
        if (!$Process.HasExited) {
            $Process.Kill()
        }
        if (!$Process.WaitForExit(5000)) {
            throw "Không thể dừng rclone (PID $($Process.Id))."
        }
    }
}

function Invoke-DriveMount {
    if (Test-Path -LiteralPath $stateFile) {
        $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
        $previous = Get-Process -Id ([int]$state.pid) -ErrorAction SilentlyContinue
        if ($previous -and !$previous.HasExited -and $previous.StartTime.Ticks -eq $state.startTimeTicks) {
            if (!$Stop) {
                throw 'Đã có tiến trình gắn Google Drive. Hãy chạy mount-drive.ps1 -Stop trước.'
            }
            Stop-MountProcess $previous
        }
        Remove-Item -LiteralPath $stateFile
    }
    if ($Stop) {
        return
    }

    $rclone = Get-Command rclone -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty Source
    if (!$rclone) {
        $wingetRoot = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet'
        $wingetPaths = @(
            "$wingetRoot\Links\rclone.exe"
            "$wingetRoot\Packages\Rclone.Rclone_*\rclone.exe"
            "$wingetRoot\Packages\Rclone.Rclone_*\*\rclone.exe"
        )
        $rclone = Get-Item -Path $wingetPaths -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName
    }
    if (!$rclone) {
        throw 'Không tìm thấy rclone trong PATH hoặc thư mục WinGet.'
    }
    if (!(Test-Path 'HKLM:\SOFTWARE\WinFsp') -and !(Test-Path 'HKLM:\SOFTWARE\WOW6432Node\WinFsp')) {
        throw 'Chưa cài WinFsp. Hãy cài WinFsp trước khi gắn Google Drive.'
    }

    $driveLetter = $MountPoint -match '^[a-z]:[\\/]?$'
    if ($driveLetter) {
        $MountPoint = $MountPoint.Substring(0, 2)
        $mountRoot = "$MountPoint\"
        if ([IO.Directory]::GetLogicalDrives() -contains $mountRoot) {
            throw "Ổ $MountPoint đang được sử dụng."
        }
    } else {
        $MountPoint = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($MountPoint).TrimEnd('\', '/')
        $mountRoot = $MountPoint
        if ((Test-Path -LiteralPath $mountRoot) -or ![IO.Directory]::Exists([IO.Path]::GetDirectoryName($mountRoot))) {
            throw "Đường dẫn gắn $MountPoint phải chưa tồn tại và có thư mục cha hợp lệ."
        }
    }

    $readyPath = [IO.Path]::Combine($mountRoot, 'VideoDieppie')
    $quotedRemote = '"' + ($Remote -replace '(\\+)$', '$1$1') + '"'
    $arguments = 'mount {0} "{1}" --read-only --vfs-cache-mode full --dir-cache-time 1h --vfs-read-chunk-size 32M' -f
        $quotedRemote, $MountPoint
    if ($driveLetter) {
        $arguments += ' --network-mode'
    }
    $process = Start-Process -FilePath $rclone -ArgumentList $arguments -WindowStyle Hidden -PassThru
    $mounted = $false
    try {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        while ($timer.Elapsed.TotalSeconds -lt 30 -and !$process.HasExited) {
            if ([IO.Directory]::Exists($readyPath)) {
                @{ pid = $process.Id; startTimeTicks = $process.StartTime.Ticks } |
                    ConvertTo-Json -Compress | Set-Content -LiteralPath $stateFile -Encoding UTF8
                $mounted = $true
                return
            }
            Start-Sleep -Milliseconds 250
        }
        if ($process.HasExited) {
            throw "rclone đã thoát (mã $($process.ExitCode)) trước khi tạo $readyPath."
        }
        throw "Hết 30 giây chờ rclone. Không tìm thấy $readyPath."
    } finally {
        if (!$mounted) {
            Stop-MountProcess $process
            if (Test-Path -LiteralPath $stateFile) {
                Remove-Item -LiteralPath $stateFile
            }
        }
    }
}

$stateDir = Split-Path -Parent $stateFile
if ($Stop -and !(Test-Path -LiteralPath $stateDir)) {
    return
}
New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
try {
    $stateLock = [IO.File]::Open("$stateFile.lock", 'OpenOrCreate', 'ReadWrite', 'None')
} catch [IO.IOException] {
    throw 'Không thể khóa trạng thái gắn Google Drive. Hãy chờ lệnh đang chạy hoàn tất.'
}
try {
    Invoke-DriveMount
} finally {
    $stateLock.Dispose()
}
