# 随想天气 · ThinkingWeather 服务端

Kotlin/JVM 网关统一访问和风天气，Android 只连接自己的服务端。服务端负责凭据、短期 JWT、请求合并、缓存、限流、超时、数据格式转换和上游错误隔离。没有凭据时可以启动并检查健康状态，但天气接口明确返回 `503 provider_not_configured`，不会生成天气数据。

源码使用统一的 `com.thinkingweather.gateway` 包，入口类为 `com.thinkingweather.gateway.MainKt`。生产代码位于 `src/main/kotlin/com/thinkingweather/gateway/`，测试位于对应的 `src/test/kotlin/` 目录。

## 本地启动

在项目根目录使用 Android Studio 配套 JDK 25 和 Gradle Wrapper：

```powershell
.\gradlew.bat :backend:test :backend:installDist
.\gradlew.bat :backend:run
```

首次不设置环境变量即可启动。默认只监听 `127.0.0.1:8080`。访问 `http://127.0.0.1:8080/health` 返回 `{"status":"ok","configured":false}`。`configured` 仅表示本地配置完整，不能证明和风账号权限、额度和外网连通性正常。

服务端也带独立 `settings.gradle.kts`：在只有 JDK 25 和现代 Gradle 的 Linux 构建服务器上执行 `gradle -p backend test installDist`，无需安装 Android SDK。Android 根项目构建仍使用根目录的插件版本管理。

在当前 PowerShell 会话设置服务端环境变量后启动。以下均为占位值；请在自己的机器填写实际值，不要提交、打印、截图或发送私钥：

```powershell
$env:QWEATHER_API_HOST = 'your-account.xy.qweatherapi.com'
$env:QWEATHER_PRIVATE_KEY_FILE = 'C:\private\qweather-private.pem'
$env:QWEATHER_KEY_ID = 'YOUR_CREDENTIAL_ID'
$env:QWEATHER_DEVELOPER_ID = 'YOUR_DEVELOPER_ID'
$env:QWEATHER_PROJECT_ID = 'YOUR_PROJECT_ID'
.\gradlew.bat :backend:run
```

使用 OpenSSL 3 在自己的机器生成 Ed25519 密钥：

```text
openssl genpkey -algorithm ED25519 -out qweather-private.pem
openssl pkey -pubout -in qweather-private.pem -out qweather-public.pem
```

仅将**公钥**上传到和风控制台的项目凭据页面。私钥文件必须为 PKCS#8 `BEGIN PRIVATE KEY` PEM，由服务进程以只读方式访问。密钥文件放在仓库外，生产环境使用 Secret Manager 或只读 secret mount。JWT 包含 `kid`、`iss=Developer ID`、`sub=Project ID`、`iat`、`exp`，有效期约 15 分钟，提前刷新，不在网络或日志中暴露给客户端。[官方身份认证文档](https://dev.qweather.com/en/docs/configuration/authentication/)

如已有 API KEY，可以只设置 `QWEATHER_API_HOST` 和 `QWEATHER_API_KEY`，删除全部 JWT 环境变量。KEY 只放在上游请求头 `X-QW-Api-Key` 中，不放进 URL。混用两种认证或不完整的 JWT 配置会启动失败并返回非零退出码。JWT 是推荐方式。

环境变量清单见 [.env.example](.env.example)。程序读取进程环境，不自动加载 `.env`；部署工具可使用该文件注入环境。Android 需要配置的是自己的 HTTPS 网关地址，不能配置和风凭据。

## 当前和风接口与兼容性

默认 `QWEATHER_WEATHER_API=v1` 使用当前的坐标接口：

- `/geo/v2/city/lookup`：城市名、Location ID 或经纬度检索；Location ID 经缓存后的 GeoAPI 转为坐标。
- `/weather/v1/current/{lat}/{lon}`、`/weather/v1/hourly/{lat}/{lon}?hours=24`、`/weather/v1/daily/{lat}/{lon}?days=7`：实时、24 小时和 7 天预报。
- `/weatheralert/v1/current/{lat}/{lon}`：官方天气预警。
- `/airquality/v1/current/{lat}/{lon}`：空气质量，多种标准的 AQI 保留在原始 `indexes` 中。

参考：[GeoAPI](https://dev.qweather.com/en/docs/api/geoapi/city-lookup/)、[实时天气](https://dev.qweather.com/en/docs/api/weather/weather-current/)、[逐小时](https://dev.qweather.com/en/docs/api/weather/weather-hourly-forecast/)、[逐日](https://dev.qweather.com/en/docs/api/weather/weather-daily-forecast/)、[预警](https://dev.qweather.com/en/docs/api/warning/weather-alert/)、[空气质量](https://dev.qweather.com/en/docs/api/air-quality/air-current/)。

官方仍列出城市 v7 接口，同时明确标记将弃用；仅当账号暂时需要兼容时设置 `QWEATHER_WEATHER_API=v7`，此时天气调用 `/v7/weather/now`、`/v7/weather/24h` 和 `/v7/weather/7d`。不会在 v1 失败后偷偷切换并重复付费调用。空气质量和预警始终使用当前坐标接口。[v7 弃用说明](https://dev.qweather.com/en/docs/api/weather/weather-now-webapi-v7/)

必须使用控制台分配的专属 `*.qweatherapi.com` API Host。代码拒绝 URL、IP、自定义端口、共享旧域名和外部主机；所有上游请求使用正常证书校验的 HTTPS，禁止重定向，请求路径由服务端固定。客户端无法指定上游 URL。[API Host 文档](https://dev.qweather.com/en/docs/configuration/api-host/)

## 给 Android 的 HTTP 协议

仅支持 GET，不接受请求体；响应为 UTF-8 JSON。

| 请求 | 返回 |
|---|---|
| `/health` | `status` 和 `configured`；不访问和风、不验证外部可用性 |
| `/v1/cities?q=北京` | `location` 城市数组，保留 `id/name/adm1/adm2/country/lat/lon/tz` 等上游字段，另有 `refer/stale` |
| `/v1/cities?q=116.41%2C39.92` | 同上，用于用户授权后的坐标反查 |
| `/v1/weather?location=101010100` | 以下聚合结构 |
| `/v1/weather?location=116.41%2C39.92` | 同上；参数顺序为**经度、纬度**，与上游路径的纬度/经度顺序不同 |

```json
{
  "now": {},
  "hourly": [],
  "daily": [],
  "warnings": [],
  "air": null,
  "refer": {"sources": [], "license": []},
  "partialFailures": [],
  "stale": false,
  "cacheAgeSeconds": 0,
  "fetchedAt": "2026-10-28T04:00:00Z"
}
```

上面只展示结构，正常天气请求要求实际温度及非空的小时、日预报。v1 响应在网关内统一成 app 使用的 `temp/feelsLike/icon/text/windSpeed/humidity/vis`、小时 `fxTime/temp/icon/pop`、逐日 `fxDate/tempMin/tempMax/iconDay/textDay/sunrise/sunset/uvIndex` 字段。温度使用 °C，风速转为 km/h，能见度转为 km，湿度和降水概率转为百分比。缺失字段保持缺失，禁止将未知数值填成 0。预警保留当前字段并增加 `title/text/pubTime/typeName`；取消消息不作为活动预警展示。

v1 当前天气不提供 `obsTime`，因此服务端不伪造观测时间。`fetchedAt` 表示本次聚合使用的最旧缓存的获取时间近似值，不代表气象观测时间；`cacheAgeSeconds` 是该缓存年龄。必须结合原始预报时间和 `stale` 显示数据新鲜度。

`air` 为当前空气质量 API 的原始对象，`indexes` 内包含不同标准，不能把 QWeather AQI 的小数范围当作中国 AQI。无权限、无覆盖或上游失败时为 `null`。预警、空气质量故障可独立降级，`partialFailures` 会返回 `warnings_unavailable`、`air_unavailable`、`now_stale` 等标志；关闭某项会返回 `_disabled`。空预警和预警获取失败必须由客户端区别展示。

每个上游的 `refer` 和 `metadata.attributions` 均合并入 `refer`，包含文本声明和链接；客户端应完整展示这些归属说明。不得只显示固定商标而丢弃官方预警声明。[元数据与归属要求](https://dev.qweather.com/en/docs/resource/metadata/)

错误响应为 `{"error":"machine_readable_code"}`，不含异常堆栈、上游原文、密钥或用户位置。非法输入返回 400，未知城市 404，限流 429，配置缺失/繁忙/本地额度预算耗尽 503，上游故障 502。401/403 和上游额度故障转换为有区别的错误码以便运维定位。

## 资源与失败边界

- 12 个入站工作线程、48 个待处理任务、10 个同时处理的业务请求；上游聚合 8 个工作线程，24 个上游连接并发许可。负载饱和时返回 503 或拒绝连接。
- 每进程缓存最多 512 个项目，同时合并至多 64 个不同查询，失败冷却 10 秒，避免重试风暴。缓存仅在内存中，重启清空。
- 城市缓存 24 小时，失败可复用 7 天；实时缓存 2 分钟、过期后最多额外 15 分钟；小时缓存 10 分钟、额外 30 分钟；逐日缓存 30 分钟、额外 60 分钟；空气质量缓存 10 分钟、额外 15 分钟。缓存复用带 `stale` 标志。
- 预警缓存 60 秒，**不使用过期缓存兜底**。上游失败明确标记不可用，避免将过期预警当作现行预警。
- 上游连接超时 3 秒，完整响应超时 8 秒；最多接收 512 KiB 压缩响应、解压后最多 1 MiB。使用 gzip 减少流量。
- 输入长度、参数个数、Location ID 格式、经纬度范围、HTTP 方法和请求体均有限制。坐标规范至小数点后两位，减少精确位置留存和缓存碎片。
- 默认入站每 socket peer 每分钟 60 次、进程总计 600 次；peer 计数表最多 4096 项，满时拒绝新 peer。默认和风实际调用预算每进程每分钟 180 次；缓存命中不消耗预算，所有天气/GeoAPI/预警/空气请求共享预算。环境变量可改为 1–6000，必须根据真实订阅额度设置。
- 启动脚本限制总连接数 128、空闲连接数 16、请求头 16 KiB/40 项，并设置接收和响应时间限制。部署仍需要反向代理处理公网的连接管理、TLS、防护和超时。标准 JDK HttpServer 适合本项目的轻量内网网关，不能替代专业边缘服务或高并发服务器。[JDK HTTP 服务限制](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.httpserver/module-summary.html)

## 面向其他用户发布前的部署要求

1. 在自己的服务器部署网关，通过 HTTPS 域名及反向代理暴露，禁止直接将开发机的 8080 端口公开。反向代理应提供 TLS 证书续期、请求头/体/超时限制、连接数限制、每真实来源 IP 限流和服务健康检查。仅允许反向代理连接网关端口。
2. **当前路由是匿名天气网关，没有用户账号认证。** 如果对公网开放，攻击者能消耗你购买的接口额度；APK 内固定 token 不能解决这一问题。正式分发前在边缘层配置短期用户/安装会话、按用户额度与滥用封禁；必要时结合 Play Integrity 或国内渠道适用的设备验证。用户登录、设备验证和跨节点计费不是此进程已提供的功能。
3. 网关不信任 `X-Forwarded-For`，只按 socket peer 计数。经过代理时所有请求可能共享代理 IP 的 60 RPM 限额，必须在可信边缘层做真实 IP/用户限流，并有意识调整 `GATEWAY_PER_PEER_RPM`。不要直接采信用户传来的转发头。`GATEWAY_GLOBAL_RPM` 和 `QWEATHER_UPSTREAM_RPM` 始终保留；多副本预算需要在边缘/Redis 统一约束，当前进程不提供分布式计数。
4. 根据和风订阅 API 权限、覆盖区域、付费额度、数据使用及商业分发条款完成真实账号验收。默认请求 7 天预报、当前预警和空气质量；账号不支持时先开通相应权限，或显式关闭可选项目。网关不会假装使用其他免费来源。
5. 将 QWeather 凭据限制为服务器出口 IP 及必需 API，配置供应商消费上限和告警。禁止把私钥、API KEY、JWT、和风账号 Host 写进 Android、前端、日志或 Git；在服务器轮换密钥后重启进程即可更新凭据。保持系统时钟同步。
6. 程序不输出查询、坐标、请求头、上游响应或异常堆栈。反向代理/CDN/APM 的默认访问日志可能包含位置参数，应关闭请求 query 与认证头记录，仅保留匿名状态码、耗时、缓存命中率和聚合错误计数；设置必要的短期保留策略。在用户隐私政策中说明授权位置会发送至你的网关和和风天气。
7. 使用 systemd、容器或其他进程管理器重启服务，检查 5xx、超时、429 和预算耗尽，配置上游告警。需要更高并发时先做真实压测，再替换为成熟服务框架及共享缓存；不要仅增加线程和供应商预算。

完成 `installDist` 后，可在项目根目录构建示例镜像：

```text
docker build -t thinking-weather-gateway backend
```

镜像不包含凭据，以 UID 10001 运行。部署平台注入环境变量，并将私钥以只读 secret volume 挂载至 `QWEATHER_PRIVATE_KEY_FILE`。容器只暴露给内网反向代理；镜像默认 `BIND_ADDRESS=0.0.0.0`，本地进程仍默认 loopback。

如果云平台从 Git 仓库构建，使用多阶段版本，以**仓库根目录**为构建上下文：

```text
docker build -f backend/Dockerfile.hosted -t thinking-weather-gateway .
```

该版本在 JDK 25 阶段通过根 Gradle Wrapper 独立构建 backend，再只复制运行包到 JRE 25 镜像。它仅复制明确列出的构建文件和 `backend/src`，不会将本地 `.env`、私钥或 Android 工程配置打入镜像。托管平台配置 Dockerfile 路径为 `backend/Dockerfile.hosted`、build context 为仓库根目录，并设置本节所述服务端环境变量和只读密钥文件。进程会读取平台注入的 `PORT`，默认 8080；平台 HTTPS 域名作为 Android 网关地址。不要在 Docker build 参数中传入任何凭据。发布公网之前仍需完成上面的认证、防滥用、额度和隐私设置。

## 验证

`:backend:test` 覆盖真实 Ed25519 签名验证、JWT 当前字段、配置错误、Host/参数校验、缓存合并/有界存储/过期兜底/故障冷却、限流边界、单位转换、取消预警过滤、可选 API 降级、真实本地 HTTP 路由、代理头伪造、未配置状态和响应体大小限制。测试使用明确的离线 fixture，不调用付费接口。首次真实凭据验收需要实际网络及订阅，不在离线测试中声称通过。
