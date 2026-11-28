# Prueba de aceptación · H-34 Asignar automáticamente al árbitro central

| Campo | Valor |
| --- | --- |
| Historia | H-34 · subtarea H-34.3 · Sprint 4 · Must · 2 SP |
| Requisitos | RF-023, RF-039, RF-115, VAL-019 (y RF-022, RF-041, RF-093, VAL-015 de apoyo) |
| Decisiones | DEC-182, DEC-213 a DEC-220 · ADR-0015 |
| API | `POST /api/v1/fechas/{id}/asignacion` (designa el central) y `POST /api/v1/fechas/{id}/verificacion` (explica «Sin árbitro»); sin endpoints nuevos |
| Implementó | Leonardo (H-34.1 #205 y H-34.2 #206) |
| Versión probada | `develop` en `da3b69d` (27-nov-2026) |
| Fecha | 2026-11-27 |
| Ejecutó | Agente de IA (Claude Code) en el entorno local, para Geovani (prueba cruzada, DEC-50) |
| Resultado global | **Aprobado** (6 de 6 criterios; pruebas de error 401/403/404 incluidas) |

La corrida valida que, al asignar una fecha (H-33), cada partido sin central vigente recibe un
árbitro central `VALIDADO` elegido de forma equitativa, contra PostgreSQL 17 real y la cadena de
seguridad del backend. El componente `DesignacionAutomatica` (puerto `DesignadorArbitroCentral`)
bloquea partidos y árbitros, elige con `SelectorArbitroCentral` y audita cada designación dentro de
la transacción de la asignación. La prueba no usó el proyecto compartido de Supabase.

H-34 es de backend: el «Sin árbitro» y su causa se muestran en F-22 (H-35.2, `BloqueFecha.tsx`), y
el cambio manual del central es la pantalla F-24 de H-14.2. Por eso la aceptación se ejecuta por la
API y la base de datos, sin navegador.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 efímero de Testcontainers (`postgres:17-alpine`), con la simulación de Supabase [`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql) y las 16 migraciones del repositorio |
| Backend | Java 25.0.5.1, Spring Boot 4.1.1 y Maven 3.9.11; `./mvnw verify` filtrado a las clases de H-34 y de la designación manual de H-14 |
| Autenticación | `AsignacionFechaControllerTest` y `DesignacionControllerTest` usan la cadena de seguridad real y tokens ES256 de `SeguridadPrueba`; las integraciones usan el contexto de seguridad con perfiles PROGRAMADOR y COMISION_ARBITRAJE de la semilla |
| Interfaz | No aplica: F-22 es H-35 y F-24 es H-14.2 |
| Alcance | Base local efímera; no se alteró el proyecto compartido de Supabase ni se usaron datos personales |

## 2. Datos de prueba

| Escenario | Datos | Criterios |
| --- | --- | --- |
| **Fecha completa** (`AsignacionFechaArbitrosIT`) | Temporada con «Libre» (domingo) y «Veteranos» (sábado), un grupo de 4 equipos cada una, 2 canchas de la unidad deportiva y el calendario generado. Árbitros `VALIDADO`: 3 de un club ajeno y 1 del club de un equipo; variantes con un solo árbitro y horario de juego hasta las 10:00 (partidos simultáneos), sin árbitros, con un central manual de la Comisión y con un rol Programador sin `AUTOMATICA`. Los árbitros ajenos a la prueba se aíslan y se restauran. | CA-1 a CA-6 |
| **Componente** (`DesignacionAutomaticaIT`) | 5 partidos (3 a la misma hora y 2 tres horas después) con 3 árbitros; un árbitro del club local como único validado; un árbitro con carga 3 frente a otro con carga 0 en partidos simultáneos; un central manual previo. | CA-1, CA-2, CA-4 y CA-5 |
| **Dominio** (`SelectorArbitroCentralTest`) | Candidatos con distinta carga, último partido, advertencia `RF-022`, zona horaria e id; listas invertidas y barajadas con 4 semillas; 6 combinaciones de causas. | CA-2 y CA-3 |
| **Contrato HTTP** (`AsignacionFechaControllerTest`) | Doble del servicio con 4 asignados, 3 centrales y 1 sin árbitro. | CA-3 y errores 401/403/404 |

Los clubes, equipos, canchas, árbitros y cuentas son ficticios y se crean dentro de la base
efímera; los identificadores son UUID generados por la prueba.

## 3. Escenarios

### CA-1 · Un árbitro VALIDADO, ajeno a los clubes del partido y sin traslape

> Dado un partido sin central vigente, cuando se asigna la fecha, entonces recibe un árbitro
> VALIDADO que no es del club de ningún equipo (VAL-019) y sin otro partido traslapado (RF-039).

**Resultado obtenido:** **Aprobado.** `PENDIENTES` devolvió totales `(asignados 4, noAsignados 0,
cancelados 0, centrales 4, sinArbitro 0)`: los **4 partidos** recibieron central y la respuesta ya
trae el árbitro de cada uno con `designacionAutomatica = true`. En la base hay **4 designaciones
vigentes**, una por partido. El árbitro del club de un equipo tiene **0** designaciones en partidos
de su club y no hay **ningún** árbitro en dos partidos traslapados. Con 3 partidos simultáneos, cada
uno llevó un central distinto; un árbitro que ya pita a esa hora en la misma corrida se descartó
aunque tuviera menor carga. Si el único validado es del club local, el partido queda sin central y,
al agregar uno ajeno, se designa ese. Tras asignar, la verificación de la fecha ya no reporta
`RF-023`.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-34/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-34/consultas-bd.txt)

**Pruebas automáticas:** `AsignacionFechaArbitrosIT#designaTodos`,
`DesignacionAutomaticaIT#repartoEquitativo`, `#arbitroDelClubLocal`, `#choqueDeLaMismaCorrida` y
`SelectorArbitroCentralTest#noElegiblesNunca`.

### CA-2 · Menor carga en la temporada y desempate determinista

> Se elige al árbitro con menos designaciones vigentes en la temporada; el desempate es
> determinista.

**Resultado obtenido:** **Aprobado.** El orden de DEC-214 es: sin advertencia `RF-022`
**[PROPUESTA]** → menor carga → último partido más antiguo (quien nunca ha sido designado va
primero) → id por texto (DEC-185). Con 5 partidos y 3 árbitros, la diferencia entre la carga
máxima y la mínima fue **≤ 1** y solo esos 3 árbitros recibieron partidos; en la fecha de 4 partidos
con 4 árbitros, también **≤ 1**. La carga se vuelve a consultar por partido dentro de la misma
transacción, así que cuenta las designaciones de la corrida. El resultado fue el mismo con la lista
invertida y con 4 barajadas, y al regenerar con los mismos datos a cada horario le tocaron **los
mismos árbitros**.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-34/resultados-por-criterio.txt)

**Pruebas automáticas:** `SelectorArbitroCentralTest#menorCarga`, `#empateDeCargaPorUltimoPartido`,
`#ultimoPartidoComoInstante`, `#nuncaDesignadoPrimero`, `#empateTotalPorTexto`,
`#independienteDelOrden` (×4), `DesignacionAutomaticaTest#consultaPorPartido`, `#porInicio`,
`DesignacionAutomaticaIT#repartoEquitativo` y `AsignacionFechaArbitrosIT#regenerar`.

### CA-3 · «Sin árbitro» con la causa, como advertencia que no bloquea el envío

> Sin candidato, el partido queda «Sin árbitro» con la causa, como advertencia que no bloquea el
> envío.

**Resultado obtenido:** **Aprobado.** Con un solo árbitro y 2 partidos a la misma hora, los totales
fueron `(2, 0, 0, 1, 1)` y `sinArbitro[0]` llevó `codigo = RF-023` y la causa «El único árbitro
validado no puede pitarlo: 1 ya pita a esa hora (RF-039).». Sin árbitros validados, los 4 partidos
quedaron con «No hay árbitros validados en la liga.». La verificación devolvió **un** aviso `RF-023`
en `advertencias` (no en `conflictos`): «Sin árbitro central: ‹Local› vs ‹Visitante›. ‹causa› La
Comisión de Arbitraje lo designa en F-24.». El envío a aprobación dejó la fecha en **REVISION** con
ese partido sin árbitro (la prueba revierte la transacción para seguir). Al agregar un segundo
árbitro y repetir `PENDIENTES`, el partido que faltaba recibió central `(0, 0, 0, 1, 0)` y el otro
conservó el suyo. Un partido sin candidato no inserta ni audita nada.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-34/resultados-por-criterio.txt),
[`respuestas-api.txt`](evidencias/H-34/respuestas-api.txt)

**Pruebas automáticas:** `AsignacionFechaArbitrosIT#unSoloArbitro`, `#respetaCentralManual`,
`DesignacionAutomaticaTest#sinCandidato`, `SelectorArbitroCentralTest#sinArbitros`,
`#causasSinCandidato` (×6), `FechaServiceTest#causaDeSinArbitro` y
`AsignacionFechaControllerTest#asignar`.

### CA-4 · No se asignan asistentes

> No se asignan asistentes.

**Resultado obtenido:** **Aprobado.** Por cada partido hay **exactamente una** designación vigente y
es `CENTRAL` (4 para 4 partidos en la fecha y 5 para 5 en el componente); no aparece ninguna fila
`ASISTENTE`. El componente solo inserta `FuncionArbitral.CENTRAL`, y un partido que solo tiene
asistentes vigentes sí recibe central sin que se toquen sus asistentes.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-34/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-34/consultas-bd.txt)

**Pruebas automáticas:** `AsignacionFechaArbitrosIT#designaTodos`,
`DesignacionAutomaticaIT#repartoEquitativo`, `DesignacionAutomaticaTest#omiteConCentral` e
`#insertaYAudita`.

### CA-5 · Marcada como automática, con `AUTOMATICA`; el cambio manual sigue en F-24

> La designación queda marcada como automática y requiere `arbitraje.designacion:AUTOMATICA`; el
> cambio manual sigue en F-24 (`arbitraje.designacion:EDITAR`).

**Resultado obtenido:** **Aprobado.** Cada central automático quedó con `automatica = true` y
`designado_por` del Programador, y dejó **una** auditoría `M-05 / DESIGNAR_ARBITRO` con
`detalle.automatica = true`, sin nombres ni CURP; la operación registró además `GENERAR_FECHA` con
`designacionAutomatica`, `centrales` y `sinArbitro`. El Programador (con `AUTOMATICA` y **sin**
`designacion:EDITAR`) designó; la Comisión de Arbitraje (sin `AUTOMATICA`) recibió **403 VAL-015** y
no se insertó nada. Sin `AUTOMATICA` dentro de la asignación, la fecha se asignó igual
`(4, 0, 0, 0, 0)`, sin designaciones, con `designacionAutomatica: false` y la advertencia
**[PROPUESTA]** de DEC-218. Un central manual de la Comisión (`automatica = false`) se respetó al
repetir `PENDIENTES` y los otros 3 partidos recibieron central automático. El cambio manual (designar,
sustituir con motivo y retirar) sigue exigiendo `arbitraje.designacion:EDITAR` en H-14.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-34/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-34/consultas-bd.txt),
[`respuestas-api.txt`](evidencias/H-34/respuestas-api.txt)

**Pruebas automáticas:** `DesignacionAutomaticaIT#permisos`, `DesignacionAutomaticaTest#sinPermiso`,
`#insertaYAudita`, `AsignacionFechaArbitrosIT#designaTodos`, `#sinPermisoDeDesignacionAutomatica`,
`#respetaCentralManual`, `AsignacionFechaServiceTest` (DEC-218), `DesignacionServiceTest` y
`DesignacionControllerTest`.

### CA-6 · Al regenerar, las designaciones de los partidos cancelados quedan no vigentes

> Al regenerar la fecha, las designaciones de los partidos cancelados quedan no vigentes.

**Resultado obtenido:** **Aprobado.** Tras una primera asignación `(4, 0, 0, 4, 0)`, `REGENERAR`
con el motivo «Se regeneró la fecha por el torneo relámpago» devolvió `(4, 0, 4, 4, 0)`. Las **4**
designaciones anteriores quedaron con `vigente = false` y `motivo_cambio` igual al motivo; los 4
partidos nuevos recibieron 4 designaciones vigentes automáticas. En `REGENERAR` el puerto solo se
llama con los partidos insertados: a los cancelados no se les designa.

**Evidencia:** [`resultados-por-criterio.txt`](evidencias/H-34/resultados-por-criterio.txt),
[`consultas-bd.txt`](evidencias/H-34/consultas-bd.txt)

**Pruebas automáticas:** `AsignacionFechaArbitrosIT#regenerar`, `AsignacionFechaServiceTest`
(«DEC-217: REGENERAR pide central solo para los insertados; los cancelados no») y
`AsignacionFechaIT#asignarYRegenerar`.

## 4. Pruebas de error HTTP de la Definición de Terminado

| Estado | Caso | Código | Prueba |
| --- | --- | --- | --- |
| 401 | Asignar la fecha sin token | `NO_AUTENTICADO` + `WWW-Authenticate: Bearer` | `AsignacionFechaControllerTest#seguridadAsignar()[1]` |
| 401 | Token firmado con otra llave | `NO_AUTENTICADO` | `seguridadAsignar()[2]` |
| 403 | Sin `programacion.jornada:EDITAR` | `VAL-015` | `seguridadAsignar()[3]` |
| 403 | Designación automática sin `arbitraje.designacion:AUTOMATICA` | `VAL-015` | `DesignacionAutomaticaTest#sinPermiso`, `DesignacionAutomaticaIT#permisos` |
| 404 | Fecha de otra liga, fuera de ámbito o inexistente | `NO_ENCONTRADO` | `seguridadAsignar()[4]`, `[5]` y `fechaInexistente` |
| 401/403/404 | Cambio manual en F-24 (designar y retirar) | `NO_AUTENTICADO` / `VAL-015` / `NO_ENCONTRADO` | `DesignacionControllerTest` (RNF-003) |
| 400 | Sustituir al central sin motivo | `RN-021` | `DesignacionControllerTest`, `DesignacionServiceTest` |

Dentro de la asignación de la fecha, la falta de `AUTOMATICA` no es un error: se consulta con
`Autorizacion.permite` y produce la advertencia de DEC-218 sin revertir la transacción.

## 5. Resultados de la corrida

```text
./mvnw -Dtest=SelectorArbitroCentralTest,DesignacionAutomaticaTest,AsignacionFechaServiceTest,\
AsignacionFechaControllerTest,FechaServiceTest,DesignacionControllerTest,DesignacionServiceTest \
       -Dit.test=DesignacionAutomaticaIT,AsignacionFechaArbitrosIT,AsignacionFechaIT \
       -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify
BUILD SUCCESS (exit 0)
157 pruebas, 0 fallos, 0 errores, 0 omitidas
```

| Clase | Pruebas | Fallos |
| --- | ---: | ---: |
| `SelectorArbitroCentralTest` | 20 | 0 |
| `DesignacionAutomaticaTest` | 16 | 0 |
| `AsignacionFechaServiceTest` | 24 | 0 |
| `AsignacionFechaControllerTest` | 12 | 0 |
| `FechaServiceTest` | 13 | 0 |
| `DesignacionServiceTest` | 25 | 0 |
| `DesignacionControllerTest` | 36 | 0 |
| `DesignacionAutomaticaIT` | 5 | 0 |
| `AsignacionFechaArbitrosIT` | 5 | 0 |
| `AsignacionFechaIT` | 1 | 0 |
| **Total** | **157** | **0** |

La compilación de todo el backend (principal y pruebas) se hizo dentro del ciclo `verify`; la
ejecución se filtró a las clases que cubren H-34 y a las de la designación manual de H-14. No se
modificó código ni el frontend, por lo que no se ejecutó su suite. El detalle está en
[`pruebas-automaticas.txt`](evidencias/H-34/pruebas-automaticas.txt).

## 6. Defectos y observaciones

No se encontraron defectos.

- **H-34 no tiene pantalla propia.** «Sin árbitro» con su causa se ve en F-22 (H-35.2) y el cambio
  manual en F-24 (H-14.2); esta corrida no reejecutó sus pruebas de frontend.
- **Textos [PROPUESTA]** pendientes de validar con la Liga: la preferencia por candidatos sin
  `RF-022` (DEC-214), la advertencia sin `AUTOMATICA` (DEC-218), la del central con `RF-022`
  (DEC-219) y la causa con elegibles (DEC-220).
- **La corrida usa una base efímera** y no altera el proyecto compartido de Supabase (DEC-52).

## 7. Conclusión

**H-34 cumple sus 6 criterios de aceptación** y las pruebas de error de la Definición de Terminado
(401/403/404, además de 400). Cada partido sin central recibe un árbitro validado, ajeno a sus clubes
y sin traslape, con la carga repartida y desempate determinista; lo que no tiene candidato queda
«Sin árbitro» con su causa sin bloquear el envío; no se asignan asistentes; la designación queda
marcada y auditada como automática bajo su propio permiso, y al regenerar las designaciones de los
partidos cancelados dejan de estar vigentes. La historia queda **Aprobada** para el cierre del
Sprint 4.
