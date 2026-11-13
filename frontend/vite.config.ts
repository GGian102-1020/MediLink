import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Landing pública estática: sin proxy al backend ni rutas web de operaciones.
export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
  },
  test: { environment: 'node' },
});
