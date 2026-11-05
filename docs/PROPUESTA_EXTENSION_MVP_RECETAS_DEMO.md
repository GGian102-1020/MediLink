# Propuesta de extensión para recetas de demostración

Propuesta para revisión del equipo. La especificación normativa MVP v1.4.3 sigue siendo la referencia para stock, reservas, seguridad y prescripciones.

| Regla propuesta | Criterio de aceptación |
| --- | --- |
| RD-01. Un administrador puede emitir lotes de recetas ficticias sin titular. | Un lote de 30 para una presentación activa BAJO_RECETA produce 30 filas disponibles, códigos distintos y una unidad autorizada por receta. |
| RD-02. El PDF imprime el documento persistido con una advertencia visible. | Se descarga una página A4 por receta, el QR reproduce su código y el documento indica su uso académico. |
| RD-03. Un visitante autenticado puede vincular el código vigente a su usuario. | La base registra una prescripción con titular e ítem, una asignación y auditoría en la misma transacción. Una falla revierte todo. |
| RD-04. El reclamo pertenece a una cuenta y es idempotente para el titular. | Un reintento devuelve la prescripción original. Otro usuario obtiene el mismo 404 que para un código inexistente. En dos reclamos concurrentes solo hay un titular. |
| RD-05. El escaneo ayuda a transcribir el documento. | La IA devuelve una lectura. Una lectura incompleta, discrepante o con un error del proveedor deja la receta disponible y la base sin cambios. |
| RD-06. La prescripción se consume mediante las reservas del MVP. | Una unidad comprometida impide una segunda reserva. Cancelación o vencimiento liberan el compromiso. El retiro consume la unidad y bloquea su reutilización. |
| RD-07. Cada usuario consulta sus prescripciones. | La lista y el detalle muestran las propias. Un detalle ajeno e inexistente comparten la respuesta 404. |
| RD-08. La demo puede hacerse con códigos impresos sin un proveedor externo. | La generación, vinculación manual, consulta y reserva funcionan con el escaneo apagado. El FAQ usa respuestas fijas. |

Las recetas impresas tienen datos ficticios. Cualquiera que obtenga un código antes del primer reclamo puede intentar usarlo. El titular se determina al vincular y la cuenta puede acceder desde distintos dispositivos.
