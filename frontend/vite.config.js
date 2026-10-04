import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

const proxy = {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  }
};

// dev 与 preview 都把 /api 代理到本地 Quarkus 8080
export default defineConfig({
  plugins: [svelte()],
  server: { port: 5173, proxy },
  preview: { port: 5173, proxy }
});
