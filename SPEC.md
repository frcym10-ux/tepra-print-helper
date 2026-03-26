# TEPRA Print Helper 仕様書

> この文書はAIエージェントが iOS / PC 等で同等機能を実装する際の参考資料です。
> Android実装の詳細と、プラットフォーム共通の仕様を分離して記述しています。

---

## 1. システム概要

### 目的

試薬管理システム（Next.js Webアプリ）から、TEPRA ラベルプリンターへラベルを印刷する。

### 全体フロー

```
[Webアプリ] --URLスキーム--> [本アプリ] --Bluetooth--> [TEPRA SR5500P]
```

1. Webアプリがラベル印刷ボタンを押す
2. `tepra-print://?data={...}` というカスタムURLスキームが発火する
3. 本アプリがURLを受け取り、JSONをパースする
4. ラベル画像（Bitmap）を生成する
5. TEPRA SDK 経由で Bluetooth 印刷する
6. アプリが自動的に閉じる

---

## 2. URLスキーム仕様（プラットフォーム共通）

### スキーム

```
tepra-print://
```

### クエリパラメータ

| パラメータ | 必須 | 説明 |
|-----------|------|------|
| `data`    | Yes  | URLエンコードされたJSON文字列 |

### JSON形式

#### 単一ラベル（旧形式・後方互換）

```json
{
  "controlNumber": "2600099-01",
  "reagentName": "抗免疫グロブリンAポリクローナル抗体",
  "lotNumber": "H25759",
  "expiryDate": "2028/09/31",
  "tapeWidthMm": 18
}
```

#### 複数ラベル（現行形式）

```json
{
  "labels": [
    {
      "controlNumber": "2600099-01",
      "reagentName": "抗免疫グロブリンAポリクローナル抗体",
      "lotNumber": "H25759",
      "expiryDate": "2028/09/31",
      "tapeWidthMm": 18
    },
    { ... }
  ]
}
```

### フィールド定義

| フィールド       | 型     | デフォルト | 説明 |
|-----------------|--------|-----------|------|
| controlNumber   | String | ""        | 管理番号（QRコードに格納） |
| reagentName     | String | ""        | 試薬名（ラベル上部に大きく表示） |
| lotNumber       | String | ""        | ロット番号 |
| expiryDate      | String | ""        | 有効期限（表示形式: YYYY/MM/DD） |
| tapeWidthMm     | Int    | 18        | テープ幅 (mm) |

---

## 3. プリンター仕様

### 対応機種

| 機種 | 接続方式 | 最大テープ幅 | 解像度 |
|------|---------|-------------|--------|
| SR5500P   | Bluetooth Classic のみ | 24mm | 180dpi |
| SR-MK1    | Bluetooth Classic のみ | 24mm | 180dpi |
| SR-R2500P | Bluetooth Classic のみ | 18mm | 180dpi |

### テープ幅と印刷可能領域

解像度 180dpi = 約 7.09 dots/mm

| テープ幅 (mm) | 印刷可能ドット数 | SDK TapeWidth値 |
|--------------|-----------------|-----------------|
| 4            | 22              | 1               |
| 6            | 36              | 2               |
| 9            | 54              | 3               |
| 12           | 72              | 4               |
| 18           | 108             | 5               |
| 24           | 128             | 6               |
| 36           | (未確認)         | 7               |
| 50           | (未確認)         | 10              |

### Bluetooth SPP UUID

```
00001101-0000-1000-8000-00805F9B34FB
```

### デフォルトプリンターアドレス（現在の環境）

```
68:84:7E:64:E4:B7
```

---

## 4. ラベルレイアウト仕様（プラットフォーム共通）

### 基本寸法

| 項目 | 値 | 備考 |
|------|---|------|
| ラベル長さ | 40mm (固定) | 将来的にURLパラメータで可変にしてもよい |
| テープ幅 | 18mm (デフォルト) | JSONの tapeWidthMm で変更可 |
| 解像度 | 180dpi | 1mm ≈ 7.09 dots |
| マージン | 4 dots | 上下左右共通 |

### ドット数計算

```
ラベル長さ: floor(40 × 180 / 25.4) = 283 dots
テープ高さ(18mm): 108 dots
```

### レイアウト構造

```
┌──────────────────────────────────┐
│ 試薬名（太字、自動縮小で1行に収める）│ ← 上部エリア (高さの35%)
│──────────────────────────────────│ ← 区切り線 (1dot)
│ ┌──────┐ 期限：2028/09/31       │
│ │  QR  │ Lot：H25759            │ ← 下部エリア (高さの65%)
│ │コード │ 管理番号：2600099-01    │
│ └──────┘                        │
└──────────────────────────────────┘
```

### 上部エリア（試薬名）

- 高さ: テープ高さの 35%
- フォント: 太字
- 初期フォントサイズ: エリア高さ × 0.75
- 自動縮小: テキスト幅がラベル幅を超える場合、0.5pt ずつ縮小
- 最小フォントサイズ: 6pt（これ以下は見切れ許容）
- 縦位置: エリア内で中央揃え

### 区切り線

- 位置: 上部エリアの下端
- 色: 黒
- 太さ: 1 dot

### 下部エリア（QRコード + 情報テキスト）

#### QRコード（左側）

- 内容: `controlNumber` の文字列
- サイズ: 下部エリアの高さ（正方形）
- マージン（QR内部余白）: 0
- 文字エンコーディング: UTF-8
- ライブラリ: ZXing（Android）

#### 情報テキスト（右側、3行）

```
期限：{expiryDate}
Lot：{lotNumber}
管理番号：{controlNumber}
```

- 左端: QRコード右端 + マージン × 2
- フォント: 太字
- 最大フォントサイズ: 16pt
- 初期フォントサイズ: (行間 × 0.75)
- 行間: 下部エリア高さ ÷ 3
- 自動縮小: 最も長い行がエリア幅を超える場合、比率で縮小

### Bitmap形式

- **向き**: 横長（width = テープ長さ方向、height = テープ幅方向）
- **色空間**: ARGB_8888
- **背景**: 白 (#FFFFFF)
- **テキスト色**: 黒 (#000000)

---

## 5. TEPRA SDK 印刷フロー

### 概要（Android実装）

```
1. プリンター検出 (TepraPrintDiscoverPrinter)
2. Bluetooth warm-up（重要な回避策）
3. コールバック設定 (TepraPrintCallback)
4. プリンター情報設定 (setPrinterInformation)
5. ステータス取得 (fetchPrinterStatus) ※失敗しても続行
6. 印刷実行 (doPrint)
7. 完了待機 (CountDownLatch, 最大60秒)
```

### 印刷パラメータ

| キー | 値 | 説明 |
|------|---|------|
| Copies | 1 | 部数 |
| TapeCut | EachLabel (0) | ラベルごとにカット |
| HalfCut | false | ハーフカットなし |
| PrintSpeed | PrintSpeedHigh (0) | 高速印刷 |
| Density | 0 | 標準濃度 |
| TapeWidth | (テープ幅から変換) | SDK定数値 |
| PriorityPrintSetting | false | SR5500Pは非対応 |
| HalfCutContinuous | false | SR5500Pは非対応 |

### 印刷フェーズ

```
Prepare (1) → Processing (2) → WaitingForPrint (3) → Complete (4)
```

### SDK必須パーミッション (Android)

```xml
<!-- Bluetooth -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" tools:targetApi="s" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Network (SDK内部で使用) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />

<!-- Location (旧Android用) -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="32" />

<!-- Nearby (Android 13+) -->
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation" tools:targetApi="tiramisu" />
```

---

## 6. 実装上の重要な注意点

### Bluetooth warm-up（必須の回避策）

SDK の接続が失敗する問題への対策として、SDK呼び出し前に raw Bluetooth ソケットで接続→切断を行う。

```
1. createRfcommSocketToServiceRecord(SPP_UUID) で接続
2. 失敗したら createRfcommSocket(1) (リフレクション) で再試行
3. 500ms 保持して切断
4. 1000ms 待機
5. SDK の印刷処理を開始
```

この手順を省略すると、SDK が CommunicationError (-6) で印刷に失敗する場合がある。

### HOST フィールドの修正

Bluetooth 接続の場合、SDK の discover で返される `PRINTER_INFO_HOST` が空文字になる。
`PRINTER_INFO_SERIAL_NUMBER`（= MACアドレス）を `PRINTER_INFO_HOST` にコピーする必要がある。

### fetchPrinterStatus の失敗は無視

`fetchPrinterStatus()` が失敗しても、`doPrint()` は成功する場合がある。
ステータス取得失敗時はデフォルト値で印刷を続行する。

### テープ幅の自動検出

`fetchPrinterStatus()` が成功した場合、実際に装着されているテープ幅を取得できる。
この値が 0 より大きければ、JSONで指定された値を上書きする。

---

## 7. 他プラットフォームへの移植ガイド

### iOS での実装方針

1. **URLスキーム**: Info.plist に `tepra-print` スキームを登録
2. **Bluetooth**: CoreBluetooth は BLE 向け。SR5500P は Bluetooth Classic なので `ExternalAccessory.framework` または MFi 認証が必要。King Jim が iOS SDK を提供しているか確認すること
3. **ラベル描画**: Core Graphics / UIGraphicsImageRenderer で同等のBitmap生成が可能
4. **QRコード**: CIFilter の `CIQRCodeGenerator` で生成可能（ZXing不要）

### PC (Windows / macOS) での実装方針

1. **URLスキーム**: OS のカスタムプロトコルハンドラーとして登録
   - Windows: レジストリに `tepra-print` プロトコルを登録
   - macOS: Info.plist の `CFBundleURLTypes`
2. **Bluetooth SPP**: OS の Bluetooth シリアルポート API を使用
   - Windows: Windows.Devices.Bluetooth (UWP) または 32feet.NET
   - macOS: IOBluetooth.framework
3. **SDK**: King Jim が PC 向け SDK を提供しているか確認。なければ ESC/P プロトコルの直接実装が必要（ただし SR5500P は独自プロトコルのため、SDK推奨）
4. **ラベル描画**: 各言語の画像ライブラリ（Pillow, System.Drawing, etc.）で同等のBitmap生成
5. **QRコード**: ZXing（Java/.NET）、qrcode（Python）など

### プラットフォーム共通で再利用できる部分

- **URLスキームの仕様** (セクション2): JSON形式は完全に共通
- **ラベルレイアウト仕様** (セクション4): ドット数・比率は全プラットフォーム共通
- **印刷パラメータ** (セクション5): SDK のパラメータ名・値は共通

### プラットフォームごとに再実装が必要な部分

- Bluetooth 接続・検出のコード
- SDK の呼び出し方法（言語・API が異なる）
- Bitmap の生成方法（描画API が異なる）
- URLスキームの受信方法
- パーミッション/権限の管理

---

## 8. King Jim TEPRA SDK について

### 入手先

King Jim 公式から SDK を入手する必要がある（一般公開されていない可能性あり）。

### Android SDK 構成

```
TepraPrint.jar                          → android/app/libs/
jniLibs/arm64-v8a/libTepraPrint.so     → android/app/src/main/jniLibs/
jniLibs/armeabi-v7a/libTepraPrint.so   → android/app/src/main/jniLibs/
jniLibs/x86/libTepraPrint.so           → android/app/src/main/jniLibs/
jniLibs/x86_64/libTepraPrint.so        → android/app/src/main/jniLibs/
```

### 主要クラス

| クラス | 役割 |
|--------|------|
| `TepraPrint` | 印刷・ステータス取得・本体制御 |
| `TepraPrintDiscoverPrinter` | プリンター検索 |
| `TepraPrintCallback` | 印刷状態のコールバック |

### ProGuard 設定（リリースビルド時）

```
-keep class jp.co.kingjim.tepraprint.sdk.** { *; }
```

---

## 9. デバッグ方法

### デバッグ用Bitmap保存

印刷直前のBitmapが以下に保存される:

```
/sdcard/Android/data/com.reagent.tepraprint/files/debug_label.png
```

Android Studio の Device Explorer または `adb pull` で取得して確認できる。

### Logcat タグ

| タグ | ファイル | 内容 |
|------|---------|------|
| `TepraPrinter` | TepraPrinter.kt | Bluetooth接続、SDK操作、印刷フェーズ |
| `PrintStatusActivity` | PrintStatusActivity.kt | 権限チェック、プリンター選択 |
| `TepraMainActivity` | MainActivity.kt | URLスキーム受信、JSONパース |

---

## 10. TEPRA Web Print JavaScript API（PC印刷）

### 概要

PCに「テプラ クリエイター」+「WebAPI用通信モジュール」がインストールされている場合、
ブラウザのJavaScriptから直接TEPRAプリンターに印刷できる。

```
ブラウザ(Next.js) → WebAPI通信モジュール(PC常駐) → プリンタドライバ → SR5500P
```

### PC側の必要ソフトウェア

1. **テプラ クリエイター プリンタドライバ** (Ver 5.53 日本語版)
2. **WebAPI用通信モジュール** — テプラ クリエイターのインストーラーから個別インストール
3. **テプラ クリエイター本体** — テンプレート作成用（画像印刷のみなら不要の場合あり）

### 印刷方式の使い分け

| 端末 | 方式 | 必要なもの |
|------|------|-----------|
| Android スマホ | URLスキーム → tepra-print-helper アプリ | Androidアプリ |
| PC ブラウザ | TEPRA Web API (JavaScript) | テプラ クリエイター + 通信モジュール |

### 定数

#### TepraPrintTapeID（テープ幅ID）

| テープ幅 | ID値 |
|---------|------|
| 4mm | 274 |
| 6mm | 259 |
| 9mm | 260 |
| 12mm | 261 |
| 18mm | 262 |
| 24mm | 263 |
| 36mm | 264 |

#### TepraPrintTapeCut（カット設定）

| 設定 | 値 |
|------|---|
| EACH_LABEL (ラベル毎) | 0 |
| AFTER_JOB (ジョブ毎) | 1 |
| NOT_CUT (カットなし) | 2 |

#### TepraPrintError（エラーコード）

| コード | 値 | 内容 |
|--------|---|------|
| SUCCESS | 0 | 成功 |
| PRINTER_NOT_FOUND | 1 | プリンター未検出 |
| PRINTER_ACCESS_ERROR | 100 | プリンターアクセスエラー |
| PRINT_START_ERROR | 101 | 印刷開始エラー |
| WEBAPI_REQUEST_ERROR | 201 | Web API リクエストエラー |
| WEBAPI_INTERNAL_ERROR | 202 | 通信モジュール内部エラー |
| PRINT_MODULE_EXEC_ERROR | 203 | 印刷モジュール開始エラー |

### JavaScript API 印刷フロー

```javascript
// 1. プリンター一覧取得
const { printers } = await TepraPrint.getPrinter();

// 2. プリンター作成
const { printer } = await TepraPrint.createPrinter(printers[0]);

// 3. パラメータ作成
const { printParameter } = await printer.createPrintParameter();
printParameter.copies = 1;
printParameter.tapeCut = 0;  // EACH_LABEL
printParameter.tape = 262;   // 18mm
printParameter.stretchImage = false;

// 4. 画像ファイルで印刷
const { printJob } = await printer.doPrint(printParameter, { imageFile: pngFile });

// 5. 完了待ち
let done = false;
while (!done) {
    const progress = await printJob.progressOfPrint();
    if (progress.jobEnd) done = true;
}
```

### 実装ファイル（試薬管理システム側）

```
reagent-system/src/lib/tepra-webapi.ts  — Web APIクライアント + ラベル画像生成
reagent-system/src/app/print/page.tsx   — 「PC TEPRA印刷」ボタン
```

### ラベル画像生成（Canvas）

Web API では画像ファイル（PNG）を渡して印刷する。
ブラウザの HTML Canvas で Android 版と同じレイアウト仕様の画像を生成する。
レイアウト仕様はセクション4を参照。

---

## 11. ファイル構成

```
tepra-print-helper/
├── CLAUDE.md                          # AI向け引継ぎ資料
├── SPEC.md                            # 本ファイル（仕様書）
├── android/
│   ├── app/
│   │   ├── build.gradle.kts           # ビルド設定・依存関係
│   │   ├── libs/
│   │   │   └── TepraPrint.jar         # TEPRA SDK
│   │   └── src/main/
│   │       ├── AndroidManifest.xml    # パーミッション・Activity定義
│   │       ├── jniLibs/               # TEPRA SDK ネイティブライブラリ
│   │       └── kotlin/com/reagent/tepraprint/
│   │           ├── MainActivity.kt         # URLスキーム受信
│   │           ├── PrintStatusActivity.kt  # 印刷実行の制御
│   │           ├── TepraLabel.kt           # ラベルデータモデル
│   │           ├── TepraLabelRenderer.kt   # ラベルBitmap生成
│   │           └── TepraPrinter.kt         # SDK経由の印刷処理
│   ├── build.gradle.kts               # プロジェクトレベル設定
│   └── settings.gradle.kts            # Gradle設定
└── .github/workflows/
    └── build.yml                       # CI (claude/**ブランチで自動ビルド)
```
