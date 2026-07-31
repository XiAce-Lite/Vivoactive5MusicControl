# 開発環境セットアップ

## 完了済み（このマシン）

| 項目 | 状態 | 場所 |
|---|---|---|
| JDK 11 | 済み | `C:\Program Files\Microsoft\jdk-11.0.16.101-hotspot` |
| Connect IQ SDK 9.2.0 | 済み | `%APPDATA%\Garmin\ConnectIQ\Sdks\connectiq-sdk-win-9.2.0-...` |
| Active SDK 設定 | 済み | `%APPDATA%\Garmin\ConnectIQ\current-sdk.cfg` |
| 開発者キー | 済み | `%APPDATA%\Garmin\ConnectIQ\Keys\developer_key.der` |
| Monkey C 拡張 (Cursor) | 済み | `garmin.monkey-c`（VSIX から導入） |
| SDK Manager | 展開済み | `tools\sdk-manager\sdkmanager.exe` |
| ワークスペース設定 | 済み | `.vscode/settings.json` |

## あなたが行う作業（必須・GUI）

デバイス定義（`vivoactive5`）と Fonts は Garmin ログイン付きの SDK Manager 経由でのみ取得できます。

1. **SDK Manager** が起動していなければ実行:
   ```powershell
   Start-Process "D:\Documents\GitHub\Vivoactive5MusicControl\tools\sdk-manager\sdkmanager.exe"
   ```
2. Garmin アカウントでログイン
3. **SDKs** タブで 9.2.0 が Current になっていることを確認（未取得なら Download）
4. **Devices** タブで **vivoactive5** を Download
5. Fonts の取得を求められたら実行
6. SDK Manager を閉じる

確認:
```powershell
Get-ChildItem "$env:APPDATA\Garmin\ConnectIQ\Devices"
```
`vivoactive5` フォルダがあれば OK。

## Cursor 側の確認

1. Cursor を再読み込み（拡張を有効化）: `Ctrl+Shift+P` → **Developer: Reload Window**
2. `Ctrl+Shift+P` → **Monkey C: Verify Installation**
3. 成功したら **Monkey C: New Project** で Device App を作成（ターゲットに vivoactive5）

## CLI ビルド / サイドロード（個人利用）

```powershell
$sdk = (Get-Content "$env:APPDATA\Garmin\ConnectIQ\current-sdk.cfg").Trim().TrimEnd('\')
$key = "$env:APPDATA\Garmin\ConnectIQ\Keys\developer_key.der"
$env:Path = "$sdk\bin;$env:Path"
Set-Location D:\Documents\GitHub\Vivoactive5MusicControl
monkeyc -d vivoactive5 -f monkey.jungle -o bin\MusicCtrl.prg -y $key -w
```

実機へ入れる（USB マスストレージ）:

1. 時計を PC に接続
2. `bin\MusicCtrl.prg` を時計の `GARMIN\APPS\` にコピー
3. 安全に取り外し → アプリ一覧から **MusicCtrl** を起動

シミュレータ:

```powershell
connectiq
monkeydo bin\MusicCtrl.prg vivoactive5
```

## 注意

- `tools\` 配下の zip / SDK Manager / VSIX はリポジトリに含めない（`.gitignore` 済み）
- 開発者キー（`.der`）はバックアップを取り、Git にコミットしない
- Cursor Marketplace に Monkey C が出ない場合は  
  `cursor --install-extension tools\monkey-c.vsix` で再導入
