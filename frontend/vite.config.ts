import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  return ({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: mode === 'development' ? {
      '/api': {
        target: env.VITE_DEV_BACKEND_URL || 'http://localhost:8081',
        changeOrigin: true,
      },
    } : undefined,
  },
  test: { environment: 'node', include: ['tests/**/*.test.ts'] },
});
});
