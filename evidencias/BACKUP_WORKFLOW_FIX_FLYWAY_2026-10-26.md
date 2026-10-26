# Evidencia de backup/restauración

Fecha: 2026-10-26T20:31:35.3950068+00:00
PostgreSQL: 16.15
Modo: base indicada por el operador
Contenedor: medlink-backup-flyway-fix-c5a87ab4d9ac4f1eb368aeb3cdd7927f
Origen: medlink_ci_verify
Destino descartable: medlink_restore_90999c9fb15947dcbf25bcde891e3028
Readiness: servidor definitivo TCP 127.0.0.1:5432 y SELECT 1 por la conexión local de backup.
Comandos: pg_dump -Fc; createdb; pg_restore --exit-on-error --single-transaction --no-owner --no-privileges.
SHA256 del esquema normalizado: 4BF15EFB8D9095C90BB628FE730383E6C847E0DD91C5DA8FF6ABD4944939D1EB
Resultado: PASS (esquema, conteos y checksums coinciden).
Backup: /results/flyway.dump

Tabla | Registros | MD5 del contenido ordenado
--- | ---: | ---
administrador|35|801e9a293d9a14351227fd2b0c0a995c
auditoria|65|f132bfbe445d3c15c0994273c78c2205
cuenta|102|a53bbd7ed476921e34f553078d039622
farmacia|35|109eaf383f92fb93798c74466b081954
flyway_schema_history|3|9d133cc5d1a49ff586de5eb8799e095d
idempotency_record|0|d41d8cd98f00b204e9800998ecf8427e
item_prescripcion|1|cba386d9fba9950c909af79d6643dbf2
medicamento|37|16b8c605de7bcaea9be11743ca8e761c
movimiento_stock|32|1f48133222961abed7cc460cec6308ce
operador|34|131746a17cbc435410b1ab0ccdf453e2
prescripcion|1|5f8fad9c9d2fe0e53768b9f4ed997a9b
presentacion_medicamento|20|500c44bf47694698084ff7db4f9f2889
reserva|9|a13b42326b9be7ffda417b9523ba6ee8
sesion_revocada|0|d41d8cd98f00b204e9800998ecf8427e
stock|15|13e823f5e63a3e973fc94d41019aee36
suspension|1|085f80ae31dd5609cff265f2fe4a988e
usuario|33|45be62f5e2df72a5d28980dd36a530b4
