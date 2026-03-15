# DataPartition Extension for Mule 4

[English version / 英語版](README.md)

Mule 4用カスタムモジュール。大規模なCSV・JSONストリームを一定メモリ使用量（約20KB）で小さなチャンクに分割します。CloudHub 2.0の0.1 vCoreのようなリソース制約環境でギガバイト規模のファイルを処理するために設計されています。

## 概要

MuleSoftで大規模データセットを処理する際、ファイル全体をメモリに読み込むとOutOfMemoryErrorが発生します。このモジュールは、一時ファイルを経由したストリーミングとMuleの`PagingProvider`インターフェースを活用して、`<foreach>`と自然に統合する形でパーティションを提供することで、この問題を解決します。

**主な機能：**

- CSVファイルをサイズ（KB/MB/GB）および/またはアイテム数で分割、ヘッダー自動付与
- JSON配列をサイズおよび/またはアイテム数で分割、パーティションごとに有効なJSON配列を生成
- メモリにデータを読み込まずにCSVの行数やJSON配列のアイテム数をカウント
- テスト実績：0.1 vCore（256MBヒープ）で1GB CSV・1GB JSONをOOMなしで処理

## 動作の仕組み

### PagingProviderと遅延評価

パーティション操作は`PagingProvider<DataPartitionConnection, InputStream>`を返します。Muleの`<foreach>`が遅延的にイテレーションを駆動し、`getPage()`を呼び出してパーティションを1つずつ取得します。各呼び出しでは、ソースストリームから1パーティション分のデータだけを読み取り、一時ファイルに書き込み、そのファイルを指す`InputStream`を返します。

つまり、同時にメモリ/ディスクに存在するパーティションは1つだけです（書き込み中の次のパーティションを含めても最大2つ）。

### 一時ファイルによるストリーミング

各パーティションのデータフロー：

```
ソース InputStream
    --> BufferedReader.readLine()（CSV）/ Jacksonストリーミングパーサー（JSON）
    --> BufferedOutputStreamで一時ファイルに行/オブジェクトを書き込み
    --> 一時ファイルのFileInputStreamをラップしたDeleteOnCloseInputStreamを返す
    --> コンシューマー（foreachボディ）がFileInputStreamから読み取る
    --> close()時に一時ファイルを自動削除
```

### DeleteOnCloseInputStream

`FilterInputStream`のサブクラスで、`FileInputStream`をラップし、`close()`呼び出し時にバッキング一時ファイルを自動削除します。処理が中断された場合でも一時ファイルがリークしないことを保証します。さらに、安全策としてすべての一時ファイルに`deleteOnExit()`が設定されています。

### メモリ使用量分析

| コンポーネント | メモリ |
|--------------|--------|
| BufferedReader（CSV） | 約8KB（デフォルトバッファ） |
| Jackson JsonParser（JSON） | 約8KB（内部バッファ） |
| BufferedOutputStream | 約8KB（デフォルトバッファ） |
| 行/オブジェクト文字列 | 行ごとに異なる（通常1KB未満） |
| **合計** | **約20KB 定数** |

パーティションサイズはディスク上の一時ファイルのサイズを制御するもので、メモリではありません。入力データサイズやパーティションサイズに関係なく、メモリ使用量は一定です。

### サイズベースとアイテム数ベースの分割

CSVとJSON両方の操作で、2つの分割戦略を組み合わせることができます：

- **サイズベース**（`partitionSize` + `sizeUnit`）：累積バイト数が閾値を超えるとパーティションを区切る
- **アイテム数ベース**（`maxItems`）：アイテム数が制限に達するとパーティションを区切る
- **組み合わせ**：どちらかの制限に先に到達した時点でパーティションを区切る。`maxItems=0`でアイテム数制限を無効化。

## 操作

### Partition CSV

CSV `InputStream`をパーティションに分割します。各パーティションは有効なCSVファイルです。`includeHeader`がtrueの場合、入力の最初の行をヘッダーとして扱い、すべてのパーティションの先頭に付与します。

**パラメータ：**

| パラメータ | 型 | デフォルト | 説明 |
|-----------|------|---------|-------------|
| `input` | InputStream | `#[payload]` | CSV入力ストリーム |
| `partitionSize` | int | `50` | パーティションサイズ |
| `sizeUnit` | KB \| MB \| GB | `MB` | partitionSizeの単位 |
| `maxItems` | int | `0` | パーティションあたりの最大行数（0 = 制限なし） |
| `includeHeader` | boolean | `true` | 各パーティションにヘッダー行を含める |

**戻り値：** `PagingProvider<DataPartitionConnection, InputStream>` -- InputStreamパーティションのイテラブル

### Partition JSON

JSON配列 `InputStream`をパーティションに分割します。各パーティションは有効なJSON配列（`[...]`）です。入力はトップレベルのJSON配列である必要があります。

**パラメータ：**

| パラメータ | 型 | デフォルト | 説明 |
|-----------|------|---------|-------------|
| `input` | InputStream | `#[payload]` | JSON配列入力ストリーム |
| `partitionSize` | int | `50` | パーティションサイズ |
| `sizeUnit` | KB \| MB \| GB | `MB` | partitionSizeの単位 |
| `maxItems` | int | `0` | パーティションあたりの最大オブジェクト数（0 = 制限なし） |

**戻り値：** `PagingProvider<DataPartitionConnection, InputStream>` -- InputStreamパーティションのイテラブル

### Count Lines

8KBバッファを使用して、InputStreamのバイト数と行数を1パスでカウントします。ストリームは完全に消費されます。

**パラメータ：**

| パラメータ | 型 | デフォルト | 説明 |
|-----------|------|---------|-------------|
| `input` | InputStream | `#[payload]` | カウント対象の入力ストリーム |

**戻り値：** `Map<String, Long>`（キー：`bytes`、`lines`）

### Count JSON Items

Jacksonストリーミングパーサーを使用して、JSON配列のバイト数とトップレベルアイテム数をカウントします。ツリーは構築されず、`skipChildren()`でアイテムをスキップします。ストリームは完全に消費されます。

**パラメータ：**

| パラメータ | 型 | デフォルト | 説明 |
|-----------|------|---------|-------------|
| `input` | InputStream | `#[payload]` | JSON配列入力ストリーム |

**戻り値：** `Map<String, Long>`（キー：`bytes`、`items`）

## 使用例

### CSVを分割して各チャンクを処理

```xml
<flow name="process-large-csv">
    <http:listener config-ref="HTTP_Listener" path="/upload-csv" allowedMethods="POST">
        <http:response statusCode="200" />
        <!-- Muleがリクエストボディをバッファリングするのを防止 -->
        <non-repeatable-stream />
    </http:listener>

    <data-partition:partition-csv partitionSize="50" sizeUnit="MB"
                                  includeHeader="true"
                                  outputMimeType="text/csv; streaming=true">
        <!-- 重要: Muleが全パーティションを実体化するのを防止 -->
        <non-repeatable-iterable />
    </data-partition:partition-csv>

    <foreach>
        <logger level="INFO" message="パーティション #[vars.counter] を処理中" />
        <!-- 各payloadはヘッダー付き約50MBのCSV InputStream -->
        <http:request method="POST" url="http://target-api/import"
                      sendBodyMode="ALWAYS">
            <http:body>#[payload]</http:body>
        </http:request>
    </foreach>
</flow>
```

### JSON配列をアイテム数で分割

```xml
<flow name="process-large-json">
    <data-partition:partition-json partitionSize="100" sizeUnit="MB"
                                   maxItems="10000">
        <non-repeatable-iterable />
    </data-partition:partition-json>

    <foreach>
        <!-- 各payloadは最大10000アイテムの有効なJSON配列 -->
        <http:request method="POST" url="http://target-api/batch-import">
            <http:body>#[payload]</http:body>
        </http:request>
    </foreach>
</flow>
```

### 分割前に行数をカウント

```xml
<flow name="count-and-partition">
    <set-variable variableName="csvStream" value="#[payload]" />

    <data-partition:count-lines>
        <data-partition:input>#[vars.csvStream]</data-partition:input>
    </data-partition:count-lines>

    <logger level="INFO" message="ファイルの行数: #[payload.lines]（#[payload.bytes] バイト）" />
</flow>
```

## 重要な注意事項

### `<non-repeatable-iterable />` を使用すること

パーティション操作は`PagingProvider`を返します。デフォルトでは、Muleはイテラブルをリピータブル（再生可能）ラッパーで包みますが、これは**全パーティションをメモリにバッファリング**してしまい、目的が完全に無効化されます。操作タグ内に必ず`<non-repeatable-iterable />`を追加してください。

### HTTP入力：`<non-repeatable-stream />` とストリーミングMIMEタイプを使用

HTTP Listenerで大きなファイルを受信する場合、Muleがリクエストボディをバッファリングするのを防ぐ必要があります：

1. `<http:listener>`内に`<non-repeatable-stream />`を追加して、リピータブルストリームバッファリングを無効化
2. パーティション操作に`outputMimeType="text/csv; streaming=true"`を設定して、DataWeaveがCSV全体をパースしないようにする

### CloudHub 2.0のリクエストボディ制限

CloudHub 2.0はHTTPリクエストボディに**1GBの制限**があります。1GBを超えるファイルの場合、オブジェクトストア（S3など）を中間ストレージとして使用することを検討してください。

### ディスク容量

各パーティションは一時ファイルに書き込まれます。ピーク時のディスク使用量は約`2 x partitionSize`です（書き込み中のパーティション1つと、コンシューマーが読み取り中のパーティション1つ）。一時ファイルは`DeleteOnCloseInputStream`と`PagingProvider.close()`コールバックにより自動的にクリーンアップされます。

## ビルド

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install
```

モジュールJARは、Muleアプリケーションの`pom.xml`に依存関係として追加できます：

```xml
<dependency>
    <groupId>com.demo-group</groupId>
    <artifactId>mule-data-partition-module</artifactId>
    <version>0.1.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```
