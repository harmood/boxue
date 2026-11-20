$ErrorActionPreference = "Stop"
$compose = Join-Path $PSScriptRoot "..\infra\docker\compose.yml"
docker compose -f $compose up -d
Write-Host "PostgreSQL: localhost:5433"
Write-Host "Redis:      localhost:6379"
Write-Host "S3 API:     localhost:9000"
Write-Host "S3 Console: localhost:9001"
Write-Host "Mailpit:    http://localhost:8025"
