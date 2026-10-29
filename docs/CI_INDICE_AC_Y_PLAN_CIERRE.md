# Campana de aceptacion AC-01..AC-60 - Guia operativa

La fuente normativa unica es la Especificacion Maestra v1.4.3, **seccion 15**. La matriz `MATRIZ_ACEPTACION_MVP_v1.4.3.md` es el registro de **aceptacion**. Este documento explica **como recolectar evidencia**, no redefine reglas de negocio.

## Estado al preparar esta actualizacion

- El ultimo SHA que el equipo comprobo en verde es `642bce7` (backend/PostgreSQL, frontend y despliegue HTTPS).
- El nuevo generador de indice, los cuatro recorridos API adicionales y la retencion de artifacts **requieren un nuevo push y tres nuevos runs**.
- El indice automatizado se obtiene desde la ejecucion del SHA: `evidencias/indice_ac_postgres/indice_ac.md` y `evidencias/indice_ac_e2e/indice_ac.md` dentro de los artifacts de GitHub Actions.
- Para un AC apareceran **EVIDENCIA_PARCIAL** o **SIN_EVIDENCIA_AUTOMATIZADA**, pero nunca PASS normativo automatico. Una prueba unitaria de algoritmo no sustituye una carrera real contra PostgreSQL.

## Procedimiento por AC

1. Abrir la fila AC en la **especificacion maestra**, leer precondiciones y resultado observable completos (no solo la sintesis de la matriz).
2. Preparar datos descartables en PostgreSQL real y registrar T del servidor. Para carreras: iniciar peticiones realmente simultaneas y registrar HTTP + estado final de BD.
3. Ejecutar la API o navegador bajo el rol correcto, guardar solicitud **sin credenciales ni tokens**, HTTP y consultas de comprobacion.
4. Conservar `SHA`, URL de Actions, fecha, version de PostgreSQL, datos iniciales y resultado; solo entonces actualizar **esa fila** a PASS/FAIL/BLOCKED.
5. Para los errores: verificar `codigo` y HTTP; para cambios de estado: verificar saldo, compromisos Rx, auditoria e idempotencia segun corresponda.

## Cobertura dirigida por clase de escenario

| Tipo de escenario | AC | Evidencia especifica obligatoria |
|---|---|---|
| Concurrencia de Stock, receta, Cuenta, Farmacia e idempotencia | 01, 04, 16, 31, 32, 38, 39, 40, 41, 42 | Dos transacciones reales paralelas, respuestas HTTP, locks serializados y estado persistido |
| Vencimientos y suspensiones, inclusive limites de milisegundos/backlog | 05, 07, 13, 14, 15, 26, 36, 37, 44, 51, 54, 55, 60 | Reloj controlado, historia de vencimientos, intervalos calculados, ausencia de duplicados |
| Reservas, limites, estados, Rx e idempotencia | 02, 03, 06, 08, 12, 17, 18, 19, 20, 21, 22, 29, 33, 43, 49, 52, 58 | API + Stock final + compromiso/utilizacion Rx + efectos transaccionales/metadata |
| Administracion, roles, privacidad, catalogo y auditoria | 09, 10, 11, 23, 24, 25, 27, 28, 30, 34, 35, 45, 46, 47, 50, 53, 56, 57 | Roles negativos/positivos, snapshots, historiales y registros append-only |
| Interfaz y navegacion publica | 48 | Visitante: busqueda -> Presentacion -> disponibilidad, refresh y capturas a 360, 390, 768 y 1440 px |
| Backup/restauracion | 59 | Restauracion en BD distinta, esquema + cantidad y checksum de filas, logs del workflow |

Los grupos suman los 60 IDs; algunos escenarios tienen componentes transversales que se deben verificar ademas de su categoria principal.

## Controles que requieren atencion especial

- **AC-31:** carrera POST Reserva / deshabilitar Usuario. Nunca dejar una Cuenta deshabilitada con Reserva ACTIVA funcional.
- **AC-32:** carrera POST Reserva / deshabilitar ultimo Operador, serializada en Farmacia.
- **AC-40:** carrera Reserva Rx / anular Prescripcion; solo un orden puede confirmar primero, sin romper autorizacion ya comprometida.
- **AC-41:** carrera Reserva / desactivacion Farmacia/Medicamento/Presentacion y revalidacion antes del commit.
- **AC-48:** prueba de navegador real sobre navegacion publica; API 200 no es por si sola evidencia del recorrido UI.
- **AC-55:** reactivar Cuenta Usuario no elimina la Suspensión vigente.

Estos seis no tienen todavia una referencia automatizada individual en el indice de cobertura y requieren preparacion prioritaria de fixtures y pruebas de integracion.

## Validacion no funcional adicional (no cubierta por la matriz AC)

- **RNF-06:** Chrome, Firefox y Edge en sus dos ultimas versiones estables al ejecutar las pruebas.
- **RNF-07:** Visitante/Usuario sin perdida funcional ni scroll horizontal entre 360 y 1440 px.
- **RNF-11:** medir p95 para registro/login, busqueda, presentaciones, disponibilidad y creacion/cancelacion/consulta de Reserva. Registrar hardware, versiones, volumen, dataset y concurrencia. Lecturas <=1s, transacciones <=2s.
- **RNF-12:** recuperar la copia PostgreSQL sobre base *separada*, verificando datos y esquema. Conservar artifact; no basta la mera presencia de un script de backup.

## Precauciones

Nunca ejecutar `scripts/smoke_roles.py` ni las pruebas destructivas contra una base real, persistente o compartida. No versionar `.env`, JWT, dumps, contraseñas ni resultados que contengan datos personales. No hacer merge ni borrar volúmenes para forzar un PASS.
