# Prueba de aceptación · H-33 Asignar automáticamente cancha y horario a una fecha

| Campo | Valor |
| --- | --- |
| Historia | H-33 · subtarea H-33.4 · Sprint 4 · Must · 5 SP |
| Requisitos | RF-032, RF-035, RF-036, RF-039, RF-041, RF-042, RN-016, RN-017, VAL-003 a VAL-007, VAL-027, CA-002 |
| Decisiones | DEC-176, DEC-178, DEC-179, DEC-180, DEC-181 · ADR-0015 |
| API | `POST /api/v1/fechas/{id}/asignacion` `{ modo: PENDIENTES\|REGENERAR, motivo? }` |
| Implementó | Israel (H-33.1 #200, H-33.2 #202 y H-33.3 #203) |
| Versión probada | `develop` en `795f4d8` (27-nov-2026) |
| Fecha | 2026-11-27 |
| Ejecutó | Agente de IA (DeepSeek Harness) en el entorno local, para Geovani (prueba cruzada, DEC-50) |
| Resultado global | **Aprobado** (8 de 8 criterios; pruebas de error 401/403/404 incluidas) |

La corrida valida la asignación automática de cancha y horario a una fecha completa (H-33) contra
PostgreSQL 17 real y la cadena de seguridad del backend. El motor voraz recibe los emparejamientos
de todos los grupos y categorías de la fecha y las ventanas por día y cancha; el servicio confirma
cada candidato con `ValidadorConflictos` y `ValidadorLocalia`, guarda las causas de lo que no cupo
y audita la operación. La prueba no usó el proyecto compartido de Supabase.

H-33 es una historia de backend: su pantalla F-22 (vista por fecha) se implementa en H-35. Por eso
la aceptación se ejecuta por la API y la base de datos, sin navegador. La edición del motivo de
localía que pide el criterio CA-4 se apoya en el ajuste de partido de H-12/B-15, que ya actualiza
`partidos.motivo_localia`; F-22 lo mostrará y editará en H-35.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 efímero de Testcontainers (`postgres:17-alpine`), con la simulación de Supabase [`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql), las 16 migraciones del repositorio y una sola conexión en el pool |
| Backend | Java 25.0.5.1, Spring Boot 4.1.1 y Maven 3.9.11; `./mvnw verify` filtrado a las clases de H-33 |
| Autenticación | `AsignacionFechaControllerTest` usa la cadena de seguridad real y tokens ES256 de `SeguridadPrueba`; las integraciones de servicio usan el contexto de seguridad con un perfil con rol PROGRAMADOR |
| Interfaz | No aplica a H-33: F-22 es H-35. La prueba del motivo de localía editable se apoya en el ajuste de partido de H-12/B-15 |
| Alcance | Base local efímera; no se alteró el proyecto compartido de Supabase ni se usaron datos personales |

El arnés de autenticación y las pruebas viven en el repositorio (`backend/src/test`). No hay
secretos, correos personales ni tokens en las evidencias.

## 2. Datos de prueba

| Escenario | Datos | Criterios |
| --- | --- | --- |
| **Multicategoría** (`AsignacionMulticategoriaIT`) | Temporada que empieza el sábado 2034-03-04, con 18 fechas de fase regular. «Libre» juega domingo con grupos de 4 y 5 equipos (el de 5 descansa); «Veteranos» e «Infantil» juegan sábado con 4 equipos cada una. Dos canchas de la unidad deportiva, la cancha del club local de un partido de «Libre» bloqueada de 08:00 a 12:00 del domingo y un visitante de «Veteranos» con suspensión vigente. Sin árbitros `VALIDADO`, para aislar H-34. | CA-1, CA-3, CA-4 y CA-5 |
| **Fecha simple** (`AsignacionFechaIT`) | Temporada que empieza el sábado 2033-03-05; «Libre» juega domingo y «Veteranos» sábado, con un grupo de 4 equipos cada una; dos canchas de la unidad deportiva. Sin árbitros `VALIDADO`. | CA-1, CA-6, CA-7 y CA-8 |
| **Motor** (`MotorAsignacionTest`) | Una cancha disponible solo de 08:00 a 12:00 del domingo con 3 partidos de 120 min; canchas del local, de la unidad deportiva y de un tercero; categorías con distinto orden y días de juego. | CA-2, CA-4 y CA-5 |
| **Contrato HTTP** (`AsignacionFechaControllerTest`) | Fecha 3 en `BORRADOR` con 4 asignados, 1 no asignado, 3 centrales y 1 sin árbitro; doble del servicio con la respuesta de ejemplo de las convenciones de API §6.7. | Caso feliz y errores 400/401/403/404/409/422 |

Los clubes, equipos, canchas, cuentas y fechas son ficticios y se crean dentro de la base efímera.
Los identificadores son UUID generados por la prueba.

## 3. Escenarios

### CA-1 · Cada partido queda en un día de su categoría, dentro del horario y sin conflictos duros

> Dada una fecha con jornadas en BORRADOR, cuando se asigna, entonces cada partido queda en un día
> de su categoría y dentro del horario de juego, y la verificación de la fecha no reporta
> conflictos duros (CA-002, VAL-003 a VAL-006).

**Resultado obtenido:** **Aprobado.** En el escenario multicategoría se asignaron **7 partidos**
(1 quedó sin asignar por la sanción del CA-3), con **4 jornadas** —una por grupo— y **1 descanso**.
Los 7 partidos quedaron en el día de su categoría (Libre en domingo; Veteranos e Infantil en
sábado) y entre las **08:00 y las 18:00**. `FechaService.verificar` devolvió **conflictos vacíos** y
las consultas de traslape dieron **0 canchas** con dos partidos y **0 equipos** con dos partidos a
la vez. Una misma cancha recibió partidos de varios grupos y de más de una categoría. En el
escenario simple se asignaron **4 de 4** partidos en **2 jornadas**, también sin conflictos.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-33/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-33/consultas-bd.txt)

**Pruebas automáticas:** `AsignacionMulticategoriaIT#asignarFechaMulticategoria` («CA-002: varias
categorías con canchas compartidas, una cancha bloqueada y un equipo sancionado se asignan sin
conflictos duros») y `AsignacionFechaIT#asignarYRegenerar` (día, hora y verificación sin
conflictos).

### CA-2 · Lo que no cabe queda «sin asignar» con causa VAL-007 y resumen por día

> Lo que no cabe queda «sin asignar» con causa codificada (VAL-007) y un resumen de franjas libres y
> partidos requeridos por día (RF-042).

**Resultado obtenido:** **Aprobado.** Con una cancha disponible solo de 08:00 a 12:00 del domingo y
3 partidos de 120 min, se asignaron 2 y el tercero quedó con código **VAL-007** y la causa «Sin
horario libre el domingo para Libre (bloque de 120 min): **2 franjas libres, 3 partidos**». El
resumen devolvió el sábado con 5 franjas libres y 0 partidos requeridos, y el domingo con 2 franjas
libres y 3 partidos requeridos. El contrato HTTP expone `noAsignados[0].codigo = VAL-007` y
`resumen[]` con `dia`, `franjasLibres`, `bloqueMinutos` y `partidosRequeridos`; el servicio guarda
el código y la causa en el emparejamiento.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-33/resultados-por-criterio.txt),
[`respuestas-api.txt`](evidencias/H-33/respuestas-api.txt)

**Pruebas automáticas:** `MotorAsignacionTest#sinLugarConCausaYResumen`,
`AsignacionFechaServiceTest#validadorRechazaAlCandidato` y `AsignacionFechaControllerTest#asignar`.

### CA-3 · Un equipo con baja o sanción vigente deja su partido sin asignar con VAL-006

> Un equipo con baja o sanción vigente deja su partido sin asignar con la causa VAL-006.

**Resultado obtenido:** **Aprobado.** El visitante sancionado de «Veteranos» produjo el único
partido sin asignar del escenario multicategoría: **VAL-006** y una causa con «suspensión vigente».
La base guardó `codigo_no_asignado = 'VAL-006'` y `partido_id NULL`, y el equipo sancionado quedó
con **0 partidos** en la fecha. El caso de equipo en `BAJA` se cubrió con la causa «El equipo
«Toros» está dado de baja y no se puede programar.». El partido no elegible no consumió franja: los
demás se asignaron normalmente.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-33/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-33/consultas-bd.txt)

**Pruebas automáticas:** `AsignacionMulticategoriaIT#asignarFechaMulticategoria`,
`AsignacionFechaServiceTest#equipoDeBaja` y `MotorAsignacionTest#equipoNoElegibleQuedaSinAsignar`.

### CA-4 · Se prefiere la cancha del club local, luego la unidad deportiva, y se llena el motivo de localía

> Se prefiere la cancha del club local y luego la unidad deportiva; si no se cumple, se llena un
> motivo de localía automático que se puede editar (VAL-027, DEC-179).

**Resultado obtenido:** **Aprobado.** El partido cuyo club local tiene cancha se asignó a esa cancha
y **sin** motivo de localía. Sin cancha del local, los partidos fueron a la **unidad deportiva**
antes que a las demás canchas. En las fechas finales (RN-049) la unidad deportiva pasó antes que la
cancha del local. Cuando no se pudo respetar la preferencia, el motor propuso un motivo que empieza
con «Asignación automática:» y menciona la unidad deportiva o la cancha usada; el servicio lo guardó
en `partidos.motivo_localia` con el mismo prefijo. El ajuste de partido de H-12/B-15
(`PATCH /partidos/{id}` con `motivoAceptacion`) puede reemplazar ese motivo; F-22 lo editará en H-35.
En el escenario multicategoría, la cancha del club local estaba bloqueada de 08:00 a 12:00 y el
partido se colocó en ella a las **12:00**, respetando el bloqueo.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-33/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-33/consultas-bd.txt)

**Pruebas automáticas:** `MotorAsignacionTest#prefiereLaCanchaDelLocalYLuegoLaUnidad`,
`#motivoDeLocaliaFueraDeLaCanchaDelLocal`, `#enFechasFinalesLaUnidadVaPrimero`,
`#motivoDeLocaliaEnFechasFinales` y `AsignacionFechaServiceTest#motivoDeLocalia`. La edición del
motivo ya está cubierta por `JornadaServiceTest` («B-15.2: aceptar la localía desde la fila guarda
solo el motivo y audita ACEPTAR_LOCALIA») y `ProgramacionIT` (misma regla contra PostgreSQL).

### CA-5 · La categoría con menor orden ocupa los horarios más tempranos

> Dentro de su día, la categoría con menor orden ocupa los horarios más tempranos (DEC-178).

**Resultado obtenido:** **Aprobado.** «Libre» (orden 1) empezó a las **08:00** y «Veteranos» (orden
2) a las **10:00**, consecutivos. La categoría con menos días permitidos se colocó antes que una de
menor orden. En la cancha compartida del sábado, «Veteranos» (orden 2) fue antes que «Infantil»
(orden 3): la secuencia fue **Veteranos, Infantil, Infantil**, y cada inicio coincidió con el fin
del partido anterior.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-33/resultados-por-criterio.txt)

**Pruebas automáticas:** `MotorAsignacionTest#menorOrdenPrimeroYConsecutivos`,
`#menosDiasPrimero` y `AsignacionMulticategoriaIT#asignarFechaMulticategoria`.

### CA-6 · «Asignar pendientes» no modifica los partidos ya asignados ni los ajustados

> «Asignar pendientes» no modifica los partidos ya asignados ni los ajustados.

**Resultado obtenido:** **Aprobado.** Con una ocupación previa de 08:00 a 10:00 (un partido ya
asignado o ajustado), el pendiente se colocó a las **10:00**; el servicio no llamó a
`cancelarPartido`, no soltó emparejamientos y no fijó un motivo de operación. La segunda llamada en
modo `PENDIENTES` devolvió totales **(0, 0, 0, 0, 4)**: no insertó ni canceló, y la lista de
partidos quedó **idéntica** a la anterior. El cuarto valor (4) es el de los partidos sin central
automático (la designación de árbitros se aisló para H-34).

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-33/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-33/consultas-bd.txt)

**Pruebas automáticas:** `AsignacionFechaServiceTest#pendientesRespetaLoAsignado` y
`AsignacionFechaIT#asignarYRegenerar` (segunda llamada).

### CA-7 · «Regenerar» exige motivo, cancela con historial y solo opera en BORRADOR

> «Regenerar» exige motivo, cancela (CANCELADO con historial) los partidos de la fecha sin borrarlos
> y vuelve a asignar; solo con todas las jornadas en BORRADOR (DEC-181).

**Resultado obtenido:** **Aprobado.** Sin motivo o con menos de 10 caracteres respondió **400
RN-021** antes de bloquear la fecha y sin escribir. Con una jornada en `REVISION` respondió **409
RF-090** en los dos modos. Con un motivo válido, `REGENERAR` canceló los **4 partidos** anteriores:
siguen en la tabla como **CANCELADO** (no se borraron), cada uno con una fila en
`partido_estado_historial` con `estado_nuevo = CANCELADO` y el motivo; se retiraron las
designaciones y se soltaron los emparejamientos. El listado de cada jornada ya no mostró los
cancelados (2 partidos `PROGRAMADO` por jornada) y los 4 nuevos ocuparon la **misma cancha e
inicio** que los anteriores.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-33/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-33/consultas-bd.txt),
[`respuestas-api.txt`](evidencias/H-33/respuestas-api.txt)

**Pruebas automáticas:** `AsignacionFechaServiceTest#regenerarSinMotivo`,
`#jornadaEnRevision`, `#regenerarCancelaSinBorrar`, `#regenerarConPartidoNoCancelable`,
`AsignacionFechaIT#asignarYRegenerar`, `AsignacionFechaControllerTest#regenerarSinMotivo` y
`#jornadaEnRevision`.

### CA-8 · Todo en una transacción, con auditoría GENERAR_FECHA y resultado determinista

> Todo ocurre en una transacción con auditoría GENERAR_FECHA (RF-041, RNF-004) y con los mismos
> datos se obtiene la misma asignación.

**Resultado obtenido:** **Aprobado.** `AsignacionFechaService#asignar` es `@Transactional` y hace
las validaciones antes de escribir. `parametros_generacion` guardó `versionParametros`, modo,
motivo (cuando hay), asignados, noAsignados, cancelados, designacionAutomatica, centrales y
sinArbitro. La auditoría registró **una** fila `M-07 / GENERAR_FECHA` sobre `fechas_programacion`
por operación (3 tras asignar, repetir `PENDIENTES` y regenerar), con el motivo y el detalle de
totales y jornadas creadas, sin nombres ni datos personales; la de `REGENERAR` guardó
`cancelados = 4`. La validación previa de un partido `EN_CURSO` respondió 409 RF-046 y **no canceló
nada**. Con los mismos datos, la firma `(emparejamientoId, canchaId, inicio)` de la asignación nueva
fue igual a la anterior, y la prueba del motor con la entrada barajada devolvió el mismo resultado.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-33/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-33/consultas-bd.txt)

**Pruebas automáticas:** `AsignacionFechaServiceTest#trazabilidadYAuditoria`,
`#regenerarConPartidoNoCancelable`, `AsignacionFechaIT#asignarYRegenerar` y
`MotorAsignacionTest#esDeterminista`.

## 4. Pruebas de error HTTP de la Definición de Terminado

| Estado | Caso | Código | Prueba |
| --- | --- | --- | --- |
| 401 | Sin token | `NO_AUTENTICADO` + `WWW-Authenticate: Bearer` | `seguridadAsignar()[1]` |
| 401 | Token firmado con otra llave | `NO_AUTENTICADO` | `seguridadAsignar()[2]` |
| 403 | Sin `programacion.jornada:EDITAR` | `VAL-015` | `seguridadAsignar()[3]` |
| 404 | Fecha de otra liga o fuera de ámbito | `NO_ENCONTRADO` | `seguridadAsignar()[4]`, `[5]` y `fechaInexistente` |
| 400 | Sin `modo` o modo inexistente | `VALIDACION` | `sinModo`, `modoInvalido` |
| 400 | `REGENERAR` con motivo de menos de 10 caracteres | `RN-021` | `regenerarSinMotivo` |
| 409 | Temporada cerrada | `RF-003` | `temporadaCerrada` |
| 409 | Jornada fuera de `BORRADOR` | `RF-090` | `jornadaEnRevision` |
| 409 | Sin parámetros vigentes | `RF-006` | `sinParametros` |
| 409 | Jornada manual con el número de la fecha | `DUPLICADO` | `jornadaManualDuplicada` |
| 409 | Partido no cancelable al regenerar | `RF-046` | `regenerarConPartidoNoCancelable` |
| 422 | Fecha sin emparejamientos vigentes | `VALIDACION` | `sinCalendario` |

Los 401 no llegan al servicio y los 403/404 conservan el código de las convenciones de API §3.1 y
§10. El contrato HTTP está en [`respuestas-api.txt`](evidencias/H-33/respuestas-api.txt).

## 5. Resultados de la corrida

```text
./mvnw -Dtest=AsignacionFechaServiceTest,AsignacionFechaControllerTest,MotorAsignacionTest \
       -Dit.test=AsignacionFechaIT,AsignacionMulticategoriaIT verify
BUILD SUCCESS (exit 0)
60 pruebas, 0 fallos, 0 errores, 0 omitidas
```

| Clase | Pruebas | Fallos |
| --- | ---: | ---: |
| `AsignacionFechaControllerTest` | 12 | 0 |
| `AsignacionFechaServiceTest` | 24 | 0 |
| `MotorAsignacionTest` | 22 | 0 |
| `AsignacionFechaIT` | 1 | 0 |
| `AsignacionMulticategoriaIT` | 1 | 0 |
| **Total** | **60** | **0** |

La compilación de todo el backend (principal y pruebas) se hizo dentro del ciclo `verify`; la
ejecución se filtró a las clases que cubren H-33. `AsignacionFechaServiceTest` incluye además las
pruebas de la integración de H-34.2 (árbitro central automático); se ejecutaron completas y no
afectan los criterios de H-33, cuyos escenarios aíslan la designación de árbitros. No se modificó el
frontend, por lo que no se ejecutó su suite. El detalle de los casos y los tiempos está en
[`pruebas-automaticas.txt`](evidencias/H-33/pruebas-automaticas.txt).

## 6. Defectos y observaciones

No se encontraron defectos.

- **H-33 no tiene pantalla propia.** F-22 (vista por fecha) es H-35; por eso la aceptación es por
  API y base de datos. El criterio de motivo de localía editable se apoya en el ajuste de partido de
  H-12/B-15 y quedará visible en F-22 al implementar H-35.
- **La corrida usa una base efímera** y no altera el proyecto compartido de Supabase (DEC-52).
- **Los árbitros** se aislaron (sin `VALIDADO`) para que H-34 no interfiera en los criterios de
  H-33; la respuesta incluye sus totales, pero su designación pertenece a H-34.

## 7. Conclusión

**H-33 cumple sus 8 criterios de aceptación** y las pruebas de error de la Definición de
Terminado (401/403/404, además de 400/409/422). La asignación respeta días, horario, conflictos,
bajas y sanciones, preferencia de cancha, orden de categorías, modos `PENDIENTES` y `REGENERAR`,
trazabilidad y determinismo. La historia queda **Aprobada** para el cierre del Sprint 4, con la
observación de que su interfaz F-22 corresponde a H-35.
