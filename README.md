# 电视 APK 更新

面向 Android 9 及以上的安卓电视。使用遥控器方向键、OK、菜单和返回键操作。

## 功能

- 读取指定公开 GitHub 仓库的最新正式 Release，**列出其中所有 APK** 的文件名和大小，由用户选择下载哪一个。
- 在应用打开时监听局域网 API，接收 HTTP/HTTPS APK 下载链接。下载后在电视上按“安装”，再按系统提示确认。
- 启动时检查本项目 GitHub Release 中的 `tv-update.apk`，发现新版本后提示下载。
- 不需要在本地安装 Android Studio 或 Android SDK；GitHub Actions 编译并发布 APK。

首次使用前，电视需要允许本应用“安装未知应用”。普通应用无法跳过系统安装确认。

## 局域网 API

电视首页显示 IP，端口固定为 `8765`。接口仅在应用打开时可用，不设密码；请仅在可信局域网中使用。

```http
GET /api/v1/status
PUT /api/v1/source
Content-Type: application/json

{"repository":"chinasoul/BT"}
```

```http
POST /api/v1/download
Content-Type: application/json

{"url":"https://example.com/app.apk"}
```

推送下载时可另传 `sha256`，值为 64 位十六进制摘要。API 返回 `202` 表示开始下载；下载和安装仍在电视界面完成。电视端目标仓库初始为 `chinasoul/BT`，可用 `PUT /api/v1/source` 修改为其他公开仓库的 `owner/repo`。

PowerShell 示例：

```powershell
Invoke-RestMethod -Method Put -Uri 'http://电视IP:8765/api/v1/source' -ContentType 'application/json' -Body '{"repository":"chinasoul/BT"}'
Invoke-RestMethod -Method Post -Uri 'http://电视IP:8765/api/v1/download' -ContentType 'application/json' -Body '{"url":"https://example.com/app.apk"}'
```

## GitHub Actions 发布

推送分支或手动运行工作流会生成供测试的 debug APK。推送 `v` 开头的标签（如 `v1.0.0`）会使用固定密钥生成签名 APK，并作为 `tv-update.apk` 发布到本仓库 Release。本程序更新源由工作流的 `github.repository` 自动写入。

发布前在仓库 Actions Secrets 中配置：

| Secret | 内容 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | Java keystore 文件的 Base64 内容 |
| `SIGNING_STORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | 密钥别名 |
| `SIGNING_KEY_PASSWORD` | 密钥密码 |

请离线保管同一份签名密钥的备份。Android 覆盖安装要求新旧 APK 签名一致。不要用 debug APK 安装后再尝试用 release APK 覆盖；首次真机安装应使用签名 release APK。

当前本地目录尚未绑定 GitHub 远端，也没有真机测试结果。发布和 T7M 真机安装需要建好仓库并配置签名后进行。
