## 中文

首个 Android + Windows 联合开源版本。

- Android 8.0+：安装 `FreeHDDownload-1.2.1-android.apk`，沿用原应用签名和包名。
- Windows 10/11 x64：完整解压 ZIP，运行 `FreeHDDownload.exe`，无需安装 Java；不要单独移动 EXE。
- 支持抖音、小红书、B站公开视频，以及平台提供的图片和文字；可选择实际可用画质。
- 文章正文可复制及保存 UTF-8 TXT；Android 媒体保存到相册，Windows 可选择目录。
- Windows 真实验证了抖音视频/图片下载和 B站音视频本地合并，成片完整解码通过。
- 桌面 UI 自动解析与清空结果检查通过。Windows 原生 EXE 启动在维护环境被系统取消，未完成 EXE 入口实测；内置运行环境的 Java 入口已验证可启动。Android 仍未完成真机保存验证。

普通链接在设备本地处理，不需要部署服务器；无网址的抖音口令依赖第三方公共转换服务。画质由平台返回决定，嵌入画面的标识不会被移除。不支持付费、DRM 或私密限制绕过。

Windows 包未做商业代码签名，可能触发 SmartScreen。请核对来源和 `SHA256SUMS.txt`；不需要关闭系统防护。

源码：Apache-2.0。Android 私钥、个人设置、测试下载和构建缓存不包含在发行源码中。

## English

First combined Android + Windows open-source release.

- Android 8.0+: install the APK. The original app package and signing key are preserved.
- Windows 10/11 x64: extract the complete archive and run `FreeHDDownload.exe`. Java is bundled; keep the adjacent directories.
- Save supported public Douyin, Xiaohongshu and Bilibili media, photo posts and text. Choose available source quality; export article text as UTF-8 TXT.
- Windows real-link tests cover Douyin video/images and Bilibili local audio/video muxing with successful full decoding.
- Desktop automatic parsing and clear/reset checks passed. Native EXE launch was canceled by Windows in the maintainer environment; the bundled-runtime Java entry was checked. Android on-device saving remains unverified.

Ordinary links run locally without a project backend. URL-less Douyin commands use a third-party public conversion service. Embedded marks remain; resolution is source-dependent. No paid, DRM or private-access bypass.

Windows builds are unsigned and may trigger SmartScreen. Verify the source and checksums; disabling protection is not required. Apache-2.0 source excludes private keys, settings, downloaded test media and build caches.
