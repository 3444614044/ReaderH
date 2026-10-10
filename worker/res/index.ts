/**
 * ReaderH 介绍页 —— Cloudflare Worker 入口
 *
 * 职责极薄：静态资源命中即返回；其余请求回退到 index.html（SPA fallback）。
 * 所有页面逻辑在 site/ 的静态 HTML 中，本文件只负责边缘缓存与安全头。
 */
export interface Env {
  ASSETS: Fetcher;          // 由 wrangler.toml 的 [assets] 注入
  ENVIRONMENT?: string;     // "production" | "preview"，由 Pages/Worker 自动注入
}

// 需要回退到 index.html 的路径（前端路由用；当前为纯静态站点，保留以兼容未来扩展）
const SPA_FALLBACK = new Set(['/']);

// 永久重定向（旧链接 / 规范域名）
const REDIRECTS: Record<string, string> = {
  '/index': '/',
  '/home': '/',
};

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const { pathname } = url;

    // 1. 规范重定向
    if (REDIRECTS[pathname]) {
      return Response.redirect(REDIRECTS[pathname], 301);
    }

    // 2. 仅接受 GET / HEAD
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      return new Response('Method Not Allowed', { status: 405 });
    }

    // 3. 尝试命中静态资源
    let response: Response;
    try {
      response = await env.ASSETS.fetch(request);
    } catch (e) {
      return new Response('Internal Error', { status: 500 });
    }

    if (response.status === 404) {
      // 4. SPA 回退：避免直接 404，回退到 index.html
      const fallback = new URL('/index.html', request.url);
      const fbReq = new Request(fallback, request);
      response = await env.ASSETS.fetch(fbReq);
    }

    // 5. 复制并追加安全头与缓存策略（Response 的 headers 不可直接写）
    return withHeaders(response, pathname);
  },
};

function withHeaders(response: Response, pathname: string): Response {
  const headers = new Headers(response.headers);

  // 安全头
  headers.set('X-Frame-Options', 'DENY');
  headers.set('X-Content-Type-Options', 'nosniff');
  headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
  headers.set('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), interest-cohort=()');
  headers.set(
    'Content-Security-Policy',
    "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self'; media-src 'self'; object-src 'none'; base-uri 'self'; form-action 'none';"
  );

  // 缓存策略：按文件类型分级
  const ext = pathname.split('.').pop()?.toLowerCase() ?? '';
  if (['js', 'css', 'woff', 'woff2', 'ttf', 'eot'].includes(ext)) {
    // 构建产物带内容哈希，可长期缓存
    headers.set('Cache-Control', 'public, max-age=31536000, immutable, stale-while-revalidate=86400');
  } else if (['png', 'jpg', 'jpeg', 'webp', 'avif', 'gif', 'svg', 'ico'].includes(ext)) {
    headers.set('Cache-Control', 'public, max-age=604800, stale-while-revalidate=86400');
  } else if (pathname.endsWith('.html') || pathname === '/') {
    // HTML 不长期缓存，保证配置/文案更新及时生效
    headers.set('Cache-Control', 'public, max-age=60, s-maxage=60, stale-while-revalidate=300');
  }

  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}
