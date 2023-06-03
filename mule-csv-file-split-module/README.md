# CsvFileSplit Extension

CSVファイルを分割、連結するモジュールです。

## 設定
### 分割 (Split)
Splitプロセッサで分割を行います。共通設定で、OS付属のsplitコマンドを使用するように設定することも可能です。
| パラメータ  | 説明                                                  | デフォルト   |
|:----------|:-----------------------------------------------------|:-----------|
| Files     | 連結するファイルパス                                      | payload  |
| Line  | 1ファイルに格納する行数                                       | 10000   |
| Chunk size  | 分割処理の単位。デフォルトでは1000行ごとにファイルへ書き込む   | 1000   |

### 連結 (Concat)
Concatプロセッサでファイルの連結を行います。
分割されたファイルパスを配列でFilesパラメータにてうけとり連結処理を行います。
デフォルトでは、Filesパラメータにはpayloadが指定されています。
出力パスを [Target File Path]へ記述します。

### 共通設定
OSに付属のsplitコマンドを使用する場合は、パスを指定します。(Javaの実装よりも高速です)
作業用ディレクトリは、[Work dir]へ指定します。デフォルトでは/tmp/mule-workとなっています。

```
<groupId>com.demo-group</groupId>
<artifactId>mule-csv-file-split-module</artifactId>
<version>0.0.55</version>
<classifier>mule-plugin</classifier>
```
