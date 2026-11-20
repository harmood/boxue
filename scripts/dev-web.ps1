$ErrorActionPreference = "Stop"
$devEnv = Join-Path $PSScriptRoot "..\..\devenv\scripts\env.ps1"
if (Test-Path -LiteralPath $devEnv) { . $devEnv }
$root = Join-Path $PSScriptRoot ".."
Push-Location $root
try { pnpm dev:web } finally { Pop-Location }