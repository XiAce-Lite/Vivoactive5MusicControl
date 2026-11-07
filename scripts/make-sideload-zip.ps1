# サイドロード用 Zip を dist/ に作る（APK + PRG + INSTALL.txt）
# 使い方（リポジトリルートで）:
#   .\scripts\make-sideload-zip.ps1
#   .\scripts\make-sideload-zip.ps1 -Version 0.1.2 -SkipBuild

param(
    [string]$Version = "0.1.2",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

New-Item -ItemType Directory -Force -Path dist, bin | Out-Null

if (-not $SkipBuild) {
    $sdkCfg = Join-Path $env:APPDATA "Garmin\ConnectIQ\current-sdk.cfg"
    if (-not (Test-Path $sdkCfg)) { throw "Connect IQ SDK が見つかりません: $sdkCfg" }
    $sdk = (Get-Content $sdkCfg).Trim().TrimEnd('\')
    $key = Join-Path $env:APPDATA "Garmin\ConnectIQ\Keys\developer_key.der"
    if (-not (Test-Path $key)) { throw "開発者キーが見つかりません: $key" }
    $env:Path = "$sdk\bin;$env:Path"
    Write-Host "Building watch PRG..."
    & monkeyc -d vivoactive5 -f monkey.jungle -o bin\MusicCtrl.prg -y $key -w
    if ($LASTEXITCODE -ne 0) { throw "monkeyc failed" }

    if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
        $jdk = Get-ChildItem "C:\Program Files\Microsoft\jdk-17*" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    }
    Write-Host "Building Android APK..."
    Push-Location android
    try {
        .\gradlew.bat :app:assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "gradle failed" }
    } finally {
        Pop-Location
    }
    Copy-Item "android\app\build\outputs\apk\debug\app-debug.apk" "dist\MusicCtrlCompanion.apk" -Force
    Copy-Item "bin\MusicCtrl.prg" "dist\MusicCtrl.prg" -Force
}

$apk = Join-Path $Root "dist\MusicCtrlCompanion.apk"
$prg = Join-Path $Root "dist\MusicCtrl.prg"
$install = Join-Path $Root "release\INSTALL.txt"
foreach ($f in @($apk, $prg, $install)) {
    if (-not (Test-Path $f)) { throw "Missing: $f" }
}

$zipName = "MusicCtrl-sideload-$Version.zip"
$zipPath = Join-Path $Root "dist\$zipName"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }

$stage = Join-Path $Root "dist\_stage"
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage | Out-Null
Copy-Item $apk (Join-Path $stage "MusicCtrlCompanion.apk")
Copy-Item $prg (Join-Path $stage "MusicCtrl.prg")
Copy-Item $install (Join-Path $stage "INSTALL.txt")

Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $zipPath -Force
Remove-Item $stage -Recurse -Force

Write-Host "Created $zipPath"
Get-Item $zipPath | Format-List FullName, Length, LastWriteTime
