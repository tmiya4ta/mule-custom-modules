# mule-custom-modules

MuleSoft Mule 4 用カスタムモジュール・エクステンション集。

[English](README.md)

## 概要

| モジュール | 説明 | リファレンスアプリ |
|-----------|------|-------------------|
| **mule-data-partition-module** | CSV/JSON ストリーミングパーティショナー — 巨大 InputStream をサイズ/アイテム数で分割。temp file ベース、メモリ ~20KB | [mule-data-partitioner](reference-apps/mule-data-partitioner/) |
| **mule-webterm-module** | ブラウザターミナルコンソール (xterm.js) — インタラクティブシェル、ファイル転送、右クリックメニュー。パスワード保護 | [mule-webterm](reference-apps/mule-webterm/) |
| **mule-jmx-module** | JMX 経由の JVM メトリクス収集 — CPU、メモリ、GC、スレッド、ランタイム情報を JSON で一括取得 | [mule-jmx-metrics](reference-apps/mule-jmx-metrics/) |
| **mule-csv-file-split-module** | ファイルベース CSV 分割 — 行数で分割、ゼロコピー FileChannel で結合 | — |
| **mule-jp-characters-module** | 日本語 半角↔全角 文字変換 (カタカナ、ASCII、数字、スペース) | — |

## モジュール

### [mule-data-partition-module](mule-data-partition-module/)

CSV・JSON のストリーミングデータパーティショナー。([詳細ドキュメント](mule-data-partition-module/README_ja.md))巨大な InputStream をサイズ（および/またはアイテム数）ベースでパーティション分割します。データ全体をメモリに載せません。

> リファレンスアプリ: [reference-apps/mule-data-partitioner](reference-apps/mule-data-partitioner/) — エンドポイント: `/test/csv`, `/test/json`, `/test/csv-stream`, `/test/json-stream`

**主な機能:**
- サイズ (KB/MB/GB) および/またはアイテム数での分割
- temp file ベースのストリーミング — データサイズに関係なくメモリ使用量は約 20KB
- OOM 安全: 0.1 vCore の CloudHub 2.0 で 1GB 以上の CSV をテスト済み
- `PagingProvider<InputStream>` を返却 — `foreach` で各パーティションを遅延消費
- `count-lines` / `count-json-items` オペレーションでストリーミングアイテム数カウント

**使い方:**
```xml
<datapartition:config name="Config" />

<datapartition:partition-csv config-ref="Config"
    partitionSize="50" sizeUnit="MB" maxItems="0" includeHeader="true">
    <non-repeatable-iterable />
</datapartition:partition-csv>

<foreach>
    <!-- payload = ~50MB の CSV パーティション (InputStream) -->
</foreach>
```

### mule-webterm-module

Mule アプリケーション用ブラウザベースターミナルコンソール。xterm.js による Web UI でランタイムコンテナへのインタラクティブシェルアクセスを提供します。

> リファレンスアプリ: [reference-apps/mule-webterm](reference-apps/mule-webterm/) — `/` または `/chterm/` でターミナル UI にアクセス

**主な機能:**
- xterm.js ターミナル（フルカラー、bash キーバインド対応: Ctrl-A/E/K/U/W/C/D/L/R）
- パスワード保護の exec key 認証
- ワンショットコマンド実行 (`/api/exec`)
- インタラクティブ PTY シェルセッション (`/api/term/*`)
- チャンク転送によるファイルアップロード/ダウンロード（10MB チャンク、大容量ファイル対応）
- ディレクトリナビゲーション付きファイルブラウザ
- 右クリックコンテキストメニュー（コピー、ペースト、クリア、アップロード、ダウンロード）

**使い方:**
```xml
<chterm:config name="Chterm" password="yourpassword" />

<flow name="chterm-flow">
    <http:listener config-ref="HTTP_Listener_config" path="/chterm/*" />
    <chterm:handle-request config-ref="Chterm" />
</flow>
```

### [mule-jmx-module](mule-jmx-module/)

JMX 経由の JVM メトリクス収集モジュール。([詳細ドキュメント](mule-jmx-module/README_ja.md))CPU、メモリ、GC、スレッド、クラスローディング、ランタイム、バッファプールのメトリクスを取得します。

> リファレンスアプリ: [reference-apps/mule-jmx-metrics](reference-apps/mule-jmx-metrics/) — エンドポイント: `/metrics`, `/metrics/cpu`, `/metrics/memory`, `/metrics/gc`, `/metrics/threads`

**オペレーション:**
- `collect-metrics` — 全メトリクスを一括取得
- `collect-cpu` — CPU 使用率、プロセス CPU 時間、システムロードアベレージ
- `collect-memory` — Heap/NonHeap 使用量、メモリプール詳細
- `collect-gc` — GC 回数・累積時間
- `collect-threads` — スレッド数、状態別内訳 (RUNNABLE/WAITING/TIMED_WAITING)

**使い方:**
```xml
<jmx:config name="JMX_Config" />

<flow name="metrics-flow">
    <http:listener config-ref="HTTP_Listener_config" path="/metrics" />
    <jmx:collect-metrics config-ref="JMX_Config" />
</flow>
```

### mule-csv-file-split-module

ファイルベースの CSV 分割モジュール。大きな CSV ファイルを行数で分割し、結合します。

- 行数ベースの分割（外部 `split` コマンドまたは Java 実装）
- `PagingProvider` ベースのストリーミングパーティション
- ゼロコピー `FileChannel.transferTo()` によるファイル結合

### mule-jp-characters-module

日本語の全角・半角文字変換モジュール。

- `Hankaku -> Zenkaku` — 半角→全角 (カタカナ、ASCII、数字、スペース)
- `Zenkaku -> Hankaku` — 全角→半角

## ビルド

Java 17 と Maven 3.x が必要です。

```bash
# モジュールのビルド
cd mule-data-partition-module
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install -DskipTests

# リファレンスアプリのビルド
cd reference-apps/mule-data-partitioner
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package -DskipTests -DattachMuleSources
```

## デプロイ

```bash
# Anypoint Exchange にアップロード
yaac upload asset target/<artifact>.jar -g <org> -a <asset-id> -v <version>

# CloudHub 2.0 にデプロイ
yaac deploy app <org> <env> <app-name> target=<target> -g <org> -a <asset-id> -v <version> v-cores=0.1
```
