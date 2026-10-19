import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

const apiTarget = process.env.REGENT_API_TARGET ?? 'http://127.0.0.1:8787'

export default defineConfig({
  // 生产包由 Nginx 挂在站点根目录。显式固定为 '/'，避免构建环境把资源路径推断成本机 file:// 地址。
  base: '/',
  plugins: [vue(), tailwindcss()],
  build: {
    // 桌面端会打包给 Windows 用户，不能假设对方是最新 Chromium。
    // 低于这个档位时，Lightning CSS 会把 @media (width>=…) 降级成 min-width，
    // 并给 color-mix() 生成静态回退色；否则旧版 Windows 浏览器会忽略整段响应式布局。
    cssTarget: 'chrome96',
  },
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    host: '127.0.0.1',
    // 8787 是后端，前端避开常用端口以免与本机其它工程打架
    port: 12580,
    // 端口是约定的一部分：被占用时宁可启动失败，也不许悄悄换一个
    // （脚本与文档都按这个端口探活，静默换端口会让探活探到别的服务上）
    strictPort: true,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
      // 安装包本地也走同一个 /downloads 路径，生产由 Nginx 直接静态发送。
      '/downloads': { target: apiTarget, changeOrigin: true },
    },
  },
})
