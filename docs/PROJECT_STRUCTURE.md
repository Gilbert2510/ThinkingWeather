# 项目结构与文件职责

本文介绍随想天气（ThinkingWeather）当前维护的源码和配置。路径以仓库根目录为起点；磁盘目录仍为 Weather，Gradle 根工程名称为 ThinkingWeather。构建目录、本机配置、天气凭据和签名私钥不属于应提交的源码。

## 目录总览

```text
Weather/
├─ app/                              Android 客户端
│  ├─ build.gradle.kts               Android 构建、版本、变体、签名与依赖
│  ├─ proguard-rules.pro             发布版 R8 规则
│  └─ src/
│     ├─ main/
│     │  ├─ AndroidManifest.xml
│     │  ├─ kotlin/com/thinkingweather/android/
│     │  │  ├─ MainActivity.kt
│     │  │  ├─ WeatherViewModel.kt
│     │  │  ├─ network/               前台网络监听与可测试的状态判定
│     │  │  ├─ model/
│     │  │  │  ├─ WeatherModels.kt
│     │  │  │  └─ SolarWindow.kt
│     │  │  ├─ data/
│     │  │  │  ├─ CityEditing.kt
│     │  │  │  ├─ DemoWeather.kt
│     │  │  │  ├─ WeatherJson.kt
│     │  │  │  ├─ WeatherRepository.kt
│     │  │  │  ├─ WeatherStore.kt
│     │  │  │  ├─ CityWeatherSummary.kt
│     │  │  │  └─ CitySummaryStore.kt
│     │  │  └─ ui/
│     │  │     ├─ CityManagement.kt
│     │  │     ├─ CitySearchPage.kt
│     │  │     ├─ FrostedToolbar.kt
│     │  │     ├─ WeatherApp.kt
│     │  │     ├─ WeatherArt.kt
│     │  │     ├─ WeatherPages.kt
│     │  │     ├─ WeatherDetailPages.kt
│     │  │     ├─ WeatherInsightsPage.kt
│     │  │     ├─ WeatherInsightData.kt
│     │  │     ├─ WeatherInsightCharts.kt
│     │  │     ├─ PullDownRefresh.kt
│     │  │     ├─ WeatherBackdrop.kt
│     │  │     ├─ SunPath.kt
│     │  │     └─ WeatherTheme.kt
│     │  └─ res/                     图标、基础主题、安全与备份规则
│     ├─ debug/                      仅调试版使用的权限与网络规则
│     ├─ test/kotlin/                 客户端 JVM 单元测试
│     └─ androidTest/kotlin/          Android 设备上的 Compose 交互测试
├─ backend/                          Kotlin/JVM 天气网关
│  ├─ build.gradle.kts
│  ├─ settings.gradle.kts            后端独立构建入口
│  ├─ Dockerfile                     运行已构建的后端分发包
│  ├─ Dockerfile.hosted              从源码构建后端的多阶段镜像
│  ├─ .env.example                   不含真实凭据的配置模板
│  ├─ README.md
│  └─ src/
│     ├─ main/kotlin/com/thinkingweather/gateway/
│     │  ├─ Config.kt
│     │  ├─ Main.kt
│     │  ├─ AdministrativeNames.kt
│     │  ├─ QWeatherClient.kt
│     │  ├─ Resilience.kt
│     │  ├─ Validation.kt
│     │  ├─ WeatherMapping.kt
│     │  └─ WeatherService.kt
│     ├─ main/resources/              行政名称表、来源说明及其 MIT 许可证
│     └─ test/kotlin/com/thinkingweather/gateway/
│        ├─ GatewayTest.kt
│        ├─ AdministrativeNamesTest.kt
│        ├─ CurrentWeatherTest.kt
│        └─ WeatherIndicesTest.kt
├─ docs/                             架构、结构、运行与验证说明
├─ scripts/                          本地开发与发布辅助脚本
├─ signing/                          只提交说明与配置模板
├─ gradle/wrapper/                   固定 Gradle 版本的 Wrapper
├─ .github/workflows/android.yml     持续集成
├─ build.gradle.kts                  根插件版本
├─ settings.gradle.kts               模块与依赖仓库
├─ gradle.properties                 共享的构建参数
├─ version.properties                统一维护版本号与版本名称
├─ local.properties.example          公开网关地址的配置模板
├─ gradlew / gradlew.bat              Unix / Windows 构建入口
├─ .gitignore / .dockerignore        Git 与 Docker 上下文排除规则
├─ .gitattributes                    跨平台文本与脚本换行规则
├─ LICENSE                          源码许可证
├─ NOTICE                           衍生项目与原有授权归属说明
└─ README.md                        项目入口与常用命令
```

Android 的命名空间、Kotlin 包与正式安装 ID 为 `com.thinkingweather.android`；网关统一为 `com.thinkingweather.gateway`。Android 的 `main`、`test`、`androidTest` 源码均放在 `kotlin/`，不再借用 `java/` 目录。调试包增加 `.debug`，因此可与正式版同时安装。1.0.0 更名时曾改变安装 ID，更名前原应用的收藏、偏好与缓存不会自动迁移；当前 1.0.4 继续沿用随想天气的安装 ID 与签名配置，使用同一发布证书可覆盖 1.0.0–1.0.3。当前版本为 `versionCode=5`、`versionName=1.0.4`，调试版名称增加 `-debug`。版本号在 `version.properties` 维护，变体与签名由 `app/build.gradle.kts` 管理；不能靠修改 Kotlin 的 `package` 来改变安装身份。

## Android 生产代码

以下路径均位于 `app/src/main/kotlin/com/thinkingweather/android/`。

| 文件 | 主要内容与职责 |
| --- | --- |
| [MainActivity.kt](../app/src/main/kotlin/com/thinkingweather/android/MainActivity.kt) | 唯一 Activity；设置边到边系统栏并承载 Compose；创建 ViewModel；管理大致定位权限和一次性定位，超时、离开前台或切换城市时取消传感器请求；为 Android 17 的本机调试入口申请本地网络权限。 |
| [WeatherViewModel.kt](../app/src/main/kotlin/com/thinkingweather/android/WeatherViewModel.kt) | 定义 `WeatherUiState`，通过只读 `StateFlow` 协调天气、收藏、模式、搜索和位置；管理城市摘要的缓存预热、取消代次及每卡状态；区分搜索错误并合并同 ID 新元数据；批量删除、排序、保存最后定位城市标记，阻止示例目录编辑；顺序保存偏好并阻止迟到响应覆盖新选择。 |
| [model/WeatherModels.kt](../app/src/main/kotlin/com/thinkingweather/android/model/WeatherModels.kt) | 定义城市、实时天气、小时/每日预报、预警、空气质量、生活指数及快照；保留新鲜度、归属和单位、AQI 标准与健康建议、日夜风况及日月时间；提供摄氏 ℃/华氏转换、旧缓存风向名称兼容、天气分类、城市上限和演示检索表。 |
| [model/SolarWindow.kt](../app/src/main/kotlin/com/thinkingweather/android/model/SolarWindow.kt) | 按城市时区与预报日期计算日出前、白昼、日落后及不可用状态；只为有效白昼提供太阳进度，时间缺失、异常或日期不符时不推算位置。 |
| [data/WeatherRepository.kt](../app/src/main/kotlin/com/thinkingweather/android/data/WeatherRepository.kt) | 客户端数据入口；先验证网关地址，仅在 debug 模拟器上将 HTTP 回环主机映射为电脑别名并保留端口/编码路径，真机继续走 USB；演示模式本地生成，真实模式通过 OkHttp 请求网关；校验 HTTPS、授权、超时和响应大小，按设备区分连接诊断并传递协程取消。 |
| [data/WeatherJson.kt](../app/src/main/kotlin/com/thinkingweather/android/data/WeatherJson.kt) | 解析完整预报与 current-only 摘要，编码/解码两类缓存；校验温度、服务端缓存年龄和获取时间，保留生活指数、扩展气象字段、AQI 标准/健康建议、污染物单位、部分失败及来源信息，兼容旧缓存缺失字段。 |
| [data/WeatherStore.kt](../app/src/main/kotlin/com/thinkingweather/android/data/WeatherStore.kt) | 通过 `thinking_weather_v1` DataStore 原子保存真实收藏及顺序、当前城市、单位、演示偏好和完整快照；另存最后定位城市身份，不另存定位轨迹；上限 30 城市/60 快照；`cachedAll` 一次读取全部完整缓存，避免逐卡重复解析。 |
| [data/CityEditing.kt](../app/src/main/kotlin/com/thinkingweather/android/data/CityEditing.kt) | 城市编辑纯逻辑：排序按 location 去重并过滤未知项，保留编辑期间新增城市；批量删除原子拒绝删光全部城市，删除当前城时选中剩余首城。 |
| [data/CityWeatherSummary.kt](../app/src/main/kotlin/com/thinkingweather/android/data/CityWeatherSummary.kt) | 定义当前天气卡片及 120 秒新鲜度；摘要 loader 最多并发 2，隔离单城失败并传播取消；包含搜索的单字母等待和离线/服务错误提示策略。 |
| [data/CitySummaryStore.kt](../app/src/main/kotlin/com/thinkingweather/android/data/CitySummaryStore.kt) | 独立 DataStore `thinking_weather_city_cards_v1` 持久化最多 30 条真实摘要；按城市去重、按获取时间保留，容忍坏条目，不覆盖完整天气缓存或保存演示数据。 |
| [data/DemoWeather.kt](../app/src/main/kotlin/com/thinkingweather/android/data/DemoWeather.kt) | 依据城市和给定时间生成确定的本地样例，包括有标记的生活指数和详情字段；快照明确标记 `isDemo=true`，不访问服务端，也不作为真实请求失败时的兜底。 |
| [ui/WeatherApp.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/WeatherApp.kt) | 页面组合与分层导航；首页温度、预报、指标、空气、日照、生活和预警卡片及对应详情入口；固定顶部栏、定位结果 3 秒后压缩收起；处理加载/错误/缓存/空态与响应式布局，底部简短和风归属；含 Android Studio 预览。 |
| [ui/WeatherPages.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/WeatherPages.kt) | 全屏设置页和共用 `PageHeader`；提供温度、模式与隐私/关于入口，共用靠近安全边缘的返回布局。 |
| [ui/CityManagement.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/CityManagement.kt) | 城市卡片及批量编辑界面；前台生命周期加载每城摘要，显示定位/当前城标记；多选、全选和删除确认，至少保留一城；长按手柄拖动、边缘自动滚动、抬起/位移动画与无障碍移动动作，完成或返回时保存排序，示例目录禁止编辑。 |
| [ui/CitySearchPage.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/CitySearchPage.kt) | 独立搜索页；国内/国际固定热门坐标目录、输入与结果、已保存/名额提示及键盘处理；仅离线显示指定红字，在线错误使用中性说明；国际真实热门在示例模式禁用。 |
| [ui/FrostedToolbar.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/FrostedToolbar.kt) | 记录滚动内容绘制层，为固定顶部栏提供羽化渐变与 API 31+ 小范围 GPU 模糊；API 26–30 使用渐变遮罩，不依赖 CPU 位图模糊。 |
| [ui/WeatherDetailPages.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/WeatherDetailPages.kt) | 独立隐私与关于页、设置导航条目及加粗选中勾；汇总已提供的来源/许可，保留原文，链接仅在点击后打开有效且无 userInfo 的 HTTPS 地址。 |
| [ui/WeatherInsightsPage.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/WeatherInsightsPage.kt) | 基于当前快照的天气详情；按实有数据展示实况、小时、单日、气象指标、空气、UV、日月及生活分区，首页入口定位对应分区；提供小时/日期选择和完整数值，不额外发起天气请求。 |
| [ui/WeatherInsightData.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/WeatherInsightData.kt) | 详情纯计算：有限数值与有效范围、温度单位、图表数轴、城市当地日期时间、实际可用分区及入口安全回退；缺失不当作 0，生活等级不当作实测 UV。 |
| [ui/WeatherInsightCharts.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/WeatherInsightCharts.kt) | Compose Canvas 曲线、柱图及百分比环；分别标注坐标与单位，断点保留缺失，支持常量与零值、字体缩放和无障碍语义，具体数值由详情列表补充。 |
| [ui/PullDownRefresh.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/PullDownRefresh.kt) | 仅在列表顶部处理真实向下拖动；指示器静止时完全隐藏，达到阈值提示松开刷新，支持阻尼、短手势回弹、取消与进行中防重复；语义/键盘滚动不会误触发，刷新结束后收起。 |
| [ui/WeatherBackdrop.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/WeatherBackdrop.kt) | 按共享天气分类和昼夜选择渐变色与低对比度环境纹理；平滑过渡到新天气色彩，无额外网络和位图。 |
| [ui/SunPath.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/SunPath.kt) | Canvas 绘制分层太阳轨迹、地平线与由太阳附近向边缘变暗的渐变；只在有效白昼进度下绘制太阳，图形作为示意而非天文测量。 |
| [network/NetworkState.kt](../app/src/main/kotlin/com/thinkingweather/android/network/NetworkState.kt) | 网络状态与固定提示文字的纯逻辑；判断调试本机地址、模拟器特征及真机误用模拟器地址的诊断。 |
| [network/NetworkMonitor.kt](../app/src/main/kotlin/com/thinkingweather/android/network/NetworkMonitor.kt) | 通过系统默认网络回调监听已验证互联网及移动流量，兼顾 VPN；前台注册、后台注销，合并短时间内的重复变化。 |
| [ui/WeatherArt.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/WeatherArt.kt) | 用 Compose Canvas 绘制工具栏线条图标、晴/云/雨/雪等天气图形；无需远程图片、旧版位图或第三方图标字体。 |
| [ui/WeatherTheme.kt](../app/src/main/kotlin/com/thinkingweather/android/ui/WeatherTheme.kt) | 集中定义天气页面颜色、Material 3 明暗主题与基本文字样式；被首页与全屏子页面共用。 |

### Manifest、资源和变体

| 文件 | 作用 |
| --- | --- |
| [app/src/main/AndroidManifest.xml](../app/src/main/AndroidManifest.xml) | 声明启动 Activity、互联网、普通网络状态与大致定位权限；配置图标、主题、网络安全和禁用备份；仅启动 Activity 对外导出。 |
| [app/src/debug/AndroidManifest.xml](../app/src/debug/AndroidManifest.xml) | 仅为调试包声明 `ACCESS_LOCAL_NETWORK`；正式包不合并此清单。 |
| [app/src/debug/res/values/strings.xml](../app/src/debug/res/values/strings.xml) | 把调试包显示名称改为“随想天气 · 开发版”，方便与正式版区分。 |
| [res/drawable/ic_thinking_weather.xml](../app/src/main/res/drawable/ic_thinking_weather.xml) | 当前应用启动图标的矢量前景。 |
| [res/mipmap-anydpi-v26/ic_launcher.xml](../app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) | 自适应启动图标，组合背景、前景与单色图层；保留 Android 自适应图标的 API 26 资源限定，避免构建资源优化时被过滤。 |
| [res/values/colors.xml](../app/src/main/res/values/colors.xml) | 启动图标背景颜色。 |
| [res/values/strings.xml](../app/src/main/res/values/strings.xml) | 应用显示名称。页面文字目前主要定义在 Compose 代码中。 |
| [res/values/themes.xml](../app/src/main/res/values/themes.xml) | Activity 启动时的原生主题和初始背景；Compose 内部主题由 `WeatherTheme.kt` 负责。 |
| [res/xml/network_security_config.xml](../app/src/main/res/xml/network_security_config.xml) | 正式网络规则：禁用明文流量，使用系统证书信任链。 |
| [app/src/debug/res/xml/network_security_config.xml](../app/src/debug/res/xml/network_security_config.xml) | 同名资源覆盖主配置，仅对 localhost、127.0.0.1、::1、10.0.2.2 开放本机调试 HTTP。 |
| [res/xml/data_extraction_rules.xml](../app/src/main/res/xml/data_extraction_rules.xml) | 排除应用数据的云备份与设备迁移，配合 Manifest 的备份配置。 |
| [app/proguard-rules.pro](../app/proguard-rules.pro) | 项目自己的 R8 配置位置，和 Android 默认优化规则共同作用。 |

此处所有 `res/` 简写均相对 `app/src/main/`。页面由 Compose 生成，没有正在维护的 XML layout、LitePal 配置或旧版 Java Activity。

## 后端生产代码

以下路径均位于 `backend/src/main/kotlin/com/thinkingweather/gateway/`。

| 文件 | 主要内容与职责 |
| --- | --- |
| [Main.kt](../backend/src/main/kotlin/com/thinkingweather/gateway/Main.kt) | `main()` 与 `GatewayServer`；注册健康、城市、当前摘要及完整预报路由；统一校验方法、请求体和 URL 边界，施加入站限流、并发与响应头，有限错误输出及资源关闭。 |
| [AdministrativeNames.kt](../backend/src/main/kotlin/com/thinkingweather/gateway/AdministrativeNames.kt) | 在中国省级范围内按名称和父级唯一匹配补全区县显示名；已完整、地级中心、未知或歧义名称保持，不改变 ID、经纬度或覆盖范围。 |
| [Config.kt](../backend/src/main/kotlin/com/thinkingweather/gateway/Config.kt) | 从进程环境读取配置并校验完整性，包括生活指数等可选源开关；限制上游为开发者专属 `*.qweatherapi.com`；实现 API Key 请求头与短期 Ed25519 JWT，读取 PKCS#8 私钥；不向客户端返回这些凭据。 |
| [QWeatherClient.kt](../backend/src/main/kotlin/com/thinkingweather/gateway/QWeatherClient.kt) | 实现 `WeatherSource`；用 JDK HttpClient 发起 HTTPS 上游请求，拒绝重定向；复用缓存和上游请求预算；限制并发、完整响应时间、压缩/解压后的大小；处理 gzip 与上游错误。 |
| [Resilience.kt](../backend/src/main/kotlin/com/thinkingweather/gateway/Resilience.kt) | `GatewayException`、`Cached` 数据类型；有界 LRU 缓存、相同请求合并、失败冷却、有限过期复用；按连接端 IP 和全局请求量进行固定窗口限流。 |
| [Validation.kt](../backend/src/main/kotlin/com/thinkingweather/gateway/Validation.kt) | 校验城市检索、Location ID、经纬度和唯一查询参数；坐标统一到两位小数；明确客户端参数“经度,纬度”与上游路径“纬度/经度”的区别。 |
| [WeatherMapping.kt](../backend/src/main/kotlin/com/thinkingweather/gateway/WeatherMapping.kt) | 和风 v1 到稳定字段与物理单位、直观十六方位、日夜风况和日月字段；按首日预报日期过滤生活指数及重复/不完整记录；映射预警并过滤取消/被替代消息，合并各源 `refer` 与 `metadata.attributions`。 |
| [WeatherService.kt](../backend/src/main/kotlin/com/thinkingweather/gateway/WeatherService.kt) | 城市查询与坐标解析；`current` 只取当前天气并复用缓存；`weather` 聚合实时、24 小时、7 日及可选预警/空气/生活指数；指数 type=0 一次请求、60 分钟缓存和额外最多 60 分钟过期回退，失败不阻断核心天气；显式 v1/v7，输出新鲜度与 `partialFailures`。 |

后端可在不安装 Android SDK 的环境下独立构建：根 Wrapper 加 `-p backend` 会使用 `backend/settings.gradle.kts`。需保留根目录的 Wrapper 和 `version.properties`，后端同样从后者读取 `VERSION_NAME`。应用运行主类为 `com.thinkingweather.gateway.MainKt`。

### 后端数据资源

| 文件 | 用途 |
| --- | --- |
| [china-counties.tsv](../backend/src/main/resources/administrative/china-counties.tsv) | 2,847 条省/父级/县级全称对照，仅补全显示名；约 100 KB 的固定快照，不代表实时官方区划覆盖。 |
| [administrative/README.md](../backend/src/main/resources/administrative/README.md) | 记录上游 `uiwjs/province-city-china` 固定 revision、日期、输入 SHA-256、生成和更新边界。 |
| [province-city-china-MIT.txt](../backend/src/main/resources/licenses/province-city-china-MIT.txt) | 原 MIT 许可与 Copyright (c) 2019 wabg；随资源进入后端 JAR，分发时应保留。 |

## 测试文件

| 文件 | 验证内容 |
| --- | --- |
| [WeatherJsonTest.kt](../app/src/test/kotlin/com/thinkingweather/android/data/WeatherJsonTest.kt) | 必需字段、缺失值、城市去重、空气质量标准/健康建议及污染物单位、生活指数和扩展字段、服务端缓存时间与缓存往返解析。 |
| [WeatherNetworkTest.kt](../app/src/test/kotlin/com/thinkingweather/android/data/WeatherNetworkTest.kt) | MockWebServer 验证完整天气和 current-only 的 URL/参数/响应、错误隔离、取消及后续请求；拒绝本地网络权限时立即失败。 |
| [WeatherRepositoryTest.kt](../app/src/test/kotlin/com/thinkingweather/android/data/WeatherRepositoryTest.kt) | HTTPS、本机调试主机白名单与 URL 边界；模拟器回环映射保留端口及编码路径、真机不映射、HTTPS/release 不改写。 |
| [WeatherModelsTest.kt](../app/src/test/kotlin/com/thinkingweather/android/model/WeatherModelsTest.kt) | ℃/华氏格式、旧缓存风向名称，演示天气身份、确定性、类型与按城市差异，中文和拼音检索及城市上限。 |
| [SolarWindowTest.kt](../app/src/test/kotlin/com/thinkingweather/android/model/SolarWindowTest.kt) | 城市时区、白昼边界、日出前/日落后、缺失/异常时间及不同预报日期的太阳进度处理。 |
| [CityWeatherSummaryTest.kt](../app/src/test/kotlin/com/thinkingweather/android/data/CityWeatherSummaryTest.kt) | 摘要解析/缓存、获取时间与 TTL、并发上限、单城失败隔离、取消、单字母等待和离线搜索分类。 |
| [CityEditingTest.kt](../app/src/test/kotlin/com/thinkingweather/android/data/CityEditingTest.kt) | 批量删除全部城市的原子拒绝、当前城市保留/回退、未知与重复排序身份过滤，以及编辑期间新增城市的保留。 |
| [WeatherInsightDataTest.kt](../app/src/test/kotlin/com/thinkingweather/android/ui/WeatherInsightDataTest.kt) | 缺失/非法数值、温度转换、常量/零值/极值数轴、跨日当地时间、分区目标回退，以及生活序数不冒充 UV 测量。 |
| [NetworkStateTest.kt](../app/src/test/kotlin/com/thinkingweather/android/network/NetworkStateTest.kt) | 未验证 Wi-Fi、移动流量与指定提示文字，以及模拟器检测和受限本机地址识别。 |
| [WeatherSmokeTest.kt](../app/src/androidTest/kotlin/com/thinkingweather/android/WeatherSmokeTest.kt) | 示例/℃/预报与收藏隔离、独立搜索和隐私/关于返回、单位、下拉隐藏、太阳轨迹及场景；新增固定顶部栏、首页对应详情入口、定位结果收起与批量编辑/排序/至少一城保护；输出截图，不依赖天气服务。 |
| [GatewayTest.kt](../backend/src/test/kotlin/com/thinkingweather/gateway/GatewayTest.kt) | JWT 签名、配置与参数边界、缓存合并/过期/限流、单位转换、可选接口降级、预警取消、真实 HTTP 路由、无凭据错误和响应体上限。 |
| [AdministrativeNamesTest.kt](../backend/src/test/kotlin/com/thinkingweather/gateway/AdministrativeNamesTest.kt) | 区、县、县级市、自治县补全，既有后缀、相同父市/县、简称末字及歧义/未知名称的保守回退。 |
| [CurrentWeatherTest.kt](../backend/src/test/kotlin/com/thinkingweather/gateway/CurrentWeatherTest.kt) | 轻量路由与参数、current-only 上游请求和完整预报缓存复用、v7 兼容及十六方位转换。 |
| [WeatherIndicesTest.kt](../backend/src/test/kotlin/com/thinkingweather/gateway/WeatherIndicesTest.kt) | 指数 type=0 单次缓存请求、归属保留、关闭不请求、权限失败/无覆盖隔离、跨日与坏记录过滤，以及日夜风/月相/降水字段映射。 |

`test/` 为 JVM 测试，适合日常本地与 CI；`androidTest/` 需要设备或模拟器。具体执行结果和未验证范围见 [VALIDATION.md](VALIDATION.md)，不能将示例模式测试等同于真实 API 联调。

## 构建、脚本与部署文件

| 文件 | 作用 |
| --- | --- |
| [settings.gradle.kts](../settings.gradle.kts) | 根工程命名为 `ThinkingWeather`，注册 `app`、`backend`；限制依赖仓库为 Google Maven、Maven Central，并集中插件仓库。 |
| [build.gradle.kts](../build.gradle.kts) | 固定 Android、Kotlin/JVM、Compose 编译插件版本。 |
| [app/build.gradle.kts](../app/build.gradle.kts) | SDK 级别、应用版本与 ID、debug/release 差异、网关地址的安全校验、签名来源、Compose/R8 与 Android 依赖。 |
| [backend/build.gradle.kts](../backend/build.gradle.kts) | 后端 Kotlin/JVM 与 application 插件；读取根 `version.properties` 统一后端版本，设置运行主类、JVM 资源/HTTP 边界参数、测试与可运行分发包。 |
| [backend/settings.gradle.kts](../backend/settings.gradle.kts) | 后端脱离 Android 根工程时所需的插件与依赖仓库。 |
| [gradle.properties](../gradle.properties) | Gradle 堆、工作线程、缓存、并行和 AndroidX 参数；没有签名密码或天气凭据。 |
| [version.properties](../version.properties) | 统一版本来源：Android 使用 `VERSION_CODE` 与 `VERSION_NAME`，后端使用同一个 `VERSION_NAME`。今后每次发布应增加版本码，避免把代码版本散落在 Manifest 或脚本中。 |
| [gradle/wrapper/gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties) | 指定 Gradle 发行版与 SHA-256 校验。 |
| [gradle/wrapper/gradle-wrapper.jar](../gradle/wrapper/gradle-wrapper.jar) | Wrapper 引导程序，必须保留在 Git；不是待清理的第三方业务 SDK。 |
| [gradlew](../gradlew)、[gradlew.bat](../gradlew.bat) | 下载/复用固定 Gradle，分别用于 Unix 与 Windows。 |
| [scripts/start-backend.ps1](../scripts/start-backend.ps1) | 把本地后端配置作为数据读取并只允许固定变量名，选择 JDK，再以 Gradle 或现有分发包启动；不执行配置内容、不打印凭据。 |
| [scripts/connect-device.ps1](../scripts/connect-device.ps1) | 为 USB 真机定位 SDK adb，检查设备授权和本机后端，建立端口反向转发；可选覆盖安装并启动调试 APK，多设备时要求明确序列号。模拟器由客户端自动选择电脑别名，不依赖此脚本。 |
| [scripts/new-release-keystore.ps1](../scripts/new-release-keystore.ps1) | 为新的发布序列生成 RSA 3072 位、有效期 10000 天的 PKCS#12 密钥库及本机签名配置；使用 32 字节随机密码，通过子进程环境传给 keytool；Windows 限制为当前用户与 SYSTEM 访问，拒绝覆盖已有文件。备份和使用方式见发布说明。 |
| [signing/README.md](../signing/README.md) | 说明签名目录中可提交和必须保密的文件，以及本地签名配置的用途。 |
| [signing/keystore.properties.example](../signing/keystore.properties.example) | 不含真实密码的签名参数模板；实际 `keystore.properties` 与密钥库不提交。 |
| [backend/Dockerfile](../backend/Dockerfile) | 把 `installDist` 结果放入 JRE 镜像，使用非 root 用户运行；需要先构建后端。 |
| [backend/Dockerfile.hosted](../backend/Dockerfile.hosted) | 只复制 Wrapper、根 `version.properties`、后端构建文件与源码，使用多阶段镜像构建并运行；不依赖 Android SDK。 |
| [backend/.dockerignore](../backend/.dockerignore) | 当以后端目录为 Docker 上下文时，仅开放 Dockerfile 和已构建的分发包，避免把配置或源码旁的本机文件一起打包。 |
| [.github/workflows/android.yml](../.github/workflows/android.yml) | GitHub Actions 中安装 JDK/SDK，执行客户端和后端测试、Lint、debug/release 构建及后端分发包，再上传验证报告。 |
| [.dockerignore](../.dockerignore) | 防止本机配置、私钥、缓存和无关客户端文件进入 Docker 构建上下文。 |
| [.gitignore](../.gitignore) | 防止本机构建输出、IDE 状态、凭据和签名文件成为 Git 新增文件；对过去已跟踪的产物还需要解除跟踪。 |
| [app/.gitignore](../app/.gitignore) | Android 模块本地的 build 排除规则，补充根目录规则。 |
| [backend/.gitignore](../backend/.gitignore) | 后端单独作为工作目录时也排除环境配置、私钥、缓存和构建产物，保留公开配置模板。 |
| [.gitattributes](../.gitattributes) | 统一源码与 Unix 脚本的换行行为，明确 Windows 脚本及二进制文件的处理，降低跨平台构建差异。 |
| [local.properties.example](../local.properties.example) | Android SDK/网关本机配置的公开示例；不能放和风凭据。 |
| [backend/.env.example](../backend/.env.example) | 后端环境变量的公开模板；真正的 `.env.local` 不提交。 |

## 文档与本地文件边界

| 文件 | 用途 |
| --- | --- |
| [README.md](../README.md) | 项目入口、运行和构建命令、版本与分发说明。 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 数据流、状态管理、缓存与安全边界。 |
| [CLEANUP.md](CLEANUP.md) | 本次目录调整、30 个旧文件的退出原因、历史归档与 Git 跟踪处理。 |
| [HOSTING.md](HOSTING.md) | 没有服务器时的本机联调，以及托管网关部署步骤。 |
| [PRIVACY.md](PRIVACY.md) | 当前权限、网络和本地数据行为；发布者仍需补充运营者信息。 |
| [VALIDATION.md](VALIDATION.md) | 有日期的构建、测试和实际 API 联调记录，保留历史限制。 |
| [RELEASING.md](RELEASING.md) | 新版本序列、调试/正式安装身份、签名创建与备份，以及签名构建和生产配置要求。 |
| [LICENSING.md](LICENSING.md) | 仓库许可选择、原有 Apache 授权保留、依赖与天气数据授权的边界。 |
| [backend/README.md](../backend/README.md) | 网关配置、HTTP 协议、限流/缓存策略和部署细节。 |
| [LICENSE](../LICENSE) | 仓库源码的许可证。天气数据与依赖继续遵守各自授权。 |
| [NOTICE](../NOTICE) | 保留项目衍生关系及原有归属说明，不以许可证替代原作者声明。 |

下面这些是本机或运行时文件，不应通过 Git 上传：

- `local.properties`、后端的 `.env.local`、签名密码配置、证书私钥及实际密钥库。
- 任意模块的 `build/`、根 `.gradle/`、`.kotlin/`、IDE 状态、日志、测试报告和安装包。
- `local-archive/legacy/`：清理时旧版未使用的 QWeather SDK、图片、APK 和元数据曾按原相对路径归档于此；更名检查时该目录已不存在，详见历史清理记录。这些文件不再进入客户端构建，归档目录仍被 Git 忽略。解除跟踪和本地归档不会改写 Git 历史。

新增功能时，先按责任放到对应层：数据格式变动在网关映射及 `WeatherJson`，展示变动在 `ui`，页面状态与用户操作在 ViewModel。只有出现独立复用需求时再拆新 Gradle 模块，避免为现有规模引入空模块。
