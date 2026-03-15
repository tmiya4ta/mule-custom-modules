# mule-sshd-connector

[English](README.md)

[Apache MINA SSHD](https://github.com/apache/mina-sshd) を使用した組み込み SFTP サーバーの MuleSoft Mule 4 カスタムコネクタ。

## 機能

- **組み込み SFTP サーバー** - Mule ランタイム内で SFTP サーバーを起動
- **SFTP アップロードリスナー** - ファイルアップロード時に Mule フローをトリガーする Source
- **設定可能** - ポート、ユーザー名/パスワード、ルートディレクトリ、ホストキーパス
- **オペレーション** - サーバーステータス確認、ファイル一覧

## 使用方法

### 設定

```xml
<sshd:config name="sshd-config"
             port="2222"
             username="sftpuser"
             password="sftppass"
             rootDirectory="/tmp/sshd-root"
             hostKeyPath="/tmp/sshd-hostkey.ser" />
```

### リスナー (Source)

SFTP でファイルがアップロードされると Mule フローをトリガー：

```xml
<flow name="sftp-upload-flow">
    <sshd:listener config-ref="sshd-config" />
    <logger message="#['Upload: ' ++ attributes.filename ++ ' by ' ++ attributes.username]" />
</flow>
```

**フローで利用可能な attributes:**

| Attribute | 説明 |
|-----------|------|
| `attributes.filename` | アップロードされたファイル名 |
| `attributes.path` | ルートディレクトリからの相対パス |
| `attributes.size` | ファイルサイズ（バイト） |
| `attributes.username` | SFTP ユーザー名 |
| `attributes.event` | イベント種別（`upload`） |

`payload` にはアップロードされたファイルの内容が `InputStream` として格納されます。

### オペレーション

```xml
<!-- サーバーステータス -->
<sshd:server-status config-ref="sshd-config" />

<!-- ディレクトリ内のファイル一覧 -->
<sshd:list-files config-ref="sshd-config" path="data/csv" />
```

## ビルド

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install -DskipTests
```

## CloudHub 2.0 での制限

このコネクタの SFTP サーバーは **CloudHub 2.0 では動作しません**。CloudHub 2.0 は全 TCP トラフィックをポート 8081（HTTP）のみに制限しており、2222 等のカスタム TCP ポートは Kubernetes NetworkPolicy でブロックされます。

**利用可能な環境:**
- オンプレミス Mule Runtime
- Runtime Fabric（RTF）

## ライセンス

Apache License 2.0
