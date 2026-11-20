$ErrorActionPreference = "Stop"
$devEnv = Join-Path $PSScriptRoot "..\..\devenv\scripts\env.ps1"
if (Test-Path -LiteralPath $devEnv) { . $devEnv }
$root = Join-Path $PSScriptRoot ".."
Push-Location $root
try {
  Write-Host "== pnpm check（typecheck + OpenAPI lint）=="
  pnpm check
  Write-Host "== pnpm build =="
  pnpm build

  if (Get-Command mvn -ErrorAction SilentlyContinue) {
    Write-Host "== maven verify =="
    mvn -f services\api\pom.xml verify
  } else {
    Write-Host "mvn not found, skip backend tests"
  }

  if (Get-Command flutter -ErrorAction SilentlyContinue) {
    Write-Host "== flutter analyze =="
    Push-Location apps\android
    try {
      flutter analyze
      flutter test
    } finally { Pop-Location }
  } else {
    Write-Host "flutter not found, skip Android checks"
  }
} finally {
  Pop-Location
}
