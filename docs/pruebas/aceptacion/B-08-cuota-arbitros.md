# Prueba de aceptación · B-08 Verificar la cuota de árbitros por club

| Campo | Valor |
| --- | --- |
| Historia | B-08 · subtarea B-08.3 · Sprint 3 · Could · Low · 1 SP (claves de Jira por confirmar) |
| Requisitos | RF-114, RN-042, DEC-17, DEC-72, VAL-024, UI-007 |
| Pantallas | F-20 (árbitros: vista del club y vista de la Comisión) |
| Implementó | Geovani (B-08.1 #143, B-08.2 #146) |
| Versión probada | `develop` en `04e9949` (incluye B-08.1 `26db96f` y B-08.2 `4bd6ddb`) |
| Fecha | 2026-11-19 |
| Ejecutó | Israel (prueba cruzada, DEC-50), con un agente de IA en el entorno local |
| Resultado global | **Aprobado** (5 de 5 criterios, V-1, V-2 y 1 complementario); una observación sin defecto |

La corrida valida de punta a punta la historia B-08. La Directiva ve en F-20 los árbitros
validados de su club frente a los requeridos (`ceil(equiposActivos / equiposPorArbitro)`), si cumple
y cuántos faltan, con el rótulo «No oficial». La Comisión de Arbitraje ve la insignia de cada club y
filtra por cumplimiento. Solo cuentan los árbitros `VALIDADO`, el divisor sale de la versión vigente
de los parámetros y la cuota solo informa: no bloquea ni se audita. Se usaron el frontend, el
backend y PostgreSQL reales sobre una base local efímera.

Los criterios salen de RF-114 («indicar si un club presenta al menos un árbitro por cada cuatro
equipos, con redondeo hacia arriba»), de las descripciones de B-08.1 y B-08.2 y del contrato de las
convenciones de API §6.5 («Cuota de árbitros del club»). B-08 es una historia B y no trae criterios
numerados en el backlog, así que esta prueba los numera de CA-1 a CA-5. V-1 y V-2 no son criterios,
pero se verifican en la misma corrida.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local y efímero (Docker `postgres:17-alpine`), con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las 14 migraciones del repositorio aplicadas, incluida `parametros_temporada.equipos_por_arbitro` (4 **[DEMO]**) |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS con una llave ES256 y responde el inicio de sesión con contraseña de las cuentas ficticias |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 25 y Spring Boot 4.1, con `DB_URL` y `SUPABASE_URL` apuntando a la base y a la autenticación locales |
| Frontend | `npm run dev -- --port 5173` (Vite) con `VITE_SUPABASE_URL` hacia la autenticación local; Vite reenvía `/api` al backend |
| Navegador | Panel de navegador del entorno de pruebas, ventana emulada de 1024 × 768 px |
| Automatización | Backend `./mvnw verify`: **BUILD SUCCESS** (2113 + 333 pruebas, 0 fallos). Frontend: lint, **839 pruebas, 0 fallos** y build correctos. Detalle en la sección 5 y en [`pruebas-automaticas.txt`](evidencias/B-08/pruebas-automaticas.txt) |

**Endpoint probado.** `GET /api/v1/clubes/{id}/cuota-arbitros?temporadaId=` responde
`{ club, temporada, equiposActivos, equiposPorArbitro, cuotaArbitros: { requeridos, validados,
cumple } }`. Sin `temporadaId` usa la temporada `ACTIVA` de la liga, que es como lo pide F-20.

**Por qué en una base local.** En el Supabase compartido no hay ninguna temporada `ACTIVA`, y la
cuota de F-20 la necesita. Activar la «Temporada 2026–2027» no tiene vuelta atrás (RF-003), y las
cuentas de prueba del compartido aún no estaban disponibles. Igual que en H-12 y B-15, la corrida
usa una base local efímera: **no se alteró el proyecto compartido de Supabase**. La temporada local
empieza en `CONFIGURACION` para ejecutar V-2 y después se activa. El arnés de autenticación, las
cuentas, la semilla y los guiones de la API viven fuera del repositorio. Aquí solo se versionan este
documento y sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Comisión de prueba** | Comisión de Arbitraje · LIGA | CA-2, CA-3, CA-5, V-1 y V-2: insignias, filtro y validación |
| **Directiva de prueba** | Directiva de club · CLUB (Club Halcones de Pátzcuaro) | CA-1, CA-2, CA-4, C-1 y V-2: tarjeta de su club y ámbito del endpoint |

Datos ficticios sembrados en la base local:

| Dato | Situación inicial | Uso en la prueba |
| --- | --- | --- |
| **Temporada local B-08** | `CONFIGURACION`, parámetros v1 con `equiposPorArbitro` = 4 **[DEMO]**; se activa después de V-2 | Temporada de la cuota |
| **Libre Primera B-08 · Grupo A** | Grupo de todos los equipos | — |
| **Club Halcones de Pátzcuaro** | 5 equipos `ACTIVO` y 1 en `BAJA`; árbitros Uno `VALIDADO`, Dos `RECHAZADO` y Tres `PROPUESTO` | Requiere `ceil(5/4)` = 2 y solo tiene 1 validado |
| **Club Deportivo Uruapan** | 4 equipos `ACTIVO`; árbitro Cuatro `VALIDADO` | Requiere 1 y cumple; club ajeno a la Directiva |
| **Club Atlético Zamora** | Sin equipos; árbitro Cinco `PROPUESTO` | V-1: sin cuota que cumplir |

Las personas de los árbitros son ficticias, con CURP de formato válido que no corresponden a nadie.

## 3. Escenarios

### V-2 · Sin temporada activa, la cuota explica la causa (`409 RF-114`)

> Se ejecuta antes de activar la temporada. Sin temporada `ACTIVA` en la liga, la consulta sin
> `temporadaId` responde `409 RF-114`; la Directiva ve la causa con «Reintentar» y la Comisión ve
> «Sin dato» junto al club, sin que la pantalla de árbitros deje de funcionar.

**Resultado obtenido.** Por API, la Directiva recibió `409` con `codigo: "RF-114"` y «La liga no tiene
una temporada activa: indica la temporada de la cuota.» ([respuestas](evidencias/B-08/respuestas-api.txt)).
En F-20, la Comisión vio la lista de árbitros con la insignia **Sin dato** en cada club y el aviso
«No se pudo consultar la cuota de los clubes de esta página.»; las dos consultas de la página
respondieron `409` ([captura](evidencias/B-08/V2-comision-sin-dato.png)). La Directiva vio en la
tarjeta «Cuota de árbitros» el mismo mensaje del backend y el botón **Reintentar**
([captura](evidencias/B-08/V2-directiva-reintentar.png)).

**Resultado:** ✅ Aprobado.

### CA-1 · La Directiva ve la cuota de su club con el parámetro vigente de la temporada

> Dado un club con equipos activos en la temporada activa, cuando su Directiva abre F-20, entonces
> ve los árbitros validados frente a los requeridos, si cumple o no y, si no cumple, cuántos faltan;
> el valor se rotula «No oficial».

**Pasos.** Se activa la Temporada local B-08 y la Directiva pulsa **Reintentar** en la tarjeta.

**Resultado obtenido.** La tarjeta mostró «Club Halcones de Pátzcuaro · Temporada local B-08», la
insignia **No oficial**, «La Liga pide 1 árbitro validado por cada 4 equipos activos del club, con
redondeo hacia arriba.», «Árbitros validados: **1 de 2 requeridos**», la insignia **No cumple** y «No
cumple la cuota: falta 1 árbitro validado.» ([captura](evidencias/B-08/CA1-directiva-no-cumple.png)).
La API respondió `equiposActivos: 5` (el equipo en `BAJA` no cuenta), `equiposPorArbitro: 4`,
`requeridos: 2`, `validados: 1` y `cumple: false`.

**Resultado:** ✅ Aprobado.

### CA-2 · Solo los árbitros validados cubren la cuota

> Dado un árbitro `PROPUESTO` del club, cuando la Comisión lo valida, entonces el indicador suma un
> validado; los `PROPUESTO` y `RECHAZADO` no cuentan.

**Pasos.** Con Halcones en «1 de 2» (Uno validado, Dos rechazado y Tres propuesto), la Comisión
valida a **Árbitro Tres Prueba** desde la pestaña «Por validar».

**Resultado obtenido.** Antes de validar, el rechazado y el propuesto no contaban (1 validado). Tras
validar, la pestaña «Validados» muestra a Halcones con **Cumple** y «2 de 2 árbitros validados»
([captura](evidencias/B-08/CA2-comision-cumple-tras-validar.png)). La API de la Directiva respondió
`requeridos: 2`, `validados: 2` y `cumple: true`.

**Resultado:** ✅ Aprobado.

### CA-3 · La Comisión ve si cada club cumple y filtra por cumplimiento

> Dada la vista de la Comisión, entonces cada árbitro muestra la insignia «Cumple», «No cumple» o
> «Sin cuota» de su club, y el filtro «Cumple cuota» deja solo los clubes que cumplen o los que no.

**Resultado obtenido.** En «Por validar», Halcones aparece con **No cumple** («1 de 2 árbitros
validados») y Atlético Zamora con **Sin cuota** ([captura](evidencias/B-08/CA3-comision-insignias-y-V1-sin-cuota.png)).
En «Validados», Deportivo Uruapan aparece con **Cumple** («1 de 1»). Con «Solo los que cumplen» la
tabla deja solo a Uruapan y con «Solo los que no cumplen», solo a Halcones; las dos indican «Mostrando
1 de 2 árbitros de esta página.» ([cumplen](evidencias/B-08/CA3-filtro-solo-cumplen.png),
[no cumplen](evidencias/B-08/CA3-filtro-solo-no-cumplen.png)). El panel «Cuota de árbitros por club»
lleva el rótulo **No oficial** y aclara que la cuota no bloquea la validación ni la designación.

**Resultado:** ✅ Aprobado.

### CA-4 · El ámbito y los errores del endpoint

> La Directiva no consulta la cuota de otro club (`404` igual que uno inexistente, VAL-024); una
> temporada inexistente responde `404`; un identificador mal formado responde `400 VALIDACION`.

**Resultado obtenido** ([respuestas](evidencias/B-08/respuestas-api.txt)):

| Consulta de la Directiva | Respuesta |
| --- | --- |
| Su club (Halcones) | `200` con su cuota |
| Club Deportivo Uruapan (ajeno) | `404 NO_ENCONTRADO` «El recurso no existe o no tienes acceso a él.» |
| Club inexistente | `404 NO_ENCONTRADO`, el mismo mensaje: no revela si el club existe |
| Su club con `temporadaId` inexistente | `404 NO_ENCONTRADO` |
| `clubId` = `no-es-uuid` | `400 VALIDACION` |
| `temporadaId` = `no-es-uuid` | `400 VALIDACION` |

La Comisión, con ámbito de liga, consultó a Uruapan con `temporadaId` explícito y recibió `200`
(`requeridos: 1`, `validados: 1`, `cumple: true`).

**Resultado:** ✅ Aprobado.

### CA-5 · La cuota solo informa: no bloquea ni se audita

> Un club que no cumple puede seguir presentando árbitros y la Comisión puede validarlos; consultar
> la cuota no deja eventos en la auditoría.

**Resultado obtenido.** La Comisión validó a Árbitro Tres mientras Halcones estaba en **No cumple**,
sin ningún aviso que lo impidiera ([captura](evidencias/B-08/CA5-validar-con-club-que-no-cumple.png)),
y la Directiva conserva el botón **Presentar árbitro**. La auditoría empezó vacía; tras más de 20
consultas de la cuota (API y F-20) tiene un solo evento, `VALIDAR_ARBITRO` de la Comisión
([auditoría](evidencias/B-08/historial-auditoria.txt)).

**Resultado:** ✅ Aprobado.

### V-1 · Un club sin equipos activos no tiene cuota que cumplir

**Resultado obtenido.** En la vista de la Comisión, Club Atlético Zamora (sin equipos, con un árbitro
propuesto) aparece con la insignia **Sin cuota** y «El club no tiene equipos activos»
([captura](evidencias/B-08/CA3-comision-insignias-y-V1-sin-cuota.png)). La tarjeta de la Directiva
para ese caso («El club no tiene equipos activos en esta temporada…») la cubre la prueba automática
«RF-114: un club sin equipos activos muestra el estado vacío de la cuota».

**Resultado:** ✅ Aprobado.

### Complementario

| # | Verificación | Resultado |
| --- | --- | --- |
| C-1 | **El divisor sale de la versión vigente de los parámetros (DEC-72).** Se registra en la base local la versión 2 de los parámetros con `equipos_por_arbitro` = 2. Sin cambiar código, la cuota de Halcones pasa a `equiposPorArbitro: 2`, `requeridos: 3` (`ceil(5/2)`), `validados: 2`, `cumple: false`, y la tarjeta dice «por cada 2 equipos activos» y «2 de 3 requeridos» ([captura](evidencias/B-08/C1-parametro-vigente-2-por-arbitro.png)) | ✅ |

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 La Directiva ve la cuota de su club | ✅ Aprobado |
| CA-2 Solo los validados cubren la cuota | ✅ Aprobado |
| CA-3 La Comisión ve y filtra el cumplimiento | ✅ Aprobado |
| CA-4 Ámbito y errores del endpoint | ✅ Aprobado |
| CA-5 Solo informa: no bloquea ni se audita | ✅ Aprobado |
| V-1 Club sin equipos activos | ✅ Aprobado |
| V-2 Sin temporada activa → `409 RF-114` explicado | ✅ Aprobado |
| C-1 Parámetro vigente (DEC-72) | ✅ |

## 5. Pruebas automáticas relacionadas

Verificación ejecutada sobre `04e9949` el 19-nov-2026 (detalle en
[`pruebas-automaticas.txt`](evidencias/B-08/pruebas-automaticas.txt)):

| Subproyecto | Comando | Resultado |
| --- | --- | --- |
| Backend | `./mvnw verify` | **BUILD SUCCESS**: 2113 pruebas unitarias y de controlador, 0 fallos; 333 de integración, 0 fallos, 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS). Incluye `ArbitroServiceTest` (30), `CuotaArbitrosControllerTest` (11), `ArbitrosIT` (7) y `ParametrosServiceTest` (38) |
| Frontend | `npm run lint` | Sin errores |
| Frontend | `npx vitest run --maxWorkers=2` | **90 archivos y 839 pruebas, 0 fallos**, incluidas las 8 «RF-114» de `PantallaArbitros.test.tsx` |
| Frontend | `npm run build` | Correcto |

Se usaron 2 workers porque, con el paralelismo por omisión y la máquina cargada, la suite tiene
fallas intermitentes por tiempo de espera en archivos de varios módulos (ver la prueba de B-15).

| Criterio | Pruebas que ya lo cubren |
| --- | --- |
| CA-1 | `ArbitroServiceTest` «RF-114: 5 equipos con 4 por árbitro requieren 2…» y «…4 equipos… exactamente 1…»; `ArbitrosIT` «B-08.1 · RF-114: 5 equipos activos con 4 por árbitro requieren 2 y solo cuenta el VALIDADO»; `CuotaArbitrosControllerTest` «RF-114: GET calcula la cuota del club en la temporada indicada» y «RF-114: sin temporadaId la cuota usa la temporada activa de la liga»; `PantallaArbitros.test.tsx` «RF-114: el club ve los árbitros validados frente a los requeridos y la advertencia» |
| CA-2 | `ArbitrosIT` «B-08.1 · RF-114: … solo cuenta el VALIDADO»; `PantallaArbitros.test.tsx` «RF-114: el club ve el cumplimiento cuando alcanza la cuota» |
| CA-3 | `PantallaArbitros.test.tsx` «RF-114: la Comisión ve la insignia «Cumple cuota» por club» y «RF-114: la Comisión filtra los clubes por «Cumple cuota»» |
| CA-4 | `CuotaArbitrosControllerTest` «RF-114: un clubId con UUID mal formado responde 400 VALIDACION», «RF-114: un temporadaId con UUID mal formado responde 400 VALIDACION» y la batería «RNF-003: seguridad de GET /api/v1/clubes/{clubId}/cuota-arbitros» (401, 403 y 404) |
| CA-5 | `CuotaArbitrosControllerTest` «RN-005: no existe DELETE de la cuota (405)»; la ausencia de auditoría solo se comprueba en esta corrida |
| V-1 | `ArbitroServiceTest` «sin equipos activos la cuota requerida es 0…»; `PantallaArbitros.test.tsx` «RF-114: un club sin equipos activos muestra el estado vacío de la cuota» |
| V-2 | `ArbitroServiceTest` «sin temporada activa ni temporadaId responde 409»; `CuotaArbitrosControllerTest` «RF-114: un club sin temporada activa responde 409 RF-114»; `PantallaArbitros.test.tsx` «RF-114: si la cuota no se puede calcular, muestra la causa y permite reintentar» y «RF-114: la Comisión marca «Sin dato» si la cuota de un club no está disponible» |
| C-1 | `ArbitroServiceTest` «el divisor sale del parámetro de la temporada…»; `ArbitrosIT` «B-08.1 · RF-114, DEC-72: la cuota usa la versión vigente del parámetro»; `ParametrosServiceTest` (herencia de `equiposPorArbitro` y el 4 **[DEMO]**) |

## 6. Defectos y observaciones

**Defectos:** ninguno.

**O-1 · La Comisión no ve la causa del «Sin dato».** Sin temporada activa, la Directiva lee la causa
real («La liga no tiene una temporada activa…»), pero la Comisión solo ve «No se pudo consultar la
cuota de los clubes de esta página.», igual que ante una falla de red. La pantalla sigue funcionando
y la insignia «Sin dato» es la acordada en B-08.2, así que no es un defecto. Queda anotado por si el
equipo quiere mostrar la causa también en la vista de la Comisión.

## 7. Conclusión

B-08 cumple sus criterios: la cuota se calcula con los equipos activos y el parámetro vigente de la
temporada, solo cuentan los árbitros validados, cada rol ve lo que le toca dentro de su ámbito, los
errores se explican y el indicador solo informa, sin bloquear ni auditarse. **B-08 puede cerrarse.**
