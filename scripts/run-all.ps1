[CmdletBinding()]
param(
    [switch]$Stop,
    [switch]$Reset,
    [switch]$NoDesktop,
    [switch]$NoBrowser,
    [int]$Port = 0,
    [string]$DataDir = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

try {
    [Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
} catch {
}

$root = Split-Path -Parent $PSScriptRoot

# mvnw.cmd của Maven bị vỡ khi đường dẫn có ( ) & ! % ^ (ví dụ thư mục giải nén "Dippie (1)").
# Gặp trường hợp đó thì tạo một junction tên an toàn trong LOCALAPPDATA trỏ về đúng thư mục dự án
# rồi chạy Maven qua đó. Junction không cần quyền admin, dữ liệu vẫn nằm ở thư mục gốc.
function Get-MavenSafeRoot {
    param([string]$ProjectRoot)
    if ($ProjectRoot -notmatch '[()&!%^]') {
        return $ProjectRoot
    }
    $hashBytes = [System.Security.Cryptography.SHA1]::Create().ComputeHash([System.Text.Encoding]::UTF8.GetBytes($ProjectRoot.ToLowerInvariant()))
    $shortHash = -join ($hashBytes[0..5] | ForEach-Object { $_.ToString("x2") })
    $linkParent = Join-Path $env:LOCALAPPDATA "Dippie\links"
    $linkPath = Join-Path $linkParent ("dippie-" + $shortHash)
    New-Item -ItemType Directory -Force -Path $linkParent | Out-Null
    $existing = Get-Item -LiteralPath $linkPath -Force -ErrorAction SilentlyContinue
    if ($existing) {
        $currentTarget = @($existing.Target)[0]
        if ($currentTarget -and ($currentTarget.TrimEnd('\') -ieq $ProjectRoot.TrimEnd('\'))) {
            return $linkPath
        }
        cmd.exe /c rmdir "$linkPath" | Out-Null
    }
    New-Item -ItemType Junction -Path $linkPath -Target $ProjectRoot | Out-Null
    return $linkPath
}

$mavenRoot = Get-MavenSafeRoot -ProjectRoot $root
$toolsDir = Join-Path $root ".tools"
$runDir = Join-Path $toolsDir "run"
$tmpDir = Join-Path $toolsDir "tmp"
$pidFile = Join-Path $runDir "pids.json"
$backendLog = Join-Path $runDir "backend.log"
$desktopLog = Join-Path $runDir "desktop.log"
$defaultDataDir = Join-Path $root "target\dippie-java"
$psExe = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
if (-not (Test-Path -LiteralPath $psExe -PathType Leaf)) {
    $psExe = "powershell.exe"
}

$catalogNames = @("jadoo", "larva", "ori", "doraemon", "trang_quynh", "peppa_pig")
$adoptiumJdk21Url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
$nodeIndexUrl = "https://nodejs.org/dist/index.json"
$nodeFallbackVersion = "v22.15.0"

function Enable-TlsModern {
    try {
        $protocols = [Net.SecurityProtocolType]::Tls12
        if ([Enum]::GetNames([Net.SecurityProtocolType]) -contains "Tls13") {
            $protocols = $protocols -bor [Net.SecurityProtocolType]::Tls13
        }
        [Net.ServicePointManager]::SecurityProtocol = $protocols
    } catch {
    }
}

function ConvertTo-PsSingleQuoted {
    param([string]$Value)
    if ($null -eq $Value) { return "''" }
    return "'" + ($Value -replace "'", "''") + "'"
}

function Invoke-ProcessCapture {
    param(
        [string]$FilePath,
        [string]$Arguments,
        [int]$TimeoutMs = 15000
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $FilePath
    $psi.Arguments = $Arguments
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    try {
        if (-not $proc.Start()) {
            return $null
        }
        $stdoutTask = $proc.StandardOutput.ReadToEndAsync()
        $stderrTask = $proc.StandardError.ReadToEndAsync()
        if (-not $proc.WaitForExit($TimeoutMs)) {
            try { $proc.Kill() } catch { }
            return $null
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        return [PSCustomObject]@{
            ExitCode = $proc.ExitCode
            StdOut   = $stdout
            StdErr   = $stderr
            Combined = "$stdout`n$stderr"
        }
    } catch {
        return $null
    } finally {
        $proc.Dispose()
    }
}

function Get-JavaCandidateInfo {
    param([string]$JavaExePath)

    if ([string]::IsNullOrWhiteSpace($JavaExePath) -or -not (Test-Path -LiteralPath $JavaExePath -PathType Leaf)) {
        return $null
    }

    $res = Invoke-ProcessCapture -FilePath $JavaExePath -Arguments "-XshowSettings:properties -version" -TimeoutMs 15000
    if ($null -eq $res -or $res.ExitCode -ne 0) {
        return $null
    }

    $major = 0
    $versionText = ""
    $javaHome = ""

    if ($res.Combined -match "(?m)^\s*java\.home\s*=\s*(.+?)\s*$") {
        $javaHome = $Matches[1].Trim()
    }
    if ($res.Combined -match "(?m)^\s*java\.version\s*=\s*(.+?)\s*$") {
        $versionText = $Matches[1].Trim()
    } elseif ($res.Combined -match 'version\s+"([^"]+)"') {
        $versionText = $Matches[1].Trim()
    }

    if ($versionText -match "^1\.(\d+)") {
        $major = [int]$Matches[1]
    } elseif ($versionText -match "^(\d+)") {
        $major = [int]$Matches[1]
    }

    if ($major -lt 21 -or [string]::IsNullOrWhiteSpace($javaHome)) {
        return $null
    }

    # Cần đủ JDK (có javac) để Maven biên dịch backend và app desktop.
    $javacExe = Join-Path $javaHome "bin\javac.exe"
    if (-not (Test-Path -LiteralPath $javacExe -PathType Leaf)) {
        return $null
    }

    return [PSCustomObject]@{
        JavaHome = $javaHome
        Version  = $versionText
        Major    = $major
    }
}

function Invoke-FileDownload {
    param(
        [string]$Url,
        [string]$Destination,
        [string]$Label
    )

    Enable-TlsModern
    $parent = Split-Path -Parent $Destination
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    if (Test-Path -LiteralPath $Destination -PathType Leaf) {
        Remove-Item -LiteralPath $Destination -Force -ErrorAction SilentlyContinue
    }

    $curlCmd = Get-Command "curl.exe" -ErrorAction SilentlyContinue
    if ($null -ne $curlCmd) {
        & $curlCmd.Source -fL --retry 3 --retry-delay 2 --connect-timeout 30 --progress-bar -o $Destination $Url
        if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $Destination -PathType Leaf) -and (Get-Item -LiteralPath $Destination).Length -gt 0) {
            return
        }
        Write-Host "  Tải bằng curl chưa thành công, thử lại bằng phương thức dự phòng..." -ForegroundColor Yellow
        Remove-Item -LiteralPath $Destination -Force -ErrorAction SilentlyContinue
    }

    $prevProgress = $ProgressPreference
    try {
        $ProgressPreference = "SilentlyContinue"
        Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing -TimeoutSec 600
    } catch {
        throw "Không thể tải $Label từ mạng ($Url). Kiểm tra kết nối Internet hoặc phần mềm diệt virus. Chi tiết: $($_.Exception.Message)"
    } finally {
        $ProgressPreference = $prevProgress
    }

    if (-not (Test-Path -LiteralPath $Destination -PathType Leaf) -or (Get-Item -LiteralPath $Destination).Length -eq 0) {
        throw "Tệp tải về cho $Label bị trống hoặc không hoàn chỉnh."
    }
}

function Expand-ZipClean {
    param(
        [string]$ZipPath,
        [string]$ExtractDir
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if (Test-Path -LiteralPath $ExtractDir) {
        Remove-Item -LiteralPath $ExtractDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $ExtractDir -Force | Out-Null
    [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $ExtractDir)
}

function Ensure-Jdk21 {
    Write-Host "[1/6] Kiểm tra Java (JDK 21 trở lên)..." -ForegroundColor Cyan

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidate = Get-JavaCandidateInfo -JavaExePath (Join-Path $env:JAVA_HOME "bin\java.exe")
        if ($null -ne $candidate) {
            Write-Host "  Dùng JDK sẵn có tại JAVA_HOME: $($candidate.JavaHome) (Java $($candidate.Version))" -ForegroundColor Green
            return $candidate.JavaHome
        }
    }

    $pathJava = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if ($null -ne $pathJava) {
        $candidate = Get-JavaCandidateInfo -JavaExePath $pathJava.Source
        if ($null -ne $candidate) {
            Write-Host "  Dùng JDK sẵn có trên PATH: $($candidate.JavaHome) (Java $($candidate.Version))" -ForegroundColor Green
            return $candidate.JavaHome
        }
    }

    $localJdkDir = Join-Path $toolsDir "jdk"
    if (Test-Path -LiteralPath $localJdkDir -PathType Container) {
        $candidate = Get-JavaCandidateInfo -JavaExePath (Join-Path $localJdkDir "bin\java.exe")
        if ($null -ne $candidate) {
            Write-Host "  Dùng JDK 21 di động đã tải sẵn: $($candidate.JavaHome) (Java $($candidate.Version))" -ForegroundColor Green
            return $candidate.JavaHome
        }
        Remove-Item -LiteralPath $localJdkDir -Recurse -Force -ErrorAction SilentlyContinue
    }

    Write-Host "  Chưa có JDK 21+. Đang tải bản Eclipse Temurin JDK 21 di động (~200 MB) vào .tools\jdk..." -ForegroundColor Yellow
    $zipPath = Join-Path $tmpDir "temurin-jdk21-win-x64.zip"
    $extractDir = Join-Path $tmpDir "jdk-extract"

    try {
        Invoke-FileDownload -Url $adoptiumJdk21Url -Destination $zipPath -Label "Eclipse Temurin JDK 21"
        Write-Host "  Đang giải nén JDK 21..."
        Expand-ZipClean -ZipPath $zipPath -ExtractDir $extractDir

        $javacFile = Get-ChildItem -LiteralPath $extractDir -Recurse -Filter "javac.exe" -File | Select-Object -First 1
        if ($null -eq $javacFile) {
            throw "Gói JDK tải về không chứa tệp bin\javac.exe."
        }
        $extractedJdkRoot = Split-Path -Parent (Split-Path -Parent $javacFile.FullName)

        if (Test-Path -LiteralPath $localJdkDir) {
            Remove-Item -LiteralPath $localJdkDir -Recurse -Force
        }
        Move-Item -LiteralPath $extractedJdkRoot -Destination $localJdkDir

        $verified = Get-JavaCandidateInfo -JavaExePath (Join-Path $localJdkDir "bin\java.exe")
        if ($null -eq $verified) {
            throw "Đã giải nén JDK vào '$localJdkDir' nhưng không chạy kiểm tra được."
        }

        Write-Host "  Đã cài JDK 21 di động thành công: $($verified.JavaHome) (Java $($verified.Version))" -ForegroundColor Green
        return $verified.JavaHome
    } finally {
        Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $extractDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Get-NodeCandidateDir {
    param([string]$NodeExePath)

    if ([string]::IsNullOrWhiteSpace($NodeExePath) -or -not (Test-Path -LiteralPath $NodeExePath -PathType Leaf)) {
        return $null
    }

    $res = Invoke-ProcessCapture -FilePath $NodeExePath -Arguments "-v" -TimeoutMs 10000
    if ($null -eq $res -or $res.ExitCode -ne 0) {
        return $null
    }

    if ($res.StdOut.Trim() -match "^v(\d+)\.") {
        $major = [int]$Matches[1]
        if ($major -ge 20) {
            $nodeDir = Split-Path -Parent $NodeExePath
            $npmCmd = Join-Path $nodeDir "npm.cmd"
            if (Test-Path -LiteralPath $npmCmd -PathType Leaf) {
                return [PSCustomObject]@{
                    NodeDir = $nodeDir
                    Version = $res.StdOut.Trim()
                }
            }
        }
    }
    return $null
}

function Get-NodeLtsDownloadInfo {
    Enable-TlsModern
    try {
        $prevProgress = $ProgressPreference
        $ProgressPreference = "SilentlyContinue"
        try {
            $releases = Invoke-RestMethod -Uri $nodeIndexUrl -TimeoutSec 20
        } finally {
            $ProgressPreference = $prevProgress
        }
        foreach ($entry in $releases) {
            if ($entry.lts -and ($entry.files -contains "win-x64-zip")) {
                if ($entry.version -match "^v(\d+)\." -and [int]$Matches[1] -ge 22) {
                    $ver = [string]$entry.version
                    return [PSCustomObject]@{
                        Version = $ver
                        Url     = "https://nodejs.org/dist/$ver/node-$ver-win-x64.zip"
                    }
                }
            }
        }
    } catch {
    }

    return [PSCustomObject]@{
        Version = $nodeFallbackVersion
        Url     = "https://nodejs.org/dist/$nodeFallbackVersion/node-$nodeFallbackVersion-win-x64.zip"
    }
}

function Ensure-FrontendDist {
    Write-Host "[2/6] Kiểm tra giao diện web (frontend\dist)..." -ForegroundColor Cyan
    $frontendDir = Join-Path $root "frontend"
    $indexHtml = Join-Path $frontendDir "dist\index.html"

    if (Test-Path -LiteralPath $indexHtml -PathType Leaf) {
        Write-Host "  Giao diện đã được build sẵn, không cần cài Node.js." -ForegroundColor Green
        return
    }

    Write-Host "  Chưa có frontend\dist\index.html, cần build giao diện trước khi chạy..." -ForegroundColor Yellow

    $nodeInfo = $null
    $pathNode = Get-Command "node.exe" -ErrorAction SilentlyContinue
    if ($null -ne $pathNode) {
        $nodeInfo = Get-NodeCandidateDir -NodeExePath $pathNode.Source
    }

    $localNodeDir = Join-Path $toolsDir "node"
    if ($null -eq $nodeInfo -and (Test-Path -LiteralPath $localNodeDir -PathType Container)) {
        $nodeInfo = Get-NodeCandidateDir -NodeExePath (Join-Path $localNodeDir "node.exe")
    }

    if ($null -eq $nodeInfo) {
        $dlInfo = Get-NodeLtsDownloadInfo
        Write-Host "  Chưa có Node.js 20+. Đang tải Node.js $($dlInfo.Version) di động vào .tools\node..." -ForegroundColor Yellow
        $zipPath = Join-Path $tmpDir "node-lts-win-x64.zip"
        $extractDir = Join-Path $tmpDir "node-extract"
        try {
            Invoke-FileDownload -Url $dlInfo.Url -Destination $zipPath -Label "Node.js $($dlInfo.Version)"
            Write-Host "  Đang giải nén Node.js..."
            Expand-ZipClean -ZipPath $zipPath -ExtractDir $extractDir

            $nodeExe = Get-ChildItem -LiteralPath $extractDir -Recurse -Filter "node.exe" -File | Select-Object -First 1
            if ($null -eq $nodeExe) {
                throw "Gói Node.js tải về không chứa node.exe."
            }
            $extractedNodeRoot = Split-Path -Parent $nodeExe.FullName
            if (Test-Path -LiteralPath $localNodeDir) {
                Remove-Item -LiteralPath $localNodeDir -Recurse -Force
            }
            Move-Item -LiteralPath $extractedNodeRoot -Destination $localNodeDir
            $nodeInfo = Get-NodeCandidateDir -NodeExePath (Join-Path $localNodeDir "node.exe")
            if ($null -eq $nodeInfo) {
                throw "Đã giải nén Node.js nhưng không chạy kiểm tra được."
            }
        } finally {
            Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $extractDir -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    Write-Host "  Dùng Node.js $($nodeInfo.Version) tại $($nodeInfo.NodeDir)" -ForegroundColor Green
    $env:PATH = "$($nodeInfo.NodeDir);$env:PATH"
    $npmCmd = Join-Path $nodeInfo.NodeDir "npm.cmd"

    Push-Location -LiteralPath $frontendDir
    try {
        Write-Host "  Đang cài đặt thư viện giao diện (npm ci)..."
        & $npmCmd ci
        if ($LASTEXITCODE -ne 0) {
            throw "Lệnh 'npm ci' trong thư mục frontend thất bại (mã lỗi $LASTEXITCODE)."
        }

        Write-Host "  Đang build giao diện (npm run build)..."
        & $npmCmd run build
        if ($LASTEXITCODE -ne 0) {
            throw "Lệnh 'npm run build' trong thư mục frontend thất bại (mã lỗi $LASTEXITCODE)."
        }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path -LiteralPath $indexHtml -PathType Leaf)) {
        throw "Build giao diện hoàn tất nhưng vẫn không thấy frontend\dist\index.html."
    }
    Write-Host "  Build giao diện thành công." -ForegroundColor Green
}

function Test-TcpPortFree {
    param([int]$PortNumber)

    try {
        $listeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
        foreach ($ep in $listeners) {
            if ($ep.Port -eq $PortNumber) {
                return $false
            }
        }
    } catch {
    }

    $tcp = $null
    try {
        $tcp = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Any, $PortNumber)
        $tcp.Start()
        $tcp.Stop()
        return $true
    } catch {
        return $false
    } finally {
        if ($null -ne $tcp) {
            try { $tcp.Stop() } catch { }
        }
    }
}

function Select-FreePort {
    param([int]$PreferredPort)

    $start = if ($PreferredPort -gt 0) { $PreferredPort } else { 3000 }
    for ($p = $start; $p -lt ($start + 100); $p++) {
        if (Test-TcpPortFree -PortNumber $p) {
            return $p
        }
    }
    throw "Không tìm được cổng trống trong khoảng $start..$($start + 99)."
}

function Read-PidState {
    if (-not (Test-Path -LiteralPath $pidFile -PathType Leaf)) {
        return $null
    }
    try {
        return Get-Content -LiteralPath $pidFile -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Save-PidState {
    param($StateObject)

    if (-not (Test-Path -LiteralPath $runDir -PathType Container)) {
        New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    }
    $json = $StateObject | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText($pidFile, $json, [System.Text.UTF8Encoding]::new($false))
}

function Stop-TrackedProcess {
    param(
        [int]$ProcessId,
        [string]$ExpectedStartUtc,
        [string]$Label
    )

    if ($ProcessId -le 0) {
        return $false
    }

    $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $proc) {
        return $false
    }

    # Đối chiếu giờ khởi tạo để không dừng nhầm tiến trình khác dùng lại PID cũ.
    if (-not [string]::IsNullOrWhiteSpace($ExpectedStartUtc)) {
        try {
            $recorded = [DateTime]::Parse($ExpectedStartUtc).ToUniversalTime()
            $actual = $proc.StartTime.ToUniversalTime()
            if ([Math]::Abs(($actual - $recorded).TotalSeconds) -gt 5) {
                return $false
            }
        } catch {
        }
    }

    $killRes = Invoke-ProcessCapture -FilePath "taskkill.exe" -Arguments "/T /F /PID $ProcessId" -TimeoutMs 10000
    if ($null -ne $killRes -and $killRes.ExitCode -eq 0) {
        Write-Host "  Đã dừng $Label (PID $ProcessId)." -ForegroundColor Green
        return $true
    }
    return $false
}

function Stop-Dippie {
    Write-Host "Đang dừng các tiến trình Dippie..." -ForegroundColor Cyan
    $state = Read-PidState
    if ($null -eq $state) {
        Write-Host "  Không tìm thấy tiến trình Dippie nào đang chạy từ trình khởi chạy này." -ForegroundColor Yellow
        return
    }

    $stoppedAny = $false
    if ($state.PSObject.Properties["desktopPid"] -and $state.desktopPid) {
        $startStr = if ($state.PSObject.Properties["desktopStartUtc"]) { [string]$state.desktopStartUtc } else { "" }
        if (Stop-TrackedProcess -ProcessId ([int]$state.desktopPid) -ExpectedStartUtc $startStr -Label "Dippie Admin Desktop") {
            $stoppedAny = $true
        }
    }
    if ($state.PSObject.Properties["backendPid"] -and $state.backendPid) {
        $startStr = if ($state.PSObject.Properties["backendStartUtc"]) { [string]$state.backendStartUtc } else { "" }
        if (Stop-TrackedProcess -ProcessId ([int]$state.backendPid) -ExpectedStartUtc $startStr -Label "cửa sổ Dippie Backend") {
            $stoppedAny = $true
            Start-Sleep -Milliseconds 400
        }
    }
    if ($state.PSObject.Properties["backendJavaPid"] -and $state.backendJavaPid) {
        $startStr = if ($state.PSObject.Properties["backendJavaStartUtc"]) { [string]$state.backendJavaStartUtc } else { "" }
        if (Stop-TrackedProcess -ProcessId ([int]$state.backendJavaPid) -ExpectedStartUtc $startStr -Label "máy chủ Java Dippie") {
            $stoppedAny = $true
        }
    }

    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    if (-not $stoppedAny) {
        Write-Host "  Các tiến trình Dippie trước đó đã kết thúc." -ForegroundColor Yellow
    } else {
        Write-Host "Hoàn tất dừng Dippie." -ForegroundColor Green
    }
}

function Reset-DippieData {
    $targetDataDir = if (-not [string]::IsNullOrWhiteSpace($DataDir)) { $DataDir } else { $defaultDataDir }
    Write-Host "Đặt lại dữ liệu demo Dippie" -ForegroundColor Cyan
    Write-Host "  Thư mục dữ liệu sẽ bị xóa: $targetDataDir" -ForegroundColor Yellow

    if (-not (Test-Path -LiteralPath $targetDataDir)) {
        Write-Host "  Chưa có dữ liệu demo để xóa. Lần chạy tiếp theo sẽ tự tạo mới." -ForegroundColor Green
        return
    }

    $answer = Read-Host "Bạn có chắc chắn muốn xóa toàn bộ dữ liệu demo? (nhập Y hoặc C để đồng ý)"
    if ($answer -notin @("y", "Y", "yes", "YES", "c", "C", "co", "CO", "có", "CÓ")) {
        Write-Host "Đã hủy thao tác đặt lại dữ liệu." -ForegroundColor Yellow
        return
    }

    # Dừng máy chủ do trình khởi chạy mở trước khi xóa tệp H2 đang bị khóa.
    if (Test-Path -LiteralPath $pidFile -PathType Leaf) {
        Stop-Dippie
        Start-Sleep -Seconds 1
    }

    Remove-Item -LiteralPath $targetDataDir -Recurse -Force
    Write-Host "Đã xóa dữ liệu demo tại '$targetDataDir'." -ForegroundColor Green
    Write-Host "Lần chạy CHAY_DIPPIE.bat tiếp theo sẽ tự động tạo mới và nhập lại 6 bộ phim." -ForegroundColor Green
}

function Read-LogTail {
    param(
        [string]$Path,
        [int]$LineCount = 40
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return @()
    }
    try {
        $fs = [System.IO.FileStream]::new($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            $reader = New-Object System.IO.StreamReader($fs, [System.Text.Encoding]::UTF8)
            try {
                $all = $reader.ReadToEnd()
            } finally {
                $reader.Dispose()
            }
        } finally {
            $fs.Dispose()
        }
        $lines = $all -split "\r?\n"
        if ($lines.Count -gt $LineCount) {
            return $lines[($lines.Count - $LineCount)..($lines.Count - 1)]
        }
        return $lines
    } catch {
        return @()
    }
}

function Find-ListeningPid {
    param([int]$PortNumber)

    try {
        $conn = Get-NetTCPConnection -LocalPort $PortNumber -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $conn -and $conn.OwningProcess -gt 0) {
            return [int]$conn.OwningProcess
        }
    } catch {
    }
    return 0
}

function Get-ProcessStartUtcString {
    param([int]$ProcessId)

    if ($ProcessId -le 0) { return "" }
    try {
        $proc = Get-Process -Id $ProcessId -ErrorAction Stop
        return $proc.StartTime.ToUniversalTime().ToString("o")
    } catch {
        return ""
    }
}

function Write-BackendRunnerScript {
    param(
        [string]$ScriptOutPath,
        [string]$JavaHomePath,
        [int]$SelectedPort,
        [string]$LogFilePath,
        [string]$CustomDataDir
    )

    $startJavaPath = Join-Path $mavenRoot "start-java.ps1"
    $springbootDir = Join-Path $mavenRoot "backend\springboot"
    $mvnwPath = Join-Path $springbootDir "mvnw.cmd"

    # Tạo script phụ để vừa in log ra cửa sổ vừa ghi tệp nhật ký UTF-8.
    $lines = @(
        '$ErrorActionPreference = "Continue"'
        ('try {{ $Host.UI.RawUI.WindowTitle = {0} }} catch {{ }}' -f (ConvertTo-PsSingleQuoted "Dippie Backend (cổng $SelectedPort)"))
        'try {'
        '    [Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)'
        '    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)'
        '    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)'
        '    & "$env:SystemRoot\System32\chcp.com" 65001 | Out-Null'
        '} catch {'
        '}'
        ('$env:JAVA_HOME = {0}' -f (ConvertTo-PsSingleQuoted $JavaHomePath))
        '$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"'
        ('$env:PORT = {0}' -f (ConvertTo-PsSingleQuoted "$SelectedPort"))
        ('$logPath = {0}' -f (ConvertTo-PsSingleQuoted $LogFilePath))
        ('$psExe = {0}' -f (ConvertTo-PsSingleQuoted $psExe))
        ('$fs = [System.IO.FileStream]::new($logPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)')
        '$writer = New-Object System.IO.StreamWriter($fs, [System.Text.UTF8Encoding]::new($false))'
        '$writer.AutoFlush = $true'
        '$exitCode = 0'
        'try {'
        ('    Write-Host {0} -ForegroundColor Cyan' -f (ConvertTo-PsSingleQuoted "Đang khởi động Dippie Backend trên cổng $SelectedPort..."))
        ('    Write-Host {0} -ForegroundColor DarkGray' -f (ConvertTo-PsSingleQuoted "Giữ nguyên cửa sổ này khi đang dùng Dippie. Đóng cửa sổ để dừng máy chủ."))
    )

    if ([string]::IsNullOrWhiteSpace($CustomDataDir)) {
        $lines += @(
            ('    $startJava = {0}' -f (ConvertTo-PsSingleQuoted $startJavaPath))
            '    & $psExe -NoProfile -ExecutionPolicy Bypass -File $startJava -Profile demo 2>&1 | ForEach-Object {'
            '        $line = "$_"'
            '        Write-Host $line'
            '        $writer.WriteLine($line)'
            '    }'
            '    $exitCode = $LASTEXITCODE'
        )
    } else {
        # start-java.ps1 luôn ghi đè DIPPIE_DATA_DIR, nên khi dùng thư mục dữ liệu riêng thì chạy thẳng mvnw.cmd.
        $lines += @(
            '    $env:SPRING_PROFILES_ACTIVE = "demo"'
            ('    $env:DIPPIE_DATA_DIR = {0}' -f (ConvertTo-PsSingleQuoted $CustomDataDir))
            ('    $springbootDir = {0}' -f (ConvertTo-PsSingleQuoted $springbootDir))
            ('    $mvnw = {0}' -f (ConvertTo-PsSingleQuoted $mvnwPath))
            '    Push-Location -LiteralPath $springbootDir'
            '    try {'
            '        & $mvnw -q -Pdemo spring-boot:run "-Dspring-boot.run.profiles=demo" 2>&1 | ForEach-Object {'
            '            $line = "$_"'
            '            Write-Host $line'
            '            $writer.WriteLine($line)'
            '        }'
            '        $exitCode = $LASTEXITCODE'
            '    } finally {'
            '        Pop-Location'
            '    }'
        )
    }

    $lines += @(
        '} finally {'
        '    $writer.Dispose()'
        '    $fs.Dispose()'
        '}'
        'exit $exitCode'
    )

    $content = ($lines -join "`r`n") + "`r`n"
    [System.IO.File]::WriteAllText($ScriptOutPath, $content, [System.Text.UTF8Encoding]::new($true))
}

function Write-DesktopRunnerScript {
    param(
        [string]$ScriptOutPath,
        [string]$JavaHomePath,
        [string]$LogFilePath
    )

    $startDesktopPath = Join-Path $mavenRoot "start-desktop.ps1"
    $lines = @(
        '$ErrorActionPreference = "Continue"'
        'try { $Host.UI.RawUI.WindowTitle = "Dippie Admin Desktop" } catch { }'
        'try {'
        '    [Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)'
        '    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)'
        '    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)'
        '    & "$env:SystemRoot\System32\chcp.com" 65001 | Out-Null'
        '} catch {'
        '}'
        ('$env:JAVA_HOME = {0}' -f (ConvertTo-PsSingleQuoted $JavaHomePath))
        '$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"'
        ('$logPath = {0}' -f (ConvertTo-PsSingleQuoted $LogFilePath))
        ('$psExe = {0}' -f (ConvertTo-PsSingleQuoted $psExe))
        ('$startDesktop = {0}' -f (ConvertTo-PsSingleQuoted $startDesktopPath))
        '$fs = [System.IO.FileStream]::new($logPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)'
        '$writer = New-Object System.IO.StreamWriter($fs, [System.Text.UTF8Encoding]::new($false))'
        '$writer.AutoFlush = $true'
        '$exitCode = 0'
        'try {'
        '    Write-Host "Đang khởi động ứng dụng quản trị Dippie Admin (JavaFX)..." -ForegroundColor Cyan'
        '    & $psExe -NoProfile -ExecutionPolicy Bypass -File $startDesktop 2>&1 | ForEach-Object {'
        '        $line = "$_"'
        '        Write-Host $line'
        '        $writer.WriteLine($line)'
        '    }'
        '    $exitCode = $LASTEXITCODE'
        '} finally {'
        '    $writer.Dispose()'
        '    $fs.Dispose()'
        '}'
        'if ($exitCode -ne 0) {'
        '    Write-Host ""'
        '    Write-Host "Không thể chạy Dippie Admin (mã lỗi $exitCode). Xem log tại: $logPath" -ForegroundColor Red'
        '    Read-Host "Nhấn Enter để đóng cửa sổ"'
        '}'
        'exit $exitCode'
    )

    $content = ($lines -join "`r`n") + "`r`n"
    [System.IO.File]::WriteAllText($ScriptOutPath, $content, [System.Text.UTF8Encoding]::new($true))
}

function Import-DemoCatalogs {
    param([int]$SelectedPort)

    Write-Host "[4/6] Kiểm tra dữ liệu danh mục phim mẫu..." -ForegroundColor Cyan
    $apiBase = "http://127.0.0.1:$SelectedPort/api"

    $existingCount = 0
    try {
        $cards = Invoke-RestMethod -Uri "$apiBase/catalog/series/cards" -TimeoutSec 15 -ErrorAction Stop
        $existingCount = @($cards).Count
    } catch {
        $existingCount = 0
    }

    if ($existingCount -ge $catalogNames.Count) {
        Write-Host "  Đã có sẵn $existingCount bộ phim trong cơ sở dữ liệu, bỏ qua bước nhập." -ForegroundColor Green
        return
    }

    $importScript = Join-Path $root "scripts\import_preview_catalog.ps1"
    if (-not (Test-Path -LiteralPath $importScript -PathType Leaf)) {
        throw "Không tìm thấy script nhập danh mục: $importScript"
    }

    Write-Host "  Hiện có $existingCount/$($catalogNames.Count) bộ phim. Đang nhập 6 gói danh mục mẫu..." -ForegroundColor Yellow
    # Tự dựng SecureString thay vì gọi ConvertTo-SecureString: cmdlet đó nằm trong module Security,
    # module này không nạp được khi PowerShell 5.1 bị gọi từ PowerShell 7 (PSModulePath lẫn đường dẫn của bản 7).
    $adminPassword = New-Object System.Security.SecureString
    foreach ($passwordChar in "password123".ToCharArray()) {
        $adminPassword.AppendChar($passwordChar)
    }
    $index = 0
    foreach ($name in $catalogNames) {
        $index++
        $catalogFile = Join-Path $root ("data\catalog-by-series\catalog.{0}.json" -f $name)
        if (-not (Test-Path -LiteralPath $catalogFile -PathType Leaf)) {
            throw "Thiếu tệp danh mục: $catalogFile"
        }
        Write-Host ("  [{0}/{1}] Đang nhập danh mục: {2}..." -f $index, $catalogNames.Count, $name) -NoNewline
        $importOutput = $null
        try {
            $importOutput = & $importScript `
                -CatalogPath $catalogFile `
                -ApiBaseUrl $apiBase `
                -Username "admin" `
                -Password $adminPassword 6>&1
            Write-Host " xong." -ForegroundColor Green
        } catch {
            Write-Host " thất bại!" -ForegroundColor Red
            if ($importOutput) {
                $importOutput | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkYellow }
            }
            throw "Không nhập được danh mục '$name': $($_.Exception.Message)"
        }
    }

    $afterCards = Invoke-RestMethod -Uri "$apiBase/catalog/series/cards" -TimeoutSec 15 -ErrorAction Stop
    $afterCount = @($afterCards).Count
    Write-Host "  Đã hoàn tất nhập danh mục ($afterCount bộ phim sẵn sàng)." -ForegroundColor Green
}

# Xử lý cờ -Stop và -Reset trước khi khởi động.
if ($Stop) {
    Stop-Dippie
    exit 0
}

if ($Reset) {
    Reset-DippieData
    exit 0
}

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  TRÌNH KHỞI CHẠY DIPPIE (CHẾ ĐỘ DEMO)" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path -LiteralPath $runDir -PathType Container)) {
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
}

# Bước 1: tìm hoặc tải JDK 21+ cho phiên chạy này.
$resolvedJavaHome = Ensure-Jdk21
$env:JAVA_HOME = $resolvedJavaHome
$env:PATH = "$resolvedJavaHome\bin;$env:PATH"

# Bước 2: kiểm tra frontend\dist, thiếu thì mới build.
Ensure-FrontendDist

# Bước 3: chọn cổng trống rồi chạy backend trong cửa sổ thu nhỏ.
$preferredPort = if ($Port -gt 0) { $Port } else { 3000 }
$selectedPort = Select-FreePort -PreferredPort $preferredPort
$env:PORT = "$selectedPort"

if ($selectedPort -ne $preferredPort) {
    Write-Host "[3/6] Cổng $preferredPort đang bận, tự động chuyển sang cổng $selectedPort." -ForegroundColor Yellow
} else {
    Write-Host "[3/6] Khởi động Dippie Backend trên cổng $selectedPort..." -ForegroundColor Cyan
}

$resolvedDataDir = ""
if (-not [string]::IsNullOrWhiteSpace($DataDir)) {
    if ([System.IO.Path]::IsPathRooted($DataDir)) {
        $resolvedDataDir = $DataDir
    } else {
        $resolvedDataDir = Join-Path $root $DataDir
    }
    if (-not (Test-Path -LiteralPath $resolvedDataDir -PathType Container)) {
        New-Item -ItemType Directory -Path $resolvedDataDir -Force | Out-Null
    }
    Write-Host "  Dùng thư mục dữ liệu riêng: $resolvedDataDir" -ForegroundColor DarkGray
}

$backendRunner = Join-Path $runDir "launch-backend.ps1"
Write-BackendRunnerScript `
    -ScriptOutPath $backendRunner `
    -JavaHomePath $resolvedJavaHome `
    -SelectedPort $selectedPort `
    -LogFilePath $backendLog `
    -CustomDataDir $resolvedDataDir

$backendProc = Start-Process `
    -FilePath $psExe `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ('"{0}"' -f $backendRunner)) `
    -WorkingDirectory $root `
    -WindowStyle Minimized `
    -PassThru

$pidState = [ordered]@{
    port                = $selectedPort
    dataDir             = if ([string]::IsNullOrWhiteSpace($resolvedDataDir)) { $defaultDataDir } else { $resolvedDataDir }
    backendPid          = $backendProc.Id
    backendStartUtc     = Get-ProcessStartUtcString -ProcessId $backendProc.Id
    backendJavaPid      = 0
    backendJavaStartUtc = ""
    desktopPid          = 0
    desktopStartUtc     = ""
    startedAtUtc        = (Get-Date).ToUniversalTime().ToString("o")
}
Save-PidState -StateObject $pidState

Write-Host "  Đang chờ máy chủ sẵn sàng (lần đầu tải thư viện Maven có thể mất 2-6 phút, tối đa 10 phút)" -NoNewline

$healthUrl = "http://127.0.0.1:$selectedPort/api/health"
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$timeoutSeconds = 600
$isHealthy = $false
$dotCount = 0

while ($stopwatch.Elapsed.TotalSeconds -lt $timeoutSeconds) {
    Start-Sleep -Seconds 2
    $backendProc.Refresh()

    if ($backendProc.HasExited) {
        Write-Host ""
        Write-Host "  [LỖI] Tiến trình Dippie Backend đã dừng đột ngột (mã thoát: $($backendProc.ExitCode))." -ForegroundColor Red
        $tailLines = Read-LogTail -Path $backendLog -LineCount 40
        if ($tailLines.Count -gt 0) {
            Write-Host "  --- Các dòng nhật ký cuối cùng ($backendLog) ---" -ForegroundColor Yellow
            foreach ($line in $tailLines) {
                Write-Host "  $line"
            }
            Write-Host "  -----------------------------------------------" -ForegroundColor Yellow
        }
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
        throw "Không thể khởi động Dippie Backend. Kiểm tra nhật ký ở trên hoặc xem HUONG_DAN_CHAY.md."
    }

    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3 -ErrorAction Stop
        if ($null -ne $health -and $health.success -eq $true) {
            $isHealthy = $true
            break
        }
    } catch {
    }

    $dotCount++
    Write-Host "." -NoNewline
    if (($dotCount % 15) -eq 0) {
        $elapsedSec = [int]$stopwatch.Elapsed.TotalSeconds
        Write-Host " (${elapsedSec}s)"
        Write-Host "  " -NoNewline
    }
}

Write-Host ""
if (-not $isHealthy) {
    $tailLines = Read-LogTail -Path $backendLog -LineCount 40
    Stop-Dippie
    if ($tailLines.Count -gt 0) {
        Write-Host "  --- Các dòng nhật ký cuối cùng ($backendLog) ---" -ForegroundColor Yellow
        foreach ($line in $tailLines) {
            Write-Host "  $line"
        }
        Write-Host "  -----------------------------------------------" -ForegroundColor Yellow
    }
    throw "Quá thời gian chờ 10 phút nhưng Dippie Backend vẫn chưa phản hồi tại $healthUrl."
}

$javaListenPid = Find-ListeningPid -PortNumber $selectedPort
if ($javaListenPid -gt 0) {
    $pidState.backendJavaPid = $javaListenPid
    $pidState.backendJavaStartUtc = Get-ProcessStartUtcString -ProcessId $javaListenPid
    Save-PidState -StateObject $pidState
}

$readySeconds = [Math]::Round($stopwatch.Elapsed.TotalSeconds, 1)
Write-Host "  Dippie Backend đã sẵn sàng sau ${readySeconds}s!" -ForegroundColor Green

# Bước 4: nạp thêm 6 bộ phim mẫu nếu DB chưa đủ.
Import-DemoCatalogs -SelectedPort $selectedPort

# Bước 5: mở trang web trên trình duyệt mặc định.
$webUrl = "http://localhost:$selectedPort/"
if (-not $NoBrowser) {
    Write-Host "[5/6] Đang mở trình duyệt tại $webUrl..." -ForegroundColor Cyan
    try {
        Start-Process $webUrl
    } catch {
        Write-Host "  Không tự mở được trình duyệt. Vui lòng mở thủ công: $webUrl" -ForegroundColor Yellow
    }
} else {
    Write-Host "[5/6] Bỏ qua mở trình duyệt (-NoBrowser)." -ForegroundColor DarkGray
}

# Bước 6: mở app quản trị JavaFX trong cửa sổ riêng.
$desktopStarted = $false
if (-not $NoDesktop) {
    Write-Host "[6/6] Đang mở ứng dụng quản trị Dippie Admin (JavaFX)..." -ForegroundColor Cyan
    $desktopRunner = Join-Path $runDir "launch-desktop.ps1"
    Write-DesktopRunnerScript `
        -ScriptOutPath $desktopRunner `
        -JavaHomePath $resolvedJavaHome `
        -LogFilePath $desktopLog

    $desktopProc = Start-Process `
        -FilePath $psExe `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ('"{0}"' -f $desktopRunner)) `
        -WorkingDirectory $root `
        -WindowStyle Minimized `
        -PassThru

    $pidState.desktopPid = $desktopProc.Id
    $pidState.desktopStartUtc = Get-ProcessStartUtcString -ProcessId $desktopProc.Id
    Save-PidState -StateObject $pidState
    $desktopStarted = $true
    Write-Host "  Đã khởi chạy cửa sổ Dippie Admin (lần đầu có thể mất thêm 30-60 giây tải thư viện JavaFX)." -ForegroundColor Green
} else {
    Write-Host "[6/6] Bỏ qua ứng dụng quản trị desktop (-NoDesktop)." -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host "  DIPPIE ĐÃ SẴN SÀNG!" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host "  Trang web      : $webUrl"
Write-Host "  Tài khoản mẫu  :"
Write-Host "    - Người dùng : user  / password123"
Write-Host "    - Quản trị   : admin / password123"
Write-Host "  Nhật ký (log)  :"
Write-Host "    - Backend    : $backendLog"
if ($desktopStarted) {
    Write-Host "    - Desktop    : $desktopLog"
}
Write-Host "  Cách dừng      : Đóng cửa sổ Dippie Backend hoặc chạy:"
Write-Host "                   CHAY_DIPPIE.bat -Stop"
Write-Host "  Đặt lại dữ liệu: CHAY_DIPPIE.bat -Reset"
Write-Host "================================================================" -ForegroundColor Green
