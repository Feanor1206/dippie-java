[CmdletBinding()]
param(
    [Alias("Profile")]
    [ValidateSet("demo", "mysql", "prod")]
    [string]$SpringProfile = "demo",
    [switch]$MountDrive
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$frontendDir = Join-Path $root "frontend"
$distDir = Join-Path $frontendDir "dist"
$indexFile = Join-Path $distDir "index.html"
$springbootDir = Join-Path $root "backend\springboot"
$mvnw = Join-Path $springbootDir "mvnw.cmd"
$javaDataDir = Join-Path $root "target\dippie-java"

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

$javaProcess = $null
$mountScript = Join-Path $root 'scripts\mount-drive.ps1'
$driveMounted = $false

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
    if ($MountDrive) {
        & $mountScript
        $driveMounted = $true
    }
    $env:SPRING_PROFILES_ACTIVE = $SpringProfile
    $env:DIPPIE_DATA_DIR = $javaDataDir
    $mavenArgs = @("-q")
    if ($SpringProfile -eq "demo") {
        $mavenArgs += "-Pdemo"
    }
    $mavenArgs += @("spring-boot:run", "-Dspring-boot.run.profiles=$SpringProfile")
    $javaProcess = Start-Process `
        -FilePath $mvnw `
        -ArgumentList $mavenArgs `
        -WorkingDirectory $springbootDir `
        -NoNewWindow `
        -PassThru

    Wait-Process -Id $javaProcess.Id
} finally {
    Stop-ChildProcess -Process $javaProcess
    if ($driveMounted) {
        & $mountScript -Stop
    }
}
