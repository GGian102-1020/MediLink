# Evidencia Maven y PostgreSQL - 26/10/2026

Rama: `auditoria-infraestructura`, base `ca7d63e`.
Ejecución final: 26/10/2026 12:59:14 (America/Buenos_Aires, UTC-03).
Comando desde backend: `.\mvnw.cmd -B clean verify`.
Resultado: **BUILD SUCCESS; 124 tests, 0 fallos, 0 errores, 0 omitidos; 24 suites**.
PostgreSQL real: **35 tests** (Catálogo 2, Farmacia/Stock 18, Auditoría 15).
Tiempo Maven: 40.857 s. Java: Temurin 21.0.12.1. Maven wrapper: 3.9.16.
Spring Boot: 4.1.1. Docker Engine: 29.9.2. PostgreSQL: 16.15 (postgres:16-alpine).

Se creó un contenedor PostgreSQL exclusivo, publicado solo en localhost con puerto aleatorio.
Las suites compartieron una base nueva mediante MEDLINK_TEST_DB_URL, como en CI.
Flyway aplicó V1/V2/V3 y Hibernate validó las entidades. Las pruebas de adopción
crearon bases UUID separadas, rechazaron baseline implícito y verificaron adopción explícita.
Al finalizar se restauró el backup completo con sus 17 tablas (incluida flyway_schema_history)
y se eliminaron exclusivamente el contenedor de prueba y sus recursos temporales.
Las bases de desarrollo no se modificaron. Evidencia: [backup con Flyway](BACKUP_RESTORE_FLYWAY_2026-10-26.md).

El gate `scripts/verificar_tests_postgres.py` también devolvió PASS para las 24 suites.
El workflow remoto se modificó pero no se ejecutó en GitHub: no hubo push ni PR.

Comprobaciones auxiliares: sintaxis YAML del workflow y sus triggers/7 pasos
validados con SnakeYAML 2.6; sintaxis PowerShell validada con su parser;
`docker compose config --quiet` aprobado; `git diff --check` sin errores.
Se comprobó además que el gate de CI rechaza informes PostgreSQL faltantes,
con tests omitidos, fallos o errores, usando copias temporales de los XML.

## Resultados por suite

Suite | Tests | Fallos | Errores | Omitidos | Segundos
--- | ---: | ---: | ---: | ---: | ---:
ar.edu.uade.medlink.admin.AdministrationServiceTest | 3 | 0 | 0 | 0 | 2.234
ar.edu.uade.medlink.admin.SuspensionPlannerTest | 4 | 0 | 0 | 0 | 0.032
ar.edu.uade.medlink.auditoria.AuditoriaPostgresTest | 15 | 0 | 0 | 0 | 14.063
ar.edu.uade.medlink.auditoria.AuditoriaSecurityTest | 3 | 0 | 0 | 0 | 0.822
ar.edu.uade.medlink.auditoria.AuditServiceTest | 5 | 0 | 0 | 0 | 0.015
ar.edu.uade.medlink.auth.AuthServiceTest | 7 | 0 | 0 | 0 | 0.019
ar.edu.uade.medlink.common.ApiClockTest | 1 | 0 | 0 | 0 | 0.016
ar.edu.uade.medlink.common.error.CorrelationIdFilterTest | 4 | 0 | 0 | 0 | 0.013
ar.edu.uade.medlink.common.error.GlobalExceptionHandlerTest | 4 | 0 | 0 | 0 | 0.004
ar.edu.uade.medlink.common.PageDTOTest | 2 | 0 | 0 | 0 | 0.002
ar.edu.uade.medlink.common.RequestValidationTest | 1 | 0 | 0 | 0 | 0.007
ar.edu.uade.medlink.farmacia.FarmaciaServiceTest | 10 | 0 | 0 | 0 | 0.27
ar.edu.uade.medlink.medicamento.MedicamentoServiceTest | 7 | 0 | 0 | 0 | 0.119
ar.edu.uade.medlink.presentacion.CatalogoPostgresTest | 2 | 0 | 0 | 0 | 1.51
ar.edu.uade.medlink.presentacion.PresentacionRepositoryMappingTest | 1 | 0 | 0 | 0 | 0.004
ar.edu.uade.medlink.presentacion.PresentacionServiceTest | 7 | 0 | 0 | 0 | 0.289
ar.edu.uade.medlink.security.BearerAuthenticationFilterTest | 6 | 0 | 0 | 0 | 0.019
ar.edu.uade.medlink.security.CatalogoSecurityTest | 3 | 0 | 0 | 0 | 0.747
ar.edu.uade.medlink.security.FarmaciaStockSecurityTest | 6 | 0 | 0 | 0 | 0.808
ar.edu.uade.medlink.security.JwtTokensTest | 2 | 0 | 0 | 0 | 0.002
ar.edu.uade.medlink.security.PublicApiRoutesTest | 1 | 0 | 0 | 0 | 0.006
ar.edu.uade.medlink.stock.FarmaciaStockPostgresTest | 18 | 0 | 0 | 0 | 3.652
ar.edu.uade.medlink.stock.StockScopeTest | 1 | 0 | 0 | 0 | 0.086
ar.edu.uade.medlink.stock.StockServiceTest | 11 | 0 | 0 | 0 | 0.206

## Trazabilidad de los informes locales

Los XML originales permanecen en `backend/target/surefire-reports` (directorio de build ignorado).
El log Maven está en `backend/target-auditoria-run.log` (ignorado). No se versionan logs completos
ni dumps para evitar copiar datos o credenciales de una futura ejecución real.
SHA256 del log de esta ejecución: `8a3a674019adce251813cc97f82e28cd0a0ad9d775414c1e48ae26b9e0278a2f`.

Informe XML | SHA256
--- | ---
TEST-ar.edu.uade.medlink.admin.AdministrationServiceTest.xml | 85e9ac7c0ad07b026ba83983120a9ce89127a2fdb81be815fa745d588173a8cc
TEST-ar.edu.uade.medlink.admin.SuspensionPlannerTest.xml | 16cf3a4bf23e6c723d0e7d8166c8b4f13f06ead0335c726a822da8abfaccb91f
TEST-ar.edu.uade.medlink.auditoria.AuditoriaPostgresTest.xml | 497b2b116ce09e5e441ddc5cc09efe55427c5e9557f76324e812e970ac1fdd1c
TEST-ar.edu.uade.medlink.auditoria.AuditoriaSecurityTest.xml | 4b8fba159d5c17e44da2f23b338e22d273cb1dcb7ab75706033e4ee29e690ca3
TEST-ar.edu.uade.medlink.auditoria.AuditServiceTest.xml | 56dfc5cd0a067bd7a626ad0eb655d71c929df40bba94578b1770aa64d85efaaf
TEST-ar.edu.uade.medlink.auth.AuthServiceTest.xml | 43e2e9a343876fd1ecced056324620d83cd51ca3ffc38875f56341c18dba9fc1
TEST-ar.edu.uade.medlink.common.ApiClockTest.xml | 054e4fca36de5d755976518f5eea1c1bf95d0ed86fcb174aabf5fb8d4d47a6e9
TEST-ar.edu.uade.medlink.common.error.CorrelationIdFilterTest.xml | bbf7c67d7be352b6875f65257ab57d99b65259e0e06da563b4100b79a8a3700d
TEST-ar.edu.uade.medlink.common.error.GlobalExceptionHandlerTest.xml | 3feb04f44c770ab120e0f490fd77aa3646aec08cec5e225a5e1500e4223515e4
TEST-ar.edu.uade.medlink.common.PageDTOTest.xml | 8c6297ddae969865ed2e1f7f772e610f04df1d915e125834632d5862412a83a3
TEST-ar.edu.uade.medlink.common.RequestValidationTest.xml | 1f797f72021c43beb2d0e4bfde3697cc07768080a0cf33d981d55a5c74f21d6f
TEST-ar.edu.uade.medlink.farmacia.FarmaciaServiceTest.xml | 699a990dd7427e874ded50f63bfb2047b8c7395e99c1beba68d9808eb34dda72
TEST-ar.edu.uade.medlink.medicamento.MedicamentoServiceTest.xml | 9caa4915d3f0cb42189f6372ffd96b9928b4430a7bc64fae81d3faea39d8fce5
TEST-ar.edu.uade.medlink.presentacion.CatalogoPostgresTest.xml | a0b1511b84aa4f26c6a0baea0a7329a0114b8ebf9c6c4e033e81dcdcd556e398
TEST-ar.edu.uade.medlink.presentacion.PresentacionRepositoryMappingTest.xml | dfcb856186f7c3b2fdd88bb77be11c828c3859fbdfe469fb32e0051c04a9345c
TEST-ar.edu.uade.medlink.presentacion.PresentacionServiceTest.xml | 030cb49faf031316ab9023c0591cf708cbd58b3b5af03ff5b4c95e6dadc3ecb7
TEST-ar.edu.uade.medlink.security.BearerAuthenticationFilterTest.xml | a9215ffff23370557c79657318571355b60ce5b04c7e301f687c964219653412
TEST-ar.edu.uade.medlink.security.CatalogoSecurityTest.xml | 2a178397c05e07dfa00628f3a3d334a8bf0e19df367d3e4282703e79cfd36ae4
TEST-ar.edu.uade.medlink.security.FarmaciaStockSecurityTest.xml | 0e1a868b16aa5f51ff227f90e2751ff9da7af822a5e70a859b0b361979e27fc3
TEST-ar.edu.uade.medlink.security.JwtTokensTest.xml | bdbcfd721924e431dd2954205838a5336eb9bbb1985a4a9bec9762ac9d2ac250
TEST-ar.edu.uade.medlink.security.PublicApiRoutesTest.xml | f1cc3e4732c3ea950da7f79fbb3665e67f663ee9b95f3bc261e726eedf79cada
TEST-ar.edu.uade.medlink.stock.FarmaciaStockPostgresTest.xml | 38e8608d2a63536ac85927444f3254aa6a6d6cd30e30f5eb9b94d8013c32883b
TEST-ar.edu.uade.medlink.stock.StockScopeTest.xml | 343962a5be9c4e67f71867d0dd8bb4b76486203eca0d4ac681a5d99c85accd12
TEST-ar.edu.uade.medlink.stock.StockServiceTest.xml | 8ddc20cd26ee1d75a8cb5041c0d6f8899c157c4affca6e113b6716b84467d1bb

## Ejecuciones previas y correcciones

- Diagnóstico inicial con Docker apagado: 99 tests, 20 omitidos; no se consideró aprobación.
- Primera integración: 122 tests, 1 error de preparación del spy transaccional de Stock; se corrigió el acceso al target del proxy sin quitar assertions.
- Segunda integración por Testcontainers: 122 tests aprobados, 0 omitidos.
- Se agregaron dos verificaciones (cada filtro individual y correlación ausente); la ejecución final anterior contiene los 124 tests.
- Backup de fixture: una comparación textual de zonas horarias falló; verificar_seed.sql ahora compara instantes sin redondear.
- El primer cotejo textual de pg_dump detectó paréntesis CHECK redundantes; se adoptó el deparser canónico PostgreSQL y la restauración completa pasó.
- Las evidencias de restauración exitosas se guardan separadamente; no se ocultaron fallos ni se convirtieron en skips.
