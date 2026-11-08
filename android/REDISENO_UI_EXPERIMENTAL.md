# MediLink Android — actualización visual experimental

Este incremento se aplica **después** de `0003-experiment-android-full-parity.patch`
en la rama `experiment/android-full-parity`. No se aplica sobre `main` ni sobre
`feat/android-compose-mvp` sin la ampliación previa.

## Objetivo

Transformar el aspecto de prototipo de la app multirol en una interfaz móvil
consistente y accesible, preservando **todos los flujos, roles y endpoints**.

- Sistema visual centralizado en `ui/theme/MediLinkTheme.kt`, compatible con
  modo claro/oscuro y sin dependencias visuales nuevas.
- Componentes reusables para portadas, métricas, tarjetas de acceso, estados,
  avisos y datos de perfil (`ui/screens/DesignComponents.kt`).
- Inicio de administración y operador con jerarquía visual, indicadores y
  acciones principales; menú de gestión en tarjetas con iconos.
- Catálogo con búsqueda y tarjetas de medicamento, presentaciones y stock.
- Perfil con identidad, estado de cuenta, datos, seguridad y cierre de sesión.
- Reservas con filtros visibles, estado destacado y botones de detalle/cancelación.
- Registro/login y pantallas de farmacia con tarjetas y mensajes legibles.
- Barra de navegación y barra superior acordes al tema; las pantallas secundarias
  incluyen navegación hacia atrás y priorizan el espacio de contenido.

## Invariantes

- No se cambian `data/`, ViewModels, DTOs, roles ni rutas REST.
- La lógica de retiro, cancelación operativa, idempotencia y manejo del stock
  sigue en el backend original.
- No se agregan permisos, proveedores de mapas, claves ni secretos.
- Se conserva la conexión de desarrollo por `adb reverse`.

## Matriz visual de aceptación

En un emulador compacto (ancho ~360 dp) y uno de tamaño medio, probar:

1. ADMINISTRADOR: Inicio con 3 métricas; Gestión -> todos los módulos; formulario
   de farmacia, medicamento y operador; auditoría y prescripciones.
2. OPERADOR: Inicio, reservas, inventario y movimientos; confirmar retiro y
   cancelar una reserva de demostración con motivo.
3. USUARIO: inicio personal, catálogo, presentaciones, disponibilidad, creación
   de reserva, Mis reservas, detalle, cambio de contraseña y logout.
4. INVITADO: catálogo y registro; acciones protegidas solicitan autenticación.
5. Búsqueda con teclado abierto: al pulsar la acción Buscar, el teclado se oculta.
6. Rotación y contraste claro/oscuro, mensajes de API caída y scroll con tamaños
   de fuente aumentados; verificar que no se oculten acciones importantes.

Usar datos de demostración. La compilación y las pruebas unitarias locales deben
pasar antes de crear un commit o abrir una Pull Request experimental. No fusionar
la rama con `main` por el mero éxito de Gradle.
