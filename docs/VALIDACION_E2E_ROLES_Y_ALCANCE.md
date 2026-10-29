# Validacion end-to-end de roles sobre entorno efimero (MedLink v1.4.3)

**Rama de trabajo:** `fix/cierre-mvp-v1.4.3`. **No requiere Docker Desktop local.** No modifica `main` ni realiza merge.

## Que se ejecuta en GitHub Actions

El workflow **MediLink - Despliegue HTTPS y seed demo** crea una base PostgreSQL nueva, ejecuta Flyway, levanta React + API detras de Caddy HTTPS y carga explicitamente el seed ficticio. Despues de comprobar la disponibilidad SPA/API y el login de Administrador, ejecuta:

1. `python3 -m unittest discover -s scripts/tests -p 'test_smoke_roles_*.py' -v`: controles unitarios del runner.
2. `MEDLINK_SMOKE_EPHEMERAL=1 python3 scripts/smoke_roles.py --demo-ack SEED_DEMO_EFIMERO`: recorridos API con Visitante, Usuario, Operador de dos farmacias y Administrador.

Los recorridos comprueban: busqueda y disponibilidad; autenticacion/roles; aislamiento por Farmacia; privacidad en DTO; ultimo Operador y desactivacion con reservas; Stock ausente y catalogo inactivo; limite OTC; crear Reserva, idempotencia secuencial y concurrente; ultima unidad de Stock entre dos Usuarios en paralelo; rechazo de clave reutilizada; retiro sin doble descuento; cancelacion y metadatos; traza de MovimientoStock; Reserva Rx con liberacion de Stock; logout y revocacion.

**Precauciones:** el runner altera Stock y Reservas y utiliza credenciales ficticias conocidas. Solo se permite en HTTPS `localhost` o `127.0.0.1`, con puerto explicito, marcador de entorno efimero y confirmacion en la CLI. Nunca usar con bases persistentes, staging compartido, datos reales ni produccion. El seed de prueba NO es apropiado para una instalacion publica.

## Interpretacion honesta de resultados

- `PASS` en el workflow: el recorrido API automatizado definido en el script termino correctamente **para ese SHA**. NO equivale a validar automaticamente toda la especificacion.
- `FAIL`: abrir el job `Docker Compose + Flyway + React + API (HTTPS)` y localizar el escenario `FAIL`. No fusionar ni etiquetar AC correspondientes como PASS hasta corregir y ejecutar nuevamente.
- `BLOCKED` / job no iniciado: no existe evidencia. No reinterpretar como PASS.

Estos casos cubren **solo partes** de AC-01, AC-03, AC-06, AC-08..11, AC-17, AC-18, AC-20, AC-21, AC-24, AC-28..30, AC-33, AC-34, AC-42, AC-47, AC-48, AC-49, AC-52 y AC-58. Solo se podran marcar individualmente en la matriz con evidencia completa de **todas** las precondiciones/efectos normativos, incluidas BD y concurrencia cuando corresponda.

## Todavia necesario antes del cierre del MVP

- AC-01..AC-60: registrar evidencia reproducible individual. Para concurrencia usar PostgreSQL real y solicitudes simultaneas; para reloj usar instantes controlados. No completar por inferencia a partir del nombre de una suite.
- UI: recorrido de Visitante, Usuario, Operador y Administrador; probar sesiones, permisos y errores en Chrome, Firefox y Edge. Responsive a 360, 390, 768 y 1440 px, capturando evidencia.
- RNF-11: medir p95 con hardware/versiones/dataset/concurrencia/volumen documentados (lecturas <=1 s; transacciones <=2 s). No sustituir por un smoke simple.
- RNF-12/AC-59: descargar o conservar evidencia real de backup/restauracion de una base independiente, no solo el estado verde de Actions.
- Asegurar que toda comprobacion corresponde al commit SHA evaluado; un cambio posterior exige nueva ejecucion.

## Uso desde GitHub Desktop

Con `fix/cierre-mvp-v1.4.3` y el working tree limpio, aplicar solo el parche provisto para esta rama. Revisar los archivos modificados y commitear. `Push origin` dispara de nuevo los tres workflows. **No es necesario abrir PR ni hacer merge.**
