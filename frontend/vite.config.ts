import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// La API corre en Caddy (HTTPS local); el navegador usa siempre un mismo origen.
// Vite queda limitado a localhost, y solo el proxy confia en el certificado local.
export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.MEDLINK_API_ORIGIN || 'https://localhost:8443',
        changeOrigin: true,
        secure: false,
      },
    },
  },
  test: { environment: 'node' },
});
