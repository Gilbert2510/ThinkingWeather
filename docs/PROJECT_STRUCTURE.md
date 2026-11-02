# 项目结构与逐文件职责

本文按 1.1.0 的 Serverless 运行结构说明。路径相对仓库根 `Weather/`，Gradle 根工程名为 ThinkingWeather；版本、签名身份及源码许可证保持独立管理。旧本机后端归档、真实凭据、安装包和构建缓存不属于应提交的源码。

## 目录

```text
Weather/
├─ app/                         唯一 Android Gradle 模块
│  ├─ build.gradle.kts          构建、版本、HTTPS 地址、签名与依赖
│  ├─ proguard-rules.pro        R8 规则
│  └─ src/
│     ├─ main/
│     │  ├─ AndroidManifest.xml
│     │  ├─ kotlin/com/thinkingweather/android/
│     │  │  ├─ MainActivity.kt
│     │  │  ├─ WeatherViewModel.kt
│     │  │  ├─ data/            原始 API 聚合、编解码和缓存
│     │  │  ├─ model/           业务模型及日照计算
│     │  │  ├─ network/         前台网络观察
│     │  │  └─ ui/              Compose 页面、图表和交互
│     │  ├─ resources/         行政名称 Java resources 及 MIT 许可
│     │  └─ res/               图标、主题、网络与备份规则
│     ├─ debug/res/            调试版显示名称
│     ├─ testFixtures/         JVM/设备测试共享的 TestWeather 夹具
│     ├─ test/                 不进入 APK 的 JVM 自动化测试
│     └─ androidTest/          单独测试 APK 的 UI 交互测试
├─ serverless/                 FC 3 HTTP 事件代理源代码
│  ├─ index.mjs
│  ├─ package.json
│  ├─ .env.example             仅变量名和非敏感默认值
│  ├─ README.md
│  └─ test/proxy.test.mjs
├─ scripts/new-release-keystore.ps1
├─ signing/                    只提交说明与配置模板
├─ docs/                       架构、部署、隐私、许可、发布及验证说明
├─ .github/workflows/android.yml
├─ gradle/wrapper/             固定 Wrapper 与 JAR
├─ version.properties
├─ settings.gradle.kts
├─ build.gradle.kts
├─ README.md
├─ LICENSE
└─ NOTICE
```

`backend/`、本地启动/USB 连接脚本及 debug 网络放行清单已退出活跃工程。`local-archive/` 中的历史本机材料由 Git 忽略，不参与 Gradle、APK、云函数源码包或 CI。没有把旧后端的凭据迁入公开源码。

## Android 生产 Kotlin 文件

均位于 `app/src/main/kotlin/com/thinkingweather/android/`：

| 文件 | 职责 |
| --- | --- |
| MainActivity.kt | Activity 生命周期、Compose 入口、主动位置权限/获取和网络提示 |
| WeatherViewModel.kt | 当前城市与天气状态、请求代次、取消、定位结果、批量编辑和持久化协调 |
| data/WeatherRepository.kt | 公网 raw API 请求、受限响应、并发、缓存/合并、429 冷却、核心与可选响应聚合 |
| data/QWeatherMapping.kt | Weather v1/v7 字段、单位、空气/预警/生活指数和归属规范化 |
| data/WeatherJson.kt | 稳定业务模型、JSON 与持久化缓存的编解码 |
| data/AdministrativeNames.kt | 固定省/父级行政名称表的保守区县显示名补全 |
| data/WeatherStore.kt | 城市/顺序/选择/定位标记的原子保存、温度单位和有界真实天气缓存 |
| data/CitySummaryStore.kt | 单独持久化最多 30 条当前天气摘要 |
| data/CityWeatherSummary.kt | 摘要模型、有效期与并发加载、城市搜索错误分类 |
| data/CityEditing.kt | 重排身份校验、批量删除至少保留一城的纯逻辑 |
| model/WeatherModels.kt | 城市、实况、预报、空气、生活指数、单位、天气分类及风向模型 |
| model/SolarWindow.kt | 城市日期/时区下的日照阶段与太阳进度计算，缺失/夜间不伪造位置 |
| model/WeatherDaylight.kt | 结合城市时区、对应日期日照与各小时预报时间判定昼夜，兼容 v1 统一天气图标 |
| network/NetworkState.kt | 有效互联网、移动流量/离线分类和提示文字 |
| network/NetworkMonitor.kt | 前台 ConnectivityManager 监听和网络回调注销 |
| ui/WeatherApp.kt | 页面路由、主页固定栏、天气卡片与详情入口、定位提示收起 |
| ui/CityManagement.kt | 收藏浏览/编辑、位置标记、全选/确认删除、拖拽与无障碍重排 |
| ui/CitySearchPage.kt | 独立城市搜索、国内/国际热门目录与选择 |
| ui/WeatherPages.kt | 设置、共享页头和基础设置组件 |
| ui/WeatherDetailPages.kt | 隐私、关于及来源/许可链接 |
| ui/WeatherInsightsPage.kt | 天气详情分区、入口定位、小时/单日选择与标签同步 |
| ui/WeatherInsightData.kt | 数值有效性、温标与时区、图表域及分区可用性纯计算 |
| ui/WeatherInsightCharts.kt | 有单位的曲线、独立降水柱图和比例环图 |
| ui/FrostedToolbar.kt | 有界 GPU 顶部背景模糊与旧系统渐变兼容 |
| ui/PullDownRefresh.kt | 真实触摸下拉阈值/释放状态、顶部指示器与防误触 |
| ui/SunPath.kt | 渐变太阳轨迹、太阳光晕、地平线及端点绘制 |
| ui/WeatherArt.kt | 天气图形与应用线条图标，使用 Canvas 绘制 |
| ui/WeatherBackdrop.kt | 天气分类渐变、昼夜和氛围背景 |
| ui/WeatherTheme.kt | 颜色、浅深色主题与字体样式 |

行政资源为 `app/src/main/resources/administrative/china-counties.tsv`，保留 2,847 条显示名。来源、固定 revision 与生成方法见 [资源说明](../app/src/main/resources/administrative/README.md)，独立 [MIT 全文](../app/src/main/resources/licenses/province-city-china-MIT.txt) 随资源保留。它不是最新官方行政边界库。

## 云端和验证代码

`serverless/index.mjs` 导出 FC `handler`，在函数环境读取 Key/Host；`createHandler` 的可注入依赖仅用于隔离测试。文件集中处理事件兼容、Host/路由/参数校验、方法与受限 CORS、超时/大小上限、状态码、实例缓存/合并及限流。该文件是待部署源码，不能据仓库更新推断云端代码已替换。

`serverless/package.json` 定义零运行依赖的 Node 测试；`test/proxy.test.mjs` 注入模拟响应，覆盖拒绝路径、错误语义、429、缓存、并发合并和取消/超时边界，不消耗真实和风额度。`.env.example` 是控制台变量说明，不包含或加载真实值。

`app/src/test/` 覆盖映射、协议、缓存模型、城市编辑/名称、温标、风向、日照、时区、网络分类及图表计算。`app/src/testFixtures/kotlin/com/thinkingweather/android/data/TestWeather.kt` 只加入 test/androidTest 源集，提供固定天气、内存偏好与摘要缓存。`WeatherDataSource`、`WeatherPreferences`、`CitySummaryCache` 是正常依赖边界，生产构造器仍使用公网服务和 DataStore。`app/src/androidTest/` 的 `WeatherSmokeTest.kt` 用独立 Compose 内容和注入依赖运行交互，不接管或修改用户的真实偏好，也不把测试状态作为正式入口。实际执行范围与结果以 [VALIDATION.md](VALIDATION.md) 为准。

## 根目录配置与文档

`settings.gradle.kts` 只包含 app，`build.gradle.kts` 固定 Android/Compose 插件，`gradle.properties` 为构建参数；`version.properties` 统一版本。`local.properties` 只用于机器 SDK 路径等本机配置，被 Git 忽略。真实签名配置与密钥库也只在本机/CI 私密环境读取。

CI 工作流验证 Android 和 Node 源码，不含云端部署凭据。`scripts/new-release-keystore.ps1` 仅供尚无发布身份的新项目，已有密钥时拒绝覆盖；本项目应恢复既有证书而非重新生成。

[README](../README.md) 介绍运行与构建；[ARCHITECTURE](ARCHITECTURE.md) 说明数据边界；[HOSTING](HOSTING.md) 和 [云函数说明](../serverless/README.md) 说明部署；[RELEASING](RELEASING.md)、[PRIVACY](PRIVACY.md)、[LICENSING](LICENSING.md) 分别说明签名、数据处理和授权；[CLEANUP](CLEANUP.md) 与 [VALIDATION](VALIDATION.md) 保留整理与验证记录。
