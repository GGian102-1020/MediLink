# Revisión final del código implementado frente al documento maestro v1.4.3

**Fuente normativa única:** *Medlink — Especificación Maestra del MVP.pdf*, versión 1.4.3.
Este informe registra diferencias **contra el ZIP original `MediLink-main.zip`**.
No constituye una segunda especificación ni declara terminadas las capacidades futuras.

## Alcance

Se revisaron exclusivamente Cuentas y autenticación, Farmacias y Stock, esquema y
fixtures PostgreSQL, infraestructura Docker/Flyway y documentación técnica. No se
implementaron Catálogo, creación/consulta/cancelación/retiro de Reservas, validación
Rx, scheduler general ni frontend. No se cambió el PDF.

## Cambios respecto del original

| Archivo / componente | Qué cambió | Motivo |
| --- | --- | --- |
| `database/02_datos_prueba.sql` | Reserva 9 de Sofía pasa de `CANCELADA` a `VENCIDA` y su suspensión se inicia en el tercer vencimiento | RN-31..36: 3 vencimientos computables en 30 días; cancelaciones no penalizan; suspensión 7 días |
| `database/02_datos_prueba.sql` | 8 filas nuevas de `movimiento_stock`: confirmación de reservas 5, 7, 8, 9 y 10, más vencimientos 7, 8 y 9 | RN-05: descontar al confirmar y restituir exactamente una vez al vencer. No restituir al retirar ni por `PRODUCTO_NO_APTO_PARA_ENTREGA` |
| `database/02_datos_prueba.sql` | 2 auditorías `CONFIRMAR_RETIRO` nuevas de reservas 3 y 5; auditoría de creación de suspensión sin puntos y con fechas tomadas de `suspension` ya persistida | RN-44/RN-50 y CV-02: trazabilidad y precisión de milisegundos |
| `scripts/verificar_seed.sql` (nuevo) | Comprobaciones de la **base de prueba recién inicializada**: umbral/fechas de suspensión, cantidad y unicidad de descuentos y restituciones, continuidad de saldos y auditorías | Detectar regresiones de la fixture conforme RN-05, RN-31..36 y RN-44; no aplica a una base operativa con datos adicionales |
| `security/BearerAuthenticationFilter.java` | Se limita el `catch` al chequeo de autenticación; fallos de controladores o servicios posteriores se propagan; se limpia el contexto de seguridad con `finally` | SEC-05/SEC-06 y tratamiento de errores HTTP; impedir que un fallo posterior se presente como `INVALID_TOKEN` |
| `common/error/GlobalExceptionHandler.java` | Se registran datos técnicos seguros ante fallos inesperados y se elimina el `400 VALIDATION_ERROR` indiscriminado por excepciones de integridad. **No se traduce toda violación única `23505` a `INVALID_STATE_TRANSITION`**: los conflictos funcionales conocidos deben mapearse en los servicios a los códigos de 11.9; un fallo de BD sin clasificación conocida se informa genéricamente como 500 | Secciones 11.1/11.2/11.9, RNF-10/13; evitar inventar códigos funcionales y exponer detalles internos |
| `common/error/CorrelationIdFilter.java` | Se introduce el `correlationId` en MDC durante el request y se limpia al terminar | RNF-13, 11.1 |
| `docker-compose.yml` | Publicación de puertos de PostgreSQL y Adminer restringida a `127.0.0.1` | Menor exposición de servicios de desarrollo; sin modificar tráfico interno de Compose |
| `.gitignore` | Exclusión de backups `*.dump` y `*.backup` | Evitar exponer accidentalmente datos precargados o reales al hacer commits |
| `.env.example`, `Readme.md`, `database/README.md`, `CUENTAS_AUTENTICACION.md` | Se documenta la adopción deliberada del esquema preexistente con Flyway, el inicio por HTTPS, backups y restauración de prueba (fuera del repositorio), y se sincroniza el estado de los módulos | RNF-12, AC-59, secciones 10/13/16; documentación verificable, sin introducir nuevas reglas de producto |
| `FARMACIA_STOCK.md` (nuevo) | Documentación técnica de un módulo existente que estaba enlazado pero faltaba del original | Hacer legibles rutas, restricciones y pruebas; documento informativo, no normativo |
| Pruebas Java | Nuevos tests de manejo de errores y correlation ID; test de propagación de excepciones posteriores al filtro JWT. Se verifica que `23505` no se convierta en `INVALID_STATE_TRANSITION` y que el conflicto conocido mantenga `EMAIL_ALREADY_EXISTS` | Evitar la regresión introducida en una versión intermedia del parche |
| `backend/mvnw` | Permiso ejecutable en ZIP para entornos Unix; sin cambiar su contenido | Ejecución del wrapper en Linux/macOS |

## Corrección importante respecto de la revisión intermedia

La revisión anterior introducía una **traducción errónea**: cualquier SQLSTATE `23505`
se convertía en `409 INVALID_STATE_TRANSITION`. Es incorrecto porque la especificación
reserva códigos distintos para correo duplicado, presentación duplicada y otros
conflictos. **La versión final ya no contiene ese mapeo global** y corrigió su prueba.
Las violaciones que la aplicación conozca deben traducirse en la capa que conoce
el caso de uso; las no reconocidas no se disfrazan de una transición funcional.

## Decisiones de conservación

No se alteraron `database/01_medlink_esquema.sql`, la migración Flyway V1, el
algoritmo `SuspensionPlanner`, los locks de Stock ni el diseño JWT/BCrypt.
La mayoría de los cambios se limita a fixtures, errores HTTP, configuración de
desarrollo, pruebas y documentación. El procedimiento de Flyway **no activa**
`baseline-on-migrate` automáticamente para bases de procedencia desconocida.

## Diferencias preexistentes que requieren una decisión normativa

- La implementación aplica 72 **bytes UTF-8** además de 72 caracteres para BCrypt.
  El documento define 72 caracteres; no son iguales con ciertos caracteres Unicode.
  No se suprimió una protección criptográfica sin una solución aprobada.
- Se usa `INTERNAL_ERROR` para fallos inesperados de servidor, pero el catálogo de
  la sección 11.9 no lo enumera explícitamente. Es una ambigüedad preexistente y
  no se modificó silenciosamente el PDF.
- Cuentas/Administración utilizan `JdbcTemplate` mientras sección 13 prescribe
  Spring Data JPA/Hibernate. Se mantuvo la decisión técnica actual; no se ha
  demostrado que la coexistencia sea errónea, pero no se declara conformidad total.

## Qué se verificó y qué queda pendiente

- Se contrastaron el ZIP original y los archivos modificados; no se eliminaron
  archivos del original y el esquema/Flyway V1 permanecen intactos.
- Verificación estática de movimientos y escenarios de la fixture; las
  comprobaciones SQL incluidas son **para ejecutar**, no evidencia de ejecución.
- Se compiló y ejecutó `SuspensionPlanner.java` de forma aislada con Java 21:
  2 vencimientos devolvieron 0 suspensiones; 3 vencimientos dentro de 30 días
  devolvieron una suspensión de 7 días que comienza en el tercer vencimiento.
- **No se ejecutó la suite completa** de Maven, las pruebas reales PostgreSQL/
  Testcontainers ni el despliegue/restauración Docker en este entorno, donde no
  hay Maven/PostgreSQL/Docker disponibles y no hay acceso al repositorio externo.
- Antes del merge, ejecutar las pruebas y verificar que
  `FarmaciaStockPostgresTest` no quedó omitida; ejecutar el smoke HTTP y probar
  una restauración completa de un backup en una base separada conforme AC-59.
- `scripts/verificar_seed.sql` debe ejecutarse únicamente sobre una instancia
  recién inicializada con los scripts 01 y 02, no sobre datos de producción.

**Advertencia sobre bases existentes:** cambiar el SQL de `02_datos_prueba.sql` no
actualiza automáticamente un volumen PostgreSQL ya creado. No borrar el volumen
para aplicar una corrección sin antes respaldarlo y acordar el procedimiento de
migración de datos. Tampoco copiar `.env` o dumps locales al ZIP ni al commit.
