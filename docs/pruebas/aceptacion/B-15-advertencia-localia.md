# Prueba de aceptación · B-15 Advertir las reglas de localía y aceptar con motivo

| Campo | Valor |
| --- | --- |
| Historia | B-15 · subtarea B-15.3 · Sprint 3 · 2 SP (claves de Jira por confirmar) |
| Requisitos | RF-120, VAL-027, RN-049, DEC-33, DEC-34, RF-093, VAL-015, UI-009, CU-006 |
| Pantallas | F-22 (programación de jornadas) |
| Implementó | Geovani (B-15.1 #148, B-15.2 #152) |
| Versión probada | `develop` en `bdc1cfc` (incluye B-15.1 `c2d6098` y B-15.2 `33e520c`) |
| Fecha | 2026-11-18 |
| Ejecutó | Israel (prueba cruzada, DEC-50), con un agente de IA en el entorno local |
| Resultado global | **Aprobado** (6 de 6 criterios y 2 complementarios); una observación sin defecto |

La corrida valida de punta a punta la historia B-15. Al programar o ajustar un partido en F-22, el
sistema advierte las dos reglas de localía: la cancha del local (DEC-33, DEC-34) y las últimas
fechas en la unidad deportiva (RN-049). Las advertencias se distinguen de los conflictos duros de
H-12 y solo se aceptan con un motivo de al menos 10 caracteres. El motivo queda en el detalle del
partido y en la auditoría, y la advertencia no impide enviar la jornada. Se usaron el frontend, el
backend y PostgreSQL reales sobre una base local efímera.

Los criterios salen de RF-120, VAL-027 y RN-049, del flujo alterno de CU-006 («Advertencia de
localía → aceptar con motivo»), de las descripciones de B-15.1 y B-15.2 y del contrato de las
convenciones de API §6.7 («Advertencias de localía»). B-15 es una historia B y el backlog no trae
criterios numerados, así que esta prueba los numera de CA-1 a CA-6.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local y efímero (Docker `postgres:17-alpine`), con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las 14 migraciones del repositorio aplicadas, incluida `20261116180000_parametros_localia.sql` |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS con una llave ES256 y responde el inicio de sesión con contraseña de las cuentas ficticias |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 25 y Spring Boot 4.1, con `DB_URL` y `SUPABASE_URL` apuntando a la base y a la autenticación locales |
| Frontend | `npm run dev -- --port 5173` (Vite) con `VITE_SUPABASE_URL` hacia la autenticación local; Vite reenvía `/api` al backend |
| Navegador | Panel de navegador del entorno de pruebas, área visible de 800 × 600 px |
| Automatización | Backend `./mvnw verify`: **BUILD SUCCESS** (2113 + 333 pruebas, 0 fallos). Frontend: lint, **839 pruebas, 0 fallos** y build correctos. Detalle en la sección 5 y en [`pruebas-automaticas.txt`](evidencias/B-15/pruebas-automaticas.txt) |

La prueba corrió sobre una **base local efímera**: **no se alteró el proyecto compartido de
Supabase** y no hizo falta activar una temporada en él. El arnés de autenticación, las cuentas, la
semilla y los guiones de la API viven fuera del repositorio. Aquí solo se versionan este documento y
sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Programador de prueba** | Programador de partidos · LIGA | CA-1 a CA-6: programa, ajusta y acepta advertencias; envía la jornada |
| **Tesorero de prueba** | Tesorero · LIGA | Complementario C-2: 403 sin `programacion.jornada:EDITAR` |

Datos ficticios sembrados en la base local:

| Dato | Situación inicial | Uso en la prueba |
| --- | --- | --- |
| **Temporada local B-15** | `ACTIVA`, parámetros v1 con `fechasFaseRegular` = 18 y `fechasFinalesUnidadDeportiva` = 10 **[DEMO]** | Las últimas fechas son las jornadas 9 a 18 |
| **Libre Primera B-15 · Grupo A** | 6 equipos de 6 clubes: Halcones, Toros, Águilas, Pumas, Leones y Tigres | Partidos de las dos jornadas |
| **Jornada 2** y **Jornada 12** | Borrador, sin partidos | La 2 no es de las últimas fechas; la 12 sí |
| **Morelos 1** y **Morelos 2** | Canchas de la liga en la unidad deportiva | Canchas que no son del local |
| **Campo Halcones** | Cancha propia del club Halcones, externa | CA-1: el local tiene cancha |
| **Morelos 2 en comodato** | Horario de Águilas en `horarios_cancha_club` para la temporada y la categoría | CA-2 y CA-5: cancha en comodato (DEC-33) |
| **Deportivo Las Flores** | Cancha de un tercero, externa | CA-3: fuera de la unidad deportiva |
| **Campo Leones** | Se registra como cancha propia de Leones **después** de programar su partido | CA-4: advertencia que aparece en la fila |

## 3. Escenarios

### CA-1 · Al programar, el local con cancha en otra cancha recibe una advertencia que solo se acepta con motivo

> Dado que el club del equipo local tiene cancha, cuando el Programador registra el partido en otra
> cancha, entonces el sistema lo advierte nombrando las dos canchas (DEC-34) y no guarda el partido
> sin un motivo de al menos 10 caracteres; con el motivo, lo guarda y lo audita.

**Pasos.** En la Jornada 2 se programa **Halcones vs. Toros** en **Morelos 1** el 21/11/2026 a las
10:00. Después se intenta aceptar con el motivo «Remodelo» (8 caracteres) y, por último, con
«Campo Halcones en remodelación hasta diciembre».

**Resultado obtenido.** El alta respondió `409 VAL-027` y el modal mostró la franja «Advertencia de
localía» con el texto «El equipo local «Halcones» tiene cancha («Campo Halcones») y el partido se
programó en «Morelos 1»…» y el botón **Aceptar con motivo** ([captura](evidencias/B-15/CA1-advertencia-cancha-del-local.png)).
La base quedó con 0 partidos. Con «Remodelo», el modal de aceptación mostró «Escribe un motivo de al
menos 10 caracteres.» y no envió la petición ([captura](evidencias/B-15/CA1-motivo-corto.png)).
Con el motivo válido, el partido quedó programado con la franja «Advertencia de localía aceptada» y
el «Motivo registrado» ([captura](evidencias/B-15/CA1-aceptada-con-motivo.png)). La base guarda
`partidos.motivo_localia` y la auditoría registra `REGISTRAR_PARTIDO` y `ACEPTAR_LOCALIA` con el
motivo y `reglas: ["CANCHA_DEL_LOCAL"]` ([auditoría](evidencias/B-15/historial-auditoria.txt)).
Por API, un motivo de 5 caracteres sigue respondiendo `409 VAL-027` y el problema trae
`aceptable: true` y la lista `advertencias` ([respuestas](evidencias/B-15/respuestas-api.txt)).

**Resultado:** ✅ Aprobado.

### CA-2 · Una cancha ofrecida o en comodato también cuenta como cancha del local

> Dado que el club local tiene horario en una cancha en comodato para la temporada y la categoría
> (DEC-33), cuando se programa en otra cancha, entonces se advierte nombrando la cancha en comodato.

**Pasos.** En la Jornada 2 se programa **Águilas vs. Pumas** en **Morelos 1** a las 14:00. Águilas
no es dueño de ninguna cancha, pero tiene **Morelos 2** en comodato.

**Resultado obtenido.** La advertencia dice «El equipo local «Águilas» tiene cancha («Morelos 2») y
el partido se programó en «Morelos 1»…» ([captura](evidencias/B-15/CA2-advertencia-comodato.png)).
Se aceptó con «Morelos 2 ocupada por torneo infantil ese sábado» y quedó auditado. En cambio,
**Leones vs. Tigres** en Deportivo Las Flores (Jornada 2) se guardó con `201` y sin advertencia,
porque en ese momento Leones no tenía cancha.

**Resultado:** ✅ Aprobado.

### CA-3 · En las últimas fechas de la fase regular se advierte una cancha fuera de la unidad deportiva

> Dada una jornada con `numero > fechasFaseRegular − fechasFinalesUnidadDeportiva`, cuando se
> programa en una cancha externa, entonces se advierte (RN-049) y el número de fechas se rotula como
> valor de demostración, no oficial; antes de las últimas fechas no se advierte.

**Pasos.** En la **Jornada 12** (de 18, con 10 últimas fechas) se programa **Leones vs. Tigres** en
**Deportivo Las Flores** el 13/02/2027 a las 10:00. Como contraste, en la **Jornada 2** se programa
el mismo encuentro en la misma cancha.

**Resultado obtenido.** En la Jornada 12 apareció «La jornada 12 está entre las últimas 10 fechas de
la fase regular (de 18) y la cancha «Deportivo Las Flores» está fuera de la unidad deportiva
(RN-049). El número de fechas es un valor de demostración, no oficial.»
([captura](evidencias/B-15/CA3-advertencia-ultimas-fechas.png)). Se aceptó con «La unidad deportiva
cierra por evento municipal» y la auditoría guardó `reglas: ["UNIDAD_DEPORTIVA"]`. En la Jornada 2,
la misma cancha externa se guardó con `201` y sin advertencia ([respuestas](evidencias/B-15/respuestas-api.txt)).

**Resultado:** ✅ Aprobado.

### CA-4 · La advertencia se ve en la fila del partido y se acepta desde ahí sin cambiar el partido

> Dado un partido guardado que no respeta la localía y no tiene motivo, cuando el Programador abre
> la jornada, entonces la fila muestra la advertencia aparte de los conflictos, con **Aceptar con
> motivo**; al aceptarla, solo se guarda el motivo y se audita, sin ajustar equipos, cancha ni hora.

**Pasos.** Después de programar Leones vs. Tigres (Jornada 2, Deportivo Las Flores, 16:00) se
registra **Campo Leones** como cancha propia de Leones y se recarga F-22. Desde la fila se acepta
con «Campo Leones sin alumbrado para el horario de la tarde».

**Resultado obtenido.** La fila mostró la franja ámbar «Advertencia de localía» con «No impide enviar
la jornada: se acepta con un motivo que queda en el detalle.», y la validación de la jornada siguió
en «Sin conflictos», con las advertencias en una sección aparte ([captura](evidencias/B-15/CA4-advertencia-pendiente-en-fila.png)).
El modal de aceptación nombra el partido y la advertencia ([captura](evidencias/B-15/CA4-modal-aceptar-desde-fila.png)).
Al confirmar se envió `PATCH /partidos/{id}` con solo `motivoAceptacion` y respondió `200`. La fila
pasó a «Advertencia de localía aceptada» con el motivo ([captura](evidencias/B-15/CA4-aceptada-desde-fila.png)).
En la base, la cancha y la hora siguieron iguales, y la auditoría registró `ACEPTAR_LOCALIA` sin
`AJUSTAR_PARTIDO`.

**Resultado:** ✅ Aprobado.

### CA-5 · Si un ajuste hace que el partido respete la localía, el motivo se borra

> Dado un partido con advertencia aceptada, cuando se ajusta a la cancha del local, entonces la
> advertencia desaparece y `motivoLocalia` queda vacío; un `PATCH` que solo trae motivo sobre un
> partido que ya respeta la localía no escribe nada.

**Pasos.** Se edita **Águilas vs. Pumas** y se cambia la cancha a **Morelos 2** (su comodato) con el
motivo de ajuste «Se libera Morelos 2: el torneo infantil cambio de sede». Después, por API, se
envía un `PATCH` con solo `motivoAceptacion` al mismo partido.

**Resultado obtenido.** El modal de ajuste avisó «Si el ajuste deja de incumplir la localía, el
motivo se borra.» ([captura](evidencias/B-15/CA5-ajuste-con-motivo-registrado.png)). Tras guardar,
la fila del partido ya no tiene advertencia ([captura](evidencias/B-15/CA5-ajuste-respeta-localia.png)),
`motivo_localia` quedó nulo y la auditoría registró `AJUSTAR_PARTIDO` con el motivo del ajuste. El
`PATCH` posterior respondió `200` sin guardar el motivo ni agregar otro `ACEPTAR_LOCALIA`.

**Resultado:** ✅ Aprobado.

### CA-6 · Las advertencias no bloquean el envío; los conflictos duros no se aceptan con motivo

> Dada una jornada sin conflictos y con advertencias aceptadas, entonces se puede enviar a
> aprobación; un conflicto duro de H-12 sigue rechazándose aunque la petición traiga un motivo de
> localía.

**Pasos.** Se envía la **Jornada 12**, con su advertencia aceptada, a aprobación. Por API se intenta
programar **Toros vs. Pumas** a las 10:00 de la Jornada 2 (Toros ya juega a esa hora) con
`motivoAceptacion`.

**Resultado obtenido.** La validación de la jornada decía «La jornada no tiene conflictos y está
lista para enviarse.». El envío dejó la Jornada 12 en revisión, con «Jornada 12 enviada a
aprobación» ([captura](evidencias/B-15/CA6-jornada-enviada-con-advertencia-aceptada.png)). El
conflicto duro respondió `409 VAL-003` («El equipo «Toros» ya tiene el partido «Halcones vs. Toros»…»)
sin `aceptable`, así que el motivo no lo salta. La verificación de la Jornada 2 respondió
`conflictos` vacío y las advertencias en `advertencias`, cada una con `codigo: "VAL-027"` y «Aceptada
con el motivo de localía registrado» ([respuestas](evidencias/B-15/respuestas-api.txt)).

**Resultado:** ✅ Aprobado.

### Complementarios

| # | Verificación | Resultado |
| --- | --- | --- |
| C-1 | Las dos reglas leen la base, no el código: `fechasFaseRegular` y `fechasFinalesUnidadDeportiva` salen de la versión vigente de los parámetros, y la cancha del local, de `canchas` y `horarios_cancha_club` (CA-2, CA-3, CA-4) | ✅ |
| C-2 | El Tesorero, sin `programacion.jornada:EDITAR`, recibe `403 VAL-015` al programar aunque envíe `motivoAceptacion` (VAL-015, RNF-003) | ✅ |

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 Cancha del local: advertencia y aceptación con motivo | ✅ Aprobado |
| CA-2 Cancha en comodato cuenta como cancha del local | ✅ Aprobado |
| CA-3 Últimas fechas fuera de la unidad deportiva, rotulado no oficial | ✅ Aprobado |
| CA-4 Advertencia en la fila y aceptación sin ajustar | ✅ Aprobado |
| CA-5 El ajuste que respeta la localía borra el motivo | ✅ Aprobado |
| CA-6 No bloquea el envío; los conflictos duros no se aceptan | ✅ Aprobado |
| C-1 Reglas desde la base | ✅ |
| C-2 403 sin permiso | ✅ |

## 5. Pruebas automáticas relacionadas

Verificación ejecutada sobre `bdc1cfc` el 18-nov-2026 (detalle en
[`pruebas-automaticas.txt`](evidencias/B-15/pruebas-automaticas.txt)):

| Subproyecto | Comando | Resultado |
| --- | --- | --- |
| Backend | `./mvnw verify` | **BUILD SUCCESS**: 2113 pruebas unitarias y de controlador, 0 fallos; 333 de integración, 0 fallos, 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS). Incluye `ValidadorLocaliaTest` (5), `JornadaServiceTest` (47), `ProgramacionControllerTest` (56), `ProgramacionIT` (30) y `ParametrosServiceTest` (38) |
| Frontend | `npm run lint` | Sin errores |
| Frontend | `npx vitest run --maxWorkers=2` | **90 archivos y 839 pruebas, 0 fallos**, incluidas las 4 de B-15.2 en `PantallaProgramacion.test.tsx` |
| Frontend | `npm run build` | Correcto |

Con el paralelismo por omisión y la máquina cargada, `npm test` tuvo fallas por tiempo de espera
(5 s) en archivos de varios módulos, no solo de programación. Con 2 workers pasan todas, así que no
se registran como defecto de B-15.

| Criterio | Pruebas que ya lo cubren |
| --- | --- |
| CA-1 | `ValidadorLocaliaTest` «DEC-34: el local con cancha programado en otra recibe la advertencia que nombra ambas»; `JornadaServiceTest` «VAL-027: con advertencia de localía y sin motivo se rechaza con 409 y la explicación» y «RF-120: con motivo de localía el partido se guarda con él y la aceptación se audita»; `ProgramacionIT` «VAL-027, DEC-34: el local con cancha en otra cancha se rechaza sin motivo; con motivo se guarda y audita»; `PantallaProgramacion.test.tsx` «B-15.2: si el alta devuelve VAL-027, permite aceptar la advertencia con motivo» |
| CA-2 | `ValidadorLocaliaTest` «DEC-34: el local que juega en su cancha o que no tiene cancha no recibe advertencia»; la consulta de comodato (`LocaliaRepository.canchasDelClub`) solo se ejercita contra PostgreSQL en esta corrida manual |
| CA-3 | `ValidadorLocaliaTest` «RN-049: cancha externa en las últimas 10 fechas de la fase regular se advierte como no oficial», «RN-049: antes de las últimas fechas, en la unidad deportiva o sin total definido no se advierte» y «VAL-027: las dos reglas se advierten juntas cuando ambas se incumplen»; `ProgramacionIT` «VAL-027, RN-049: cancha externa en las últimas 10 fechas se advierte; antes, no»; `ParametrosServiceTest` (herencia de los parámetros y 10 **[DEMO]**) |
| CA-4 | `JornadaServiceTest` «B-15.2: aceptar la localía desde la fila guarda solo el motivo y audita ACEPTAR_LOCALIA» y «B-15.2: aceptar sin un motivo de 10 caracteres responde 409 VAL-027 y no escribe»; `ProgramacionIT` «B-15.2: aceptar la localía desde la fila guarda el motivo sin ajustar el partido y lo audita»; `PantallaProgramacion.test.tsx` «B-15.2: muestra la advertencia de localía en la fila y la acepta con motivo» y «B-15.2: el motivo aceptado se ve en el detalle del partido» |
| CA-5 | `JornadaServiceTest` «RF-120: el ajuste conserva el motivo de localía guardado si la advertencia sigue» y «B-15.2: si el partido ya respeta la localía, el motivo de aceptación se ignora»; `PantallaProgramacion.test.tsx` «B-15.2: un ajuste con VAL-027 conserva el motivo del ajuste y añade el de localía» |
| CA-6 | `JornadaServiceTest` «VAL-027: la verificación lista las advertencias de localía sin escribir»; `ProgramacionControllerTest` «VAL-027: el 409 de localía es aceptable y el reenvío lleva motivoAceptacion al servicio» |

## 6. Defectos y observaciones

**Defectos:** ninguno.

**O-1 · El motivo es del partido, no de cada regla.** En la Jornada 12, Leones vs. Tigres se aceptó
por RN-049. Al registrarse después Campo Leones, la verificación muestra también la regla DEC-34
como «Aceptada con el motivo de localía registrado», aunque el motivo se escribió para la otra regla.
El comportamiento coincide con el contrato (§6.7: un solo `motivoLocalia` por partido, que se
conserva mientras haya advertencia, **[PROPUESTA]**), así que no es un defecto. Se deja anotado para
que el equipo decida si una regla nueva debe volver a pedir motivo cuando llegue el reglamento.

## 7. Conclusión

B-15 cumple sus criterios: las advertencias de localía se explican con los nombres de los equipos y
las canchas, se distinguen de los conflictos duros, solo se aceptan con motivo, quedan en el detalle
del partido y en la auditoría, y no bloquean el envío de la jornada. **B-15 puede cerrarse.**
