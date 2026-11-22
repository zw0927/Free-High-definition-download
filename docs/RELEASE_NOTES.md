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
