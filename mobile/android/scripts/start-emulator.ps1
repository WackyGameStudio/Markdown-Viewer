param(
  [ValidateSet('Phone', 'Tablet')]
  [string]$Device = 'Phone',
  [ValidateSet('36', '37')]
  [string]$Api = '37',
  [switch]$Headless,
  [switch]$WaitForBoot
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'android-env.ps1')

$avd = "MarkdownViewer_${Device}_API$Api"

$emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
$arguments = @("@$avd", '-no-audio', '-gpu', 'auto')
if ($Headless) {
  $arguments += '-no-window'
  Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle Hidden
} else {
  Start-Process -FilePath $emulator -ArgumentList $arguments
}
Write-Host "에뮬레이터 시작: $avd"

if ($WaitForBoot) {
  adb wait-for-device
  $booted = $false
  for ($attempt = 0; $attempt -lt 120; $attempt += 1) {
    if ((adb shell getprop sys.boot_completed 2>$null).Trim() -eq '1') {
      $booted = $true
      break
    }
    Start-Sleep -Seconds 2
  }
  if (-not $booted) {
    throw "에뮬레이터 부팅 대기 시간을 초과했습니다: $avd"
  }
  Write-Host "에뮬레이터 부팅 완료: $avd"
}
