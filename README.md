# 随想天气 · ThinkingWeather

Kotlin / Jetpack Compose 天气应用，当前版本 **1.1.0（versionCode 6）**。正式安装 ID 为 `com.thinkingweather.android`，调试版为 `com.thinkingweather.android.debug`，均通过同一个公网 HTTPS 云函数获取真实天气。

默认服务地址为 [阿里云 FC 天气代理](https://thinkiner-proxy-pjqxglocom.cn-hangzhou.fcapp.run)。API Key 和专属 API Host 仅保存在阿里云环境变量中，APK 不包含这些凭据。1.1.0 已移除本机 Kotlin 后端、USB 端口转发、开发 HTTP 放行、本地网络权限及 App 内的示例预览；手机和模拟器均可脱离开发电脑访问服务。

## 功能与界面

- 实况、逐小时、每日、空气质量、日出日落、生活指数及预警；接口缺失的内容不补造。
- 固定城市与操作栏，顶部滚动渐变模糊；顶部下拉松手刷新，静止时不显示提示。
- 最多 30 座收藏城市，批量删除、长按手柄平滑排序、国内/国际独立搜索和最后定位城市标记。
- 摄氏度使用 ℃，风向使用“西南偏西风”等直观名称；区县名称依据保守的行政名称对照补全。
- 蓝色天气详情页，卡片点击直达对应分区；温度/UV 曲线、降水柱图、湿度/云量环图保留原始单位与有效值。
- 前台大致定位，结果提示约 3 秒后压缩收起；移动流量和离线状态短暂提醒。
- 城市与设置保存在私有目录，天气缓存用于离线展示；服务错误不会替换成演示数据。

完整来源与许可位于“设置 → 关于”，首页保留“数据由和风天气提供”。代码许可与天气数据授权分别说明在 [LICENSING.md](docs/LICENSING.md)。

## 构建

在 Android Studio 打开 `Weather` 目录，使用 JBR 25 作为 Gradle JDK。固定构建版本为 AGP 9.4.1、Gradle 9.6.0、Kotlin 2.4.21、Compose BOM 2026.09.00；compile/target SDK 37，最低 Android 8.0（API 26）。安装 SDK Platform 37.0 与 Build Tools 36.0.0。

Gradle 运行于 Java 25，Android 字节码目标仍为 JVM 17。普通构建直接使用默认公网云函数，无需本机天气服务或 `adb reverse`。

```powershell
$env:JAVA_HOME = 'C:\software\AndroidStudio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

如需更换公开服务，可通过 Gradle 属性 `-PWEATHER_BASE_URL=https://你的云函数地址` 覆盖，必须使用 HTTPS，不能在地址中放账号、密钥、查询参数或片段。`local.properties` 只用于 Android SDK 等机器配置，不再承载本地天气服务地址。

## 云函数

当前已部署的代理由运营者管理。仓库提供 [serverless/index.mjs](serverless/index.mjs) 作为更严格的替换实现，兼容现有的 `QWEATHER_KEY` / `QWEATHER_HOST` 变量与官方原始 API 路径；它包含固定 Host/路由校验、超时和响应上限、错误码、缓存、请求合并与实例级限流。

**仓库代码的更新不等于阿里云已部署更新。** 请按 [云函数部署说明](serverless/README.md) 在控制台替换源码并重新发布，再核对真实请求。公开匿名服务的全局额度控制仍应配置在云端入口，实例内的限流不能替代跨实例配额。排查网络、429 和认证问题见 [HOSTING.md](docs/HOSTING.md)。

## 验证与分发

自动化测试保留在 `app/src/test`、`app/src/androidTest` 和 `serverless/test`，均不作为正式运行页面或本地服务器进入 APK。Node 测试注入模拟网络，不调用真实和风服务。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
node --test serverless/test/*.test.mjs
```

设备交互测试需要连接模拟器或真机。发布证书保持不变，通过 `:app:assembleSignedRelease` / `:app:bundleSignedRelease` 生成签名 APK/AAB，见 [RELEASING.md](docs/RELEASING.md)。实际结果、产物和限制以 [VALIDATION.md](docs/VALIDATION.md) 为准。版本提升和代码中的默认 URL 本身不代表云端可用性已经验证。

源码继续采用 [Apache-2.0](LICENSE)，保留 [NOTICE](NOTICE)。真实凭据、签名、缓存、构建产物和旧本机后端归档均由 `.gitignore` 排除；APK/AAB 作为 Release 附件分发，不提交到源码分支。

项目说明：[结构与逐文件职责](docs/PROJECT_STRUCTURE.md) · [架构](docs/ARCHITECTURE.md) · [隐私](docs/PRIVACY.md) · [许可](docs/LICENSING.md) · [历史清理](docs/CLEANUP.md)。
