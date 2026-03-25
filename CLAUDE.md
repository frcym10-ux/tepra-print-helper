# CLAUDE.md — TEPRA Print Helper

このファイルはAIアシスタント（Claude Code）向けの引継ぎ資料です。

---

## ユーザーについて

- プログラミング初心者に近い（少しかじっている程度）
- 説明は**平易な日本語**で、専門用語には補足をつける
- 手順は**番号付きのステップ形式**で示す
- GitHubの操作はWebブラウザからの手順を優先して説明する

---

## プロジェクト概要

Flutter + Android Kotlin のアプリ。Flutterの部分は使わず、android/ 配下のKotlinコードだけが本体。

### 何をするアプリか

1. 試薬管理システム（Next.js Webアプリ）がラベル印刷ボタンを押す
2. tepra-print://?data={...} というURLが発火する
3. このAndroidアプリがURLを受け取る
4. TEPRA SR5500P プリンター（Bluetooth接続）にラベルを印刷する
5. アプリが自動的に閉じる

---

## 現在の実装状態（2026-03-25時点）

### 完成しているもの

- MainActivity.kt — URLスキーム受信、PrintStatusActivityへ委譲
- PrintStatusActivity.kt — Bluetooth権限チェック、プリンター選択、印刷実行
- TepraPrinter.kt — Bluetooth SPP raw接続 + TEPRA ESC/Pラスタープロトコル
- TepraLabel.kt — JSON→データクラス変換
- TepraLabelRenderer.kt — ラベルBitmap生成
- build.yml — claude/**ブランチでも自動ビルド

---

## 重要な技術メモ

### TepraPrinterの実装方針

SDKなし・Bluetooth raw ソケット方式を採用。
KingJim公式SDK（TepraPrint.jar）は配布・管理が難しいため使わない。

接続時は標準UUID接続を試み、失敗したらリフレクションでchannel 1に再試行
（Android「SPP read failed」エラーの対策）。

### TEPRA ESC/Pラスタープロトコル

1. 0x1B 0x40          — 初期化
2. 0x1B 0x69 0x6D XX  — テープ幅設定
3. 0x67 0x00 NN [data] — ラスター行（1行ずつ）
4. 0x0C               — 印刷・排出

### プリンターのBluetoothアドレス

デフォルト: 68:84:7E:64:E4:B7（SR5500P）
初回起動時にペアリング済みデバイスから自動検出・保存される。

---

## git操作の注意点

.gitignoreにandroid/が含まれているため、新規ファイル追加には -f オプションが必要：

  git add -f android/path/to/file.kt

既存ファイルの変更は通常通りでOK。

### ブランチ命名規則

CIが動くブランチ: claude/ で始まるもの

---

## URLスキームの仕様

単一ラベル（旧）:
  tepra-print://?data={"controlNumber":"26-00042","reagentName":"...","lotNumber":"...","expiryDate":"...","tapeWidthMm":18}

複数ラベル（現行）:
  tepra-print://?data={"labels":[{...},{...}]}
