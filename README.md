# Vivoactive5MusicControl

Garmin Vivoactive 5 向けカスタム音楽コントロール（Connect IQ）+ Android コンパニオン（PowerAMP）。

**作った理由:** 標準の音楽コントロール画面に時刻表示がなく使いにくかったため、時刻付きの操作 UI を自作した。

**方針:** 個人サイドロード用。ストア公開は必須としない。時計内サブスク UI の差し替えは対象外。PowerAMP（Android）専用。

## 横着インストール（Release の Zip）

[Releases](https://github.com/XiAce-Lite/Vivoactive5MusicControl/releases) から **`MusicCtrl-sideload-*.zip`** を落とす（APK + 時計アプリ + 手順書入り）。

1. **Android:** `MusicCtrlCompanion.apk` をインストール → アプリを開きサービス開始（通知許可）
2. **時計:** USB 接続し、`MusicCtrl.prg` を `GARMIN\APPS\` にコピー → 取り外して **MusicCtrl** を起動
3. **前提:** PowerAMP で再生、Garmin Connect Mobile を起動・ペアリングしたまま

詳細は Zip 内の `INSTALL.txt` を参照。

> `MusicCtrl-android-*.zip` は CI が自動生成する **APK のみ** の Zip です。時計の `.prg` は開発者キーが必要なため、フルセットはメンテナがアップロードする `MusicCtrl-sideload-*.zip` を使ってください。

## 構成

| 部分 | 内容 |
|---|---|
| 時計アプリ | `source/` — UI + 携帯連携 |
| Android | `android/` — Connect IQ SDK + PowerAMP Intent 操作 |
| 手順 | [docs/PHONE-BRIDGE.md](docs/PHONE-BRIDGE.md) / [docs/SETUP.md](docs/SETUP.md) |

## CI / リリース

| トリガ | 内容 |
|---|---|
| `develop` への push / PR | Android debug APK をビルド（`.github/workflows/ci.yml`） |
| タグ `v*` の push | APK をビルドして GitHub Release に添付（`.github/workflows/release.yml`） |

ローカルでフル Zip（APK+PRG）を作る:

```powershell
.\scripts\make-sideload-zip.ps1 -Version 0.1.0
# 既存の dist\ 成果物だけで Zip する場合:
.\scripts\make-sideload-zip.ps1 -Version 0.1.0 -SkipBuild
```

Release に追加アップロードする例:

```powershell
gh release upload v0.1.0 .\dist\MusicCtrl-sideload-0.1.0.zip --clobber
```

## 時計ビルド（開発者）

```powershell
$sdk = (Get-Content "$env:APPDATA\Garmin\ConnectIQ\current-sdk.cfg").Trim().TrimEnd('\')
$key = "$env:APPDATA\Garmin\ConnectIQ\Keys\developer_key.der"
$env:Path = "$sdk\bin;$env:Path"
monkeyc -d vivoactive5 -f monkey.jungle -o bin\MusicCtrl.prg -y $key -w
```

`bin\MusicCtrl.prg` → 時計の `GARMIN\APPS\`

## Android ビルド（開発者）

JDK 17 が必要です。

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
cd android
.\gradlew.bat :app:assembleDebug
```

APK: `android\app\build\outputs\apk\debug\app-debug.apk`
