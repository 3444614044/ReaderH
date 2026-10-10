# ReaderH

[![Android CI](https://github.com/3444614044/ReaderH/actions/workflows/android.yml/badge.svg)](https://github.com/3444614044/ReaderH/actions/workflows/android.yml)

把 Chrome 扩展 **Circle 阅读助手**（https://circlereader.com/ ）的核心能力搬进 Android：
**内置浏览器抓正文 → 原生排版引擎按屏分页 → 番茄小说式仿真翻页 → 书架留存进度**。
数据全部保存在手机本地，不依赖任何服务器。

---

## 一、功能对照

| Circle 扩展能力 | 本 App 实现 |
|---|---|
| 智能识别网页正文、去广告去噪音 | 内置 WebView + 内置 `readability.js` 抽取引擎，页面加载完成后自动检测，可读则浮出「阅读模式」按钮 |
| 自研排版引擎、舒适排版 | `ReaderEngine` + `Paginator`：HTML → Spanned → `StaticLayout` 按行量高分页，绝不出现半行截断 |
| 所见即所得设置面板 | 阅读页底部面板实时预览：字号 / 行距 / 段距 / 背景 / 亮度 / 翻页方式 |
| 五款炫彩主题 | 内置 7 套（纸白 / 纯白 / 米黄 / 护眼绿 / 青蓝 / 暗夜 / 纯黑），一键切换 |
| 大屏分栏不浪费空间 | 横屏 1 / 2 / 3 栏切换，页宽 = 屏宽 ÷ 栏数 |
| 滚动到底自动加载下一页 | 读到最后一页自动用隐藏 WebView 抓取「下一章」URL 正文，增量分页无缝续接 |
| 数据只存本地 | SQLite（`articles` 表）+ `SharedPreferences`，无账号、无网络上报 |
| 阅读进度云同步（无） | 退出阅读页自动入库并保存页码，书架点开即恢复上次位置，无需手动保存 |
| 多浏览器支持 | Android 端统一由内置 WebView 承载，可切换桌面版 UA |
| 多搜索引擎 | 内置 8 个搜索引擎，地址栏左侧按钮一键切换，设置页可设默认 |

---

## 二、依赖源（腾讯软件源镜像）

所有仓库地址已改为**腾讯软件源**，无需访问 Google / Maven Central：

| 用途 | 地址 |
|---|---|
| Gradle 插件（AGP / Kotlin 插件） | `https://mirrors.tencent.com/nexus/repository/gradle-plugins/` |
| Google Maven（androidx / material） | `https://mirrors.tencent.com/nexus/repository/google/` |
| Maven 公共代理 | `https://mirrors.tencent.com/nexus/repository/maven-public/` |
| Gradle 发行包 | `https://mirrors.tencent.com/gradle/gradle-8.9-bin.zip` |

配置位置：`settings.gradle.kts`、`gradle/wrapper/gradle-wrapper.properties`。
若你的网络可直连官方仓库，把 `settings.gradle.kts` 里的三组 url 换回 `google()` / `mavenCentral()` / `gradlePluginPortal()` 即可。

---

## 三、怎么用

1. Android Studio（Giraffe 以上 / AGP 8.x）打开本目录 `ReaderH`，等待 Gradle 同步完成。
2. 连接手机（Android 8.0+ / minSdk 26）或启动模拟器，点 **Run ▶**。
3. 首页地址栏输入网址（非网址自动跳百度搜索）→ 页面加载完右下角浮出 📖 → 点击进入阅读模式。
4. 阅读页：
   - 点屏幕**左侧**上一页，**右侧**下一页；翻页方式可在底部「翻页」里切换仿真 / 覆盖 / 滑动 / 无。
   - 点屏幕**正中**隐藏或显示菜单栏。
   - 底部：目录、翻页、字号、背景、亮度、设置。
   - 右上角 ⋮：存入书架 / 分享 / 用浏览器打开 / 设置。
5. 搜索：地址栏输入任意关键词，按当前搜索引擎打开；点地址栏左侧的引擎徽标（百 / B / 搜 / 360 / G / D / 夸 / 头）可随时切换，设置页「默认搜索引擎」可设长期默认值。

6. 书架：保存过的文章列表，显示站点、时间、读到第几页，点击进入会**自动恢复到上次页码**。

---

## 四、目录结构

包名 `com.readerh.app`，应用名 **ReaderH**。

```
ReaderH/
├─ app/src/main/java/com/readerh/app/
│  ├─ App.kt                      应用入口，初始化 Prefs / ArticleStore / ImageLoader
│  ├─ Prefs.kt                    SharedPreferences 读写（字号/行距/主题/亮度/翻页…）
│  ├─ model/Article.kt            文章数据模型
│  ├─ data/ArticleStore.kt        手写 SQLite 书架（增删改查 + 阅读进度）
│  ├─ browser/MainActivity.kt     内置浏览器：地址栏、进度条、前进后退、正文检测
│  ├─ reader/
│  │  ├─ ReaderTheme.kt           7 套阅读主题
│  │  ├─ PageTurnView.kt          自定义 View：仿真卷曲 / 覆盖 / 滑动 / 无 四种翻页
│  │  ├─ Paginator.kt             StaticLayout 逐行量高分页
│  │  ├─ ReaderModel.kt           HTML 清洗、Spanned 构建、图片下载与插入
│  │  └─ ReaderActivity.kt        阅读页：分页、目录、面板、自动下一章、进度保存
│  ├─ library/LibraryActivity.kt  书架
│  ├─ settings/SettingsActivity.kt 设置
│  └─ util/                       Extensions / ImageLoader / Readability
├─ app/src/main/assets/readability.js  正文抽取脚本
├─ app/src/main/res/                   布局、图标、主题、字符串
├─ gradle/wrapper/gradle-wrapper.properties  Gradle 发行包走腾讯镜像
└─ preview/reader_preview.html    手机浏览器可打开的交互原型预览
```

---

## 五、关键实现说明

- **翻页手势**：轻点屏幕**左 35%** 上一页、**右 35%** 下一页、**中间 30%** 显示/隐藏菜单栏；也可从任意位置**横拖**翻页——起点在屏幕对应侧边缘、高度跟随手指，不必非要从底部角落起手。仿真模式下折痕跟随手指纵向位置形成斜向卷曲，拖过屏宽的 22% 即提交翻页，否则回弹。
- **分页**：`ReaderEngine.paginate()` 用 `StaticLayout` 按实际行高量测，逐行累积到达页高即切页；字号、行距、段距、分栏、横竖屏变化都会触发 `relayout()` 后台重新分页，主线程回灌页面列表，不卡 UI。
- **仿真翻页**：`PageTurnView.onDraw()` 用折痕垂直平分线 + 矩形求交 + 反射矩阵绘制被掀起页的背面内容，折痕处叠加渐变阴影；右下角起翻为下一页，左下角起翻为上一页（水平镜像）。
- **正文抽取**：`assets/readability.js` 在页面内执行，返回 JSON `{title, byline, content, text, length, nextPageUrl, siteName}`，Kotlin 侧用 `org.json.JSONTokener` 反转义解析；抽取结果长度过短则判定为「非文章页」，不显示阅读按钮。
- **自动续章**：`pendingNextUrl` 保存当前最后一章的下一章链接，读到倒数第二页即后台预取，追加章节后以 `setPages(pages, index, keepCacheFrom)` 增量重排，已渲染页面缓存不丢，翻页不跳、不闪。
- **图片**：`ImageLoader` 内存 LruCache + 文件缓存，`HttpURLConnection` 同步下载并按 `inSampleSize` 采样，缩放到页宽以内作为 `ImageSpan` 插入，失败显示灰色占位块。
- **亮度与耗电**：`window.screenBrightness` 独立调节（可跟随系统）；「保持亮屏」使用 `FLAG_KEEP_SCREEN_ON`；沉浸式用 `WindowInsetsControllerCompat` 隐藏系统栏。

---

## 六、依赖

仅使用官方 Jetpack / AndroidX，无第三方 SDK、无 kapt、无网络统计，全部经腾讯源解析：

`appcompat 1.7.0` · `material 1.12.0` · `constraintlayout 2.1.4` · `recyclerview 1.3.2` · `webkit 1.11.0` · `core-ktx 1.13.1`

`compileSdk 34` / `minSdk 26` / `targetSdk 34` · Kotlin 1.9.24 · AGP 8.5.2 · Gradle 8.9。

---

## 七、原型预览

不想装 Android Studio 也可以先看效果，两个页面都在 `preview/` 下，用手机或电脑浏览器直接打开即可：

- **`landing.html` — 产品介绍页**：ReaderH 的完整介绍，含 Hero 机型演示（点手机屏幕左右两侧可翻页）、双引擎前后对比、六项核心能力、七套主题色卡（点色卡实时换手机里的阅读背景）、隐私说明与常见问题。
- **`reader_preview.html` — 交互原型**：浏览器页 → 阅读模式 → 翻页、字号/行距/段距、7 套背景切换的完整流程（CSS 多列分页 + 卷曲动画模拟原生分页与翻页）。

两个页面均为零依赖单文件 HTML，离线可用。

---

## 八、GitHub Actions 一键构建（无需本地 Android SDK）

本仓库 `android` 分支内置 `.github/workflows/android.yml`，**push 即自动构建 APK**：

1. 推送代码到任意分支（或打 `v*` 标签）。
2. Actions 自动：JDK 17 + Gradle 8.9（走腾讯软件源镜像解析依赖）→ `assembleRelease`。
3. 构建产物在 workflow 的 **Artifacts** 里下载（`ReaderH-apk`）；若推送的是 `v*` 标签，还会自动创建 **Release** 并附带 APK。

签名（可选）：在仓库 Settings → Secrets and variables → Actions 配置以下四项后，产物使用正式签名；**不配置也能构建**，此时 release APK 用 debug 签名、可直接安装：

| Secret | 说明 |
|---|---|
| `KEYSTORE_BASE64` | keystore 文件的 base64（`base64 -w0 release.jks`） |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | 别名（缺省 `readerh`） |
| `RELEASE_KEY_PASSWORD` | 别名密码 |

---

## 九、已知边界

- 本地构建需要 Android Studio / Android SDK；无本地环境时直接用上面的 GitHub Actions。
- 正文抽取依赖页面结构，对强 JS 渲染或反爬站点可能抽不到正文（可在设置中开启桌面版 UA 或 JavaScript 改善）。
- 小说站点的「下一章」依赖 `nextPageUrl` 抽取结果，个别站点规则特殊时需补充解析规则。
