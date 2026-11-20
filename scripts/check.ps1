$ErrorActionPreference = "Stop"
$devEnv = Join-Path $PSScriptRoot "..\..\devenv\scripts\env.ps1"
if (Test-Path -LiteralPath $devEnv) { . $devEnv }
$root = Join-Path $PSScriptRoot ".."
Push-Location $root
try {
  Write-Host "== pnpm typecheck =="
  pnpm typecheck
  if (Get-Command mvn -ErrorAction SilentlyContinue) {
    Write-Host "== maven test =="
    mvn -f services\api\pom.xml test
  } else {
    Write-Host "mvn not found, skip backend tests"
  }
  if (Get-Command flutter -ErrorAction SilentlyContinue) {
    Write-Host "== flutter analyze =="
    Push-Location apps\android
    try { flutter analyze } finally { Pop-Location }
  } else {
    Write-Host "flutter not found, skip Android analysis"
  }
} finally {
  Pop-Location
}