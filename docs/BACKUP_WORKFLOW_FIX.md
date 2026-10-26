# Corrección del backup de GitHub Actions - 26/10/2026

## Fallo revisado

- Rama: `auditoria-infraestructura`; commit del equipo `4fbcf4474be65af54dede2ec2685d99494cee059`.
- [Ejecución 40384893088](https://github.com/GGian102-1020/MediLink/actions/runs/40384893088),
  job `121790451828`, runner Ubuntu 24.04.5.
- Maven terminó a las 16:15:14 de Argentina con `BUILD SUCCESS`: 124 pruebas,
  0 fallos, 0 errores y 0 omitidas. El gate de 24 suites también aprobó.
- A las 16:15:17 falló `Verify complete backup and isolated restoration`:
  `psql: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed: No such file or directory`.
- El backup posterior con historial Flyway quedó omitido como consecuencia del
  fallo anterior, no porque hubiese fallado Maven o PostgreSQL durante los tests.

## Causa

La fixture de backup crea **otro contenedor**, independiente del servicio que ya
utilizó Maven. Su espera anterior ejecutaba `pg_isready` sin `-h`, usando el
socket Unix. La imagen oficial `postgres:16-alpine` inicializa un volumen vacío
con esta secuencia:

1. `docker_temp_server_start` inicia PostgreSQL con `listen_addresses=''`:
   admite conexiones por socket, pero no TCP. En esta fase, el probe anterior
   ya devuelve código 0.
2. El entrypoint crea la base y ejecuta sus archivos de inicialización.
3. `docker_temp_server_stop` apaga ese servidor y elimina el socket.
4. `exec postgres` inicia el servidor definitivo, con TCP y socket nuevos.

El script interpretaba la fase 1 como disponibilidad definitiva y podía
ejecutar `psql` durante la fase 3. De ahí el socket ausente. Se inspeccionó el
entrypoint de la **misma imagen** usada en el run fallido:
`sha256:0cefaabd882e42bcb2a2f3dc56d3ce4770560ba62fd7f197f1a2e008fd5731ab`,
PostgreSQL 16.15. Además, la regresión reproduce exactamente este intervalo.

Es una carrera de arranque, no una incompatibilidad SQL ni un socket que deba
montarse en el runner. Las diferencias de tiempo entre Linux nativo en Actions
y Docker Desktop/cliente Windows pueden hacerla aparecer o esconderla. Se
reprodujo de manera controlada en ambas plataformas. El éxito de Maven no
garantizaba la disponibilidad de la fixture creada más tarde.

## Corrección

`scripts/backup_restore.ps1` ahora usa una espera común para Fixture y Live:

- `pg_isready -h 127.0.0.1 -p 5432 -t 1` dentro del contenedor: el servidor
  temporal no satisface esta condición.
- Una vez disponible TCP, `psql -X -w ... -c 'SELECT 1;'` por la conexión local
  de backup verifica base, usuario y autenticación.
- Solo después se cargan SQL, se genera el dump y se crea la base de restauración.
- La espera falla con diagnóstico y límite de 60 s, ajustable mediante
  `-ReadyTimeoutSeconds` (1..300). No genera evidencia de éxito ante un timeout.

Se conserva la autenticación por socket de `pg_dump`, `pg_restore`, `createdb`
y `dropdb`. Cambiar todos esos clientes a TCP habría requerido configurar
contraseñas adicionales; no hace falta para resolver esta carrera. El probe TCP
es interno: no publica nuevos puertos del contenedor.

El workflow usa también TCP en el healthcheck de su servicio PostgreSQL y añade
la regresión determinista antes de los dos backups. Se conservan íntegros:
Maven `clean verify`, el gate contra omisiones, backup de fixture, backup con
Flyway, restauración real, comparación de esquema/conteos/checksums y artefactos.
Los fallos siguen terminando el job con error; no se añadió `continue-on-error`.

## Regresión automatizada

`scripts/tests/backup_restore_regression.ps1` crea un contenedor UUID sin puertos
publicados y copia esquema/seed/V2/V3 a su directorio de inicialización. Pausa
el entrypoint durante el servidor temporal y comprueba:

1. El probe antiguo por socket tiene éxito y TCP todavía no acepta conexiones.
2. El script corregido agota un timeout corto sin producir dump, evidencia de
   éxito ni base de restauración.
3. Apagar controladamente ese servidor temporal reproduce el error exacto del
   socket ausente; toda esta manipulación se limita al contenedor de la regresión.
4. Al liberar el arranque definitivo, el script completa `pg_dump -Fc`, una
   restauración separada y verifica estructura/conteos/checksums de las 16 tablas.

El contenedor y sus volúmenes propios se eliminan en `finally`. Los nombres de
contenedores/bases temporales se generan dentro de los scripts; no se emplean
`medlink-db`, la base de desarrollo ni volúmenes existentes. La prueba Live usa
como origen exclusivamente su contenedor de regresión.

## Resultados reales

| Comprobación | Plataforma | Resultado |
| --- | --- | --- |
| Regresión de servidor temporal, timeout, socket ausente y restauración real | Windows, kernel 10.0.26300; PowerShell 7.6.5 | 4 comprobaciones PASS; 16 tablas restauradas |
| Modo Fixture corregido, sin mocks | Windows; PowerShell 7.6.5 | PASS; 16 tablas, esquema/conteos/checksums iguales |
| Misma regresión sin mocks | Ubuntu 24.04.1 LTS; PowerShell 7.5.0 | 4 comprobaciones PASS; 16 tablas restauradas |
| Modo Fixture corregido | Ubuntu 24.04.1 LTS; PowerShell 7.5.0 | PASS; 16 tablas, esquema/conteos/checksums iguales |
| Maven `mvnw.cmd -B clean verify` con una base nueva en puerto aleatorio de localhost | Windows; Java 21; PostgreSQL 16.15 | 124 pruebas aprobadas; 0 fallos/errores/omisiones; 43.846 s; 17:31:33 Argentina |
| Gate `verificar_tests_postgres.py` sobre esa nueva ejecución | Windows | PASS; 24 suites; 35 pruebas PostgreSQL reales |
| Backup Live de la base anterior, incluyendo `flyway_schema_history` | Cliente PowerShell sobre Ubuntu 24.04; PostgreSQL aislado | PASS; 17 tablas; esquema/conteos/checksums iguales |

Para las pruebas Linux se creó una imagen auxiliar con el runtime oficial
`mcr.microsoft.com/powershell:7.5-ubuntu-24.04` y cliente Docker, ejecutando los
mismos archivos del repositorio montados en solo lectura. No es una nueva
ejecución alojada en GitHub: valida el procedimiento con PowerShell/Linux, y
la confirmación remota de esta revisión requiere el próximo push manual.

La restauración de 17 tablas conserva su evidencia en
[BACKUP_WORKFLOW_FIX_FLYWAY_2026-10-26.md](../evidencias/BACKUP_WORKFLOW_FIX_FLYWAY_2026-10-26.md).
Los XML de la nueva ejecución están en `backend/target/surefire-reports`, y su
log en `backend/backup-fix-maven.log` (ambos ignorados por Git). No se copiaron
logs completos del runner ni dumps a archivos versionables.

También aprobaron el parser de PowerShell, la validación YAML con SnakeYAML
(8 pasos, healthcheck TCP, regresión y ambos backups presentes, sin bypass de
fallos) y `git diff --check`. Al terminar solo seguían activos `medlink-db` y
`medlink-adminer`, los servicios existentes al comenzar; se retiraron todos los
contenedores de prueba y la imagen auxiliar creada para el cliente Linux.

## Verificación local y próxima ejecución de Actions

Desde la raíz, con PowerShell 7 y Docker disponibles:

```powershell
$testRun = [guid]::NewGuid().ToString('N')
pwsh -File ./scripts/tests/backup_restore_regression.ps1 `
  -OutputDirectory "./backend/target/backup-regression-$testRun"
pwsh -File ./scripts/backup_restore.ps1 -Fixture `
  -BackupPath "../medlink-backup-$testRun.dump" `
  -EvidencePath "./backend/target/backup-$testRun.md"
```

Ambos deben terminar con código 0, mostrar `PostgreSQL ready: permanent TCP
server and SQL connection verified` y `PASS: complete restore verified`.
La regresión incluye un timeout **esperado y comprobado**; el job solo aprueba
si se cumplen las cuatro comprobaciones y la restauración final.

Después de revisar los cambios, el equipo debe hacer commit/push manual a
`auditoria-infraestructura`; el push ejecuta el workflow. Alternativamente,
usar `Run workflow` sobre la rama actualizada. Verificar en **el nuevo commit**:

1. Maven y el gate siguen verdes (124 pruebas, 35 PostgreSQL, ninguna omitida).
2. `Regress PostgreSQL temporary-server readiness and real restoration` aprueba.
3. `Verify complete backup and isolated restoration` aprueba con 16 tablas.
4. `Verify backup including Flyway history from the disposable CI database`
   aprueba con 17 tablas, incluido el historial de migraciones.
5. El artefacto `medlink-postgres-surefire-reports` contiene los XML,
   `backup_ci.md`, `backup_ci_flyway.md` y los Markdown de la regresión.

**Re-run jobs de la ejecución antigua vuelve a usar el commit antiguo** y no
verifica esta corrección. No se hicieron commits, pushes ni cambios remotos
durante este diagnóstico.

## Archivos de esta corrección

- `scripts/backup_restore.ps1`: espera del servidor definitivo y evidencia.
- `.github/workflows/medlink-postgres-integration.yml`: healthcheck TCP,
  regresión y publicación de sus resultados; ambos backups se conservan.
- `scripts/tests/backup_restore_regression.ps1`: regresión real y determinista.
- `AUDITORIA_INFRAESTRUCTURA.md` y `database/README.md`: procedimiento actualizado.
- Este documento y la evidencia de restauración con Flyway.

No se cambiaron módulos Java, reglas de negocio, migraciones, seed, Compose,
dependencias Maven ni las pruebas existentes.
