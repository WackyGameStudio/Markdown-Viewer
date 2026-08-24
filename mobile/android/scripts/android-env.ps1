$ErrorActionPreference = 'Stop'

$javaCandidates = @()
if ($env:JAVA_HOME) {
  $javaCandidates += $env:JAVA_HOME
}
if ($env:STUDIO_JDK) {
  $javaCandidates += $env:STUDIO_JDK
}
$javaCandidates += @(
  (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
  (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr')
)

$versionedStudioRoot = Join-Path $env:LOCALAPPDATA 'Programs'
if (Test-Path -LiteralPath $versionedStudioRoot) {
  $javaCandidates += Get-ChildItem -LiteralPath $versionedStudioRoot -Directory -Filter 'AndroidStudio-*' |
    Sort-Object Name -Descending |
    ForEach-Object { Join-Path $_.FullName 'android-studio\jbr' }
}

$javaHome = $javaCandidates |
  Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe')) } |
  Select-Object -First 1

$sdkCandidates = @()
if ($env:ANDROID_HOME) {
  $sdkCandidates += $env:ANDROID_HOME
}
if ($env:ANDROID_SDK_ROOT) {
  $sdkCandidates += $env:ANDROID_SDK_ROOT
}
$sdkCandidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk'

$sdkRoot = $sdkCandidates |
  Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platform-tools\adb.exe')) } |
  Select-Object -First 1

if (-not $javaHome) {
  throw 'Gradle을 실행할 JDK를 찾을 수 없습니다. Android Studio를 설치하거나 JAVA_HOME 또는 STUDIO_JDK를 설정하세요.'
}
if (-not $sdkRoot) {
  throw 'Android SDK Platform Tools를 찾을 수 없습니다. SDK를 설치하거나 ANDROID_HOME 또는 ANDROID_SDK_ROOT를 설정하세요.'
}

$env:JAVA_HOME = (Resolve-Path -LiteralPath $javaHome).Path
$env:ANDROID_HOME = (Resolve-Path -LiteralPath $sdkRoot).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$(Join-Path $sdkRoot 'platform-tools');$(Join-Path $sdkRoot 'emulator');$env:Path"

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
