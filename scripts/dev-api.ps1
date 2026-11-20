$ErrorActionPreference = "Stop"
$devEnv = Join-Path $PSScriptRoot "..\..\devenv\scripts\env.ps1"
if (Test-Path -LiteralPath $devEnv) { . $devEnv }
$root = Join-Path $PSScriptRoot ".."
Push-Location (Join-Path $root "services\api")
try { mvn spring-boot:run } finally { Pop-Location }