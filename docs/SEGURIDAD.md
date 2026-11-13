# Revisión de seguridad — MediFind (panel de operadores + backend)

**Fecha:** 13/11/2026
**Tipo:** revisión liviana de código fuente, basada en la skill `security-audit` de Cloudflare.
No se ejecutó la auditoría completa de seis fases (se cortó después del reconocimiento para reducir consumo),
por lo que esto **no es una cobertura completa**: es una pasada focalizada en los controles más críticos.
No se ejecutó código del proyecto ni se envió tráfico a ningún servicio: todo es lectura de código.

## Resumen

No se encontraron vulnerabilidades graves en los controles revisados. La separación por farmacia, la
autenticación con JWT y la construcción de SQL están bien resueltas. Hay mejoras recomendadas para
antes de un despliegue real (límite de intentos de login, credenciales por defecto y datos de prueba).

## Controles verificados (correctos)

| Control | Dónde | Resultado |
|---|---|---|
| Un operador solo accede a su farmacia (reservas) | `backend/.../reserva/service/ReservaService.java` (`scope`, líneas ~197-201) | Compara la farmacia de la URL con la de la cuenta; si no coincide, 403. |
| Un operador solo accede a su farmacia (stock: listar, movimientos, ajustar) | `stock/service/StockService.java` (`checkRead`), `stock/repository/StockIntegration.java:29-34` | Mismo control; además exige cuenta ACTIVA. |
| Validación del JWT | `security/JwtTokens.java`, `security/BearerAuthenticationFilter.java:54-63` | HS256 con clave ≥ 32 bytes (si falta, el backend no arranca), vence a los 30 min, se valida `tokenVersion`, rol y sesiones revocadas (logout). |
| Cambio de contraseña / cuenta deshabilitada | `BearerAuthenticationFilter.java` | Invalida tokens anteriores (`tokenVersion`) y rechaza cuentas deshabilitadas. |
| SQL dinámico (ordenamiento) | `common/PageRequests.java`, `reserva/service/ReservaStore.java:116-139` | Lista blanca de campos y direcciones; los valores van como parámetros. Sin inyección SQL. |
| Paginación | `PageRequests.validate` | `size` máximo 100. |
| CORS | `security/CorsConfig.java` | Sin orígenes por defecto; lista exacta configurable; sin credenciales. |
| Contraseñas | `auth/AuthService.java` | BCrypt(12) y límite de 72 bytes. Mensaje de error genérico en el login. |
| Puertos de Docker | `docker-compose.yml` | Postgres, Adminer y gateway escuchan solo en `127.0.0.1`; el backend no se publica. |
| Frontend | `frontend/src` | Sin `dangerouslySetInnerHTML`, `innerHTML` ni `eval`. El token vive en `sessionStorage` (se borra al cerrar la pestaña). El panel rechaza cuentas que no sean OPERADOR (el backend también lo exige). |
| Recursos externos | `frontend/index.html`, `frontend/src/main.tsx` | La tipografía se empaqueta localmente (`@fontsource/inter`): el panel no hace pedidos a servidores de terceros. |

## Recomendaciones (antes de un despliegue real)

| Prioridad | Tema | Detalle | Sugerencia |
|---|---|---|---|
| Media | Sin límite de intentos de login | `POST /api/auth/login` no limita intentos; permite probar contraseñas por fuerza bruta. | Limitar por IP y por cuenta (por ejemplo, en el gateway Caddy o con un filtro en Spring) y registrar intentos fallidos. |
| Media | Datos de prueba con contraseña conocida | `database/02_datos_prueba.sql` crea cuentas con `Password123!`. | Cargarlo solo en bases locales descartables; nunca en una base compartida o productiva. |
| Media | Credenciales por defecto | `docker-compose.yml` y `.env.example` usan `medlink123` para Postgres. | Definir contraseñas propias en `.env` en cualquier entorno que no sea local. |
| Baja | Enumeración de correos por tiempo | Si el correo no existe, el login responde sin calcular BCrypt (más rápido). El registro también informa si el correo ya existe (por diseño). | Ejecutar un BCrypt "de relleno" cuando el correo no existe. |
| Baja | Encabezados `X-Forwarded-*` | `server.forward-headers-strategy=framework` confía en esos encabezados. | Asegurar que solo el gateway pueda llegar al backend (hoy es así en Compose). |
| Baja | Adminer | Expuesto en `127.0.0.1:8080`. | No levantarlo fuera del entorno local. |
| Info | `.env` local | Contiene la clave JWT real de esta máquina; está en `.gitignore`. | No subirlo ni compartirlo. |

## Limitaciones

- Revisión parcial y solo de código: no se probaron los hallazgos en ejecución.
- No se revisaron en profundidad los módulos de administración (`admin`, `prescripcion`, `auditoria`) ni el flujo de creación de reservas del paciente.
- La configuración de un despliegue real (proxy, TLS, firewall, secretos) no está en el repositorio y no se pudo evaluar.
