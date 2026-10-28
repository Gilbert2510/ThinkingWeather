# 本地发布签名

ThinkingWeather（随想天气）使用 `thinkingweather-release` 作为发布别名。本次更名保留已有私钥和证书，指纹不变；证书中的旧主体名称只记录原始签名身份，与当前软件显示名无关。更名前的私有备份位于本机 `signing/backups/before-thinkingweather/`，同样不提交。

本目录只提交本说明和 keystore.properties.example。

运行 scripts/new-release-keystore.ps1 会在本机生成 release-keystore.p12 和 keystore.properties，已有任一文件时拒绝覆盖。实际密钥和密码均被 Git 与 Docker 构建上下文排除。不要将它们上传 GitHub、粘贴到聊天、放进 APK 或与普通源码压缩包一起分发。

请把密钥和密码分别备份至安全的离线位置。保留同一发布证书用于后续升级。查看构建、版本策略及 CI 环境变量配置请阅读 [发布说明](../docs/RELEASING.md)。
