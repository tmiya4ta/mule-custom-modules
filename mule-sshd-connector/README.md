# mule-sshd-connector

[日本語](README.ja.md)

A MuleSoft Mule 4 custom connector that embeds an SFTP server using [Apache MINA SSHD](https://github.com/apache/mina-sshd).

## Features

- **Embedded SFTP server** - Starts an SFTP server within the Mule runtime
- **SFTP Upload Listener** - Mule Source that triggers flows when files are uploaded via SFTP
- **Configurable** - Port, username/password, root directory, host key path
- **Operations** - Server status check, file listing

## Usage

### Configuration

```xml
<sshd:config name="sshd-config"
             port="2222"
             username="sftpuser"
             password="sftppass"
             rootDirectory="/tmp/sshd-root"
             hostKeyPath="/tmp/sshd-hostkey.ser" />
```

### Listener (Source)

Triggers a Mule flow when a file is uploaded via SFTP:

```xml
<flow name="sftp-upload-flow">
    <sshd:listener config-ref="sshd-config" />
    <logger message="#['Upload: ' ++ attributes.filename ++ ' by ' ++ attributes.username]" />
</flow>
```

**Attributes available in the flow:**

| Attribute | Description |
|-----------|-------------|
| `attributes.filename` | Uploaded file name |
| `attributes.path` | Relative path from root directory |
| `attributes.size` | File size in bytes |
| `attributes.username` | SFTP username |
| `attributes.event` | Event type (`upload`) |

The `payload` contains the uploaded file content as `InputStream`.

### Operations

```xml
<!-- Server status -->
<sshd:server-status config-ref="sshd-config" />

<!-- List files in a directory -->
<sshd:list-files config-ref="sshd-config" path="data/csv" />
```

## Build

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install -DskipTests
```

## CloudHub 2.0 Limitation

This connector's SFTP server **does not work on CloudHub 2.0**. CloudHub 2.0 restricts all TCP traffic to port 8081 (HTTP only). Custom TCP ports like 2222 are blocked by Kubernetes NetworkPolicy.

**Supported environments:**
- On-premises Mule Runtime
- Runtime Fabric (RTF)

## License

Apache License 2.0
