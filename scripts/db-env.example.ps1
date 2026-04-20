# Copy this file to scripts/db-env.ps1 and replace the placeholder values.
# scripts/db-env.ps1 is ignored by git so your real credentials stay local.

$env:DB_URL = "jdbc:mysql://db:3306/OOAD"
$env:DB_USERNAME = "your_username"
$env:DB_PASSWORD = "your_password"
$env:DB_POOL_SIZE = "5"

$targetClasses = Join-Path $PSScriptRoot "..\target\classes"
$runtimeProperties = Join-Path $targetClasses "database.properties"

if (Test-Path $targetClasses) {
    @"
db.url=$env:DB_URL
db.username=$env:DB_USERNAME
db.password=$env:DB_PASSWORD
db.pool.size=$env:DB_POOL_SIZE
"@ | Set-Content -Path $runtimeProperties -Encoding UTF8

    Write-Host "Database environment variables set and runtime database.properties updated."
} else {
    Write-Host "Database environment variables set. Run mvn compile, then run this script again to update target/classes/database.properties."
}
