# 没有服务器和域名，如何接入真实天气

本机开发与对外服务是两个不同阶段。电脑运行 Kotlin 后端后，调试版在模拟器上直连电脑别名，在实体手机上通过 USB 端口转发联调。其他用户脱离电脑长期使用时，网关必须部署到持续在线的 HTTPS 服务。APK 里放置和风的长期凭据不能解决公开分发后的凭据泄露问题。

## 为什么模拟器或手机显示暂时无法更新

10.0.2.2 是 Android 模拟器指向开发电脑回环接口的特殊别名，实体手机没有这个映射。手机能上网也无法通过此地址连接电脑。[Android 官方地址说明](https://developer.android.com/studio/run/emulator-networking-address)

1.0.1 曾统一使用 http://127.0.0.1:8080 加 adb reverse。127.0.0.1 指向设备自身，端口转发在模拟器或 adb 重启后可能丢失；即使电脑后端返回正常、设备已允许本地网络权限，App 仍会无法更新。

从 1.0.2 起，本机配置可继续保留 http://127.0.0.1:8080，调试版根据设备选择连接方式：

| 设备 | 实际访问地址 | 是否需要 adb reverse |
| --- | --- | --- |
| Android Studio 模拟器 | 自动将 HTTP 回环主机改为 10.0.2.2，保留端口和编码路径 | 不需要 |
| 实体手机 | 保留配置中的 127.0.0.1 | 需要，连接 USB 后运行连接脚本 |

自动改写仅限 debug 模拟器的 HTTP 回环地址；公开 HTTPS 地址与 release 地址保持原样。两种本机方式都要求电脑天气后端持续运行。电脑重启、终端关闭或后端退出后，应先重新启动后端；`adb reverse` 只转发端口，不会启动服务。这不是手机脱离电脑后可独立访问的公网服务。

## 先在本机连接

1. 编辑 backend/.env.local；JWT 和 API Key 两种方式任选其一，不能同时配置。不要把凭据填到 Android 的 local.properties。
2. JWT 私钥保存在 backend/secrets/qweather-private.pem，配置文件中的路径使用绝对路径。只有后端读取该文件；Git 已排除这些文件。
3. 在项目根目录运行：

```powershell
.\scripts\start-backend.ps1
```

4. 浏览器打开 http://127.0.0.1:8080/health，configured 应为 true。这只确认配置存在，实际权限及配额由天气请求确认。
5. 在 local.properties 添加 WEATHER_DEBUG_BASE_URL=http://127.0.0.1:8080，重新构建 Android 调试版。模拟器可直接在 Android Studio 运行 app，无需端口转发。
6. 实体手机开启 USB 调试并连接电脑，在手机上允许调试授权，再在另一个终端执行下面的连接脚本；保持 USB 与后端连接。
7. Android 17 访问受保护的本地网络时，按系统提示允许“附近的设备／本地网络”权限。该权限仅在调试清单中声明，正式版连接公开 HTTPS 网关不需要它。[Android 官方说明](https://developer.android.com/privacy-and-security/local-network-permission)
8. 若该设备曾选择示例预览，到设置里关闭示例预览。确认首页没有“示例预览”标记并成功返回真实数据；之后可在首页顶部下拉、达到“松开刷新”后松手更新，静止时提示隐藏。

本机 HTTP 仅被调试版允许。release 不读取 WEATHER_DEBUG_BASE_URL，并强制天气网关 HTTPS。

```powershell
$env:JAVA_HOME = 'C:\software\AndroidStudio\jbr'
.\gradlew.bat :app:assembleDebug
# 模拟器：在 Android Studio 运行 app，以下连接脚本可跳过。
# USB 真机：在后端已运行的前提下执行：
.\scripts\connect-device.ps1 -Install
# 多台设备同时连接时，先用 adb devices 查询序列号，再指定：
.\scripts\connect-device.ps1 -Serial '你的设备序列号' -Install
```

连接脚本用于 USB 真机：先验证电脑网关的健康状态和设备授权，再执行 adb reverse tcp:8080 tcp:8080。-Install 为可选参数，使用已有调试 APK 覆盖安装并启动，不会卸载应用。真机若通过 Android Studio 运行，可先执行不带 -Install 的脚本；断开 adb 或重启手机后需重建转发。模拟器不依赖此脚本，连接失败时应先检查电脑后端和本地网络权限。App 按设备显示对应诊断，不把服务不可达简单当成设备断网。

更名前曾验证 build/review/window-weather-local-debug.apk；该路径仅为历史联调记录，不是当前随想天气的分发包。当前调试版请按上述步骤配置地址后重新构建，安装 ID 为 com.thinkingweather.android.debug。使用模拟器访问电脑的 10.0.2.2:8080 时，需要电脑上的后端持续运行；可通过 scripts/start-backend.ps1 启动，已构建过后端时可加 -UseInstalledDistribution 直接运行现有程序。本机联调没有公网入口，不能作为向其他用户分发的正式服务。

## 托管容器服务：不需要自购域名

例如 Render 的 Web Service 会分配 onrender.com 子域名并提供 HTTPS。它能够构建 Docker 容器；选择支持常驻的实例即可运行这个无数据库网关。此处只提供部署文件，没有创建账户、服务或付费资源。

1. 将不包含任何凭据的代码推送到自己的 Git 仓库。
2. 在托管平台创建 Docker Web Service，构建上下文为仓库根目录，Dockerfile 为 backend/Dockerfile.hosted。
3. 设置 BIND_ADDRESS=0.0.0.0；程序会读取平台给定的 PORT。
4. 将 API Host、ID 和所选认证变量保存在平台的环境变量或 Secrets。JWT 私钥使用平台的 Secret File，并将 QWEATHER_PRIVATE_KEY_FILE 指向该文件；不要加入仓库或 Docker 镜像。
5. 健康检查使用 /health。完成部署后先实际访问 /v1/weather?location=101010100 验证认证与订阅，不只看 health。
6. 将服务的 HTTPS 地址填入 local.properties 的 WEATHER_BASE_URL，再生成正式签名 APK/AAB。

Render 免费实例空闲 15 分钟会休眠，唤醒约需一分钟，可能超过客户端超时；官方说明不适合生产。可将其用于体验，正式分发选择满足稳定性要求的方案，先核对当前费用、地区连接质量与自己的预算。面向中国大陆用户需实测延迟、连通性和适用的服务条款。

公开入口仍需要在边缘设置真正的用户/设备授权、按用户限流与预算告警。当前网关有进程级上游预算、IP 限流和有界缓存，无法单独证明请求来自你的 APK。反向代理不得记录坐标、搜索参数或凭据，详见 backend/README.md。

官方参考：

- [Render Web Service、默认地址与 HTTPS](https://render.com/docs/web-services)
- [免费服务的休眠与限制](https://render.com/docs/free)
- [和风认证配置](https://dev.qweather.com/en/docs/configuration/authentication/)
