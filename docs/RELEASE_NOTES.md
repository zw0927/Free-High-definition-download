## 中文

### v1.2.4

- Windows ZIP 增加当前用户安装脚本。电脑拒绝从解压目录运行的程序写入桌面时，可安装到本地应用目录并通过桌面快捷方式启动。
- Windows 补丁：使用资源管理器打开下载目录。下载前检查目标目录能否写入，并提示如何处理目录错误。
- 修复小红书原生 `live_photo.media.stream` 数据结构未识别：现在支持 camelCase 与 snake_case 字段及主/备用视频地址。
- 小红书和抖音实况均保存静态照片及同序号动态 MP4；无动态源时保留静态照片并提示。
- 检测 MP4 音轨轨道；源视频含音轨则保留，源本身无音轨时保存静音 MP4并提示。Android 按 MP4 音频轨是否存在判断，不依赖首帧音频时间戳。
- 支持并原样保存 JPEG、PNG、GIF、WebP、HEIC、HEIF、AVIF 图片，修复 HEIC/AVIF 被拒绝保存的问题。
- Windows 完整回归保存了抖音图集的 4 张静态图与 3 段实况 MP4；三个源均含音轨。另从实况样本生成 video-only MP4，验证无音轨识别与提示计数。小红书实况的 native snake_case 响应使用模拟数据验证。
- 已检查一条小红书公开笔记的实况源，其网页端 MP4 无音轨。Android 真机相册播放仍待验证。
- 图片格式识别单元测试通过，Android APK 在 aapt2 无法启动时由同一 SDK 的 legacy AAPT 完成资源打包；APK v2/v3 签名与旧版相同。
- 回归测试和安装包验证记录见 `VALIDATION.md`。
- 从发行附件下载 APK、Windows 便携 ZIP 或源码 ZIP。校验值见 `SHA256SUMS.txt`。

## English

### v1.2.4

- The Windows ZIP includes a per-user installer for PCs that restrict writes from the extracted location.
- Windows patch: Open the download folder in File Explorer. Check the destination before downloading and provide a recovery message for directory errors.
- Parse Xiaohongshu native `live_photo.media.stream` as well as camelCase responses, including primary and backup motion URLs.
- Save the still photo and matching motion MP4 for Xiaohongshu and Douyin Live Photos. Preserve the still and report when motion is unavailable.
- Inspect MP4 audio tracks. Preserve source audio; save silent clips and report when the source contains no audio track.
- Detect and preserve JPEG, PNG, GIF, WebP, HEIC, HEIF and AVIF image formats, fixing unsupported HEIC/AVIF saves.
- The Douyin gallery regression saved four still images and three motion MP4s; all three sources contained audio. A derived video-only MP4 verified silent-source detection and notice counting. Xiaohongshu native snake_case data uses fixtures.
- A public Xiaohongshu post was inspected, and its web motion MP4 contained no audio track.
- Android on-device gallery playback remains unverified.
- See `VALIDATION.md` for test and package verification details.
- Download the APK, portable Windows ZIP, or source ZIP from the release assets. Checksums are in `SHA256SUMS.txt`.

---

## 中文

### v1.2.3

- 修复抖音和小红书实况图只保存静态封面的问题。图集中的实况视频保存为 MP4，静态图片保留原顺序。
- 支持原生 GIF，动态 WebP 保留动画。跳过标有水印的实况地址，缺少可用动态源时显示提示。
- 下载 Android 8.0+ 的 `FreeHDDownload-1.2.3-android.apk` 可覆盖升级。Windows 10/11 x64 请完整解压 `FreeHDDownload-1.2.3-windows-x64.zip` 后运行 `FreeHDDownload.exe`。
- 抖音公开样例的 3 段实况 MP4 已通过完整解码。小红书实况使用模拟数据验证，安卓真机相册尚待验证。发行包校验值见 `SHA256SUMS.txt`。

## English

### v1.2.3

- Save Live Photo motion as MP4 in mixed Douyin and Xiaohongshu galleries. Preserve the order of still images.
- Save native GIF and animated WebP without re-encoding. Skip URLs explicitly flagged as watermarked and report unavailable motion sources.
- Install the signed Android APK to upgrade in place. On Windows 10/11 x64, extract the full ZIP and run `FreeHDDownload.exe`.
- Three motion clips from a public Douyin post passed full decoding. Xiaohongshu motion was covered by fixtures. Android gallery playback remains unverified on a physical device. See `SHA256SUMS.txt` for release checksums.

---

## 中文（历史版本）

### v1.2.2

- 修复小红书图片中央平台水印：使用原图CDN，原图失败时不退回H5展示图。用户样例5张图片均实测下载为1440×2400。
- 新增B站登录：Windows用哔哩哔哩手机App扫码，Android使用官方登录页；登录状态保留在本机，可退出。
- 登录后重新解析可获取账号可用画质；匿名访问可能只返回360P/480P，1080P及更高档位以平台响应和账号权限为准。
- 保留B站H.265高清轨，相同分辨率优先H.264；音视频直接合并，不放大或重新压缩。
- 保留抖音、小红书、B站视频、图片、文案和TXT保存功能。

### 下载

- Android 8.0+：安装 `FreeHDDownload-1.2.2-android.apk`，沿用原应用签名和包名，可以覆盖升级。
- Windows 10/11 x64：完整解压 `FreeHDDownload-1.2.2-windows-x64.zip`，运行 `FreeHDDownload.exe`。保留旁边app与runtime文件夹，无需安装Java。
- 源码：`FreeHDDownload-1.2.2-source.zip`，Apache-2.0；校验和见 `SHA256SUMS.txt`。

### 验证

Android编译、对齐、v2/v3签名校验及旧版证书一致性检查通过。核心回归、小红书真实图集保存、B站二维码生成/轮询/解码、Windows自动解析与清空检查通过。

尚未完成安卓真机安装与保存、真实账号登录后1080P下载以及新增H.265轨真实合并的端到端验证。Windows发行包未做商业代码签名。详细记录见仓库VALIDATION.md。

普通链接在设备本地处理，无需部署项目服务器。源码和发行包不包含个人设置、签名私钥或测试下载。

## English

### v1.2.2

- Fix Xiaohongshu image downloads by selecting original CDN sources instead of watermarked H5 display images. All five images in the reported post downloaded at 1440×2400.
- Add Bilibili login: scan with the Bilibili mobile app on Windows, or use the official login page on Android. Sessions remain on-device and can be removed through sign-out.
- Parse again after login for account-accessible quality. Anonymous requests may return only 360p/480p; higher resolutions depend on the source and account permissions.
- Keep available Bilibili HEVC tracks; prefer H.264 at equal resolution. Mux directly without upscaling or re-encoding.
- Retain supported video, image, caption and TXT saving across Douyin, Xiaohongshu and Bilibili.

### Downloads

- Android 8.0+: `FreeHDDownload-1.2.2-android.apk`, with the original package and signing certificate for in-place upgrades.
- Windows 10/11 x64: extract the complete `FreeHDDownload-1.2.2-windows-x64.zip` and run `FreeHDDownload.exe`. Keep app/runtime directories beside it. Java is included.
- Source: `FreeHDDownload-1.2.2-source.zip`, Apache-2.0. Checksums: `SHA256SUMS.txt`.

### Validation

Android compilation, alignment, v2/v3 signatures and certificate continuity passed. Core regression, real Xiaohongshu image saving, Bilibili QR generation/polling/decoding and Windows automatic parse/reset checks passed.

Android on-device installation/saving, authenticated 1080p downloads and real HEVC muxing remain unverified end to end. Windows binaries do not have a commercial code-signing certificate. See VALIDATION.md for details.

Ordinary links run locally without a project backend. Source and distributions exclude private keys, personal settings and downloaded test media.
