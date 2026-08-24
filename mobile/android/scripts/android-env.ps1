$ErrorActionPreference = 'Stop'

$studioRoot = Join-Path $env:LOCALAPPDATA 'Programs\AndroidStudio-2026.1.3.8\android-studio'
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$javaHome = Join-Path $studioRoot 'jbr'

if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) {
  throw "Android Studio JBR을 찾을 수 없습니다: $javaHome"
}
if (-not (Test-Path -LiteralPath (Join-Path $sdkRoot 'platform-tools\adb.exe'))) {
  throw "Android SDK를 찾을 수 없습니다: $sdkRoot"
}

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:Path = "$(Join-Path $sdkRoot 'platform-tools');$(Join-Path $sdkRoot 'emulator');$env:Path"

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
