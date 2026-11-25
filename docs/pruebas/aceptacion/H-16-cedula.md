# Prueba de aceptación · H-16 Capturar la cédula del partido y enviarla a validación

| Campo | Valor |
| --- | --- |
| Historia | H-16 · subtarea H-16.4 · Sprint 4 (Must, 5 SP) |
| Requisitos | RF-019, RF-020, RF-025, RF-026, RF-047, RF-110, RF-122, RF-126, VAL-002, VAL-018, VAL-020, UI-011, DEC-24, DEC-72, DEC-164 a DEC-166, DEC-170 a DEC-172 |
| Pantalla | F-26 «Captura de cédula» (`/app/partidos/:partidoId/cedula`) |
| Implementó | Israel (H-16.1 #181, H-16.2 #186 y H-16.3 #188) |
| Versión probada | `develop` en `6c53c0c` |
| Fecha | 2026-11-24 |
| Ejecutó | Geovani (prueba cruzada, DEC-50), con un agente de IA en el entorno local |
| Resultado global | **Aprobado** (5 de 5 criterios); tres observaciones sin defecto |

La corrida valida de punta a punta la captura de la cédula. Con el partido en curso, el Capturista
abre la cédula y arma las alineaciones: quien no tiene registro activo o tiene una sanción vigente se
rechaza con su causa, igual que quien viene de otro club o de dos categorías abajo; el jugador de la
categoría inmediata inferior del mismo club sí sube. La no presentación asigna el marcador por default
de la **versión vigente** de los parámetros (2-0 en la prueba, no el 3-0 de la semilla) y F-26 lo
rotula «No oficial». Una cédula incompleta no se envía y lista lo que falta; completa pasa a
`PENDIENTE_VALIDACION` con el plazo del árbitro. En validación o aprobada ya nada se edita, ni por la
API ni directamente en la base.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local y efímero (Docker `postgres:17-alpine`), con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las 14 migraciones del repositorio (H-16 no agrega migración) |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS (ES256) y responde el inicio de sesión con contraseña |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 25 y Spring Boot 4.1, con `DB_URL` y `SUPABASE_URL` apuntando a la base y a la autenticación locales |
| Frontend | `npm run dev -- --port 5173` (Vite) con `VITE_SUPABASE_URL` apuntando a la autenticación local |
| Navegador | Panel de navegador del entorno de pruebas, 1280 × 800 px |
| Automatización | Backend `./mvnw verify`: **BUILD SUCCESS** (2305 + 363 pruebas, 0 fallos). Frontend: 13 pruebas de F-26 en verde. Detalle en la sección 5 y en [`pruebas-automaticas.txt`](evidencias/H-16/pruebas-automaticas.txt) |

La prueba corrió sobre una **base local efímera**: **no se alteró el proyecto compartido de
Supabase**. El arnés de autenticación, la semilla y los guiones de la API viven fuera del repositorio.
Aquí solo se versionan este documento y sus evidencias. La corrida por la API (CA-1 a CA-5) y la de
F-26 usaron la misma semilla sobre bases recreadas desde cero.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos son ficticios (dominio `.test`) y sus contraseñas
no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Capturista de prueba** | Capturista · LIGA (`partidos.cedula:CAPTURAR`) | Abre, captura y envía las cédulas |
| **Directiva de prueba** | Directiva de club · CLUB Halcones | Comprueba el `403` (no tiene permisos de cédula) |
| **Secretario de prueba** | Secretario · LIGA | Valida los registros en la semilla |
| **Árbitro de prueba** | Árbitro · PROPIO, central designado de P1 | Aprueba la cédula de P1 para CA-5 (ver O-3) |

Semilla por SQL como dueño del esquema: temporada «Temporada prueba H-16» con parámetros v1 (default
3-0 de la semilla) y **v2 vigente con default 2-0**; categorías encadenadas Tercera → Segunda → Primera
(`categoria_superior_id`); clubes Halcones, Toros y Pumas, afiliados. Partidos: **P1** Halcones
Primera vs. Toros Primera y **P2** Pumas Primera vs. Toros Primera, los dos `EN_CURSO`, y **P3**
Halcones Primera vs. Pumas Primera, `PROGRAMADO`.

| Jugador (ficticio) | Registro | Para qué |
| --- | --- | --- |
| Andrés y Bruno Halcón | Halcones Primera, `ACTIVO` | Elegibles |
| Pedro Pendiente | Halcones Primera, `PENDIENTE_VALIDACION` | CA-1: sin registro activo |
| Samuel Sancionado | Halcones Primera, `ACTIVO` con suspensión `VIGENTE` de 2 partidos (por resolución) | CA-1: sanción vigente |
| Diego Segunda | Halcones Segunda, `ACTIVO` | CA-2: sube a la inmediata superior de su club |
| Tomás Tercera | Halcones Tercera, `ACTIVO` | CA-2: dos categorías abajo |
| Óscar Torito | Toros Segunda, `ACTIVO` | CA-2: otro club (sí sube a Toros Primera) |
| Carlos y Mario Toro | Toros Primera, `ACTIVO` | Elegibles |
| Pablo y Raúl Puma | Pumas Primera, `ACTIVO` | Alineación de P2 |

## 3. Escenarios

Antes de los criterios se comprobaron la seguridad y la apertura
([respuestas](evidencias/H-16/respuestas-api.txt), S-1 a S-8): sin sesión `401 NO_AUTENTICADO`; la
Directiva `403 VAL-015`; partido o cédula inexistentes `404`; P3 programado `409 RF-046` «La cédula
se abre cuando el partido está en curso…»; el Capturista abre P1 (`201`, `EN_CAPTURA`, él como
capturista) y una segunda apertura responde `409 DUPLICADO`.

### CA-1 · Un jugador sin registro activo o con sanción vigente se rechaza con la causa (VAL-002, RF-019, RF-020)

> Dado un jugador sin registro activo o con sanción vigente, cuando se agrega a la alineación,
> entonces se rechaza con la causa.

**Resultado obtenido** ([respuestas](evidencias/H-16/respuestas-api.txt), CA-1a a CA-1d):

| Petición `PUT /cedulas/{id}/alineaciones` | Respuesta |
| --- | --- |
| Pedro Pendiente | `422 VAL-002` con `errores[0].codigo: "RF-019"` «…no puede jugar: su registro está pendiente de validación.» |
| Samuel Sancionado | `422 VAL-002` con `errores[0].codigo: "RF-020"` «…tiene una sanción vigente que le impide jugar.» |
| Andrés (elegible) con Pedro y Samuel | `422 VAL-002` «2 jugadores no pueden alinearse…» con un error por jugador (`jugadores[1]` y `jugadores[2]`) |
| `GET /partidos/{id}/cedula` después | `alineaciones: []`: no se guardó ni a Andrés |

`GET /cedulas/{id}/jugadores` devuelve a Pedro y a Samuel con `elegible: false` y la misma causa. En
F-26 aparecen tachados, sin casilla activa y con la causa en rojo bajo su nombre
([captura](evidencias/H-16/CA1-CA2-CA4-elegibilidad-y-faltantes.png)).

**Resultado:** ✅ Aprobado.

### CA-2 · Solo se alinea con su equipo o con el de la categoría inmediata superior de su club (VAL-018, RF-110)

> Dado un jugador de otro equipo del club que no está en la categoría inmediata inferior, cuando se
> alinea, entonces se rechaza.

**Resultado obtenido** ([respuestas](evidencias/H-16/respuestas-api.txt), CA-2a a CA-2c):

| Petición | Respuesta |
| --- | --- |
| Tomás Tercera (Halcones Tercera) con Halcones Primera | `422 VAL-002` con `codigo: "VAL-018"` «…solo puede alinearse con su equipo o con el de la categoría inmediata superior de su club.» |
| Óscar Torito (Toros Segunda, otro club) con Halcones Primera | `422 VAL-002` con `codigo: "VAL-018"` y el mismo mensaje |
| Andrés, Bruno y Diego con Halcones Primera; Carlos, Mario y Óscar con Toros Primera | `200`: Diego y Óscar con `categoriaSuperior: true` |

`GET /cedulas/{id}/jugadores` no ofrece a Tomás (no es candidato) y ofrece a Diego con Halcones
Primera y a Óscar con Toros Primera. En F-26 los dos llevan la leyenda «Sube de Halcones Segunda» y
«Sube de Toros Segunda» ([captura](evidencias/H-16/CA1-CA2-CA4-elegibilidad-y-faltantes.png)), y la
alineación de 3 + 3 se guardó desde la pantalla.

**Resultado:** ✅ Aprobado.

### CA-3 · La no presentación aplica el marcador por default configurado (RF-047, RF-126, DEC-24, DEC-72)

> Dada la no presentación de un equipo, cuando se marca, entonces se aplica el marcador por default
> configurado.

**Resultado obtenido** sobre P2 ([respuestas](evidencias/H-16/respuestas-api.txt), CA-3a a CA-3f):

| Petición `PUT /cedulas/{id}/marcador` | Respuesta |
| --- | --- |
| `noPresentadoEquipoId` de Toros con goles | `400 VALIDACION` «Con un equipo no presentado el marcador lo asigna la Liga por default: no envíes goles ni penales.» |
| `noPresentadoEquipoId` de Halcones (no juega P2) | `422 RF-126` «El equipo no presentado debe ser uno de los dos equipos del partido.» |
| `noPresentadoEquipoId` de Toros | `200` con Pumas **2** – Toros **0**, `porDefault: true`, `valorDemo: true` |

El 2-0 sale de la **versión 2 vigente** de los parámetros (la v1 tenía el 3-0 de la semilla): el
valor es configuración, no código (DEC-72). Después, con solo la alineación de Pumas, el envío pasa a
`PENDIENTE_VALIDACION`; la de Toros no se exige. En F-26, al elegir «No se presentó Toros Primera» y
guardar, el marcador muestra 2 – 0 con «Marcador por default por no presentación» y la insignia
**NO OFICIAL**, y la columna de Toros dice «No se presentó: no necesita alineación»
([captura](evidencias/H-16/CA3-no-presentacion-no-oficial.png)). La auditoría `CAPTURAR_MARCADOR`
guarda `porDefault: true` y el `noPresentadoEquipoId`.

**Resultado:** ✅ Aprobado.

### CA-4 · Incompleta se rechaza listando lo que falta; completa pasa a validación (VAL-020, RF-122)

> Dada una cédula incompleta, cuando se envía, entonces se rechaza listando lo que falta (VAL-020);
> completa, pasa a PENDIENTE_VALIDACION.

**Resultado obtenido** ([respuestas](evidencias/H-16/respuestas-api.txt)):

| Petición `POST /cedulas/{id}/envio` | Respuesta |
| --- | --- |
| P2 recién abierta (vacía) | `422 VAL-020` con `faltantes`: `marcador`, `alineacionLocal` «Captura la alineación de «Pumas Primera».» y `alineacionVisitante` |
| P1 con alineaciones y sin marcador | `422 VAL-020` con `faltantes: [marcador]` |
| P2 con la no presentación de Toros y sin alineación de Pumas | `422 VAL-020` con `faltantes: [alineacionLocal]` |
| P1 con alineaciones, gol, amarilla, sustitución, incidencia (un jugador y un equipo), observaciones y marcador 2-1 | `200` `PENDIENTE_VALIDACION` con `enviadaEn` y `plazoVenceEn` 48 h después |

En F-26, el aviso «Faltan 3 datos obligatorios para enviar» lista lo mismo que la API y «Enviar a
validación» está deshabilitado ([captura](evidencias/H-16/CA1-CA2-CA4-elegibilidad-y-faltantes.png));
al guardar la alineación baja a 1 dato y, con el marcador, cambia a «La cédula tiene lo obligatorio
para enviarse a validación». Enviada, la pantalla muestra «Pendiente de validación» y «El árbitro tiene
hasta el 26 nov 2026, 21:33 para validarla» ([captura](evidencias/H-16/CA4-CA5-enviada-solo-lectura.png)).
En la base, `cedula_estado_historial` registra `EN_CAPTURA → PENDIENTE_VALIDACION` a nombre del
Capturista ([base y auditoría](evidencias/H-16/base-y-auditoria.txt)).

**Resultado:** ✅ Aprobado.

### CA-5 · Una cédula en validación o aprobada no se edita (RF-122, RF-048)

> Una cédula en validación o aprobada no se edita.

**Resultado obtenido** ([respuestas](evidencias/H-16/respuestas-api.txt), CA-5a a CA-5j;
[base](evidencias/H-16/base-y-auditoria.txt)):

| Estado de P1 | Petición | Respuesta |
| --- | --- | --- |
| `PENDIENTE_VALIDACION` | Alineación, marcador, evento, incidencia, observaciones y reenvío | `409 RF-122` en los seis, «… solo se modifica mientras la cédula está en captura; ahora está pendiente validacion.» |
| `APROBADA` (ver O-3) | Marcador, alineación y reenvío | `409 RF-122` «… ahora está aprobada.» |
| `APROBADA` | `GET /partidos/{id}/cedula` | `200` con el contenido intacto: 2-1, 6 alineados, 3 eventos y 1 incidencia |

La última garantía en la base también responde, sin pasar por el servicio: cambiar el marcador de la
aprobada → `RF-048` «Una cédula aprobada no se modifica…»; insertar un jugador en la cédula en
validación de P2 → `RF-122` «El contenido de la cédula solo se modifica mientras está en captura.»;
cambiar su marcador → `RF-122` «La cédula en validación no se edita…». En F-26, la cédula enviada
queda de solo lectura: todos los campos y botones de `<main>` están deshabilitados (la observación es
un texto de solo lectura) y no aparecen «Guardar» ni «Enviar a validación»
([captura](evidencias/H-16/CA4-CA5-enviada-solo-lectura.png)).

**Resultado:** ✅ Aprobado.

### Auditoría (RF-093)

Cada operación aceptada dejó su evento M-08 a nombre del Capturista: `ABRIR_CEDULA`,
`CAPTURAR_ALINEACION` (con el número de jugadores por equipo), `REGISTRAR_EVENTO_CEDULA` (tipo, minuto
y equipo), `REGISTRAR_INCIDENCIA_CEDULA`, `CAPTURAR_OBSERVACIONES`, `CAPTURAR_MARCADOR` y
`ENVIAR_CEDULA` con el plazo. Ningún detalle trae nombres, CURP ni los textos libres de la incidencia o
las observaciones (la búsqueda de nombres y textos en `detalle` devuelve 0 filas), y las peticiones
rechazadas no dejaron evento ([base y auditoría](evidencias/H-16/base-y-auditoria.txt)).

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 Sin registro activo o con sanción vigente | ✅ Aprobado |
| CA-2 Solo la categoría inmediata superior del mismo club | ✅ Aprobado |
| CA-3 Marcador por default configurado en la no presentación | ✅ Aprobado |
| CA-4 Envío incompleto con faltantes; completo a validación | ✅ Aprobado |
| CA-5 En validación o aprobada no se edita | ✅ Aprobado |

## 5. Pruebas automáticas relacionadas

Verificación ejecutada sobre `6c53c0c` el 24-nov-2026 (detalle en
[`pruebas-automaticas.txt`](evidencias/H-16/pruebas-automaticas.txt)):

| Subproyecto | Comando | Resultado |
| --- | --- | --- |
| Backend | `./mvnw verify` | **BUILD SUCCESS**: 2305 pruebas unitarias y de controlador, 0 fallos; 363 de integración, 0 fallos, 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS). Incluye `CedulaServiceTest` (33), `CedulaControllerTest` (72) y `CedulasIT` (11) |
| Frontend | `npx vitest run src/features/partidos/cedula` | 1 archivo, 13 pruebas, 0 fallos |

| Criterio | Pruebas que ya lo cubren |
| --- | --- |
| CA-1 | `CedulaServiceTest` «VAL-002 · RF-019 · RF-020 · VAL-018 · RF-025: devuelve un error por jugador y no guarda nada»; `CedulasIT` «VAL-002 · RF-019 · VAL-018 · RF-025: un error por jugador no elegible y no se guarda nadie»; `CedulaControllerTest` «VAL-002: un jugador no elegible responde 422 con la lista errores por jugador»; `PantallaCapturaCedula.test` «DEC-172: … quien no puede jugar está deshabilitado con su causa» y «VAL-002: el error de un jugador se muestra en su fila». La sanción vigente contra la base real solo se comprueba en esta corrida |
| CA-2 | `CedulaServiceTest` «VAL-018: un jugador de su club dos categorías abajo no se alinea en la superior» y «RF-019 · RF-110: guarda jugadores elegibles, incluido uno de la categoría inmediata superior…»; `CedulasIT` «RF-019 · RF-110: guarda la alineación y marca al jugador de la categoría inmediata inferior» y «RF-019 · VAL-018: el trigger rechaza una alineación no elegible insertada sin el servicio» |
| CA-3 | `CedulaServiceTest` «RF-126 · DEC-24: el equipo no presentado pierde con el marcador por default…», «DEC-72: el marcador por default sale de la versión vigente de los parámetros, no del código» y «RF-126 · RF-100: no presentación con goles, de un equipo ajeno o sin parámetros se rechaza»; `CedulasIT` «RF-047 · RF-126 · DEC-72: …»; `PantallaCapturaCedula.test` «RF-126: …» y «DEC-72: el marcador por default de parámetros de demostración se rotula «No oficial»» |
| CA-4 | `CedulaServiceTest` «VAL-020: una cédula incompleta no se envía y lista lo que falta», «VAL-020 · RF-126: el equipo no presentado no necesita alineación para enviar» y «RF-122: envía la cédula completa, la audita y publica CedulaEnviada…»; `CedulasIT` «VAL-020 · RF-122: …» y «VAL-020 · RF-126: …»; `PantallaCapturaCedula.test` «VAL-020: lista lo que falta y no deja enviar una cédula incompleta» y «VAL-020: si el backend rechaza el envío muestra sus faltantes» |
| CA-5 | `CedulaServiceTest` «RF-122: con la cédula fuera de captura la alineación y el marcador responden 409» y «RF-122: fuera de captura no se agregan ni quitan eventos o incidencias…»; `CedulasIT` «RF-122: en validación ni el servicio ni la base modifican la alineación ni el marcador»; `PantallaCapturaCedula.test` «RF-122: completa se envía y queda sin edición…». La cédula `APROBADA` solo se comprueba en esta corrida |
| Seguridad | `CedulaControllerTest` (401, 403 y 404 por endpoint) y `CedulasIT` «VAL-015: una cuenta sin partidos.cedula:CAPTURAR no abre la cédula»; `PantallaCapturaCedula.test` «VAL-015: …» |

## 6. Defectos y observaciones

**Defectos:** ninguno.

**O-1 · Redacción del `409 RF-122`.** El mensaje se arma con el estado en minúsculas y el verbo en
singular: «Los eventos solo se modifica… ahora está pendiente validacion.» (falta «de», el acento y la
concordancia con «Los eventos», «Las incidencias» y «Las observaciones»). El código y el estado HTTP
son correctos y F-26 no lo muestra, porque deshabilita la edición fuera de captura. Se anota para
pulir los textos de `CedulaService` (`estadoLegible` y `bloquearEnCaptura`).

**O-2 · El envío no pide confirmación.** «Enviar a validación» envía al primer clic; la única
advertencia es la nota «Después ya no se edita.» bajo el botón. No contradice los criterios ni F-26,
pero el envío no se puede deshacer hasta que el árbitro devuelva la cédula (DEC-170). Se deja a
consideración del equipo un diálogo de confirmación.

**O-3 · La aprobación se simuló en la base.** La aprobación del árbitro es de H-17 y aún no tiene
endpoint, así que P1 pasó a `APROBADA` con un `UPDATE` en la base con la sesión del árbitro central
designado (el trigger exige la designación vigente y que no sea el capturista, VAL-021). El resto del
flujo es el real. El endpoint `GET /registros-jugador/{id}/elegibilidad` que cita la historia tampoco
existe todavía; F-26 usa `GET /cedulas/{id}/jugadores` (DEC-172), que da la misma elegibilidad por
jugador y se probó aquí.

## 7. Conclusión

H-16 cumple sus cinco criterios: la alineación rechaza con su causa a quien no tiene registro activo,
tiene sanción vigente o no viene de la categoría inmediata inferior de su club; la no presentación
aplica el marcador por default de la versión vigente de los parámetros y se rotula «No oficial»; el
envío incompleto lista lo que falta y el completo pasa a `PENDIENTE_VALIDACION` con el plazo del
árbitro; y en validación o aprobada la cédula ya no se edita, ni por la API ni en la base. **H-16
puede cerrarse.** Las observaciones O-1 a O-3 no bloquean el cierre.
