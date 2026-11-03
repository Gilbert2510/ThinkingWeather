# 版本、签名与发布

## 当前发行标识

| 项目 | 正式版 | 调试版 |
| --- | --- | --- |
| 版本号 | 1.2.0 | 1.2.0-debug |
| versionCode | 7 | 7 |
| applicationId | com.thinkingweather.android | com.thinkingweather.android.debug |
| 显示名称 | 随想天气 | 随想天气 · 开发版 |
| 签名 | 现有私有发布证书 | 默认 Android 调试证书 |
| 天气服务 | 公网 FC HTTPS | 同一公网 FC HTTPS |

`version.properties` 是版本来源。每次发行 `VERSION_CODE` 必须递增，`VERSION_NAME` 按 major.minor.patch 维护。1.2.0 改进城市管理与共享容器动画、提供独立官方地图、保存一次性流量提醒，并修复预警参数白名单兼容；保持 1.1.0 的 Serverless 运行链路。

当前安装 ID 与既有发布证书不变，可覆盖升级同签名的随想天气 1.0.0–1.1.0 并保留真实城市和设置。旧示例偏好忽略、示例缓存不作为真实数据；更名前不同安装 ID 的旧应用不自动迁移。[Android 版本规则](https://developer.android.com/studio/publish/versioning)

## 保留现有签名身份

本机私有材料仍为 `signing/release-keystore.p12` 与 `signing/keystore.properties`，别名 `thinkingweather-release`，RSA 3072 位。发布证书 SHA-256 保持：

`400bf419f75110917b42e58f1a19195c80dbdc357989ebc0e5d4ed671763e050`

证书主体中的 `CN=Window Weather, OU=Android App` 是历史身份，不影响显示名称；不要为了更名而替换证书。更名前私有备份在 `signing/backups/before-thinkingweather/`，同样不能提交。实际密钥、密码、签名配置与归档均由 Git 忽略，不得写到聊天、日志、APK 或源码压缩包。

请分别安全备份密钥库与密码，后续覆盖更新需要同一证书。其他机器优先恢复备份；仅对全新且尚未签名的项目使用 `scripts/new-release-keystore.ps1`，脚本检测到已有材料会拒绝覆盖。默认生成参数不会改写本机现有证书。[Android 签名规则](https://developer.android.com/studio/publish/app-signing)

## 构建和分发

```powershell
$env:JAVA_HOME = 'C:\software\AndroidStudio\jbr'
.\gradlew.bat :app:assembleSignedRelease :app:bundleSignedRelease
```

输出：

- `app/build/outputs/apk/release/app-release.apk`：签名 APK。
- `app/build/outputs/bundle/release/app-release.aab`：签名商店 Bundle。

Signed 任务先验证私有签名配置。没有配置的干净克隆可用普通 `assembleRelease` 验证未签名构建，不会自动用调试证书代替正式身份。

默认公网服务地址已进入公开构建配置，正式包不再因为缺少本机网关而展示示例。若换服务，可用 `-PWEATHER_BASE_URL=https://新云函数地址` 覆盖；必须是无账号、查询或片段的 HTTPS URL。`WEATHER_DEBUG_BASE_URL`、USB 转发、本地 HTTP 和本地网络权限已移除。密钥与专属 API Host 只在 FC 环境变量中，不能写入构建地址。

仓库内 `serverless/index.mjs` 的新防护实现需要在阿里云另行部署，修改源码/生成 APK 不会自动发布云函数。云端配置和状态排查见 [HOSTING.md](HOSTING.md)。构建通过不代表真实订阅、额度和地区覆盖已通过。

分发文件命名为 `dist/thinking-weather-1.2.0.apk`、`dist/thinking-weather-1.2.0.aab`、`dist/thinking-weather-1.2.0-debug.apk`。仅在实际打包后以 `dist/SHA256SUMS.txt` 和 [VALIDATION.md](VALIDATION.md) 核对大小、哈希、签名与限制；版本号更新本身不表示这些操作已完成。旧版本产物应存入 `dist/previous-versions/`，不混作当前分发物。

## CI 与签名配置

| 私密环境变量 | 用途 |
| --- | --- |
| WEATHER_STORE_FILE | 密钥库路径 |
| WEATHER_STORE_PASSWORD | 密钥库密码 |
| WEATHER_KEY_ALIAS | 发布别名 |
| WEATHER_KEY_PASSWORD | 私钥密码 |

四项需完整提供；本机也可使用被忽略的签名配置。CI 密钥由受保护的 Secret 文件/变量注入，不写入 YAML、Issue 或命令行。默认工作流仅执行 Android 构建、单元测试、Lint 与隔离 Node 测试，不需要天气凭据、不会部署 FC，也不负责签名或发布。

## 验证与上传前检查

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
npm --prefix serverless test
# 已连接设备后按当前测试入口执行：
.\gradlew.bat :app:connectedDebugAndroidTest
```

自动化夹具不进入正式 APK，也不启动本地天气后端。真实验证应另外检查公网城市查询、核心预报、可选内容、离线缓存与实际手机的 Wi-Fi/移动网络，记录服务端错误与限制。

提交前检查 `git status` 和暂存区：保留源码、Wrapper、配置模板、LICENSE/NOTICE 与说明；排除真实环境变量、密钥库、`local.properties`、`build/`、`dist/`、`node_modules/`、IDE 缓存和 `local-archive/`。旧 Kotlin 后端及私有配置的本机归档不作为云函数包、APK 或 Git 上传内容；`.gitignore` 不会自动撤回历史提交。

行政名称资源现在随 Android Java resources 分发，单独使用 MIT，需保留原版权和全文；天气数据仍受和风及对应来源条款约束，见 [LICENSING.md](LICENSING.md)。
