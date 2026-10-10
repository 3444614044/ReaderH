# ReaderH · 介绍页（Cloudflare 部署）

一个纯静态、零后端依赖的产品介绍页 + 阅读器交互原型，部署到 **Cloudflare Pages**（推荐）或 **Cloudflare Workers + Assets**。

> 说明：ReaderH 本体是 Android 应用；本仓库是**它的官网/介绍页与浏览器内交互原型**，不含 Android 源码，因此可完全静态托管。

---

## 目录结构

```
readerh-landing/
├── package.json              # 构建脚本（vite + wrangler）
├── vite.config.ts            # 静态站点构建配置
├── tsconfig.json             # 仅用于 worker 类型检查
├── wrangler.toml             # Cloudflare 部署配置（Pages 项目名、环境变量）
├── worker/
│   ├── src/index.ts          # Workers 入口：SPA 回退 + 安全头 + 边缘缓存
│   └── tsconfig.json
└── site/                     # ★ 站点源文件，可直接双击打开
    ├── index.html            # 介绍页（翻页演示 / 7 套主题 / 字号行距实时调节）
    └── reader_preview.html   # 阅读器交互原型（分页、仿真翻页、自动续章）
```

## 本地开发

```bash
npm install
npm run dev                  # 访问 http://localhost:5173
npm run build                # 产出 dist/
npm run preview              # 预览构建产物
```

`site/` 是纯 HTML/CSS/JS，不依赖构建即可运行：直接 `open site/index.html` 也能完整体验。

## 部署方式一：Cloudflare Pages（推荐）

零配置、自带全球 CDN、自动 HTTPS、支持预览部署（PR/分支预览）。

### 1. 控制台创建

1. 登录 [Cloudflare Dashboard](https://dash.cloudflare.com) → **Workers & Pages** → **Create application** → **Pages** → **Connect to Git**。
2. 授权并选中本仓库。
3. 构建设置：
   - Framework preset：**None**（或 Vite）
   - Build command：`npm ci && npm run build`
   - Build output directory：`dist`
   - Root directory：`/`
4. 环境变量（可选）：
   - `VITE_GA_ID`：统计 ID（默认不采集，见“隐私”）
5. 点 **Save and Deploy**。

### 2. CLI 部署（无需 Git）

```bash
npm install -g wrangler
wrangler login
wrangler pages project create readerh --production-branch main
wrangler pages deploy dist --project-name readerh
# 自定义域名
wrangler pages domain add readerh.example.com --project-name readerh
```

部署后获得 `https://readerh.pages.dev`，可再绑定自己的域名并开启 **Cloudflare for SaaS** 实现按地区加速。

### 3. 分支预览

`git push` 到 `main` → 生产环境更新；推送到其他分支 → 生成 `https://<hash>.readerh.pages.dev` 预览链接，适合审核文案与动效。

## 部署方式二：Cloudflare Worker + Assets

适合需要**边缘逻辑**的场景（A/B、地区跳转、API 聚合、防盗链、请求改写）。

```bash
npm run build
wrangler deploy          # 读取 wrangler.toml 的 main + assets
```

`worker/src/index.ts` 做了三件事：

1. **SPA 回退**：静态资源命中即返回，否则回退到 `index.html`（利于后续接入 `history` 路由）。
2. **安全响应头**：`Content-Security-Policy`、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: strict-origin-when-cross-origin`、`Permissions-Policy`。
3. **边缘缓存**：HTML 60s、JS/CSS/字体 1 年（按内容哈希）、图片 7 天，均带 `stale-while-revalidate`。

## 性能与合规

| 项 | 策略 |
|---|---|
| 首屏 | 单文件内联 CSS/JS，无外部请求，LCP < 1s（全球边缘） |
| 缓存 | 静态资产 immutable + 长 Cache-Control；HTML 短缓存 |
| 体积 | 未压缩 < 40KB（含原型页），gzip 后 < 12KB |
| 隐私 | 不加载统计/广告 SDK，不采集、不上报；仅 `navigator.language` 用于文案语言（可选） |
| 可访问性 | 语义化标签、`:focus-visible`、减少动画开关（prefers-reduced-motion） |
| 兼容性 | 现代浏览器（Chrome 90+ / Safari 15+ / Firefox 90+），无 IE 支持 |

## 自定义

- **产品文案 / 数据**：编辑 `site/index.html` 顶部 `<title>`、`.stats`、`.lead`。
- **主题色**：`:root` 中的 `--brand`、`--brand2`。
- **原型默认参数**：`reader_preview.html` 顶部 `CHAPTER`、`THEMES`、`fs/lh/mb`。
- **域名与重定向**：Cloudflare Dashboard → Pages → Custom domains；旧链接跳转在 `worker/src/index.ts` 的 `REDIRECTS`。

## License

MIT
