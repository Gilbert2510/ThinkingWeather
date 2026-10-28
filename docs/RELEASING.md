# 版本、签名与发布

## 当前发行标识

| 项目 | 正式版 | 调试版 |
| --- | --- | --- |
| 版本号 | 1.0.1 | 1.0.1-debug |
| versionCode | 2 | 2 |
| applicationId | com.thinkingweather.android | com.thinkingweather.android.debug |
| 显示名称 | 随想天气 | 随想天气 · 开发版 |
| 签名 | 本机独立发布证书 | Android 默认调试证书 |

版本来源为仓库根目录 version.properties。Android 与后端读取 VERSION_NAME；Android 另读取 VERSION_CODE。以后的每次正式发布，VERSION_CODE 都必须递增，VERSION_NAME 按 major.minor.patch 维护。

版本在前次整理中按新发行系列重置为 1，本次网络与界面改进发布为 1.0.1 / versionCode 2。1.0.0 更名时已建立 com.thinkingweather.android 安装 ID，本次继续沿用，可覆盖更新使用同一发布证书的随想天气 1.0.0 并保留数据。更名前的旧包属于另一个应用，其收藏、偏好和缓存不会自动迁移。调试版使用独立包名，可与正式版并存。已上架应用不能通过更名或重置版本号绕过商店的安装 ID 和版本递增规则。[Android 版本规则](https://developer.android.com/studio/publish/versioning)

## 已生成的本地证书

本机已经生成：

- signing/release-keystore.p12：PKCS#12 密钥库，RSA 3072 位，有效期 10000 天。
- signing/keystore.properties：本地签名配置，包括密钥库相对路径、别名及随机密码。
- 别名：thinkingweather-release；本次只调整密钥别名，保留原私钥与证书。

现有证书的 SHA-256 指纹仍为 400bf419f75110917b42e58f1a19195c80dbdc357989ebc0e5d4ed671763e050。现有证书主体仍为 CN=Window Weather, OU=Android App，属于历史签名身份，不决定手机上显示的软件名称。不要为改证书中的名称而替换密钥或证书；显示名称已通过 Android 资源改为“随想天气”。更名前的本机签名备份位于已被忽略的 signing/backups/before-thinkingweather/，其中也含敏感材料。

这些实际文件被 Git 与 Docker 忽略；Windows 上目录权限限制为当前账户和 SYSTEM。密码不写入构建脚本、命令行参数或聊天。生成脚本通过短暂的进程环境传递密码，完成后清除环境值。

请立即将密钥库和密码分别备份到安全位置。今后的 APK 更新必须保留同一发布证书；不要再次生成密钥来替换已分发的证书。[Android 签名规则](https://developer.android.com/studio/publish/app-signing)

其他电脑首次配置可先恢复备份。仅新项目尚无证书时，才运行：

```powershell
$env:JAVA_HOME = '你的 JDK 25 目录'
.\scripts\new-release-keystore.ps1
```

新项目生成脚本使用 thinkingweather-release 别名与 CN=ThinkingWeather, OU=Android App 主体；这些是新生成证书的默认参数，不会改写本机已经存在的证书。脚本检测到任一现有密钥或配置就会拒绝覆盖。勿删除现有文件来绕过这个保护；它们是后续版本升级所需的身份凭据。

## 构建命令

```powershell
.\gradlew.bat :app:assembleSignedRelease
.\gradlew.bat :app:bundleSignedRelease
```

产物：

- app/build/outputs/apk/release/app-release.apk：已签名 APK。
- app/build/outputs/bundle/release/app-release.aab：已签名商店 Bundle。

Signed 任务会先验证私有签名配置。对没有签名配置的 GitHub 干净克隆，普通 assembleRelease 允许生成未签名包供 CI 检查；不会偷偷使用调试证书作为发布证书。

生成发布包之前，在本机 local.properties 配置正式 HTTPS 网关 WEATHER_BASE_URL。未配置时，即使 APK 已签名，仍展示示例天气。签名与真实服务接入是独立配置。调试版专用 WEATHER_DEBUG_BASE_URL 绝不会替代正式地址。

## CI 中配置签名

可通过私密环境变量代替本机配置：

| 变量 | 用途 |
| --- | --- |
| WEATHER_STORE_FILE | 密钥库路径，项目相对路径或绝对路径 |
| WEATHER_STORE_PASSWORD | 密钥库密码 |
| WEATHER_KEY_ALIAS | 发布密钥别名 |
| WEATHER_KEY_PASSWORD | 密钥密码 |

四项要同时提供；不完整配置会立即报错。密钥库应由 CI 的 Secret 文件或受保护步骤注入，默认类型 PKCS12。不要在 YAML、GitHub Issue、日志、截图或上传的构建诊断包中写密码。默认验证工作流不需要这些 Secrets，也不负责发布。

分发 APK 时，统一使用 dist/thinking-weather-1.0.1.apk、dist/thinking-weather-1.0.1.aab 和 dist/thinking-weather-1.0.1-debug.apk；以 [验证记录](VALIDATION.md) 和 dist/SHA256SUMS.txt 确认最终产物后，再上传 GitHub Releases 的附件。不要把 APK/AAB 或签名文件提交到源码分支。dist/ 仅保存本机生成文件，已被 Git 忽略；更名前的旧品牌安装包不能作为本次分发文件。

## 本机验证

完整验证：

```powershell
.\gradlew.bat :app:assembleSignedRelease :app:bundleSignedRelease :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :backend:test :backend:installDist
```

运行示例测试时可加 -PWEATHER_DEBUG_BASE_URL=，以免访问本机网关。设备测试还需要已连接的模拟器或真机：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -PWEATHER_DEBUG_BASE_URL=
```

正式版安装 ID 已变更、原应用数据不会自动迁移，这些行为应在分发说明中明确告知；不要自动卸载用户设备上的旧应用。未来 com.thinkingweather.android 的覆盖升级仍需相同发布证书与更高版本码。

## GitHub 上传前

检查 git status 和暂存区。源码、Wrapper JAR、配置模板、LICENSE、NOTICE 与文档应提交；实际密钥、密码配置、.env.local、local.properties、build/、dist/、IDE 缓存及 local-archive/ 不应提交。.gitignore 不会自动清除旧提交历史，历史中的文件需要另外评估，不能靠新 ignore 规则撤回。
