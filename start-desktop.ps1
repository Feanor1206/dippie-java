$ErrorActionPreference = 'Stop'
$mavenHome = Join-Path $env:USERPROFILE '.m2'
$previousMavenHome = $env:MAVEN_USER_HOME

# Vào thư mục desktop để chạy đúng Maven wrapper đi kèm trong repo.
Push-Location -LiteralPath (Join-Path $PSScriptRoot 'desktop')
try {
    $env:MAVEN_USER_HOME = $mavenHome
    $repositoryOption = "-Dmaven.repo.local=$(Join-Path $mavenHome 'repository')"
    & .\mvnw.cmd -q $repositoryOption compile javafx:run
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể chạy Dippie Admin (mã Maven $LASTEXITCODE)."
    }
} finally {
    $env:MAVEN_USER_HOME = $previousMavenHome
    Pop-Location
}
