import { createServer as createHttpServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { describe, expect, it } from 'vitest';
import { createServer as createViteServer } from 'vite';
import config from '../vite.config';

function listen(server: Server): Promise<number> {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      server.removeListener('error', reject);
      resolve((server.address() as AddressInfo).port);
    });
  });
}

function close(server: Server): Promise<void> {
  server.closeAllConnections();
  return new Promise((resolve, reject) => {
    server.close(error => error ? reject(error) : resolve());
  });
}

describe('proxy de desarrollo de la API', () => {
  it('no reenvia Origin al backend y permite POST /api/auth/login', async () => {
    let originRecibido: string | undefined;
    const backend = createHttpServer((request, response) => {
      originRecibido = request.headers.origin;
      if (originRecibido) {
        response.writeHead(403, { 'Content-Type': 'text/plain' });
        response.end('Invalid CORS request');
      } else {
        response.writeHead(200, { 'Content-Type': 'application/json' });
        response.end('{"accessToken":"token-de-prueba"}');
      }
    });
    const puertoBackend = await listen(backend);
    let frontend: Server | undefined;
    let vite: Awaited<ReturnType<typeof createViteServer>> | undefined;
    try {
      const proxyApi = config.server?.proxy?.['/api'];
      if (!proxyApi || typeof proxyApi === 'string') {
        throw new Error('Falta la configuracion de proxy /api.');
      }
      vite = await createViteServer({
        configFile: false,
        server: {
          host: '127.0.0.1',
          middlewareMode: true,
          proxy: { '/api': { ...proxyApi, target: `http://127.0.0.1:${puertoBackend}` } },
        },
      });
      frontend = createHttpServer(vite.middlewares);
      const puertoFrontend = await listen(frontend);
      const respuesta = await fetch(`http://127.0.0.1:${puertoFrontend}/api/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Origin: `http://127.0.0.1:${puertoFrontend}`,
        },
        body: JSON.stringify({ correo: 'prueba@mail.test', contrasena: 'Password123!' }),
      });
      expect(respuesta.status).toBe(200);
      expect(await respuesta.json()).toEqual({ accessToken: 'token-de-prueba' });
      expect(originRecibido).toBeUndefined();
    } finally {
      if (frontend) await close(frontend);
      if (vite) await vite.close();
      await close(backend);
    }
  }, 15000);
});
