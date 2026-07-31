# Vivoactive5MusicControl

Garmin Vivoactive 5 向けカスタム音楽コントロール（Connect IQ）+ Android コンパニオン（PowerAMP）。

**作った理由:** 標準の音楽コントロール画面に時刻表示がなく使いにくかったため、時刻付きの操作 UI を自作した。

**方針:** 個人サイドロード用。ストア公開は必須としない。時計内サブスク UI の差し替えは対象外。PowerAMP（Android）専用。

## 構成

| 部分 | 内容 |
|---|---|
| 時計アプリ | `source/` — UI + BLE コマンド送信 |
| Android | `android/` — Connect IQ SDK + PowerAMP Intent 操作 |
| 手順 | [docs/PHONE-BRIDGE.md](docs/PHONE-BRIDGE.md) / [docs/SETUP.md](docs/SETUP.md) |

## 時計ビルド

```powershell
$sdk = (Get-Content "$env:APPDATA\Garmin\ConnectIQ\current-sdk.cfg").Trim().TrimEnd('\')
$key = "$env:APPDATA\Garmin\ConnectIQ\Keys\developer_key.der"
$env:Path = "$sdk\bin;$env:Path"
monkeyc -d vivoactive5 -f monkey.jungle -o bin\MusicCtrl.prg -y $key -w
```

`bin\MusicCtrl.prg` → 時計の `GARMIN\APPS\`

## Android ビルド

JDK 17 が必要です。

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
cd android
.\gradlew.bat :app:assembleDebug
```

APK: `android\app\build\outputs\apk\debug\app-debug.apk`
