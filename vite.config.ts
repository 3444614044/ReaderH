import { defineConfig } from 'vite';
import { resolve } from 'node:path';

// 纯静态站点：把 site/ 作为根目录打包。
// Cloudflare Pages 也可直接 git 连接并设为 framework preset = "None"。
export default defineConfig({
  root: resolve(__dirname, 'site'),
  base: '/',
  build: {
    outDir: resolve(__dirname, 'dist'),
    emptyOutDir: true,
    assetsDir: 'assets',
    sourcemap: false,
  },
  server: { port: 5173 },
});
