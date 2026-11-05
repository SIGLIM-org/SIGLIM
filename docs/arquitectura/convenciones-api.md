# Convenciones de la API REST

**SIGLIM · Sistema de Gestión de la Liga Municipal de Futbol Amateur Morelia**  
Versión 3.0 · 5 de noviembre de 2026 · Alineado con el [ERS Rev. 0.4.1](../requisitos/ERS.md), la
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
| Registro de jugador | Validar / rechazar | `POST /registros-jugador/{id}/validacion` | `{ "decision": "ACTIVO"\|"RECHAZADO", "observaciones" }` | `PENDIENTE_VALIDACION → …` | `registros.registro:VALIDAR` | RF-109 |
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
| `403` | Separación de funciones de la persona: aprobar la cédula que capturó, cambiar permisos de su propio rol, corregir un resultado sin resolución | `VAL-021`, `VAL-022`, `VAL-010` |
| `404` | El recurso no existe **o** está fuera del ámbito del usuario (no se revela cuál de los dos) | `NO_ENCONTRADO` (VAL-024) |
| `405` | El recurso existe pero no admite ese método (la respuesta incluye la cabecera `Allow`) | `METODO_NO_PERMITIDO` |
| `406`, `415` | La petición no envía o no acepta JSON (`Content-Type`, `Accept`) | `FORMATO_NO_SOPORTADO` |
| `409` | Conflicto con el estado actual de otros datos: programación, unicidad, estado que no admite la acción, cobro ya pagado | `VAL-003` a `VAL-006`, `VAL-008`, `VAL-017`, `VAL-026`, `VAL-028`, `RF-046`, `RN-021`… |
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
| `403` | `VAL-021`, `VAL-022` | La persona no puede hacer esa acción aunque tenga el permiso |
| `409` | `VAL-003`, `VAL-005`, `VAL-017`, `VAL-026`, `VAL-028`; `RF-001`, `RF-017`, `RF-039`, `RF-046`, `RF-048`, `RF-062`, `RF-090`, `RF-100`, `RF-113`, `RF-121`, `RF-122`, `RF-123`, `RF-124`, `RF-127`; `RN-024`, `RN-039`; `RNF-015`, `RNF-018` | Transición o cambio no permitido por el estado actual, o choque con otros datos |
| `422` | `VAL-018`, `VAL-019`, `VAL-020`, `VAL-025`; `RF-013`, `RF-019`, `RF-020`, `RF-025`, `RF-033`; `RF-139` (pago que no cubre el monto) | El contenido de la petición incumple una regla |

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
| `GET /avisos-privacidad/vigente` | Aviso de privacidad vigente | sesión | RF-097 | N |
| `POST /personas/{id}/consentimientos` | Registrar consentimiento del titular o del tutor | `seguridad.consentimiento:REGISTRAR` | RF-097, VAL-025 | N |
| `GET /solicitudes-arco` · `POST /solicitudes-arco` · `PATCH …/{id}` | Solicitudes ARCO | `seguridad.arco:GESTIONAR` | RF-098 | B |
| `GET /auditoria?perfilId=&modulo=&accion=&desde=&hasta=` | Consultar auditoría. `tamano` por defecto 50. `orden`: `ocurridoEn` | `auditoria.registro:CONSULTAR` | RF-134 | N |
| `GET /auditoria/opciones-filtro` | Perfiles, módulos y acciones presentes en la auditoría de la liga, para llenar los filtros; sin paginación | `auditoria.registro:CONSULTAR` | RF-134 | N |

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

**Usuarios, invitación y asignaciones (H-03.1, RF-094).** Los permisos son
`seguridad.usuario:CONSULTAR` para `GET /usuarios` y `GET /usuarios/{id}`,
`seguridad.usuario:CREAR` para `POST /usuarios` y `seguridad.usuario:EDITAR` para
`PATCH /usuarios/{id}`, `POST /usuarios/{id}/asignaciones` y
`POST /asignaciones-rol/{id}/revocacion`. Todos se exigen con ámbito de liga: una cuenta no
pertenece a un club. `GET /roles` exige `seguridad.rol:CONSULTAR` y es la lista que llena el
selector de rol de F-12.

`GET /usuarios` responde la envoltura de paginación (§4) y admite `q` (nombre o correo), `rolId`,
`ambito` (`LIGA`, `CLUB` o `PROPIO`), `clubId`, `activo`, `pagina` y `tamano`; el orden es fijo por
nombre. Cada fila trae `id`, `nombreMostrado`, `correo`, `activo`, `creadoEn` y `asignaciones`
(solo las activas, para los chips de rol):

```json
{
  "contenido": [
    {
      "id": "…",
      "nombreMostrado": "Ana López",
      "correo": "ana@ejemplo.mx",
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
`{ "nombreMostrado", "correo", "asignaciones": [ { "rolId", "ambito"?, "clubId"? } ] }`. El ámbito
es opcional y, si se omite, se usa `roles.ambito_predeterminado`; con `CLUB` el `clubId` es
obligatorio y el club debe ser de la liga. Responde `201` con `Location` y el detalle. Errores:
`400 VALIDACION` si falta un rol, el ámbito `CLUB` no lleva club (o al revés), el rol no es asignable
(`asignable = false`), el club no existe o la asignación está repetida; `409 DUPLICADO` si el correo
ya tiene perfil; `503 SERVICIO_NO_DISPONIBLE` si Supabase Auth no responde. El backend llama a la API
de administración de Supabase Auth (ADR-0003 punto 5) y, si la transacción falla después de crear la
cuenta, la elimina como compensación para no dejarla sin perfil. Se audita `INVITAR_USUARIO` sobre
`perfiles` con los roles, ámbitos y clubes, sin nombre ni correo.

`PATCH /usuarios/{id}` lleva `{ "activo": true|false, "motivo"? }`. Al desactivar, el motivo es
obligatorio (mínimo 10 caracteres, **[PROPUESTA]**) y el cambio se audita como `DESACTIVAR_CUENTA`;
al activar, como `ACTIVAR_CUENTA`. Una cuenta inactiva se rechaza en cada petición aunque su token
siga vigente (ADR-0003 punto 6). Repetir el estado actual responde `200` sin generar un evento.

`POST /usuarios/{id}/asignaciones` lleva `{ "rolId", "ambito"?, "clubId"? }` y responde `201` con
`Location` y la asignación. Una asignación activa repetida responde `409 DUPLICADO`. La revocación es
`POST /asignaciones-rol/{id}/revocacion` con `{ "motivo" }` (mínimo 10): la asignación no se borra,
queda `activa: false` con `revocadaEn` y se audita `REVOCAR_ASIGNACION`; sin motivo, `400 RN-021`; si
ya estaba revocada, `409 RF-094`. Asignar (`ASIGNAR_ROL`) y revocar se auditan sobre
`asignaciones_rol` con el rol, el ámbito y el club, sin datos personales (RF-093, RNF-012).

`GET /roles` devuelve un arreglo sin paginación con `id`, `codigo`, `nombre`, `descripcion`,
`ambitoPredeterminado` y `asignable`; los roles `asignable = false` (`SISTEMA`, `USUARIO_PUBLICO`) se
listan, pero no se pueden asignar. El detalle `GET /roles/{id}`, `GET /permisos`, la edición de la
matriz de permisos y la denegación VAL-022 del propio rol son H-20, no de este contrato.

### 6.2 M-02 Temporadas y normativa

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /temporadas` · `POST /temporadas` · `PATCH /temporadas/{id}` | Temporadas | `temporadas.temporada:CONSULTAR` en los `GET`; `temporadas.temporada:EDITAR` en `POST` y `PATCH` | RF-001, RF-003 | N |
| `POST /temporadas/{id}/activacion` · `/cierre` | Activar y cerrar | `temporadas.temporada:EDITAR` | RF-001, RF-003, RF-100, RN-058 | N |
| `GET /categorias` · `POST` · `PATCH /categorias/{id}` | Catálogo con orden y categoría superior | `temporadas.temporada:EDITAR` | RF-099 | N |
| `GET /temporadas/{id}/categorias` · `POST` | Categorías que participan en la temporada | `temporadas.temporada:EDITAR` | RF-004 | N |
| `GET /grupos?categoriaTemporadaId=` · `POST /grupos` · `PATCH` | Grupos | `temporadas.temporada:EDITAR` | RF-005 | N |
| `GET /temporadas/{id}/parametros` · `POST /temporadas/{id}/parametros` | Versiones de parámetros; `POST` crea una versión nueva, nunca edita la vigente | `temporadas.parametros:EDITAR` | RF-006, RF-100 | N |
| `PUT /temporadas/{id}/convocatoria` | Referencia de la convocatoria | `temporadas.convocatoria:EDITAR` | RF-002 | B |
| `GET /interpretaciones-regla` · `POST` · `POST …/{id}/resolucion` | Reglas interpretadas | `temporadas.interpretacion:*` | RF-007 | B |

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

La lista es la que usa F-10 para deshabilitar el botón y explicar qué falta. `POST /temporadas` lleva
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

**Activación y cierre.** `POST /temporadas/{id}/activacion` no lleva cuerpo y pasa
`CONFIGURACION → ACTIVA` si la lista de verificación está completa. Errores: `409 RN-058` si otra
temporada está activa (el mensaje la nombra), `409 RF-100`, `409 RF-004` o `409 RF-005` con la
extensión `requisitos` para los que falten, y `409 RF-001` si la temporada no está en configuración.
`POST /temporadas/{id}/cierre` lleva `{ "motivo" }` (mínimo 10 caracteres), pasa
`ACTIVA → CERRADA`, fija el motivo para el historial y la auditoría (RN-021, RF-093); sin motivo
responde `400 RN-021` y si no está activa, `409 RF-001`. Ambas acciones se auditan como
`ACTIVAR_TEMPORADA` y `CERRAR_TEMPORADA`.

Las respuestas de parámetros incluyen `"esDemo": true` en cada valor **[DEMO]** para que la
interfaz los rotule «no oficial».

### 6.3 M-03 Organización deportiva

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /clubes` · `GET /clubes/{id}` · `POST /clubes` | Clubes. Sin `PATCH`: el nombre solo cambia con la acción de B-04 | `organizacion.club:CONSULTAR` en los `GET`; `organizacion.club:EDITAR` en `POST` | RF-008 | N |
| `POST /clubes/{id}/cambio-nombre` | Cierra el club y crea uno nuevo | `organizacion.club:EDITAR` | RF-105 | B |
| `GET /afiliaciones?temporadaId=&estado=` · `POST /afiliaciones` · `PATCH /afiliaciones/{id}` | Tarjetón por temporada (folio lo asigna el sistema) | `organizacion.afiliacion:CAPTURAR` | RF-101 | N |
| `POST /afiliaciones/{id}/resolucion` | Aprobar o rechazar | `organizacion.afiliacion:APROBAR` | RF-101 | N |
| `GET /clubes/{id}/directiva` · `POST /clubes/{id}/directiva` · `POST /cargos-directiva/{id}/autorizacion` | Cargos con vigencia | `organizacion.directiva:*` | RF-102 | N |
| `GET /equipos?temporadaId=&grupoId=&clubId=&estado=` · `GET /equipos/{id}` · `POST` · `PATCH /equipos/{id}` · `POST /equipos/{id}/baja` | Equipos | `organizacion.equipo:CONSULTAR` en los `GET`; `organizacion.equipo:EDITAR` en `POST`, `PATCH` y la baja | RF-009 | N |
| `POST /operaciones-equipo` · `POST …/{id}/resolucion` | Venta y conservación de plaza | `organizacion.venta:*` | RF-103, RF-104 | B |
| `GET /personas?curp=` · `POST /personas` · `PATCH /personas/{id}` | Identidad única por CURP; `POST` con una CURP existente responde `409` con el `id` para reutilizarla | `organizacion.persona:*` | RF-010, RF-011 | N |
| `GET /personas/{id}/historial` | Clubes y equipos por temporada | `organizacion.persona:CONSULTAR` | RF-012 | N |
| `POST /personas/{id}/tutelas` | Vincular menor y tutor | `organizacion.persona:EDITAR` | RF-106 | N |

**Ámbito.** Clubes y equipos se autorizan con el ámbito del club: la directiva de un club solo ve y
opera el suyo, y un club o equipo ajeno responde `404` igual que uno inexistente (VAL-024). Los
listados filtran por ámbito en el `WHERE` y en el `COUNT`; un `clubId` ajeno en el filtro de
`GET /equipos` da una página vacía. El alta de un club es de toda la liga.

**Club.** `GET /clubes?q=&estado=&pagina=&tamano=` responde la envoltura de paginación (§4) ordenada
por nombre; `q` busca dentro del nombre y `estado` es `ACTIVO` o `CERRADO`. Cada club trae `id`,
`nombre`, `estado`, `creadoEn` y, solo si está cerrado, `cerradoEn` y `motivoCierre`.
`GET /clubes/{id}` responde ese detalle. `POST /clubes` lleva `{ "nombre" }` y responde `201` con
`Location`; el club nace `ACTIVO` y la liga sale de la configuración del backend, no del cuerpo.
Errores: sin nombre, `400 VALIDACION`; nombre ya usado en la liga, `409 DUPLICADO` con un mensaje que
nombra el club. **No existe `PATCH /clubes/{id}`:** el único dato editable es el nombre y su cambio
crea un club nuevo y cierra el anterior con `POST /clubes/{id}/cambio-nombre` (B-04, RN-041,
DEC-49). Tampoco hay `DELETE`. Se audita como `REGISTRAR_CLUB`.

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

### 6.4 M-04 Registros y elegibilidad

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /registros-jugador?temporadaId=&equipoId=&estado=` | Registros de su ámbito | `registros.registro:CONSULTAR` | RF-017 | N |
| `POST /registros-jugador` | Alta: `{ personaId, equipoId }`; queda `PENDIENTE_VALIDACION`. `409 VAL-017` dice club y equipo donde ya está registrada la CURP | `registros.registro:EDITAR` | RF-013, RF-107 | N |
| `GET /registros-jugador/{id}/requisitos` | Documentos que exige su categoría y cuáles faltan | `registros.registro:CONSULTAR` | RF-108, VAL-001 | N |
| `POST /registros-jugador/{id}/validacion` · `/baja` | Validar, rechazar, dar de baja | ver §2.1 | RF-109, RF-014 | N |
| `GET /registros-jugador/{id}/historial` | Cambios de estado con usuario y motivo | `registros.registro:CONSULTAR` | RF-018 | B |
| `GET /registros-jugador/{id}/elegibilidad?equipoId=` | `{ elegible, causas: [ { codigo, mensaje } ] }` | `registros.registro:CONSULTAR` | RF-019, RF-020, RF-110 | N |
| `POST /registros-jugador/renovaciones` · `/transferencias` | Renovación y cambio de club entre temporadas | `registros.registro:EDITAR` | RF-015, RF-016 | B |
| Documentos | Ver §7 | `registros.documento:*` | RF-108, RF-109 | N |
| `GET /mi-informacion` | Registro, documentos, elegibilidad, partidos y sanciones del jugador o de los menores que tutela | ámbito `PROPIO` | RF-144 | N |

### 6.5 M-05 Árbitros y designaciones

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /arbitros?estado=&clubId=` · `POST /arbitros` | Árbitros presentados por el club | `arbitraje.arbitro:PROPONER` | RF-112, RF-021 | N |
| `POST /arbitros/{id}/validacion` | Validar o rechazar | `arbitraje.arbitro:VALIDAR` | RF-113 | N |
| `GET /clubes/{id}/cuota-arbitros` | Árbitros presentados frente a los requeridos | `arbitraje.arbitro:CONSULTAR` | RF-114 | B |
| `GET /arbitros/{id}/disponibilidad` · `PUT` | Disponibilidad | `arbitraje.disponibilidad:EDITAR` | RF-022 | B |
| `GET /designaciones?jornadaId=&arbitroId=` · `POST /partidos/{id}/designaciones` | Designar; `422 VAL-019` si el árbitro es de un club del partido | `arbitraje.designacion:*` | RF-023, RF-115 | N |
| `GET /mis-designaciones` | Partidos asignados al árbitro | ámbito `PROPIO` | RF-123 | N |

### 6.6 M-06 Canchas y recursos

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /canchas?activa=&ubicacion=&propietario=&sedeId=&q=` · `GET /canchas/{id}` · `POST` · `PATCH /canchas/{id}` | Catálogo con ubicación, propietario y sede (`sede: { id, nombre }`, omitida si no tiene). `orden`: `nombre`, `creadoEn`. Activar o desactivar por `PATCH` con `activa` y `motivo` | `canchas.cancha:CONSULTAR` en los `GET`, `canchas.cancha:EDITAR` en `POST` y `PATCH` | RF-027, RF-028 | N · B |
| `GET /sedes` · `POST /sedes` · `PATCH /sedes/{id}` | Sedes que agrupan canchas. El `GET` responde la lista completa sin paginar, por `nombre` | `canchas.cancha:CONSULTAR` en el `GET` (cualquier ámbito), `canchas.cancha:EDITAR` en `POST` y `PATCH` | RF-028 | B |
| `GET /canchas/{id}/disponibilidad?desde=&hasta=` · `POST` | Disponibilidad, bloqueos y mantenimiento con motivo. El `POST` responde `{ disponibilidad, partidosAfectados }` | `canchas.cancha:CONSULTAR` en el `GET`, `canchas.cancha:EDITAR` en el `POST` | RF-029, RF-030, RF-031 | N · B |
| `GET /canchas/{id}/ocupacion?desde=&hasta=` | Disponibilidad, bloqueos, mantenimiento y partidos no cancelados del rango (calendario semanal y apoyo a la programación) | `canchas.cancha:CONSULTAR` | RF-032, RF-039 | N |
| `GET /canchas/{id}/horarios-club` · `PUT` | Horarios por categoría y comodato | `canchas.cancha:EDITAR` | RF-119 | B |
| `GET /solicitudes-cancha` · `POST` · `POST …/{id}/resolucion` · `GET …/{id}/oficio` | Formatos 1 y 2 y su carta | `canchas.solicitud:*`, `canchas.oficio:GENERAR` | RF-116 a RF-118 | B |

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
- **Bloqueo y mantenimiento sobre partidos (PEN-007, [PROPUESTA]).** Un `BLOQUEO` o un
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

### 6.7 M-07 Programación manual

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /jornadas?grupoId=&estadoPublicacion=` · `POST /jornadas` | Jornadas del grupo | `programacion.jornada:EDITAR` | RF-033 | N |
| `GET /jornadas/{id}` | Jornada con partidos, conflictos y advertencias | `programacion.jornada:CONSULTAR` | RF-033 | N |
| `POST /jornadas/{id}/partidos` · `PATCH /partidos/{id}` | Registrar o ajustar partido (equipos, cancha, inicio); solo con la jornada en `BORRADOR`; ajuste con `motivo` | `programacion.jornada:EDITAR` | RF-033, RF-036, RF-040 | N |
| `POST /jornadas/{id}/verificacion` | Revisa todos los partidos sin guardar: `{ conflictos: [ { partidoId, codigo, mensaje } ], advertencias: [...] }` | `programacion.jornada:EDITAR` | RF-039, RF-120 | N · B |
| `POST /jornadas/{id}/envio` · `/resolucion` | Aprobación y publicación | ver §2.1 | RF-121, RF-090 | N |

La verificación y el guardado usan las mismas reglas: VAL-003 (equipo duplicado en el bloque),
VAL-004 (cancha ocupada), VAL-005 (cancha bloqueada o fuera de disponibilidad), VAL-006 (equipo con
baja o sanción). Cada conflicto nombra los partidos, la cancha y el horario que chocan.

### 6.8 M-08 Partidos, cédula y resultados

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /partidos?jornadaId=&estado=&desde=&hasta=` · `GET /partidos/{id}` | Partido con estado, historial, árbitros, cédula, resultado y `accionesPermitidas` | `partidos.partido:CONSULTAR` | RF-043, RF-046 | N |
| `GET /partidos/{id}/historial` | Transiciones con usuario y motivo | `partidos.partido:CONSULTAR` | RF-045 | N |
| `POST /partidos/{id}/transiciones` | Suspender, reprogramar o cancelar | `partidos.partido:CAMBIAR_ESTADO` | RF-043 | N |
| `GET /partidos/{id}/cedula` · `POST /partidos/{id}/cedula` | Obtener o abrir la cédula (`EN_CAPTURA`) | `partidos.cedula:*` | RF-025 | N |
| `PUT /cedulas/{id}/alineaciones` | Alineación de ambos equipos; valida elegibilidad por jugador (`422` con `errores` por jugador) | `partidos.cedula:CAPTURAR` | RF-019, RF-110, VAL-002, VAL-018 | N |
| `PUT /cedulas/{id}/marcador` | Goles, penales, no presentación (marcador por default de los parámetros) | `partidos.cedula:CAPTURAR` | RF-047, RF-126 | N |
| `POST /cedulas/{id}/eventos` · `DELETE /cedulas/{id}/eventos/{eventoId}` | Goles, tarjetas, sustituciones | `partidos.cedula:CAPTURAR` | RF-025 | N |
| `POST /cedulas/{id}/incidencias` | Incidencia con tipo e involucrados | `partidos.cedula:CAPTURAR` | RF-026 | N |
| `POST /cedulas/{id}/envio` · `/aprobacion` · `/devolucion` · `/reapertura` | Flujo de la cédula | ver §2.1 | RF-122 a RF-124 | N |
| `GET /cedulas?estado=PENDIENTE_VALIDACION&plazoVencido=true` | Bandeja del árbitro o de la Comisión | `partidos.cedula:CONSULTAR` | RF-123, RF-124 | N |
| `GET /partidos/{id}/resultados` | Versiones del resultado (vigente y anteriores) | `partidos.partido:CONSULTAR` | RF-052 | N |
| `GET /grupos/{id}/tabla` | Tabla calculada con `calcular_tabla` y la versión de parámetros usada | `partidos.partido:CONSULTAR` | RF-049, RF-050, RF-053 | N |
| `POST /grupos/{id}/sorteos` | Registrar sorteo de desempate | `partidos.sorteo:REGISTRAR` | RF-050 | N |
| `GET /grupos/{id}/estadisticas-jugador` | Goles, tarjetas y participaciones | `partidos.partido:CONSULTAR` | RF-054 | B |

No existe un endpoint para escribir un resultado oficial: lo crea la aprobación de la cédula o una
resolución disciplinaria (RF-048, VAL-010).

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
`ENVIAR_COMUNICADO_A_REVISION`, `APROBAR_COMUNICADO` y `DEVOLVER_COMUNICADO`. La lista combinada de
`GET /publicaciones/pendientes` se completará cuando exista la consulta de jornadas de M-07.

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
| `GET /tarifas?temporadaId=` · `POST` · `PATCH` | Tarifas por temporada y categoría | `tesoreria.tarifa:EDITAR` | RF-135 | B |
| `GET /cobros?clubId=&equipoId=&estado=` · `POST /cobros` | Cobros (manuales en N; los automáticos los genera la publicación de la jornada, B) | `tesoreria.cobro:*` | RF-137, RF-136 | N · B |
| `GET /adeudos?clubId=` | Suma de cobros pendientes y vencidos por club y equipo | `tesoreria.cobro:CONSULTAR` | RF-140 | N |
| `POST /cobros/{id}/intentos-pago` · `POST /intentos-pago/{id}/confirmacion` | Pago en línea simulado | `tesoreria.pago:PAGAR` | RF-138, ADR-0012 | N |
| `POST /cobros/{id}/pagos` | Pago externo con comprobante | `tesoreria.pago:REGISTRAR` | RF-139 | N |
| `GET /cobros/{id}/pagos` | Pagos e intentos del cobro | `tesoreria.cobro:CONSULTAR` | RF-140 | N |

La pantalla de pago simulado muestra siempre «Pago simulado: no se realiza ningún cargo». La API
**no acepta** datos de tarjeta en ningún campo.

### 6.14 M-14 Notificaciones

| Método y ruta | Propósito | Permiso | RF | E |
| --- | --- | --- | --- | --- |
| `GET /notificaciones?leida=false` | Avisos del usuario, más recientes primero; la cabecera `X-No-Leidas` trae el conteo | `notificaciones.aviso:CONSULTAR` | RF-142 | N |
| `POST /notificaciones/{id}/lectura` · `POST /notificaciones/lectura` | Marcar uno o todos como leídos | `notificaciones.aviso:CONSULTAR` | RF-142 | N |

Sin tiempo real: la interfaz consulta al cargar y cada 60 s **[PROPUESTA]** mientras la pestaña está
visible. El campo `enlace` es una ruta interna del frontend (`/partidos/{id}`).

## 7. Documentos y archivos (AWS S3)

Aplica a documentos de registro y de tutor (RF-108), comprobantes de afiliación y de pago (RF-139) y
evidencias (RF-059). Decisión completa en ADR-0013.

| Método y ruta | Propósito | Permiso |
| --- | --- | --- |
| `POST /registros-jugador/{id}/documentos` · `/personas/{id}/documentos` · `/afiliaciones/{id}/documentos` | Cargar (`multipart/form-data`: `archivo` + `tipoDocumentoId`) | `registros.documento:CREAR` |
| `GET /registros-jugador/{id}/documentos` | Metadatos: tipo, tamaño, estado de validación, quién y cuándo | `registros.documento:CONSULTAR` |
| `GET /documentos/{id}/enlace` | URL prefirmada de lectura | `registros.documento:CONSULTAR` |
| `POST /documentos/{id}/validacion` | `{ "decision": "APROBADO"\|"RECHAZADO", "observaciones" }` | `registros.documento:VALIDAR` |
| `POST /documentos/{id}/reemplazo` | Carga una versión nueva; la anterior queda con `reemplazadoPorId` | `registros.documento:CREAR` |

- Tipos aceptados por **firma binaria**: JPEG, PNG, WebP y PDF. Tamaño máximo 5 MB **[PROPUESTA]**.
- `GET /documentos/{id}/enlace` responde `{ "url": "https://…", "expiraEn": "…" }` con vigencia de
  60 s **[PROPUESTA]**. La respuesta lleva `Cache-Control: no-store`; la interfaz abre la URL de
  inmediato y nunca la guarda.
- Las respuestas **nunca** incluyen `s3Bucket`, `s3Clave` ni la `url` guardada en la base.

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
  la persona; en listados se enmascara (`GAPA******HMN***05`) **[PROPUESTA]**.
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
| Textos definitivos de los mensajes de error | PEN-016 |
| Duración y renovación de la sesión, política de contraseñas, intentos | PEN-030 |
| Proveedor de correo para RF-143 | PEN-034, DEC-46 |
| Valores **[PROPUESTA]** de este documento: longitud mínima del motivo, límites de tamaño, vigencia de la URL prefirmada, caché del portal, intervalo de consulta de avisos, enmascarado de CURP | Validar con el equipo y el profesor |
