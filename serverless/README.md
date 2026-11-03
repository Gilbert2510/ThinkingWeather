# 阿里云 FC 天气代理

本目录是可部署的 Serverless 原始 API 代理，采用零第三方运行依赖的 ES 模块。客户端默认访问：

`https://thinkiner-proxy-pjqxglocom.cn-hangzhou.fcapp.run`

已经在线的代理与本目录新增的防护实现是两个状态：本轮只修改仓库代码，没有登录阿里云控制台或代为部署。替换并发布 `index.mjs` 后，下列防护才作用于公网函数。

本机分发目录中的 `dist/thinking-weather-fc-1.2.0.zip` 是可直接上传的函数源码包，仅包含入口、包描述及许可证，不包含环境变量、测试或本机归档。不要上传整个 Android 工程目录。

## 部署到现有函数

1. 在函数计算控制台备份现有函数代码和配置，保留原公网 URL。
2. 选择支持 ES 模块和全局 `fetch` 的内置 Node.js 运行时；本实现要求 Node.js 20 或更新版本。将 `index.mjs` 放到函数代码根目录，请求处理程序设为 `index.handler`。不需安装 npm 运行依赖；控制台若原来用 `index.js`，应同步文件名/Handler，不能只粘贴不同模块格式。
3. 保留云端 `QWEATHER_KEY` 与 `QWEATHER_HOST`。后者仅填开发者专属域名，如 `你的专属子域.xy.qweatherapi.com`，不要包含 `https://`、端口或路径。值不得提交仓库，也不要填进 Android Gradle 属性。变量模板见 [.env.example](.env.example)，无需在电脑创建真实配置。
4. 将 FC 超时设为足以覆盖 8 秒上游请求及冷启动的值，例如 15 秒，并按实际负载调整实例并发和最大实例数。配置云端全局请求频率、额度/预算告警与访问控制；匿名 URL 的调用者无法仅凭 APK 内的常量可靠认证。
5. 发布新代码。先用合法路径检验成功和错误状态，再发布 Android 包；不要反复刷新来排查 429。
6. 将控制台访问日志设置为不记录请求参数、请求/响应全文或环境变量；只按需要保留状态、耗时和请求 ID，并补齐运营者隐私说明。

FC 3 内置运行时的 HTTP 触发器将请求转为事件，使用 `rawPath`、`queryParameters` 与 `requestContext.http`。代码兼容对象、JSON 字符串、Buffer 及旧式 `path/httpMethod/queryStringParameters`。如使用自定义运行时的 Web Function/自建 HTTP 服务，该入口签名不适用，需按对应运行时适配，不能直接视作 Node HTTP Server。

## 路由与约束

| 允许路径 | 查询参数与用途 | 实例缓存 TTL |
| --- | --- | --- |
| `/geo/v2/city/lookup` | `location`、`lang`、`number`（1–20）、`adm`、`range` | 24 小时 |
| `/weather/v1/current/{lat}/{lon}` | `lang`、`localTime=true` | 120 秒 |
| `/weather/v1/hourly/{lat}/{lon}` | 上述参数和 `hours`（1–24） | 10 分钟 |
| `/weather/v1/daily/{lat}/{lon}` | 上述参数和 `days`（1–7） | 30 分钟 |
| `/airquality/v1/current/{lat}/{lon}` | `lang` | 10 分钟 |
| `/weatheralert/v1/current/{lat}/{lon}` | `lang`、可选 `localTime=true` | 不缓存 |
| `/v7/indices/1d` | `location`、`type=0`、`lang` | 60 分钟 |
| `/v7/weather/now`、`24h`、`7d` | `location`、`lang`、`unit=m`，兼容已有调用 | 120 秒 / 10 分钟 / 30 分钟 |

路径坐标限制为合法经纬度，最多两位小数；未知路径或参数不会转发。只允许 GET；OPTIONS 仅用于明确允许的网页来源，且不访问上游。只有服务端配置的 `*.qweatherapi.com` 专属域名可作为上游，不接受调用者给出的 Host、URL、认证参数或其他请求方法。上游重定向被拒绝。

代理保留成功 JSON；HTTP 错误和旧版 JSON `code` 错误映射为实际错误状态，返回通用诊断而不暴露上游正文、Host 或密钥。响应头 `Age` 表示本实例缓存年龄；过期记录不作为成功数据回传。预警完全不缓存，避免把已结束/取消的消息重新作为新预警提供。

## 有界资源与配额

环境变量的默认值和允许范围由 `index.mjs` 校验，非法配置返回通用 500。

| 变量 | 默认值 | 作用 |
| --- | --- | --- |
| `FC_FETCH_TIMEOUT_MS` | 8000 | 包含响应读取的上游超时，100–15000 毫秒 |
| `FC_MAX_RESPONSE_BYTES` | 1048576 | 解压后读取上限，最多 2 MiB |
| `FC_CACHE_ENTRIES` | 256 | 实例 LRU 最大条数，最多 1024 |
| `FC_CACHE_BYTES` | 8388608 | 实例缓存总字节上限，最多 32 MiB |
| `FC_PEER_RPM` | 60 | 每实例中每个直接连接端的分钟上限 |
| `FC_INSTANCE_RPM` | 600 | 每实例入站分钟上限 |
| `FC_UPSTREAM_RPM` | 180 | 每实例上游分钟预算 |
| `FC_ALLOWED_ORIGINS` | 空 | 逗号分隔的精确 HTTPS Origin，空时不发 CORS 允许头 |

同一缓存缺失最多合并成一次请求，最多 8 个不同上游请求同时在途；失败短暂冷却，429 将 `Retry-After` 约束在 1–3600 秒（无有效值时为 30 秒），并在本实例暂停新的上游请求。进程退出或扩容后缓存和计数不会跨实例共享。不能将实例级预算乘以不受控的实例数后仍宣称有全局额度保护，应在 API 网关/WAF/受控入口配置集中配额和必要的用户授权。CORS 不是 Android 客户端认证；在 APK 放固定 token 也不是保密凭据。

连接端计数使用 FC 事件里的 `requestContext.http.sourceIp`，不盲目信任调用者填写的 `X-Forwarded-For`。经过代理时它可能是共享的直接对端地址；需要按实际可信代理拓扑配置入口限流，不能把它当作稳定用户身份。

## 自动化验证

```powershell
node --test serverless/test/*.test.mjs
# 或在本目录：
npm test
```

测试只使用注入的响应，不访问真实公网服务、不读取真实环境变量，也不运行本地天气服务。源码包部署仅需 `index.mjs`，`.env.example` 和 `test/` 不包含真实配置，不必上传到云函数执行包。

## 官方协议

1.2.0 补齐预警 `localTime=true` 参数白名单；客户端省略此可选参数以兼容已部署的旧规则，无需先更换函数。真实空预警响应仍视为成功。部署本目录更新后才会改变云端参数校验，见 [预警修复记录](../docs/WEATHER_MAP.md)。

- [FC HTTP 触发器事件及返回格式](https://www.alibabacloud.com/help/en/functioncompute/http-trigger-invoking-function)
- [Node.js Handler、ES 模块及 index.handler](https://help.aliyun.com/zh/functioncompute/request-handlers)
- [和风专属 API Host](https://dev.qweather.com/docs/configuration/api-host/)
- [和风错误码](https://dev.qweather.com/docs/resource/status-code/)
- [Weather v1 实况](https://dev.qweather.com/docs/api/weather/weather-current/)
- [当日天气指数](https://dev.qweather.com/docs/api/indices/indices-forecast/)
