import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// 构建产物直接输出到 Quarkus 静态资源目录，随后端一个进程提供服务
export default defineConfig({
  plugins: [svelte()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' }
  },
  build: {
    outDir: '../backend/src/main/resources/META-INF/resources',
    emptyOutDir: true
  }
});
