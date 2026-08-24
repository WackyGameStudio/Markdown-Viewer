param(
  [switch]$SkipWebInstall,
  [switch]$WithDeviceTests
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'android-env.ps1')

$projectRoot = Split-Path -Parent $PSScriptRoot
$webRoot = Join-Path $projectRoot 'viewer-web'

Push-Location $webRoot
try {
  if (-not $SkipWebInstall) {
    npm ci
  }
  npm test
  npm run build
} finally {
  Pop-Location
}

Push-Location $projectRoot
try {
  $tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug')
  if ($WithDeviceTests) {
    $tasks += 'connectedDebugAndroidTest'
  }
  & .\gradlew.bat @tasks
  if ($LASTEXITCODE -ne 0) {
    throw "Gradle 빌드가 실패했습니다. 종료 코드: $LASTEXITCODE"
  }
} finally {
  Pop-Location
}

$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
Write-Host "완료: $apk"
