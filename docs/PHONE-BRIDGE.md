# スマホ連携（PowerAMP）

方針: 時計の Connect IQ アプリ ↔ Android コンパニオン ↔ PowerAMP。

## 前提

- Vivoactive 5 に `MusicCtrl.prg` をサイドロード
- スマホに **Garmin Connect Mobile**（または Connect IQ ランタイム）と **PowerAMP**
- このリポジトリの `android/` を Android Studio でビルドしてインストール

## プロトコル

Watch → Phone（`Communications.transmit`）:

| c | 意味 |
|---|---|
| `toggle` | 再生/一時停止 |
| `next` | 次へ |
| `prev` | 前へ |
| `vol` + `v` | 音量 0–100 |

Phone → Watch（状態）:

`t=st`, `p`, `ti`, `ar`, `pos`, `dur`, `vol`

アプリ ID（一致必須）: `a6c98919-2956-452a-a02b-592d01fbb039`

## 使い方

1. 時計で MusicCtrl を起動
2. スマホで Companion を開き **サービス開始**
3. PowerAMP で再生し、時計から操作

シミュレータと Android の BLE 連携は環境によって不安定なことがあります。実機同士で確認してください。

## ビルド

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

APK: `android\app\build\outputs\apk\debug\app-debug.apk`
