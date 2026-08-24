$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'android-env.ps1')

$projectRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'

if (-not (Test-Path -LiteralPath $apk)) {
  throw "디버그 APK가 없습니다. 먼저 scripts\build.ps1을 실행하세요."
}

& $adb install -r $apk
if ($LASTEXITCODE -ne 0) {
  throw "APK 설치가 실패했습니다. 종료 코드: $LASTEXITCODE"
}

& $adb shell monkey -p com.example.markdownviewer -c android.intent.category.LAUNCHER 1
