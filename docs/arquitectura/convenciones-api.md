# Convenciones de la API REST

**SIGLIM · Sistema de Gestión de la Liga Municipal de Futbol Amateur Morelia**  
Versión 5.33 · 27 de noviembre de 2026 · Alineado con el [ERS Rev. 0.4.1](../requisitos/ERS.md), la
[arquitectura v2.1](arquitectura-y-diseno-jerarquico.md) y el
[diccionario de datos v2.0](../base-de-datos/diccionario-de-datos.md)

> [!NOTE]
> **Estado: [PROPUESTA] del equipo.** El ERS deja abiertos los protocolos y formatos de las
> interfaces internas (§3.1.4) y los textos de error (PEN-016). Este documento fija el contrato entre
> el frontend y el backend para que ninguno improvise. Un cambio al contrato se hace por PR revisado
> por quien programa ambos lados. La versión 1.0 (alcance de la Rev. 0.3) está en
> [`historico/`](historico/convenciones-api-v1.0-rev0.3.md).

## Control del documento

| Versión | Fecha | Cambios |
| --- | --- | --- |
| 1.0 | 7 de octubre de 2026 | Reglas generales, errores, paginación y portal del alcance de la Rev. 0.3. |
| 2.0 | 20 de octubre de 2026 | Alcance de la Rev. 0.4.1: 14 módulos, permisos `modulo.recurso:ACCION` con ámbito, códigos HTTP por tipo de regla, traducción de errores de PostgreSQL, catálogo de recursos por módulo, documentos en AWS S3, pago simulado, notificaciones y portal sobre las vistas `v_publica_*`. |
| 2.1 | 30 de octubre de 2026 | H-07.1: §6.6 con el detalle de una cancha, el permiso de consulta de la disponibilidad, la ocupación por rango y el `409` aceptable `RF-030`; §3.1 con las extensiones del problema. |
| 2.2 | 31 de octubre de 2026 | H-21.1: §6.1 con el detalle de la consulta de auditoría y `GET /auditoria/opciones-filtro`. |
| 2.3 | 2 de noviembre de 2026 | H-04.1: §6.2 con el contrato de temporadas, la lista de verificación de activación y los errores de activación y cierre. |
| 2.4 | 3 de noviembre de 2026 | B-27.1: §6.11 con el contrato de comunicados, el dictamen del Presidente y la separación entre redactar y aprobar. |
| 2.4 | 3 de noviembre de 2026 | H-06.1: §6.3 con el contrato de clubes y equipos (sin `PATCH /clubes/{id}`), sus errores y acciones de auditoría; §2.1 con la baja de un equipo. |
| 2.5 | 3 de noviembre de 2026 | H-26.1: §6.9 con las rutas completas de los catálogos disciplinarios, el filtro `activo`, la baja lógica y los valores por tipo de sanción. |
| 2.6 | 3 de noviembre de 2026 | H-26.1: §6.9 con el rango numérico de `partidosSuspension`, `puntos` y `montoMulta` según sus columnas. |
| 2.7 | 4 de noviembre de 2026 | B-02.1: §6.2 con el contrato de la convocatoria (`PUT /temporadas/{id}/convocatoria`), su reemplazo completo, sus errores y la auditoría; la temporada incluye `convocatoria` cuando existe. |
| 2.8 | 4 de noviembre de 2026 | B-11.1: §6.6 con el contrato de sedes (`CONSULTAR` en el `GET`, `EDITAR` en `POST` y `PATCH`, lista sin paginar), el filtro `sedeId` de `GET /canchas`, `sede` en la cancha, `quitarSede` y el `422 REFERENCIA_INVALIDA` de una sede inexistente. |
| 2.9 | 4 de noviembre de 2026 | B-12.1: §6.6 con el registro de `MANTENIMIENTO` (RF-031) como bloqueo tipificado: motivo obligatorio, mismo `409 RF-030` aceptable sobre partidos, traslape libre y acción `REGISTRAR_MANTENIMIENTO`. |
| 3.0 | 5 de noviembre de 2026 | H-03.1: §6.1 con el contrato de usuarios, invitación, activación/desactivación, asignaciones y catálogo de roles; §2.1 con la activación de una cuenta. |
| 3.1 | 5 de noviembre de 2026 | H-05.1: §6.2 con el contrato del catálogo de categorías (jerarquía sin ciclos, desactivación, reactivación solo con superior activa o sin ella, y reordenamiento atómico), las categorías de la temporada con sus grupos anidados y los grupos; `temporadas.temporada:CONSULTAR` en los `GET` de categorías y grupos (antes pedía `EDITAR`); errores y acciones de auditoría. |
| 3.2 | 5 de noviembre de 2026 | H-05.2: §6.2 con el contrato de parámetros versionados (juego completo por versión, `esDemo` por versión con `camposDemo`, criterios de desempate sin nombre de equipo, filas por categoría, historial con `camposCambiados`, valores predeterminados y `GET` por versión); `temporadas.temporada:CONSULTAR` en los `GET` de parámetros (antes pedía `temporadas.parametros:EDITAR`); errores y la acción `REGISTRAR_PARAMETROS`; §11 con la confirmación parcial de valores oficiales. |
| 3.3 | 5 de noviembre de 2026 | H-05.3: §6.2 documenta `GET /temporadas/{id}`, que usa F-11. |
| 3.4 | 5 de noviembre de 2026 | DEF-04 (SCRUM-399): §6.1 aplica VAL-022 a las asignaciones y al estado de la propia cuenta (DEC-53); §3 lo cita entre los 403. |
| 3.5 | 5 de noviembre de 2026 | DEF-03 (SCRUM-398): §6.1 precisa que la invitación envía `redirect_to` a `/restablecer-contrasena` del frontend, que `409 DUPLICADO` también cubre un correo con cuenta en Supabase Auth (`email_exists`) y que cualquier otro rechazo de Supabase Auth responde `503`. |
| 3.6 | 5 de noviembre de 2026 | H-29.1: §6.13 con el contrato de cobros manuales (`POST /cobros`) y de la consulta de cobros y adeudos (`GET /cobros`, `GET /adeudos`), sus filtros, montos `BigDecimal`, errores y acciones de auditoría. |
| 3.7 | 6 de noviembre de 2026 | H-31.1: §6.13 con el contrato del pago externo con comprobante (`POST /cobros/{id}/pagos`, `multipart/form-data`) y de la consulta de pagos e intentos (`GET /cobros/{id}/pagos`), su orden de validación, errores y auditoría (DEC-56). |
| 3.8 | 6 de noviembre de 2026 | H-30.1: §6.13 con el contrato del pago en línea simulado (`POST /cobros/{id}/intentos-pago` y `POST /intentos-pago/{id}/confirmacion`), la referencia ficticia, la confirmación atómica, sus errores, auditoría y evento `CobroPagado` (RF-138, VAL-028, RNF-004, ADR-0012). |
| 3.9 | 7 de noviembre de 2026 | B-03.1: §6.2 con el contrato de las interpretaciones de regla (RF-007): alta en `PENDIENTE` con la autoría de la sesión, listado con paginación y filtros, resolución por sub-recurso como acción de estado, la separación de funciones de DEC-57, las observaciones del rechazo de DEC-58 y la temporada cerrada de DEC-59; auditoría `REGISTRAR_INTERPRETACION` y `RESOLVER_INTERPRETACION`. |
| 4.0 | 7 de noviembre de 2026 | B-31.1: §6.13 aclara que el estado `VENCIDO` lo fija la tarea programada de vencimiento (RF-141, DEC-60) y ningún endpoint. |
| 4.1 | 7 de noviembre de 2026 | B-04.1: §6.3 con el contrato del cambio de nombre de club (`POST /clubes/{id}/cambio-nombre`), su orden de errores, el traslado de asignaciones de rol, los cobros que se quedan en el club anterior y la auditoría `CERRAR_CLUB` + `REGISTRAR_CLUB` (DEC-49, DEC-61); el club incluye `clubAnterior` cuando existe. |
| 4.2 | 7 de noviembre de 2026 | H-20.1: §6.1 con el contrato de la matriz de permisos (`GET /roles/{id}`, `GET /permisos` y `PATCH /roles/{id}/permisos` con `200`/`202`), el listado de solicitudes y su dictamen, los errores de VAL-022, VAL-023 y RF-096 y las acciones de auditoría `CAMBIAR_PERMISOS_ROL`, `SOLICITAR_CAMBIO_PERMISOS`, `APROBAR_CAMBIO_PERMISOS` y `RECHAZAR_CAMBIO_PERMISOS`. |
| 4.3 | 8 de noviembre de 2026 | H-22.1: §6.3 documenta la consulta, registro pendiente y autorización de cargos de directiva con vigencias consecutivas (RF-102). |
| 4.4 | 8 de noviembre de 2026 | H-22.2: §6.3 documenta la consulta de una persona por CURP que usa F-15 para identificar al titular de un cambio de directiva, con el permiso en cualquier ámbito y `Cache-Control: no-store` (RF-010, RF-011, RF-102). |
| 4.5 | 10 de noviembre de 2026 | B-13.1: §6.6 con `canchas.cancha:CONSULTAR` en el `GET` de horarios de club y `EDITAR` en el `PUT`; §6.6.1 con el contrato de `GET`/`PUT /canchas/{id}/horarios-club` (reemplazo atómico por temporada, reglas por tipo de cancha de DEC-33, traslapes en el mismo día), su orden de errores y la acción `REGISTRAR_HORARIOS_CANCHA`; §3.2 con `RF-119` en `422`. |
| 4.6 | 11 de noviembre de 2026 | DEC-63 y DEC-68: §11 quita los textos de los mensajes de error (PEN-016) y el proveedor de correo (PEN-034), ya resueltos; §6.6 cita DEC-64 como regla final del bloqueo de cancha sobre partidos (PEN-007). |
| 4.7 | 11 de noviembre de 2026 | H-09.1: §6.1 con el aviso de privacidad vigente (`GET /avisos-privacidad/vigente`) y el registro del consentimiento (`POST /personas/{id}/consentimientos`), su orden de errores y la acción `REGISTRAR_CONSENTIMIENTO`; §6.3 con el alta y la edición de personas (`POST`/`PATCH /personas`) y la tutela (`POST /personas/{id}/tutelas`), sus órdenes de errores y las acciones `REGISTRAR_PERSONA`, `EDITAR_PERSONA` y `REGISTRAR_TUTELA`; la consulta por CURP de H-22.2 usa `persona_por_curp` (DEC-78 a DEC-84). |
| 4.8 | 11 de noviembre de 2026 | H-09.2: §6.4 con el contrato del alta de un registro de jugador (`POST /registros-jugador`): permiso sobre el club del equipo, orden de errores (`RF-003`, `RF-013`, `RF-101`, `REFERENCIA_INVALIDA`, `VAL-017` con `club` y `equipo`, `VAL-026`, `RN-039` y `VAL-025` con `requisito`), fecha de alta en `America/Mexico_City`, respuesta `201` sin `Location` y la acción `REGISTRAR_REGISTRO_JUGADOR` (DEC-85 a DEC-90). |
| 4.9 | 11 de noviembre de 2026 | H-11.1: §6.5 con el contrato de árbitros (`GET`/`POST /arbitros` y `POST /arbitros/{id}/validacion`), el estado `PROPUESTO`, la validación y el rechazo con observaciones, el `409 DUPLICADO` que solo nombra el club (DEC-88) y el `INSERT … ON CONFLICT DO NOTHING` de las carreras (DEC-90); permisos `CONSULTAR`/`PROPONER`/`VALIDAR` y auditoría `PROPONER_ARBITRO`, `VALIDAR_ARBITRO` y `RECHAZAR_ARBITRO`. |
| 5.0 | 12 de noviembre de 2026 | H-10.1: §6.4 y §7 con los documentos de un registro: carga (`POST /registros-jugador/{id}/documentos`), reemplazo (`POST /documentos/{id}/reemplazo`, solo de `PENDIENTE` o `RECHAZADO`), requisitos (`GET /registros-jugador/{id}/requisitos`), enlace prefirmado (`GET /documentos/{id}/enlace`) y validación de un documento (`POST /documentos/{id}/validacion`), con sus permisos y ámbitos, órdenes de errores, `201` sin `Location`, `Cache-Control: no-store` y las acciones de auditoría `CARGAR_DOCUMENTO`, `REEMPLAZAR_DOCUMENTO`, `VALIDAR_DOCUMENTO` y `CONSULTAR_DOCUMENTO`; §3.2 con `RF-109` en `409` y `VAL-015` en `403` (DEC-91 a DEC-96). |
| 5.1 | 12 de noviembre de 2026 | H-10.2: §6.4 con la validación de un registro (`POST /registros-jugador/{id}/validacion`): permiso `registros.registro:VALIDAR` sobre el club del equipo, orden de errores, `VAL-022` **[PROPUESTA]** para quien dio de alta el registro, RF-013, `422 VAL-001` con la extensión `faltantes`, VAL-025, `UPDATE` condicional con el registro bloqueado, respuesta `200` con `validadoPor`, `validadoEn` y `observaciones`, motivo del historial y auditoría `VALIDAR_REGISTRO_JUGADOR`; el alta muestra los tres campos nuevos en `null`; §2.1 con las observaciones opcionales al aprobar; §3.2 con `VAL-001` en `422` y `VAL-015` también para registros (DEC-97 a DEC-102). |
| 5.2 | 12 de noviembre de 2026 | H-09.3: §6.3 con `esMenor` en `GET /personas?curp=` (DEC-104). |
| 5.3 | 12 de noviembre de 2026 | H-10.5: §6.4 con la consulta de registros: `GET /registros-jugador` (filtros `estado`, `temporadaId`, `clubId`, `equipoId`; orden `creadoEn,asc`, `creadoEn,desc` o `nombre,asc`; ámbito en el `WHERE` y en el `COUNT`; filas con la CURP enmascarada, sin avance de documentos) y `GET /registros-jugador/{id}` (detalle con `esMenor`, tutores vigentes y la CURP completa solo con `organizacion.persona:CONSULTAR` sobre el club), ambos con `Cache-Control: no-store` y su orden de errores; el alta responde `201` con `Location`; §9.2 cita DEC-110 como la aplicación del enmascarado en M-04 (DEC-109, DEC-110). |
| 5.4 | 12 de noviembre de 2026 | H-10.3: §7 precisa que `GET /registros-jugador/{id}/documentos` sigue fuera porque la página de documentos y la bandeja de validación no lo usan: los documentos vigentes vienen en `GET /registros-jugador/{id}/requisitos` (DEC-96, DEC-112). |
| 5.5 | 14 de noviembre de 2026 | H-23.1: §6.4 con la baja de un registro (`POST /registros-jugador/{id}/baja`): permiso `registros.registro:EDITAR` sobre el club del equipo, motivo obligatorio de 10 a 500 caracteres (`400 RN-021`), solo desde `ACTIVO` con un `409 RF-017` por estado de origen, `409 RF-003` con la temporada cerrada, `UPDATE` condicional con el registro bloqueado, `baja_en` en `America/Mexico_City`, motivo solo en el historial, respuesta `200` con `bajaEn` y `motivoBaja` y auditoría `DAR_BAJA_REGISTRO_JUGADOR`; el alta y el detalle muestran los dos campos nuevos (`null` salvo en `BAJA`) (DEC-118 a DEC-124). |
| 5.6 | 14 de noviembre de 2026 | B-05.1: §6.3 con el contrato de la venta y la conservación de plaza (`POST /operaciones-equipo` y `POST /operaciones-equipo/{id}/resolucion`), la conservación como operación independiente (RF-104), el dictamen del Presidente, las reglas del club destino, los permisos `organizacion.venta:SOLICITAR`/`APROBAR` y las acciones `SOLICITAR_OPERACION_EQUIPO` (con las observaciones de la solicitud), `AUTORIZAR_OPERACION_EQUIPO` y `RECHAZAR_OPERACION_EQUIPO`; una sola operación vigente por equipo y tipo (`409 RF-103`/`RF-104`, DEC-132); autorizar sin nota conserva las observaciones y revalida la temporada y el club destino; §2.1 con la transición. |
| 5.7 | 15 de noviembre de 2026 | B-05.2: §6.2 documenta el efecto del cierre de la temporada sobre las operaciones de equipo: en la misma transacción, las `AUTORIZADA` pasan a `APLICADA` con `aplicadaEn`, la venta mueve el equipo a su club destino y las `SOLICITADA` no se aplican; cada aplicación se audita como `APLICAR_OPERACION_EQUIPO` y `CERRAR_TEMPORADA` registra `operacionesAplicadas` (RF-103, RF-104, RN-040). |
| 5.8 | 16 de noviembre de 2026 | H-13.1: §6.7 con el envío a aprobación (`POST /jornadas/{id}/envio`, `409 RF-039` con la extensión `conflictos`, `409 RF-121` sin partidos vigentes) y el dictamen (`POST /jornadas/{id}/resolucion`) con la separación de funciones (`403 RF-121` si quien dictamina creó o envió la jornada o tiene también `programacion.jornada:EDITAR`), la devolución con observaciones (`400 RF-121`), los campos de publicación del detalle y la auditoría `ENVIAR_JORNADA_A_APROBACION`, `APROBAR_JORNADA` y `DEVOLVER_JORNADA`; §6.11 con el contrato de `GET /publicaciones/pendientes`. |
| 5.9 | 16 de noviembre de 2026 | B-05.3: §6.3 con la consulta de operaciones de equipo que necesita B-09: `GET /operaciones-equipo` (filtro `temporadaId`/`estado`, paginación, alcance unificado de `SOLICITAR`/`APROBAR` en el `WHERE` y el `COUNT`), `GET /operaciones-equipo/{id}` (404 fuera de ámbito) y `GET /operaciones-equipo/contexto` (clubes `ACTIVO` de la liga para elegir el destino de una venta); `POST` responde `201` con `Location`. |
| 5.10 | 16 de noviembre de 2026 | B-08.1: §6.5 con el contrato de `GET /clubes/{id}/cuota-arbitros?temporadaId=` (equipos activos, árbitros `VALIDADO`, redondeo hacia arriba y `cumple`), su ámbito de club, errores y la temporada activa por omisión; §6.2 con el parámetro `equiposPorArbitro` de la temporada (4 **[DEMO]**, DEC-72), su herencia de la versión vigente cuando se omite y su inclusión en `camposDemo` (RF-114, DEC-17). |
| 5.11 | 16 de noviembre de 2026 | B-15.1: §6.7 con las advertencias de localía (VAL-027): las reglas de la cancha del local (DEC-33, DEC-34) y de las últimas fechas en la unidad deportiva (RN-049), el `409 VAL-027` con `aceptable` y `advertencias` al registrar o ajustar un partido sin `motivoAceptacion`, el motivo guardado en `motivoLocalia`, su conservación en los ajustes, la lista `advertencias` de la verificación y la auditoría `ACEPTAR_LOCALIA`; §6.2 con los parámetros `fechasFaseRegular` (sin default) y `fechasFinalesUnidadDeportiva` (10 **[DEMO]**), heredados de la versión vigente cuando se omiten. |
| 5.12 | 16 de noviembre de 2026 | B-29.1: §6.13 con el contrato de tarifas (`GET /tarifas?temporadaId=` sin paginar, `POST /tarifas` y `PATCH /tarifas/{id}` solo del monto): una tarifa por temporada, categoría y concepto (`409 DUPLICADO`), monto mayor o igual a cero, categoría asignada a la temporada (`422 REFERENCIA_INVALIDA`), temporada cerrada de solo lectura (`409 RF-003`), `esDemo` siempre `true` mientras siga PEN-042 y auditoría `REGISTRAR_TARIFA` y `EDITAR_TARIFA` (RF-135). |
| 5.13 | 17 de noviembre de 2026 | B-15.2: §6.7 aclara que un `PATCH /partidos/{id}` que solo trae `motivoAceptacion` registra la aceptación de la advertencia de localía sin cambiar equipos, cancha ni inicio (VAL-027), y la audita como `ACEPTAR_LOCALIA`; si el partido ya respeta la localía, no escribe. |
| 5.14 | 17 de noviembre de 2026 | B-29.2: §6.13 con `GET /tarifas/contexto`: temporadas de la liga (de la más reciente a la más antigua) con su estado y sus categorías en el orden del catálogo, autorizado con `tesoreria.tarifa:EDITAR` porque el Tesorero no tiene `temporadas.temporada:CONSULTAR`, como los contextos de afiliaciones y de operaciones de equipo. |
| 5.15 | 17 de noviembre de 2026 | B-09.1: §6.5 con el contrato de la disponibilidad arbitral (`GET /arbitros/{id}/disponibilidad?desde=&hasta=`, `POST` y `DELETE …/{disponibilidadId}`): intervalos `[desde, hasta)`, `disponible` opcional con `true` por omisión, solo árbitros `VALIDADO`, borrado físico porque no es historial deportivo, permiso `arbitraje.disponibilidad:EDITAR` sobre la persona del árbitro (Comisión en `LIGA`, árbitro en `PROPIO`) y auditoría `REGISTRAR_DISPONIBILIDAD_ARBITRAL`/`ELIMINAR_DISPONIBILIDAD_ARBITRAL` (RF-022). |
| 5.16 | 17 de noviembre de 2026 | B-30: §6.7 indica que publicar la jornada emite `JornadaPublicada`; §6.13 con «Cobros automáticos al publicar la jornada» (RF-136): cobros `ARBITRAJE` y `CANCHA` a cada equipo con la tarifa de la temporada y la categoría, sin cobro de cancha al dueño de la cancha (DEC-33), omisión auditada sin tarifa o con tarifa en cero, periodo configurable (`dias-para-pagar`), idempotencia por `ux_cobros_partido` y auditoría `GENERAR_COBRO` y `GENERAR_COBROS_JORNADA`. |
| 5.17 | 17 de noviembre de 2026 | B-06.1: §6.4 con la renovación y la transferencia entre temporadas (`POST /registros-jugador/renovaciones` y `/transferencias`): `{ personaId, equipoId }`, registro nuevo `PENDIENTE_VALIDACION` enlazado por `registro_anterior_id`, `422 RF-015`/`RF-016` si el registro anterior no existe o no corresponde al tipo de movimiento, `409 RF-111` si la temporada no permite el cambio de club, `409 VAL-026`/`RN-039` dentro de la temporada, documentos aprobados heredados solo en la renovación del mismo club y auditoría `RENOVAR_REGISTRO`/`TRANSFERIR_REGISTRO`; §3.2 con `RF-015` y `RF-016` en `422` y `RF-111` en `409` (DEC-146). |
| 5.18 | 17 de noviembre de 2026 | B-07.1: §6.4 con el historial de un registro (`GET /registros-jugador/{id}/historial`): arreglo sin paginar del más antiguo al más reciente con `estadoAnterior`, `estadoNuevo`, `motivo`, `cambiadoPor { id, nombre }` y `cambiadoEn`; `registros.registro:CONSULTAR` sobre el club del registro (otro club, `404`, VAL-024; `PROPIO` queda para H-24), `Cache-Control: no-store`, solo lectura y sin auditar (RF-018, RNF-015). |
| 5.19 | 17 de noviembre de 2026 | B-06.2: §6.4 con `GET /registros-jugador/movimientos/opcion?personaId=&equipoId=` (DEC-153): `{ tipo, permitido, temporadaAnterior, motivo }` para que el asistente de alta (F-18) ofrezca la renovación o la transferencia, o la muestre deshabilitada si la temporada no permite el cambio de club (RF-111), sin datos del club de origen; solo lectura y sin auditoría. |
| 5.20 | 17 de noviembre de 2026 | §6.4 «Alta de un registro», paso 11: `POST /registros-jugador` responde `409 RF-111` si el registro anterior más reciente de la persona es de otro club y la temporada no permite el cambio de club, como la transferencia; no obliga a usar el movimiento (DEC-154). |
| 5.21 | 22 de noviembre de 2026 | DEF-10.1 (SCRUM-450): §6.1 con `curp` opcional en `POST /usuarios`, que vincula el perfil con la persona (`perfiles.persona_id`); el orden de errores (`400 VALIDACION` del formato, `409 DUPLICADO` con `campo`, `422 REFERENCIA_INVALIDA`, `422 RF-106` y `409 DUPLICADO` con `perfilId`), todos antes de llamar a Supabase Auth; `personaId` en la lista y el detalle; y `personaId` en el detalle de `INVITAR_USUARIO` (DEC-155 a DEC-159). |
| 5.22 | 22 de noviembre de 2026 | H-15.1: §6.8 «Consulta y transiciones del partido» con el listado paginado `GET /partidos?jornadaId=&estado=&desde=&hasta=`, el detalle con jornada, árbitros, cédula, resultado vigente, historial y `accionesPermitidas`, el historial y `POST /partidos/{id}/transiciones` (motivo `400 RN-021`, transición prohibida `409 RF-046` con `estadoActual` y `accionesPermitidas`, jornada sin publicar `409 RF-090`, reprogramación con `inicio` futuro y conflictos `409 VAL-003` a `VAL-006`); visibilidad por ámbito (DEC-160 a DEC-162). |
| 5.23 | 23 de noviembre de 2026 | H-16.1: §6.8 con la captura de la cédula: `GET` y `POST /partidos/{id}/cedula` (abrir solo con el partido `EN_CURSO`, una vez, `201` con `Location`), `PUT /cedulas/{id}/alineaciones` (reemplazo completo, `422 VAL-002` con un error por jugador: `RF-019`, `RF-020`, `VAL-018`, `RF-025`, `REFERENCIA_INVALIDA`) y `PUT /cedulas/{id}/marcador` (no presentación con el marcador por default de la versión vigente de los parámetros, penales solo con empate); permisos `partidos.cedula:CONSULTAR` y `:CAPTURAR` con ámbito `LIGA`; auditoría `ABRIR_CEDULA`, `CAPTURAR_ALINEACION` y `CAPTURAR_MARCADOR` (DEC-164 a DEC-166). |
| 5.24 | 23 de noviembre de 2026 | H-16.2: §6.8 «Eventos, incidencias, observaciones y envío»: `POST /cedulas/{id}/eventos` (`201` con la cédula, equipo tomado de la alineación, sustitución del mismo equipo), `DELETE /cedulas/{id}/eventos/{eventoId}` (`204`), `POST /cedulas/{id}/incidencias` (tipo activo del catálogo e involucrados: jugador alineado o equipo del partido, `422 RF-026`), `PUT /cedulas/{id}/observaciones` (nuevo) y `POST /cedulas/{id}/envio` (`422 VAL-020` con la extensión `faltantes`); fuera de captura todo responde `409 RF-122`; la cédula agrega `eventos`, `incidencias`, `enviadaEn` y `plazoVenceEn`; auditoría y evento `CedulaEnviada` (DEC-170, DEC-171). |
| 5.25 | 24 de noviembre de 2026 | H-16.3: §6.8 con `GET /cedulas/{id}/jugadores` (candidatos a la alineación de los dos equipos y de la categoría inmediata inferior de cada club, con `elegible` y `causa` de la misma regla del `PUT`) y `GET /cedulas/{id}/tipos-incidencia` (tipos activos), los dos con `partidos.cedula:CAPTURAR`, para que el Capturista arme la cédula en F-26 sin permisos de registros ni del catálogo; la alineación agrega `personaId`, que identifica al jugador como involucrado de una incidencia (DEC-172). |
| 5.26 | 25 de noviembre de 2026 | H-35.1: §6.7 con la fecha de programación (DEC-175): `GET /fechas?temporadaId=` (paginado, estado derivado de sus jornadas y conteos), `GET /fechas/{id}` con `bloques` por «Categoría – Grupo» (partidos con cancha, horario, árbitro central e insignias; descansos; sin asignar con `codigo` y `causa`), `POST /fechas/{id}/verificacion`, `/envio` y `/resolucion` sobre todas las jornadas en una transacción con la separación de funciones de RF-121, y `emparejamientoId` opcional en `POST /jornadas/{id}/partidos` (DEC-180). |
| 5.27 | 25 de noviembre de 2026 | H-25.1: §6.14 con el contrato de los avisos: `GET /notificaciones?leida=` paginado (más recientes primero) con `X-No-Leidas` expuesta por CORS y `Cache-Control: no-store`, `POST /notificaciones/{id}/lectura` (`200` con el aviso; conserva la primera lectura; ajeno o inexistente `404`) y `POST /notificaciones/lectura` (`200` con `marcadas`); eventos `CEDULA_PENDIENTE`, `COBRO_VENCIDO` y `PROGRAMACION_PUBLICADA` con sus destinatarios por permiso. |
| 5.28 | 26 de noviembre de 2026 | H-32.2: §6.7 «Programación automática (H-32.2)» con `POST /temporadas/{id}/calendario` (cuerpo opcional `{ fechaInicio?, grupoIds?, motivo? }`, `201` con `Location`, orden de errores `403` → `400` → `404` → `409 RF-003` → `409 RF-006` → `422 REFERENCIA_INVALIDA` → `400 RN-021` → `409 RN-020` con `grupos` → `422 VALIDACION`; grupos omitidos y advertencias; reemplazo lógico con `vigente`, DEC-187 a DEC-193), `GET /temporadas/{id}/calendario`, el listado de fechas solo hasta la última fecha del calendario vigente y con `descansos`, y `PATCH /fechas/{id}` `{ dias, motivo }`; §6.2 con `PATCH /temporadas/{id}/categorias/{categoriaTemporadaId}` `{ diasJuego }` con `programacion.jornada:EDITAR` (DEC-191) y `diasJuego` en las categorías de la temporada; §3.2 agrega `RN-020` a `409` (migración `20261126090000_emparejamientos_vigentes.sql`). |
| 5.29 | 26 de noviembre de 2026 | H-32.3: §6.2 «Parámetros deportivos» con `vueltas` (1 a 4), `horaInicioJuego` y `horaFinJuego` (`HH:mm` o `HH:mm:ss`, minutos exactos, las dos o ninguna) opcionales en el `POST`, heredados de la vigente y con 1, 08:00 y 18:00 **[DEMO]** en la primera versión; horario ≥ mayor bloque de cancha, evaluado con los valores efectivos después del `404`; las horas en `camposDemo` y no las vueltas; no retroactivo (DEC-194 a DEC-196). |
| 5.30 | 26 de noviembre de 2026 | H-14.1: §6.5 «Designaciones» con `GET /designaciones?jornadaId=` (jornada, avance y partidos no cancelados con el central y los asistentes vigentes), `GET /partidos/{id}/arbitros-candidatos` (árbitros `VALIDADO` con `elegible`, `causas` `VAL-019`, `RF-039` y `YA_DESIGNADO`, la advertencia `RF-022` y `carga`), `POST /partidos/{id}/designaciones` (`201` sin `Location`; partido `PROGRAMADO` o `REPROGRAMADO`, árbitro repetido y tercer asistente `409 RF-023`; sustitución del central con motivo `400 RN-021`), `POST /designaciones/{id}/retiro` y `GET /mis-designaciones` (vigentes en jornadas `PUBLICADO`, siempre del titular); auditoría `DESIGNAR_ARBITRO`, `SUSTITUIR_ARBITRO` y `RETIRAR_DESIGNACION`; §3.1 agrega `RF-023` a `409` (DEC-201 a DEC-205). |
| 5.31 | 26 de noviembre de 2026 | H-33.2: §6.7 «Asignación automática de la fecha» con `POST /fechas/{id}/asignacion` (`{ modo: PENDIENTES\|REGENERAR, motivo? }`, orden de errores de DEC-206, respuesta `{ fecha, totales, noAsignados, advertencias, resumen }`), la confirmación con el validador de H-12 y el motivo de localía automático (DEC-207) y la jornada de la fecha, la jornada manual duplicada y los partidos que no se pueden cancelar (DEC-208). `GET /jornadas/{id}` ya no lista los partidos `CANCELADO` (DEC-181). |
| 5.32 | 27 de noviembre de 2026 | H-34.2: §6.7 «Asignación automática de la fecha» con el árbitro central automático: con `arbitraje.designacion:AUTOMATICA` designa el central de los existentes (en `PENDIENTES`) y de los insertados, con el orden de candados fecha → jornadas → partidos → canchas → árbitros (DEC-217); sin el permiso asigna igual y lo advierte (DEC-218); `totales.centrales`, `totales.sinArbitro`, la lista `sinArbitro`, la advertencia `RF-022` y los campos nuevos de `parametros_generacion` y `GENERAR_FECHA` (DEC-219); la verificación de la fecha agrega la causa al aviso `RF-023` (DEC-220). |
| 5.33 | 27 de noviembre de 2026 | H-35.1 (corrección): §6.7 indica que `POST /jornadas/{id}/envio` y `/resolucion` responden `409 RF-090` **[PROPUESTA]** con una jornada que pertenece a una fecha de programación (`jornadas.fecha_id`, creada por la asignación de H-33, DEC-188): se envía y se dictamina con la fecha completa (DEC-175). Los endpoints por jornada quedan para fases finales y jornadas manuales. |

## Índice

1. [Reglas generales](#1-reglas-generales)
2. [Verbos, códigos de éxito y acciones de negocio](#2-verbos-códigos-de-éxito-y-acciones-de-negocio)
3. [Errores (RFC 9457)](#3-errores-rfc-9457)
4. [Listas, filtros y paginación](#4-listas-filtros-y-paginación)
5. [Seguridad, permisos y ámbito](#5-seguridad-permisos-y-ámbito)
6. [Catálogo de recursos por módulo](#6-catálogo-de-recursos-por-módulo)
7. [Documentos y archivos (AWS S3)](#7-documentos-y-archivos-aws-s3)
8. [Portal público](#8-portal-público-apiv1publico)
9. [Representación de datos](#9-representación-de-datos)
10. [Documentación del contrato y pruebas](#10-documentación-del-contrato-y-pruebas)
11. [Pendientes](#11-pendientes)

## 1. Reglas generales

| Tema | Convención |
| --- | --- |
| Protocolo | HTTPS + JSON (`application/json; charset=utf-8`). Archivos: `multipart/form-data` (§7). |
| Prefijos | `/api/v1` para la aplicación interna · `/api/v1/publico` para el portal (sin sesión). |
| Recursos | Sustantivos en **plural, español, kebab-case**: `/temporadas`, `/registros-jugador`, `/solicitudes-cancha`. |
| Anidación | Máximo un nivel cuando el hijo no existe sin el padre: `/cedulas/{id}/alineaciones`. Si el hijo tiene identidad propia, va en la raíz con filtro: `/partidos?jornadaId=…`. |
| Campos JSON | **camelCase** (`equipoLocalId`). La BD usa snake_case; el repositorio hace el mapeo. Los nombres salen del diccionario de datos: `goles_local` → `golesLocal`. |
| Identificadores | UUID en texto. Excepción: los catálogos `permisos`, `tipos_documento` y `tipos_incidencia` usan `bigint`, y `permisos` se expone por su `codigo`. |
| Fechas y horas | ISO 8601 con desplazamiento: `2026-10-18T10:00:00-06:00` (`timestamptz`). Fechas sin hora: `2026-10-18`. Intervalos (`tstzrange`): objeto `{ "desde": …, "hasta": … }` con `hasta` exclusivo. |
| Zona horaria de negocio | `America/Mexico_City`, para mostrar y para calcular la «semana» del portal. |
| Montos | Número decimal con dos decimales en pesos mexicanos (`"monto": 350.00`), nunca flotante en el backend (`BigDecimal`). |
| Enums | Igual que en la BD, en MAYÚSCULAS: `PROGRAMADO`, `EN_CURSO`, `PENDIENTE_VALIDACION`. El frontend traduce a texto (§9.3). |
| Nulos | Se omiten los campos opcionales vacíos en las respuestas; nunca se usa `""` como nulo. |
| Idioma | Rutas, campos, mensajes y `detail` en español. |
| Versión | El número mayor va en la ruta (`/v1`). Agregar campos o recursos no cambia la versión; quitar o renombrar sí. |

## 2. Verbos, códigos de éxito y acciones de negocio

| Operación | Verbo | Éxito | Ejemplo |
| --- | --- | --- | --- |
| Listar | `GET` | `200` | `GET /api/v1/equipos?grupoId=…` |
| Obtener | `GET` | `200` | `GET /api/v1/partidos/{id}` |
| Crear | `POST` | `201` + `Location` + cuerpo | `POST /api/v1/clubes` |
| Actualizar parcial | `PATCH` | `200` + cuerpo | `PATCH /api/v1/canchas/{id}` |
| Reemplazar un sub-recurso completo | `PUT` | `200` | `PUT /api/v1/cedulas/{id}/marcador` |
| Acción de negocio | `POST` a un sub-recurso con nombre de sustantivo | `200` (o `201` si crea un recurso) | `POST /api/v1/cedulas/{id}/aprobacion` |
| Acción que queda pendiente de otra persona | `POST` | `202` + cuerpo con el trámite | `PATCH /roles/{id}/permisos` con un par incompatible (VAL-023) |

- **No hay `DELETE` sobre datos con historial** (temporadas, clubes, equipos, personas, registros,
  partidos, cédulas, resultados, expedientes, sanciones, cobros, pagos, documentos): RF-003, RF-044,
  RN-005, RN-020. Las bajas, cancelaciones y reemplazos son acciones de negocio. `DELETE` solo se
  usa para quitar un elemento de una captura en curso (un evento de una cédula `EN_CAPTURA`).
- Los cambios de estado **no** se hacen con `PATCH` del campo `estado`: cada transición es una
  acción explícita y, si el ERS lo exige, lleva `motivo` (RF-045, RN-021).
- Una acción repetida sobre un recurso que ya está en el estado final responde `409` con el código
  de la regla (no es idempotente por diseño, para que la interfaz sepa que alguien más actuó).

### 2.1 Máquinas de estado

| Entidad | Acción | Endpoint | Cuerpo | Transición | Permiso | Trazabilidad |
| --- | --- | --- | --- | --- | --- | --- |
| Temporada | Activar | `POST /temporadas/{id}/activacion` | `{}` | `CONFIGURACION → ACTIVA` | `temporadas.temporada:EDITAR` | RF-001 |
| Temporada | Cerrar | `POST /temporadas/{id}/cierre` | `{ "motivo" }` | `ACTIVA → CERRADA` | `temporadas.temporada:EDITAR` | RF-003 |
| Equipo | Baja | `POST /equipos/{id}/baja` | `{ "motivo" }` | `ACTIVO → BAJA` | `organizacion.equipo:EDITAR` | RF-009, RN-005 |
| Cuenta | Activar / desactivar | `PATCH /usuarios/{id}` | `{ "activo", "motivo"? }` | `activo` ↔ `inactivo` | `seguridad.usuario:EDITAR` | RF-094 |
| Afiliación | Aprobar / rechazar | `POST /afiliaciones/{id}/resolucion` | `{ "decision": "APROBADA"\|"RECHAZADA", "observaciones" }` | `SOLICITADA → …` | `organizacion.afiliacion:APROBAR` | RF-101 |
| Operación de equipo | Autorizar / rechazar | `POST /operaciones-equipo/{id}/resolucion` | `{ "decision": "AUTORIZADA"\|"RECHAZADA", "observaciones" }` | `SOLICITADA → …` | `organizacion.venta:APROBAR` | RF-103, RF-104, RN-040 |
| Registro de jugador | Validar / rechazar | `POST /registros-jugador/{id}/validacion` | `{ "decision": "ACTIVO"\|"RECHAZADO", "observaciones"? }` (obligatorias solo al rechazar, §6.4) | `PENDIENTE_VALIDACION → …` | `registros.registro:VALIDAR` | RF-109, VAL-001 |
| Registro de jugador | Baja | `POST /registros-jugador/{id}/baja` | `{ "motivo" }` | `ACTIVO → BAJA` | `registros.registro:EDITAR` | RF-014 |
| Árbitro | Validar / rechazar | `POST /arbitros/{id}/validacion` | `{ "decision": "VALIDADO"\|"RECHAZADO", "observaciones" }` | `PROPUESTO → …` | `arbitraje.arbitro:VALIDAR` | RF-113 |
| Jornada | Enviar a aprobación | `POST /jornadas/{id}/envio` | `{}` | `BORRADOR → REVISION` | `programacion.jornada:ENVIAR` | RF-090, RF-121 |
| Jornada | Aprobar y publicar / devolver | `POST /jornadas/{id}/resolucion` | `{ "decision": "PUBLICADO"\|"BORRADOR", "observaciones" }` | `REVISION → …` | `programacion.jornada:APROBAR` | RF-121, DEC-21 |
| Partido | Suspender, reprogramar o cancelar | `POST /partidos/{id}/transiciones` | `{ "estadoNuevo", "motivo", "inicio"? , "canchaId"? }` | DEC-23 (ERS §4.3.1) | `partidos.partido:CAMBIAR_ESTADO` | RF-043, RF-045 |
| Cédula | Enviar a validación | `POST /cedulas/{id}/envio` | `{}` | `EN_CAPTURA → PENDIENTE_VALIDACION` | `partidos.cedula:CAPTURAR` | RF-122, VAL-020 |
| Cédula | Aprobar | `POST /cedulas/{id}/aprobacion` | `{}` | `PENDIENTE_VALIDACION → APROBADA` | `partidos.cedula:APROBAR` (árbitro designado) o `partidos.cedula:APROBAR_VENCIDA` | RF-123, RF-124, VAL-021 |
| Cédula | Devolver | `POST /cedulas/{id}/devolucion` | `{ "observaciones" }` | `PENDIENTE_VALIDACION → DEVUELTA` | `partidos.cedula:APROBAR` | RF-123 |
| Cédula | Reabrir para corregir | `POST /cedulas/{id}/reapertura` | `{}` | `DEVUELTA → EN_CAPTURA` | `partidos.cedula:CAPTURAR` | ERS §4.3.2 |
| Expediente | Iniciar revisión | `POST /expedientes/{id}/revision` | `{}` | `ABIERTO → EN_REVISION` | `disciplina.expediente:RESOLVER` | RF-062 |
| Expediente | Resolver | `POST /expedientes/{id}/resoluciones` | ver §6.9 | `EN_REVISION → RESUELTO` | `disciplina.expediente:RESOLVER` | RF-062, RF-063 |
| Expediente | Interponer recurso | `POST /expedientes/{id}/recursos` | `{ "motivo" }` | `RESUELTO → EN_RECURSO` | `disciplina.recurso:INTERPONER` | RF-067 (B) |
| Recurso | Resolver | `POST /recursos/{id}/resolucion` | ver §6.9 | `EN_RECURSO → CERRADO` | `disciplina.recurso:RESOLVER` | RF-067 (B) |
| Cobro | Iniciar pago simulado | `POST /cobros/{id}/intentos-pago` | `{}` | crea intento `PENDIENTE` | `tesoreria.pago:PAGAR` | RF-138, ADR-0012 |
| Intento de pago | Confirmar simulación | `POST /intentos-pago/{id}/confirmacion` | `{ "resultado": "APROBADO"\|"RECHAZADO" }` | cobro `PENDIENTE → PAGADO\|RECHAZADO` | `tesoreria.pago:PAGAR` | RF-138, VAL-028 |
| Cobro | Registrar pago externo | `POST /cobros/{id}/pagos` | multipart (§7) | `PENDIENTE\|VENCIDO → PAGADO` | `tesoreria.pago:REGISTRAR` | RF-139 |
| Comunicado | Enviar / aprobar / devolver | `POST /comunicados/{id}/envio` · `/resolucion` | como la jornada | Borrador → Revisión → Publicado | `publicacion.comunicado:*` | RF-090 (B) |
| Solicitud de permisos | Aprobar / rechazar | `POST /solicitudes-cambio-permisos/{id}/resolucion` | `{ "decision", "motivo" }` | `SOLICITADO → …` | `seguridad.permisos:APROBAR_INCOMPATIBLE` | RF-096 |

Las transiciones automáticas (partido a `EN_CURSO` por fecha, a `FINALIZADO` por cédula aprobada,
cédula con plazo vencido, cobro `VENCIDO`) las hacen las tareas programadas (ADR-0009) y no tienen
endpoint.

## 3. Errores (RFC 9457)

Todas las respuestas de error usan `application/problem+json` con `ProblemDetail` de Spring y dos
propiedades de extensión: `codigo` (siempre) y `errores` (cuando hay campos señalados).

```json
{
  "type": "https://siglim.mx/errores/val-004",
  "title": "Conflicto de programación",
  "status": 409,
  "detail": "La cancha «Unidad Deportiva Morelos 2» ya tiene el partido Halcones vs. Toros de 10:00 a 11:45.",
  "instance": "/api/v1/partidos",
  "codigo": "VAL-004",
  "errores": [
    { "campo": "canchaId", "mensaje": "Cancha ocupada en ese bloque" }
  ]
}
```

- `detail` es un mensaje **para la persona que usa el sistema**: en español, dice la causa y, si
  aplica, qué hacer (ERS §3.1.1: «mostrar los conflictos de programación y su motivo»). Nunca
  incluye SQL, nombres de tabla, trazas ni datos personales de terceros.
- `codigo` es el identificador del ERS que se incumplió (`VAL-xxx`, `RF-xxx`, `RN-xxx`) o uno de
  los códigos técnicos de la tabla. El frontend decide el tratamiento por `codigo`, nunca por el
  texto.
- `type` es una URI estable por código; no necesita resolver a una página.

### 3.1 Código HTTP por tipo de regla

| HTTP | Cuándo | `codigo` |
| --- | --- | --- |
| `400` | JSON mal formado, tipos incorrectos, campos obligatorios ausentes o formato inválido (CURP, correo) | `VALIDACION` |
| `401` | Sin token, token vencido o inválido | `NO_AUTENTICADO` |
| `403` | El usuario no tiene el permiso (denegación por defecto) | `VAL-015` |
| `403` | Separación de funciones de la persona: aprobar la cédula que capturó, cambiar permisos de su propio rol, cambiar sus propias asignaciones o el estado de su cuenta, registrarse como tutor desde su propia cuenta (DEC-79), corregir un resultado sin resolución | `VAL-021`, `VAL-022`, `VAL-010` |
| `404` | El recurso no existe **o** está fuera del ámbito del usuario (no se revela cuál de los dos) | `NO_ENCONTRADO` (VAL-024) |
| `405` | El recurso existe pero no admite ese método (la respuesta incluye la cabecera `Allow`) | `METODO_NO_PERMITIDO` |
| `406`, `415` | La petición no envía o no acepta JSON (`Content-Type`, `Accept`) | `FORMATO_NO_SOPORTADO` |
| `409` | Conflicto con el estado actual de otros datos: programación, unicidad, estado que no admite la acción, cobro ya pagado | `VAL-003` a `VAL-006`, `VAL-008`, `VAL-017`, `VAL-026`, `VAL-028`, `RF-023`, `RF-046`, `RN-021`… |
| `413` | Archivo mayor que el límite (§7) | `ARCHIVO_DEMASIADO_GRANDE` |
| `415` | Tipo de archivo no permitido por su firma binaria (§7) | `TIPO_ARCHIVO_NO_PERMITIDO` |
| `422` | La petición está bien formada pero incumple una regla sobre su propio contenido: documentos faltantes, elegibilidad, categoría superior, árbitro del mismo club, cédula incompleta, menor sin consentimiento | `VAL-001`, `VAL-002`, `VAL-018`, `VAL-019`, `VAL-020`, `VAL-025`, `RF-019`, `RF-020`… |
| `500` | Error inesperado | `ERROR_INTERNO` (con un identificador de rastreo en `instance`, sin detalles técnicos) |
| `503` | Supabase o S3 no responden | `SERVICIO_NO_DISPONIBLE` |

Una advertencia que el usuario puede aceptar (localía, VAL-027, B) **no** es un error: el servicio
responde `409` con `codigo: "VAL-027"` y `"aceptable": true`; la interfaz muestra la advertencia y
reenvía la petición con `"motivoAceptacion"`. Las extensiones del problema como `aceptable` las
agrega el servicio con `ReglaNegocioException.con(nombre, valor)`. El bloqueo de una cancha con
partidos (`RF-030`, §6.6) sigue este patrón y se acepta con `"confirmarPartidosAfectados": true`.

### 3.2 Traducción de los errores de PostgreSQL

Los triggers y restricciones de la base son la **última garantía**: el servicio valida antes y
explica mejor. Si aun así la base rechaza la operación, el manejador global en `compartido` traduce
la excepción:

| SQLSTATE | Origen en la migración | Traducción |
| --- | --- | --- |
| `P0001` | `siglim.error_negocio(codigo, mensaje)` en triggers | `codigo` = valor de `HINT`; `detail` = `MESSAGE`; HTTP según §3.1 |
| `23P01` | `ex_partidos_cancha_ocupada` (exclusión GiST) | `409`, `VAL-004` |
| `23505` | Índices únicos (CURP activa en la temporada, folio, una temporada activa, resultado vigente…) | `409`; el código sale del comentario de la restricción o del índice; si no tiene, `DUPLICADO` |
| `23503` | Llave foránea | `422`, `REFERENCIA_INVALIDA` |
| `23514` | `CHECK` | `400`, `VALIDACION` |
| `42501` | Privilegio o política RLS | `404` si fue una lectura (`GET` o `HEAD`; VAL-024); `403`, `VAL-015`, si fue una escritura |
| `40001`, `40P01` | Serialización o interbloqueo | `409`, `CONCURRENCIA`; la interfaz pide recargar |
| `08xxx`, `53300`, `57P01`–`57P03` | Sin conexión, demasiadas conexiones o servidor reiniciándose | `503`, `SERVICIO_NO_DISPONIBLE` |

El mapeo `HINT → HTTP` vive en una sola tabla del backend (`compartido.error.TablaCodigosHttp`) para
que el mismo código responda siempre con el mismo estado. `error_negocio` arma `MESSAGE` como
«`VAL-003: texto`»; el `detail` lleva solo el texto. Mapeo inicial de los códigos que lanza la migración
`20261020090000_esquema_inicial.sql`:

| HTTP | Códigos en `HINT` | Qué significan |
| --- | --- | --- |
| `400` | `RN-021` | Falta el motivo obligatorio |
| `403` | `VAL-015` (la liga valida documentos y registros); `VAL-021`, `VAL-022` | La persona no puede hacer esa acción aunque tenga el permiso |
| `409` | `VAL-003`, `VAL-005`, `VAL-017`, `VAL-026`, `VAL-028`; `RF-001`, `RF-017`, `RF-039`, `RF-046`, `RF-048`, `RF-062`, `RF-090`, `RF-100`, `RF-109`, `RF-111`, `RF-113`, `RF-121`, `RF-122`, `RF-123`, `RF-124`, `RF-127`; `RN-012`, `RN-020`, `RN-024`, `RN-039`; `RNF-015`, `RNF-018` | Transición o cambio no permitido por el estado actual, o choque con otros datos |
| `422` | `VAL-001` (activar un registro sin todos sus documentos aprobados); `VAL-018`, `VAL-019`, `VAL-020`, `VAL-025`; `RF-013`, `RF-015`, `RF-016`, `RF-019`, `RF-020`, `RF-025`, `RF-033`; `RF-119` (horario de club o comodato incompatible con la cancha, o traslapado); `RF-139` (pago que no cubre el monto) | El contenido de la petición incumple una regla |

Algunos códigos cubren más de un caso en la base (`RF-121` y `RF-123` también rechazan una
devolución sin observaciones, y `RF-123` a quien no es el árbitro central). El servicio detecta esos
casos **antes** de escribir: la observación faltante responde `400 VALIDACION` y el árbitro no
designado, `403 VAL-015`; por eso en la base solo llegan como `409`.

Un código nuevo en la base exige agregarlo a esta tabla en el mismo PR. Si llega un código sin
mapeo, el backend responde `409` y lo registra en la bitácora como advertencia. La prueba
`TablaCodigosHttpTest` compara la tabla del backend con esta y con los `error_negocio(...)` de las
migraciones: si divergen, `mvnw verify` falla.

## 4. Listas, filtros y paginación

```http
GET /api/v1/partidos?jornadaId=…&estado=PROGRAMADO&pagina=0&tamano=20&orden=inicio,asc
```

```json
{
  "contenido": [ { "id": "…" } ],
  "pagina": 0,
  "tamano": 20,
  "totalElementos": 57,
  "totalPaginas": 3
}
```

- `tamano` por defecto 20 y máximo 100. `pagina` empieza en 0.
- `orden=campo,asc|desc`, repetible; solo se aceptan los campos que el endpoint documenta.
- Filtros con el nombre del campo en camelCase; varios valores separados por coma
  (`estado=PROGRAMADO,REPROGRAMADO`). Rangos de fecha: `desde` (incluido) y `hasta` (excluido).
- `q` es una búsqueda de texto **solo** dentro de un listado (nombre de club, de equipo o de
  persona); no existe buscador general (RF-089).
- Los listados **filtran por ámbito en el SQL** con `autorizacion.alcance(permiso)`: una directiva
  de club solo recibe filas de su club y `totalElementos` cuenta solo esas (ADR-0007).
- Los catálogos pequeños (categorías, tipos de incidencia, artículos, tipos de documento, permisos)
  pueden responder un arreglo sin envoltura de paginación; lo indica el endpoint.

## 5. Seguridad, permisos y ámbito

- **Autenticación.** Cabecera `Authorization: Bearer <access_token de Supabase Auth>` en todo
  `/api/v1/**` excepto `/api/v1/publico/**` y `GET /api/v1/salud`. El backend valida firma, emisor,
  audiencia y expiración con JWKS (ADR-0003). El frontend usa Supabase solo para iniciar sesión y
  renovar el token.
- **Contexto de base de datos.** Cada transacción fija el perfil y adopta `siglim_app`, o
  `siglim_publico` en el portal (ADR-0014). Ningún endpoint recibe el `perfilId` del usuario
  actual en el cuerpo: sale del token.
- **Autorización.** Cada método de servicio llama `autorizacion.exigir("modulo.recurso:ACCION",
  objetivo)`; los listados filtran con `autorizacion.alcance(permiso)` en el `WHERE` y en el
  `COUNT`. El catálogo de permisos es el de la migración (73 códigos, Apéndice E del ERS). Nunca se
  compara el nombre del rol (ADR-0007).
- **Ámbito.** `LIGA` permite toda la liga; `CLUB`, solo los recursos de su club; `PROPIO`, solo
  aquellos de los que la persona es titular (jugador, tutor del menor, árbitro designado). Fuera de
  ámbito: `404`.
- **Lo que sabe la interfaz.** `GET /api/v1/yo` devuelve el perfil, las asignaciones de rol con su
  ámbito y club, y los permisos efectivos. El frontend oculta lo que no aplica, pero **no es
  barrera**: el backend vuelve a verificar. Solo exige sesión y responde con
  `Cache-Control: no-store`. Las asignaciones son las activas, ordenadas por ámbito (`LIGA`,
  `CLUB`, `PROPIO`) y nombre del rol; los permisos son la unión de los de esos roles, sin
  duplicados por `(codigo, ambito, clubId)` y ordenados por código:

  ```json
  {
    "perfil": { "id": "…", "nombreMostrado": "Ana López", "correo": "ana@ejemplo.mx" },
    "asignaciones": [
      { "id": "…", "rol": { "codigo": "PROGRAMADOR", "nombre": "Programador de partidos" },
        "ambito": "LIGA", "club": null },
      { "id": "…", "rol": { "codigo": "DIRECTIVA_CLUB", "nombre": "Directiva de club" },
        "ambito": "CLUB", "club": { "id": "…", "nombre": "Club Halcones" } }
    ],
    "permisos": [
      { "codigo": "partidos.partido:CONSULTAR", "modulo": "M-08", "ambito": "LIGA", "clubId": null },
      { "codigo": "partidos.partido:CONSULTAR", "modulo": "M-08", "ambito": "CLUB", "clubId": "…" }
    ]
  }
  ```
- **Motivo.** Las acciones que el ERS marca con motivo lo exigen en el cuerpo (`"motivo"`, mínimo
  10 caracteres **[PROPUESTA]**); sin él, `400`. El servicio lo fija como `siglim.motivo` para el
  historial y la auditoría (RN-021, RF-093).
- **Auditoría.** Toda operación crítica se audita en la misma transacción: usuario, fecha, módulo,
  acción, entidad y motivo (RF-093). El cliente no envía datos de auditoría.
- **Cabeceras.** Las respuestas con datos personales llevan `Cache-Control: no-store`. CORS solo
  admite el origen del frontend (`SIGLIM_CORS_ORIGEN`). No se registran en bitácoras el token, el
  cuerpo de las peticiones con datos personales ni las URL prefirmadas (RNF-011).
- **Límites.** Tamaño máximo del cuerpo JSON: 1 MB **[PROPUESTA]**. Los intentos de inicio de
  sesión los limita Supabase Auth (PEN-030).

## 6. Catálogo de recursos por módulo

Rutas relativas a `/api/v1`. **E** = entrega del ERS (N núcleo, B backlog). Un recurso B no se
implementa hasta que su historia entre a un Sprint (AGENTS.md §3). Los permisos de consulta
(`…:CONSULTAR`) aplican a los `GET` del mismo recurso.

### 6.1 M-01 Administración y seguridad

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /yo` | Perfil, asignaciones con ámbito y permisos efectivos | sesión | RF-094 | N |
| `GET /usuarios` · `GET /usuarios/{id}` | Cuentas y sus roles | `seguridad.usuario:CONSULTAR` | RF-094 | N |
| `POST /usuarios` | Invitar una cuenta (Supabase Auth envía el correo) y asignar roles | `seguridad.usuario:CREAR` | RF-094 | N |
| `PATCH /usuarios/{id}` | Activar o desactivar | `seguridad.usuario:EDITAR` | RF-094 | N |
| `POST /usuarios/{id}/asignaciones` · `POST /asignaciones-rol/{id}/revocacion` | Asignar o revocar rol con ámbito | `seguridad.usuario:EDITAR` | RF-094 | N |
| `GET /roles` · `GET /roles/{id}` · `GET /permisos` | Roles, permisos y pares incompatibles | `seguridad.rol:CONSULTAR` | RF-095 | N |
| `PATCH /roles/{id}/permisos` | Cambiar permisos de un rol: `200` si se aplica, `202` si queda pendiente | `seguridad.rol:EDITAR` | RF-095, VAL-022, VAL-023 | N |
| `GET /solicitudes-cambio-permisos` · `POST …/{id}/resolucion` | Pares incompatibles por aprobar | `seguridad.permisos:APROBAR_INCOMPATIBLE` | RF-096 | N |
| `GET /avisos-privacidad/vigente?tipo=` | Aviso de privacidad vigente de la liga | sesión | RF-097 | N |
| `POST /personas/{id}/consentimientos` | Registrar consentimiento del titular o del tutor | `seguridad.consentimiento:REGISTRAR` en cualquier ámbito | RF-097, VAL-025 | N |
| `GET /solicitudes-arco` · `POST /solicitudes-arco` · `PATCH …/{id}` | Solicitudes ARCO | `seguridad.arco:GESTIONAR` | RF-098 | B |
| `GET /auditoria?perfilId=&modulo=&accion=&desde=&hasta=` | Consultar auditoría. `tamano` por defecto 50. `orden`: `ocurridoEn` | `auditoria.registro:CONSULTAR` | RF-134 | N |
| `GET /auditoria/opciones-filtro` | Perfiles, módulos y acciones presentes en la auditoría de la liga, para llenar los filtros; sin paginación | `auditoria.registro:CONSULTAR` | RF-134 | N |

- **Solicitudes ARCO (B-01).** `GET /solicitudes-arco?estado=&tipo=&personaId=` ordena por
  `recibidaEn` descendente (`tamano` por defecto 20). `POST` recibe `personaId`, `tipo`
  (`ACCESO`, `RECTIFICACION`, `CANCELACION`, `OPOSICION`) y `descripcion`; responde `201` sin
  `Location` con la solicitud `RECIBIDA`. `PATCH …/{id}` recibe `estado` y, al cerrar, `respuesta`.
  **[PROPUESTA]** Transiciones: `RECIBIDA → EN_ATENCION`; `RECIBIDA` o `EN_ATENCION` →
  `ATENDIDA` o `RECHAZADA` (finales, con `respuesta` obligatoria; guardan `atendidaPor` y
  `atendidaEn`). Otra transición responde `422 RF-098`. Registrar se autoriza sobre la persona
  titular; cambiar el estado exige ámbito `LIGA` (política RLS `arco_editar`). Un ámbito `CLUB` ve una
  página vacía. Una cancelación se atiende como bloqueo, nunca como borrado (DEC-67). Se audita
  `REGISTRAR_SOLICITUD_ARCO` y `CAMBIAR_ESTADO_SOLICITUD_ARCO` sin la descripción ni la respuesta.
- **Ámbito de la auditoría [PROPUESTA].** La tabla no tiene club ni persona: solo el ámbito `LIGA` ve
  filas. Con el permiso en ámbito `CLUB` o `PROPIO` la respuesta es `200` con página vacía y
  `totalElementos = 0` (y listas vacías en las opciones), coherente con la política RLS `auditoria_leer`.
- **Filtros de la auditoría.** `perfilId` es un UUID; `modulo` admite uno o varios `M-xx` separados por
  coma; `accion`, una o varias con el formato `^[A-Z][A-Z0-9_]{0,79}$`. Un formato inválido responde
  `400 VALIDACION`. Una acción bien formada que no existe devuelve una página vacía: no hay catálogo fijo.
- **Fechas de la auditoría [PROPUESTA].** `desde` (incluido) y `hasta` (excluido) son opcionales y sin
  rango máximo. Si vienen los dos y `desde >= hasta`: `400 VALIDACION`.
- **Orden de la auditoría.** Por defecto `ocurridoEn,desc`; `ocurridoEn` es el único campo ordenable, con
  `asc` o `desc` (sin dirección, `asc` (§4)), y el id desempata en la misma dirección. Otro campo: `400 VALIDACION`.
- **Detalle.** Cada fila trae el `motivo` completo y el `detalle`; no existe `GET /auditoria/{id}`.
- **Datos personales.** Cada fila devuelve `id`, `ocurridoEn`, `perfil`, `modulo`, `accion`, `entidad`,
  `entidadId`, `motivo` y `detalle`. Del perfil solo salen `id` y `nombreMostrado`; si el registro no tiene
  perfil, `perfil` se omite; también `entidadId`, `motivo` y `detalle` cuando están vacíos (§1). No se devuelve el rol: la auditoría no guarda con cuál actuó el usuario.
  Las dos respuestas llevan `Cache-Control: no-store`.
- **Solo lectura (RNF-015).** `POST`, `PUT`, `PATCH` y `DELETE` sobre `/auditoria` responden
  `405 METODO_NO_PERMITIDO` con la cabecera `Allow`.

```json
{
  "id": 1042,
  "ocurridoEn": "2026-10-25T12:30:00-06:00",
  "perfil": { "id": "…", "nombreMostrado": "Ana López" },
  "modulo": "M-06",
  "accion": "BLOQUEAR_CANCHA",
  "entidad": "disponibilidad_canchas",
  "entidadId": "…",
  "motivo": "Riego de la cancha programado",
  "detalle": { "tipo": "BLOQUEO" }
}
```

**Aviso de privacidad vigente (H-09.1, RF-097, DEC-74, DEC-80).** `GET /avisos-privacidad/vigente?tipo=`
acepta `INTEGRAL` o `SIMPLIFICADO` (por defecto `SIMPLIFICADO`; otro valor, `400 VALIDACION`) y
devuelve, de la liga configurada (`siglim.liga-id`), la versión de ese tipo con el mayor
`vigenteDesde` que no sea posterior a hoy en `America/Mexico_City` (ante un empate, la creada al
último **[PROPUESTA]**). Basta con tener sesión. Si no hay aviso vigente, `404 NO_ENCONTRADO`. Lleva
`Cache-Control: no-store` y no se audita.

```json
{
  "id": "…", "tipo": "SIMPLIFICADO", "version": "v1", "contenido": "AVISO DE PRIVACIDAD … NO OFICIAL …",
  "finalidades": ["REGISTRO_DEPORTIVO", "DIFUSION"], "vigenteDesde": "2026-11-11", "esDemo": true
}
```

`esDemo` es `true` en el aviso v1 de demostración que carga la migración `20261111130000`: la
interfaz lo rotula «No oficial» (DEC-74).

**Consentimiento (H-09.1, RF-097, VAL-025, DEC-76, DEC-81).** `POST /personas/{id}/consentimientos`
recibe `{ "avisoId", "finalidades": [ … ], "otorgadoPorId"? }` y responde `201` con
`{ "id", "personaId", "otorgadoPorId", "avisoId", "finalidades", "otorgadoEn" }`, sin `Location`
(todavía no existe un `GET` del recurso) y con `Cache-Control: no-store`. Exige
`seguridad.consentimiento:REGISTRAR` en **cualquier ámbito** (`autorizacion.alcance`), porque la
persona puede no estar todavía en el club. Orden de errores:

1. Sin el permiso: `403 VAL-015`.
2. Sin `avisoId`, sin finalidades o con una finalidad vacía: `400 VALIDACION`. Las finalidades se
   recortan y las repetidas se quitan **[PROPUESTA]**.
3. La persona no existe: `404 NO_ENCONTRADO` (se comprueba con `es_menor`, que no depende de RLS,
   DEC-78).
4. El aviso no existe: `422 REFERENCIA_INVALIDA`.
5. El aviso no es el vigente de su tipo en la liga: `422 RF-097`.
6. Alguna finalidad no está en el aviso: `422 RF-097`.
7. **Menor:** `otorgadoPorId` es obligatorio y debe ser su tutor vigente (`es_tutor_vigente`, aunque
   la tutela la haya registrado otro club); si no, `422 VAL-025`. **Adulto:** `otorgadoPorId` se omite
   o es la misma persona; si es otra, `422 RF-097`. Se guarda `otorgado_por_id = persona_id` (DEC-76).

`registrado_por` es el perfil de la sesión. Se audita en `M-01` como `REGISTRAR_CONSENTIMIENTO`
(entidad `consentimientos`) con `avisoId` y `finalidades` en el detalle, sin datos personales.
Revocar un consentimiento no forma parte de H-09.1.

**Usuarios, invitación y asignaciones (H-03.1, RF-094).** Los permisos son
`seguridad.usuario:CONSULTAR` para `GET /usuarios` y `GET /usuarios/{id}`,
`seguridad.usuario:CREAR` para `POST /usuarios` y `seguridad.usuario:EDITAR` para
`PATCH /usuarios/{id}`, `POST /usuarios/{id}/asignaciones` y
`POST /asignaciones-rol/{id}/revocacion`. Todos se exigen con ámbito de liga: una cuenta no
pertenece a un club. `GET /roles` exige `seguridad.rol:CONSULTAR` y es la lista que llena el
selector de rol de F-12.

`GET /usuarios` responde la envoltura de paginación (§4) y admite `q` (nombre o correo), `rolId`,
`ambito` (`LIGA`, `CLUB` o `PROPIO`), `clubId`, `activo`, `pagina` y `tamano`; el orden es fijo por
nombre. Cada fila trae `id`, `nombreMostrado`, `correo`, `personaId`, `activo`, `creadoEn` y
`asignaciones` (solo las activas, para los chips de rol). `personaId` es la persona vinculada a la
cuenta (`perfiles.persona_id`) y siempre viene, en `null` si la cuenta no tiene persona (DEC-155):

```json
{
  "contenido": [
    {
      "id": "…",
      "nombreMostrado": "Ana López",
      "correo": "ana@ejemplo.mx",
      "personaId": null,
      "activo": true,
      "creadoEn": "2026-10-25T12:30:00-06:00",
      "asignaciones": [
        {
          "id": "…",
          "rol": { "id": "…", "codigo": "DIRECTIVA_CLUB", "nombre": "Directiva de club" },
          "ambito": "CLUB",
          "club": { "id": "…", "nombre": "Club Halcones" },
          "activa": true,
          "creadoEn": "2026-10-25T12:30:00-06:00"
        }
      ]
    }
  ],
  "pagina": 0,
  "tamano": 20,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

`GET /usuarios/{id}` es el panel lateral: la misma cuenta con **todas** sus asignaciones, incluidas
las revocadas (`activa: false` y `revocadaEn`). El perfil técnico `SISTEMA` no se lista ni se opera y
un `id` inexistente responde `404`. Las respuestas llevan `Cache-Control: no-store` por los datos
personales (RNF-012). Con el permiso en ámbito de club, el listado responde `200` con página vacía y
`totalElementos = 0`; las demás rutas responden `404` como un recurso fuera de ámbito (VAL-024).

`POST /usuarios` invita:
`{ "nombreMostrado", "correo", "asignaciones": [ { "rolId", "ambito"?, "clubId"? } ], "curp"? }`. El
ámbito es opcional y, si se omite, se usa `roles.ambito_predeterminado`; con `CLUB` el `clubId` es
obligatorio y el club debe ser de la liga. `curp` (hasta 18 caracteres; se recorta y se pasa a
mayúsculas) es opcional y vincula la cuenta con la persona ya registrada con esa CURP
(`perfiles.persona_id`), que necesitan `persona_usuario()` y `es_titular()` en el ámbito `PROPIO`
(H-24). Vale con cualquier rol y no es obligatoria con ninguno, tampoco con `JUGADOR_TUTOR`; sin
ella, la invitación no cambia (DEC-155). Responde `201` con `Location` y el detalle, con `personaId`.
Errores, en este orden y **todos antes de llamar a Supabase Auth**, que es quien envía el correo
(DEC-156):

1. `403 VAL-015` sin `seguridad.usuario:CREAR` en ámbito de liga.
2. `400 VALIDACION` si falta el nombre o el correo, falta un rol, el ámbito `CLUB` no lleva club (o
   al revés), el rol no es asignable (`asignable = false`), el club no existe, la asignación está
   repetida o la CURP no tiene el formato de `ck_personas_curp` (extensión `campo: "curp"`).
3. `409 DUPLICADO` con `campo: "correo"` si el correo ya tiene perfil.
4. Solo con `curp`: `422 REFERENCIA_INVALIDA` si no hay una persona con esa CURP
   (`persona_por_curp`, aunque RLS no la muestre); `422 RF-106` si la persona es menor de edad hoy
   en `America/Mexico_City` (DEC-89), porque un menor no tiene cuenta propia y se invita a su tutor
   con la CURP del tutor (DEC-11); `409 DUPLICADO` con `campo: "curp"` y `perfilId` si la persona
   ya tiene un perfil, activo o no. Los tres llevan `campo: "curp"` y ningún mensaje repite la CURP.

Después, `409 DUPLICADO` si el correo ya tiene cuenta en Supabase Auth (`error_code` `email_exists`
o `user_already_exists`); `503 SERVICIO_NO_DISPONIBLE` si Supabase Auth no responde o rechaza la
invitación por cualquier otra causa (por ejemplo, el correo integrado de Supabase solo envía a
miembros del equipo del proyecto). Si otra invitación vinculó a la misma persona entre la
validación y el alta, el `INSERT … ON CONFLICT DO NOTHING` no inserta y la respuesta es
`409 DUPLICADO` con `campo: "curp"`, con la cuenta de Auth eliminada como cualquier otra
compensación (DEC-157). El backend llama a la API de administración de Supabase Auth
(ADR-0003 punto 5) y, si la transacción falla después de crear la cuenta, la elimina como
compensación para no dejarla sin perfil. El enlace del correo vuelve a
`<SIGLIM_FRONTEND_ORIGEN>/restablecer-contrasena`, donde el invitado fija su contraseña. Se audita `INVITAR_USUARIO` sobre
`perfiles` con los roles, ámbitos y clubes y, solo cuando hay vínculo, el `personaId`; nunca la CURP,
el nombre ni el correo (RNF-012, DEC-158). Vincular o desvincular una cuenta ya existente, tomar el
nombre de la persona y filtrar la lista por persona quedan fuera (DEC-159).

`PATCH /usuarios/{id}` lleva `{ "activo": true|false, "motivo"? }`. Al desactivar, el motivo es
obligatorio (mínimo 10 caracteres, **[PROPUESTA]**) y el cambio se audita como `DESACTIVAR_CUENTA`;
al activar, como `ACTIVAR_CUENTA`. Una cuenta inactiva se rechaza en cada petición aunque su token
siga vigente (ADR-0003 punto 6). Repetir el estado actual responde `200` sin generar un evento, salvo
sobre la propia cuenta (ver la separación de funciones abajo).

`POST /usuarios/{id}/asignaciones` lleva `{ "rolId", "ambito"?, "clubId"? }` y responde `201` con
`Location` y la asignación. Una asignación activa repetida responde `409 DUPLICADO`. La revocación es
`POST /asignaciones-rol/{id}/revocacion` con `{ "motivo" }` (mínimo 10): la asignación no se borra,
queda `activa: false` con `revocadaEn` y se audita `REVOCAR_ASIGNACION`; sin motivo, `400 RN-021`; si
ya estaba revocada, `409 RF-094`. Asignar (`ASIGNAR_ROL`) y revocar se auditan sobre
`asignaciones_rol` con el rol, el ámbito y el club, sin datos personales (RF-093, RNF-012).

**Separación de funciones (VAL-022, DEC-53).** Ninguna cuenta cambia sus propias asignaciones ni su
estado, sin importar su rol. `POST /usuarios/{id}/asignaciones`, `POST /asignaciones-rol/{id}/revocacion`
y `PATCH /usuarios/{id}` responden `403` con `codigo: "VAL-022"` cuando la cuenta objetivo es la del
usuario actual; en la revocación, la cuenta objetivo es la dueña de la asignación. El `PATCH` sobre la
propia cuenta responde `403` aunque no cambiaría nada (por ejemplo, `activo: true` sobre una cuenta
activa). La regla se evalúa después de la validación de forma del cuerpo (`400 VALIDACION` de §3,
por ejemplo un motivo de menos de 10 caracteres en la revocación o un `PATCH` sin `activo`), del
permiso (`403 VAL-015`) y del `404` de la cuenta o la asignación, y antes de las reglas del servicio
(`400 VALIDACION` del rol, el ámbito o el club, `409 DUPLICADO`, `409 RF-094` y `400 RN-021` al
desactivar sin motivo); con el `403` no se escribe ni se audita nada. Consultar el propio detalle
(`GET /usuarios/{id}`) sigue permitido.

`GET /roles` devuelve un arreglo sin paginación con `id`, `codigo`, `nombre`, `descripcion`,
`ambitoPredeterminado` y `asignable`; los roles `asignable = false` (`SISTEMA`, `USUARIO_PUBLICO`) se
listan, pero no se pueden asignar. El detalle del rol, el catálogo de permisos, la edición de la
matriz y el control dual se describen en **Permisos y control dual (H-20.1)**; la denegación
VAL-022 sobre las asignaciones y el estado de la propia cuenta es de H-03 (arriba).

**Permisos y control dual (H-20.1, RF-095, RF-096, VAL-022, VAL-023).** Los roles y sus solicitudes
son datos de la liga, así que las cinco rutas se exigen con ámbito de liga; con un ámbito de club
responden `404` como un recurso fuera de ámbito (VAL-024). Los permisos se identifican por su
`codigo`, nunca por el identificador numérico de la base (§1).

`GET /roles/{id}` exige `seguridad.rol:CONSULTAR` y responde el rol con sus permisos efectivos:

```json
{
  "id": "…",
  "codigo": "PROGRAMADOR",
  "nombre": "Programador de partidos",
  "descripcion": "Programa las jornadas",
  "ambitoPredeterminado": "LIGA",
  "asignable": true,
  "permisos": [
    { "codigo": "programacion.jornada:EDITAR", "modulo": "M-07",
      "descripcion": "Registrar y ajustar jornadas" }
  ]
}
```

`GET /permisos` exige el mismo permiso y responde un objeto sin paginación con el catálogo completo y
los pares declarados incompatibles, que es lo que F-13 necesita para pintar la matriz y avisar antes
de guardar:

```json
{
  "permisos": [
    { "codigo": "seguridad.rol:EDITAR", "modulo": "M-01", "descripcion": "Modificar la matriz de permisos" }
  ],
  "paresIncompatibles": [
    { "permisoA": "programacion.jornada:EDITAR", "permisoB": "programacion.jornada:APROBAR",
      "motivo": "Programar y aprobar la programación" }
  ]
}
```

`PATCH /roles/{id}/permisos` exige `seguridad.rol:EDITAR` y lleva el **conjunto final** del rol, no
una lista de altas, para que la matriz no dependa del orden de las casillas:
`{ "permisos": ["programacion.jornada:CONSULTAR", …], "motivo": "…" }`. `permisos` puede quedar
vacío; el `motivo` es obligatorio, de 10 a 500 caracteres (RF-093).

- Si el cambio no crea ningún par incompatible, se aplica y responde `200` con el detalle del rol.
  Repetir el conjunto actual responde `200` sin escribir ni auditar.
- Si agrega un par incompatible que el rol no tenía, **no se aplica**: se registra una solicitud
  `SOLICITADO` y responde `202` con el trámite (VAL-023); el rol conserva su matriz anterior. Un par
  que ya estaba en el rol no vuelve a bloquear la edición.
- Errores, en orden: sin `seguridad.rol:EDITAR`, `403 VAL-015`; rol inexistente o de otra liga,
  `404`; editar un rol que la propia cuenta tiene asignado, `403 VAL-022` (aunque el conjunto no
  cambie); sin motivo suficiente, `400`; un código fuera del catálogo, `400 VALIDACION`. Con el
  `403`/`400` no se escribe ni se audita nada.

```json
{
  "id": "…",
  "rol": { "id": "…", "codigo": "PROGRAMADOR", "nombre": "Programador de partidos",
           "ambitoPredeterminado": "LIGA", "asignable": true },
  "permisosAgregados": ["programacion.jornada:APROBAR"],
  "permisosQuitados": [],
  "motivo": "La Liga pidió concentrar la programación en una sola cuenta",
  "estado": "SOLICITADO",
  "solicitadoPor": { "id": "…", "nombreMostrado": "Ana López" },
  "solicitadoEn": "2026-11-07T10:00:00-06:00"
}
```

`GET /solicitudes-cambio-permisos?estado=&rolId=&pagina=&tamano=` exige
`seguridad.permisos:APROBAR_INCOMPATIBLE` y responde la envoltura de paginación (§4), de la más
reciente a la más antigua. Cada fila es el objeto anterior ya resuelto, con `resueltoPor`,
`resueltoEn` y `observaciones` cuando corresponde. `tamano` es 20 por defecto y 100 como máximo.

`POST /solicitudes-cambio-permisos/{id}/resolucion` es la acción de estado (§2.1) y lleva
`{ "decision": "APROBADO" | "RECHAZADO", "motivo": "…" }`, con el motivo de 10 a 500 caracteres en
los dos dictámenes (F-13). Responde `200` con la solicitud resuelta. `APROBADO` aplica a la matriz el
cambio guardado; `RECHAZADO` lo descarta. Errores, en orden: sin el permiso, `403 VAL-015`;
solicitud inexistente o de otra liga, `404`; ya resuelta, `409 RF-096` (una resolución no se reabre);
quien la solicitó intenta dictaminarla, `403 RF-096` (DEC-04, aunque su cuenta tenga los dos
permisos); sin decisión o sin motivo suficiente, `400`; y al aprobar, si el rol es de quien
dictamina, `403 VAL-022` (el mismo límite de la edición directa, comprobado antes de aplicar el
cambio). Ese último caso es el de una solicitud sobre `PRESIDENTE` que el propio Presidente intenta
aprobar: la solicitud sigue `SOLICITADO` y la matriz no cambia. No hay `DELETE`: el histórico de
solicitudes se conserva (RF-003).

Las respuestas de las solicitudes llevan `Cache-Control: no-store` porque nombran a quien solicita y
a quien resuelve (RNF-012). En el módulo `M-01` se auditan `CAMBIAR_PERMISOS_ROL` sobre `roles` al
aplicar un cambio directo, `SOLICITAR_CAMBIO_PERMISOS`, `APROBAR_CAMBIO_PERMISOS` y
`RECHAZAR_CAMBIO_PERMISOS` sobre `solicitudes_cambio_permisos`; el detalle guarda el `rol`, los
permisos agregados y quitados y, en el dictamen, el motivo; nunca datos personales.

### 6.2 M-02 Temporadas y normativa

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /temporadas` · `GET /temporadas/{id}` · `POST /temporadas` · `PATCH /temporadas/{id}` | Temporadas | `temporadas.temporada:CONSULTAR` en los `GET`; `temporadas.temporada:EDITAR` en `POST` y `PATCH` | RF-001, RF-003 | N |
| `POST /temporadas/{id}/activacion` · `/cierre` | Activar y cerrar | `temporadas.temporada:EDITAR` | RF-001, RF-003, RF-100, RN-058 | N |
| `GET /categorias?activa=` · `GET /categorias/{id}` · `POST /categorias` · `PATCH /categorias/{id}` · `POST /categorias/reordenamiento` | Catálogo con orden y categoría superior | `temporadas.temporada:CONSULTAR` en los `GET`; `temporadas.temporada:EDITAR` en `POST` y `PATCH` | RF-099 | N |
| `GET /temporadas/{id}/categorias` · `GET /temporadas/{id}/categorias/{categoriaTemporadaId}` · `POST /temporadas/{id}/categorias` | Categorías que participan en la temporada, con sus grupos | `temporadas.temporada:CONSULTAR` en los `GET`; `temporadas.temporada:EDITAR` en `POST` | RF-004, RF-005 | N |
| `PATCH /temporadas/{id}/categorias/{categoriaTemporadaId}` | Días de juego de la categoría en la temporada (`diasJuego`, DEC-177, DEC-191) | `programacion.jornada:EDITAR` | RF-035 | N |
| `GET /grupos?categoriaTemporadaId=` · `GET /grupos/{id}` · `POST /grupos` · `PATCH /grupos/{id}` | Grupos | `temporadas.temporada:CONSULTAR` en los `GET`; `temporadas.temporada:EDITAR` en `POST` y `PATCH` | RF-005 | N |
| `GET /temporadas/{id}/parametros` · `GET /temporadas/{id}/parametros/{version}` · `POST /temporadas/{id}/parametros` | Versiones de parámetros; `POST` crea una versión nueva, nunca edita la vigente | `temporadas.temporada:CONSULTAR` en los `GET`; `temporadas.parametros:EDITAR` en `POST` | RF-006, RF-100 | N |
| `PUT /temporadas/{id}/convocatoria` | Referencia de la convocatoria | `temporadas.convocatoria:EDITAR` | RF-002 | B |
| `GET /interpretaciones-regla` · `POST /interpretaciones-regla` · `POST /interpretaciones-regla/{id}/resolucion` | Reglas interpretadas | `temporadas.interpretacion:REGISTRAR` en el alta, `temporadas.interpretacion:APROBAR` en la resolución; el `GET` acepta cualquiera de las dos | RF-003, RF-006, RF-007 | B |

**Temporada.** `GET /temporadas?pagina=&tamano=` responde la envoltura de paginación (§4) con
`contenido` ordenado por `fechaInicio` descendente. Cada fila trae `id`, `nombre`, `fechaInicio`,
`fechaFin`, `estado`, `creadoEn`, `categoriasAsignadas`, `gruposCreados`, `versionParametros`,
`activacion` solo cuando el estado es `CONFIGURACION`, y `convocatoria` cuando ya está asociada:

```json
{
  "puedeActivarse": false,
  "requisitos": [
    { "codigo": "RN-058", "descripcion": "No hay otra temporada activa.", "cumple": true },
    { "codigo": "RF-100", "descripcion": "Faltan los parámetros deportivos de la temporada.", "cumple": false },
    { "codigo": "RF-004", "descripcion": "Falta asignar al menos una categoría a la temporada.", "cumple": false },
    { "codigo": "RF-005", "descripcion": "Falta crear al menos un grupo en una categoría.", "cumple": false }
  ]
}
```

La lista es la que usa F-10 para deshabilitar el botón y explicar qué falta. `GET /temporadas/{id}`
responde una temporada con la misma forma que una fila de la lista (incluidas `activacion` y
`convocatoria` en los mismos casos); si no existe o es de otra liga, `404`. `POST /temporadas` lleva
`{ "nombre", "fechaInicio", "fechaFin"? }` y responde `201` con `Location`; `PATCH /temporadas/{id}`
cambia solo los campos presentes. Una temporada `CERRADA` es de solo lectura: cualquier `PATCH`
responde `409 RF-003` y sus datos se conservan (RF-003, RN-005). No existe `DELETE`.

**Convocatoria (B-02.1, RF-002, RN-002).** `PUT /temporadas/{id}/convocatoria` reemplaza la
referencia completa de la convocatoria: `{ "referencia", "version"?, "documentoUrl"? }`. Es
idempotente, porque cada temporada tiene una sola convocatoria (`UNIQUE (temporada_id)`): la crea
si no existe o reemplaza la anterior, y responde `200` con
`{ "id", "temporadaId", "referencia", "version"?, "documentoUrl"?, "creadoEn" }`. Lo omitido
queda vacío; no hay `DELETE` ni `PATCH`. Exige `temporadas.convocatoria:EDITAR` con ámbito de liga.
Errores: `400 VALIDACION` si la referencia está vacía, la versión pasa de 30 caracteres o el enlace
no empieza por `http://` o `https://`; `404` igual que una temporada inexistente (VAL-024);
`409 RF-003` si la temporada está `CERRADA`, porque es de solo lectura. Se audita como
`ASOCIAR_CONVOCATORIA` sobre `convocatorias`, con la temporada, la versión y el enlace en el
`detalle`; cuando reemplaza una convocatoria anterior, el detalle conserva también
`referenciaAnterior`, `versionAnterior` y `documentoUrlAnterior`, porque el `PUT` sobrescribe la
fila y la tabla no guarda historial. `creadoEn` es la fecha de la primera asociación: el reemplazo
no la modifica, así que la interfaz la muestra como «Asociada el…». La temporada puede quedar sin
convocatoria mientras se configura (CU-001).

**Reglas interpretadas (B-03.1, RF-007, RN-006, DEC-57 a DEC-59).** Cuando una regla no es
explícita, se registra una interpretación administrativa que **no** es una regla permanente y
**no** toca los parámetros versionados (RF-006): en ningún caso escribe `parametros_temporada` ni
afecta la tabla de posiciones. Los tres verbos comparten `/api/v1/interpretaciones-regla`:

- `POST /interpretaciones-regla` con `{ "temporadaId", "descripcion" }` nace siempre
  `PENDIENTE`: el cuerpo no admite estado, resolución ni autoría, que sale de la sesión (403
  `VAL-015` si falta `temporadas.interpretacion:REGISTRAR`). La descripción se guarda sin espacios
  en los extremos; vacía, `400 VALIDACION`, y de más de 2000 caracteres **[PROPUESTA]**, `400
  VALIDACION`. Temporada inexistente o de otra liga, `404`; temporada `CERRADA`, `409 RF-003`
  (DEC-59). Se audita como `REGISTRAR_INTERPRETACION` sobre `interpretaciones_regla`, con
  `temporadaId` y `estado`; sin el texto completo.
- `GET /interpretaciones-regla?temporadaId=&estado=&pagina=&tamano=` responde la envoltura de
  paginación (§4), ordenada por `registradaEn` descendente. Cada fila trae `id`, `temporadaId`,
  `descripcion`, `estado`, `registradaPor`, `registradaEn` y, cuando ya está resuelta,
  `resueltaPor`, `resueltaEn` y `observaciones`. El `GET` exige cualquiera de los dos permisos
  del recurso con ámbito de liga; un ámbito de club no alcanza los datos de la liga y responde
  `404` idéntico a un recurso inexistente (VAL-024), con el `COUNT` bajo la misma condición.
- `POST /interpretaciones-regla/{id}/resolucion` lleva `{ "estado": "AUTORIZADA" | "RECHAZADA",
  "observaciones"? }` y responde `200` con la fila ya resuelta. Es una resolución de acción de
  estado (§2.1), no un `PATCH`. El bloqueo de la fila (`FOR UPDATE`) serializa dos resoluciones
  simultáneas. Errores en su orden: interpretación inexistente o de otra liga, `404`; sin
  `temporadas.interpretacion:APROBAR`, `403 VAL-015`; ya resuelta, `409 RF-007` (la máquina de
  estados la considera definitiva: la tabla guarda una sola resolución y la anterior queda solo
  en la auditoría); `estado` ausente o `PENDIENTE`, `400 VALIDACION`; quien la registró intenta
  resolverla aunque tenga el permiso, `403 RF-007` (DEC-57); rechazar con observaciones de menos
  de 10 caracteres, `400 RF-007` (DEC-58) — al autorizar, las observaciones son opcionales. Se
  audita como `RESOLVER_INTERPRETACION` con `temporadaId`, `estadoAnterior` y `estado`; al
  rechazar, las observaciones van como motivo.

No existe `PATCH` ni `DELETE`: una interpretación no se edita ni se borra, se resuelve (RF-003).

**Activación y cierre.** `POST /temporadas/{id}/activacion` no lleva cuerpo y pasa
`CONFIGURACION → ACTIVA` si la lista de verificación está completa. Errores: `409 RN-058` si otra
temporada está activa (el mensaje la nombra), `409 RF-100`, `409 RF-004` o `409 RF-005` con la
extensión `requisitos` para los que falten, y `409 RF-001` si la temporada no está en configuración.
`POST /temporadas/{id}/cierre` lleva `{ "motivo" }` (mínimo 10 caracteres), pasa
`ACTIVA → CERRADA`, fija el motivo para el historial y la auditoría (RN-021, RF-093); sin motivo
responde `400 RN-021` y si no está activa, `409 RF-001`. En la misma transacción aplica las
operaciones de equipo `AUTORIZADA` de la temporada (B-05.2): pasan a `APLICADA` con `aplicadaEn`, la
venta mueve el equipo a su club destino conservando el historial de propietarios y la conservación
de plaza solo se marca aplicada (RF-103, RF-104, RN-040); las `SOLICITADA` no se aplican. Cada
aplicación se audita como `APLICAR_OPERACION_EQUIPO` en `M-03` y el evento `CERRAR_TEMPORADA` guarda
`operacionesAplicadas` en su detalle. Ambas acciones se auditan como `ACTIVAR_TEMPORADA` y
`CERRAR_TEMPORADA`.

**Permisos y ámbito de categorías y grupos (H-05.1).** Todos los `GET` de categorías, categorías de
la temporada y grupos exigen `temporadas.temporada:CONSULTAR`; los `POST` y `PATCH`,
`temporadas.temporada:EDITAR`. Ambos con ámbito de liga: son datos de la liga y un ámbito de club no
los alcanza. La liga sale de la configuración del backend, no del cuerpo. Las escrituras validan en
este orden: sesión (`401`) → permiso (`403`) → cuerpo (`400`) → recurso de la ruta (`404`) →
referencias del cuerpo (`422`) → reglas de negocio (`409`). No hay `DELETE` en ninguno de los tres
recursos (§2).

**Catálogo de categorías (RF-099, DEC-16).** `GET /categorias?activa=` responde un arreglo **sin
paginar** (§4) ordenado por `orden` y `nombre`; `activa` es opcional (`true` o `false`) y sin él
vienen todas. Cada categoría trae `id`, `nombre`, `tipo` (`INFANTIL`, `LIBRE` o `VETERANOS`),
`orden`, `categoriaSuperior` (`{ id, nombre }`, omitida si la categoría es el tope de su rama),
`activa` y `creadoEn`. `GET /categorias/{id}` responde ese objeto; una categoría inexistente o de otra
liga, `404`.

```json
{
  "id": "…",
  "nombre": "Juvenil",
  "tipo": "LIBRE",
  "orden": 2,
  "categoriaSuperior": { "id": "…", "nombre": "Libre" },
  "activa": true,
  "creadoEn": "2026-11-05T10:00:00-06:00"
}
```

- `POST /categorias` lleva `{ "nombre", "tipo", "categoriaSuperiorId"? }` y responde `201` con
  `Location: /api/v1/categorias/{id}`. El nombre se guarda sin espacios al inicio ni al final y mide
  de 1 a 80 caracteres; sin nombre o sin tipo, `400 VALIDACION`. La categoría nace activa y al final
  del catálogo (`orden` = máximo actual + 1). Nombre ya usado en la liga, `409 DUPLICADO`.
- `PATCH /categorias/{id}` acepta `nombre`, `tipo`, `categoriaSuperiorId`, `quitarSuperior` y
  `activa`; lo ausente conserva su valor y responde `200` con la categoría. Con
  `"quitarSuperior": true` la categoría queda como tope de su rama; junto con `categoriaSuperiorId`
  responde `400 VALIDACION`. El orden no se edita aquí. Un `PATCH` sin cambios responde `200` y no se
  audita.
- **Jerarquía.** En `POST` y `PATCH`, una superior inexistente o de otra liga responde
  `422 REFERENCIA_INVALIDA`; una superior inactiva, la propia categoría o una superior que formaría
  un ciclo (A → B → A, A → B → C → A) responden `409 RF-099`, y el mensaje muestra la cadena. El
  backend bloquea las categorías de la liga durante la edición para que dos cambios simultáneos no
  formen un ciclo.
- **Jerarquía en uso (RF-110).** Cambiar o quitar la superior de una categoría que participa en una
  temporada `ACTIVA` responde `409 RF-110`: el trigger de alineaciones usa esa jerarquía para decidir
  en qué equipos puede jugar un jugador.
- **Desactivar.** `"activa": false` responde `409 RF-099` si la categoría participa en una temporada
  en `CONFIGURACION` o `ACTIVA`, o si es la superior de otra categoría activa. Reactivar
  (`"activa": true`) procede salvo que su superior resultante (la actual o la que venga en el mismo
  `PATCH`) esté inactiva: `409 RF-099`, porque toda categoría activa tiene una superior activa o
  ninguna. Con `"quitarSuperior": true` en el mismo `PATCH`, la reactivación procede.
- `POST /categorias/reordenamiento` lleva `{ "ids": [ … ] }` con **todas** las categorías de la liga,
  activas e inactivas, en el orden nuevo, y asigna `orden` de 1 a n en una sola transacción. Responde
  `200` con el catálogo completo ya ordenado. Si falta, sobra o se repite alguna, responde
  `400 VALIDACION` con las extensiones `faltantes`, `sobrantes` y `repetidos` (listas de `id`) y no
  cambia nada. Si el orden pedido es el actual, no escribe ni audita.

**Categorías de la temporada (RF-004).** `GET /temporadas/{id}/categorias` responde un arreglo sin
paginar ordenado por el `orden` del catálogo; cada elemento trae los grupos anidados, por nombre,
con el conteo de sus equipos en cualquier estado (§9.1). Una temporada inexistente responde `404`.

```json
[
  {
    "id": "…",
    "categoria": {
      "id": "…", "nombre": "Juvenil", "tipo": "LIBRE", "orden": 2,
      "categoriaSuperior": { "id": "…", "nombre": "Libre" }
    },
    "diasJuego": [7],
    "grupos": [ { "id": "…", "nombre": "A", "equipos": 8 } ]
  }
]
```

El `id` de cada elemento es el `categoriaTemporadaId` que usan los grupos.
`GET /temporadas/{id}/categorias/{categoriaTemporadaId}` responde un elemento; si no existe o es de
otra temporada, `404`. `POST /temporadas/{id}/categorias` lleva `{ "categoriaId" }` y responde `201`
con `Location` al `GET` anterior y el elemento en el cuerpo. Errores: temporada inexistente, `404`;
sin `categoriaId`, `400 VALIDACION`; categoría inexistente, `422 REFERENCIA_INVALIDA`; temporada
`CERRADA`, `409 RF-003`; temporada `ACTIVA`, `409 RF-004`; categoría inactiva, `409 RF-004`; ya
asignada, `409 DUPLICADO`. Todavía no hay forma de quitar una categoría de la temporada (§11).

`diasJuego` son los días ISO 8601 en que juega la categoría (1 = lunes … 7 = domingo), ordenados;
nacen por tipo de categoría **[DEMO]** (DEC-177). **Días de juego (H-32.2, DEC-191).**
`PATCH /temporadas/{id}/categorias/{categoriaTemporadaId}` lleva `{ "diasJuego": [6, 7] }` y responde
`200` con el elemento. Exige `programacion.jornada:EDITAR` en `LIGA` (no el permiso de M-02: los días
solo sirven para programar y el Programador no tiene `temporadas.temporada:EDITAR`). Orden: permiso
(`403`) → de 1 a 7 valores, cada uno de 1 a 7, sin repetir (`400 VALIDACION`) → temporada de la liga
(`404`) → categoría de esa temporada (`404`) → temporada `CERRADA` (`409 RF-003`). Procede en
configuración o activa; se guardan ordenados. Sin cambios no escribe ni audita; si cambian, se audita
`CAMBIAR_DIAS_JUEGO` en `M-02` sobre `categorias_temporada` con `{ temporadaId, diasAnteriores,
diasJuego }`. No mueve partidos ya programados: aplica a las asignaciones siguientes.

**Grupos (RF-005).** `GET /grupos?categoriaTemporadaId=` exige el filtro (sin él, `400 VALIDACION`) y
responde un arreglo sin paginar ordenado por nombre con `{ "id", "nombre", "categoriaTemporadaId",
"equipos" }`; una categoría de la temporada inexistente responde `404`. `GET /grupos/{id}` agrega
`temporadaId` y `categoria` (`{ id, nombre }`).

- `POST /grupos` lleva `{ "categoriaTemporadaId", "nombre" }` y responde `201` con `Location`. El
  nombre se guarda sin espacios al inicio ni al final y mide de 1 a 60 caracteres (`400 VALIDACION`).
  Categoría de la temporada inexistente, `422 REFERENCIA_INVALIDA`; temporada `CERRADA`,
  `409 RF-003`; temporada `ACTIVA`, `409 RF-005`; nombre repetido en la categoría, `409 DUPLICADO`.
- `PATCH /grupos/{id}` lleva `{ "nombre" }` y responde `200`. Procede con la temporada en
  `CONFIGURACION` o `ACTIVA`; `CERRADA` responde `409 RF-003`. Grupo inexistente, `404`; nombre
  repetido, `409 DUPLICADO`. Sin nombre o con el mismo, responde `200` y no se audita. El grupo no
  cambia de categoría.
- Todavía no hay forma de borrar un grupo (§11).

**Auditoría de categorías y grupos.** En el módulo `M-02`, sin motivo: `REGISTRAR_CATEGORIA`
(`tipo`, `orden` y `categoriaSuperiorId`), `EDITAR_CATEGORIA` (`campos` cambiados y, si cambiaron,
`categoriaSuperiorId` y `activa`) y `REORDENAR_CATEGORIAS` (los `ids` en el orden nuevo, sin
`entidadId`) sobre `categorias`; `ASIGNAR_CATEGORIA_TEMPORADA` (`temporadaId` y `categoriaId`) sobre
`categorias_temporada`; `REGISTRAR_GRUPO` (`categoriaTemporadaId` y `temporadaId`) y `EDITAR_GRUPO`
(`campos` y `temporadaId`) sobre `grupos`. El detalle lleva solo identificadores y datos no
personales.

**Parámetros deportivos (H-05.2, RF-006, RF-100).** Cada temporada guarda sus reglas en versiones
inmutables: un cambio es una versión nueva y la de número mayor es la vigente (DP-002). Con al menos
una versión se cumple el requisito `RF-100` de la activación. Los `GET` exigen
`temporadas.temporada:CONSULTAR` y el `POST`, `temporadas.parametros:EDITAR`, ambos con ámbito de
liga; la liga sale de la configuración del backend. El `POST` valida en el mismo orden que H-05.1:
`401` → `403` → cuerpo (`400`) → temporada de la ruta (`404`) → categorías del cuerpo (`422`) →
reglas de negocio (`409`). No hay `PATCH` ni `DELETE`.

`POST /temporadas/{id}/parametros` lleva el juego **completo** de valores: nada se hereda de la
versión anterior y todos los campos son obligatorios salvo `equiposPorArbitro`,
`fechasFaseRegular`, `fechasFinalesUnidadDeportiva`, `vueltas`, `horaInicioJuego`,
`horaFinJuego`, `esDemo` y `categorias`. `equiposPorArbitro` omitido conserva el de la versión
vigente y, en la primera versión, vale 4 **[DEMO]** (RF-114); los dos de localía se heredan igual y,
en la primera versión, valen sin definir y 10 **[DEMO]** (RF-120). Las vueltas y el horario de juego
también se heredan y, en la primera versión, valen 1 y de 08:00 a 18:00 **[DEMO]**; las dos horas
van juntas o ninguna (DEC-194).

```json
{
  "puntosVictoria": 3, "puntosEmpate": 1, "puntosDerrota": 0,
  "criteriosDesempate": ["PUNTOS", "DIFERENCIA_GOLES", "GOLES_FAVOR", "ENFRENTAMIENTO_DIRECTO", "MENOS_EXPULSIONES", "SORTEO"],
  "golesDefaultGanador": 3, "golesDefaultPerdedor": 0,
  "plazoValidacionCedulaHoras": 48,
  "duracionJuegoMin": 90, "bloqueCanchaMin": 120,
  "clasificadosLiguilla": 8, "formatoLiguilla": "PARTIDO_UNICO", "empateLiguillaPorPenales": true,
  "equiposAscienden": 2, "equiposDescienden": 2, "permiteCambioClub": true,
  "equiposPorArbitro": 4,
  "fechasFaseRegular": 18, "fechasFinalesUnidadDeportiva": 10,
  "vueltas": 2, "horaInicioJuego": "08:00", "horaFinJuego": "18:00",
  "esDemo": true,
  "categorias": [ { "categoriaId": "…", "duracionJuegoMin": 70, "bloqueCanchaMin": 90 } ],
  "motivo": "Valores de demostración para la temporada"
}
```

| Campo | Regla |
| --- | --- |
| `puntosVictoria`, `puntosEmpate`, `puntosDerrota` | Enteros de 0 a 100 **[PROPUESTA]**; victoria ≥ empate ≥ derrota |
| `criteriosDesempate` | De 1 a 8, el primero `PUNTOS`, sin repetidos; `SORTEO` es opcional y, si aparece, va al final. Valores: `PUNTOS`, `DIFERENCIA_GOLES`, `GOLES_FAVOR`, `MENOS_GOLES_CONTRA`, `PARTIDOS_GANADOS`, `ENFRENTAMIENTO_DIRECTO`, `MENOS_EXPULSIONES`, `SORTEO` |
| `golesDefaultGanador`, `golesDefaultPerdedor` | Enteros de 0 a 99; ganador > perdedor (marcador por default, DEC-24) |
| `plazoValidacionCedulaHoras` | Entero de 1 a 720 horas **[PROPUESTA]** (DEC-20) |
| `duracionJuegoMin`, `bloqueCanchaMin` | Enteros de 1 a 600 minutos **[PROPUESTA]**; bloque ≥ duración (DEC-25) |
| `clasificadosLiguilla` | 2, 4, 8, 16 o 32 (DEC-28) |
| `formatoLiguilla` | `PARTIDO_UNICO` o `IDA_Y_VUELTA` |
| `empateLiguillaPorPenales`, `permiteCambioClub` | Booleanos |
| `equiposAscienden`, `equiposDescienden` | Enteros de 0 a 32 **[PROPUESTA]** (DEC-29) |
| `equiposPorArbitro` | Entero de 1 a 100; opcional. Omitido conserva el de la versión vigente y, en la primera versión, vale 4 **[DEMO]**. Equipos activos por cada árbitro validado que el club debe presentar (RF-114, DEC-17, DEC-72) |
| `fechasFaseRegular` | Entero de 1 a 99 **[PROPUESTA]**; opcional. Total de fechas (jornadas) de la fase regular. Omitido conserva el de la versión vigente; en la primera versión queda sin definir y la regla de las últimas fechas no se evalúa (RN-049, B-15.1) |
| `fechasFinalesUnidadDeportiva` | Entero de 0 a 99 **[PROPUESTA]**; opcional. Últimas fechas de la fase regular en que se advierte una cancha fuera de la unidad deportiva; 0 desactiva la regla. Omitido conserva el de la versión vigente y, en la primera versión, vale 10 **[DEMO]** (RF-120, VAL-027, DEC-34) |
| `vueltas` | Entero de 1 a 4 (`ck_parametros_vueltas`); opcional. Vueltas del calendario por el método del círculo. Omitido conserva el de la versión vigente y, en la primera versión, vale 1 (DEC-174, DEC-194) |
| `horaInicioJuego`, `horaFinJuego` | Horas `HH:mm` o `HH:mm:ss` en minutos exactos (segundos en cero, **[PROPUESTA]**); opcionales, pero las dos o ninguna. Horario de juego por defecto de cada día de la fecha. Omitidas conservan las de la versión vigente y, en la primera versión, valen 08:00 y 18:00 **[DEMO]**. Fin > inicio, y el horario debe durar al menos el mayor bloque de cancha (el de la temporada o el de alguna fila de `categorias`) (DEC-176, DEC-194, DEC-195) |
| `esDemo` | Opcional; omitido vale `true` |
| `categorias` | Opcional. Duración y bloque propios de una categoría **asignada a esta temporada**, sin repetir categoría y con bloque ≥ duración. Una categoría omitida usa los valores de la temporada |
| `motivo` | De 10 a 500 caracteres sin espacios en los extremos (RN-021); se guarda en la versión y en la auditoría |

Responde `201` con `Location: /api/v1/temporadas/{id}/parametros/{version}` y la versión creada, con
la misma forma que `GET /temporadas/{id}/parametros/{version}`:

```json
{
  "id": "…", "temporadaId": "…", "version": 2,
  "puntosVictoria": 3, "puntosEmpate": 1, "puntosDerrota": 0,
  "criteriosDesempate": ["PUNTOS", "DIFERENCIA_GOLES", "GOLES_FAVOR", "ENFRENTAMIENTO_DIRECTO", "MENOS_EXPULSIONES", "SORTEO"],
  "golesDefaultGanador": 3, "golesDefaultPerdedor": 0, "plazoValidacionCedulaHoras": 48,
  "duracionJuegoMin": 90, "bloqueCanchaMin": 120, "clasificadosLiguilla": 8,
  "formatoLiguilla": "PARTIDO_UNICO", "empateLiguillaPorPenales": true,
  "equiposAscienden": 2, "equiposDescienden": 2, "permiteCambioClub": true,
  "equiposPorArbitro": 4,
  "fechasFaseRegular": 18, "fechasFinalesUnidadDeportiva": 10,
  "vueltas": 2, "horaInicioJuego": "08:00:00", "horaFinJuego": "18:00:00",
  "categorias": [ { "categoria": { "id": "…", "nombre": "Veteranos" }, "duracionJuegoMin": 70, "bloqueCanchaMin": 90 } ],
  "esDemo": true,
  "camposDemo": ["puntosVictoria", "puntosEmpate", "puntosDerrota", "criteriosDesempate",
                 "golesDefaultGanador", "golesDefaultPerdedor", "plazoValidacionCedulaHoras", "bloqueCanchaMin",
                 "clasificadosLiguilla", "formatoLiguilla", "empateLiguillaPorPenales",
                 "equiposAscienden", "equiposDescienden", "equiposPorArbitro",
                 "fechasFinalesUnidadDeportiva", "horaInicioJuego", "horaFinJuego"],
  "motivo": "Ajuste de la asamblea de la Liga",
  "creadoPor": { "id": "…", "nombre": "Ana López" },
  "creadoEn": "2026-11-05T10:00:00-06:00"
}
```

`categorias` va por el orden del catálogo y `creadoPor` y `fechasFaseRegular` se omiten si la versión
no los tiene. Las horas se responden siempre en `HH:mm:ss`, también en `predeterminados`.

- **`esDemo` y `camposDemo`.** `esDemo` vale para la versión completa: es `true` mientras la Liga no
  confirme los valores como oficiales (PEN-021). Con `esDemo: true`, `camposDemo` lista los campos
  **[DEMO]** que la interfaz rotula «No oficial»; con `esDemo: false` va vacío. La lista es fija,
  incluye las dos horas del horario de juego (DEC-176) y no incluye `duracionJuegoMin` (DEC-25
  adopta 90 minutos como estándar), `permiteCambioClub` (DEC-13) ni `vueltas` (DEC-174, DEC-194).
  «Oficial» significa que la Liga confirmó los valores; se registra con una versión nueva con
  `esDemo: false`.
- **Criterios de desempate (RF-050).** Ninguno usa el nombre del equipo. Un valor fuera de la lista,
  como `NOMBRE_EQUIPO`, no se puede leer del JSON y responde `400 VALIDACION`.
- **`GET /temporadas/{id}/parametros`** responde la vigente completa, el historial de la mayor a la
  menor (incluida la vigente) y, solo si todavía no hay versiones, los valores predeterminados de la
  tabla para precargar el formulario (`esDemo: true`, sin `categorias` ni `motivo`):

  ```json
  {
    "vigente": { "…": "la versión 2 completa" },
    "historial": [
      { "id": "…", "version": 2, "esDemo": true, "motivo": "Ajuste de la asamblea de la Liga",
        "creadoPor": { "id": "…", "nombre": "Ana López" }, "creadoEn": "…",
        "camposCambiados": ["puntosVictoria", "categorias"] },
      { "id": "…", "version": 1, "esDemo": true, "motivo": "…", "creadoEn": "…",
        "camposCambiados": ["puntosVictoria", "…", "esDemo"] }
    ]
  }
  ```

  Sin versiones: `{ "vigente": null, "historial": [], "predeterminados": { … } }`.
  `camposCambiados` compara cada versión con la anterior en camelCase, incluye `esDemo` y agrega
  `categorias` si cambió el conjunto de filas por categoría. La versión 1 lista todos los campos (y
  `categorias` solo si trae filas propias).
- **Qué es retroactivo.** La tabla de posiciones se calcula con la versión vigente
  (`calcular_tabla()`, RN-022): un cambio de puntos o de criterios cambia las tablas de toda la
  temporada. **No** cambia lo ya guardado: el `fin_bloque` de los partidos programados, el
  `plazo_vence_en` de las cédulas enviadas, ni las fases y propuestas de movimiento que registraron
  su `parametros_id`. Cambiar las vueltas o el horario de juego tampoco cambia el calendario ya
  generado ni los partidos ya programados: las vueltas se aplican al regenerar el calendario y el
  horario en la siguiente asignación de una fecha. El cambio no se bloquea; F-11 lo avisa (DEC-196).
- **Errores.** Motivo ausente o de menos de 10 caracteres, `400 RN-021`; motivo de más de 500 o
  cualquier otro campo inválido, `400 VALIDACION` con la extensión `errores` (`campo`, `mensaje`),
  como en §3. El motivo se valida antes que los demás campos. Temporada inexistente o de otra liga,
  `404`; categoría inexistente o no asignada a la temporada, `422 REFERENCIA_INVALIDA` con la
  extensión `categoriasInvalidas`; temporada `CERRADA`, `409 RF-003` (`CONFIGURACION` y `ACTIVA`
  admiten versiones nuevas). Al final, con los valores **efectivos** (vueltas y horas ya heredadas),
  un horario de juego más corto que el mayor bloque de cancha responde `400 VALIDACION` con
  `errores: [{ "campo": "horaFinJuego", … }]`; por eso puede llegar después del `404`, del `422` y
  del `409` (DEC-195). En el `GET` por versión, una versión inexistente responde `404`.
- **Concurrencia.** El backend bloquea la temporada (`FOR UPDATE`, el mismo candado que activación y
  cierre) antes de leer la versión anterior e insertar, así que dos registros simultáneos reciben
  versiones consecutivas.
- **Auditoría.** `REGISTRAR_PARAMETROS` en el módulo `M-02` sobre `parametros_temporada`, con el
  `id` de la versión nueva y el motivo; el `detalle` lleva `temporadaId`, `version`,
  `versionAnterior` (`null` en la primera), `esDemo` y `campos` (los `camposCambiados`). Sin valores
  ni datos personales.

### 6.3 M-03 Organización deportiva

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /clubes` · `GET /clubes/{id}` · `POST /clubes` | Clubes. Sin `PATCH`: el nombre solo cambia con `POST /clubes/{id}/cambio-nombre` | `organizacion.club:CONSULTAR` en los `GET`; `organizacion.club:EDITAR` en `POST` | RF-008 | N |
| `POST /clubes/{id}/cambio-nombre` | Cierra el club y crea uno nuevo | `organizacion.club:EDITAR` con ámbito de liga | RF-105 | B |
| `GET /afiliaciones?temporadaId=&estado=` · `GET /afiliaciones/{id}` · `POST /afiliaciones` · `PATCH /afiliaciones/{id}` | Tarjetón por temporada (folio lo asigna el sistema) | `organizacion.afiliacion:CAPTURAR`; los `GET` también con `organizacion.afiliacion:APROBAR` | RF-101 | N |
| `GET /afiliaciones/contexto` | Temporadas, categorías y grupos para capturar o revisar el tarjetón | `organizacion.afiliacion:CAPTURAR` o `organizacion.afiliacion:APROBAR` | RF-101 | N |
| `POST /afiliaciones/{id}/resolucion` | Aprobar o rechazar | `organizacion.afiliacion:APROBAR` | RF-101 | N |
| `GET /clubes/{id}/directiva` | Cargos e historial | `organizacion.club:CONSULTAR` con ámbito del club | RF-102 | N |
| `POST /clubes/{id}/directiva` | Registrar un cambio pendiente | `organizacion.directiva:EDITAR` con ámbito del club | RF-102 | N |
| `POST /cargos-directiva/{id}/autorizacion` | Autorizar un cambio | `organizacion.directiva:APROBAR` con ámbito del club | RF-102 | N |
| `GET /equipos?temporadaId=&grupoId=&clubId=&estado=` · `GET /equipos/{id}` · `POST` · `PATCH /equipos/{id}` · `POST /equipos/{id}/baja` | Equipos | `organizacion.equipo:CONSULTAR` en los `GET`; `organizacion.equipo:EDITAR` en `POST`, `PATCH` y la baja | RF-009 | N |
| `GET /operaciones-equipo?temporadaId=&estado=` · `GET /operaciones-equipo/{id}` · `GET /operaciones-equipo/contexto` · `POST /operaciones-equipo` · `POST …/{id}/resolucion` | Venta y conservación de plaza | `organizacion.venta:*` | RF-103, RF-104 | B |
| `GET /personas?curp=` · `POST /personas` · `PATCH /personas/{id}` | Identidad única por CURP; `POST` con una CURP existente responde `409 RF-010` con el `personaId` para reutilizarla | `organizacion.persona:CONSULTAR` en el `GET` y `organizacion.persona:EDITAR` en `POST` y `PATCH`, en cualquier ámbito; corregir la identidad exige `EDITAR` en toda la liga (DEC-77) | RF-010, RF-011 | N |
| `GET /personas/{id}/historial` | Clubes y equipos por temporada | `organizacion.persona:CONSULTAR` | RF-012 | N |
| `POST /personas/{id}/tutelas` | Vincular menor y tutor | `organizacion.persona:EDITAR` en cualquier ámbito | RF-106 | N |

**Ámbito.** Clubes y equipos se autorizan con el ámbito del club: la directiva de un club solo ve y
opera el suyo, y un club o equipo ajeno responde `404` igual que uno inexistente (VAL-024). Los
listados filtran por ámbito en el `WHERE` y en el `COUNT`; un `clubId` ajeno en el filtro de
`GET /equipos` da una página vacía. El alta de un club es de toda la liga.

**Club.** `GET /clubes?q=&estado=&pagina=&tamano=` responde la envoltura de paginación (§4) ordenada
por nombre; `q` busca dentro del nombre y `estado` es `ACTIVO` o `CERRADO`. Cada club trae `id`,
`nombre`, `estado`, `creadoEn`; solo si está cerrado, `cerradoEn` y `motivoCierre`; y solo si
proviene de un cambio de nombre, la referencia anidada `clubAnterior: { "id", "nombre" }` (§9.1).
`GET /clubes/{id}` responde ese detalle. `POST /clubes` lleva `{ "nombre" }` y responde `201` con
`Location`; el club nace `ACTIVO` y la liga sale de la configuración del backend, no del cuerpo.
Errores: sin nombre, `400 VALIDACION`; nombre ya usado en la liga, `409 DUPLICADO` con un mensaje que
nombra el club (también si otra petición lo registró al mismo tiempo y lo rechaza el índice único).
**No existe `PATCH /clubes/{id}`:** el único dato que cambia es el nombre y su cambio crea un club
nuevo y cierra el anterior con `POST /clubes/{id}/cambio-nombre` (B-04, RN-041, DEC-49). Tampoco hay
`DELETE`. El alta se audita en `M-03` como `REGISTRAR_CLUB` con `{ "estado": "ACTIVO" }`.

**Cambio de nombre (B-04, RF-105, DEC-15, DEC-49, DEC-61).** `POST /clubes/{id}/cambio-nombre`
lleva `{ "nombre", "motivo" }` y responde `201` con `Location: /api/v1/clubes/{nuevoId}` y el club
**nuevo** en el cuerpo (con `clubAnterior`). Es una operación de toda la liga
(`organizacion.club:EDITAR` con ámbito de liga) y se registra entre temporadas. Un nombre de más de
150 caracteres o un motivo de más de 500 responden `400 VALIDACION` antes de llegar al servicio; el
resto se valida en este orden:

1. Sin el permiso con ámbito de liga, `403 VAL-015`, antes de buscar el club (no revela si existe).
2. Club inexistente en la liga, `404`.
3. Club `CERRADO`, `409 RN-041`.
4. Motivo ausente o de menos de 10 caracteres sin contar espacios a los extremos, `400 RN-021`.
5. Nombre vacío, `400 VALIDACION`.
6. El club tiene equipos no dados de baja en una temporada de la liga `ACTIVA` o en
   `CONFIGURACION`, `409 RN-041`; el mensaje explica que el cambio se registra entre temporadas y
   cuántos equipos tiene inscritos.
7. El club tiene cobros `PENDIENTE` o `VENCIDO`, `409 RN-041`: deben liquidarse antes.
8. El nombre es el actual, `409 DUPLICADO`; el nombre ya lo usa otro club de la liga, aunque esté
   `CERRADO` (comparación exacta tras recortar espacios), `409 DUPLICADO` con el mismo mensaje del
   alta.

Efectos, en una sola transacción con el club anterior bloqueado (`FOR UPDATE`; el alta de equipos
lo lee con `FOR SHARE` para no colarse): se inserta el club nuevo `ACTIVO` con `club_anterior_id`;
el anterior pasa a `CERRADO` con `cerradoEn` (fecha del día en `America/Mexico_City`) y
`motivoCierre`, y conserva su nombre e historial (RN-005). Cada asignación de rol activa con ámbito
`CLUB` del club anterior se revoca y se crea una igual (mismo perfil y rol) en el club nuevo; nunca
se cambia el `club_id` de una asignación. La directiva (`cargos_directiva`) no se traslada: es
historial y la del club nuevo se registra con RF-102. Los cobros pagados, rechazados y el resto del
historial se quedan en el club anterior. Se audita en `M-03` `CERRAR_CLUB` sobre el club anterior
(con el motivo y `{ "estado": "CERRADO", "clubNuevoId", "causa": "CAMBIO_NOMBRE" }`) y
`REGISTRAR_CLUB` sobre el nuevo (`{ "estado": "ACTIVO", "clubAnteriorId" }`); por cada asignación
trasladada, en `M-01`, `REVOCAR_ASIGNACION` (motivo «Cambio de nombre del club») y `ASIGNAR_ROL`, con
el mismo detalle que en §6.1. Si cualquier paso falla, no queda nada de lo anterior.

**Afiliación del club (H-08.1, RF-101, CU-018, UI-023, DEC-09).** `GET /afiliaciones?temporadaId=&estado=`
responde la envoltura de paginación (§4) ordenada por fecha de inicio de la temporada descendente y,
dentro de ella, por nombre del club. Un `temporadaId` fuera del ámbito no es un error: la página sale
vacía, y un `estado` sin coincidencias también. El listado y su `totalElementos` filtran por el club
con la unión del alcance de `organizacion.afiliacion:CAPTURAR` (Secretaría y Directiva) y de
`organizacion.afiliacion:APROBAR` (Presidencia), porque el Presidente revisa tarjetones aunque no los
capture. `GET /afiliaciones/{id}`
responde el mismo detalle; un tarjetón de otro club responde `404` igual que uno inexistente
(VAL-024). El detalle lleva `id`, `folio`, `club`, `temporada` (con su `estado`), `coloresUniformes`,
`estado`, `categorias`, `capturadaPor`, `capturadaEn` y, solo si ya se resolvió, `resueltaPor`,
`resueltaEn` y `observaciones`. El `id` del perfil siempre se incluye; su `nombreMostrado` se omite si
las políticas RLS de `perfiles` no permiten verlo al usuario de la sesión.

**Contexto de afiliación (H-08.2).** `GET /afiliaciones/contexto` responde
`{ "temporadas": [{ "id", "nombre", "estado", "fechaInicio", "categorias": [{ "id", "nombre",
"grupos": [{ "id", "nombre", "equipos" }] }] }] }` con las temporadas de la liga de la más reciente
a la más antigua. Es la vía de la pantalla F-16 para que la Directiva capture sin depender de
`temporadas.temporada:CONSULTAR` (M-02): el endpoint se autoriza con cualquiera de los dos permisos
de afiliación y solo devuelve datos de contexto del tarjetón. Las temporadas y categorías son de la
liga, no de un club; el ámbito no las filtra, el permiso sí. Es de solo lectura y no se audita.

`POST /afiliaciones` lleva `{ "clubId", "temporadaId", "coloresUniformes", "categoriaIds" }` y
responde `201` con `Location`. El estado inicial es `SOLICITADA` y el perfil que captura sale de la
sesión; el cuerpo no los envía. El folio lo asigna el sistema como `AF-<año de inicio de la
temporada>-<consecutivo de cuatro dígitos>` **[PROPUESTA]** (el ejemplo `AF-2025-0137` de F-16), único
por temporada: la temporada se bloquea con `FOR UPDATE` antes de contar, así que dos altas
simultáneas no repiten consecutivo. El club debe existir, ser de la liga y estar `ACTIVO`
(`409 RN-041` si está cerrado); la temporada debe ser de la liga (`422 REFERENCIA_INVALIDA`) y no
estar `CERRADA` (`409 RF-003`); se exige al menos una categoría y los colores; todas las categorías
deben participar en la temporada (`422 REFERENCIA_INVALIDA` con la extensión `categoriasInvalidas`).
Un club ya afiliado en esa temporada responde `409 DUPLICADO` con el folio, el estado, y las
extensiones `afiliacionId` y `folio`.

`PATCH /afiliaciones/{id}` edita `coloresUniformes` o `categoriaIds`; lo ausente conserva su valor y
sin cambios responde el tarjetón sin auditar. Solo se edita en `SOLICITADA`: un tarjetón resuelto
responde `409 RF-101`, uno de un club cerrado, `409 RN-041`, y uno de una temporada cerrada,
`409 RF-003`. Las categorías nuevas se validan igual que en el alta y se reemplazan en la misma
transacción.

`POST /afiliaciones/{id}/resolucion` lleva `{ "decision": "APROBADA"|"RECHAZADA", "observaciones" }`
y exige `organizacion.afiliacion:APROBAR` con ámbito del club. Pasar a `APROBADA` o `RECHAZADA` es
terminal: resolver otra vez responde `409 RF-101` y hacerlo con el club cerrado, `409 RN-041`. El
rechazo exige observaciones de 10 a 500 caracteres (`400 RF-101` si faltan, `400 VALIDACION` si
sobran) y las guarda como motivo; la aprobación las admite como nota. En ambos
casos se guardan `resueltaPor` (perfil de la sesión) y `resueltaEn` (reloj del servidor). Las
operaciones se auditan en `M-03` sobre `afiliaciones` como `REGISTRAR_AFILIACION`,
`EDITAR_AFILIACION`, `APROBAR_AFILIACION` y `RECHAZAR_AFILIACION`, con folio y estados, sin datos
personales.

**Directiva del club (H-22.1, RF-102, DEC-03).** `GET /clubes/{id}/directiva` devuelve un arreglo con
los cargos actuales, pendientes y anteriores del club, ordenados por cargo, pendientes primero y
fecha `desde` descendente. Cada elemento contiene `id`, `clubId`, `personaId`, `nombrePersona`,
`cargo`, `desde`, `hasta`, `estado` (`PENDIENTE` o `AUTORIZADO`) y `autorizadoPor`; los campos sin
valor se omiten. Consulta con `organizacion.club:CONSULTAR` y ámbito del club.

`POST /clubes/{id}/directiva` lleva `{ "personaId", "cargo", "desde" }`, donde `cargo` es
`PRESIDENTE`, `SECRETARIO`, `TESORERO` o `DELEGADO`. Responde `201` con `Location` y el cargo en
estado `PENDIENTE`; `autorizadoPor` y `hasta` se omiten. Requiere `organizacion.directiva:EDITAR`
con ámbito del club. Un club fuera de ámbito o inexistente responde `404`; una persona inexistente
responde `422 REFERENCIA_INVALIDA`.

`POST /cargos-directiva/{id}/autorizacion` no lleva cuerpo y responde `200` con el cargo autorizado.
Requiere `organizacion.directiva:APROBAR` con ámbito del club; el perfil de la sesión se guarda en
`autorizadoPor`. En la misma transacción termina el cargo autorizado vigente de igual tipo en ese
club el día anterior al `desde` nuevo, y autoriza el cambio. El inicio nuevo debe ser posterior al
del cargo anterior; volver a autorizar un cambio ya autorizado o una vigencia que se traslapa
responde `409 VAL-003`. Ambas operaciones se auditan en `M-03` como `REGISTRAR_CARGO_DIRECTIVA` y
`AUTORIZAR_CARGO_DIRECTIVA`, sin nombres ni otros datos personales en la auditoría.

**Persona por CURP (H-22.2, H-09.3, RF-010, RF-011, DEC-104).** `GET /personas?curp=` responde
`{ "id", "curp", "nombre", "apellidoPaterno", "apellidoMaterno", "esMenor" }`; `apellidoMaterno` se
omite si no existe. Es la consulta mínima que necesita F-15 para enviar el `personaId` de un cambio de
directiva y confirmar el nombre antes de registrarlo. `esMenor` (boolean) se calcula con
`es_menor(id, fecha)` y la fecha de hoy en `America/Mexico_City`, la misma con la que el alta de un
registro decide VAL-025 (DEC-89): con él, el asistente de alta (F-18) sabe si pide tutor y quién otorga
el consentimiento, también para una persona de otro club. No se expone la fecha de nacimiento. La CURP se normaliza a mayúsculas y debe cumplir el
formato de 18 caracteres de `ck_personas_curp` (`400 VALIDACION` si no); una CURP sin persona
responde `404 NO_ENCONTRADO` (VAL-024). Exige `organizacion.persona:CONSULTAR` en **cualquier
ámbito** (se comprueba con `autorizacion.alcance`): la identidad es global y no pertenece a un club,
pero la consulta exige la CURP exacta y no permite listar personas. Desde H-09.1 la consulta usa la
función `persona_por_curp` (`SECURITY DEFINER`, DEC-78), así que encuentra también a las personas que
RLS no muestra al club (por ejemplo, quien jugó en otro club); devuelve solo estos campos. La
respuesta lleva `Cache-Control: no-store` porque incluye datos personales (RNF-012).

**Alta de una persona (H-09.1, RF-010, DEC-62, DEC-84).** `POST /personas` recibe
`{ "curp", "nombre", "apellidoPaterno", "apellidoMaterno"?, "fechaNacimiento", "correo"?, "telefono"? }`
y responde `201` con `Location: /api/v1/personas/{id}`, `Cache-Control: no-store` y el detalle
`{ "id", "curp", "nombre", "apellidoPaterno", "apellidoMaterno", "fechaNacimiento", "correo", "telefono" }`
(los opcionales vacíos se omiten). Exige `organizacion.persona:EDITAR` en cualquier ámbito. Orden:

1. Sin el permiso: `403 VAL-015`.
2. Datos: CURP con el formato de `ck_personas_curp`, nombre y apellido paterno obligatorios (hasta 100
   caracteres), fecha de nacimiento obligatoria y no futura en `America/Mexico_City`, correo con el
   formato de `ck_personas_correo` y teléfono de hasta 20 caracteres **[PROPUESTA]**; si no,
   `400 VALIDACION`. Los textos se recortan y un opcional vacío se guarda como `NULL`.
3. La CURP ya existe en cualquier club (`persona_por_curp`, aunque RLS no la muestre): `409 RF-010`
   con la extensión `personaId` y un `detail` que pide reutilizarla. Si una carrera inserta la misma
   CURP antes, el `INSERT … ON CONFLICT (curp) DO NOTHING` no inserta y se responde igual.

`creado_por` es el perfil de la sesión. Se audita en `M-03` como `REGISTRAR_PERSONA` (entidad
`personas`) sin detalle.

**Edición de una persona (H-09.1, RF-010, RF-011, DEC-77, DEC-83).** `PATCH /personas/{id}` recibe
un cuerpo parcial con los mismos campos del alta: un campo ausente o `null` conserva su valor y una
cadena vacía en `apellidoMaterno`, `correo` o `telefono` borra el dato **[PROPUESTA]**. Responde `200`
con el detalle y `Cache-Control: no-store`. Orden:

1. Sin `organizacion.persona:EDITAR` en ningún ámbito: `403 VAL-015`.
2. Cuerpo sin campos: `400 VALIDACION`.
3. La persona no existe o RLS no la deja ver al usuario: `404 NO_ENCONTRADO`.
4. Correo y teléfono se editan con el permiso en cualquier ámbito. Cambiar CURP, nombre, apellidos o
   fecha de nacimiento exige el permiso con ámbito de **toda la liga**; si no, `403 VAL-015` con un
   mensaje que explica que esos datos los corrige el Secretario de la liga. Un campo de identidad
   igual al actual no cuenta como cambio **[PROPUESTA]**.
5. Datos con las reglas del alta: `400 VALIDACION`.
6. Una CURP nueva que ya tiene otra persona: `409 DUPLICADO`.

Sin cambios reales se responde la persona sin escribir ni auditar. Si hay cambios, se actualiza
`actualizado_en` y se audita en `M-03` como `EDITAR_PERSONA` con `{ "campos": [ … ] }`: solo los
**nombres** de los campos cambiados, nunca sus valores.

**Tutela (H-09.1, RF-106, DEC-75, DEC-79, DEC-82).** `POST /personas/{id}/tutelas` vincula al menor
`{id}` con `{ "tutorId", "parentesco" }` y responde `201` con
`{ "id", "menorId", "tutorId", "parentesco", "vigente", "creadoEn" }`, sin `Location` (todavía no existe
un `GET` del recurso). Exige `organizacion.persona:EDITAR` en cualquier ámbito. La cuenta del tutor no
se exige: se crea después con la invitación de H-03 (DEC-75). Orden:

1. Sin el permiso: `403 VAL-015`.
2. Sin `tutorId`, o `parentesco` vacío o de más de 40 caracteres tras recortarlo: `400 VALIDACION`.
3. El menor no existe: `404 NO_ENCONTRADO`.
4. El tutor es la persona de la cuenta que hace la petición (`persona_usuario()`): `403 VAL-022`
   (separación de funciones, DEC-79).
5. La persona no es menor de edad: `422 RF-106`.
6. El tutor no existe: `422 REFERENCIA_INVALIDA`.
7. El tutor es menor de edad: `422 RF-106`.
8. Ya hay una tutela vigente entre ambos (`ux_tutelas_vigente`): `409 DUPLICADO`.

`creado_por` es el perfil de la sesión. Se audita en `M-03` como `REGISTRAR_TUTELA` (entidad
`tutelas`) con `menorId` y `tutorId` en el detalle, sin parentesco ni otros datos personales.

**Equipo.** `GET /equipos?temporadaId=&grupoId=&clubId=&estado=&pagina=&tamano=` responde la envoltura
de paginación ordenada por el orden de la categoría, el nombre del grupo y el nombre del equipo.
Cada equipo trae referencias anidadas (§9.1):

```json
{
  "id": "…",
  "nombre": "Halcones",
  "estado": "ACTIVO",
  "club": { "id": "…", "nombre": "Club Halcones" },
  "grupo": { "id": "…", "nombre": "A" },
  "categoria": { "id": "…", "nombre": "Libre" },
  "temporada": { "id": "…", "nombre": "Temporada 2026", "estado": "ACTIVA" },
  "creadoEn": "2026-11-03T10:00:00-06:00"
}
```

`bajaEn` y `motivoBaja` solo aparecen en un equipo `BAJA`. `GET /equipos/{id}` responde ese detalle.

- `POST /equipos` lleva `{ "clubId", "grupoId", "nombre" }` y responde `201` con `Location`; el equipo
  nace `ACTIVO`. Se valida en este orden: club inexistente o fuera de ámbito, `404`; club `CERRADO`,
  `409 RN-041`; grupo inexistente, `422 REFERENCIA_INVALIDA`; temporada del grupo `CERRADA`,
  `409 RF-003`; nombre repetido en el grupo, `409 DUPLICADO`. Sin nombre: `400 VALIDACION`.
- `PATCH /equipos/{id}` **[PROPUESTA]** acepta solo `nombre` y `grupoId`; lo ausente conserva su
  valor. El club no se edita (la venta es B-05) y el estado cambia solo con la baja. Errores, en este
  orden: equipo `BAJA`, `409 RF-009`; temporada `CERRADA`, `409 RF-003`; cambio de grupo con la
  temporada `ACTIVA`, `409 RN-007`; grupo inexistente, `422 REFERENCIA_INVALIDA`; grupo de otra
  temporada, `422 RN-007`; nombre repetido en el grupo, `409 DUPLICADO`. Un `PATCH` sin cambios
  responde `200` y no se audita.
- `POST /equipos/{id}/baja` lleva `{ "motivo" }` (mínimo 10 caracteres), pasa `ACTIVO → BAJA` con
  la fecha del día en la zona de negocio, fija el motivo (RN-021) y responde `200` con el equipo. La
  fila nunca se borra (RN-005). Errores: ya está de baja, `409 RF-009`; temporada `CERRADA`,
  `409 RF-003`; sin motivo, `400 RN-021`. Los efectos de la baja sobre los partidos del equipo
  (RN-056, RF-130, RF-132) son B.
- Crear, editar y dar de baja solo con la temporada del grupo en `CONFIGURACION` o `ACTIVA`.
- Se audita en el módulo `M-03` como `REGISTRAR_EQUIPO`, `EDITAR_EQUIPO` (con los `campos`
  cambiados) y `DAR_BAJA_EQUIPO` (con el motivo); el detalle lleva solo identificadores y estados.

**Venta de equipo y conservación de plaza (B-05.1, RF-103, RF-104, RN-040, DEC-14).**
`POST /operaciones-equipo` lleva `{ "tipo": "VENTA"|"CONSERVACION_PLAZA", "equipoId",
"clubDestinoId"?, "observaciones"? }` y deja la operación en `SOLICITADA` con el perfil que la
registra. `POST /operaciones-equipo/{id}/resolucion` lleva `{ "decision":
"AUTORIZADA"|"RECHAZADA", "observaciones"? }` y solo lo usa el Presidente
(`organizacion.venta:APROBAR`). Registrar exige `organizacion.venta:SOLICITAR` sobre el club actual
del equipo. Un equipo o una operación de otro club responde `404` igual que uno inexistente
(VAL-024).

- **Consulta (B-05.3).** `GET /operaciones-equipo?temporadaId=&estado=&pagina=&tamano=` es la
  bandeja de B-09, de la solicitud más reciente a la más antigua; usa el alcance unificado de
  `SOLICITAR` y `APROBAR` en el `WHERE` y en el `COUNT`, así que la Directiva ve las operaciones de
  su club y el Presidente las de la liga. Un estado o una temporada fuera del alcance dan una página
  vacía, no un error. `GET /operaciones-equipo/{id}` responde la operación y, si es de otro club,
  `404` igual que una inexistente (VAL-024). `GET /operaciones-equipo/contexto` entrega los clubes
  `ACTIVO` de la liga para elegir el destino de una venta; es un catálogo de identificador y nombre,
  sin datos personales, que la Directiva necesita completo aunque su ámbito de consulta de clubes
  solo alcance el propio.
- **VENTA** exige `clubDestinoId` y que sea distinto del club actual del equipo; ausente o repetido,
  `400 VALIDACION`. **CONSERVACION_PLAZA** es una operación independiente de la venta (RF-104) y no
  lleva club destino; si lo lleva, `400 VALIDACION`. Las dos pueden convivir para el mismo equipo y
  no se bloquean entre sí.
- **Una vigente por tipo (DEC-132).** Un equipo no puede tener dos operaciones del mismo tipo en
  `SOLICITADA` o `AUTORIZADA`: otra venta responde `409 RF-103` y otra conservación de plaza
  `409 RF-104`. Las `RECHAZADA` y `APLICADA` no cuentan. El equipo se lee con su candado, así que
  dos solicitudes simultáneas se serializan.
- Orden de errores al registrar: sin `tipo` o `equipoId`, `400 VALIDACION`; equipo inexistente o
  fuera de ámbito, `404`; sin permiso, `403 VAL-015`; temporada `CERRADA`, `409 RF-003`; equipo
  `BAJA`, `409 RF-009`; reglas del tipo (venta sin club destino o con el de origen, conservación con
  club destino), `400 VALIDACION`; club destino inexistente, `422 REFERENCIA_INVALIDA`; club destino
  `CERRADO`, `409 RN-041`; observaciones de más de 500 caracteres, `400 VALIDACION`; operación
  vigente del mismo tipo, `409 RF-103` o `RF-104`.
- Resolver: operación inexistente o de otro club, `404`; sin permiso, `403 VAL-015`; ya resuelta,
  `409 RF-103` (venta) o `RF-104` (conservación); sin `decision` u observaciones de más de 500
  caracteres, `400 VALIDACION`; al autorizar, temporada `CERRADA`, `409 RF-003`, y club destino de
  la venta `CERRADO`, `409 RN-041` (lo que pudo cambiar desde la solicitud); al rechazar sin
  observaciones de 10 a 500 caracteres, `400 RN-021`. La autorización admite una nota opcional que
  reemplaza las observaciones; sin nota se conservan las de la solicitud. Solo `SOLICITADA` se resuelve: una
  operación `AUTORIZADA` pasa a `APLICADA` al cerrar la temporada (H-04.1), que es cuando el equipo
  cambia de club; hasta entonces conserva a su propietario.
- Respuesta `201` con `Location: /api/v1/operaciones-equipo/{id}` y `200` con la operación
  resuelta. Campos: `id`, `tipo`, `estado`, `equipo`, `clubOrigen`, `clubDestino` solo en venta,
  `temporada`, `solicitadaPor`, `solicitadaEn`, `autorizadaPor`, `autorizadaEn`, `aplicadaEn` y
  `observaciones`.
- Se audita en `M-03` sobre `operaciones_equipo`: `SOLICITAR_OPERACION_EQUIPO` con tipo, equipo,
  clubes, estado y, si las lleva, las observaciones de la solicitud (quedan aunque el dictamen las
  reemplace en la fila); `AUTORIZAR_OPERACION_EQUIPO` y `RECHAZAR_OPERACION_EQUIPO` con el estado anterior
  y el nuevo, y el rechazo con las observaciones como motivo. La fila nunca se borra: es el
  historial de propietarios (RN-040).

### 6.4 M-04 Registros y elegibilidad

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /registros-jugador?estado=&temporadaId=&clubId=&equipoId=&orden=&pagina=&tamano=` | Registros de su ámbito, paginados (§4). `estado` admite varios valores separados por coma; `orden`: `creadoEn,asc` (por defecto), `creadoEn,desc` o `nombre,asc`. Filas con la CURP enmascarada; detalle abajo | `registros.registro:CONSULTAR` (ámbito en el `WHERE` y en el `COUNT`) | RF-017 | N |
| `GET /registros-jugador/{id}` | Detalle con `esMenor`, tutores vigentes del menor y la CURP completa solo con `organizacion.persona:CONSULTAR` sobre el club; detalle abajo | `registros.registro:CONSULTAR` (club o persona del registro) | RF-017 | N |
| `POST /registros-jugador` | Alta: `{ personaId, equipoId }`; queda `PENDIENTE_VALIDACION`. `409 VAL-017` dice club y equipo donde ya está registrada la CURP; detalle abajo | `registros.registro:EDITAR` (club del equipo) | RF-013, RF-107, VAL-025 | N |
| `GET /registros-jugador/{id}/requisitos` | Documentos que exige su categoría y cuáles faltan; detalle abajo | `registros.registro:CONSULTAR` (club o persona del registro) | RF-108, VAL-001 | N |
| `POST /registros-jugador/{id}/validacion` | Validar o rechazar; detalle abajo | `registros.registro:VALIDAR` (club del equipo) | RF-109 | N |
| `POST /registros-jugador/{id}/baja` | Dar de baja un registro `ACTIVO` con motivo; detalle abajo | `registros.registro:EDITAR` (club del equipo) | RF-014 | N |
| `GET /registros-jugador/{id}/historial` | Cambios de estado con usuario y motivo, del más antiguo al más reciente; detalle abajo | `registros.registro:CONSULTAR` (club del registro) | RF-018 | B |
| `GET /registros-jugador/{id}/elegibilidad?equipoId=` | `{ elegible, causas: [ { codigo, mensaje } ] }` | `registros.registro:CONSULTAR` | RF-019, RF-020, RF-110 | N |
| `GET /registros-jugador/movimientos/opcion?personaId=&equipoId=` | Movimiento entre temporadas que el asistente de alta puede ofrecer: `{ tipo, permitido, temporadaAnterior, motivo }`; detalle abajo | `registros.registro:EDITAR` (club del equipo de destino) | RF-015, RF-016, RF-111 | B |
| `POST /registros-jugador/renovaciones` · `/transferencias` | Renovación en el mismo club (RF-015) o cambio de club entre temporadas (RF-016): `{ personaId, equipoId }`; queda `PENDIENTE_VALIDACION` y enlaza el registro anterior; detalle abajo | `registros.registro:EDITAR` (club del equipo de destino) | RF-015, RF-016, RF-111 | B |
| Documentos | Carga, reemplazo, enlace y validación de cada documento; detalle abajo y en §7 | `registros.documento:*` | RF-108, RF-109 | N |
| `GET /mi-informacion` | `{ titulares: [ { personaId, nombre, esPropia, esMenor } ] }`: la persona de la cuenta y los menores bajo su tutela vigente; detalle abajo | `registros.registro:CONSULTAR` (cualquier ámbito) | RF-144 | N |
| `GET /mi-informacion/{personaId}` | Registros, documentos, elegibilidad, partidos y sanciones de esa persona; solo si es la de la cuenta o un menor bajo su tutela vigente (`es_titular`); detalle abajo | `registros.registro:CONSULTAR` (cualquier ámbito) + `es_titular` | RF-144, RF-019, RF-020 | N |

#### Alta de un registro (H-09.2, RF-013, RF-107, RF-111, VAL-017, VAL-025, DEC-85 a DEC-90, DEC-154)

`POST /registros-jugador` recibe `{ "personaId", "equipoId" }` y registra a la persona en el equipo;
la temporada es la del grupo del equipo. Exige `registros.registro:EDITAR` sobre el **club del
equipo** (`ObjetivoAutorizacion.deClubes`); hoy solo la Directiva de club lo tiene, con ámbito
`CLUB`. Los obligatorios los valida el servicio después del permiso, así que el cuerpo no lleva
`@Valid`. Orden de errores:

1. Sin el permiso en ningún ámbito: `403 VAL-015`.
2. Falta `personaId` o `equipoId`: `400 VALIDACION`.
3. El equipo no existe en la liga, o es de un club fuera del ámbito del usuario: `404 NO_ENCONTRADO`,
   sin distinguir los dos casos (VAL-024).
4. La temporada del equipo está `CERRADA`: `409 RF-003` (se aceptan `CONFIGURACION` y `ACTIVA`).
5. El equipo está en `BAJA`: `422 RF-013`.
6. El club no tiene afiliación `APROBADA` en la temporada: `409 RF-101`, con un `detail` que pide
   tramitar el tarjetón o esperar su aprobación. No se cruza la categoría con el tarjetón.
7. La persona no existe: `422 REFERENCIA_INVALIDA`. Se comprueba con `es_menor`, que ve a personas
   que RLS no muestra al club (identidad global, DEC-78).
8. La persona ya tiene un registro `PENDIENTE_VALIDACION`, `ACTIVO` o `SUSPENDIDO` en la temporada,
   en cualquier club: `409 VAL-017`. El `detail` nombra el club y el equipo (o dice «tu club» o «este
   equipo») y el problema lleva las extensiones `club` y `equipo` con sus **nombres**:

   ```json
   {
     "status": 409,
     "codigo": "VAL-017",
     "detail": "Esta persona ya está registrada en esta temporada en el club «Club Águilas», equipo «Águilas». Un jugador solo puede estar en un equipo por temporada.",
     "club": "Club Águilas",
     "equipo": "Águilas"
   }
   ```

   Nunca se exponen ids, categoría ni estado de ese registro (un `SUSPENDIDO` revelaría una sanción).
9. Sin registro activo, pero con uno en `BAJA` en **otro club** de la temporada: `409 VAL-026`, sin
   nombrar el club.
10. Con uno en `BAJA` en **otro equipo del mismo club**: `409 RN-039` (dentro de una temporada no hay
    cambio de equipo, RF-016). Registrarse de nuevo en el mismo equipo sí se permite. Los registros
    `RECHAZADO` no cuentan en los pasos 8 a 10.
11. El registro anterior más reciente de la persona (`registro_anterior`) es de **otro club** y la
    versión vigente de parámetros de la temporada no permite el cambio de club: `409 RF-111`, con el
    mismo mensaje de la transferencia y sin nombrar al otro club (DEC-154). El alta no obliga a usar
    la renovación o la transferencia (pendiente, DEC-146) y no enlaza el registro anterior.
12. La persona es menor en la fecha de alta: sin tutela vigente, `422 VAL-025` con
    `"requisito": "TUTOR"`; con tutor pero sin consentimiento no revocado otorgado por un tutor
    vigente, `422 VAL-025` con `"requisito": "CONSENTIMIENTO"`. Es la misma condición que
    `tg_registros_validar` al activar. No se exige aviso vigente ni una finalidad concreta
    **[PROPUESTA]**; el INE del tutor es un documento y se exige al validar (H-10.2), no aquí. A los
    adultos no se les exige consentimiento (DEC-76).

La fecha de alta (`alta_en`) la calcula el servicio en `America/Mexico_City` y se envía en el
`INSERT`; con ella se decide si la persona es menor. Los registros de otros clubes y los requisitos
del menor se leen con `registros_en_temporada` y `requisitos_menor` (`SECURITY DEFINER`, solo
`siglim_app`). El `INSERT … ON CONFLICT … DO NOTHING` sobre `ux_registros_activo` resuelve una
carrera sin abortar la transacción: si otra petición registró antes a la persona, se responde el
mismo `409 VAL-017` con los nombres. Si aun así llega un `23505` de ese índice, se traduce como
`VAL-017` por su comentario (§3.2).

Responde `201` con `Location: /api/v1/registros-jugador/{id}` (DEC-109) y con
`Cache-Control: no-store`, con referencias anidadas (§9.1) y sin CURP (§9.2):

```json
{
  "id": "…",
  "persona": { "id": "…", "nombreCompleto": "Ana López Ruiz" },
  "equipo": { "id": "…", "nombre": "Halcones" },
  "club": { "id": "…", "nombre": "Club Halcones" },
  "categoria": { "id": "…", "nombre": "Libre" },
  "temporada": { "id": "…", "nombre": "Temporada 2026", "estado": "ACTIVA" },
  "estado": "PENDIENTE_VALIDACION",
  "altaEn": "2026-11-11",
  "creadoEn": "2026-11-11T12:00:00-06:00",
  "validadoPor": null,
  "validadoEn": null,
  "observaciones": null,
  "bajaEn": null,
  "motivoBaja": null
}
```

`validadoPor`, `validadoEn` y `observaciones` se llenan al validar el registro (H-10.2, abajo);
`bajaEn` y `motivoBaja`, al darlo de baja (H-23.1, abajo).
`creado_por` es el perfil de la sesión; el historial lo escribe `tg_registros_historial` con el
motivo «Alta del registro». Se audita en `M-04` como `REGISTRAR_REGISTRO_JUGADOR` (entidad
`registros_jugador`) con `{ "personaId", "equipoId", "temporadaId", "estado": "PENDIENTE_VALIDACION" }`,
sin datos personales.

#### Consulta de registros (H-10.5, RF-017, DEC-109, DEC-110)

Los dos endpoints exigen `registros.registro:CONSULTAR` y responden `200` con
`Cache-Control: no-store`. Hoy lo tienen el Secretario y la Comisión Disciplinaria (ámbito `LIGA`),
la Directiva de club (`CLUB`) y el jugador o su tutor (`PROPIO`). Las consultas no se auditan. Los
usan la bandeja de validación y los documentos del registro (H-10.3) y la plantilla del club
(H-09.4).

**Listado.** `GET /registros-jugador` filtra por ámbito en el SQL con `autorizacion.alcance` y la
**misma condición en el `WHERE` y en el `COUNT`**, así que `totalElementos` cuenta solo las filas
visibles (§4, ADR-0007):

```sql
(:todaLaLiga OR e.club_id = ANY(:clubes) OR (:propio AND es_titular(r.persona_id)))
```

| Parámetro | Valores |
| --- | --- |
| `estado` | Uno o varios de `PENDIENTE_VALIDACION`, `ACTIVO`, `SUSPENDIDO`, `BAJA`, `RECHAZADO`, separados por coma. Sin él, todos. |
| `temporadaId`, `clubId`, `equipoId` | Opcionales. Uno fuera del ámbito del usuario devuelve una página vacía, no `403` ni `404`. |
| `orden` | `creadoEn,asc` (por defecto), `creadoEn,desc` o `nombre,asc` (apellido paterno, apellido materno y nombre). Siempre desempata por `id`. |
| `pagina`, `tamano` | `pagina` desde 0; `tamano` 20 por defecto, de 1 a 100. |

Orden de errores: sin el permiso en ningún ámbito, `403 VAL-015`; un `estado` u `orden`
desconocido o un `tamano` o `pagina` fuera de rango, `400 VALIDACION` (lo valida el servicio
después del permiso); un id que no es UUID, `400 VALIDACION` antes del servicio.

Cada fila lleva la CURP **enmascarada** (§9.2, DEC-110) y `esMenor` calculado en la fecha de alta
(`es_menor(persona, alta_en)`, DEC-89 y DEC-92); no lleva CURP completa, fecha de nacimiento,
contacto, datos de validación ni avance de documentos (ese avance es
`GET /registros-jugador/{id}/requisitos`):

```json
{
  "contenido": [
    {
      "id": "…",
      "persona": { "id": "…", "nombreCompleto": "Ana López Ruiz", "curpEnmascarada": "LORA******MMN***05" },
      "equipo": { "id": "…", "nombre": "Halcones" },
      "club": { "id": "…", "nombre": "Club Halcones" },
      "categoria": { "id": "…", "nombre": "Libre" },
      "temporada": { "id": "…", "nombre": "Temporada 2026", "estado": "ACTIVA" },
      "estado": "PENDIENTE_VALIDACION",
      "esMenor": false,
      "altaEn": "2026-11-11",
      "creadoEn": "2026-11-11T12:00:00-06:00"
    }
  ],
  "pagina": 0,
  "tamano": 20,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

**Detalle.** `GET /registros-jugador/{id}`. Orden de errores, igual que los requisitos:

1. Sin el permiso en ningún ámbito: `403 VAL-015`.
2. El registro no existe en la liga: `404 NO_ENCONTRADO`.
3. El registro es de un club fuera del ámbito y de una persona de la que el usuario no es titular
   (`ObjetivoAutorizacion.deClubesYPersonas`): `404 NO_ENCONTRADO`, igual que uno inexistente
   (VAL-024).

Responde los campos del alta (`validadoPor`, `validadoEn`, `observaciones`, `bajaEn` y `motivoBaja`
incluidos) más `esMenor` y `tutores`; `persona` lleva siempre `curpEnmascarada` y `curp` **completa
solo si la sesión tiene `organizacion.persona:CONSULTAR` sobre el club del registro** (hoy el
Secretario y la Directiva de
ese club); si no (la Comisión, el jugador o su tutor), `curp` es `null` (DEC-110). El backend lo
pregunta con `autorizacion.permite(...)`, que no lanza el `403` ni el `404`. `tutores` son las
tutelas vigentes del menor por fecha de creación, solo con nombre y parentesco (sin CURP ni
contacto); es una lista vacía si la persona no era menor en la fecha de alta. Un tutor que la sesión
no puede ver por RLS no aparece.

```json
{
  "id": "…",
  "persona": {
    "id": "…",
    "nombreCompleto": "Luis López Ruiz",
    "curpEnmascarada": "LORL******HMN***08",
    "curp": "LORL150101HMNPZS08"
  },
  "equipo": { "id": "…", "nombre": "Halcones Infantil" },
  "club": { "id": "…", "nombre": "Club Halcones" },
  "categoria": { "id": "…", "nombre": "Infantil" },
  "temporada": { "id": "…", "nombre": "Temporada 2026", "estado": "ACTIVA" },
  "estado": "PENDIENTE_VALIDACION",
  "esMenor": true,
  "altaEn": "2026-11-11",
  "creadoEn": "2026-11-11T12:00:00-06:00",
  "validadoPor": null,
  "validadoEn": null,
  "observaciones": null,
  "bajaEn": null,
  "motivoBaja": null,
  "tutores": [ { "nombreCompleto": "María Ruiz Pérez", "parentesco": "Madre" } ]
}
```

#### Documentos de un registro (H-10.1, RF-108, RF-109, DEC-91 a DEC-96)

Los documentos de un registro de jugador se cargan como `multipart/form-data` y se guardan en S3 en
la carpeta `registros` (§7, ADR-0013). Todas las respuestas llevan `Cache-Control: no-store`.

| Método y ruta | Cuerpo | Éxito | Permiso y objetivo |
| --- | --- | --- | --- |
| `POST /registros-jugador/{id}/documentos` | `archivo` + `tipoDocumentoId` | `201` sin `Location`, documento | `registros.documento:CREAR`, club del equipo |
| `POST /documentos/{id}/reemplazo` | `archivo` | `201` sin `Location`, documento nuevo | `registros.documento:CREAR`, club del equipo |
| `GET /registros-jugador/{id}/requisitos` | — | `200` requisitos | `registros.registro:CONSULTAR`, club o persona del registro |
| `GET /documentos/{id}/enlace` | — | `200 { url, expiraEn }` | `registros.documento:CONSULTAR`, club o persona del registro |
| `POST /documentos/{id}/validacion` | JSON `{ decision, observaciones? }` | `200` documento | `registros.documento:VALIDAR`, club del equipo |

Hoy carga y reemplaza la Directiva de club (ámbito `CLUB`) y valida el Secretario (ámbito `LIGA`).
Los requisitos y el enlace se autorizan con el club **y la persona** del registro
(`ObjetivoAutorizacion.deClubesYPersonas`), así que el jugador o su tutor (ámbito `PROPIO`) también
llegan. Las rutas `/documentos/{id}/…` solo atienden documentos de un registro: uno de pago,
afiliación, expediente o persona responde `404` hasta que su historia lo implemente. No hay
`Location` porque no existe `GET /documentos/{id}`.

**Orden de errores común (DEC-91):**

1. Sin el permiso en ningún ámbito: `403 VAL-015`.
2. Faltan datos o tienen otro formato: `400 VALIDACION`. Los obligatorios los valida el servicio
   después del permiso (el controlador recibe el archivo como opcional); un `tipoDocumentoId` que no
   es número, o una `decision` que no es del enum, no se pueden leer y responden `400 VALIDACION`
   antes del servicio.
3. Registro o documento inexistente en la liga, o fuera de ámbito: `404 NO_ENCONTRADO`, sin
   distinguirlos (VAL-024).
4. Las reglas de cada endpoint (abajo).
5. Archivo vacío (`400 VALIDACION`), mayor que 5 MB (`413 ARCHIVO_DEMASIADO_GRANDE`) o con una firma
   que no es JPEG, PNG, WebP ni PDF (`415 TIPO_ARCHIVO_NO_PERMITIDO`). Un archivo mayor que
   `spring.servlet.multipart.max-file-size` lo corta Spring antes del controlador, así que ese `413`
   llega antes que el `403` o el `404`.

Se guarda el MIME detectado por la firma; el nombre del archivo y su `Content-Type` declarado se
ignoran y no se guardan ni se registran.

**Carga.** Reglas, en orden: temporada `CERRADA` → `409 RF-003`; registro que no está
`PENDIENTE_VALIDACION` → `409 RF-017`; `tipoDocumentoId` inexistente → `422 REFERENCIA_INVALIDA`; tipo
que el registro no exige (otra categoría, `INE_TUTOR` de un adulto, `TARJETON_FIRMADO`…) →
`422 RF-108`, con un `detail` que lo explica; cupo lleno → `409 DUPLICADO`, con un `detail` que pide
reemplazar uno. Los requisitos son las filas de `requisitos_documento` de la liga para el `tipo` de
la categoría del equipo; las de `solo_menores` se exigen si la persona era menor en la fecha de alta
(`es_menor(persona, alta_en)`). El INE del tutor es un documento del registro. Un tipo admite
`cantidad` documentos **vigentes** (sin `reemplazadoPor`) de cualquier estado: un rechazado ocupa su
lugar hasta que se reemplaza.

**Reemplazo (DEC-93).** Mismas reglas de temporada y registro; solo sobre un documento vigente
`PENDIENTE` o `RECHAZADO` (`APROBADO` o ya reemplazado → `409 RF-109`). El nuevo queda `PENDIENTE` con
el mismo tipo y registro; el anterior conserva su estado y su fila. Toda escritura bloquea primero el
registro (`FOR UPDATE`) y los `UPDATE` de documentos son condicionales: si otra petición lo
reemplazó, aprobó o rechazó antes, `409 RF-109`.

**Validación de un documento (DEC-96).** `{ "decision": "APROBADO" | "RECHAZADO", "observaciones" }`.
Sin `decision` → `400 VALIDACION`; observaciones de más de 500 caracteres → `400 VALIDACION`; rechazo
sin observaciones de al menos 10 caracteres (sin contar los espacios de los extremos) → `400 RF-109`.
Después del `404`: quien valida es quien cargó el documento → `403 VAL-022` **[PROPUESTA]**
(separación de funciones, como DEC-79); temporada `CERRADA` → `409 RF-003`; registro no
`PENDIENTE_VALIDACION` → `409 RF-017`; documento reemplazado o que ya no está `PENDIENTE` →
`409 RF-109`. Guarda `estadoValidacion`, `validadoPor`, `validadoEn` y `observaciones`; **no** cambia
el estado del registro (eso es `POST /registros-jugador/{id}/validacion`, H-10.2, con VAL-001).

Documento (nunca `s3Bucket`, `s3Clave`, `url` ni `sha256`):

```json
{
  "id": "…",
  "registroId": "…",
  "tipoDocumento": { "id": 5, "codigo": "FOTOGRAFIA", "nombre": "Fotografía" },
  "tipoMime": "image/jpeg",
  "tamanoBytes": 183204,
  "estadoValidacion": "PENDIENTE",
  "observaciones": null,
  "validadoPor": null,
  "validadoEn": null,
  "reemplazaA": null,
  "cargadoEn": "2026-11-12T12:00:00-06:00"
}
```

`validadoPor` es `{ "id", "nombre" }` con el nombre mostrado del perfil (`null` si el usuario no
puede ver ese perfil); `reemplazaA`, el documento al que sustituye.

**Requisitos (DEC-92, DEC-96).** Solo `403` y `404`. Responde:

```json
{
  "registroId": "…",
  "estadoRegistro": "PENDIENTE_VALIDACION",
  "categoria": { "id": "…", "nombre": "Infantil", "tipo": "INFANTIL" },
  "esMenor": true,
  "requisitos": [
    {
      "tipoDocumento": { "id": 5, "codigo": "FOTOGRAFIA", "nombre": "Fotografía" },
      "cantidad": 2, "soloMenores": false, "esDemo": true,
      "cargados": 1, "aprobados": 0, "faltan": 1,
      "documentos": [ { "…documento vigente…": "" } ]
    }
  ],
  "completo": false,
  "listoParaValidar": false
}
```

`cargados` cuenta los vigentes no rechazados, `aprobados` los vigentes `APROBADO` y
`faltan = max(0, cantidad - cargados)`; `documentos` son los vigentes de cualquier estado, por fecha
de carga, para que la Directiva vea las observaciones de un rechazo. `completo` significa todos los
requisitos cargados y `listoParaValidar`, todos aprobados. `esDemo` marca un valor de demostración
que la interfaz rotula «no oficial». Los requisitos van en el orden de la semilla
(`tipo_documento_id`).

**Enlace (DEC-95).** Solo `403`, `404` y `503 SERVICIO_NO_DISPONIBLE` si no se puede firmar. Un
documento reemplazado también se consulta (es historial). La URL prefirmada vence a los 60 s y nunca
se guarda, se audita ni va a la bitácora.

**Auditoría (DEC-95).** En `M-04`, entidad `documentos`, en la misma transacción:
`CARGAR_DOCUMENTO` `{ registroId, tipoDocumento, tipoMime, tamanoBytes }`, `REEMPLAZAR_DOCUMENTO`
(lo mismo más `documentoAnteriorId`), `VALIDAR_DOCUMENTO` `{ registroId, tipoDocumento, decision }`
con las observaciones como motivo, y `CONSULTAR_DOCUMENTO` `{ registroId, tipoDocumento }` al generar
el enlace. `tipoDocumento` es el código. Nunca la URL, el bucket, la clave, el SHA-256, el nombre del
archivo ni datos personales.

#### Validación de un registro (H-10.2, RF-109, VAL-001, DEC-97 a DEC-102)

`POST /registros-jugador/{id}/validacion` recibe `{ "decision": "ACTIVO" | "RECHAZADO",
"observaciones"? }` y exige `registros.registro:VALIDAR` sobre el **club del equipo** del registro
(`ObjetivoAutorizacion.deClubes`); hoy solo el Secretario lo tiene, con ámbito `LIGA`. Nunca se
comparan roles. Los obligatorios los valida el servicio después del permiso, así que el cuerpo no
lleva `@Valid`. Quien aprueba los documentos y quien valida el registro pueden ser la misma persona.
Orden de errores:

1. Sin el permiso en ningún ámbito: `403 VAL-015`.
2. Falta `decision`: `400 VALIDACION`. Un valor que no es del enum no se puede leer y responde
   `400 VALIDACION` antes del servicio.
3. Observaciones de más de 500 caracteres: `400 VALIDACION`.
4. Rechazo sin observaciones de al menos 10 caracteres (sin contar los espacios de los extremos):
   `400 RF-109`. Al activar, las observaciones son opcionales.
5. El registro no existe en la liga, o es de un club fuera del ámbito del usuario:
   `404 NO_ENCONTRADO`, sin distinguir los dos casos (VAL-024).
6. Se bloquea el registro (`SELECT … FOR UPDATE`, el mismo bloqueo de la validación de documentos).
7. Quien valida es quien dio de alta el registro (`creado_por`): `403 VAL-022` **[PROPUESTA]**
   (separación de funciones, como DEC-79 y DEC-91). Solo ocurre con alguien que tenga los dos roles;
   un `creado_por` nulo no lo activa.
8. Temporada `CERRADA`: `409 RF-003` (al activar y al rechazar). Registro que no está
   `PENDIENTE_VALIDACION`: `409 RF-017`, también si ya se validó o rechazó (acción repetida, §2).
9. Solo al activar, en este orden:
   1. Equipo en `BAJA`: `422 RF-013`, el mismo código del alta. La baja de un equipo no da de baja a
      sus registros, y el registro sí se puede rechazar.
   2. VAL-001: cada requisito aplicable (los mismos de `GET /registros-jugador/{id}/requisitos`)
      necesita, entre sus documentos vigentes, al menos `cantidad` en `APROBADO`; un documento
      cargado pero `PENDIENTE` cuenta como faltante. Si no se cumple, `422 VAL-001`: el `detail`
      nombra los documentos que faltan, sin códigos, y la extensión `faltantes` lleva, en el orden de
      los requisitos, `cargados` (vigentes no rechazados) y `aprobados` (vigentes `APROBADO`). El INE
      del tutor es un documento del registro: su falta es `VAL-001`, no `VAL-025`. Si la categoría no
      tiene requisitos configurados, VAL-001 se cumple.

      ```json
      {
        "status": 422,
        "codigo": "VAL-001",
        "detail": "El registro no se puede activar: faltan documentos aprobados de «Identificación», «Fotografía». Aprueba los documentos pendientes o pide al club que cargue los que faltan.",
        "faltantes": [
          { "tipoDocumento": { "id": 2, "codigo": "IDENTIFICACION", "nombre": "Identificación" },
            "cantidad": 1, "cargados": 0, "aprobados": 0 },
          { "tipoDocumento": { "id": 5, "codigo": "FOTOGRAFIA", "nombre": "Fotografía" },
            "cantidad": 2, "cargados": 1, "aprobados": 0 }
        ]
      }
      ```

   3. La persona era menor en `alta_en` (`es_menor(persona, alta_en)`; quien cumplió 18 después del
      alta sigue contando como menor): sin tutela vigente, `422 VAL-025` con
      `"requisito": "TUTOR"`; con tutor pero sin consentimiento no revocado de un tutor vigente,
      `422 VAL-025` con `"requisito": "CONSENTIMIENTO"`. Es la misma condición del alta.
10. Otra petición validó el registro entre tanto (el `UPDATE … AND estado = 'PENDIENTE_VALIDACION'`
    no devolvió fila): `409 RF-017`.

Rechazar no exige VAL-001 ni VAL-025: se puede rechazar con documentos pendientes. La afiliación del
club no se vuelve a revisar (`APROBADA` es final y el alta ya la exigió). `RECHAZADO` es terminal y no
cuenta para la unicidad: el club puede dar de alta otro registro de la persona con
`POST /registros-jugador` y cargar documentos nuevos; los del rechazado quedan como historial. Un
documento con problemas se rechaza **como documento** y se reemplaza; el registro se rechaza por la
identidad (persona equivocada, CURP que no corresponde).

Responde `200` con `Cache-Control: no-store` y el registro, con los mismos campos del alta y la
validación llena:

```json
{
  "id": "…",
  "estado": "ACTIVO",
  "…": "…",
  "validadoPor": { "id": "…", "nombre": "Secretario" },
  "validadoEn": "2026-11-12T10:30:00-06:00",
  "observaciones": null
}
```

`validadoPor` es `{ "id", "nombre" }` con el nombre mostrado del perfil de la sesión; `observaciones`
es `null` si no hubo. El servicio fija el motivo antes del `UPDATE` (RN-021): las observaciones o, si
se activa sin ellas, «Registro validado por la liga»; `tg_registros_historial` lo guarda en el
historial. Se audita en `M-04` como `VALIDAR_REGISTRO_JUGADOR` (entidad `registros_jugador`) con
`{ "personaId", "equipoId", "temporadaId", "estadoAnterior": "PENDIENTE_VALIDACION", "estado" }` y las
observaciones como motivo del evento (`null` si se activa sin ellas; en ese caso `AuditoriaService`
guarda el motivo fijado en la transacción, «Registro validado por la liga»), sin datos personales.

`tg_registros_validar` repite las reglas como última garantía (DEC-101): solo un usuario de liga
cambia un registro de `PENDIENTE_VALIDACION` a `ACTIVO` o `RECHAZADO`, o su `validado_por` o
`validado_en` (`VAL-015`), y al activar exige los documentos aprobados (`VAL-001`) antes de
`VAL-025`.

#### Baja de un registro (H-23.1, RF-014, RN-021, DEC-118 a DEC-124)

`POST /registros-jugador/{id}/baja` recibe `{ "motivo" }` y pasa un registro `ACTIVO` a `BAJA`
conservando su historial (RF-014, RN-005). Exige `registros.registro:EDITAR` sobre el **club del
equipo** del registro (`ObjetivoAutorizacion.deClubes`); hoy solo la Directiva de club lo tiene,
con ámbito `CLUB`. Nunca se comparan roles y no hay separación de funciones: quien dio de alta el
registro puede darlo de baja. Que el Secretario también pueda (RF-014) queda **[PROPUESTA]**:
darle `EDITAR` le daría también el alta, así que, si hace falta, será un permiso propio con su
migración en otra historia (DEC-119). El motivo lo valida el servicio después del permiso, así que
el cuerpo no lleva `@Valid`. Orden de errores:

1. Sin el permiso en ningún ámbito: `403 VAL-015`.
2. Sin motivo, o con menos de 10 caracteres sin contar los espacios de los extremos: `400 RN-021`
   («Escribe un motivo de al menos 10 caracteres para dar de baja el registro.»). Con más de 500:
   `400 VALIDACION`.
3. El registro no existe en la liga, o es de un club fuera del ámbito del usuario:
   `404 NO_ENCONTRADO`, sin distinguir los dos casos (VAL-024).
4. Se bloquea el registro (`SELECT … FOR UPDATE`, el mismo bloqueo de la validación).
5. Temporada `CERRADA`: `409 RF-003` («La temporada del registro está cerrada: sus registros ya no
   cambian.»). Se aceptan `CONFIGURACION` y `ACTIVA`.
6. Registro que no está `ACTIVO`: `409 RF-017`, con un `detail` según su estado (DEC-118); una
   segunda baja es una acción repetida (§2):

   | Estado | `detail` |
   | --- | --- |
   | `PENDIENTE_VALIDACION` | El registro aún no está validado: si no procede, pide a la liga que lo rechace. |
   | `SUSPENDIDO` | El registro tiene una sanción vigente: no se puede dar de baja mientras esté suspendido. |
   | `BAJA` | El registro ya está dado de baja. |
   | `RECHAZADO` | El registro fue rechazado: no está activo. |

7. Otra petición cambió el estado entre tanto (el `UPDATE … AND estado = 'ACTIVO'` no devolvió
   fila): `409 RF-017` («Otra persona cambió el estado de este registro al mismo tiempo. Actualiza la
   página para ver su estado.»).

Un equipo en `BAJA` no impide la baja del registro (DEC-120). `SUSPENDIDO → BAJA`, que el trigger sí
acepta, queda para la sanción de tipo baja de M-09 (DEC-118).

`baja_en` es la fecha de hoy en `America/Mexico_City`, calculada con el `Clock` del servicio
(`current_date` de la base es UTC); `ck_registros_baja` la exige con el estado `BAJA`. El motivo,
recortado, se fija antes del `UPDATE` (RN-021) y **solo va al historial**: `tg_registros_historial`
lo guarda con el usuario de la sesión. No hay columna de motivo de baja, y `observaciones`,
`validado_por` y `validado_en` no cambian (DEC-121).

Responde `200` con `Cache-Control: no-store` y el registro, con los mismos campos del alta:

```json
{
  "id": "…",
  "estado": "BAJA",
  "…": "…",
  "validadoPor": { "id": "…", "nombre": "Secretario" },
  "validadoEn": "2026-11-12T10:30:00-06:00",
  "observaciones": "Documentos revisados en ventanilla",
  "bajaEn": "2026-11-14",
  "motivoBaja": "El jugador se mudó de ciudad"
}
```

`bajaEn` y `motivoBaja` son `null` salvo en `BAJA`; `motivoBaja` es el motivo del último cambio a
`BAJA` del historial (DEC-124). Se audita en `M-04` como `DAR_BAJA_REGISTRO_JUGADOR` (entidad
`registros_jugador`) con `{ "personaId", "equipoId", "temporadaId", "estadoAnterior": "ACTIVO",
"estado": "BAJA", "bajaEn" }` y el motivo como motivo del evento, sin datos personales (DEC-123).

#### Historial de un registro (B-07.1, RF-018)

`GET /registros-jugador/{id}/historial` devuelve los cambios de estado del registro que escribe
`tg_registros_historial`: el alta y cada cambio de estado (validación, rechazo, baja…), con su
motivo, quién lo hizo y cuándo. Es de **solo lectura** (RNF-015): la ruta no admite `POST`, `PATCH`
ni `DELETE` (`405`) y la consulta no se audita.

Exige `registros.registro:CONSULTAR` sobre el **club del registro**
(`ObjetivoAutorizacion.deClubes`): una cuenta con ámbito `LIGA` ve el de cualquier registro de la
liga y una de ámbito `CLUB`, solo los de su club. La consulta del propio jugador o de su tutor
(ámbito `PROPIO`) no se incluye: es de H-24. Orden de errores:

1. Sin el permiso en ningún ámbito: `403 VAL-015`.
2. El registro no existe en la liga, o es de un club fuera del ámbito del usuario:
   `404 NO_ENCONTRADO`, sin distinguir los dos casos (VAL-024).

Responde `200` con `Cache-Control: no-store` y un **arreglo sin paginar** (un registro tiene pocos
cambios), del más antiguo al más reciente (`cambiado_en` y, a igual fecha, el orden de inserción):

```json
[
  {
    "estadoAnterior": null,
    "estadoNuevo": "PENDIENTE_VALIDACION",
    "motivo": "Alta del registro",
    "cambiadoPor": { "id": "…", "nombre": "Directiva Halcones" },
    "cambiadoEn": "2026-11-11T12:00:00-06:00"
  },
  {
    "estadoAnterior": "PENDIENTE_VALIDACION",
    "estadoNuevo": "ACTIVO",
    "motivo": "Documentos revisados en ventanilla",
    "cambiadoPor": { "id": "…", "nombre": "Secretario" },
    "cambiadoEn": "2026-11-12T10:30:00-06:00"
  }
]
```

`estadoAnterior` es `null` en el alta. `cambiadoEn` va en `America/Mexico_City`. `cambiadoPor` es
`{ "id", "nombre" }` con el nombre mostrado del perfil; como en `validadoPor`, el nombre es `null`
si la sesión no puede ver ese perfil (RLS `perfiles_leer`: hoy una cuenta de club no ve los perfiles
de la liga), y todo el campo es `null` si el historial no guardó usuario.

#### Consulta propia (H-24.1, RF-144, DEC-139 a DEC-145)

Información del jugador o del menor para su tutor (M-04.6, pantalla F-35). Son dos rutas: la lista
de personas que la cuenta puede consultar y el detalle de una de ellas. Las implementa
`MiInformacionService`; ninguna se audita, porque no cambian nada.

**Autorización y orden de errores (DEC-140).** Las dos exigen `registros.registro:CONSULTAR` en
**cualquier** ámbito (`autorizacion.alcance`); el detalle exige además que `siglim.es_titular(personaId)`
sea verdadero: la persona es la de la cuenta (`perfiles.persona_id`) o un menor con tutela vigente
cuyo tutor es esa persona. Esto vale también para cuentas con ámbito `LIGA` o `CLUB`: es «mi»
información, no una consulta administrativa (esas usan `/registros-jugador`).

1. Sin el permiso en ningún ámbito: `403 VAL-015`.
2. `personaId` mal formado: `400 VALIDACION` (antes del servicio, como el resto de la API).
3. Persona que no es la de la cuenta ni un menor bajo su tutela vigente, o que no existe:
   `404 NO_ENCONTRADO`, sin distinguir los casos (VAL-024).

**`GET /mi-informacion` (DEC-139).** Primero la persona de la cuenta (`persona_usuario()`), si
existe, y después los menores con tutela vigente (`tutelas.tutor_id` = esa persona), por nombre
completo. Una cuenta sin persona vinculada responde `200` con la lista vacía. `esMenor` se calcula
con la fecha de hoy en `America/Mexico_City`.

```json
{
  "titulares": [
    { "personaId": "…", "nombre": "María López Ruiz", "esPropia": true, "esMenor": false },
    { "personaId": "…", "nombre": "Ana López Ruiz", "esPropia": false, "esMenor": true }
  ]
}
```

**`GET /mi-informacion/{personaId}` (DEC-141).** Responde `200` con `Cache-Control: no-store`:

- `persona`: `id`, `nombreCompleto`, `curpEnmascarada` (§9.2; nunca la CURP completa) y `esMenor` (hoy).
- `registros`: los de temporadas que no estén `CERRADA`, del más reciente al más antiguo, cada uno con
  `temporada`, `club`, `equipo`, `categoria`, `estado`, `altaEn`, `bajaEn`, `motivoBaja` (DEC-124),
  `observaciones` (del rechazo o la validación) y:
  - `elegibilidad`: `{ elegible, causas: [ { codigo, mensaje } ] }` con `ReglaElegibilidad` (DEC-142):
    `RF-019` si el registro no está `ACTIVO` (mensaje según el estado) y `RF-020` por cada sanción
    `VIGENTE` de tipo `SUSPENSION_PARTIDOS` o `BAJA`, con el artículo y la vigencia.
    **[PROPUESTA]** `MULTA`, `PERDIDA_PUNTOS` y `PERDIDA_PARTIDO` no impiden jugar.
  - `documentos`: los mismos requisitos y avance que `GET /registros-jugador/{id}/requisitos`
    (`RequisitosDocumentos`, con la edad en la fecha de alta): `requisitos[]` con `tipoDocumento`,
    `cantidad`, `soloMenores`, `esDemo`, `cargados`, `aprobados`, `faltan` y `documentos[]` (los vigentes:
    `id`, `tipo`, `estadoValidacion`, `observaciones`, `cargadoEn`), más `completo` y `listoParaValidar`.
    **Nunca** la URL, el bucket, la clave, el SHA-256, el MIME ni el nombre del archivo (RNF-018).
  - `proximosPartidos` y `partidosJugados` **[PROPUESTA]** (DEC-144): partidos del equipo del registro,
    como local o visitante (mientras no haya alineaciones). Próximos: jornada `PUBLICADO` y partido que
    no esté `FINALIZADO` ni `CANCELADO`, por `inicio` ascendente, hasta 5. Jugados: con resultado
    oficial vigente, por `inicio` descendente, hasta 10. Cada partido lleva `id`, `jornada` (número),
    `inicio`, `cancha` (nombre), `local` y `visitante` (`id`, `nombre`), `esLocal`, `estado` y
    `resultado` (`golesLocal`, `golesVisitante`, `penalesLocal`, `penalesVisitante`) o `null`.
- `sanciones` (de la persona, no por registro): las de sujeto `JUGADOR` en estado `VIGENTE` más las de
  temporadas no cerradas, de la más reciente a la más antigua, con `id`, `tipo`, `estado`, `partidos`,
  `partidosCumplidos`, `vigenteDesde`, `vigenteHasta`, `articulo` (`numero`, `descripcion`) y `folio` del
  expediente.

```json
{
  "persona": { "id": "…", "nombreCompleto": "Ana López Ruiz", "curpEnmascarada": "LORA******MMN***05", "esMenor": true },
  "registros": [
    {
      "id": "…",
      "temporada": { "id": "…", "nombre": "Temporada 2026", "estado": "ACTIVA" },
      "club": { "id": "…", "nombre": "Club Halcones" },
      "equipo": { "id": "…", "nombre": "Halcones" },
      "categoria": { "id": "…", "nombre": "Infantil" },
      "estado": "ACTIVO",
      "altaEn": "2026-11-03",
      "bajaEn": null,
      "motivoBaja": null,
      "observaciones": "Documentos revisados en ventanilla",
      "elegibilidad": {
        "elegible": false,
        "causas": [
          { "codigo": "RF-020", "mensaje": "Suspensión de 3 partidos por el artículo 12 («Conducta antideportiva»): faltan 2 partidos." }
        ]
      },
      "documentos": {
        "requisitos": [
          {
            "tipoDocumento": { "id": 5, "codigo": "FOTOGRAFIA", "nombre": "Fotografía" },
            "cantidad": 2, "soloMenores": false, "esDemo": true, "cargados": 2, "aprobados": 2, "faltan": 0,
            "documentos": [
              { "id": "…", "tipo": { "id": 5, "codigo": "FOTOGRAFIA", "nombre": "Fotografía" },
                "estadoValidacion": "APROBADO", "observaciones": null, "cargadoEn": "2026-11-03T10:15:00-06:00" }
            ]
          }
        ],
        "completo": true,
        "listoParaValidar": true
      },
      "proximosPartidos": [
        { "id": "…", "jornada": 4, "inicio": "2026-11-22T10:00:00-06:00", "cancha": "Unidad Deportiva 1",
          "local": { "id": "…", "nombre": "Halcones" }, "visitante": { "id": "…", "nombre": "Lobos" },
          "esLocal": true, "estado": "PROGRAMADO", "resultado": null }
      ],
      "partidosJugados": [
        { "id": "…", "jornada": 3, "inicio": "2026-11-15T10:00:00-06:00", "cancha": "Unidad Deportiva 2",
          "local": { "id": "…", "nombre": "Pumas" }, "visitante": { "id": "…", "nombre": "Halcones" },
          "esLocal": false, "estado": "FINALIZADO",
          "resultado": { "golesLocal": 1, "golesVisitante": 2, "penalesLocal": null, "penalesVisitante": null } }
      ]
    }
  ],
  "sanciones": [
    { "id": "…", "tipo": "SUSPENSION_PARTIDOS", "estado": "VIGENTE", "partidos": 3, "partidosCumplidos": 1,
      "vigenteDesde": "2026-11-16", "vigenteHasta": null,
      "articulo": { "numero": "12", "descripcion": "Conducta antideportiva" }, "folio": "EXP-2026-0007" }
  ]
}
```

**Dependencias (DEC-143, ADR-0006).** `registros` (nivel 4) no importa `partidos` ni `disciplina`:
define `ConsultaPartidosPropios` y `ConsultaSancionesPropias` con sus records (`PartidoPropio`,
`SancionPropia`), y las implementan `partidos.PartidosPropiosJdbc` y `disciplina.SancionesPropiasJdbc`
con SQL de solo lectura dentro de la transacción del servicio (contexto de usuario y RLS, ADR-0014).
Vincular la cuenta con su persona (`perfiles.persona_id`) y los consentimientos quedan fuera (DEC-145).

#### Renovación y transferencia entre temporadas (B-06.1, RF-015, RF-016, RF-111, VAL-026, DEC-146)

`POST /registros-jugador/renovaciones` y `POST /registros-jugador/transferencias` reciben
`{ "personaId", "equipoId" }`; la temporada es la del grupo del equipo de destino y el registro
anterior lo deduce el servicio (`siglim.registro_anterior`): el registro no rechazado más reciente de
la persona en una temporada anterior de la liga. Ambos exigen `registros.registro:EDITAR` sobre el
**club del equipo de destino** y responden `201` con `Location: /api/v1/registros-jugador/{id}`,
`Cache-Control: no-store` y el mismo registro del alta; queda `PENDIENTE_VALIDACION` con
`registro_anterior_id`, que no va en la respuesta pero queda en la base para la trazabilidad.

- **Renovación.** El registro anterior debe ser del mismo club; se conserva al jugador y los documentos aprobados y
  no reemplazados de sus registros anteriores no rechazados del mismo club cuentan para VAL-001, sin copiarlos en
  S3 (DEC-146).
- **Transferencia.** El registro anterior debe ser de otro club y la versión vigente de parámetros
  de la temporada destino debe permitir el cambio (`permite_cambio_club`, RF-111); si no, `409
  RF-111`. Sin versión de parámetros rige el valor por omisión de la tabla (`true`). No hereda los
  documentos del club de origen: el club destino carga los del registro nuevo. Nunca hay cambio de
  club dentro de una temporada (VAL-026, DEC-13): el registro anterior debe ser de una temporada
  anterior.

Orden de errores (el del alta más los de DEC-146):

1. Sin el permiso en ningún ámbito: `403 VAL-015`.
2. Falta `personaId` o `equipoId`: `400 VALIDACION`.
3. El equipo no existe en la liga o es de un club fuera de ámbito: `404` (VAL-024).
4. Temporada `CERRADA`: `409 RF-003`; equipo `BAJA`: `422 RF-013`; club sin afiliación `APROBADA`:
   `409 RF-101`.
5. Persona inexistente: `422 REFERENCIA_INVALIDA`.
6. Registro activo en la temporada destino: `409 VAL-017` (con `club` y `equipo`); una baja en otro
   club de la misma temporada: `409 VAL-026`; en otro equipo del mismo club: `409 RN-039`.
7. No hay registro anterior en una temporada previa: `422 RF-015` en la renovación y `422 RF-016` en
   la transferencia.
8. El registro anterior no corresponde al movimiento: otro club en una renovación, `422 RF-015`; el
   mismo club en una transferencia, `422 RF-016`.
9. Transferencia a una temporada cuya versión vigente de parámetros no permite el cambio de club:
   `409 RF-111`, con un mensaje que explica la configuración de la Liga.
10. Menor sin tutor vigente o sin consentimiento: `422 VAL-025` con `requisito` `TUTOR` o
    `CONSENTIMIENTO`.

El motivo del historial es «Renovación desde la temporada «…»» o «Transferencia desde el club «…» de
la temporada «…»». Se audita en `M-04` sobre `registros_jugador` como `RENOVAR_REGISTRO` o
`TRANSFERIR_REGISTRO`, con `{ "personaId", "equipoId", "temporadaId", "registroAnteriorId",
"clubAnteriorId", "equipoAnteriorId", "temporadaAnteriorId", "estado" }`, sin datos personales.

**Opción para el asistente de alta (B-06.2, DEC-153).** `GET /registros-jugador/movimientos/opcion?personaId=&equipoId=`
responde `200` con `Cache-Control: no-store` y
`{ "tipo": "RENOVACION" | "TRANSFERENCIA" | null, "permitido", "temporadaAnterior", "motivo" }`, con
las mismas reglas que los `POST` (que vuelven a validar al enviar). Es de solo lectura y no se audita.

1. Sin `registros.registro:EDITAR` en ningún ámbito: `403 VAL-015`.
2. Falta `personaId` o `equipoId`: `400 VALIDACION`.
3. El equipo no existe en la liga o es de un club fuera de ámbito: `404` (VAL-024).
4. La persona ya tiene un registro no `RECHAZADO` en la temporada del equipo, o no tiene registro
   anterior: `tipo` nulo (`permitido` falso y lo demás nulo); el asistente sigue con el alta normal.
5. Registro anterior del mismo club: `RENOVACION`, `permitido` verdadero.
6. De otro club: `TRANSFERENCIA`, con `permitido` igual a `permite_cambio_club` vigente de la
   temporada destino; si es falso, `motivo` explica la configuración de la Liga (el mismo texto del
   `409 RF-111`).

`temporadaAnterior` es solo el nombre de la temporada del registro anterior: nunca se responden el
club, el equipo, el estado ni los ids de ese registro (DEC-88, DEC-90).

### 6.5 M-05 Árbitros y designaciones

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /arbitros?estado=&clubId=` · `POST /arbitros` | Árbitros presentados por el club | `GET`: `arbitraje.arbitro:CONSULTAR` · `POST`: `arbitraje.arbitro:PROPONER` | RF-112, RF-021 | N |
| `POST /arbitros/{id}/validacion` | Validar o rechazar | `arbitraje.arbitro:VALIDAR` | RF-113 | N |
| `GET /clubes/{id}/cuota-arbitros` | Árbitros presentados frente a los requeridos | `arbitraje.arbitro:CONSULTAR` | RF-114 | B |
| `GET /arbitros/{id}/disponibilidad?desde=&hasta=` · `POST` · `DELETE …/{disponibilidadId}` | Disponibilidad por fecha y horario; el `POST` responde `201` sin `Location` y el `DELETE`, `204` | `arbitraje.disponibilidad:EDITAR` | RF-022 | B |
| `GET /designaciones?jornadaId=` | Partidos de la jornada con sus designaciones vigentes y el avance; el filtro `arbitroId` llega con B-10 | `arbitraje.designacion:CONSULTAR` | RF-023 | N |
| `GET /partidos/{id}/arbitros-candidatos` | Árbitros validados con su elegibilidad, advertencias y carga (DEC-204) | `arbitraje.designacion:EDITAR` | RF-023, RF-039, RF-115 | N |
| `POST /partidos/{id}/designaciones` | Designar central o asistente; sustituir al central con motivo; `422 VAL-019` si el árbitro es de un club del partido | `arbitraje.designacion:EDITAR` | RF-023, RF-115, RF-039 | N |
| `POST /designaciones/{id}/retiro` | Retirar una designación con motivo (DEC-203) | `arbitraje.designacion:EDITAR` | RF-023 | N |
| `GET /mis-designaciones?desde=&hasta=` | Partidos asignados al árbitro de la sesión en jornadas publicadas (DEC-205) | `arbitraje.designacion:CONSULTAR`, siempre del titular | RF-023, RF-123 | N |

**Árbitros del club (H-11.1, RF-021, RF-112, RF-113, DEC-17).** `GET /arbitros?estado=&clubId=&pagina=&tamano=`
responde la envoltura de paginación; cada elemento trae `persona` con `{ id, nombreCompleto }` (sin
CURP ni datos de contacto, convenciones §9.2), `club`, `estado`, `propuestoPor`, `propuestoEn` y, si
ya se resolvió, `validadoPor`, `validadoEn` y `observaciones`. Exige
`arbitraje.arbitro:CONSULTAR` y filtra por el ámbito en el `WHERE` y en el `COUNT`: la Directiva de
club solo ve los árbitros de su club y la Comisión (ámbito `LIGA`) los ve todos. Un `clubId` ajeno
en el filtro devuelve una página vacía, no un `403`.

`POST /arbitros` lleva `{ "personaId", "clubId" }`, requiere `arbitraje.arbitro:PROPONER` con ámbito
del club y responde `201` con `Location` y el árbitro en estado `PROPUESTO`; el perfil que lo
presenta sale de la sesión. Un club de otra liga o inexistente responde `404` (VAL-024). Como la
identidad es única (RF-010, DEC-10), si la persona ya está en el catálogo responde `409 DUPLICADO`
nombrando solo el club que ya la presenta (extensión `club`), sin su estado ni identificadores
(DEC-88). Una persona inexistente responde `422 REFERENCIA_INVALIDA`. El alta usa
`INSERT … ON CONFLICT (persona_id) DO NOTHING RETURNING id`: si dos directivas presentan a la misma
persona a la vez, la segunda no aborta la transacción y recibe el mismo `409` (DEC-90).

`POST /arbitros/{id}/validacion` lleva `{ "decision": "VALIDADO"|"RECHAZADO", "observaciones" }` y
exige `arbitraje.arbitro:VALIDAR`. Solo se aplica a un árbitro `PROPUESTO`; cualquier otro estado
responde `409 RF-113`. El rechazo exige observaciones de 10 a 500 caracteres (`400 RF-113` si
faltan); la validación las admite como nota. Se guardan el perfil y la fecha del dictamen y se
auditan como `PROPONER_ARBITRO`, `VALIDAR_ARBITRO` y `RECHAZAR_ARBITRO` en `M-05` sobre `arbitros`,
sin nombres ni CURP. Solo los `VALIDADO` pueden designarse: el trigger de `designaciones` responde
`RF-113` para cualquier otro estado.

**Disponibilidad arbitral (B-09.1, RF-022).** `GET /api/v1/arbitros/{id}/disponibilidad` responde
`200` con la lista de periodos del árbitro, ordenada por inicio. `desde` y `hasta` son opcionales: si
se envían, **ambos** son obligatorios y se devuelven los periodos que se traslapan con ese rango
(`400 VALIDACION` si falta uno o el orden es incorrecto); sin ellos se devuelven todos. Cada
elemento lleva `{ "id", "arbitroId", "periodo": { "desde", "hasta" }, "disponible", "creadoEn" }`,
con las fechas en `America/Mexico_City` y el intervalo `[desde, hasta)`, igual que el `tstzrange` de
la tabla.

`POST /api/v1/arbitros/{id}/disponibilidad` lleva
`{ "periodo": { "desde", "hasta" }, "disponible": true|false }`. `disponible` es opcional y vale
`true` por omisión; con `false` el árbitro declara que no puede ser designado en ese periodo.
Responde `201` con el registro y **sin** `Location` (no hay `GET` por identificador). El inicio debe
ser anterior al fin (`400 VALIDACION`; la restricción `ck_disponibilidad_arbitral` no admite un
periodo vacío). Solo se registra para un árbitro en estado `VALIDADO`: cualquier otro responde
`409 RF-022` sin guardar. Los periodos ya creados se pueden consultar y retirar aunque el estado del
árbitro cambie, para que la Comisión limpie el calendario sin tocar el catálogo.

`DELETE /api/v1/arbitros/{id}/disponibilidad/{disponibilidadId}` responde `204` sin cuerpo y borra
la fila: la disponibilidad no es historial deportivo y el diccionario no la marca como histórica. Si
la regla del equipo cambiara, se sustituiría por una desactivación. Un periodo que no pertenece al
árbitro, o un árbitro de otra liga, responde `404` (VAL-024).

Los tres endpoints exigen `arbitraje.disponibilidad:EDITAR` sobre la **persona del árbitro**: la
Comisión de Arbitraje (ámbito `LIGA`) opera con cualquiera y el árbitro (ámbito `PROPIO`) solo con la
suya; un ámbito de club no la alcanza y responde `404` (VAL-024, ADR-0007). El alta y el borrado se
auditan en `M-05` sobre `disponibilidad_arbitral` como `REGISTRAR_DISPONIBILIDAD_ARBITRAL` y
`ELIMINAR_DISPONIBILIDAD_ARBITRAL`, con `{ "arbitroId", "desde", "hasta", "disponible" }` y sin
nombres, CURP ni datos de contacto. En la designación solo **advierte** (`RF-022` en los candidatos):
no filtra ni bloquea (DEC-204).

**Designaciones (H-14.1, RF-023, RF-115, RF-039, DEC-201 a DEC-205).** Las designa la Comisión de
Arbitraje con ámbito `LIGA` (`ObjetivoAutorizacion.deLiga()`); las fechas van en
`America/Mexico_City` y el nombre del árbitro va `null` si la política de `personas` no deja verlo
(DEC-78). Nunca se exponen CURP ni datos de contacto.

`GET /api/v1/designaciones?jornadaId=` exige `arbitraje.designacion:CONSULTAR`. `jornadaId` es
obligatorio (`400 VALIDACION`); una jornada inexistente o de otra liga responde `404`. Responde
`200` con `Cache-Control: no-store`:

```json
{
  "jornada": { "id": "…", "numero": 3, "estadoPublicacion": "BORRADOR",
               "grupo": { "id": "…", "nombre": "A" }, "categoria": { "id": "…", "nombre": "Libre" } },
  "avance": { "partidos": 6, "conCentral": 4, "asistentesCompletos": 1 },
  "partidos": [
    { "id": "…", "inicio": "2030-08-10T10:00:00-06:00", "finBloque": "2030-08-10T12:00:00-06:00",
      "estado": "PROGRAMADO", "cancha": { "id": "…", "nombre": "…" },
      "equipoLocal": { "id": "…", "nombre": "…" }, "equipoVisitante": { "id": "…", "nombre": "…" },
      "designable": true,
      "central": { "id": "…", "arbitro": { "id": "…", "nombre": "…", "club": { "id": "…", "nombre": "…" } },
                   "funcion": "CENTRAL", "automatica": false, "designadoEn": "…" },
      "asistentes": [] }
  ]
}
```

Los partidos van por `inicio` e `id` y **excluyen** los `CANCELADO` (DEC-181); solo se muestran las
designaciones vigentes. `central` es `null` si no hay. `designable` es verdadero si el partido está
`PROGRAMADO` o `REPROGRAMADO`. `asistentesCompletos` cuenta los partidos con sus dos asistentes. El
filtro `arbitroId` y el historial de designaciones (RF-024) llegan con B-10.

`GET /api/v1/partidos/{id}/arbitros-candidatos` exige `arbitraje.designacion:EDITAR` después del
`404` del partido (otra liga o inexistente). Responde `200` con los árbitros `VALIDADO` de la liga
por nombre: `[{ "arbitro": { "id", "nombre", "club" }, "elegible", "causas": [{ "codigo", "mensaje" }],
"advertencias": [{ "codigo", "mensaje" }], "carga" }]`. Las **causas** usan las mismas condiciones
que el trigger `tg_designaciones_validar`, así que `elegible` (sin causas) coincide con lo que acepta
el `POST`:

| `codigo` | Mensaje | Cuándo |
| --- | --- | --- |
| `VAL-019` | «Pertenece a &lt;club&gt;, que juega este partido.» | Su club es el de uno de los equipos (RF-115) |
| `RF-039` | «Ya está designado en &lt;local&gt; vs &lt;visitante&gt; a las HH:mm (&lt;cancha&gt;).» | Tiene una designación vigente en otro partido no `CANCELADO` cuyo `tstzrange(inicio, fin_bloque)` se traslapa; se nombra el primero por inicio |
| `YA_DESIGNADO` | «Ya está designado en este partido como central» (o «asistente») | Ya tiene una designación vigente en el partido |

La única **advertencia** es `RF-022`, «Declaró que no está disponible en ese horario.», si un periodo
`disponible = false` de su disponibilidad se traslapa con el bloque del partido: **no bloquea**; la
disponibilidad solo informa (DEC-204). `carga` cuenta las designaciones vigentes del árbitro en
partidos no `CANCELADO` de la temporada del partido. La consulta es una sola sentencia SQL en el
componente `arbitraje.ElegibilidadArbitral`, que reutiliza la asignación automática del central,
`arbitraje.DesignacionAutomatica` (H-34.1, DEC-182, DEC-213).

`POST /api/v1/partidos/{id}/designaciones` lleva `{ "arbitroId", "funcion": "CENTRAL"|"ASISTENTE",
"motivo"? }` y exige `arbitraje.designacion:EDITAR`. Responde `201` **sin** `Location` (no hay
`GET /designaciones/{id}`) con la designación de arriba más `partidoId` y `vigente: true`. Orden de
los errores (DEC-201):

1. `400 VALIDACION` si faltan `arbitroId` o `funcion`, o la función no existe.
2. Partido de la liga, bloqueado (`404`).
3. Permiso (`403 VAL-015`), después del `404` como en la disponibilidad.
4. Árbitro de la liga, bloqueado después del partido (`422 REFERENCIA_INVALIDA` si no existe).
5. `409 RF-023` si el partido no está `PROGRAMADO` ni `REPROGRAMADO`: «Solo se designan árbitros a
   partidos programados o reprogramados; este está &lt;estado&gt;.» **[PROPUESTA]**
6. `409 RF-023` si el árbitro ya tiene una designación vigente en el partido, con cualquier función.
7. `409 RF-023` si es `ASISTENTE` y el partido ya tiene **dos** asistentes vigentes **[PROPUESTA]**.
8. **Sustitución del central (DEC-202).** Si es `CENTRAL` y ya hay central vigente, `motivo` es
   obligatorio, de 10 a 500 caracteres tras recortarlo (`400 RN-021`); la anterior queda
   `vigente = false` con `motivo_cambio` y después se inserta la nueva. Sin central vigente, el
   motivo se ignora.
9. Alta con `designado_por` = perfil de la sesión y `automatica = false`. El trigger aplica RF-113
   (`409 RF-113`, solo validados), VAL-019 (`422 VAL-019`) y RF-039 (`409 RF-039`); el servicio no
   las repite.

`POST /api/v1/designaciones/{id}/retiro` lleva `{ "motivo" }` (10 a 500 caracteres, `400 RN-021`) y
exige `arbitraje.designacion:EDITAR` (DEC-203). Una designación inexistente o de otra liga responde
`404`; bloquea el partido y responde `409 RF-023` si la designación ya no está vigente o el partido
no está `PROGRAMADO` ni `REPROGRAMADO`. Deja `vigente = false` y `motivo_cambio`: nada se borra
(RN-005). Responde `200` con la designación, `vigente: false` y `motivoCambio`.

La designación, la sustitución y el retiro se auditan en `M-05` sobre `designaciones` como
`DESIGNAR_ARBITRO`, `SUSTITUIR_ARBITRO` y `RETIRAR_DESIGNACION` con
`{ "partidoId", "arbitroId", "funcion", "designacionAnteriorId"?, "motivo"? }`, sin nombres ni CURP.

`GET /api/v1/mis-designaciones?desde=&hasta=&pagina=&tamano=` (DEC-205) exige que alguna asignación
otorgue `arbitraje.designacion:CONSULTAR` (`403 VAL-015` si ninguna) y filtra **siempre** por
`es_titular` de la persona del árbitro, sea cual sea el ámbito: es «mis». Solo designaciones vigentes
en jornadas `PUBLICADO`. Incluye los partidos `CANCELADO` cuya designación siga vigente, con su
estado, para que el árbitro sepa que no se juega (al regenerar una fecha, DEC-181 deja sus
designaciones sin vigencia y ya no aparecen). `desde` y `hasta` son opcionales sobre `inicio`
(`[desde, hasta)`, `400 VALIDACION` si el orden es inválido). Responde la envoltura de paginación
(§4), por `inicio`, con la misma condición en el `COUNT`; cada elemento es
`{ "designacionId", "funcion", "partido": { "id", "inicio", "finBloque", "estado", "cancha",
"equipoLocal", "equipoVisitante", "categoria", "grupo", "jornada": { "id", "numero" } },
"cedula": { "id", "estado" } | null }`.

**Cuota de árbitros del club (B-08.1, RF-114, RN-042, DEC-17, DEC-72).**
`GET /clubes/{id}/cuota-arbitros?temporadaId=` exige `arbitraje.arbitro:CONSULTAR` sobre el club
(ADR-0007) y responde `200` con:

```json
{
  "club": { "id": "…", "nombre": "Club Halcones" },
  "temporada": { "id": "…", "nombre": "Apertura 2030" },
  "equiposActivos": 5,
  "equiposPorArbitro": 4,
  "cuotaArbitros": { "requeridos": 2, "validados": 1, "cumple": false }
}
```

`temporadaId` es opcional; sin él se usa la temporada `ACTIVA` de la liga y, si no hay, responde
`409 RF-114`. `equiposActivos` cuenta solo los equipos `ACTIVO` del club en los grupos de esa
temporada. `equiposPorArbitro` es el valor de la versión vigente de `parametros_temporada` (el
parámetro **[DEMO]** de DEC-17; la interfaz lo rotula «no oficial»): nunca es una constante del
código. Sin ninguna versión de parámetros, responde `409 RF-100`. `requeridos` es
`ceil(equiposActivos / equiposPorArbitro)`, `validados` cuenta solo los árbitros `VALIDADO` del club
y `cumple` indica si `validados >= requeridos`. Los árbitros `PROPUESTO`, `RECHAZADO` y `BAJA` no
cubren la cuota. Un club fuera del ámbito o inexistente y una temporada inexistente o de otra liga
responden `404` idéntico (VAL-024). Es una consulta calculada: no se guarda ni se audita.

### 6.6 M-06 Canchas y recursos

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /canchas?activa=&ubicacion=&propietario=&sedeId=&q=` · `GET /canchas/{id}` · `POST` · `PATCH /canchas/{id}` | Catálogo con ubicación, propietario y sede (`sede: { id, nombre }`, omitida si no tiene). `orden`: `nombre`, `creadoEn`. Activar o desactivar por `PATCH` con `activa` y `motivo` | `canchas.cancha:CONSULTAR` en los `GET`, `canchas.cancha:EDITAR` en `POST` y `PATCH` | RF-027, RF-028 | N · B |
| `GET /sedes` · `POST /sedes` · `PATCH /sedes/{id}` | Sedes que agrupan canchas. El `GET` responde la lista completa sin paginar, por `nombre` | `canchas.cancha:CONSULTAR` en el `GET` (cualquier ámbito), `canchas.cancha:EDITAR` en `POST` y `PATCH` | RF-028 | B |
| `GET /canchas/{id}/disponibilidad?desde=&hasta=` · `POST` | Disponibilidad, bloqueos y mantenimiento con motivo. El `POST` responde `{ disponibilidad, partidosAfectados }` | `canchas.cancha:CONSULTAR` en el `GET`, `canchas.cancha:EDITAR` en el `POST` | RF-029, RF-030, RF-031 | N · B |
| `GET /canchas/{id}/ocupacion?desde=&hasta=` | Disponibilidad, bloqueos, mantenimiento y partidos no cancelados del rango (calendario semanal y apoyo a la programación) | `canchas.cancha:CONSULTAR` | RF-032, RF-039 | N |
| `GET /canchas/{id}/horarios-club?temporadaId=` · `PUT` | Horarios por categoría de la cancha de club y comodato de la cancha de la liga, por temporada (§6.6.1) | `canchas.cancha:CONSULTAR` en el `GET`, `canchas.cancha:EDITAR` en el `PUT` | RF-119 | B |
| `GET /solicitudes-cancha?tipo=&estado=` · `GET …/contexto` · `POST` · `POST …/{id}/resolucion` · `GET …/{id}/oficio` | Formatos 1 y 2 y su carta (§6.6.2) | `CREAR`, `APROBAR` o `GENERAR` en los `GET` de la bandeja y el contexto; `canchas.solicitud:CREAR` en el `POST`; `canchas.solicitud:APROBAR` en la resolución; `canchas.oficio:GENERAR` en el oficio | RF-116 a RF-118 | B |

- **Sede de una cancha (RF-028, [PROPUESTA]).** `POST /canchas` acepta `sedeId` opcional. En el
  `PATCH`, sin `sedeId` ni `quitarSede` la sede se conserva; `sedeId` asigna esa sede y
  `"quitarSede": true` deja la cancha sin sede; los dos juntos responden `400 VALIDACION`. Una sede
  inexistente o de otra liga responde `422 REFERENCIA_INVALIDA` sin guardar. En el filtro
  `GET /canchas?sedeId=` no es error: la lista sale vacía.
- Un nombre de sede repetido en la liga responde `409 DUPLICADO`. Las sedes no se borran ni se
  desactivan (no hay `DELETE`). El alta y la edición se auditan como `REGISTRAR_SEDE` y `EDITAR_SEDE`,
  sin la dirección en el detalle.
- `desde` y `hasta` son obligatorios en las consultas de disponibilidad y ocupación, con un rango de
  31 días como máximo **[PROPUESTA]**. Sin ellos, o con un rango mayor, `400 VALIDACION`.
- **Bloqueo y mantenimiento sobre partidos (PEN-007, resuelto por DEC-64).** Un `BLOQUEO` o un
  `MANTENIMIENTO` que se traslapa con partidos `PROGRAMADO` o `REPROGRAMADO` responde `409`,
  `codigo: "RF-030"`, `"aceptable": true` y `"partidosAfectados": […]`, sin guardar. Reenviado con
  `"confirmarPartidosAfectados": true`, se guarda (`201`). Los partidos **no se modifican**: se
  reprograman a mano. Desactivar una cancha con partidos en curso o futuros sigue el mismo flujo en
  el `PATCH`.
- Dos `DISPONIBLE` de la misma cancha no se traslapan: `409`, `codigo: "RF-029"` **[PROPUESTA]**. Un
  `BLOQUEO` o un `MANTENIMIENTO` puede traslaparse con cualquier registro, también entre ellos
  **[PROPUESTA]**.
- **Mantenimiento (RF-031, [PROPUESTA] B-12.1).** `MANTENIMIENTO` es un bloqueo tipificado: el
  trigger de VAL-005 rechaza cualquier partido que caiga en él, igual que en un `BLOQUEO`.
  - El `motivo` es obligatorio, de al menos 10 caracteres: sin él, `400 RN-021` sin guardar.
  - Sobre partidos sigue el flujo de dos pasos del bloqueo, con el mismo código `RF-030` (la regla de
    confirmación es de RF-030 y RF-031 la hereda) y el mensaje «El mantenimiento afecta a N
    partido(s), que no se reprogramarán solos.».
  - En la ocupación sale como un bloque `MANTENIMIENTO`, separado de los `BLOQUEO`, con su motivo.
- **Auditoría del `POST` [PROPUESTA].** Cada tipo tiene su acción: `REGISTRAR_DISPONIBILIDAD`,
  `BLOQUEAR_CANCHA` y `REGISTRAR_MANTENIMIENTO`. El evento lleva el `motivo` y en el detalle
  `canchaId`, `tipo`, `desde`, `hasta`, `partidosAfectados` y `partidosAfectadosIds`.

#### 6.6.1 Horarios de club y comodato (B-13.1, RF-119, DEC-33)

`GET /api/v1/canchas/{id}/horarios-club?temporadaId={uuid}` responde `200` con la lista de horarios
de la cancha en esa temporada, vacía si no tiene, ordenada por `diaSemana`, `horaInicio` y orden de la
categoría. `temporadaId` es obligatorio: sin él, `400 VALIDACION`. Una temporada cerrada se consulta
igual.

```json
[
  {
    "id": "…",
    "canchaId": "…",
    "temporadaId": "…",
    "categoria": { "id": "…", "nombre": "Libre" },
    "club": { "id": "…", "nombre": "Halcones" },
    "diaSemana": 6,
    "horaInicio": "10:00:00",
    "horaFin": "12:00:00",
    "esComodato": false
  }
]
```

`PUT /api/v1/canchas/{id}/horarios-club` reemplaza de forma **atómica** los horarios de la cancha en
**una** temporada y responde `200` con la lista resultante, igual que el `GET`. Los horarios de otras
temporadas no se tocan; `"horarios": []` borra los de esa temporada.

```json
{
  "temporadaId": "…",
  "horarios": [
    { "categoriaId": "…", "diaSemana": 6, "horaInicio": "10:00", "horaFin": "12:00", "clubId": "…" }
  ]
}
```

- `diaSemana` va de 1 (lunes) a 7 (domingo). Las horas se responden como `HH:mm:ss` y se aceptan como
  `HH:mm` o `HH:mm:ss`; el intervalo es semiabierto (`horaFin` exclusiva).
- `clubId` es opcional en una cancha de club (se toma su propietario) y obligatorio en una de la liga.
- **Ámbito.** Una cancha `CLUB` se autoriza en el ámbito de su club; una `LIGA` o `EXTERNO`, en el de
  la liga, como el resto de §6.6.

Errores del `PUT`, en este orden (todo se valida en una transacción, con la cancha bloqueada):

| Orden | Caso | Respuesta |
| --- | --- | --- |
| 1 | Cancha inexistente o de otra liga | `404 NO_ENCONTRADO` |
| 2 | Sin `canchas.cancha:EDITAR` en el ámbito de la cancha | `403 VAL-015` |
| 3 | Cuerpo mal formado, `temporadaId` u `horarios` ausentes, campo obligatorio ausente, `diaSemana` fuera de 1–7 o `horaInicio` ≥ `horaFin` | `400 VALIDACION` con `errores` (`horarios[i].campo`) |
| 4 | Temporada que no existe en la liga | `422 REFERENCIA_INVALIDA` |
| 5 | Temporada `CERRADA`: «La temporada «X» está cerrada: sus horarios son de solo lectura.» | `409 RF-003` |
| 6 | Categoría no asignada a la temporada (`categoriasInvalidas`) o club que no es de la liga (`clubesInvalidos`) | `422 REFERENCIA_INVALIDA` |
| 7 | Tipo de cancha (DEC-33): `EXTERNO` no admite horarios; en `CLUB` el club es su propietario y `esComodato` es `false` (un `clubId` distinto se rechaza); en `LIGA` cada horario es comodato (`esComodato` es `true`) y `clubId` es obligatorio | `422 RF-119` |
| 8 | Dos horarios de la cancha del mismo día se traslapan, sin importar la categoría (10:00–12:00 y 12:00–14:00 no chocan) | `422 RF-119` |

El `GET` responde `404` y `403` igual (con `canchas.cancha:CONSULTAR`) y `422 REFERENCIA_INVALIDA` si
la temporada no es de la liga.

**Auditoría.** El `PUT` registra en `M-06` la acción `REGISTRAR_HORARIOS_CANCHA` sobre la cancha
(`entidad = canchas`, `entidadId` = la cancha), sin motivo, con el detalle
`{ "canchaId", "temporadaId", "horarios": <número de horarios guardados> }`. Se registra también al
vaciar la lista.

#### 6.6.2 Trámites de cancha: Formatos 1 y 2 (B-14, RF-116 a RF-118, DEC-35)

- **Alta.** `POST /solicitudes-cancha` lleva `tipo` (`PRESTAMO`, `COMODATO` u `OFERTA`), el
  solicitante y el bloque del formato: `prestamo` (Formato 1) u `oferta` (Formato 2), con los campos
  de la plantilla del cliente (R8); el otro bloque no se admite (`400 VALIDACION`). El préstamo y el
  comodato los pide un club (`clubId`); la oferta, un club o un oferente externo
  (`solicitanteExterno`), nunca los dos (`400 VALIDACION`). Un club inexistente o cerrado, o un
  `canchaId` que no es de la liga, responden `422 REFERENCIA_INVALIDA`; la hora de término no
  posterior al inicio, `400 VALIDACION`. Responde `201` con `Location`.
- **Permiso y ámbito.** El alta exige `canchas.solicitud:CREAR` sobre el club; la oferta de un
  externo, sobre la liga (la captura el Secretario). Un club o un trámite fuera del ámbito responde
  `404` (VAL-024).
- **Número de oficio (RF-118, DEC-135, [PROPUESTA]).** Lo asigna el sistema al capturar:
  `OF-<año>-<consecutivo de cuatro dígitos>` por liga y año de la zona de negocio (el ejemplo
  `OF-2025-0089` de B-07). Un candado consultivo de la transacción evita repetir el consecutivo y
  `UNIQUE (liga_id, numero_oficio)` es la última garantía.
- **Estados.** El Formato 1 nace `PENDIENTE` y el Formato 2 `PENDIENTE_NEGOCIACION`.
  `POST …/{id}/resolucion` lleva `{ "decision", "observaciones" }`: `APROBADA` o `NO_APROBADA` en el
  Formato 1, `ACEPTADA` o `RECHAZADA` en el Formato 2 (otra decisión, `400 VALIDACION`). La decisión
  desfavorable exige observaciones de 10 a 500 caracteres (`400 RN-021`) y las guarda como motivo.
  Los estados finales no cambian: dictaminar otra vez responde `409 RF-117` (Formato 1) o
  `409 RF-116` (Formato 2). Aceptar una oferta o aprobar un comodato **no** da de alta la cancha ni
  sus horarios (DEC-138): la cancha se registra con RF-027 y los horarios con RF-119 (§6.6.1).
- **Bandeja.** `GET /solicitudes-cancha` admite `tipo` repetido (la pestaña del Formato 1 manda
  `tipo=PRESTAMO&tipo=COMODATO`), `estado`, `pagina` y `tamano`, por captura descendente. Como el
  catálogo no tiene permiso de consulta propio, basta cualquiera de los tres permisos (DEC-136, **[PROPUESTA]**);
  el alcance se une en el `WHERE` y en el `COUNT`, y las ofertas de externos solo las ve un ámbito de
  liga. `GET …/contexto` responde `{ clubes, canchas, capturaExternos }`: los clubes `ACTIVO` del
  alcance de `CREAR`, las canchas activas y si la sesión captura ofertas de externos; sin `CREAR`, las
  listas van vacías.
- **Carta (DEC-137, [PROPUESTA]).** `GET …/{id}/oficio` exige `canchas.oficio:GENERAR` (en la matriz
  inicial, solo el Secretario; la Directiva no imprime la carta de su solicitud salvo que la matriz le
  otorgue el permiso con ámbito de club) y responde los datos de la carta (`numeroOficio`, `asunto`, `liga`,
  `fechaEmision`, `solicitante`, el bloque del formato y el estado con su dictamen). La interfaz la
  compone con la plantilla del formato y la guarda en PDF con el diálogo de impresión del navegador.
- **Auditoría.** `REGISTRAR_SOLICITUD_CANCHA` y `RESOLVER_SOLICITUD_CANCHA` en `M-06`, con
  identificadores, tipo, número de oficio y estados; nunca nombres, teléfonos ni correos del formato.

### 6.7 M-07 Programación manual

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /jornadas?grupoId=&estadoPublicacion=` · `POST /jornadas` | Jornadas del grupo | `programacion.jornada:EDITAR` | RF-033 | N |
| `GET /jornadas/{id}` | Jornada con partidos, conflictos y advertencias | `programacion.jornada:CONSULTAR` | RF-033 | N |
| `POST /jornadas/{id}/partidos` · `PATCH /partidos/{id}` | Registrar o ajustar partido (equipos, cancha, inicio); solo con la jornada en `BORRADOR`; ajuste con `motivo`; `motivoAceptacion` para aceptar una advertencia de localía | `programacion.jornada:EDITAR` | RF-033, RF-036, RF-040, RF-120 | N · B |
| `POST /jornadas/{id}/verificacion` | Revisa todos los partidos sin guardar: `{ conflictos: [ { partidoId, codigo, mensaje } ], advertencias: [...] }` | `programacion.jornada:EDITAR` | RF-039, RF-120 | N · B |
| `POST /jornadas/{id}/envio` · `/resolucion` | Aprobación y publicación | ver §2.1 | RF-121, RF-090 | N |
| `GET /fechas?temporadaId=` · `GET /fechas/{id}` | Fechas de la temporada y vista por fecha en bloques «Categoría – Grupo» (F-22, F-23) | `programacion.jornada:CONSULTAR` | RF-033, RF-037, RF-042 | N |
| `POST /fechas/{id}/verificacion` | Conflictos y advertencias de toda la fecha, sin guardar | `programacion.jornada:CONSULTAR` | RF-039 | N |
| `POST /fechas/{id}/envio` · `/resolucion` | Envío y dictamen de la fecha completa | `ENVIAR` · `APROBAR` | RF-121, RF-090 | N |
| `POST /temporadas/{id}/calendario` · `GET /temporadas/{id}/calendario` | Generar o regenerar y consultar el calendario de la temporada (H-32.2) | `programacion.jornada:EDITAR` · `CONSULTAR` | RF-034, RF-037 | N |
| `PATCH /fechas/{id}` | Cambiar o agregar días de la fecha en `BORRADOR`, con motivo (DEC-193) | `programacion.jornada:EDITAR` | RF-035, RF-090 | N |
| `POST /fechas/{id}/asignacion` | Asignar cancha y horario a los pendientes o regenerar la fecha completa (H-33.2, DEC-181) | `programacion.jornada:EDITAR` | RF-035, RF-041, RF-042 | N |

La verificación y el guardado usan las mismas reglas: VAL-003 (equipo duplicado en el bloque),
VAL-004 (cancha ocupada), VAL-005 (cancha bloqueada o fuera de disponibilidad), VAL-006 (equipo con
baja o sanción). Cada conflicto nombra los partidos, la cancha y el horario que chocan.

**Advertencias de localía (B-15.1, B-15.2, RF-120, VAL-027).** No son conflictos duros: se aceptan con
motivo (§3.1) y no impiden enviar la jornada a aprobación. Se evalúan dos reglas con los datos de la
base, nunca con valores fijos en el código:

- **Cancha del local (DEC-33, DEC-34).** El club del equipo local tiene cancha si es propietario de
  una cancha activa (`propietario = 'CLUB'`) o si tiene horarios en `horarios_cancha_club` (oferta o
  comodato) para la temporada del grupo y la categoría del local. Si la tiene y el partido se
  programa en otra (incluida la del rival), se advierte nombrando las canchas.
- **Últimas fechas en la unidad deportiva (RN-049).** En una jornada de la fase regular con
  `numero > fechasFaseRegular − fechasFinalesUnidadDeportiva` (parámetros vigentes, §6.2), una
  cancha con `ubicacion = 'EXTERNA'` se advierte; el mensaje rotula el número de fechas como valor de
  demostración, no oficial. Sin `fechasFaseRegular` definido, en una fase final o con
  `fechasFinalesUnidadDeportiva = 0`, la regla no se evalúa.

El alta y el ajuste validan la localía después de los conflictos duros. Si hay advertencias y la
petición no trae `motivoAceptacion` de al menos 10 caracteres (como RN-021), responden
`409 VAL-027` con la primera explicación y las extensiones `aceptable: true` y
`advertencias: [ { partidoId, codigo, mensaje } ]` (`partidoId` nulo en un alta). Con motivo, el
partido se guarda con `motivoLocalia` y se audita `ACEPTAR_LOCALIA` en `M-07` sobre `partidos`, con el
motivo y `{ jornadaId, reglas: ["CANCHA_DEL_LOCAL" | "UNIDAD_DEPORTIVA"] }`. Un ajuste sin
`motivoAceptacion` conserva el `motivoLocalia` guardado si la advertencia sigue **[PROPUESTA]**; si el
partido ya respeta la localía, `motivoLocalia` se borra. Un `PATCH` que solo trae `motivoAceptacion`
registra la aceptación sin cambiar equipos, cancha ni inicio (B-15.2): si el partido ya respeta la
localía no escribe; si la advertencia sigue, guarda el motivo y audita `ACEPTAR_LOCALIA`. La
verificación devuelve las advertencias de todos los partidos no cancelados en
`advertencias` con `codigo: "VAL-027"`; si el partido ya tiene motivo, el mensaje lo indica.

**Aprobación y publicación (H-13.1).** El detalle y las respuestas de las dos acciones traen, además,
`enviadaPor`, `enviadaEn`, `aprobadaPor`, `aprobadaEn`, `publicadaEn` y `observaciones` (los vacíos
se omiten). El trigger `tg_jornadas_publicacion` valida la misma máquina de estados y registra cada
paso en `historial_publicacion` con quien lo hizo y su motivo.

- `POST /jornadas/{id}/envio` no lleva cuerpo y exige `programacion.jornada:ENVIAR`. Orden de
  errores: permiso (`403`) → jornada de la liga (`404`) → no es de una fecha de programación
  (`409 RF-090`, ver abajo) → en `BORRADOR` (`409 RF-090`) → al menos un
  partido no cancelado (`409 RF-121`, **[PROPUESTA]**) → sin conflictos duros (`409 RF-039`, con la
  extensión `conflictos: [ { partidoId, codigo, mensaje } ]` igual que la verificación). Fija
  `enviadaPor` con el perfil de la sesión y `enviadaEn`, y responde `200` con la jornada en
  `REVISION` y sus partidos.
- `POST /jornadas/{id}/resolucion` lleva `{ "decision": "PUBLICADO"|"BORRADOR", "observaciones"? }` y
  exige `programacion.jornada:APROBAR`. Orden: permiso (`403`) → `404` → no es de una fecha de
  programación (`409 RF-090`) → en `REVISION` (`409 RF-090`) → `decision` presente (`400`) → separación de funciones (`403 RF-121`) →
  observaciones de la devolución (`400 RF-121`). **Quien programa no aprueba:** se rechaza si quien
  dictamina creó o envió la jornada, o si alguna de sus asignaciones le da también
  `programacion.jornada:EDITAR` (par incompatible del Apéndice E); `ck_jornadas_aprobacion` es la
  última garantía.
  - `PUBLICADO` fija `aprobadaPor` y `aprobadaEn`; el trigger fija `publicadaEn` y los partidos
    aparecen en `v_publica_programacion`. Las observaciones son opcionales y, si llegan, son el motivo
    del historial.
  - `BORRADOR` exige observaciones de al menos 10 caracteres, las guarda y las usa como motivo del
    historial; la jornada vuelve a admitir altas y ajustes.
- Auditoría en `M-07` sobre `jornadas`: `ENVIAR_JORNADA_A_APROBACION` (`{ estadoAnterior, estado,
  partidos }`), `APROBAR_JORNADA` y `DEVOLVER_JORNADA` (`{ estadoAnterior, estado }`, con las
  observaciones como motivo). Al publicarse, la jornada emite `JornadaPublicada` y `tesoreria` genera
  sus cobros en la misma transacción (B-30, RF-136; ver §6.13 «Cobros automáticos»).

**Jornadas de una fecha (DEC-175, DEC-188).** Una jornada con `fecha_id` (la crea la asignación de
la fecha, H-33) no se envía ni se dictamina sola: `POST /jornadas/{id}/envio` y `/resolucion`
responden `409 RF-090` **[PROPUESTA]** con un `detail` que nombra la fecha y pide hacerlo con
`POST /fechas/{id}/envio` o `/resolucion`. Así todas las jornadas de la fecha cambian juntas. El alta,
el ajuste y la verificación por jornada no cambian.

**Fecha de programación (H-35.1, DEC-175, DEC-180).** Una fecha (`fechas_programacion`) agrupa las
jornadas de todos los grupos de la misma semana (`jornadas.fecha_id`) y los emparejamientos del
calendario con `numeroFecha` igual a su `numero`. Su `estadoPublicacion` no se guarda: es el de la
jornada menos avanzada (`BORRADOR` si no tiene jornadas). Los partidos `CANCELADO` no aparecen
(DEC-181) y su emparejamiento vuelve a contarse como sin asignar.

- `GET /fechas?temporadaId=&pagina=&tamano=` exige `temporadaId` (`400`) de una temporada de la liga
  (`404`). Cada fila: `id`, `temporadaId`, `numero`, `dias`, `estadoPublicacion`, `jornadas`,
  `partidos` (vigentes), `sinAsignar` (sin contar descansos), `descansos` (H-32.2) y `generadaEn`; sin
  `bloques`. Desde H-32.2 solo llega hasta la última fecha del calendario vigente (ver abajo).
- `GET /fechas/{id}` agrega los datos de publicación de sus jornadas (`enviadaPor`, `enviadaEn`,
  `aprobadaPor`, `aprobadaEn`, `publicadaEn`, `observaciones`) y `bloques`, ordenados por el primer día
  de juego de la categoría, su `orden` y el nombre del grupo:

  ```json
  { "categoria": { "id": "…", "nombre": "Primera" }, "grupo": { "id": "…", "nombre": "Grupo 1" },
    "diasJuego": [7], "jornadaId": "…", "estadoPublicacion": "BORRADOR",
    "partidos": [ { "id": "…", "jornadaId": "…", "emparejamientoId": "…",
                    "equipoLocal": { "id": "…", "nombre": "Halcones" }, "equipoVisitante": { … },
                    "cancha": { … }, "inicio": "…", "finBloque": "…", "estado": "PROGRAMADO",
                    "arbitro": { "id": "…", "nombre": "Juan Pérez" }, "designacionAutomatica": true,
                    "asignadoAutomaticamente": true, "ajustado": false, "motivoLocalia": "…" } ],
    "descansos": [ { "emparejamientoId": "…", "equipo": { … } } ],
    "sinAsignar": [ { "emparejamientoId": "…", "equipoLocal": { … }, "equipoVisitante": { … },
                      "codigo": "VAL-007", "causa": "Sin horario libre el domingo…" } ] }
  ```

  `arbitro` se omite si el partido no tiene central vigente («Sin árbitro», DEC-182). `ajustado` es
  verdadero si el partido se ajustó con motivo (`AJUSTAR_PARTIDO`, RF-040). En `sinAsignar`, `codigo` y
  `causa` se omiten mientras la asignación automática no haya procesado el emparejamiento.
- `POST /fechas/{id}/verificacion` responde `{ conflictos, advertencias }` con avisos
  `{ jornadaId, partidoId?, emparejamientoId?, codigo, mensaje }`. Los conflictos son los de
  `POST /jornadas/{id}/verificacion` de cada jornada. Las advertencias no bloquean el envío: localía
  (`VAL-027`), sin árbitro central (`RF-023`) y sin asignar (su `codigo`, o `RF-033` **[PROPUESTA]**
  si aún no tiene causa). El aviso `RF-023` dice «Sin árbitro central: ‹Local› vs ‹Visitante›.
  ‹causa› La Comisión de Arbitraje lo designa en F-24.»: la causa de DEC-216 si ningún árbitro validado
  puede pitarlo, o «N árbitros validados pueden pitarlo.» **[PROPUESTA]** si alguno puede; se calcula al
  verificar, sin escribir ni bloquear y sin permiso adicional (DEC-220).
- `POST /fechas/{id}/envio`, sin cuerpo, exige `programacion.jornada:ENVIAR`. Orden: permiso (`403`) →
  fecha de la liga (`404`) → con jornadas (`409 RF-121`) → todas en `BORRADOR` (`409 RF-090`, nombra la
  primera que no lo está) → al menos un partido vigente (`409 RF-121`, **[PROPUESTA]**) → sin conflictos
  duros en ninguna jornada (`409 RF-039` con la extensión `conflictos`). Todas pasan a `REVISION` en
  una transacción; los sin asignar y los sin árbitro no la bloquean.
- `POST /fechas/{id}/resolucion` lleva `{ "decision": "PUBLICADO"|"BORRADOR", "observaciones"? }` y
  exige `programacion.jornada:APROBAR`. Orden: permiso → `404` → con jornadas y todas en `REVISION`
  (`409 RF-090`) → `decision` (`400`) → separación de funciones (`403 RF-121`: quien creó o generó la
  fecha, quien creó o envió alguna de sus jornadas o quien además tiene `programacion.jornada:EDITAR`)
  → observaciones de la devolución (`400 RF-121`). Todas las jornadas cambian juntas; al publicar,
  cada una emite `JornadaPublicada` y sus partidos aparecen en `v_publica_programacion`.
- Las respuestas de `envio` y `resolucion` son el detalle de la fecha. Se bloquea con `FOR UPDATE` la
  fila de la fecha y después sus jornadas por `id`.
- Auditoría en `M-07` sobre `fechas_programacion`: `ENVIAR_FECHA_A_APROBACION` (`{ estadoAnterior,
  estado, jornadas, partidos, sinAsignar }`), `APROBAR_FECHA` y `DEVOLVER_FECHA` (`{ estadoAnterior,
  estado, jornadas }`, con las observaciones como motivo). Cada jornada deja su paso en
  `historial_publicacion`.
- **Asignar a mano un pendiente.** `POST /jornadas/{id}/partidos` acepta `emparejamientoId` opcional.
  Debe ser de la misma fecha y grupo que la jornada (`422 REFERENCIA_INVALIDA`), no un descanso
  (`422 RF-037`), con el mismo local y visitante (`422 REFERENCIA_INVALIDA`) y sin partido vigente
  (`409 DUPLICADO`; uno cancelado no cuenta). El partido queda ligado y se limpian `codigo` y `causa`;
  `REGISTRAR_PARTIDO` incluye `emparejamientoId` en su detalle.

**Programación automática (H-32.2, DEC-187 a DEC-193).** El calendario por grupo se guarda en
`emparejamientos` (método del círculo, DEC-174) y las fechas de la temporada en `fechas_programacion`.
Toda lectura usa solo el calendario **vigente**: regenerar marca las filas anteriores como
reemplazadas (`vigente = false`, con quién, cuándo y por qué) y nunca borra (DEC-189). Generar el
calendario **no crea jornadas**: las crea la asignación automática de H-33 (DEC-188).

- `POST /temporadas/{id}/calendario` exige `programacion.jornada:EDITAR`. Cuerpo opcional:

  ```json
  { "fechaInicio": "2031-03-05", "grupoIds": ["…"], "motivo": "Se agregó un equipo al grupo" }
  ```

  `fechaInicio` (por defecto, el inicio de la temporada) fija la fecha 1 en el primer sábado en o
  después de ella (DEC-186); `grupoIds` (por defecto, todos los grupos de la temporada) elige qué
  grupos generar; `motivo` es obligatorio si algún grupo elegido ya tiene calendario. Responde `201`
  con `Location: /api/v1/temporadas/{id}/calendario`:

  ```json
  { "versionParametros": 3, "vueltas": 2, "totalFechas": 10, "fechasCreadas": 10,
    "grupos": [ { "grupoId": "…", "nombre": "Grupo 1", "equipos": 5, "fechas": 10, "reemplazado": false } ],
    "gruposOmitidos": [ { "grupoId": "…", "nombre": "Grupo 2", "equiposActivos": 1,
                          "causa": "Tiene 1 equipo activo; se necesitan al menos 2 para generar su calendario." } ],
    "advertencias": [ { "tipo": "JORNADAS_MANUALES", "grupoId": "…", "mensaje": "…" } ] }
  ```

  Orden de validación: permiso (`403`) → cuerpo (`grupoIds` vacío o con nulos, fecha mal formada:
  `400 VALIDACION`) → temporada de la liga (`404`) → temporada `CERRADA` (`409 RF-003`) → sin parámetros
  vigentes (`409 RF-006`, DEC-190) → un `grupoId` que no es de la temporada (`422 REFERENCIA_INVALIDA`)
  → algún grupo con calendario y sin motivo de al menos 10 caracteres (`400 RN-021`) → algún grupo con un
  emparejamiento vigente ligado a un partido o con una jornada ligada a una fecha (`409 RN-020`, con la
  extensión `grupos: [ { grupoId, nombre } ]`; no escribe nada) → ningún grupo con al menos 2 equipos
  `ACTIVO` (`422 VALIDACION`; los que no llegan se omiten, DEC-187) → `fechaInicio` anterior al inicio de
  la temporada, o enviada cuando alguna fecha ya tiene jornadas (`422 VALIDACION`).

  Las vueltas salen de los parámetros vigentes (DEC-190) y los equipos `ACTIVO` se ordenan por `id`
  (DEC-185). `F` (`totalFechas`) es la última fecha del calendario vigente de la temporada; se crean
  las fechas 1…F que falten (sábado y domingo). Sin jornadas en ninguna fecha, `fechaInicio` recalcula
  los días de todas; sin `fechaInicio`, las existentes conservan sus días y las nuevas siguen semana a
  semana desde la última (DEC-193). Las fechas mayores que F se conservan y no se listan. Advertencias
  que no bloquean: `FECHAS_DESPUES_DEL_FIN` (el último día pasa de `fechaFin` de la temporada) y
  `JORNADAS_MANUALES` (el grupo tiene jornadas sin fecha ni fase con el número de una de sus fechas).
  Todo ocurre en una transacción con la temporada bloqueada. Auditoría `GENERAR_CALENDARIO` en `M-07`
  sobre `temporadas`, con el motivo y `{ versionParametros, vueltas, fechaInicio?, grupos, reemplazados,
  omitidos, emparejamientos, totalFechas, fechasCreadas, fechasRecalculadas }` (DEC-192).
- `GET /temporadas/{id}/calendario` exige `programacion.jornada:CONSULTAR` (temporada ajena: `404`).
  Devuelve todos los grupos por `orden` de la categoría y nombre del grupo:

  ```json
  { "temporadaId": "…",
    "grupos": [ { "grupoId": "…", "nombre": "Grupo 1", "categoria": { "id": "…", "nombre": "Libre" },
                  "generado": true,
                  "fechas": [ { "numero": 1, "vuelta": 1,
                                "partidos": [ { "emparejamientoId": "…",
                                                "local": { "id": "…", "nombre": "Halcones", "estado": "ACTIVO" },
                                                "visitante": { "id": "…", "nombre": "Toros", "estado": "ACTIVO" },
                                                "partidoId": "…" } ],
                                "descansa": { "id": "…", "nombre": "Águilas", "estado": "ACTIVO" } } ] } ] }
  ```

  `generado` es falso (y `fechas` vacía) en un grupo sin calendario; `descansa` se omite si el grupo es
  par y `partidoId` mientras el emparejamiento no tiene partido.
- `GET /fechas?temporadaId=` (H-35.1) solo lista las fechas con `numero ≤ F`; sin calendario vigente
  no lista ninguna. Sus conteos (`sinAsignar` y el nuevo `descansos`) solo cuentan emparejamientos
  vigentes, igual que los bloques de `GET /fechas/{id}`, la verificación y el alta manual con
  `emparejamientoId` (uno reemplazado responde `422 REFERENCIA_INVALIDA`).
- `PATCH /fechas/{id}` lleva `{ "dias": ["2031-03-07", "2031-03-08"], "motivo": "…" }` y exige
  `programacion.jornada:EDITAR`. Orden: permiso (`403`) → `dias` presente (`400 VALIDACION`) → motivo de
  al menos 10 caracteres (`400 RN-021`) → fecha de la liga (`404`) → temporada `CERRADA` (`409 RF-003`)
  → alguna jornada de la fecha en `REVISION` o `PUBLICADO` (`409 RF-090`) → de 1 a 7 días, sin repetir
  y dentro de 7 días consecutivos (`422 VALIDACION`) → un día que ya es de otra fecha de la temporada
  (`422 VALIDACION`, nombra la fecha) → quitar un día con partidos no cancelados (`409 RF-090`, nombra
  el día y cuántos). Se guardan ordenados; sin cambios no escribe ni audita. Responde `200` con la fecha
  en la forma del listado (sin `bloques`). Auditoría `CAMBIAR_DIAS_FECHA` sobre `fechas_programacion`
  con el motivo y `{ temporadaId, numero, diasAnteriores, dias }`.

**Asignación automática de la fecha (H-33.2, H-34.2, DEC-181, DEC-206 a DEC-208, DEC-217 a DEC-220).** El motor de H-33.1
(ADR-0015) coloca cancha y horario a los emparejamientos de la fecha en todas las categorías y grupos
a la vez; el resultado queda en `BORRADOR` para que el Programador lo revise.

- `POST /fechas/{id}/asignacion` exige `programacion.jornada:EDITAR` (ámbito `LIGA`) y lleva
  `{ "modo": "PENDIENTES" | "REGENERAR", "motivo"? }`. `PENDIENTES` solo coloca los emparejamientos
  sin partido vigente; lo ya asignado o ajustado a mano no se mueve. `REGENERAR` cancela con el motivo
  los partidos de la fecha y vuelve a asignar todo.
- Orden de validación (DEC-206): permiso (`403 VAL-015`) → `modo` presente (`400 VALIDACION`; un valor
  que no existe responde `400` antes de llegar al servicio) → con `REGENERAR`, motivo de al menos 10
  caracteres (`400 RN-021`) → fecha de la liga (`404`; se bloquea con `FOR UPDATE` la fecha y después
  sus jornadas, como el envío) → temporada `CERRADA` (`409 RF-003`) → alguna jornada de la fecha que no
  está en `BORRADOR` (`409 RF-090`, nombra la primera) → sin parámetros vigentes (`409 RF-006`) → la fecha
  sin emparejamientos en el calendario vigente (`422 VALIDACION`, **[PROPUESTA]**) → un grupo con una
  jornada manual con el número de la fecha (`409 DUPLICADO`, **[PROPUESTA]**, DEC-208) → con
  `REGENERAR`, un partido de la fecha que no está `PROGRAMADO` ni `SUSPENDIDO` (`409 RF-046`,
  **[PROPUESTA]**, DEC-208).
- En una transacción: con `REGENERAR`, los partidos de la fecha pasan a `CANCELADO` con el motivo en
  su historial, sus designaciones quedan `vigente = false` con `motivo_cambio` y los emparejamientos
  sueltan el partido cancelado (nada se borra, RF-044). Se crean las jornadas de la fecha que falten
  (una por grupo, en `BORRADOR`, con el número de la fecha). Cada candidato del motor se confirma con
  las reglas de H-12: si aún tiene un conflicto duro no se inserta y queda sin asignar con ese código
  y su mensaje (DEC-207). El partido se guarda `PROGRAMADO` con `asignadoAutomaticamente` y, si la
  localía advierte, con un `motivoLocalia` que empieza con «Asignación automática:» (DEC-207). Los
  que no caben guardan `codigo` (`VAL-006` o `VAL-007`) y `causa` (DEC-180).
- **Árbitro central automático (H-34.2).** Si la sesión tiene además `arbitraje.designacion:AUTOMATICA`
  (ámbito `LIGA`, PROGRAMADOR), en la misma transacción se designa el central de los partidos
  existentes no cancelados de la fecha (solo con `PENDIENTES`) y de los recién insertados (DEC-217): los
  que ya tienen central vigente, manual o automático, se omiten, y uno cuyo central se retiró sin
  sustituto lo vuelve a recibir. Se elige con la carga y el desempate de DEC-214 y se guarda
  `automatica = true`. Candados en un orden total: fecha → jornadas → partidos existentes de la fecha
  (`FOR UPDATE`, en los dos modos y antes de escribir) → canchas → árbitros (DEC-215, DEC-217). Un
  central que declaró no estar disponible en ese horario se designa igual y deja una advertencia
  (`RF-022`, DEC-219). Sin el permiso, la asignación sigue sin designaciones y `advertencias` lo indica
  (DEC-218).
- Respuesta `200`:

  ```json
  { "fecha": { "id": "…", "numero": 3, "estadoPublicacion": "BORRADOR", "bloques": [ … ] },
    "totales": { "asignados": 14, "noAsignados": 2, "cancelados": 0, "centrales": 13, "sinArbitro": 1 },
    "noAsignados": [ { "emparejamientoId": "…", "codigo": "VAL-007",
                       "causa": "Sin horario libre el domingo para Libre (bloque de 120 min): 14 franjas libres, 16 partidos" } ],
    "sinArbitro": [ { "partidoId": "…", "codigo": "RF-023",
                      "causa": "Ninguno de los 6 árbitros validados puede pitarlo: 1 es de los clubes del partido (VAL-019) y 5 ya pitan a esa hora (RF-039)." } ],
    "advertencias": [ "Halcones vs Toros: el árbitro central designado declaró que no está disponible en ese horario (RF-022). La Comisión puede cambiarlo en F-24." ],
    "resumen": [ { "dia": "2031-03-09", "franjasLibres": 14, "bloqueMinutos": 120, "partidosRequeridos": 16 } ] }
  ```

  `fecha` es el detalle de `GET /fechas/{id}`, ya con los centrales designados. `totales.centrales` y
  `totales.sinArbitro` cuentan las designaciones de esta operación y los partidos que se quedaron sin
  central elegible; `sinArbitro` los lista con la causa de DEC-216 (vacía sin el permiso, DEC-219).
  `advertencias` no bloquean: las del motor (por ejemplo, una categoría sin día de juego en la fecha,
  DEC-177), la de la sesión sin `arbitraje.designacion:AUTOMATICA` **[PROPUESTA]** (DEC-218) y una por
  cada central con `RF-022` **[PROPUESTA]** (DEC-219). En `resumen`, las franjas son los
  bloques que cabían antes de asignar y los partidos requeridos, los que tienen ese día permitido
  (**[PROPUESTA]**, `ResumenDia`).
- `fechas_programacion` guarda `generada_por`, `generada_en` y `parametros_generacion`
  (`{ versionParametros, modo, motivo?, asignados, noAsignados, cancelados, designacionAutomatica,
  centrales, sinArbitro }`, RF-041). Auditoría `GENERAR_FECHA` en `M-07` sobre `fechas_programacion`,
  con el motivo y `{ temporadaId, numero, modo, versionParametros, asignados, noAsignados, cancelados,
  designacionAutomatica, centrales, sinArbitro, jornadasCreadas }`, sin nombres ni datos personales; cada
  central designado deja además `DESIGNAR_ARBITRO` en `M-05` con `automatica: true` (DEC-213, DEC-219).
  Con los mismos datos se obtiene la misma cancha y el mismo horario; los árbitros dependen además
  del orden de los partidos a la misma hora (DEC-214).

### 6.8 M-08 Partidos, cédula y resultados

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /partidos?jornadaId=&estado=&desde=&hasta=` · `GET /partidos/{id}` | Partido con estado, historial, árbitros, cédula, resultado y `accionesPermitidas` | `partidos.partido:CONSULTAR` | RF-043, RF-046 | N |
| `GET /partidos/{id}/historial` | Transiciones con usuario y motivo | `partidos.partido:CONSULTAR` | RF-045 | N |
| `POST /partidos/{id}/transiciones` | Suspender, reprogramar o cancelar | `partidos.partido:CAMBIAR_ESTADO` | RF-043 | N |
| `GET /partidos/{id}/cedula` · `POST /partidos/{id}/cedula` | Obtener o abrir la cédula (`EN_CAPTURA`); detalle abajo | `partidos.cedula:CONSULTAR` · `partidos.cedula:CAPTURAR` (ámbito `LIGA`) | RF-025, RF-046 | N |
| `GET /cedulas/{id}/jugadores` · `GET /cedulas/{id}/tipos-incidencia` | Candidatos a la alineación con su elegibilidad y tipos de incidencia activos para capturar (DEC-172) | `partidos.cedula:CAPTURAR` | RF-019, RF-026, RF-110 | N |
| `PUT /cedulas/{id}/alineaciones` | Alineación de ambos equipos; valida elegibilidad por jugador (`422` con `errores` por jugador) | `partidos.cedula:CAPTURAR` | RF-019, RF-110, VAL-002, VAL-018 | N |
| `PUT /cedulas/{id}/marcador` | Goles, penales, no presentación (marcador por default de los parámetros) | `partidos.cedula:CAPTURAR` | RF-047, RF-126 | N |
| `POST /cedulas/{id}/eventos` · `DELETE /cedulas/{id}/eventos/{eventoId}` | Goles, tarjetas, sustituciones | `partidos.cedula:CAPTURAR` | RF-025 | N |
| `POST /cedulas/{id}/incidencias` | Incidencia con tipo e involucrados | `partidos.cedula:CAPTURAR` | RF-026 | N |
| `PUT /cedulas/{id}/observaciones` | Observaciones del partido | `partidos.cedula:CAPTURAR` | RF-025 | N |
| `POST /cedulas/{id}/envio` · `/aprobacion` · `/devolucion` · `/reapertura` | Flujo de la cédula | ver §2.1 | RF-122 a RF-124 | N |
| `GET /cedulas?estado=PENDIENTE_VALIDACION&plazoVencido=true` | Bandeja del árbitro o de la Comisión | `partidos.cedula:CONSULTAR` | RF-123, RF-124 | N |
| `GET /partidos/{id}/resultados` | Versiones del resultado (vigente y anteriores) | `partidos.partido:CONSULTAR` | RF-052 | N |
| `GET /grupos/{id}/tabla` | Tabla calculada con `calcular_tabla` y la versión de parámetros usada | `partidos.partido:CONSULTAR` | RF-049, RF-050, RF-053 | N |
| `POST /grupos/{id}/sorteos` | Registrar sorteo de desempate | `partidos.sorteo:REGISTRAR` | RF-050 | N |
| `GET /grupos/{id}/estadisticas-jugador` | Goles, tarjetas y participaciones | `partidos.partido:CONSULTAR` | RF-054 | B |

No existe un endpoint para escribir un resultado oficial: lo crea la aprobación de la cédula o una
resolución disciplinaria (RF-048, VAL-010).

#### Consulta y transiciones del partido (H-15.1, RF-043, RF-045, RF-046, DEC-160 a DEC-162)

**Visibilidad (DEC-161).** Listado, detalle e historial exigen `partidos.partido:CONSULTAR` y usan
su ámbito en el `WHERE` y en el `COUNT`: `LIGA` ve todos los partidos de la liga; `CLUB`, los de los
equipos de sus clubes; `PROPIO`, los del equipo donde la persona de la sesión o un menor bajo su
tutela tiene un registro no rechazado y los que arbitra con designación vigente. Club y propio solo
ven jornadas `PUBLICADO` **[PROPUESTA]**. Un partido fuera de alcance responde `404 NO_ENCONTRADO`.
Las tres respuestas llevan `Cache-Control: no-store`.

`GET /partidos?jornadaId=&estado=&desde=&hasta=&pagina=0&tamano=20` devuelve la envoltura de
paginación (§4) por `inicio` ascendente; `desde` se incluye y `hasta` no; `tamano` de 1 a 100 y
`hasta` posterior a `desde`, o `400 VALIDACION`. Cada fila es
`{ id, jornadaId, jornadaNumero, equipoLocal, equipoVisitante, cancha, inicio, finBloque, estado, accionesPermitidas }`.

`GET /partidos/{id}` agrega `jornada { id, numero, estadoPublicacion }`, `temporada`, `categoria`,
`grupo`, `arbitros [ { id, nombre, funcion } ]` (designaciones vigentes, el central primero; `nombre`
`null` si la sesión no puede ver a la persona), `cedula { id, estado }` o `null`,
`resultado { golesLocal, golesVisitante, penalesLocal, penalesVisitante, porDefault, version }`
(la versión vigente) o `null` y `historial`. `GET /partidos/{id}/historial` devuelve solo el
historial: un arreglo sin paginar, del más antiguo al más reciente, con
`{ estadoAnterior, estadoNuevo, motivo, cambiadoPor { id, nombre }, cambiadoEn }`, como el
historial de un registro (§6.4).

**`accionesPermitidas` (DEC-162).** Subconjunto de `SUSPENDER`, `REPROGRAMAR` y `CANCELAR` según el
estado: `PROGRAMADO` y `SUSPENDIDO` → `["REPROGRAMAR", "CANCELAR"]`; `EN_CURSO` → `["SUSPENDER"]`;
`REPROGRAMADO`, `FINALIZADO` y `CANCELADO` → `[]`. Va vacía si la sesión no tiene
`partidos.partido:CAMBIAR_ESTADO` o si la jornada no está `PUBLICADO`. La interfaz muestra solo esos
botones.

**`POST /partidos/{id}/transiciones`** con
`{ "estadoNuevo": "SUSPENDIDO" | "REPROGRAMADO" | "CANCELADO", "motivo", "inicio"?, "canchaId"? }`.
Responde `200` con el detalle ya actualizado. Orden de errores:

1. Sin `partidos.partido:CAMBIAR_ESTADO`: `403 VAL-015`.
2. Sin `estadoNuevo`; `inicio` o `canchaId` sin reprogramar; reprogramar sin `inicio` o con un
   `inicio` que no es futuro: `400 VALIDACION`. Motivo con menos de 10 o más de 500 caracteres:
   `400 RN-021`.
3. El partido no es de la liga: `404 NO_ENCONTRADO`.
4. El estado no admite la transición (incluye `EN_CURSO` y `FINALIZADO`, que son automáticas, RF-127):
   `409 RF-046` con `estadoActual` y `accionesPermitidas`.
5. La jornada está en `BORRADOR` o `REVISION`: `409 RF-090` (el partido se ajusta con
   `PATCH /partidos/{id}`, §6.7) **[PROPUESTA]**.
6. Reprogramar: cancha que no es de la liga `422 REFERENCIA_INVALIDA`; conflicto con otro partido,
   la cancha o el equipo `409 VAL-003` a `VAL-006` o `RF-100`, con el mismo mensaje de §6.7 (DEC-160).
   Sin `canchaId` se conserva la cancha; `finBloque` lo recalcula el trigger.

Suspender y cancelar solo cambian el estado: no repiten las reglas de programación ni recalculan
`finBloque`, para poder suspender un partido aunque su cancha se acabe de bloquear (DEC-162).

El motivo queda en `partido_estado_historial` con el usuario y la fecha, y en la auditoría
`CAMBIAR_ESTADO_PARTIDO` (M-08), que al reprogramar guarda también el inicio y la cancha anteriores y
nuevos.

```json
{
  "estadoNuevo": "REPROGRAMADO",
  "motivo": "Cancha inundada por la lluvia de la noche",
  "inicio": "2026-11-29T12:00:00-06:00",
  "canchaId": "…"
}
```


#### Captura de la cédula: abrirla, alineación y marcador (H-16.1, RF-025, RF-047, RF-126, DEC-164 a DEC-166)

Los tres exigen su permiso con ámbito `LIGA` (`partidos.cedula:CONSULTAR` para leer y
`partidos.cedula:CAPTURAR` para escribir; en la matriz inicial, el Capturista). La consulta del árbitro
designado (`PROPIO`) llega con H-17 **[PROPUESTA]**. Las respuestas llevan `Cache-Control: no-store`
y la misma cédula:

```json
{
  "id": "…",
  "partido": { "id": "…", "equipoLocal": { "id": "…", "nombre": "Halcones" },
               "equipoVisitante": { "id": "…", "nombre": "Toros" },
               "inicio": "2026-11-28T10:00:00-06:00", "estado": "EN_CURSO" },
  "estado": "EN_CAPTURA",
  "capturista": { "id": "…", "nombre": "Capturista" },
  "marcador": { "golesLocal": 3, "golesVisitante": 0, "penalesLocal": null, "penalesVisitante": null,
                "noPresentado": { "id": "…", "nombre": "Toros" }, "porDefault": true, "valorDemo": true },
  "alineaciones": [
    { "id": "…", "registroJugadorId": "…", "personaId": "…", "nombre": "Juan Pérez López", "equipoId": "…",
      "numeroPlayera": 10, "titular": true, "categoriaSuperior": false }
  ],
  "observaciones": null,
  "creadoEn": "2026-11-28T10:05:00-06:00",
  "actualizadoEn": "2026-11-28T10:20:00-06:00"
}
```

`alineaciones` va con el local primero, los titulares antes y por número de playera; nunca lleva la
CURP. `marcador.porDefault` indica la no presentación y `marcador.valorDemo`, que el marcador por
default sale de parámetros de demostración (la interfaz lo rotula «No oficial»).

- **`GET /partidos/{id}/cedula`.** `404` si el partido no es de la liga o si todavía no tiene cédula.
- **`POST /partidos/{id}/cedula`**, sin cuerpo. Orden: `403 VAL-015` → partido de otra liga `404` →
  el partido no está `EN_CURSO`: `409 RF-046` → ya tiene cédula: `409 DUPLICADO`. Responde `201` con
  `Location: /api/v1/partidos/{id}/cedula`; la sesión queda como capturista y nunca podrá aprobarla
  (VAL-021). Se audita `ABRIR_CEDULA`.
- **`PUT /cedulas/{id}/alineaciones`** con
  `{ "jugadores": [ { "registroJugadorId", "equipoId", "numeroPlayera", "titular" } ] }` reemplaza la
  alineación de los dos equipos (una lista vacía la quita). `titular` vale `true` si se omite. Orden:
  `403` → cédula de otra liga `404` → la cédula no está `EN_CAPTURA`: `409 RF-122` → forma inválida:
  `400 VALIDACION` con `errores` por campo (`jugadores[i].registroJugadorId`, jugador repetido,
  `numeroPlayera` de 0 a 99, hasta 100 jugadores) → algún jugador no elegible: `422 VAL-002` → un
  jugador que sale ya tiene eventos: `422 RF-025`. En el `422 VAL-002` no se guarda nadie y `errores`
  trae un elemento por jugador:

  ```json
  { "campo": "jugadores[1]", "indice": 1, "registroJugadorId": "…", "codigo": "RF-020",
    "mensaje": "«Juan Pérez López» tiene una sanción vigente que le impide jugar." }
  ```

  | `codigo` del jugador | Causa |
  | --- | --- |
  | `RF-025` | El equipo indicado no juega el partido |
  | `REFERENCIA_INVALIDA` | El registro no existe en la liga |
  | `RF-019` | El registro es de otra temporada o no está `ACTIVO` (el mensaje dice su estado) |
  | `RF-020` | Sanción `VIGENTE` de suspensión por partidos o de baja |
  | `VAL-018` | Otro equipo que no es la categoría inmediata superior de su club (RF-110, DEC-12) |

  Quien sigue en la alineación conserva su fila (sus eventos no se pierden). Se audita
  `CAPTURAR_ALINEACION` con el número de jugadores de cada equipo, sin nombres.
- **`PUT /cedulas/{id}/marcador`** con
  `{ "golesLocal", "golesVisitante", "penalesLocal", "penalesVisitante", "noPresentadoEquipoId" }`.
  Orden: `403` → `404` → `409 RF-122` → datos: `400 VALIDACION` (goles obligatorios de 0 a 99 si ambos
  jugaron, penales de los dos o de ninguno, sin goles ni penales con un equipo no presentado) → el
  equipo no presentado no juega el partido: `422 RF-126` → la temporada no tiene parámetros:
  `409 RF-100` → penales sin empate: `422 RF-047` (DEC-28). Con `noPresentadoEquipoId`, el sistema
  asigna `goles_default_ganador` al equipo que sí se presentó y `goles_default_perdedor` al otro, de la
  versión vigente de los parámetros (3-0 **[DEMO]**, DEC-24, DEC-72). Se audita `CAPTURAR_MARCADOR`
  con los goles, el equipo no presentado y `porDefault`.

#### Lo que consulta el Capturista para armar la cédula (H-16.3, RF-019, RF-026, RF-110, DEC-172)

Los dos exigen `partidos.cedula:CAPTURAR` con ámbito `LIGA` (`403 VAL-015`) y una cédula de la liga
(`404`); se consultan en cualquier estado y responden con `Cache-Control: no-store`, sin paginar.

- **`GET /cedulas/{id}/jugadores`**: los registros de los dos equipos y los del mismo club en la
  categoría inmediata inferior, de la temporada del partido, salvo `RECHAZADO` y `BAJA`. Van primero los
  del local, cada equipo antes que su categoría inferior, y por nombre. `elegible` y `causa` salen de la
  misma regla del `PUT /cedulas/{id}/alineaciones` (DEC-165), para que la pantalla muestre deshabilitado
  y con su causa a quien no puede jugar. Nunca lleva la CURP.

  ```json
  [
    { "registroJugadorId": "…", "nombre": "Juan Pérez López", "equipoId": "…",
      "equipoRegistro": { "id": "…", "nombre": "Halcones Segunda" }, "categoriaSuperior": true,
      "elegible": false,
      "causa": { "codigo": "RF-020", "mensaje": "«Juan Pérez López» tiene una sanción vigente que le impide jugar." } }
  ]
  ```

  `equipoId` es el equipo del partido con el que jugaría; `equipoRegistro`, el de su registro.
- **`GET /cedulas/{id}/tipos-incidencia`**: `[ { "id", "nombre", "abreExpediente" } ]`, solo los tipos
  activos de la liga, que son los que admite `POST /cedulas/{id}/incidencias`. El catálogo completo
  sigue en `GET /tipos-incidencia` (§6.9).

Además, cada jugador de `alineaciones` trae `personaId`, con el que `POST /cedulas/{id}/incidencias`
nombra al jugador involucrado.

#### Eventos, incidencias, observaciones y envío (H-16.2, RF-025, RF-026, RF-122, VAL-020, DEC-170, DEC-171)

Los cinco exigen `partidos.cedula:CAPTURAR` con ámbito `LIGA` y siguen el mismo orden de errores:
`403 VAL-015` → cédula de otra liga `404` → la cédula no está `EN_CAPTURA`: `409 RF-122` (en
validación, devuelta o aprobada nada se edita; el trigger `tg_cedula_en_captura` es la última
garantía) → los errores propios de cada uno. Todos, salvo el `DELETE`, responden la cédula completa,
que ahora agrega:

```json
{
  "eventos": [
    { "id": "…", "tipo": "SUSTITUCION", "minuto": 60, "equipoId": "…",
      "jugador": { "id": "…", "nombre": "Juan Pérez López" },
      "entra": { "id": "…", "nombre": "Luis Gómez Ruiz" }, "observaciones": null }
  ],
  "incidencias": [
    { "id": "…", "tipo": { "id": 2, "nombre": "Expulsión", "abreExpediente": true }, "minuto": 75,
      "descripcion": "Agresión a un rival después de la jugada",
      "involucrados": [ { "personaId": null, "equipoId": "…", "nombre": "Toros" },
                        { "personaId": "…", "equipoId": null, "nombre": "Juan Pérez López" } ] }
  ],
  "enviadaEn": null,
  "plazoVenceEn": null
}
```

`jugador.id` y `entra.id` son filas de `alineaciones`. Los eventos y las incidencias van por minuto (sin
minuto, al final).

- **`POST /cedulas/{id}/eventos`** con
  `{ "tipo": "GOL"|"AUTOGOL"|"TARJETA_AMARILLA"|"TARJETA_ROJA"|"SUSTITUCION", "minuto"?, "alineacionId",
  "alineacionEntraId"?, "observaciones"? }`. El equipo es el de la alineación del jugador.
  `400 VALIDACION` sin tipo o jugador, con `minuto` fuera de 0 a 150, `observaciones` de más de 500
  caracteres, una sustitución sin el que entra o con el mismo jugador, o `alineacionEntraId` en otro tipo
  → el jugador (o el que entra) no está en la alineación de esta cédula: `422 REFERENCIA_INVALIDA` → en
  una sustitución, el que entra es de otro equipo: `422 RF-025`. Responde `201` con la cédula y sin
  `Location` (el evento no tiene `GET`). Se audita `REGISTRAR_EVENTO_CEDULA` con tipo, minuto y equipo.
- **`DELETE /cedulas/{id}/eventos/{eventoId}`** quita un evento capturado por error: `404` si no es de
  esta cédula; responde `204`. Se audita `ELIMINAR_EVENTO_CEDULA`.
- **`POST /cedulas/{id}/incidencias`** con
  `{ "tipoIncidenciaId", "minuto"?, "descripcion", "involucrados": [ { "personaId" } | { "equipoId" } ] }`.
  `400 VALIDACION` sin tipo, sin descripción (hasta 1000 caracteres), con `minuto` fuera de rango, sin
  involucrados (hasta 30) o con uno que trae los dos identificadores, ninguno o repetido (`errores` con
  `involucrados[i]`) → el tipo no existe en el catálogo de la liga o está inactivo:
  `422 REFERENCIA_INVALIDA` → la persona no es un jugador alineado en la cédula o el equipo no juega el
  partido: `422 RF-026` **[PROPUESTA]** (DEC-171). Responde `201` con la cédula. Se audita
  `REGISTRAR_INCIDENCIA_CEDULA` con el tipo y el número de involucrados, sin la descripción. Abrir el
  expediente de los tipos que lo piden es de M-09.
- **`PUT /cedulas/{id}/observaciones`** con `{ "observaciones" }`: hasta 2000 caracteres; vacías o
  `null` las quitan. Se audita `CAPTURAR_OBSERVACIONES` sin el texto.
- **`POST /cedulas/{id}/envio`**, sin cuerpo: `EN_CAPTURA → PENDIENTE_VALIDACION`. Exige el marcador y
  la alineación de cada equipo que se presentó (el no presentado no la necesita). Si falta algo,
  `422 VAL-020` con la extensión `faltantes`, en este orden (DEC-170):

  ```json
  {
    "status": 422,
    "codigo": "VAL-020",
    "detail": "La cédula está incompleta y no se puede enviar: Captura el marcador o marca al equipo que no se presentó. Captura la alineación de «Toros».",
    "faltantes": [
      { "campo": "marcador", "mensaje": "Captura el marcador o marca al equipo que no se presentó." },
      { "campo": "alineacionVisitante", "mensaje": "Captura la alineación de «Toros»." }
    ]
  }
  ```

  Completa, responde `200` con `enviadaEn` y `plazoVenceEn` (el plazo del árbitro de la versión vigente
  de los parámetros, 48 h **[DEMO]**, RF-124), deja el cambio en `cedula_estado_historial`, audita
  `ENVIAR_CEDULA` y publica el evento de dominio `CedulaEnviada` para el aviso al árbitro designado
  (H-25).

### 6.9 M-09 Disciplina

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /tipos-incidencia?activo=` · `POST /tipos-incidencia` · `PATCH /tipos-incidencia/{id}` | Catálogo; indica si abre expediente | `disciplina.catalogo:EDITAR` | RF-128 | N |
| `GET /articulos?activo=` · `POST /articulos` · `PATCH /articulos/{id}` | Artículos con sanción, sujeto, efecto y aplicación automática | `disciplina.catalogo:EDITAR` | RF-129, RF-060 | N |
| `GET /expedientes?estado=&temporadaId=` · `GET /expedientes/{id}` | Expediente con incidencia, involucrados, cédula y resoluciones | `disciplina.expediente:CONSULTAR` | RF-057, RF-058 | N |
| `POST /expedientes` | Expediente manual | `disciplina.expediente:RESOLVER` | RF-057 | N |
| `POST /expedientes/{id}/revision` | Iniciar revisión | `disciplina.expediente:RESOLVER` | RF-062 | N |
| `GET /expedientes/{id}/propuesta-sancion?articuloId=` | Sanción sugerida por el artículo (no se guarda) | `disciplina.expediente:RESOLVER` | RF-061 | B |
| `POST /expedientes/{id}/resoluciones` | `{ articuloId, fundamentos, sinSancion, sanciones: [ { tipo, sujeto, personaId\|equipoId\|clubId, partidos, puntos, monto, vigenteDesde, vigenteHasta } ] }` | `disciplina.expediente:RESOLVER` | RF-062 a RF-065 | N |
| `POST /expedientes/{id}/evidencias` | Evidencia (§7) | `disciplina.expediente:RESOLVER` | RF-059 | B |
| `GET /sanciones?vigente=true&sujeto=` | Lista de castigados | `disciplina.expediente:CONSULTAR` | RF-066 | B |
| `POST /sanciones/{id}/publicacion` | Autorizar difusión | `disciplina.sancion:PUBLICAR` | RF-068 | B |
| `POST /expedientes/{id}/recursos` · `POST /recursos/{id}/resolucion` | Recurso de inconformidad | `disciplina.recurso:*` | RF-067 | B |

Los dos catálogos son de la liga y **no se borran**: un elemento que deja de usarse se desactiva con
`PATCH … { "activo": false }` (RN-005). `GET` acepta `?activo=true|false`; sin el parámetro devuelve
también los inactivos. El alta responde `201` con `Location` y la edición `200`.

En los artículos, `SUSPENSION_PARTIDOS` exige `partidosSuspension`, `PERDIDA_PUNTOS` exige `puntos`
y `MULTA` exige `montoMulta`; `PERDIDA_PARTIDO` y `BAJA` no llevan ninguno de esos valores
(DEC-30). Si no se envían, `efecto` nace `HACIA_ADELANTE`, `aplicacionAutomatica` en `false` y
`otorgaDefaultRestantes` en `true` (DEC-31, DEC-32). Los valores numéricos respetan el rango de sus
columnas: `partidosSuspension` y `puntos` admiten de `1` a `32767` (`smallint`) y `montoMulta` de
`0.01` a `9999999999.99` (`numeric(12,2)`); fuera de rango la respuesta es `400 VALIDACION`.

Las resoluciones son inmutables: no hay `PATCH` ni `PUT` sobre `resoluciones` ni `sanciones`
(RF-065). Una corrección es una resolución nueva en instancia de recurso.

### 6.10 M-10 Competencias y fases finales (B)

| Método y ruta | Propósito | Permiso | RF |
| --- | --- | --- | --- |
| `GET /fases` · `POST /fases` · `POST /fases/{id}/llaves` | Liguilla y llaves | `competencias.fase:EDITAR` | RF-076 a RF-081 |
| `POST /temporadas/{id}/propuestas-movimiento` · `POST /propuestas-movimiento/{id}/resolucion` | Ascensos y descensos | `competencias.propuesta:APROBAR` | RF-069 a RF-075 |

### 6.11 M-11 Publicación (interna)

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /comunicados` · `GET /comunicados/{id}` | Consulta para redactar o dictaminar | `publicacion.comunicado:REDACTAR` o `publicacion.comunicado:APROBAR` | RF-090 | B |
| `POST /comunicados` · `PATCH /comunicados/{id}` | Redacción (solo en `BORRADOR`) | `publicacion.comunicado:REDACTAR` | RF-090 | B |
| `POST /comunicados/{id}/envio` · `/resolucion` | Revisión y publicación | `publicacion.comunicado:*` | RF-090 | B |
| `GET /publicaciones/pendientes` | Jornadas y comunicados en `REVISION` para el Presidente | `programacion.jornada:APROBAR` o `publicacion.comunicado:APROBAR` | RF-090, RF-121 | N |

**Comunicado.** `GET /comunicados?estado=&pagina=&tamano=` responde la envoltura de paginación (§4)
con `contenido` ordenado por `creadoEn` descendente. Cada fila trae `id`, `titulo`, `cuerpo`,
`estadoPublicacion`, `redactadoPor`, `aprobadoPor`, `aprobadoEn`, `publicadoEn`, `observaciones`,
`creadoEn` y `actualizadoEn`; los opcionales vacíos se omiten. La consulta la autoriza
`publicacion.comunicado:REDACTAR` o `publicacion.comunicado:APROBAR` con ámbito de liga, porque la
Secretaría redacta y la Presidencia dictamina (el catálogo inicial de M-11 no tiene un permiso de
consulta aparte).

`POST /comunicados` lleva `{ "titulo", "cuerpo" }` (título de hasta 200 caracteres), crea el
comunicado en `BORRADOR` con el perfil de la sesión y responde `201` con `Location`.
`PATCH /comunicados/{id}` cambia solo los campos presentes y únicamente en `BORRADOR`; en otro
estado responde `409 RF-090`. `POST /comunicados/{id}/envio` no lleva cuerpo y pasa
`BORRADOR → REVISION`; `POST /comunicados/{id}/resolucion` lleva
`{ "decision": "PUBLICADO"|"BORRADOR", "observaciones"? }`:

- `PUBLICADO` exige `publicacion.comunicado:APROBAR`, fija `aprobadoPor` y `aprobadoEn`, y el
  trigger fija `publicadoEn` y registra el paso en `historial_publicacion`. Quien redactó no puede
  aprobar: responde `403 RF-090` (la restricción `ck_comunicados_dual` es la última garantía).
- `BORRADOR` devuelve el comunicado a la Secretaría; exige observaciones de al menos 10 caracteres,
  las guarda y las usa como motivo del historial. Sin ellas responde `400 RF-090`.

En ambos casos, si el comunicado no está en `REVISION` la acción responde `409 RF-090`. Las altas,
ediciones y transiciones se auditan como `REDACTAR_COMUNICADO`, `EDITAR_COMUNICADO`,
`ENVIAR_COMUNICADO_A_REVISION`, `APROBAR_COMUNICADO` y `DEVOLVER_COMUNICADO`.

**Pendientes (H-13.1).** `GET /publicaciones/pendientes?pagina=&tamano=` responde la envoltura de
paginación (§4) con las jornadas y los comunicados en `REVISION`, del envío más antiguo al más
reciente. Cada fila trae `tipo` (`JORNADA`|`COMUNICADO`), `id`, `titulo` («Jornada 3» o el título
del comunicado), `contexto` (temporada · categoría · grupo, solo en jornadas), `partidos` (no
cancelados, solo en jornadas), `enviadoPor: { id, nombre }` y `enviadoEn` (en el comunicado, su último
paso a `REVISION` del historial). Las jornadas se incluyen con `programacion.jornada:APROBAR` y los
comunicados con `publicacion.comunicado:APROBAR`, ambos con ámbito de liga; con uno solo, la bandeja
trae solo ese tipo. Sin ninguno responde `403 VAL-015`; con alguno sin ámbito de liga, `404`
(VAL-024). La consulta no se audita.

### 6.12 M-12 Reportes (B)

| Método y ruta | Propósito | Permiso | RF |
| --- | --- | --- | --- |
| `GET /reportes/{rep}` | `rep` = `programacion`, `resultados`, `tabla`, `castigados`, … (REP-001 a REP-014) con sus filtros | `reportes.reporte:CONSULTAR` | RF-091 |
| `GET /reportes/{rep}` con `Accept: application/pdf` o `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | Exportación | `reportes.reporte:EXPORTAR` | RF-092 |

Las consultas de REP-001, REP-002, REP-003, REP-010 y REP-012 son N por medio de los listados de
los módulos; los endpoints `/reportes` y la exportación son B.

### 6.13 M-13 Tesorería y cobros

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /tarifas/contexto` · `GET /tarifas?temporadaId=` · `POST /tarifas` · `PATCH /tarifas/{id}` | Tarifas por temporada y categoría | `tesoreria.tarifa:EDITAR` | RF-135 | B |
| `GET /cobros?clubId=&equipoId=&estado=` · `POST /cobros` | Cobros (manuales en N; los automáticos los genera la publicación de la jornada, B) | `tesoreria.cobro:*` | RF-137, RF-136 | N · B |
| `GET /adeudos?clubId=` | Suma de cobros pendientes y vencidos por club y equipo | `tesoreria.cobro:CONSULTAR` | RF-140 | N |
| `POST /cobros/{id}/intentos-pago` · `POST /intentos-pago/{id}/confirmacion` | Pago en línea simulado | `tesoreria.pago:PAGAR` | RF-138, ADR-0012 | N |
| `POST /cobros/{id}/pagos` | Pago externo con comprobante | `tesoreria.pago:REGISTRAR` | RF-139 | N |
| `GET /cobros/{id}/pagos` | Pagos e intentos del cobro | `tesoreria.cobro:CONSULTAR` | RF-140 | N |

La pantalla de pago simulado muestra siempre «Pago simulado: no se realiza ningún cargo». La API
**no acepta** datos de tarjeta en ningún campo.

Ningún endpoint pone un cobro en `VENCIDO`: lo hace la tarea programada de vencimiento (B-31.1,
RF-141, ADR-0009) como el perfil Sistema, para los cobros `PENDIENTE` con `fecha_limite` anterior al
día de hoy en `America/Mexico_City` **[PROPUESTA, DEC-60]**, con la acción de auditoría
`VENCER_COBRO`. Desde ahí el cobro solo admite el pago externo (`POST /cobros/{id}/pagos`).

#### Tarifas (B-29.1, RF-135)

Las tarifas reales están pendientes (PEN-042): todo monto capturado es **[DEMO]** y cada tarifa
lleva `esDemo: true` para que la interfaz la rotule «No oficial». Los tres endpoints exigen
`tesoreria.tarifa:EDITAR` en ámbito de liga (la matriz no tiene un permiso de consulta propio).

`GET /tarifas?temporadaId=` devuelve la lista **sin paginar** de la temporada (a lo sumo dos tarifas
por categoría), ordenada por el orden del catálogo de categorías y después por concepto. Cada tarifa
se representa con `id`, `temporada` y `categoria` anidados (`{ id, nombre }`), `concepto`
(`ARBITRAJE` o `CANCHA`), `monto` (`BigDecimal`, dos decimales) y `esDemo`. Sin `temporadaId`
responde `400 VALIDACION`; una temporada que no es de la liga, `404`.

`GET /tarifas/contexto` alimenta la pestaña «Tarifas» de F-33 (B-29.2): devuelve
`{ "temporadas": [{ "id", "nombre", "estado", "categorias": [{ "id", "nombre" }] }] }` con las
temporadas de la liga de la más reciente a la más antigua y, en cada una, las categorías asignadas en
el orden del catálogo (una temporada sin categorías trae la lista vacía). Se autoriza con
`tesoreria.tarifa:EDITAR` y no con `temporadas.temporada:CONSULTAR`, que el Tesorero no tiene en la
matriz inicial; solo lleva identificadores, nombres y estado.

`POST /tarifas` responde `201` con `Location` y la tarifa. El cuerpo es:

```json
{ "temporadaId": "…", "categoriaId": "…", "concepto": "ARBITRAJE", "monto": 350.00 }
```

`PATCH /tarifas/{id}` solo cambia el monto (`{ "monto": 420.50 }`) y responde `200` con la tarifa;
la temporada, la categoría y el concepto identifican la tarifa y no se editan. Un monto igual al
vigente no cambia nada ni se audita.

- `monto` es obligatorio, mayor o igual a cero, con hasta diez dígitos enteros y dos decimales
  (`numeric(12,2)`); un negativo o un tercer decimal responden `400 VALIDACION`.
- `409 DUPLICADO` si ya existe la tarifa de ese concepto para la categoría en la temporada (la
  restricción `UNIQUE` es la última garantía); `422 REFERENCIA_INVALIDA` si la temporada no existe o
  la categoría no está asignada a ella (`categorias_temporada`); `409 RF-003` si la temporada está
  `CERRADA`: sus tarifas son de solo lectura. `PATCH` de una tarifa inexistente responde `404`.
- Auditoría: `REGISTRAR_TARIFA` y `EDITAR_TARIFA` en `M-13`, entidad `tarifas`, con temporada,
  categoría, concepto, monto (y `montoAnterior` en la edición) y `esDemo`.

#### Cobros (H-29.1, RF-137, RF-140)

`GET /cobros` lista los cobros visibles con la envoltura de paginación de §4:

| Parámetro | Tipo | Descripción |
| --- | --- | --- |
| `clubId` | uuid | Club; opcional. |
| `equipoId` | uuid | Equipo; opcional. |
| `tipo` | enum `tipo_cobro` | `ARBITRAJE`, `CANCHA`, `MULTA` o `MANUAL`; opcional. |
| `estado` | lista | Uno o varios `estado_cobro` separados por coma (`PENDIENTE,VENCIDO`); sin él, todos. |
| `desde` / `hasta` | fecha | Filtran por `fechaLimite`: `desde` incluido y `hasta` excluido; opcionales. |
| `pagina` / `tamano` | entero | Página desde 0 y tamaño de 1 a 100 (predeterminados 0 y 20). |
| `orden` | texto | `creadoEn` (predeterminado), `fechaLimite` o `monto`, con `asc` o `desc`; el desempate es `id`. |

Cada cobro se representa con `id`, `tipo`, `concepto`, `monto` (`BigDecimal`, dos decimales),
`periodoInicio`, `fechaLimite`, `estado`, `club` y `equipo` anidados (`{ id, nombre }`; `equipo` se
omite en un cobro al club) y `creadoEn`. El `totalElementos` cuenta solo las filas del ámbito, con
la misma condición que el `WHERE` (ADR-0007).

`POST /cobros` crea un cobro **manual** y siempre `PENDIENTE`; responde `201` con `Location` y el
cobro. El cuerpo es:

```json
{
  "clubId": "…",
  "equipoId": "…",
  "concepto": "Cuota de inscripción",
  "monto": 1500.00,
  "periodoInicio": "2026-11-05",
  "fechaLimite": "2026-11-20"
}
```

- `clubId` es obligatorio; `equipoId` es opcional y, si se indica, debe pertenecer a ese club.
- `concepto` va de 1 a 200 caracteres; `monto` es mayor que cero, con hasta diez dígitos enteros
  y dos decimales (`numeric(12,2)`): tres decimales se rechazan aunque sean ceros (`1500.000` da
  `400`, como `10.123`).
- `periodoInicio` es opcional (se usa el día de negocio en `America/Mexico_City`); `fechaLimite` es
  obligatoria y no puede ser anterior al inicio.
- No se aceptan `tipo`, `estado`, `partidoId` ni `sancionId`: los cobros automáticos los generan
  sus flujos (RF-136 al publicar la jornada, B-30; RF-133 por multa, backlog).
- Errores: `404` si el club no existe o está fuera del ámbito (VAL-024); `403 VAL-015` sin
  `tesoreria.cobro:CREAR`; `422 REFERENCIA_INVALIDA` si el equipo no existe o es de otro club (el
  mensaje no revela el nombre del equipo, que puede estar fuera del ámbito); `400 VALIDACION` si el
  concepto, el monto o las fechas no cumplen.
- Auditoría: `REGISTRAR_COBRO` en `M-13`, entidad `cobros`, con club, equipo, tipo, monto, periodo,
  fecha límite y estado; el detalle no lleva concepto ni datos personales.

#### Cobros automáticos al publicar la jornada (B-30, RF-136)

No hay endpoint: los genera `POST /jornadas/{id}/resolucion` con `decision: PUBLICADO` (§6.7). La
jornada emite `JornadaPublicada` y el oyente síncrono de `tesoreria` corre en la misma transacción
(ADR-0006): si falla, la publicación se revierte. La respuesta del dictamen no cambia.

- Por cada partido de la jornada que no esté `CANCELADO`, a **cada** equipo: un cobro `ARBITRAJE` y
  uno `CANCHA` cargados a su club, con `partidoId`, `equipoId` y el monto de la tarifa de la temporada
  y la categoría (§ «Tarifas»). **[PROPUESTA]** Cada equipo paga la tarifa completa.
- **Sin cobro de cancha** al equipo cuyo club es dueño de la cancha (`propietario = CLUB`, DEC-33);
  en una cancha de la liga o de un tercero pagan los dos.
- **[PROPUESTA]** Sin tarifa del concepto, o con tarifa en cero, ese cobro se omite y la omisión queda
  en la auditoría; la publicación no se bloquea.
- **[PROPUESTA]** Periodo: desde el día de la publicación hasta el día del partido (o hoy, si ya pasó)
  más `siglim.tesoreria.cobros-automaticos.dias-para-pagar` días (7 por defecto), en
  `America/Mexico_City`. Después, la tarea de vencimiento lo vence (RF-141).
- Concepto que ve el club: `Arbitraje · Jornada 3 · Halcones vs Toros` (hasta 200 caracteres).
- Idempotente: `ux_cobros_partido` impide un segundo cobro del mismo partido, equipo y tipo.
- Auditoría en `M-13`: `GENERAR_COBRO` por cobro (entidad `cobros`, con jornada, partido, club,
  equipo, tipo, monto, periodo y estado) y `GENERAR_COBROS_JORNADA` sobre la jornada con
  `{ partidos, cobrosGenerados, omitidos }`; sin concepto ni datos personales. `creadoPor` es el
  Presidente que publicó.

#### Adeudos (H-29.1, RF-140)

`GET /adeudos` suma los cobros `PENDIENTE` y `VENCIDO` visibles, por club y por equipo; `PAGADO` y
`RECHAZADO` no son adeudo. El filtro `clubId` es opcional y no cambia el ámbito: un club ajeno
simplemente no aparece.

```json
{
  "total": 825.50,
  "pendiente": 300.00,
  "vencido": 525.50,
  "cobros": 5,
  "clubes": [
    {
      "club": { "id": "…", "nombre": "Halcones" },
      "total": 825.50,
      "pendiente": 300.00,
      "vencido": 525.50,
      "cobros": 5,
      "equipos": [
        {
          "equipo": { "id": "…", "nombre": "Halcones A" },
          "total": 250.00,
          "pendiente": 200.00,
          "vencido": 50.00,
          "cobros": 2
        }
      ]
    }
  ]
}
```

- Un cobro cargado al club sin equipo suma en el club y no en un equipo; `equipos` solo lista los
  equipos con adeudo. Un club sin adeudo no aparece.
- `total = pendiente + vencido`; los montos son `BigDecimal` con dos decimales. SQL suma por club y
  equipo sobre `numeric`; el servicio acumula los totales generales.
- La Directiva de club solo recibe su club; el Tesorero, toda la liga (ADR-0007).
- Sin adeudos responde `200` con ceros y `clubes: []`.

#### Pago en línea simulado (H-30.1, RF-138)

El pago es **simulado**: no hay cargo real, no interviene una pasarela externa y la API **no recibe
ni guarda datos de tarjeta**. `POST /cobros/{id}/intentos-pago` inicia un intento y responde `201`
con `Location: /api/v1/intentos-pago/{id}`:

```json
{
  "id": "…",
  "referencia": "SIM-20261106-4K7Q2M",
  "estado": "PENDIENTE",
  "iniciadoEn": "2026-11-06T12:00:00-06:00"
}
```

- La `referencia` la genera el backend con el formato `SIM-AAAAMMDD-XXXXXX`: la fecha es el día de
  negocio en `America/Mexico_City` y el sufijo son seis caracteres alfanuméricos aleatorios. Es
  única; el cliente no la envía ni la elige.
- Solo un cobro `PENDIENTE` inicia un intento. Un cobro `RECHAZADO` vuelve a `PENDIENTE` y permite
  reintentar. Si ya existe un intento `PENDIENTE` para el cobro, se devuelve ese mismo sin crear
  otro: un doble clic no duplica el intento. Un cobro `PAGADO` o `VENCIDO` responde
  `409 VAL-028`.
- El intento no cambia el estado del cobro: sigue `PENDIENTE` hasta la confirmación.

`POST /intentos-pago/{id}/confirmacion` recibe el resultado que eligió la persona:

```json
{ "resultado": "APROBADO" }
```

`resultado` es obligatorio y solo admite `APROBADO` o `RECHAZADO`. Responde `200` con el intento
resuelto:

```json
{
  "id": "…",
  "referencia": "SIM-20261106-4K7Q2M",
  "estado": "APROBADO",
  "iniciadoEn": "2026-11-06T12:00:00-06:00",
  "resueltoEn": "2026-11-06T12:02:00-06:00"
}
```

- `APROBADO` crea un pago con medio `EN_LINEA_SIMULADO`, el monto del cobro, la referencia del
  intento y el usuario que confirma; el cobro queda `PAGADO`. `RECHAZADO` deja el intento y el cobro
  en `RECHAZADO` y **no** crea pago.
- La confirmación es **atómica** (RNF-004): bloquea primero el cobro y después el intento, y resuelve
  el intento con `WHERE estado = 'PENDIENTE'`. Si otra persona confirmó antes, responde
  `409 CONCURRENCIA` y no duplica el pago. Un intento ya resuelto o un cobro que dejó de estar
  `PENDIENTE` responden `409 VAL-028`.
- Errores:
  - `400 VALIDACION` si falta `resultado` o no es un valor conocido.
  - `401 NO_AUTENTICADO` sin sesión; `403 VAL-015` sin `tesoreria.pago:PAGAR`.
  - `404 NO_ENCONTRADO` si el cobro o el intento no existe o está fuera del ámbito del club
    (VAL-024).
  - `409 VAL-028` si el cobro está `PAGADO` o `VENCIDO`, si el intento ya se resolvió o si el cobro
    dejó de estar `PENDIENTE`; el mensaje indica el estado.
  - `409 CONCURRENCIA` si otra confirmación ganó la carrera.
- Auditoría: `INICIAR_PAGO_SIMULADO` y `CONFIRMAR_PAGO_SIMULADO` en `M-13`, entidad
  `intentos_pago`, con `cobroId`, `referencia`, estado anterior y nuevo; la confirmación agrega
  `resultado`, `estadoCobro`, `monto` y, al aprobar, `pagoId`. El detalle no lleva datos personales
  ni de tarjeta.
- Al aprobar se publica `CobroPagado` dentro de la misma transacción (ADR-0012); los oyentes ven el
  cobro ya `PAGADO` y, si fallan, se revierte la confirmación completa.

#### Pagos externos (H-31.1, RF-139)

`POST /cobros/{id}/pagos` registra un pago recibido fuera del sistema, con su comprobante, en una
sola petición `multipart/form-data` (DEC-56). El cobro pasa de `PENDIENTE` o `VENCIDO` a `PAGADO`.

| Parte | Tipo | Descripción |
| --- | --- | --- |
| `archivo` | archivo | Comprobante; obligatorio. JPEG, PNG, WebP o PDF por firma binaria; hasta 5 MB **[PROPUESTA]** (§7). |
| `medio` | enum `medio_pago` | `EFECTIVO`, `TRANSFERENCIA` u `OTRO`; obligatorio. `EN_LINEA_SIMULADO` se rechaza con `400`: solo lo produce el pago simulado. |
| `monto` | decimal | Obligatorio; igual al monto del cobro, con hasta dos decimales (no hay pagos parciales). |
| `fechaPago` | fecha | Opcional; no puede ser posterior a hoy en `America/Mexico_City`. Sin ella, `pagadoEn` es el momento del registro; con ella, el inicio de ese día en esa zona. |
| `referencia` | texto | Opcional, hasta 60 caracteres; se recortan los espacios y una vacía se guarda nula. |

Responde `201` con `Location: /api/v1/cobros/{id}/pagos` y el pago:

```json
{
  "id": "…",
  "cobroId": "…",
  "medio": "TRANSFERENCIA",
  "monto": 1500.00,
  "referencia": "SPEI-123",
  "pagadoEn": "2026-11-05T00:00:00-06:00",
  "registradoPor": { "id": "…", "nombre": "Tesorería" },
  "comprobante": { "documentoId": "…", "tipoMime": "application/pdf", "tamanoBytes": 48213 }
}
```

`GET /cobros/{id}/pagos` (`tesoreria.cobro:CONSULTAR`, mismo ámbito) responde
`{ "pagos": [ … ], "intentos": [ … ] }` en orden cronológico. Cada intento de pago simulado lleva
`id`, `referencia`, `estado` (`PENDIENTE`, `APROBADO` o `RECHAZADO`), `iniciadoEn` y `resueltoEn`
(se omite si sigue pendiente). `registradoPor.nombre` se omite cuando el perfil no es visible para
quien consulta (la Directiva de club).

- Las respuestas **nunca** incluyen `s3Bucket`, `s3Clave` ni `url` (§7). El enlace temporal para ver
  el comprobante queda **pendiente** para el Sprint 3: `GET /documentos/{id}/enlace` exige
  `registros.documento:CONSULTAR`, que el Tesorero no tiene (DEC-56).
- Orden de validación: formato de la petición (`400`, en el controlador: partes obligatorias, tipos,
  decimales y longitud) → cobro (`404`) → permiso (`403`) → reglas de los campos (`400`: medio, archivo
  vacío, fecha futura) → estado (`409`) → monto (`422`) → subida a S3. El archivo se sube solo si
  todo lo anterior pasa. El `400` de formato no revela si el cobro existe.
- Errores:
  - `400 VALIDACION`: falta el archivo o está vacío, falta el medio o el monto, monto con más de dos
    decimales, medio `EN_LINEA_SIMULADO` o desconocido, fecha futura o referencia de más de 60
    caracteres.
  - `401 NO_AUTENTICADO` sin sesión; `403 VAL-015` sin `tesoreria.pago:REGISTRAR`.
  - `404 NO_ENCONTRADO` si el cobro no existe o está fuera del ámbito (VAL-024).
  - `409 VAL-028` si el cobro no está `PENDIENTE` ni `VENCIDO`; el mensaje indica su estado. Si dos
    pagos llegan a la vez, el trigger `tg_pagos_validar` rechaza el segundo con el mismo código.
  - `413 ARCHIVO_DEMASIADO_GRANDE` si el archivo supera el límite; la interfaz debe validar el tamaño
    antes de enviar, porque con un archivo muy grande el servidor puede cerrar la conexión antes de
    que el cliente lea la respuesta.
  - `415 TIPO_ARCHIVO_NO_PERMITIDO` si la firma binaria no es de un tipo permitido, y
    `415 FORMATO_NO_SOPORTADO` si la petición no es `multipart/form-data`.
  - `422 RF-139` si el monto no es el del cobro: «El pago debe cubrir el monto total del cobro
    (1500.00 MXN).».
  - `503 SERVICIO_NO_DISPONIBLE` si S3 falla; no se escribe nada en la base.
- Auditoría: `REGISTRAR_PAGO` en `M-13`, entidad `pagos`, con `cobroId`, `medio`, `monto`,
  `pagadoEn`, `estadoAnterior`, `documentoId`, `tipoMime` y `tamanoBytes`; sin URL, bucket, clave,
  nombre del archivo ni referencia.

### 6.14 M-14 Notificaciones

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /notificaciones?leida=false` | Avisos del usuario, más recientes primero; la cabecera `X-No-Leidas` trae el conteo | `notificaciones.aviso:CONSULTAR` | RF-142 | N |
| `POST /notificaciones/{id}/lectura` · `POST /notificaciones/lectura` | Marcar uno o todos como leídos | `notificaciones.aviso:CONSULTAR` | RF-142 | N |

Sin tiempo real: la interfaz consulta al cargar y cada 60 s **[PROPUESTA]** mientras la pestaña está
visible. El campo `enlace` es una ruta interna del frontend (`/app/partidos/{id}`).

**Consulta (H-25.1).** `GET /notificaciones` admite `leida` (`true`, `false` o sin filtro), `pagina` y
`tamano` (§4; sin `orden`: siempre `creadaEn` descendente). Cada aviso trae `id`, `evento`, `titulo`,
`cuerpo`, `enlace`, `creadaEn` y `leidaEn` (`null` si no se ha leído). La cabecera `X-No-Leidas`
cuenta todos los avisos sin leer del usuario, sin importar el filtro, y CORS la expone para que la
campana la lea. Solo se ven los avisos propios: el permiso con cualquier ámbito basta y el SQL filtra
por el perfil de la sesión (RLS lo repite). Sin el permiso, `403 VAL-015`.

**Lectura.** `POST /notificaciones/{id}/lectura` responde `200` con el aviso; repetirlo conserva la
primera `leidaEn`. Un aviso de otro usuario o inexistente responde `404`. `POST /notificaciones/lectura`
marca todos los no leídos y responde `200` con `{ "marcadas": n }`. Leer no se audita **[PROPUESTA]**:
no cambia ningún trámite y la operación que originó el aviso ya quedó auditada.

**Eventos y destinatarios.** Los avisos se escriben en la transacción del evento (ADR-0010); los
destinatarios se eligen por permiso, nunca por nombre de rol, y deben tener además
`notificaciones.aviso:CONSULTAR`. Sin correo mientras RF-143 siga en el backlog.

| `evento` | Evento de dominio | Destinatarios | `enlace` |
| --- | --- | --- | --- |
| `CEDULA_PENDIENTE` | `CedulaEnviada` (RF-122) | Perfil activo de la persona del árbitro **central** vigente con `partidos.cedula:APROBAR` | `/app/partidos/{partidoId}` |
| `COBRO_VENCIDO` | `CobroVencido` (RF-141) | Perfiles con `tesoreria.cobro:CONSULTAR` en ámbito `CLUB` del club deudor | `/app/tesoreria` |
| `PROGRAMACION_PUBLICADA` | `JornadaPublicada` (RF-121) | Perfiles con `programacion.jornada:CONSULTAR` en ámbito `CLUB` de los clubes con partidos no cancelados en la jornada | `/app/programacion/jornadas` |

**[PROPUESTA]** Los usuarios de ámbito `LIGA` no reciben los avisos de cada club. Los demás eventos de
RF-142 (cédula devuelta o con plazo vencido, expediente abierto, trámite por aprobar y cobro generado)
se agregan cuando su módulo publique el evento de dominio.

## 7. Documentos y archivos (AWS S3)

Aplica a documentos de registro y de tutor (RF-108), comprobantes de afiliación y de pago (RF-139) y
evidencias (RF-059). Decisión completa en ADR-0013.

| Método y ruta | Propósito | Permiso |
| --- | --- | --- |
| `POST /registros-jugador/{id}/documentos` | Cargar (`multipart/form-data`: `archivo` + `tipoDocumentoId`); `201` sin `Location` (H-10.1, §6.4) | `registros.documento:CREAR` |
| `POST /personas/{id}/documentos` · `/afiliaciones/{id}/documentos` | Cargar documentos de una persona (renovación, RF-015) o de una afiliación (B) | `registros.documento:CREAR` |
| `GET /registros-jugador/{id}/documentos` | Historial con los reemplazados; sigue fuera porque H-10.3 no lo usa: los vigentes vienen en `GET /registros-jugador/{id}/requisitos` (DEC-96, DEC-112) | `registros.documento:CONSULTAR` |
| `GET /documentos/{id}/enlace` | URL prefirmada de lectura | `registros.documento:CONSULTAR` |
| `POST /documentos/{id}/validacion` | `{ "decision": "APROBADO"\|"RECHAZADO", "observaciones" }` | `registros.documento:VALIDAR` |
| `POST /documentos/{id}/reemplazo` | Carga un documento nuevo (`multipart/form-data`: `archivo`) en lugar de uno vigente `PENDIENTE` o `RECHAZADO`; el anterior conserva su estado y queda reemplazado. `201` sin `Location` | `registros.documento:CREAR` |

- Tipos aceptados por **firma binaria**: JPEG, PNG, WebP y PDF. Tamaño máximo 5 MB **[PROPUESTA]**.
- `GET /documentos/{id}/enlace` responde `{ "url": "https://…", "expiraEn": "…" }` con vigencia de
  60 s **[PROPUESTA]**. La respuesta lleva `Cache-Control: no-store`; la interfaz abre la URL de
  inmediato y nunca la guarda.
- Las respuestas **nunca** incluyen `s3Bucket`, `s3Clave`, `sha256` ni la `url` guardada en la base.
- El reemplazo solo procede sobre un documento vigente `PENDIENTE` o `RECHAZADO` (`409 RF-109` si
  está `APROBADO` o ya fue reemplazado); no hay número de versión ni se borra nada en S3. Responde
  `201` sin `Location` porque no existe `GET /documentos/{id}`.
- `POST /personas/{id}/documentos` (carpeta `tutores`) queda para la renovación (entrega B): en
  H-10.1 el INE del tutor es un documento del registro (DEC-92).

## 8. Portal público (`/api/v1/publico`)

Solo `GET`, sin sesión y sin datos personales. El backend consulta con el rol `siglim_publico`, que
solo ve las vistas `v_publica_*` y la función `calcular_tabla` (ADR-0014). Solo aparece lo que está
publicado (RF-056, VAL-014).

| Endpoint | Fuente | Filtros | RF | E |
| --- | --- | --- | --- | --- |
| `GET /publico/temporada` | Temporada activa con sus categorías y grupos | — | RF-089 | N |
| `GET /publico/programacion` | `v_publica_programacion` | `desde`, `hasta` (por defecto la semana actual), `categoriaId`, `grupoId`, `equipoId` | RF-082, RF-089 | N |
| `GET /publico/resultados` | `v_publica_resultados` | los mismos | RF-083, RF-089 | N |
| `GET /publico/grupos/{id}/tabla` | `calcular_tabla` | — | RF-084 | N |
| `GET /publico/calendario` | `v_publica_programacion` agrupado por jornada | `categoriaId`, `grupoId` | RF-085 | B |
| `GET /publico/comunicados` | `v_publica_comunicados` | paginación | RF-086 | B |
| `GET /publico/sanciones` | `v_publica_sanciones` | `categoriaId`, `vigente` | RF-087 | B |
| `GET /publico/equipos` | `v_publica_equipos` | `categoriaId`, `grupoId`, `q` | RF-088 | B |

- Respuestas con `Cache-Control: public, max-age=60` **[PROPUESTA]** (RNF-002).
- Un jugador solo aparece con nombre si consintió la difusión; si no, «Jugador (dato reservado)»
  (RF-145, RNF-012).
- Los resultados incluyen `porDefault` y `version`; la tabla incluye `"parametrosDemo": true`
  mientras los puntos o desempates sean **[DEMO]**, para el rótulo «no oficial».

## 9. Representación de datos

### 9.1 Referencias anidadas

Las respuestas incluyen el identificador y el nombre de las entidades relacionadas para que la
interfaz no haga una petición por fila:

```json
{
  "id": "…",
  "estado": "PROGRAMADO",
  "inicio": "2026-10-25T10:00:00-06:00",
  "equipoLocal": { "id": "…", "nombre": "Halcones" },
  "equipoVisitante": { "id": "…", "nombre": "Toros" },
  "cancha": { "id": "…", "nombre": "Unidad Deportiva Morelos 2" },
  "accionesPermitidas": ["SUSPENDER", "REPROGRAMAR", "CANCELAR"]
}
```

`accionesPermitidas` combina el estado (RF-046) y los permisos del usuario; la interfaz muestra
solo esos botones (ERS §3.1.1).

### 9.2 Datos personales

- La CURP solo se devuelve completa a quien tiene `organizacion.persona:CONSULTAR` en el ámbito de
  la persona; en listados se enmascara (`GAPA******HMN***05`) **[PROPUESTA]**: se conservan los
  caracteres 1–4, 11–13 y 17–18 y se cambian 5–10 y 14–16 por `*`; un valor de otra longitud conserva
  sus 4 primeros caracteres. DEC-110 lo aplica en M-04: `GET /registros-jugador` solo lleva
  `curpEnmascarada` y `GET /registros-jugador/{id}` lleva `curp` completa solo con
  `organizacion.persona:CONSULTAR` sobre el club del registro.
- Fecha de nacimiento, teléfono y correo solo en el detalle de la persona, nunca en listados.

### 9.3 Textos de los enums

El frontend mantiene un diccionario único `enum → texto` (por ejemplo, `PENDIENTE_VALIDACION` →
«Pendiente de validación», `EN_CURSO` → «En curso»). La API no envía textos traducidos de enums.

## 10. Documentación del contrato y pruebas

- Cada controlador se documenta con OpenAPI 3.1 (`springdoc-openapi`) cuando se agregue la
  dependencia; la especificación generada debe coincidir con este documento.
- Cada endpoint tiene, como mínimo, pruebas de: caso feliz, `401`, `403` (sin permiso), `404`
  (fuera de ámbito) y el error de la regla principal con su `codigo` (RNF-003).
  Las de `401`, `403` y `404` se generan con el arnés `ArnesAutorizacionEndpoint` (H-02.3,
  `backend/src/test/java/.../seguridad/`) desde un `@TestFactory` de la prueba de controlador; ver
  el ejemplo en `backend/AGENTS.md` (Pruebas).
- Los nombres de las pruebas citan el ID del ERS: `VAL-004: rechaza cancha ocupada`.

## 11. Pendientes

| Tema | Referencia |
| --- | --- |
| Duración y renovación de la sesión, política de contraseñas, intentos | PEN-030 |
| Quitar una categoría de la temporada y borrar un grupo en configuración: `siglim_app` no tiene `DELETE` sobre `categorias_temporada` ni `grupos`, así que requiere una migración y una decisión del equipo | H-05.1 (D7) |
| Confirmar como oficiales solo una parte de los parámetros: `es_demo` es una sola columna por versión, así que marcarlos campo por campo requiere una migración | H-05.2 (D2), PEN-021 |
| Valores **[PROPUESTA]** de este documento: longitud mínima del motivo, límites de tamaño, vigencia de la URL prefirmada, caché del portal, intervalo de consulta de avisos, enmascarado de CURP | Validar con el equipo y el profesor |
