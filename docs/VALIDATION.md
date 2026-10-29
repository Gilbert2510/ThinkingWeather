# 本机验证记录

最新验证日期：2026-10-29（Asia/Shanghai）。1.0.0 和 1.0.1 的历史记录保留自 2026-10-28。

## 1.0.4 固定顶部栏、城市编辑与天气详情（当前交付）

版本 1.0.4 / versionCode 5，正式安装 ID com.thinkingweather.android、调试后缀 .debug 与原发布证书保持不变。

主页定位结果在可见的首页展示约 3 秒，然后淡出并向上压缩收起。顶部城市、管理和设置按钮固定；滚动区域与顶部栏分离，Android 12 及以上对顶部边缘使用 GPU 模糊与渐变羽化，API 26–30 使用渐变遮罩。下拉刷新仍只在顶部真实触摸下拉时显示。复核修复定位选城等待写盘后的迟到更新：定位选城留在可取消的定位任务内，选择、消息与标记先同步更新，城市/选择/定位标记通过同一个 DataStore 事务保存。

城市管理增加最后定位城市标记、编辑/全选/批量删除/完成及长按手柄排序；拖动时缩放与投影，相邻卡片平滑让位，并支持边缘自动滚动和无障碍上下移动。至少保留一座城市，全删提示不会偷偷删除部分城市。独立搜索页面包含国内/国际热门城市与返回；示例模式保留固定目录，国际实时城市不会冒充演示天气。

主页空气污染物移到右侧紧凑排列，大字体时保留可读单位；日出日落移到空气质量下方，之后按实际数据展示生活指数，优先穿衣、防晒、旅游等。底部恢复一行“数据由和风天气提供”，完整来源仍在关于。天气卡片进入统一蓝色详情页并定位对应分区，包含实况、小时、单日、天气指标、空气、紫外线、日月及生活指数；缺失分区隐藏。温度/UV 曲线、独立降水柱图、湿度/云量环图均保留有效值及单位，污染物不混算成饼图，生活等级不冒充实测 UV。上游缺少观测时间时明确展示获取时间，不伪造观测时间。

| 验证项目 | 结果 |
| --- | --- |
| 构建 | debug APK、设备测试 APK、签名 release APK、签名 AAB、R8、资源裁剪、后端分发包均成功 |
| JVM 测试 | 客户端 76 项、后端 28 项，共 104 项，0 失败；包含城市编辑边界、指数协议与可选失败、扩展字段缓存、图表有效值与时区等 |
| 模拟器交互 | Android 17 / API 37 上完整 12 项通过，57.205 秒；覆盖安装并直接运行 instrumentation，没有卸载或清空应用数据，结束恢复原城市、顺序、选择、模式、单位和定位标记 |
| 定位与顶部栏 | 3 秒提示消失、定位城市标记、深滚动后按钮仍可见、静止无刷新提示、真实下拉松手刷新均通过 |
| 城市编辑 | 实际长按手柄把北京拖到上海之后，完成后 ViewModel 顺序更新；全选删全被阻止，改选两城删除后保留北京，完成退出通过 |
| 搜索与详情 | 独立搜索返回、热门分类、搜索切城、小时卡片直达，以及单日/空气/UV/日月/生活/天气指标标签切换均通过；晴/雷雨/大雪/晴夜场景回归通过 |
| 真实 API | 北京返回 24 小时、7 日、16 项生活指数；伦敦坐标返回 24 小时、7 日、5 项当地支持的生活指数；两者空气可用、stale=false、partialFailures=[] |
| 指数降级 | 无权限/失败只影响生活指数，可通过 QWEATHER_ENABLE_INDICES 关闭；60 分钟缓存与额外最多 60 分钟过期复用，跨日及坏条目过滤由测试验证 |
| 图像检查 | 固定顶部栏、紧凑空气卡片、拖拽抬升/重排、独立搜索及蓝色详情图表已目视检查；截图保存在 build/104-review/ |
| Lint | 0 errors、8 warnings；消除了新代码中两处频繁滚动值读取告警，保留既有依赖升级及资源目录警告，无新增全局忽略 |
| 签名 | APK v2/v3 有效；AAB 全部 99 个内容条目签名有效且证书一致，原发布指纹不变 |
| Git 与文档 | 本地链接、源码职责覆盖、差异格式及私有/生成文件忽略检查通过；暂存区没有实际凭据、密钥库、安装包或构建日志 |

| 当前 dist/ 文件 | 字节数 | SHA-256 |
| --- | --- | --- |
| thinking-weather-1.0.4.apk | 1,748,353 | 4f8d153d8a5aa6982e1c6a306ca830b8e03166a6751740735ad4686a2432d747 |
| thinking-weather-1.0.4.aab | 4,314,464 | b89f8daa1ceec0684fb5a0d82edfbf6b60c18f4c67057e1899b94b02f29bc3bd |
| thinking-weather-1.0.4-debug.apk | 13,851,272 | 5512d4ca42cc62bdb23e4fd5f9ef0f90f0ed22c1cc0fb377d9e34a9c5bf2fdf0 |

校验文件为 dist/SHA256SUMS.txt。1.0.3 产物已归档至 dist/previous-versions/1.0.3/，不再作为当前分发文件。正式包元数据已核对为“随想天气 / 1.0.4 / code 5”；调试版与构建输出内容一致。

构建日志：build/104-verified-debug.log、build/104-release-build.log、build/104-final-build.log、build/104-package.log。最终设备日志：build/104-device-tests.log（OK 12 tests），设备测试 APK 补编日志为 build/104-device-test-build.log。早期界面测试选择器/坐标调整记录另留在 build/104-device-tests-initial.log 与 build/104-device-tests-second.log，旧 connected 报告不代表本轮结果。真实 API 结构化核对见 build/104-live-api-proof.json。

本机后端已更新并保持运行。未连接实体手机，不将模拟器结果写作真实手机 GPS 或蜂窝实测。正式签名包尚无生产 HTTPS 网关，因此默认使用明确标注的示例预览；debug 包通过电脑后端取得实时天气。独立对外使用仍需配置在线 HTTPS 网关，见 [HOSTING.md](HOSTING.md)。

## 1.0.3 温度、城市管理与设置详情（历史记录）

版本 1.0.3 / versionCode 4，安装 ID 与发布证书保持不变，可覆盖同签名的随想天气版本。摄氏温度统一为 ℃，包括主页、体感、逐小时、逐日及城市摘要；风向同时兼容上游新值和旧缓存，使用“西南偏西风”等直观名称。

主页底部数据来源与许可证移入“设置 → 关于”，多余底部文字移除。设置删除操作方式，“隐私”“关于”均进入独立全屏页面，支持返回与页面过渡，返回触控区保留 48dp。日出日落改为随当地日期、时区和时间定位太阳的渐变曲线；夜间不在白昼轨迹上显示太阳，缺失或无效时间不伪造日照位置。

城市页通过新增 /v1/current 接口补齐全部真实收藏城市的温度，摘要独立缓存 120 秒、最多并发 2 个请求；离开页面或进入后台取消待处理任务。选中标记加粗。搜索只在确认离线时显示红色“天气服务不可用，请稍后重试”，其他错误使用中性文字；单个拉丁字母显示继续输入提示。区县名通过带来源与 MIT 许可证的行政名称资源补全，并按省份、父级与唯一匹配校验，歧义名称保持上游原值；已有收藏也能更新名称而不重复添加。

| 验证项目 | 结果 |
| --- | --- |
| 构建 | debug APK、设备测试 APK、签名 release APK、签名 AAB、R8、资源裁剪与后端分发包成功 |
| 单元测试 | 客户端 61 项、后端 22 项，共 83 项，0 失败；覆盖摘要协议与并发/取消、错误分类、摄氏与风向、日照时区及行政名称歧义等 |
| 模拟器交互 | Android 17 / API 37 上 7 项通过；覆盖安装并直接运行 instrumentation，没有卸载或清除应用数据，示例测试结束恢复真实偏好 |
| 页面与截图 | ℃、全部天气场景、城市温度/加粗勾选、设置/隐私/关于、日照完整曲线、下拉释放刷新均已检查；日照截图调整后的同一用例单独重跑 1 次通过，不重复计入 7 项 |
| 真实城市数据 | 北京、上海、杭州、沙坪坝区共 4 个现有收藏均显示真实温度；上游摘要接口返回 HTTP 200；城市页保留现有收藏及当前选择 |
| 名称端到端 | /v1/cities?q=沙坪坝 返回“沙坪坝区”；模拟器搜索 shapingba 显示完整区名，选取现有同 ID 城市后主页名称同步更新，随后恢复原选中的北京 |
| 搜索状态 | 在线输入单个 c 显示中性继续输入提示，没有错误的红色离线告警；离线与服务失败分类由单元测试覆盖 |
| Lint | 0 errors、8 warnings；没有新增全局忽略 |
| 后端资源 | 分发 JAR 已包含行政名称 TSV、来源说明与原始 MIT 许可证；更新分发包后已启动，/health 为 status=ok、configured=true |
| 签名与元数据 | APK v2/v3 校验通过；AAB 全部 99 个内容条目签名有效且证书相同；正式版元数据为随想天气 / 1.0.3 / code 4 |
| Git 与文档 | 格式与本地链接检查通过；真实凭据、密钥、构建产物、安装包和截图日志继续被忽略 |

| 当前 dist/ 文件 | 字节数 | SHA-256 |
| --- | --- | --- |
| thinking-weather-1.0.3.apk | 1,663,057 | cc159cef1255e4a4629676913d0c01397e76cc2bd9579edb8e372198b7dd9ad2 |
| thinking-weather-1.0.3.aab | 4,061,583 | 9bbc0d839ccf4970dd26c45be194f624e44728763c267757f34a141282a51d76 |
| thinking-weather-1.0.3-debug.apk | 13,261,798 | a20a47ff37e88c71d42d5a8c9bd324c99b0b9029f9e644725bf9f428487a650d |

校验值另见 dist/SHA256SUMS.txt；旧 1.0.2 产物已移至被忽略的 dist/previous-versions/1.0.2/。发布证书 SHA-256 仍为 400bf419f75110917b42e58f1a19195c80dbdc357989ebc0e5d4ed671763e050。

日志为 build/103-app-validation.log、build/103-build.log、build/103-device-tests.log 与 build/103-sun-capture.log。本轮截图与 UI 层级保存在 build/103-review/，城市显示的结构化核对结果见 build/103-city-cards-proof.json。直接 instrumentation 日志代表本轮设备结果，旧 connected HTML/XML 报告不作为本轮结果。

正式签名包尚未配置生产 HTTPS 网关，仍默认展示明确标注的完整示例天气；debug 包连接正在运行的电脑后端，模拟器使用 10.0.2.2 自动直连。没有连接实体手机，本轮不声称完成真机蜂窝网络验证。独立对外提供真实天气仍需要持续在线的 HTTPS 网关，见 [连接说明](HOSTING.md)。

## 1.0.2 模拟器连接与顶部下拉刷新（历史记录）

版本 1.0.2 / versionCode 3，包名与发布证书延续 1.0.1，可覆盖同签名版本并保留数据。

原问题现场已确认：电脑后端及天气接口返回正常，Android 17 本地网络权限已授权，但模拟器重启后 adb 反向转发为空，而旧版本仍向 127.0.0.1 查询，导致显示“暂时无法更新”。新版本只在 debug 且识别为模拟器时，将已验证的 HTTP 回环网关改为电脑别名 10.0.2.2；端口与编码路径保持，真机、HTTPS、release 不改写。恢复工作时另发现电脑后端已停止，已重新构建分发包并启动。

刷新改为顶部下拉。静止时指示器从界面中完全移除，不留列表首项或间距；后台自动更新不会展开它。真实下拉时显示箭头，达到阈值显示“松开刷新”，松手才请求，刷新完成后收起。

| 验证项目 | 结果 |
| --- | --- |
| 构建 | debug APK、设备测试 APK、签名 release APK、签名 AAB、R8、资源裁剪与后端分发包成功 |
| 单元测试 | 客户端 47 项、后端 13 项，共 60 项，0 失败 |
| 模拟器交互 | 6 项通过；在现有模拟器上覆盖安装并直接调用 instrumentation，未卸载或清除应用数据，测试偏好在结束后恢复 |
| 手势边界 | 静止无指示器；非顶部、短下拉及语义滚动不刷新；长下拉按住可见“松开刷新”但未发起请求，松手后快照时间更新并隐藏指示器 |
| 图像检查 | home-sunny.png 静止无提示/占位，pull-down-ready.png 按住显示提示，均已目视检查 |
| Lint | 0 errors、8 warnings，无新增全局忽略 |
| 上游 | 本机网关天气 HTTP 200，24 小时、7 日预报，partialFailures 为空 |
| 重启后直连 | 模拟器正常重启后，确认 adb reverse 中无 8080 转发；启动 1.0.2 调试版，显示实时天气，无更新失败、缓存或示例标记，静止无刷新文字 |
| 发布签名 | APK v2/v3 有效；AAB 99 个内容条目签名有效且证书指纹与 APK 一致，原发布身份保持 |

| 当前 dist/ 文件 | 字节数 | SHA-256 |
| --- | --- | --- |
| thinking-weather-1.0.2.apk | 1,646,669 | 8bd32484752819172a96978853f9dcef02488b434c1c6b5f757fd5a131193491 |
| thinking-weather-1.0.2.aab | 4,014,799 | 8ced2ac7962fc24aa4663e929e6f21ee495d6fafae6be93087e00cffffb9472a |
| thinking-weather-1.0.2-debug.apk | 13,162,986 | 09b06dee9fe7ad96f45b7a12429b4afc5fd1c7b79847e4abbca98a2a75f6d981 |

日志为 build/pull-down-network-build.log、build/pull-down-device-tests.log；截图为 build/pull-down-review/，其中 live-direct.png 为重启后直连结果，结构化结果见 build/pull-down-network-proof.json。本轮直接 instrumentation 日志是最新设备结果，旧 connected HTML/XML 报告仅代表前次运行。旧 1.0.1 产物已移至被忽略的 dist/previous-versions/1.0.1/。

正式签名包未配置生产 HTTPS 网关，仍用于明确标注的示例预览；Android Studio/debug 包连接电脑后端，模拟器自动直连、真机使用 USB 转发。独立分发真实天气仍需在线 HTTPS 网关。

## 1.0.1 网络与界面改进（历史记录）

版本为 1.0.1 / versionCode 2，正式与调试安装 ID 延续 com.thinkingweather.android 和 com.thinkingweather.android.debug，发布证书保持不变。

本次实现真机开发连接诊断、移动流量/离线提示、独立全屏城市及设置页面、页面动画、新齿轮、上拉释放刷新、30 个真实收藏名额，以及覆盖 13 类视觉天气的 24 个固定城市场景。示例不覆盖真实收藏，也不读取旧演示缓存。

| 验证内容 | 结果 |
| --- | --- |
| 构建 | debug、设备测试、签名 release APK 和 AAB、R8 与资源裁剪成功 |
| 单元测试 | Android 42 项 + 后端 13 项，共 55 项，0 失败 |
| Android 17 / API 37 设备测试 | 6 项全部通过，0 失败、0 跳过 |
| 手势 | 语义滚动与不足阈值的短上拉不刷新；到底后继续上拉松开，快照时间确实更新 |
| 页面与场景 | 搜索、全屏页面返回、单位换算、示例收藏隔离、雷雨/大雪/晴夜切换通过；截图已检查 |
| Lint | 0 errors、8 warnings；无错误基线或全局忽略 |
| 真实网关 | HTTP 200，24 小时和 7 日预报，partialFailures 为空 |
| USB 链路 | 模拟器经 127.0.0.1 转发显示实时天气；设备关闭 Wi-Fi/移动数据后，仍由电脑网关返回真实天气，无示例或缓存提示 |
| 离线提示 | 确认 Active default network=none，观察到“当前未连接网络”Toast；测试后恢复网络设置 |
| 流量提示 | 网络分类和文字“请注意，当前正在使用移动流量”由单元测试验证；本轮没有实体手机蜂窝实测 |
| 权限 | 正式 APK 有 ACCESS_NETWORK_STATE，无 ACCESS_LOCAL_NETWORK；后者仅在 debug 真实模式按需申请 |
| 签名 | APK v2/v3 有效；AAB 99 个内容条目签名有效、证书一致，原指纹不变 |

刷新除了检查 UserInput 来源，还跟踪真实按下/松开，避免 Compose 的语义滚动误置刷新状态。夜间图形使用透明月牙路径。实际 APK 元数据为随想天气 / 1.0.1 / code 2，调试版名称增加“开发版”和 -debug 后缀。

| 当前 dist/ 文件 | 字节数 | SHA-256 |
| --- | --- | --- |
| thinking-weather-1.0.1.apk | 1,630,289 | a428dda22087a3072ad1de857401e92f4597ff2cd0c5ce7e65cdfaea614eca22 |
| thinking-weather-1.0.1.aab | 4,005,633 | 77116d1e1beeb3ed53a255488fd49471e3354304678ad66ffe60ddc0589a992f |
| thinking-weather-1.0.1-debug.apk | 13,378,443 | 735d42b52217e0f0f9a9b418ee4fb03fd6062ef37bbd8293616adf81d40c4047 |

正式版未配置生产 HTTPS 网关，安装后默认展示完整示例目录；调试 APK 须保持电脑后端及 adb 转发。用户本轮选择暂不连接实体手机，因此不将模拟器通过写成真机通过。独立对外提供真实天气仍需要持续在线的 HTTPS 网关，见 [连接说明](HOSTING.md)。

日志：build/improvements-build.log、build/improvements-tests.log、build/improvements-instrumentation.log；最终设备 XML/HTML 报告：app/build/outputs/androidTest-results/connected/、app/build/reports/androidTests/connected/。截图：build/ui-review/，包括 home-sunny、home-thunderstorm、home-snow、home-night、cities、settings、live-usb 与 network-toast。旧 1.0.0 分发文件在被忽略的 dist/previous-versions/1.0.0/，更早品牌产物在 dist/previous-brand/。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:assembleSignedRelease :app:bundleSignedRelease :backend:test :backend:installDist -PWEATHER_DEBUG_BASE_URL=http://127.0.0.1:8080
.\gradlew.bat :app:connectedDebugAndroidTest -PWEATHER_DEBUG_BASE_URL=http://127.0.0.1:8080
```

下文为历史记录，不作为当前分发信息。

## ThinkingWeather／随想天气更名后的最终验证

本节记录当前交付产物。版本保持 1.0.0 / versionCode 1；Android 安装 ID 改为 com.thinkingweather.android，调试版为 com.thinkingweather.android.debug。包名改变后，系统将其视为独立应用，旧应用数据不自动迁移。

| 项目 | 结果 |
| --- | --- |
| 当前品牌与目录 | ThinkingWeather／随想天气；24 个 Kotlin 文件的 package、import 和目录已迁移；当前运行源码、配置和资源没有旧品牌残留 |
| 正式 APK 元数据 | com.thinkingweather.android / 1.0.0 / versionCode 1 / 随想天气 |
| 调试 APK 元数据 | com.thinkingweather.android.debug / 1.0.0-debug / versionCode 1 / 随想天气 · 开发版 |
| 构建 | 签名 APK、签名 AAB、debug APK、R8 与资源裁剪全部成功 |
| 单元测试 | Android 28 项、后端 13 项，合计 41 项，0 失败 |
| Lint | 0 errors、8 warnings，未添加错误基线或全局忽略 |
| 设备测试源码 | assembleDebugAndroidTest 成功，测试安装 ID 与目标 ID 均为新包名；本轮无已连接设备，未执行设备交互测试 |
| APK／AAB 签名 | APK v2、v3 有效；AAB 全部 99 个非 META-INF 内容条目签名有效，证书与 APK 一致 |
| 现有签名身份 | 别名改为 thinkingweather-release，私钥与证书保留，SHA-256 指纹仍为 400bf419f75110917b42e58f1a19195c80dbdc357989ebc0e5d4ed671763e050 |
| 后端运行 | 新主类 com.thinkingweather.gateway.MainKt 的分发包启动成功，/health 返回 status=ok、configured=true |
| Git 与文档 | diff --check 通过；实际凭据、签名及其私有备份、安装包继续被忽略；当前文档路径已同步 |

当前 dist/ 文件：

| 文件 | 字节数 | SHA-256 |
| --- | --- | --- |
| thinking-weather-1.0.0.apk | 1,668,461 | 6988b1697a26d2f17b1d8b78fbd3c74e7eb7257c7c964d9d836c8cdfbf8f555a |
| thinking-weather-1.0.0.aab | 3,999,592 | 54624e72010b2efbda0c06a6577190683c9b59101c00c07c8ed0e598323c04b4 |
| thinking-weather-1.0.0-debug.apk | 13,113,794 | 73037a4301fdfef4ab7bced0c1b202e046ab1ce18e85b6a3a32a54b06a343002 |

校验值另见 dist/SHA256SUMS.txt。更名前的分发文件已保留在被忽略的 dist/previous-brand/，不再作为当前版本分发。

日志为 build/thinkingweather-validation.log 和 build/thinkingweather-androidtest-compile.log；本轮完整构建命令与下方整理验证命令相同，设备测试 APK 另用 :app:assembleDebugAndroidTest -PWEATHER_DEBUG_BASE_URL= 构建。当前发布包仍未配置生产 HTTPS 网关，因此展示明确标注的示例天气。后端健康检查确认配置和启动可用，本轮未重复请求上游天气接口。

历史中的旧名称仅用于原作者归属、旧文件原始路径和既有证书身份，不代表当前软件显示名称。

## 更名前版本、签名与目录整理验证（历史记录）

本节记录更名前 1.0.0 / versionCode 1 的构建结果，包标识和产物名按当时实际状态保留。它们不代表 ThinkingWeather 更名后的重新验证；下面还保留前期界面重写与真实接口联调记录，其中旧预览包、旧版本码和测试签名不作为当前分发产物。

| 项目 | 结果 |
| --- | --- |
| 完整构建 | 签名 release APK、签名 AAB、debug APK、R8 与资源裁剪全部成功 |
| 实际包标识 | 正式版 com.windowweather.android / 1.0.0 / 1；调试版 com.windowweather.android.debug / 1.0.0-debug / 1 |
| 发布签名 | 独立 RSA 3072 位证书；APK v2、v3 校验通过，与调试证书不同 |
| AAB 签名 | JarFile 验证全部 99 个非 META-INF 内容条目，全部签名有效，证书与 APK 一致 |
| 单元测试 | Android 28 项、后端 13 项，合计 41 项，0 失败 |
| Lint | 0 errors、8 warnings；未添加错误基线或全局忽略 |
| 独立后端 | 不加载 Android 模块，以 -p backend 执行 test 成功；更新后的分发包启动成功，/health 返回正常且已配置 |
| 签名保护 | 重复运行生成脚本拒绝覆盖，现有密钥内容未改变；实际密钥与密码配置均被 Git 忽略 |
| Git 检查 | diff --check 通过；实际凭据、签名、缓存、构建产物、dist 与本地归档均忽略；配置模板和 Wrapper JAR 保留 |
| 清理 | 30 个旧二进制与元数据文件退出活跃工程及 Git 跟踪，可恢复归档共 58,624,325 字节，详见 [清理记录](CLEANUP.md) |

本轮完整命令：

```powershell
.\gradlew.bat :app:assembleSignedRelease :app:bundleSignedRelease :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :backend:test :backend:installDist -PWEATHER_DEBUG_BASE_URL= --console=plain
.\gradlew.bat -p backend test --console=plain
```

本轮未连接设备，未重新执行设备交互测试。当前分发包未配置生产 HTTPS 网关，因此使用明确标注的示例数据；此前真实 API 成功记录不能代替公网服务部署。

当时产物位于已被 Git 忽略的 dist/，更名时已移到 dist/previous-brand/。以下名称和大小为历史记录，不能作为当前随想天气分发文件：

- window-weather-1.0.0.apk：已签名正式版，1,668,445 字节。
- window-weather-1.0.0.aab：已签名 Bundle，3,998,221 字节。
- window-weather-1.0.0-debug.apk：独立包名调试版，13,113,768 字节。
- SHA256SUMS.txt：上述三个文件的 SHA-256 校验值。

正式证书 SHA-256 指纹为 400bf419f75110917b42e58f1a19195c80dbdc357989ebc0e5d4ed671763e050。证书指纹是公开身份信息，不是密码。密钥与密码应分别安全备份；新证书和版本重置不能覆盖旧证书签名或更高版本码的安装。

日志：build/reorganization-validation.log、build/backend-standalone-validation.log。测试报告位于 app/build/reports/ 与 backend/build/reports/；Lint 详情为 app/build/reports/lint-results-debug.html。

## 首轮重写验证（历史记录）

| 项目 | 结果 |
| --- | --- |
| 构建工具 | Android Studio JBR 25.0.3 / Gradle 9.6.0 / AGP 9.4.1 / Kotlin 2.4.21 |
| Android 编译 | debug 与 release 成功，compile/target API 37，最低 API 26 |
| 客户端 JVM 测试 | 本地网络权限修复后 28 通过，0 失败 |
| 服务端 JVM 测试 | 13 通过，0 失败 |
| Android 37 模拟器交互测试 | 3 通过，0 失败、0 跳过 |
| Lint | 0 errors，25 warnings；无错误基线或全局忽略 |
| 发布包 | R8 压缩与资源裁剪成功 |
| 预览 APK 签名 | 测试证书，v2/v3 验证通过 |
| 压缩包运行 | Android 17 / API 37 模拟器安装、冷启动成功 |
| 界面检查 | 首页、逐小时、每日预报、指标、城市管理、设置、1.6 倍字体 |
| Java 源文件 | app/src 下为 0，运行代码已迁为 Kotlin |

客户端测试覆盖服务端协议、响应解析、实际单位、演示/真实数据隔离、失败与取消、温度换算、缓存 JSON 往返。服务端测试覆盖 JWT 签名与声明、参数校验、缓存合并/失败冷却/过期、限流、上游响应边界、接口聚合、预警取消和过期字段。模拟器测试覆盖示例标签与预报、搜索添加深圳、摄氏/华氏切换。

初次网络测试修正了 Windows 回环主机名的测试夹具；生产地址白名单未放宽。初次模拟器测试发现 Compose 间接引入的 Espresso 3.5 调用 Android 17 已移除的隐藏 API，显式使用 Espresso 3.7.0 后三项通过。详见 [AndroidX 官方修复记录](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)。

Lint 的其余提示主要是未使用的历史图片资源、可升级的依赖和测试 API 迁移提示。历史图片在压缩发布包中被裁剪，不影响运行代码。Gradle 固定为 AGP 9.4 所要求的稳定兼容版本 9.6.0，并校验官方发行包 SHA-256。

## 首轮交付位置（历史产物）

- 可安装、使用内置示例的预览包：build/review/window-weather-preview.apk，约 1.6 MiB。
- 正式签名前的压缩包：app/build/outputs/apk/release/app-release-unsigned.apk。
- 调试包：app/build/outputs/apk/debug/app-debug.apk。
- 构建与验证日志：build/validation.log。
- 模拟器测试日志：build/instrumentation.log。
- JVM 与模拟器报告：app/build/reports/、backend/build/reports/。
- 截图：build/review/home.png、forecast.png、metrics.png、cities.png、settings.png、large-font.png。

预览包使用 Android 的默认调试证书为压缩版签名，仅供体验，不是生产签名。若设备已安装签名不同的旧版，不能直接覆盖安装；正式升级需要原签名证书。预览包没有生产网关地址，页面始终明确显示示例数据。

预览 APK SHA-256：

`d0e77a3b99d3778fc5a2c6d88b4bfdc60c80bab470675181d497d7400e6678c0`

## 更名前真实接口验证（历史记录）

用户调整和风控制台限制后，于 2026-10-28 15:01（北京时间）重新从本机 backend/.env.local 加载 API Key，城市检索与天气聚合均返回 HTTP 200。北京与上海的真实查询均通过：24 条小时预报、7 条每日预报、空气质量可用，partialFailures 为空。此次北京查询返回 15.5°C、多云，App 按整数显示 16°C。

此前的 HTTP 403 Security Restriction 已解除，不再等待控制台配置生效；原定 16:40 的复查任务已删除。凭据、Host 及完整上游请求没有写入此记录。

实际模拟器联调另发现 Android 17 的局域网权限要求：targetSdk 37 的 App 未获 ACCESS_LOCAL_NETWORK 权限时，访问模拟器电脑别名 10.0.2.2 会超时。已仅在 debug 清单中声明并动态申请该权限，在每次请求前检查授权，拒绝时明确报错。允许后，模拟器经本地网关成功显示真实实时天气、24 小时预报和 7 日预报，首页没有示例标记。正式版清单不包含此权限。

本机联调包：build/review/window-weather-local-debug.apk，连接电脑上的 10.0.2.2:8080。首次使用需允许附近设备／本地网络权限，并保持电脑上的后端运行。截图：build/review/live-home.png。这个地址仅用于模拟器联调，不是已部署的公网服务。

同时将 UV 卡片明确标为“今日最高紫外线”，对应每日预报中的 uvIndexMax，避免误解为此刻的实时紫外线。

本轮 debug/release 构建、28 项客户端 JVM 测试、Lint 全部通过，记录在 build/live-build.log。通过 aapt 检查了实际 release APK，其清单不含 ACCESS_LOCAL_NETWORK。此前 3 项自动交互测试已通过；本轮额外复测时模拟器已断开，故 connectedDebugAndroidTest 未执行成功（No connected devices），不将其记为新一轮通过。模拟器断开前已完成真实天气的手动授权、网络请求和界面截图验证。

官方：[Security Restriction 错误说明](https://dev.qweather.com/docs/resource/error-code/#security-restriction)。

## 仍需正式上线验证

真实和风账户请求已成功，当前已完成独立生产签名。当前没有部署云服务、创建域名或创建收费资源。托管容器文件已提供，但 Docker 镜像构建与云部署尚未执行。公开发布前还需持续在线的 HTTPS 网关、实际设备及 Android 8 等较旧系统验证，并补齐运营者信息与入口授权/预算策略。旧版 LitePal 收藏不自动迁移。
