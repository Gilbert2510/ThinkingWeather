# 官方天气地图与交互说明

1.2.0 在主页右侧加入圆形地图按钮，进入独立的“天气地图”页面。当前采用用户选择的官方网页方案，浏览 [和风官方地图](https://map.qweather.com/)，不需要新增地图 SDK 凭据，不经 FC 转发网页资源，也不抓取私有瓦片或去除官方署名。可用图层、时间轴、覆盖范围和数据时效取决于官方网页；App 不承诺网页未提供的功能。

## 和风公开 API 的能力

| 需求 | 公开服务 | 在本项目中的边界 |
| --- | --- | --- |
| 温度、降水量、风向/风速 | [天气预报 API](https://dev.qweather.com/docs/api/weather/) | 已用于单地点天气及图表；单地点响应不等于连续空间图层 |
| 临近降水 | [分钟预报 API](https://dev.qweather.com/docs/api/minutely/) | 中国地区、未来两小时；具体权限和覆盖以账户及文档为准 |
| 台风位置、路径与预测 | [热带气旋 API](https://dev.qweather.com/docs/api/tropical-cyclone/) | 可用于独立台风接入，当前地图页面展示官方站点已有内容，不新增付费查询 |
| 连续温度/降水/风场图层 | 公开目录未提供可直接集成的通用图层契约 | 不用密集调用单地点接口来伪造雷达或风场；如建设原生地图，需另确认地图与气象图层来源、授权、坐标和费用 |

## 网页边界

只有主动打开地图时才创建 WebView。主页面导航限于 `https://map.qweather.com`，用户点击的外部 HTTPS 链接交给系统浏览器；阻止其他导航协议。禁止文件/内容访问、明文混合内容和自动新窗口，不提供原生 JavaScript 桥，不绕过证书错误；关闭网页定位并禁用第三方 Cookie。地图需要 JavaScript 和 DOM 存储，正常站点缓存保存在应用私有数据中。

页面使用独立 `WeatherMapActivity`，提供稳定的原生 WebView 视口，避免网页处在 Compose 共享容器的过渡布局中。窗口淡入淡出，返回保持主页选择与滚动位置。页面展示官方域名，提供加载进度、主文档加载失败重试及浏览器后备入口。离开前台暂停 WebView，退出页面停止加载并释放；渲染进程失败时移除损坏实例。网页的瓦片/脚本也可能因服务端或网络故障无法显示，此时可尝试浏览器后备入口。App 不读取网页内部状态或注入脚本来替换官方的错误提示。

独立网页不接收 App 的位置或 Key，初始视野由官方站点决定。详见 [隐私说明](PRIVACY.md)。

## 城市页动画

城市列表采用固定高度标题与搜索入口；编辑时直接替换“我的城市 / 选择项目 / 已选择x项”，不播放界面位移动画。只有实际重排时，相邻卡片通过 `animateItem` 平滑让位。长按卡片进入编辑、选择该城，并调用一次系统长按触感，遵循设备触感设置。

按压将卡片平滑缩至 0.965 倍，手柄拖动抬至 1.065 倍；释放采用较低阻尼的弹簧，经过轻微缩小再回到原始大小。点击浏览卡片时使用 `SharedTransitionLayout / sharedBounds` 将同一城市容器展开为主页，支持反向返回。使用 Compose 动画时钟，遵循系统动画时长设置；没有自建无限计时的缩放循环。

实现依据：[Compose 弹簧动画](https://developer.android.com/develop/ui/compose/animation/customize)、[共享元素与共享边界](https://developer.android.com/develop/ui/compose/animation/shared-elements)、[长按与触感反馈](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press)。

## 预警请求修复

2026-11-03 实际验证：线上带 `localTime=true` 的预警请求返回 `400 {"error":"invalid_query"}`，同一坐标仅带 `lang=zh` 返回 `metadata.zeroResult=true, alerts=[]`。原因是旧代理白名单漏掉了官方支持的 `localTime`，不是“没有预警”本身导致失败。客户端现省略该非必需参数，按返回时间偏移解析；仓库代理也补齐白名单。

[官方预警文档](https://dev.qweather.com/docs/api/warning/weather-alert/) 支持该参数。客户端回归测试模拟旧白名单，云端回归测试确认新契约；正常空结果不显示“天气预警暂时无法更新”，真实权限、限流、网络或格式错误仍显式提示。修复客户端无需先更新线上函数；仓库函数变更须在控制台重新部署才生效。
