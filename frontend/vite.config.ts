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
        // El navegador manda Origin en POST desde http://127.0.0.1:5173.
        // Este es un proxy de desarrollo en localhost, no una peticion CORS
        // directa al backend. Si reenviamos ese Origin, Spring responde 403
        // antes de entrar a /api/auth/login ("Invalid CORS request").
        configure(proxy) {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin');
          });
        },
      },
    },
  },
  test: { environment: 'node' },
});
