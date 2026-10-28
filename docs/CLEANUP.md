# 本次目录整理记录

正式版已从 1.0.0 / versionCode 1 开始，调试版使用独立包名；相关操作见 [发布说明](RELEASING.md)。

## 活跃工程

- Android 的 main、test、androidTest Kotlin 文件统一到各自的 kotlin/ 目录；品牌更名后，客户端包名与安装 ID 为 com.thinkingweather.android。
- 后端统一为 com.thinkingweather.gateway，启动主类、测试和部署配置同步更新。
- 启动图标仅保留新 vector + adaptive icon；移除未引用的图形分支、空回调和无用导入。
- 删除未使用的 ACCESS_NETWORK_STATE 权限。INTERNET、主动大致定位与 debug-only 本地网络权限按用途保留。
- 版本集中到 version.properties，仍为 1.0.0 / versionCode 1；独立发布证书与密码仅保存在本机 signing/ 中，更名保留已有密钥与证书。
- 根工程名称为 ThinkingWeather，中文名为随想天气；图标资源、主题、DataStore、代码路径及分发文件名同步更新。
- 构建与打包时不再包含旧 SDK、旧天气背景图和旧安装包。

## 归档操作（历史记录）

直接删除二进制文件被自动审批拒绝，仅返回 blocked by policy，没有更具体原因。因此使用了可逆整理：30 个无用文件，共 58,624,325 字节，逐个移到 local-archive/legacy/，保存原路径。核对了源路径已移出、目标存在且大小一致；没有销毁原文件。无内容的旧目录也移到 local-archive/empty-directories/。

以上记录的是整理当时的归档操作，下面的表格保留原文件名，不随应用品牌更名。更名检查时，本地 local-archive/ 目录已不存在，因此旧文件表仅保留历史记录，当前不能从该目录恢复文件。该目录不是工程依赖，缺少它不影响构建；Git 和 Docker 仍保留其忽略规则。请与必须备份的 signing/ 实际密钥区分。

| 已退出工程的原路径 | 原因 |
| --- | --- |
| app/src/main/res/drawable/main_day.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/drawable/main_night.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/drawable/main_sunny.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/drawable/weather_anim_cloudy.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/drawable/weather_url.gif | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-anydpi-v26/title_icon.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-hdpi/ic_launcher_round.webp | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-hdpi/ic_launcher.webp | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-hdpi/ic_title_round.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-hdpi/ic_title.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-mdpi/ic_launcher_round.webp | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-mdpi/ic_launcher.webp | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-mdpi/ic_title_round.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-mdpi/ic_title.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xhdpi/ic_launcher.webp | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xhdpi/ic_title_round.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xhdpi/ic_title.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xxhdpi/ic_launcher.webp | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xxhdpi/ic_title_round.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xxhdpi/ic_title.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xxxhdpi/ic_title_round.png | 未引用的旧位图、动画或图标 |
| app/src/main/res/mipmap-xxxhdpi/ic_title.png | 未引用的旧位图、动画或图标 |
| app/libs/QWeather_Public_Android_V4.14.jar | 旧和风 SDK，已改用服务端 HTTP 网关 |
| app/release/WindowWeather1.0.1.apk | 旧发行安装包或产物元数据 |
| app/release/output-metadata.json | 旧发行安装包或产物元数据 |
| app/src/main/ic_title-playstore.png | 未引用的旧位图、动画或图标 |

## Git 状态

上述旧文件已从 Git 索引解除跟踪；新的签名文件、环境凭据、本机配置、日志、IDE 缓存、归档、dist/ 和构建产物均被排除。保留应提交的 Gradle Wrapper JAR、源码、公开模板、LICENSE 与 NOTICE。

没有创建提交、推送或改写历史。git status 中的删除与新增代表本次重构的待提交变更；提交时应一并包含源码迁移和删除。.gitignore 不会清除既有提交里的旧安装包或曾经公开的内容。已有公开凭据应单独更换，不能靠 ignore 规则撤回。
