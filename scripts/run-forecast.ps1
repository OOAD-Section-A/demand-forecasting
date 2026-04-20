$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$envFile = Join-Path $PSScriptRoot "db-env.ps1"
$targetClasses = Join-Path $projectRoot "target\classes"

if (-not (Test-Path $envFile)) {
    throw "Missing scripts\db-env.ps1. Copy scripts\db-env.example.ps1 to scripts\db-env.ps1 and add your local DB credentials."
}

if (-not (Test-Path $targetClasses)) {
    Push-Location $projectRoot
    try {
        mvn compile
    } finally {
        Pop-Location
    }
}

. $envFile

Push-Location $projectRoot
try {
    mvn exec:java
} finally {
    Pop-Location
}
