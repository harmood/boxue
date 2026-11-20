param(
  [Parameter(Mandatory = $true)]
  [string]$Email,

  [string]$Username = "admin",

  [string]$DisplayName = "Administrator",

  [string]$Password
)

$ErrorActionPreference = "Stop"
$workspace = Split-Path -Parent $PSScriptRoot
$devEnv = Join-Path $workspace "..\devenv\scripts\env.ps1"

if (Test-Path -LiteralPath $devEnv) {
  . $devEnv
}

if (-not $Password) {
  $secure = Read-Host "Admin password (at least 12 characters)" -AsSecureString
  $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  try {
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
  } finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
  }
}

if ($Password.Length -lt 12) {
  throw "Admin password must be at least 12 characters."
}

$env:BOXUE_BOOTSTRAP_ADMIN_EMAIL = $Email
$env:BOXUE_BOOTSTRAP_ADMIN_USERNAME = $Username
$env:BOXUE_BOOTSTRAP_ADMIN_PASSWORD = $Password
$env:BOXUE_BOOTSTRAP_ADMIN_DISPLAY_NAME = $DisplayName

try {
  Write-Host "Bootstrapping admin: $Email ($Username)"
  & "$env:MAVEN_HOME\bin\mvn.cmd" -f (Join-Path $workspace "services\api\pom.xml") spring-boot:run "-Dspring-boot.run.profiles=bootstrap-admin"
  if ($LASTEXITCODE -ne 0) {
    throw "Admin bootstrap failed with exit code $LASTEXITCODE"
  }
  Write-Host "Admin bootstrap finished. Sign in and change the initial password immediately." -ForegroundColor Green
} finally {
  Remove-Item Env:BOXUE_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:BOXUE_BOOTSTRAP_ADMIN_EMAIL -ErrorAction SilentlyContinue
  Remove-Item Env:BOXUE_BOOTSTRAP_ADMIN_USERNAME -ErrorAction SilentlyContinue
  Remove-Item Env:BOXUE_BOOTSTRAP_ADMIN_DISPLAY_NAME -ErrorAction SilentlyContinue
}
