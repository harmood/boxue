$ErrorActionPreference = "Stop"
$compose = Join-Path $PSScriptRoot "..\infra\docker\compose.yml"
docker compose -f $compose logs -f