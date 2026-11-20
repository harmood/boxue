$ErrorActionPreference = "Stop"
$compose = Join-Path $PSScriptRoot "..\infra\docker\compose.yml"
docker compose -f $compose up -d
Write-Host "PostgreSQL: localhost:5432"
Write-Host "Redis:      localhost:6379"
Write-Host "MinIO:      localhost:9000 (console 9001)"
Write-Host "Mailpit:    http://localhost:8025"