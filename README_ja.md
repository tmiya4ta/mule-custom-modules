# mule-custom-modules

MuleSoft Mule 4 用カスタムモジュール・エクステンション集。

[English](README.md)

## モジュール

### mule-data-partition-module

CSV・JSON のストリーミングデータパーティショナー。巨大な InputStream をサイズ（および/またはアイテム数）ベースでパーティション分割します。データ全体をメモリに載せません。

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

### mule-chterm-module

Mule アプリケーション用ブラウザベースターミナルコンソール。xterm.js による Web UI でランタイムコンテナへのインタラクティブシェルアクセスを提供します。

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

ブラウザで `/chterm/` にアクセスするとターミナルが使えます。

### mule-csv-file-split-module

ファイルベースの CSV 分割モジュール。大きな CSV ファイルを行数で分割し、結合します。

- 行数ベースの分割（外部 `split` コマンドまたは Java 実装）
- `PagingProvider` ベースのストリーミングパーティション
- ゼロコピー `FileChannel.transferTo()` によるファイル結合

### mule-jp-characters-module

日本語の全角・半角文字変換モジュール。

## リファレンスアプリケーション

| アプリ | 説明 |
|--------|------|
| `mule-data-partition-app` | data-partition モジュールのリファレンスアプリ (`/test/csv`, `/test/json`, `/test/csv-stream`, `/test/json-stream`) |
| `mule-term-app` | chterm モジュールのリファレンスアプリ — ブラウザターミナルコンソール |

## ビルド

Java 17 と Maven 3.x が必要です。

```bash
# モジュールのビルド
cd mule-data-partition-module
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install -DskipTests

# テストアプリのビルド
cd mule-data-partition-app
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package -DskipTests -DattachMuleSources
```

## デプロイ

```bash
# Anypoint Exchange にアップロード
yaac upload asset target/<artifact>.jar -g <org> -a <asset-id> -v <version>

# CloudHub 2.0 にデプロイ
yaac deploy app <org> <env> <app-name> target=<target> -g <org> -a <asset-id> -v <version> v-cores=0.1
```
