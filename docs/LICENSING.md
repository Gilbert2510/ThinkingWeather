# GitHub 仓库许可说明

建议继续使用 **Apache License 2.0**，SPDX 标识为 **Apache-2.0**。仓库已有该 LICENSE，本次保留其完整正文，并通过 NOTICE 说明原有归属与改写情况。

Apache 2.0 允许按条款使用、修改和分发源码及二进制，并包含明确的专利许可。分发时需保留许可证、相关署名和归属，标明修改；有 NOTICE 的衍生作品也需要保留适用通知。原作者既有授权和署名不会因 Kotlin 重写而自动消失。[Apache 官方正文](https://www.apache.org/licenses/LICENSE-2.0)、[应用许可的官方说明](https://www.apache.org/foundation/license-faq.html)

## 当前文件

- LICENSE：Apache 2.0 的完整权威英文文本。
- NOTICE：记录原项目、Git 历史中的贡献者及 2026 年重写与结构修改。
- 本说明：解释代码许可与第三方服务的边界，不修改 LICENSE 条款。

在 GitHub 选择许可证时选择 Apache License 2.0 即可；已有 LICENSE 无需再生成一个不同的模板覆盖它。

## 第三方组件

主要依赖按各自授权提供：

| 范围 | 主要组件 | 已核对的许可类型 |
| --- | --- | --- |
| 运行依赖 | Kotlin、kotlinx、AndroidX、Compose、DataStore | Apache-2.0 |
| 网络运行依赖 | OkHttp / Okio | Apache-2.0 |
| 测试依赖 | AndroidX Test | Apache-2.0 |
| 测试依赖 | JUnit 4.13.2 | Eclipse Public License 1.0 |
| 测试依赖 | JSON-java 20250517 | Public Domain |
| 后端显示名资源 | uiwjs/province-city-china 派生行政名称表 | MIT |

JUnit 和 JSON-java 位于 testImplementation，不作为运行时天气引擎嵌入客户端。依赖的 Gradle/Maven 元数据与上游许可证为准；此表说明主要依赖，不冒充全部传递依赖的完整法律审计。二进制分发时仍应随实际依赖保留所需许可证和归属说明。

旧和风 SDK JAR 与旧图片已退出活跃工程，并从 Git 跟踪中移除；更名检查时，先前的本地归档目录已不存在，其原路径仅保留在历史清理记录中。旧文件不会作为本次源码或发布内容上传。新增天气图形、太阳轨迹与详情图表由 Compose Canvas 绘制，顶部模糊使用平台图形能力；没有把参考截图直接打包成应用资产，也未重新引入旧图表库或图标字体。

## 行政名称资源

后端的 [china-counties.tsv](../backend/src/main/resources/administrative/china-counties.tsv) 从 [uiwjs/province-city-china](https://github.com/uiwjs/province-city-china) 的 `gh-pages` revision `547ade93bc913de0c937200d4fa218d05c0ba97b`（2026-10-11）派生，保留省、父级与县级全称共 2,847 条。它只用于显示名补全，未知或歧义记录保留提供方名称，不宣称覆盖实时官方行政边界。

该资源单独采用 MIT 许可，Copyright (c) 2019 wabg。原 [MIT 全文](../backend/src/main/resources/licenses/province-city-china-MIT.txt) 随后端资源保留；输入文件、SHA-256 与更新方法见 [资源来源说明](../backend/src/main/resources/administrative/README.md)。发布包含该资源的服务端 JAR 或镜像时，保留其版权和许可通知；项目根 LICENSE 继续是 Apache-2.0，未将第三方资源重新许可为 Apache。

## 天气服务与数据

代码采用 Apache-2.0，不代表和风天气的数据、商标或接口也采用此许可证。天气数据须按服务账户对应条款使用；设置 → 关于集中展示和风、数据来源和已提供的许可信息，保留可访问链接，首页与天气详情底部提供简短和风归属。天气预警需要保留提供方要求的来源内容。[和风服务条款](https://dev.qweather.com/en/docs/terms/tos/)、[归属要求](https://dev.qweather.com/en/docs/terms/attribution/)

本轮生活指数、扩展气象字段、空气质量标准及健康建议沿用相应数据源条款，不因重新排版或绘制图表而取得新的数据授权。服务端将每个响应的 `refer` 和 `metadata.attributions` 合并，客户端关于页保留已提供的声明与链接；启用可选指数前仍需核对账户权限、地区覆盖和商业分发条件，官方接口资料见 [后端说明](../backend/README.md)。国内/国际热门城市为随包维护的坐标入口，天气数据仍由同一服务链路提供；示例生活建议只属于明确标注的本地样例。

API 凭据、签名密钥及个人本地配置不属于公开仓库内容。
