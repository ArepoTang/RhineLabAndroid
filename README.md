# Rhine Lab · 莱茵生命交互终端（Android）

把 [RhineLabWallpaper](https://github.com/LBEILC/RhineLabWallpaper) 的壁纸构建装进一个 Android 应用。

## 这是什么

一个**最小原生外壳**（Java + WebView），不是重写。全部界面、3D、工作台、档案都还是原项目那份 Web 构建，
放进 APK 的 assets 里由 `WebViewAssetLoader` 以 https origin 就地提供。原生侧只做四件 Web 层做不了的事：

| 需求 | 实现 |
|---|---|
| 非 `file://` origin | `WebViewAssetLoader` → `https://appassets.androidplatform.net/assets/www/` |
| 沉浸式全屏 + 挖孔 | `setDecorFitsSystemWindows(false)` + `WindowInsetsController` + 挖孔 `ALWAYS` |
| 返回手势 | 先问页面（查看器 → 弹窗 → 详情），页面无事可做才退出应用 |
| 软键盘不跳版 | 只把 IME inset 作为底边距给 WebView |
| 切后台停渲染 | `WebView.onPause()` + 页面自身的 `document.hidden` 分支 |
| 冷启动不白闪 | 窗口与 WebView 底色 = 应用纸色 `#E8E5E1` |

**没有 `INTERNET` 权限** —— 壁纸从不联网；外部链接（档案里的设定参考）交给系统浏览器。

**没有音频律动、没有系统媒体信息**（v1 范围）。它们对应 Web 层已有的
`window.wallpaperRegisterAudioListener` 与 `wallpaperRegisterMedia*Listener`，将来在原生侧实现同样的签名即可，
Web 层不用改。

## 构建

**在 GitHub Actions 上构建**（本机不需要 Android SDK/NDK）。
推送到 `main` 或手动 `workflow_dispatch` 即触发：

1. checkout 本仓
2. checkout `LBEILC/RhineLabWallpaper@main`
3. `npm ci && npm run build:wallpaper`
4. `scripts/prepare-web.sh` 把 `release/wallpaper` 复制进 `app/src/main/assets/www`
5. `./gradlew assembleRelease`
6. 上传 `app-release.apk` 作为 workflow artifact

本机若要构建，先自备 Android SDK，然后：

```sh
./scripts/prepare-web.sh /path/to/RhineLabWallpaper-src/release/wallpaper
./gradlew assembleRelease
```

## 版本

| 项 | 值 |
|---|---|
| Gradle | 9.7.1（AGP 9.4.1 的最低要求是 9.6.0） |
| AGP | 9.4.1 |
| compileSdk / targetSdk | 36 |
| minSdk | 30 |
| Java | 17（源码级） |
| 依赖 | 只有一个：`androidx.webkit` |

## 包名与签名

- 包名：`com.rhine.lab`（2026-10-11 由 `cc.lubeiluchen.rhinelab` 改来；旧包名不再维护，
  两者在系统里是**两个不同的应用**，不会互相覆盖，数据也不共享）。
- 签名材料**不进仓库**：`.p12` 以 base64 存进仓库 Secrets，CI 构建前解码到临时目录再喂给 Gradle。
  本地没有这些环境变量时只出未签名包（避免拿错钥匙签出装不上的东西）。

Secrets（Actions → Secrets and variables → Actions）：

| Secret | 内容 |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 key.p12` |
| `SIGNING_STORE_PASSWORD` | keystore 口令 |
| `SIGNING_KEY_ALIAS` | 别名（当前是 `exported_key`） |
| `SIGNING_KEY_PASSWORD` | key 口令（PKCS12 通常与上面相同） |

换钥匙等于换应用身份：已装用户无法覆盖安装，必须卸载。

## 已知未验证项

- 真机上 `env(safe-area-inset-*)` 是否被 WebView 正确映射到挖孔/手势区（若不对，需要用 WE 属性
  `uimargintop/bottom/left/right` 从原生侧补进去）
- 华为 `com.huawei.webview` 的 WebGL2 行为（本项目所有性能数字都是在 Edge 上测的）
- 首版没有音频律动与媒体信息
