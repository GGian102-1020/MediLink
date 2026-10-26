# Evidencia de backup/restauración

Fecha: 2026-10-26T12:56:41.6591859-03:00
PostgreSQL: 16.15
Modo: fixture aislada con scripts 01/02 + V2/V3
Contenedor: medlink-backup-test-1d2bc6f5f8c7415886ba826049ebbae3
Origen: medlink_backup_source
Destino descartable: medlink_restore_1d2bc6f5f8c7415886ba826049ebbae3
Comandos: pg_dump -Fc; createdb; pg_restore --exit-on-error --single-transaction --no-owner --no-privileges.
SHA256 del esquema normalizado: D230B43181D33D09FDF2F840BC84CCEE4020DEC470F1457125900FB477E5BB7D
Resultado: PASS (esquema, conteos y checksums coinciden).
Backup: C:\Users\gians\AppData\Local\Temp\medlink-auditoria-e7b15df53ca14b74b2319476bc572aac.dump

Tabla | Registros | MD5 del contenido ordenado
--- | ---: | ---
administrador|1|45afe92881926fd0a0193cd67b4a541d
auditoria|12|0fd63ac1f71481d9cdec4a0bbbe1ea11
cuenta|12|f5bd1a9c876abcb91cfa011bd84dd045
farmacia|5|e10fc1e5646b4ebb205dac540a4a03ee
idempotency_record|0|d41d8cd98f00b204e9800998ecf8427e
item_prescripcion|10|d8a09c8a4ef62e1a71212d71db4db138
medicamento|13|966c6b7423cb5420b33648184ceb9591
movimiento_stock|17|80abd17351a7ee488f373e047dc207f9
operador|6|2a3a988e41417eb733ce2d8080aa2174
prescripcion|8|12c4ac58ada8fa278f40f48c18e46019
presentacion_medicamento|17|337db91e1d0fba256aee4172d904057d
reserva|11|319eb5bae108aeac1dc0335b41f455f7
sesion_revocada|0|d41d8cd98f00b204e9800998ecf8427e
stock|36|27c609bf53086dbf7f301b92a5986bb4
suspension|1|f77b13a14d9d6df7356851a88aaab4a1
usuario|5|83a921c73061e83b740b5dbc2acce475
