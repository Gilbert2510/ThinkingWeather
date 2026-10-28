# 随想天气 · ThinkingWeather

当前名称为 **随想天气 · ThinkingWeather**，正式安装 ID 为 com.thinkingweather.android。当前版本为 **1.0.2（versionCode 3）**，由 version.properties 统一维护。调试版使用独立的 com.thinkingweather.android.debug 包名与 1.0.2-debug 版本，可与正式版并存。

Kotlin 重写的 Android 天气应用。以柔和的天气渐变、大温度排版和半透明信息卡片呈现实时天气、24 小时趋势、7 日预报、日出日落、空气质量与预警；包含城市搜索与收藏、温度单位、前台大致定位和离线缓存。

没有配置服务时显示明确标注的内置示例。真实服务失败时显示错误与已有缓存，不会替换成演示数值。

设置与城市管理为独立全屏页面，提供平滑的前进/返回过渡。首页回到顶部后向下拖动，出现“松开刷新”时放手即可更新；不足阈值会回弹，列表中间的普通滚动和无障碍滚动不会触发刷新。真实收藏最多 30 座城市；示例预览提供完整天气场景目录，固定城市对应不同天气，独立于真实收藏保存。

进入前台或网络切换时，会短暂提示移动流量或未连接网络。本机调试配置可统一保留 http://127.0.0.1:8080：模拟器上的 debug 版会自动使用 10.0.2.2 访问电脑，重启后无需重新建立 adb 转发；真机继续使用 127.0.0.1 加 USB 转发，需执行 scripts/connect-device.ps1。两种方式都需要电脑后端持续运行。详见 [连接步骤与超时排查](docs/HOSTING.md)。

## 在 Android Studio 打开

打开本目录 Weather，Android Studio 中的 Gradle 根工程名称为 ThinkingWeather；磁盘目录不必跟随品牌更名。使用 Android Studio 的 JBR 25 作为 Gradle JDK，完成同步后运行 app。构建使用固定版本 AGP 9.4.1、Gradle 9.6.0、Kotlin 2.4.21、Compose BOM 2026.09.00；compile/target SDK 37，最低 Android 8.0（API 26）。需安装 Android SDK Platform 37.0 和 Build Tools 36.0.0。

Gradle 运行于 Java 25，应用与服务端编译目标为 JVM 17。不要将 Android 的 targetCompatibility 改为 25，也无需安装独立的 Kotlin Android 插件：AGP 9 使用内置 Kotlin。

本机终端构建（JAVA_HOME 请按自己的 Android Studio 安装目录修改）：

```powershell
$env:JAVA_HOME = 'C:\software\AndroidStudio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :backend:test
```

调试 APK 输出 app/build/outputs/apk/debug/app-debug.apk。默认可直接体验示例界面。

## 真实天气接入

长期凭据仅放在 Kotlin 后端，APK 只包含公开网关地址。旧和风 Android SDK、签名方式和共享旧域名已退出运行链路。后端默认调用当前坐标型 Weather v1；确需旧账户兼容时可通过后端配置切换 v7。

1. 按 [后端说明](backend/README.md) 配置开发者专属 API Host 和 JWT（推荐）或 API Key。
2. 没有服务器/域名时，先按 [本机联调与托管说明](docs/HOSTING.md) 使用本机网关；scripts/start-backend.ps1 读取不会提交的 backend/.env.local。
3. Android 调试版在 local.properties 配置 WEATHER_DEBUG_BASE_URL=http://127.0.0.1:8080。模拟器会自动选择电脑别名，直接在 Android Studio 运行即可；实体手机先运行 scripts/connect-device.ps1 建立 USB 转发。正式环境使用 WEATHER_BASE_URL=https://你的网关地址。示例见 local.properties.example。
4. 重新构建。如果之前开启了示例预览，在设置中关闭，然后验证真实天气。

Android 17 本机调试时需要允许“附近的设备／本地网络”权限。按上面的本机网关地址配置后，重新构建并运行 debug 版；使用时需要同时启动本地后端。前期联调记录见 [验证说明](docs/VALIDATION.md)。

发布版强制 HTTPS；仅调试版对 localhost、127.0.0.1、::1 与模拟器的 10.0.2.2 开放 HTTP。自动改写仅限 debug 模拟器的 HTTP 回环地址，保留端口与路径，公开 HTTPS 地址及 release 不改写。未配置网关时打出的包仍是明确标注的示例模式。

## 验证和分发

```powershell
.\gradlew.bat :app:testDebugUnitTest :backend:test :app:lintDebug :app:assembleDebug :app:assembleRelease :backend:installDist
# 已启动模拟器/连接设备后：
.\gradlew.bat :app:connectedDebugAndroidTest
```

本机已配置独立的私有发布证书；使用 :app:assembleSignedRelease 或 :app:bundleSignedRelease 生成签名 APK / AAB，详见 [版本与发布说明](docs/RELEASING.md)。没有私有签名配置的干净克隆可用 assembleRelease 验证未签名构建。正式分发前仍需生产 HTTPS WEATHER_BASE_URL 与真机验证。1.0.2 延续随想天气 1.0.0、1.0.1 的安装 ID 和签名，可以覆盖升级并保留数据；此前更名前旧包的城市收藏、偏好与缓存不会自动迁移。后续升级仍需使用同一发布证书。

发布之前还需要：部署持续在线的 HTTPS 网关、在入口配置用户/设备授权与配额防滥用、核对和风订阅与数据授权、补全隐私说明中的运营者信息。没有把私钥放进客户端并不代表公开网关自动具备客户端身份认证。服务端内置进程级限流与上游请求预算，详见 backend/README.md。

分发文件统一使用 dist/thinking-weather-1.0.2.apk、dist/thinking-weather-1.0.2.aab 与 dist/thinking-weather-1.0.2-debug.apk，校验值见 dist/SHA256SUMS.txt；实际构建结果见 [验证记录](docs/VALIDATION.md)。未配置生产网关的分发包使用明确标注的内置示例。

详见 [逐文件结构说明](docs/PROJECT_STRUCTURE.md)、[架构](docs/ARCHITECTURE.md)、[清理记录](docs/CLEANUP.md)、[签名与发布](docs/RELEASING.md)、[许可证说明](docs/LICENSING.md)、[隐私说明](docs/PRIVACY.md)、[验证记录](docs/VALIDATION.md)。

## GitHub 与许可证

继续采用现有的 [Apache License 2.0](LICENSE)，保留原作者归属和 [NOTICE](NOTICE)。和风数据与商标仍按服务提供方条款使用。实际签名、天气凭据、本机配置、构建产物和本地归档均被 .gitignore 排除；APK/AAB 应作为 Releases 附件分发，不提交到源码目录。

## 官方资料

- [AGP 版本与 Gradle 对应关系](https://developer.android.com/build/releases/about-agp)
- [AGP 内置 Kotlin 迁移](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Gradle Java 兼容表](https://docs.gradle.org/current/userguide/compatibility.html)
- [和风认证](https://dev.qweather.com/en/docs/configuration/authentication/)
- [和风 API Host](https://dev.qweather.com/en/docs/configuration/api-host/)
