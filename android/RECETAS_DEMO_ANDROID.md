# Mis recetas en Android

La integración vive en `feature/recetas-demo-ia`. El directorio `android/` proviene de `origin/develop-web` (`7efca58`). La variante debug usa la versión `0.2.0-recetas-dev` y el identificador `ar.edu.uade.medilink.android.debug`, de modo que se puede instalar junto al APK publicado.

## Probar con Android Studio

1. Abrí `android/` como proyecto. Sincronizá el Gradle Wrapper con Android SDK 36 y un JDK 17 o superior compatible con Gradle 8.13.
2. Iniciá Spring Boot desde la rama de recetas con V5 y V6 aplicadas, en el puerto `8081`. Para escanear con el proveedor real, configurá `MEDLINK_RECETAS_ESCANEO_ENABLED`, `OPENAI_API_KEY` y `OPENAI_MODEL` en el backend. El ingreso manual funciona con el escaneo deshabilitado.
3. Conectá un teléfono con depuración USB o arrancá un emulador. Ejecutá en PowerShell:

   ```powershell
   $adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
   & $adb devices -l
   & $adb reverse tcp:18081 tcp:8081
   ```

   Con varios dispositivos, agregá `-s <serial>` antes de `reverse`. La variante debug accede a `http://localhost:18081/` dentro de Android.
4. Ejecutá `app` con la variante `debug`. Iniciá sesión con una cuenta `USUARIO` y abrí **Inicio → Accesos rápidos → Mis recetas**.
5. Usá el código de una receta disponible para probar la vinculación manual. Para el escaneo, elegí **Adjuntar receta → Cámara** o **Archivos o galería**. Cargá un PDF de una página, JPG, PNG o WebP de hasta 6 MiB. Usá otra receta disponible para ver una vinculación nueva. El original y la copia alterada de un mismo registro sirven para verificar los resultados desde Android. [Generar los PDF de demo](../docs/RECETAS_DEMO_IA.md#demostración-con-un-pdf-original-y-uno-alterado)
6. Abrí **Ver detalle** para consultar las cantidades. Mantené presionado el código para copiarlo y pegalo en **Código de prescripción demo** en el flujo de disponibilidad.

## API y almacenamiento

| Función | Contrato |
| --- | --- |
| Vinculación | `POST /api/me/recetas-demo/vinculacion`, JSON con `codigoReceta`. |
| Escaneo | `POST /api/me/recetas-demo/escaneo`, multipart con la parte `archivo`. Usa `nueva`, `prescripcion` y el indicador `codigo` de la respuesta. |
| Lista | `GET /api/me/prescripciones?page=0&size=20`, con paginación. |
| Detalle | `GET /api/me/prescripciones/{id}`. |
| Medicamento | `GET /api/presentaciones/{id}` para obtener su nombre. La descripción y los saldos vienen de la prescripción. |

Las solicitudes reutilizan el JWT en memoria. El escaneo tiene 120 segundos de espera de lectura y 180 segundos para la llamada completa. El botón permanece deshabilitado durante la carga y el análisis.

El selector usa Storage Access Framework. Android lee la URI con `ContentResolver` y copia el archivo con un límite de tamaño. La cámara escribe en caché privada a través de `FileProvider`. La app orienta y reduce capturas JPEG grandes para ajustarlas al límite de resolución del backend. Los documentos temporales se eliminan al quitar o reemplazar el adjunto, completar el escaneo o salir de la pantalla. Los temporales que sobrevivan un cierre abrupto se limpian al volver a entrar después de 24 horas.

## Verificación y ramas

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

En Windows pasaron 28 pruebas unitarias y el build debug. En un emulador API 37 pasaron las dos pruebas instrumentadas de URI de cámara y selección. Con una API simulada también se verificaron el inicio de sesión, la navegación, la cancelación del selector, el envío de un PDF desde Descargas, la vista previa de PNG y de una captura de la cámara virtual y la limpieza del archivo adjunto. El ensayo con la cámara de un teléfono y con el proveedor real queda pendiente.

La futura integración de backend necesita reconciliar `V5__recetas_demo.sql` de la rama de recetas y `V5__usuario_datos_personales.sql` de `develop-web`. Ambas ocupan la misma versión Flyway. La estrategia tendrá que considerar qué V5 tiene aplicada cada base antes de combinar ambas ramas. Algunas funciones de perfil y registro de `develop-web` dependen de su backend de datos personales. Para la primera prueba de recetas, usá una cuenta de paciente existente en el backend de la rama de recetas.
