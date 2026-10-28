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

JUnit 和 JSON-java 位于 testImplementation，不作为运行时天气引擎嵌入客户端。依赖的 Gradle/Maven 元数据与上游许可证为准；此表说明主要依赖，不冒充全部传递依赖的完整法律审计。二进制分发时仍应随实际依赖保留所需许可证和归属说明。

旧和风 SDK JAR 与旧图片已退出活跃工程，并从 Git 跟踪中移除；更名检查时，先前的本地归档目录已不存在，其原路径仅保留在历史清理记录中。旧文件不会作为本次源码或发布内容上传。新增天气图形由 Compose Canvas 绘制，没有把参考截图直接打包成应用资产。

## 天气服务与数据

代码采用 Apache-2.0，不代表和风天气的数据、商标或接口也采用此许可证。天气数据须按服务账户对应条款使用，界面继续展示和风及数据源归属；天气预警需要保留提供方要求的来源内容。[和风服务条款](https://dev.qweather.com/en/docs/terms/tos/)、[归属要求](https://dev.qweather.com/en/docs/terms/attribution/)

API 凭据、签名密钥及个人本地配置不属于公开仓库内容。
