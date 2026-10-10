#!/usr/bin/env node
// 零依赖构建：site/ -> dist/（Cloudflare Pages 可直接部署）
// 不依赖 npm install；本地开发仍可用 `npx vite`。
import { mkdirSync, copyFileSync, readdirSync, statSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { createHash } from 'node:crypto';

const ROOT = new URL('.', import.meta.url).pathname;
const SRC = join(ROOT, 'site');
const OUT = join(ROOT, 'dist');
const HTMLS = new Set(['index.html', 'reader_preview.html']);

rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });

/** @param {string} dir */
function* walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) yield* walk(p);
    else yield p;
  }
}

// 1) 静态资源按内容哈希重命名，便于 immutable 长缓存
const assets = new Map();
for (const p of walk(SRC)) {
  const rel = relative(SRC, p).split(sep).join('/');
  if (HTMLS.has(rel)) continue;
  const h = createHash('sha256').update(readFileSync(p)).digest('hex').slice(0, 8);
  const parts = rel.split('.');
  const ext = parts.pop();
  const name = parts.join('.') + '.' + h + '.' + ext;
  mkdirSync(join(OUT, name, '..'), { recursive: true });
  copyFileSync(p, join(OUT, name));
  assets.set('/' + rel, '/' + name);
}

// 2) HTML 中的资源引用替换为指纹路径
for (const f of HTMLS) {
  let s = readFileSync(join(SRC, f), 'utf8');
  for (const [from, to] of assets) s = s.split(from).join(to);
  writeFileSync(join(OUT, f), s);
}

// 3) Cloudflare Pages 原生配置（无需 Worker 即生效）
writeFileSync(join(OUT, '_headers'), [
  '/*',
  '  X-Frame-Options: DENY',
  '  X-Content-Type-Options: nosniff',
  '  Referrer-Policy: strict-origin-when-cross-origin',
  '  Permissions-Policy: camera=(), microphone=(), geolocation=(), interest-cohort=()',
  "  Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self'; media-src 'self'; object-src 'none'; base-uri 'self'; form-action 'none';",
  '',
  '/*.html',
  '  Cache-Control: public, max-age=60, s-maxage=60, stale-while-revalidate=300',
  '/assets/*',
  '  Cache-Control: public, max-age=31536000, immutable, stale-while-revalidate=86400',
  '/*.css',
  '  Cache-Control: public, max-age=31536000, immutable, stale-while-revalidate=86400',
  '/*.js',
  '  Cache-Control: public, max-age=31536000, immutable, stale-while-revalidate=86400',
  '/*.woff2',
  '  Cache-Control: public, max-age=31536000, immutable, stale-while-revalidate=86400',
  '',
].join('\n'));

// 注意：不再生成 _redirects。
// SPA 回退由 worker/src/index.ts 处理；旧路径跳转也在 Worker 的 REDIRECTS 中。
// 若在此生成 `/*  /index.html  200`，Cloudflare 会判定为无限循环（strip .html / index 后再匹配自身）。

const files = [];
for (const p of walk(OUT)) files.push('/' + relative(OUT, p).split(sep).join('/'));

writeFileSync(join(OUT, 'manifest.json'), JSON.stringify({
  generated: new Date().toISOString(), files, assets: Object.fromEntries(assets),
}, null, 2));

console.log('dist/ 产物：' + files.length + ' 个文件，指纹化资源：' + assets.size + ' 个');
process.stdout.write(files.join('\n') + '\n');