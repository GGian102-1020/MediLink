# Evidencia de backup/restauración

Fecha: 2026-10-26T12:59:15.0082768-03:00
PostgreSQL: 16.15
Modo: base indicada por el operador
Contenedor: medlink-audit-verify-0de2a2c896b9448184859524806479d3
Origen: medlink_ci_verify
Destino descartable: medlink_restore_ce59a764548c4b33a167c593527c8d33
Comandos: pg_dump -Fc; createdb; pg_restore --exit-on-error --single-transaction --no-owner --no-privileges.
SHA256 del esquema normalizado: 4BF15EFB8D9095C90BB628FE730383E6C847E0DD91C5DA8FF6ABD4944939D1EB
Resultado: PASS (esquema, conteos y checksums coinciden).
Backup: C:\Users\gians\AppData\Local\Temp\medlink-flyway-3804ac205b524213802bf05a6bc5ded9.dump

Tabla | Registros | MD5 del contenido ordenado
--- | ---: | ---
administrador|35|801e9a293d9a14351227fd2b0c0a995c
auditoria|65|f613b303e2999e9ff1128108cf09e2db
cuenta|102|9dde9f2122eabcc5881ddfba14c1db1c
farmacia|35|55497a98321a77b3d7ca2690e9050337
flyway_schema_history|3|70004106f89d4c1b1e38a9946b05bbd6
idempotency_record|0|d41d8cd98f00b204e9800998ecf8427e
item_prescripcion|1|cba386d9fba9950c909af79d6643dbf2
medicamento|37|a2e348632650d11075b36897799bc831
movimiento_stock|32|7212dcfbc8bd5d31cd87595dffc208e3
operador|34|131746a17cbc435410b1ab0ccdf453e2
prescripcion|1|1d3f5e77164d4e92a94fd7f53f35fcd1
presentacion_medicamento|20|500c44bf47694698084ff7db4f9f2889
reserva|9|fd79cdb4e27b747ca319bf9479ffc513
sesion_revocada|0|d41d8cd98f00b204e9800998ecf8427e
stock|15|a46561bd122bbb2f173784ef4ef7d85e
suspension|1|9e8422fcfa3fd6968ab42adc2f7a9665
usuario|33|45be62f5e2df72a5d28980dd36a530b4
