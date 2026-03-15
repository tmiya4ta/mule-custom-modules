# JMX Metrics Extension for Mule 4

[English version / 英語版](README.md)

Mule 4用カスタムモジュール。JVM内のJMX MXBeanからメトリクスを収集し、JSONとして公開します。外部モニタリングエージェントなしで、Muleランタイムの健全性、CPU使用率、メモリ、ガベージコレクション、スレッドなどを監視するための軽量な`/metrics`エンドポイントを提供します。

## 概要

このモジュールは`java.lang.management.ManagementFactory`から直接JVMメトリクスを収集し、JSONとして返します。HTTPエンドポイントから呼び出して、実行中のMuleアプリケーションのリアルタイム可観測性を提供するように設計されています。

**収集するメトリクスカテゴリ：**

- OS情報と物理メモリ
- CPU負荷（プロセスとシステム）
- メモリ使用量（ヒープ、ノンヒープ、ピーク・コレクション使用量を含む個別メモリプール）
- ガベージコレクタ統計
- スレッド数とスレッド状態内訳（RUNNABLE、WAITING、TIMED_WAITING、BLOCKEDなど）
- クラスローディング統計
- JVMランタイム情報（稼働時間、PID、VMバージョン、起動引数）
- バッファプール（ダイレクト・マップドメモリ）

## 動作の仕組み

### 使用するMXBean

| MXBean | メトリクス |
|--------|---------|
| `OperatingSystemMXBean` | OS名、アーキテクチャ、バージョン、利用可能プロセッサ数、システム負荷平均 |
| `com.sun.management.OperatingSystemMXBean` | プロセス/システムCPU負荷、CPU時間、物理/スワップメモリサイズ |
| `MemoryMXBean` | ヒープ・ノンヒープメモリ使用量（init、used、committed、max） |
| `MemoryPoolMXBean` | プールごとの使用量、ピーク使用量、コレクション使用量 |
| `GarbageCollectorMXBean` | コレクタごとの名前、コレクション回数、コレクション時間、メモリプール名 |
| `ThreadMXBean` | スレッド数、ピーク数、デーモン数、総開始数、状態ごとの内訳 |
| `ClassLoadingMXBean` | ロード済み/アンロード済みクラス数 |
| `RuntimeMXBean` | VM名、ベンダー、バージョン、稼働時間、開始時刻、PID、起動引数 |
| `BufferPoolMXBean` | ダイレクト/マップドバッファ数、使用メモリ、総容量 |

### アーキテクチャ

すべてのメトリクス収集は`MetricsCollector.java`に静的メソッドとして実装されています。`JmxOperations.java`がこれらをMule SDK操作として公開し、結果をJSON文字列にシリアライズします。JDK組み込みの管理APIのみを使用し、外部依存関係は不要です。

## 操作

### Collect All Metrics（全メトリクス収集）

すべてのJMXメトリクスを単一のJSONレスポンスで返します。

```xml
<jmx:collect-metrics />
```

### Collect CPU Metrics（CPUメトリクス収集）

CPU関連のメトリクスのみを返します。

```xml
<jmx:collect-cpu />
```

**返却フィールド：**

| フィールド | 型 | 説明 |
|-------|------|-------------|
| `processCpuLoad` | double | このJVMプロセスのCPU負荷（0.0～1.0） |
| `processCpuTime` | long | このプロセスが使用したCPU時間（ナノ秒） |
| `systemCpuLoad` | double | システム全体のCPU負荷（0.0～1.0） |
| `availableProcessors` | int | 利用可能なプロセッサ数 |
| `systemLoadAverage` | double | 直近1分のシステム負荷平均 |

### Collect Memory Metrics（メモリメトリクス収集）

ヒープ、ノンヒープ、およびメモリプールごとのメトリクスを返します。

```xml
<jmx:collect-memory />
```

**返却構造：**

- `memory.heap` -- `{init, used, committed, max}`（バイト単位）
- `memory.nonHeap` -- `{init, used, committed, max}`（バイト単位）
- `memory.objectPendingFinalizationCount` -- ファイナライズ待ちオブジェクト数
- `memoryPools[]` -- プールごとの詳細配列：
  - `name` -- プール名（例："G1 Eden Space"、"Metaspace"）
  - `type` -- HEAPまたはNON_HEAP
  - `usage` -- `{init, used, committed, max}`
  - `peakUsage` -- `{init, used, committed, max}`（JVM起動以降の最大値）
  - `collectionUsage` -- `{init, used, committed, max}`（直近GC後、利用可能な場合）

### Collect GC Metrics（GCメトリクス収集）

ガベージコレクタごとの統計を返します。

```xml
<jmx:collect-gc />
```

**返却フィールド（コレクタごと）：**

| フィールド | 型 | 説明 |
|-------|------|-------------|
| `name` | string | GC名（例："G1 Young Generation"、"G1 Old Generation"） |
| `collectionCount` | long | コレクション総回数 |
| `collectionTime` | long | コレクション総時間（ミリ秒） |
| `memoryPoolNames` | string[] | このコレクタが管理するメモリプール |

### Collect Thread Metrics（スレッドメトリクス収集）

スレッド数と状態ごとの内訳を返します。

```xml
<jmx:collect-threads />
```

**返却フィールド：**

| フィールド | 型 | 説明 |
|-------|------|-------------|
| `threadCount` | int | 現在のライブスレッド数 |
| `peakThreadCount` | int | JVM起動以降のピークスレッド数 |
| `totalStartedThreadCount` | long | これまでに作成されたスレッド総数 |
| `daemonThreadCount` | int | 現在のデーモンスレッド数 |
| `states` | object | スレッド状態ごとの内訳（RUNNABLE、WAITING、TIMED_WAITING、BLOCKED、NEW、TERMINATED） |

## 完全なJSONレスポンス例

`collect-metrics`（全メトリクス一括）のレスポンス：

```json
{
  "timestamp": 1710500000000,
  "os": {
    "name": "Linux",
    "arch": "amd64",
    "version": "6.1.0",
    "availableProcessors": 2,
    "systemLoadAverage": 0.5,
    "totalPhysicalMemorySize": 4294967296,
    "freePhysicalMemorySize": 1073741824,
    "committedVirtualMemorySize": 2147483648,
    "totalSwapSpaceSize": 2147483648,
    "freeSwapSpaceSize": 2147483648
  },
  "cpu": {
    "processCpuLoad": 0.05,
    "processCpuTime": 30000000000,
    "systemCpuLoad": 0.12,
    "availableProcessors": 2,
    "systemLoadAverage": 0.5
  },
  "memory": {
    "heap": {
      "init": 268435456,
      "used": 134217728,
      "committed": 268435456,
      "max": 536870912
    },
    "nonHeap": {
      "init": 7667712,
      "used": 95000000,
      "committed": 100000000,
      "max": -1
    },
    "objectPendingFinalizationCount": 0
  },
  "memoryPools": [
    {
      "name": "G1 Eden Space",
      "type": "HEAP",
      "usage": { "init": 27262976, "used": 10485760, "committed": 27262976, "max": -1 },
      "peakUsage": { "init": 27262976, "used": 27262976, "committed": 27262976, "max": -1 },
      "collectionUsage": { "init": 27262976, "used": 0, "committed": 27262976, "max": -1 }
    },
    {
      "name": "G1 Survivor Space",
      "type": "HEAP",
      "usage": { "init": 0, "used": 3145728, "committed": 3145728, "max": -1 },
      "peakUsage": { "init": 0, "used": 3145728, "committed": 3145728, "max": -1 },
      "collectionUsage": { "init": 0, "used": 3145728, "committed": 3145728, "max": -1 }
    },
    {
      "name": "G1 Old Gen",
      "type": "HEAP",
      "usage": { "init": 241172480, "used": 50331648, "committed": 241172480, "max": 536870912 },
      "peakUsage": { "init": 241172480, "used": 50331648, "committed": 241172480, "max": 536870912 }
    },
    {
      "name": "Metaspace",
      "type": "NON_HEAP",
      "usage": { "init": 0, "used": 80000000, "committed": 82000000, "max": -1 },
      "peakUsage": { "init": 0, "used": 80000000, "committed": 82000000, "max": -1 }
    }
  ],
  "gc": [
    {
      "name": "G1 Young Generation",
      "collectionCount": 25,
      "collectionTime": 350,
      "memoryPoolNames": ["G1 Eden Space", "G1 Survivor Space"]
    },
    {
      "name": "G1 Old Generation",
      "collectionCount": 2,
      "collectionTime": 120,
      "memoryPoolNames": ["G1 Eden Space", "G1 Survivor Space", "G1 Old Gen"]
    }
  ],
  "threads": {
    "threadCount": 45,
    "peakThreadCount": 52,
    "totalStartedThreadCount": 120,
    "daemonThreadCount": 38,
    "states": {
      "RUNNABLE": 12,
      "WAITING": 18,
      "TIMED_WAITING": 14,
      "BLOCKED": 1
    }
  },
  "classLoading": {
    "loadedClassCount": 12500,
    "totalLoadedClassCount": 12600,
    "unloadedClassCount": 100
  },
  "runtime": {
    "vmName": "OpenJDK 64-Bit Server VM",
    "vmVendor": "Eclipse Adoptium",
    "vmVersion": "17.0.8+7",
    "specVersion": "17",
    "uptime": 3600000,
    "startTime": 1710496400000,
    "inputArguments": ["-Xmx512m", "-Xms256m"],
    "pid": 12345
  },
  "bufferPools": [
    {
      "name": "direct",
      "count": 10,
      "memoryUsed": 1048576,
      "totalCapacity": 1048576
    },
    {
      "name": "mapped",
      "count": 0,
      "memoryUsed": 0,
      "totalCapacity": 0
    }
  ]
}
```

## 使用例

### /metrics エンドポイントの公開

```xml
<flow name="metrics-endpoint">
    <http:listener config-ref="HTTP_Listener" path="/metrics" allowedMethods="GET" />
    <jmx:collect-metrics />
</flow>
```

### メモリのみを監視

```xml
<flow name="memory-check">
    <http:listener config-ref="HTTP_Listener" path="/metrics/memory" allowedMethods="GET" />
    <jmx:collect-memory />
</flow>
```

### スケジューラによる定期的なヘルスログ

```xml
<flow name="health-logger">
    <scheduler>
        <scheduling-strategy>
            <fixed-frequency frequency="60" timeUnit="SECONDS" />
        </scheduling-strategy>
    </scheduler>
    <jmx:collect-cpu />
    <logger level="INFO" message="CPUメトリクス: #[payload]" />
</flow>
```

### スレッド状態によるデッドロック検出

```xml
<flow name="thread-monitor">
    <http:listener config-ref="HTTP_Listener" path="/metrics/threads" allowedMethods="GET" />
    <jmx:collect-threads />
    <!-- BLOCKEDスレッドのチェック -->
    <choice>
        <when expression="#[payload.states.BLOCKED default 0 > 5]">
            <logger level="WARN" message="BLOCKEDスレッド数が多いことを検出しました！" />
        </when>
    </choice>
</flow>
```

## ビルド

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install
```

Muleアプリケーションの`pom.xml`にモジュールを依存関係として追加：

```xml
<dependency>
    <groupId>com.demo-group</groupId>
    <artifactId>mule-jmx-module</artifactId>
    <version>0.1.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```
