# 本轮修改文件

## 1.2.4 修复实况配对、图片格式及无声提示

- 修改 `VideoResolver.java`、`VideoInfo.java`：解析小红书 camelCase 与 native snake_case 实况结构；每个实况同时保存静态照片和动态 MP4，并按原图集序号配对。
- 新增 `ImageMedia.java`：按魔数识别 JPEG、PNG、GIF、WebP、HEIC、HEIF 和 AVIF，保留源格式。
- 修改 `GalleryItem.java`、`DownloadService.java`：识别/登记原 MIME 与扩展名；分析实况音视频轨道，保存静音 MP4 并显示提示。
- 修改 `DesktopDownload.java`、`DesktopApp.java`：Windows 静态+动态成对下载、实况音轨检查和图片格式识别。
- 修改 `tests/CoreTest.java`、`tests/run.ps1`：添加图片格式、两种平台 mixed gallery、snake_case、序号和水印源过滤回归。
- 修改 Android/Windows 构建版本和 GitHub 发布工具。发布默认保留草稿，使用 `-MakePublic` 公开。
- 修改 `README.md`、`VALIDATION.md`、`docs/RELEASE_NOTES.md`、Windows README。

## 1.2.4 修复实况配对、图片格式及无声提示

- 修改 `VideoResolver.java`、`VideoInfo.java`：小红书、抖音实况条目同时加入静态照片和动态视频，按原作品序号成对保存。
- 新增 `ImageMedia.java`：按文件签名识别 JPEG、PNG、GIF、WebP、HEIC、HEIF、AVIF，保留原始编码。
- 修改 `GalleryItem.java`、`DownloadService.java`：保留新图片 MIME/扩展名，绕过设备位图解码器不支持时的静态图拒绝；检查实况视频轨道并提示无音轨源。
- 修改 `DesktopDownload.java`、`DesktopApp.java`：Windows 原格式图片识别、实况视频轨检查及静音完成提示。
- 修改 `tests/CoreTest.java`、`tests/run.ps1`：补充图片签名、静态/动态配对、原顺序、过滤水印源和缺失动态源回归。
- 修改构建、版本和中英说明文件；Android versionCode 7、versionName 1.2.4。
- Windows 构建可指定系统 JDK 编译并继续打包既有 Temurin runtime；Android aapt2 初始化失败时回退到同一 SDK 的 legacy AAPT。GitHub 发布脚本默认只上传草稿，需显式使用 MakePublic 才会公开。
- v1.2.4 提供 Android APK、Windows 便携 ZIP、源码 ZIP 和 SHA256 校验清单。

## 1.2.3 抖音和小红书实况图

- 解析与保存：`VideoInfo.java`、`VideoResolver.java`、`MainActivity.java`、`DownloadService.java`、`GalleryItem.java`、`DesktopApp.java`、`DesktopDownload.java`。
- 测试：`tests/CoreTest.java`、`tests/ResolveProbe.java`。
- 构建与发布：`AndroidManifest.xml`、`build.ps1`、`desktop/build.ps1`、`tools/package-release.ps1`、`tools/github-release.ps1`。
- 文档：`README.md`、`desktop/README.txt`、`docs/RELEASE_NOTES.md`、`VALIDATION.md` 和本文件。

## 1.2.2 小红书原图与B站登录画质

- 修改 `src/cn/dousave/app/VideoResolver.java`、`VideoInfo.java`：小红书图片与封面使用原图 CDN，禁止回退到 H5 展示图；B站请求使用可选登录凭据，并显示画质状态。
- 新增 `src/cn/dousave/app/BiliLogin.java`、`BiliLoginActivity.java`：官方扫码接口、会话校验与安卓登录页。
- 修改 `src/cn/dousave/app/HttpClient.java`：显式Cookie不随跳转发送到其他主机；B站保留H.265高清轨，相同分辨率优先H.264。
- 修改 `src/cn/dousave/app/MainActivity.java`：B站登录、退出、登录后刷新解析。
- 新增 `desktop/src/cn/dousave/desktop/BiliLoginDialog.java`，修改 `DesktopApp.java`：官方二维码、本地登录状态与退出。
- 修改 `AndroidManifest.xml`、`build.ps1`、`desktop/build.ps1`、`desktop/README.txt`：版本1.2.2、登录页和 ZXing 依赖。
- 修改 `tests/CoreTest.java`、`tests/run.ps1`、`desktop/tests/run.ps1`；新增 `desktop/tests/BiliQrTest.java`、`XhsOriginalTest.java`：原图地址、防展示图回退、Cookie过滤、二维码解码及真实图集保存验证。
- 修改 `README.md`、`VALIDATION.md`、`NOTICE`、`assets/THIRD_PARTY_NOTICES.txt`：用法、验证范围与新增组件说明。
- Windows 1.2.2 EXE便携包与Android签名APK已生成，Android包名和签名证书与1.2.1一致，可覆盖升级。
- 修改 `tools/github-release.ps1`、`tools/package-release.ps1`、`docs/RELEASE_NOTES.md`：1.2.2源码归档、校验和、明确提交版本的GitHub发布与中英更新说明。

## Android + Windows 联合开源发布

- 新增 `desktop/src/cn/dousave/desktop/DesktopApp.java`：Windows UI、自动解析、目录选择、文字保存、下载取消、持久记录。
- 新增 `desktop/src/cn/dousave/desktop/DesktopDownload.java`：本地媒体保存、备用源、真实格式检查、安全文件名及 MP4 合并。
- 新增 `desktop/setup.ps1`、`desktop/build.ps1`、`desktop/README.txt`：Temurin校验下载、EXE便携包和使用说明。
- 新增 `desktop/tests/DesktopTest.java`、`DesktopUiTest.java`、`run.ps1`：真实下载、合流、取消及UI自动解析/清空检查。
- 重写 `README.md` 为中英双语；原Android说明保留为 `docs/ANDROID_HISTORY.md`。
- 新增 `LICENSE`、`NOTICE`、`assets/licenses/AspectJ.txt`、`JSON-java.txt`、`SLF4J.txt`，更新 `assets/THIRD_PARTY_NOTICES.txt`。
- 修改 `.gitignore`：排除密钥、发行二进制、临时和个人数据。
- 修改 `build.ps1`：已有私钥口令从环境变量读取，不在开源脚本保留原升级密钥口令。
- 新增 `docs/windows.png`、`docs/RELEASE_NOTES.md`、`tools/github-release.ps1`，更新 `VALIDATION.md`、`CHANGES.md`。
- 新增 `tools/package-release.ps1`、`promotion/build-gallery.ps1`：源码发行归档、校验和以及联网参考图库。
- 新增 `promotion/PRODUCTION.md`：使用skill及待确认的风格、内容节点；尚未制作样片或成片。
- 发行物：Android APK、Windows x64 ZIP、源码ZIP与SHA256清单，由 GitHub Release 分发。

## 1.2.1

- 修改 `src/cn/dousave/app/MainActivity.java`：文字按钮随解析结果隐藏和显示；两个清空入口移除过期下载状态；未解析内容不导出；文章状态显示。
- 修改 `src/cn/dousave/app/ShareLink.java`：抖音文章口令、完整 URL 优先与 article 路径识别。
- 修改 `src/cn/dousave/app/VideoResolver.java`：口令转换、服务响应校验、文章及图文分享页数据识别。
- 修改 `src/cn/dousave/app/VideoInfo.java`：完整文章正文、文章优先级、配图及原图备用源，拒绝仅摘要响应。
- 修改 `src/cn/dousave/app/NetworkPolicy.java`：限定口令转换 HTTPS 主机与接口路径。
- 修改 `tests/CoreTest.java`：口令及文章正文、配图、错误响应回归检查。
- 修改 `tests/ResolveProbe.java`：UTF-8 标准输入与真实文章正文探测。
- 修改 `AndroidManifest.xml`、`build.ps1`：1.2.1，versionCode=4，保留原签名及包名。
- 修改 `assets/THIRD_PARTY_NOTICES.txt`：参考项目及公共口令服务说明。
- 修改 `README.md`、`VALIDATION.md`、`CHANGES.md`：更新行为、外部服务依赖、实测结果与限制。
- 新增 `release/无水印下载-1.2.1.apk`。

本轮清理项目中间文件的命令仍被执行环境策略拒绝，`build` 及 `.tools` 中的测试媒体、调研文件和测试类仍保留；这些文件未打包进入 APK。

## 1.2.0

- 新增 `src/cn/dousave/app/GalleryItem.java`：直接写公共相册待完成文件，发布、失败清理、残留恢复和图片格式校验。
- 修改 `src/cn/dousave/app/MainActivity.java`：粘贴/清空边框、清空记录、文案显示复制和 TXT 导出、图集/封面保存、图片记录打开。
- 修改 `src/cn/dousave/app/ShareLink.java`：xhslink.cn 与抖音 note 链接。
- 修改 `src/cn/dousave/app/HttpClient.java`：分享跳转中的 HTTP 升级至 HTTPS。
- 修改 `src/cn/dousave/app/VideoResolver.java`：小红书手机页、登录重定向还原、图集、文案及封面。
- 修改 `src/cn/dousave/app/VideoInfo.java`：文案、图片列表、抖音图集。
- 修改 `src/cn/dousave/app/DownloadService.java`：移除缓存媒体写入；公共相册直接下载、图集保存、轨道合流及恢复清理。
- 修改 `src/cn/dousave/app/MediaMux.java`：使用相册文件描述符合并音视频。
- 修改 `tests/CoreTest.java`、`tests/ResolveProbe.java`：新增图文和手机分享回归检查与图片下载探测。
- 修改 `AndroidManifest.xml`、`build.ps1`：版本1.2.0，保留包名与签名。
- 修改 `README.md`、`VALIDATION.md`、`CHANGES.md`：新功能、真实用户链接验证和水印边界。
- 新增 `release/无水印下载-1.2.0.apk`。

本轮尝试删除 `.tools`、`build` 临时目录，仍被执行环境策略拒绝，未完成清理；已停止本轮 ADB 服务。

## 1.1.0 历史清单

新增：

- `src/cn/dousave/app/VideoResolver.java`：三平台后台解析。
- `src/cn/dousave/app/HttpClient.java`：HTTP 请求、Cookie 及跳转。
- `src/cn/dousave/app/NetworkPolicy.java`：平台域名、HTTPS 及本地地址检查。
- `src/cn/dousave/app/DouyinSigner.java`：抖音签名。
- `src/cn/dousave/app/MediaMux.java`：B站音视频轨本地合并。
- `tests/ResolveProbe.java`：真实网络探测、可选下载。
- `assets/THIRD_PARTY_NOTICES.txt`：参考来源与移植说明。
- `assets/licenses/Apache-2.0.txt`：签名移植代码的上游许可证。
- `CHANGES.md`：本清单。
- `release/无水印下载-1.1.0.apk`：新版安装包。

修改：

- `AndroidManifest.xml`：名称和版本升级，保留包名。
- `build.ps1`：新版安装包名称。
- `src/cn/dousave/app/MainActivity.java`：删除 WebView，接入自动解析、超时和三平台下载参数。
- `src/cn/dousave/app/DownloadService.java`：平台 Referer、音轨下载、备用源、轨道校验和合流。
- `src/cn/dousave/app/ShareLink.java`：识别抖音、小红书、B站。
- `src/cn/dousave/app/VideoInfo.java`：平台、原片标签与独立音轨。
- `tests/CoreTest.java`：多平台回归检查。
- `tests/run.ps1`：编译多平台核心及测试入口。
- `README.md`：新版用法、实现及限制。
- `VALIDATION.md`：本轮真实测试及未验证范围。

删除：

- `assets/bridge.js`：不再使用的旧 WebView 注入脚本。

临时 `.tools` 与 `build` 目录的清理被环境命令策略拒绝，仍保留。原密钥和旧版 APK 保留。
