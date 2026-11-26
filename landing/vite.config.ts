import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Landing pública estática: sin proxy al backend ni rutas web de operaciones.
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5174,
    strictPort: true,
  },
  test: { environment: 'node' },
  // A local build has its own output. Public `npm run build` cannot reuse it.
  build: { outDir: mode === 'localdemo' ? 'dist-local' : 'dist' },
}));
