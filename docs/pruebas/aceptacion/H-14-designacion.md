# Prueba de aceptación · H-14 Designar árbitros a los partidos de una jornada

| Campo | Valor |
| --- | --- |
| Historia | H-14 · subtarea H-14.3 · Sprint 4 · Must · 2 SP |
| Requisitos | RF-023, RF-115, RF-039, VAL-019, UI-025; de apoyo RF-022, RF-093, RF-113, RF-123, RN-021 y VAL-015 |
| Decisiones | DEC-201 a DEC-205 (designación) y DEC-209 a DEC-212 (pantallas) |
| Pantallas | F-24 «Designaciones» (`/app/programacion/designaciones`) y «Mis designaciones» (`/app/mis-designaciones`) |
| API | `GET /api/v1/designaciones?jornadaId=`, `GET /api/v1/partidos/{id}/arbitros-candidatos`, `POST /api/v1/partidos/{id}/designaciones`, `POST /api/v1/designaciones/{id}/retiro` y `GET /api/v1/mis-designaciones` |
| Implementó | Leonardo (H-14.1 #201 y H-14.2 #204) |
| Versión probada | `develop` en `341ac5a` (27-nov-2026) |
| Fecha | 2026-11-27 |
| Ejecutó | Israel (prueba cruzada, DEC-50) con un agente de IA en el entorno local |
| Resultado global | **Aprobado** (3 de 3 criterios y 2 comprobaciones complementarias; 401/403/404 incluidos) |

La corrida valida la designación manual de árbitros de punta a punta. F-24 anticipa las causas de
inelegibilidad con la misma consulta que usa el backend, permite designar, sustituir al central y
retirar con motivo, y muestra el avance de la jornada. La designación válida queda guardada y el
árbitro la ve en «Mis designaciones» cuando la jornada se publica. El rechazo por pertenecer al club
de un equipo (VAL-019), el choque de horario (RF-039) y el árbitro no validado (RF-113) los aplica
`tg_designaciones_validar`; F-24 anticipa VAL-019 y RF-039 con `ElegibilidadArbitral`, el backend
los traduce a `422`, `409` y `409` respectivamente, y un árbitro `PROPUESTO` ni siquiera aparece en
la lista de candidatos.

La prueba se ejecutó con la suite automatizada: el backend contra **PostgreSQL 17 real**
(Testcontainers, con la simulación de Supabase y las 16 migraciones) y las pantallas con
**Testing Library**. El entorno de ejecución no dispone de Supabase Auth ni de navegador, así que no
se hizo una sesión manual con capturas; el flujo visible de F-24 y «Mis designaciones» se comprobó
con sus pruebas de componente y el contrato HTTP con la cadena de seguridad real. **No se alteró el
proyecto compartido de Supabase** (DEC-52).

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 efímero de Testcontainers (`postgres:17-alpine`), con [`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql) y las 16 migraciones del repositorio |
| Backend | Java 25.0.4, Spring Boot 4.1.1 y Maven 3.9.11; `./mvnw verify` filtrado a las clases de H-14 |
| Autenticación | `DesignacionControllerTest` usa la cadena de seguridad real y tokens ES256 de `SeguridadPrueba`; `DesignacionesIT` usa el contexto de seguridad con los perfiles COMISION_ARBITRAJE, PROGRAMADOR y ARBITRO de la semilla |
| Frontend | Node 24.22.1, React 19, Vite 8.3.5 y Vitest 5.0.5; pruebas de `PantallaDesignaciones` y `PantallaMisDesignaciones` |
| Interfaz | Sin navegador: se validaron los componentes con Testing Library. F-24 es H-14.2 y «Mis designaciones» es DEC-212 |
| Alcance | Base local efímera; no se consultó ni modificó el proyecto compartido de Supabase ni se usaron datos personales |

## 2. Datos de prueba

| Escenario | Datos | Criterios |
| --- | --- | --- |
| **DesignacionesIT** | Liga de la semilla (`a0000000-…-000000000001`), partidos creados con `PartidosPrueba`, clubes y personas ficticias con CURP válida y única, árbitros `VALIDADO` y `PROPUESTO`, una jornada publicada y otra en borrador, y disponibilidad arbitral de apoyo. | CA-1 a CA-5 |
| **PantallaDesignaciones** | Halcones vs Toros, Pumas vs Lobos y Águilas vs Halcones; candidatos Marta López (elegible), Luis Soto (del club Halcones), Raúl Mora (ya designado) y Carlos Vega (advertencia RF-022); una jornada con avance «4 de 6». | CA-1, CA-2 y CA-4 |
| **PantallaMisDesignaciones** | Dos designaciones del titular: una de asistente con cédula pendiente y otra de central en un partido cancelado. | CA-3 |

Los clubes, equipos, canchas, árbitros, personas y cuentas son ficticios y se crean dentro de la
base efímera o en el propio componente de prueba; los identificadores son UUID generados por la
prueba.

## 3. Escenarios

### CA-1 · Un árbitro del mismo club que un equipo se rechaza con VAL-019

> Dado un árbitro del mismo club que un equipo del partido, cuando se designa, entonces se rechaza
> con VAL-019.

**Flujo verificado.** En F-24 se abre «Designar» en el partido Halcones vs Toros. La lista de
candidatos trae solo árbitros `VALIDADO`; el árbitro del club Halcones aparece deshabilitado con la
causa «Pertenece a Halcones, que juega este partido.» y la leyenda «0 designaciones en la
temporada». Si se fuerza el alta, el backend responde `422 VAL-019` y no deja ninguna designación
vigente.

**Resultado obtenido:** **Aprobado.** `DesignacionesIT#arbitroDelMismoClub` crea un partido y un
árbitro `VALIDADO` del club local; la llamada al servicio (la misma que ejecuta el `POST`) devuelve
`422 VAL-019` traducido del trigger y `SELECT count(*) … WHERE vigente` sigue en **0**.
`DesignacionControllerTest#designarMismoClub`
confirma el `422 VAL-019` del contrato HTTP. En la pantalla, `PantallaDesignaciones.test.tsx` (caso
«los no elegibles quedan deshabilitados con su causa accesible») comprueba que el radio de Luis Soto
está deshabilitado y que su descripción accesible incluye la causa `VAL-019`.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-14/resultados-por-criterio.txt),
[`respuestas-api.txt`](evidencias/H-14/respuestas-api.txt)

**Pruebas automáticas:** `DesignacionesIT#arbitroDelMismoClub`,
`DesignacionControllerTest#designarMismoClub`, `PantallaDesignaciones.test.tsx` (VAL-019).

### CA-2 · Un árbitro ya designado a la misma hora se rechaza con el conflicto explicado

> Dado un árbitro ya designado en otro partido a la misma hora, cuando se designa, entonces se
> rechaza con el conflicto explicado.

**Flujo verificado.** Raúl Mora ya pita Pumas vs Lobos a las 10:00. En el diálogo de designación de
Halcones vs Toros aparece deshabilitado con «Ya está designado en Pumas vs Lobos a las 10:00 (Cancha
1).» Si la designación concurrente llega al backend, `POST /partidos/{id}/designaciones` responde
`409 RF-039`; F-24 muestra el `detail` en una alerta dentro del diálogo, lo deja abierto y vuelve a
pedir la lista de candidatos.

**Resultado obtenido:** **Aprobado.** `DesignacionesIT#choqueDeHorario` designa al árbitro en un
partido de las 10:00 y el segundo intento a las 11:00 —dentro del bloque— devuelve `409 RF-039`;
cuando el tercer partido empieza exactamente al `fin_bloque` del primero, la designación sí se
guarda, porque el rango es `[inicio, fin_bloque)`. `DesignacionesIT#candidatos` comprueba que el
candidato con choque lleva la causa `RF-039` con el mensaje «Ya está designado en Local vs Visitante
a las 10:30 (Cancha …)». En el componente, el caso «un 409 del backend se muestra en el diálogo, que
sigue abierto y refresca la lista» verifica la alerta y la nueva consulta de candidatos.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-14/resultados-por-criterio.txt),
[`respuestas-api.txt`](evidencias/H-14/respuestas-api.txt)

**Pruebas automáticas:** `DesignacionesIT#choqueDeHorario`, `DesignacionesIT#candidatos`,
`PantallaDesignaciones.test.tsx` (RF-039 y el `409` del backend).

### CA-3 · Una designación válida aparece en «Mis designaciones» del árbitro

> Dada una designación válida, cuando se guarda, entonces el árbitro ve el partido en «Mis
> designaciones».

**Flujo verificado.** La Comisión guarda una designación válida y el árbitro inicia sesión y abre
«Mis designaciones». Ve el partido con su fecha, hora, cancha, categoría, grupo, jornada, función y
el estado de la cédula, con el enlace «Ver partido». Solo aparecen las designaciones vigentes de
jornadas `PUBLICADO`; la de una jornada en borrador no se muestra. «Ver también anteriores» quita el
filtro de fecha.

**Resultado obtenido:** **Aprobado.** `DesignacionesIT#misDesignaciones` publica la jornada de un
partido, designa al árbitro como `ASISTENTE` y deja otra designación en una jornada en borrador. Al
entrar con el perfil `ARBITRO` vinculado a la persona, `listarPropias` devuelve **1** elemento con
la designación, la función, el partido, el inicio y la jornada esperados, y `cedula: null`; el rango
que excluye el partido publicado devuelve **0**. `DesignacionControllerTest#listarPropias` comprueba
la envoltura de paginación y el contrato de `GET /mis-designaciones`. `PantallaMisDesignaciones.test.tsx`
comprueba que la lista muestra la fecha, la hora, la cancha, «Libre · Grupo A · Jornada 3», la
función, el estado de la cédula y el enlace a F-25, además de «Cancelado: no se juega» en el partido
cancelado.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-14/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-14/consultas-bd.txt)

**Pruebas automáticas:** `DesignacionesIT#misDesignaciones`, `DesignacionControllerTest#listarPropias`,
`PantallaMisDesignaciones.test.tsx` (lista, filtro y estado vacío).

### CA-4 · Solo los árbitros VALIDADO forman el catálogo y F-24 anticipa las causas (complementario)

Escenario que cierra el criterio 3 de H-11, que anunció esta comprobación para H-14.3.

**Flujo verificado.** En F-24 los candidatos son únicamente árbitros `VALIDADO`; un `PROPUESTO` no
aparece. Los no elegibles se ven deshabilitados con su causa (`VAL-019`, `RF-039` o ya designado) y
la advertencia `RF-022` se muestra en ámbar sin impedir la elección. El encabezado de la jornada
muestra «N de M partidos con árbitro central» y «K de M con asistentes completos».

**Resultado obtenido:** **Aprobado.** `DesignacionesIT#arbitroPropuesto` comprueba que un árbitro
`PROPUESTO` recibe `409 RF-113` desde el trigger y no se inserta. `DesignacionesIT#candidatos`
comprueba que la lista no incluye al `PROPUESTO`, que los no elegibles llevan sus causas y que
`elegible` coincide con lo que acepta el `POST`. `PantallaDesignaciones.test.tsx` comprueba la
leyenda «N designaciones en la temporada», el orden de los candidatos y que la advertencia `RF-022`
no deshabilita la opción.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-14/resultados-por-criterio.txt)

**Pruebas automáticas:** `DesignacionesIT#arbitroPropuesto`, `DesignacionesIT#candidatos`,
`PantallaDesignaciones.test.tsx` (RF-022, carga y orden).

### CA-5 · Sustituir o retirar conserva el historial y deja auditoría (complementario)

**Flujo verificado.** En F-24, «Cambiar» sobre el central exige un motivo de 10 a 500 caracteres y
«Retirar» también pide motivo. La designación anterior no se borra: queda con `vigente = false` y
`motivo_cambio`, y la operación queda en la auditoría de M-05 sin nombres ni CURP. Con dos
asistentes vigentes no se ofrece un tercero.

**Resultado obtenido:** **Aprobado.** `DesignacionesIT#sustituirCentral` comprueba que el primer
central queda `vigente = false` con el motivo recortado, que la nueva designación es `automatica =
false` y que la auditoría registra `SUSTITUIR_ARBITRO`; `DesignacionServiceTest` comprueba que el
detalle lleva `designacionAnteriorId` y el motivo, sin nombres ni CURP. `DesignacionesIT#retirar`
comprueba `RETIRAR_DESIGNACION`, el `409 RF-023` al retirar dos veces y que la fila se conserva.
`DesignacionesIT#tercerAsistente` comprueba el `409 RF-023` del tercer asistente y el avance de la
jornada. En el componente, los casos de DEC-202, DEC-203 y DEC-201 comprueban el motivo obligatorio
en el cliente, el retiro y la ausencia del tercer asistente.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-14/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-14/consultas-bd.txt)

**Pruebas automáticas:** `DesignacionesIT#sustituirCentral`, `#retirar`, `#tercerAsistente`,
`PantallaDesignaciones.test.tsx` (DEC-202, DEC-203 y DEC-201).

## 4. Pruebas de error HTTP de la Definición de Terminado

| Estado | Caso | Código | Prueba |
| --- | --- | --- | --- |
| 401 | Cualquiera de los cinco endpoints sin token | `NO_AUTENTICADO` + `WWW-Authenticate: Bearer` | `DesignacionControllerTest#seguridadListar`, `#seguridadCandidatos`, `#seguridadDesignar`, `#seguridadRetirar` y `#seguridadMisDesignaciones` |
| 401 | Token firmado con otra llave | `NO_AUTENTICADO` | Las mismas pruebas de seguridad |
| 403 | Sin `arbitraje.designacion:EDITAR` al pedir candidatos, designar, sustituir o retirar | `VAL-015` | `DesignacionesIT#sinPermisoDeEditar`, pruebas de seguridad de candidatos, `POST /partidos/{id}/designaciones` y `POST /designaciones/{id}/retiro` |
| 404 | Partido de otra liga o inexistente; designación de otra liga | `NO_ENCONTRADO` | `DesignacionesIT#partidoDeOtraLiga`, `DesignacionServiceTest` (designación de otra liga) y las pruebas de seguridad de `DesignacionControllerTest` |
| 409 | Árbitro ya designado a la misma hora | `RF-039` | `DesignacionesIT#choqueDeHorario` |
| 409 | Árbitro no validado | `RF-113` | `DesignacionesIT#arbitroPropuesto` |
| 409 | Partido no designable, árbitro repetido o tercer asistente | `RF-023` | `DesignacionServiceTest` y `DesignacionesIT#tercerAsistente` |
| 422 | Árbitro del club de un equipo | `VAL-019` | `DesignacionesIT#arbitroDelMismoClub`, `DesignacionControllerTest#designarMismoClub` |
| 422 | Árbitro que no existe en la liga | `REFERENCIA_INVALIDA` | `DesignacionServiceTest` (DEC-201) |
| 400 | Falta `arbitroId` o `funcion`, o el motivo no tiene de 10 a 500 caracteres | `VALIDACION` / `RN-021` | `DesignacionControllerTest#designarSinCampos`, `#designarFuncionInvalida`, `#designarSustitucionSinMotivo` |

## 5. Resultados de la corrida

```text
Backend (PowerShell, una sola línea):
.\mvnw.cmd "-Dtest=DesignacionServiceTest,DesignacionControllerTest" "-Dit.test=DesignacionesIT" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
BUILD SUCCESS (exit 0)
71 pruebas, 0 fallos, 0 errores, 0 omitidas

Frontend (PowerShell, una sola línea):
npm.cmd test -- --reporter=verbose src/features/arbitraje/designaciones/PantallaDesignaciones.test.tsx src/features/arbitraje/mis-designaciones/PantallaMisDesignaciones.test.tsx
2 archivos, 18 pruebas, 0 fallos
```

| Clase o archivo | Pruebas | Fallos |
| --- | ---: | ---: |
| `DesignacionControllerTest` | 36 | 0 |
| `DesignacionServiceTest` | 25 | 0 |
| `DesignacionesIT` | 10 | 0 |
| `PantallaDesignaciones.test.tsx` | 15 | 0 |
| `PantallaMisDesignaciones.test.tsx` | 3 | 0 |
| **Total** | **89** | **0** |

También pasaron `npm run lint` (oxlint, sin errores) y `npm run build` (TypeScript y Vite, sin
errores). No se modificó código en esta subtarea: solo se agregan este documento y sus evidencias.
El detalle está en [`pruebas-automaticas.txt`](evidencias/H-14/pruebas-automaticas.txt).

## 6. Defectos y observaciones

No se encontraron defectos.

- **La corrida no usó navegador.** El entorno no tiene Supabase Auth ni el proyecto compartido; F-24
  y «Mis designaciones» se validaron con sus pruebas de componente y el backend con PostgreSQL real.
  No se alteró el proyecto compartido (DEC-52).
- **Pendientes de validar con la Liga:** el estado designable (`PROGRAMADO` o `REPROGRAMADO`) y el
  tope de dos asistentes son **[PROPUESTA]** de DEC-201; no son defectos.
- **H-11 CA-3 queda cerrado:** el catálogo solo ofrece `VALIDADO` y el trigger rechaza a los demás
  con `RF-113`, como se anunció en la prueba de aceptación de H-11.

## 7. Conclusión

**H-14 cumple sus 3 criterios de aceptación** y las dos comprobaciones complementarias: el árbitro
del club de un equipo se rechaza con `VAL-019`, el choque de horario se explica y se rechaza con
`RF-039`, y una designación válida aparece en «Mis designaciones» cuando la jornada se publica. La
pantalla anticipa las causas, exige motivo para sustituir o retirar y muestra el avance; todo queda
auditado y sin borrar la designación anterior. La historia queda **Aprobada** para el cierre del
Sprint 4.
