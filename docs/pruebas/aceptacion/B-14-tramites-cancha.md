# Prueba de aceptación · B-14 Gestionar solicitudes de préstamo, comodato y oferta de cancha con oficio

| Campo | Valor |
| --- | --- |
| Historia | B-14 · subtarea B-14.3 · Sprint 3 · 5 SP (claves de Jira por confirmar) |
| Requisitos | RF-116, RF-117, RF-118, UI-028, CU-024, CU-025, DEC-35, DEC-135 a DEC-138, RN-021, VAL-015, VAL-024, RF-093 |
| Pantallas | «Trámites de cancha» (`/app/programacion/tramites-cancha`, pantalla B-07 del prototipo) |
| Implementó | Geovani (B-14 #157, decisiones DEC-135 a DEC-138 #160) |
| Versión probada | `develop` en `04e9949` (incluye `5244396` y `7333dce`) |
| Fecha | 2026-11-19 |
| Ejecutó | Israel (prueba cruzada, DEC-50), con un agente de IA en el entorno local |
| Resultado global | **Aprobado** (6 de 6 criterios); dos observaciones sin defecto |

La corrida valida de punta a punta la historia B-14. La Directiva de un club captura préstamos y
comodatos con el Formato 1, y ofertas con el Formato 2. El Secretario captura la oferta de un
oferente externo. Cada trámite recibe su número de oficio al capturarse y la Presidencia lo
dictamina, con motivo cuando la decisión es desfavorable. El Secretario genera la carta con el
número de oficio y el dictamen. Cada rol ve solo los trámites de su ámbito, y aceptar o aprobar no
da de alta la cancha (DEC-138). Se usaron el frontend, el backend y PostgreSQL reales sobre una base
local efímera.

Los criterios salen de RF-116, RF-117 y RF-118, de CU-024 y CU-025, de las decisiones DEC-135 a
DEC-138 y del contrato de las convenciones de API §6.6.2 («Trámites de cancha: Formatos 1 y 2»).
B-14 es una historia B y el backlog no trae criterios numerados, así que esta prueba los numera de
CA-1 a CA-6.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local y efímero (Docker `postgres:17-alpine`), con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las 14 migraciones del repositorio aplicadas (la tabla `solicitudes_cancha` ya existía; B-14 no agrega migración) |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS con una llave ES256 y responde el inicio de sesión con contraseña de las cuentas ficticias |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 25 y Spring Boot 4.1, con `DB_URL` y `SUPABASE_URL` apuntando a la base y a la autenticación locales |
| Frontend | `npm run dev -- --port 5173` (Vite) con `VITE_SUPABASE_URL` hacia la autenticación local; Vite reenvía `/api` al backend |
| Navegador | Panel de navegador del entorno de pruebas, ventana emulada de 1024 × 768 px |
| Automatización | Backend `./mvnw verify`: **BUILD SUCCESS** (2113 + 333 pruebas, 0 fallos). Frontend: lint, **839 pruebas, 0 fallos** y build correctos. Detalle en la sección 5 y en [`pruebas-automaticas.txt`](evidencias/B-14/pruebas-automaticas.txt) |

La prueba corrió sobre una **base local efímera**: **no se alteró el proyecto compartido de
Supabase** y no se emitieron números de oficio en él. El arnés de autenticación, las cuentas, la
semilla y los guiones de la API viven fuera del repositorio. Aquí solo se versionan este documento y
sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican. La matriz de
permisos es la inicial de la migración (Apéndice E): `canchas.solicitud:CREAR` lo tienen la
Directiva de club y el Secretario; `canchas.solicitud:APROBAR`, el Presidente; y
`canchas.oficio:GENERAR`, solo el Secretario.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Directiva Halcones de prueba** | Directiva de club · CLUB (Club Halcones) | CA-1, CA-2 y CA-5: captura y bandeja de su club |
| **Directiva Toros de prueba** | Directiva de club · CLUB (Club Toros) | CA-5: trámite de otro club |
| **Secretario de prueba** | Secretario · LIGA | CA-2 (oferta de un externo), CA-4 (carta) y CA-5 |
| **Presidente de prueba** | Presidente del Comité Ejecutivo · LIGA | CA-3: dictámenes |
| **Tesorero de prueba** | Tesorero · LIGA | CA-5: sin ninguno de los tres permisos |

Datos ficticios sembrados en la base local:

| Dato | Situación inicial | Uso en la prueba |
| --- | --- | --- |
| **Club Halcones** y **Club Toros** | `ACTIVO` | Clubes solicitantes |
| **Club Venados (cerrado)** | `CERRADO` | Referencia inválida (`422`) |
| **Morelos 1** | Cancha de la liga, unidad deportiva | Comodato con cancha del catálogo |
| **Campo Ejidal Tarímbaro** | Cancha externa de un tercero | Préstamo dirigido a un dueño externo |

Los nombres, cargos y teléfonos capturados en los formatos son ficticios («Solicitante Ficticio»,
«Oferente Ficticio», teléfonos `44300000xx`).

## 3. Escenarios

### CA-1 · La Directiva captura un préstamo o comodato con el Formato 1 y recibe su número de oficio

> Dada la Directiva de un club, cuando captura un préstamo o comodato con los campos del Formato 1,
> entonces el trámite nace `PENDIENTE` con el número `OF-<año>-<consecutivo>` (DEC-135); una hora de
> término no posterior al inicio se rechaza.

**Pasos.** En «Trámites de cancha», la Directiva de Halcones abre **Nueva solicitud** y captura un
**préstamo** del Campo Ejidal Tarímbaro, dirigido a un dueño externo, para el 05/12/2026. Primero pone
las 12:00 a las 10:00 y luego lo corrige a las 12:00 a las 14:00. Por API captura además un
**comodato** de Morelos 1.

**Resultado obtenido.** El formulario muestra los campos de la plantilla del Formato 1 (tipo, club,
cancha del catálogo opcional, destinatario Liga o dueño externo, datos de la carta, fecha, horario,
actividad, responsable, contacto y lugar de emisión). Su único club es Halcones, porque es el único de
su ámbito. Con 12:00 a 10:00, el formulario marca «La hora de término debe ser posterior a la de
inicio.» y no envía nada ([captura](evidencias/B-14/CA1-hora-termino-invalida.png)); por API, el mismo
caso responde `400 VALIDACION`. Corregido el horario, el alta respondió `201` y la bandeja muestra
«Se registró el oficio OF-2026-0001. Quedó pendiente hasta el dictamen de la Presidencia.», con el
trámite en **PENDIENTE** ([captura](evidencias/B-14/CA1-prestamo-registrado-con-oficio.png)). El
comodato recibió `OF-2026-0002` en `PENDIENTE` ([respuestas](evidencias/B-14/respuestas-api.txt)).

**Resultado:** ✅ Aprobado.

### CA-2 · La oferta de cancha (Formato 2) la captura un club o, si el oferente es externo, el Secretario

> Dada una oferta de cancha, cuando la captura la Directiva para su club o el Secretario para un
> oferente externo, entonces nace `PENDIENTE_NEGOCIACION` con su número de oficio; la Directiva no
> captura ofertas de externos y una oferta no lleva club y externo a la vez.

**Resultado obtenido** ([respuestas](evidencias/B-14/respuestas-api.txt)):

| Captura | Respuesta |
| --- | --- |
| Directiva de Halcones: oferta del Campo Las Huertas para su club | `201`, `OF-2026-0003`, `PENDIENTE_NEGOCIACION` |
| Directiva de Halcones: oferta de un oferente externo | `404 NO_ENCONTRADO`: necesita ámbito de liga |
| Secretario: oferta del «Deportivo Privado El Sauz (ficticio)» | `201`, `OF-2026-0004`, `PENDIENTE_NEGOCIACION` |
| Secretario: oferta con club **y** oferente externo | `400 VALIDACION` «Indica quién ofrece la cancha: un club o un oferente externo, no ambos.» |
| Directiva: préstamo que además trae el bloque de oferta | `400 VALIDACION` «Un préstamo o comodato se captura con los campos del Formato 1…» |
| Secretario: préstamo del club cerrado | `422 REFERENCIA_INVALIDA` |
| Directiva: préstamo con una cancha que no es de la liga | `422 REFERENCIA_INVALIDA` |

El contexto de captura confirma la regla: el Secretario recibe los clubes `ACTIVO` de la liga (sin el
cerrado) y `capturaExternos: true`; la Directiva, solo su club y `capturaExternos: false`. Préstamo,
comodato y oferta comparten la serie de oficios: `OF-2026-0001` a `OF-2026-0005`, sin saltos.

**Resultado:** ✅ Aprobado.

### CA-3 · La Presidencia dictamina con los estados de cada formato y motivo en la decisión desfavorable

> Dado un trámite pendiente, cuando la Presidencia lo dictamina, entonces el Formato 1 pasa a
> `APROBADA` o `NO_APROBADA` y el Formato 2 a `ACEPTADA` o `RECHAZADA`; la decisión desfavorable exige
> un motivo de 10 a 500 caracteres (RN-021); un estado final no cambia; y aprobar o aceptar no da de
> alta la cancha (DEC-138).

**Resultado obtenido.**

- **No aprobar con motivo (interfaz).** La Presidencia ve los trámites de los dos clubes con
  **Aprobar** y **No aprobar**, sin «Nueva solicitud» ni «Generar carta». En «No aprobar» de
  `OF-2026-0001`, el motivo «Sin campo» (9 caracteres) marca «Escribe un motivo de al menos 10
  caracteres.» ([captura](evidencias/B-14/CA3-motivo-corto.png)). Con un motivo válido, el trámite
  queda en **NO APROBADA**, con el motivo, la fecha del dictamen y «Sin acciones»
  ([captura](evidencias/B-14/CA3-no-aprobada-con-motivo.png)).
- **Aprobar un comodato y aceptar una oferta (interfaz).** Los dos modales advierten «Este dictamen no
  da de alta la cancha ni sus horarios: regístralos aparte en Canchas.»
  ([captura](evidencias/B-14/CA3-aprobar-comodato-aviso-DEC-138.png)). `OF-2026-0002` quedó en
  **APROBADA** con sus condiciones, y `OF-2026-0003` en **ACEPTADA** con «Sin condiciones adicionales.»
  ([captura](evidencias/B-14/CA3-oferta-aceptada.png)). El catálogo siguió con 2 canchas y 0 horarios
  de club antes y después ([auditoría](evidencias/B-14/historial-auditoria.txt)).
- **API** ([respuestas](evidencias/B-14/respuestas-api.txt)):

| Dictamen | Respuesta |
| --- | --- |
| Rechazar la oferta del externo sin observaciones | `400 RN-021` «Escribe observaciones de al menos 10 caracteres…» |
| Rechazar la oferta del externo con observaciones | `200`, `RECHAZADA` |
| `ACEPTADA` sobre un préstamo (decisión del otro formato) | `400 VALIDACION` «Un préstamo o comodato se aprueba o no se aprueba.» |
| Dictaminar otra vez el préstamo ya no aprobado | `409 RF-117` «Este trámite ya se dictaminó: está no aprobada.» |
| Dictaminar otra vez la oferta ya aceptada | `409 RF-116` |

**Resultado:** ✅ Aprobado.

### CA-4 · La carta del trámite lleva el número de oficio y los datos del formato, y solo la genera quien tiene el permiso

> Dado un trámite, cuando quien tiene `canchas.oficio:GENERAR` genera la carta, entonces ve la vista
> previa con el número de oficio, el asunto, los datos del formato y el dictamen, y la guarda en PDF
> con el diálogo de impresión; sin el permiso, `403` (DEC-137).

**Resultado obtenido.** El Secretario ve **Generar carta** en cada trámite
([captura](evidencias/B-14/CA4-secretario-generar-carta.png)). La carta de `OF-2026-0002` (comodato)
muestra «LIGA MUNICIPAL DE FUTBOL AMATEUR MORELIA», «OFICIO N.º: OF-2026-0002», «ASUNTO: Solicitud de
comodato de cancha deportiva.», el lugar y la fecha, el destinatario, la instalación, el horario, la
actividad y el responsable ([encabezado](evidencias/B-14/CA4-carta-formato1-encabezado.png)). Al pie
trae el «Espacio para la autorización» con **Aprobada** marcada, las condiciones, «Presidente de
prueba» y la fecha ([autorización](evidencias/B-14/CA4-carta-formato1-autorizacion.png)). La carta de
`OF-2026-0004` (oferta del externo) usa la plantilla del Formato 2: encabezado del oferente,
«ASUNTO: Oferta de disponibilidad de cancha deportiva.», características y costo de la cancha, y el
«Acuse de recepción de la liga» con **Rechazada** y las observaciones
([captura](evidencias/B-14/CA4-carta-formato2-oferta-externa.png)). Por API, la Presidencia y la
Directiva de Halcones, incluso sobre su propio trámite, reciben `403 VAL-015`: es lo que fija
DEC-137 con la matriz inicial. No se pulsó «Descargar PDF», porque abre el diálogo de impresión del
sistema; la generación del PDF depende del navegador.

**Resultado:** ✅ Aprobado.

### CA-5 · La bandeja y las acciones respetan el permiso y el ámbito

> La bandeja se abre con cualquiera de `CREAR`, `APROBAR` o `GENERAR` (DEC-136) y une sus alcances:
> una Directiva ve solo los trámites de su club y no las ofertas de externos; un ámbito de liga ve
> todos. Un club fuera del ámbito responde `404` (VAL-024); sin permiso, `403`.

**Resultado obtenido** ([respuestas](evidencias/B-14/respuestas-api.txt)):

| Consulta o acción | Respuesta |
| --- | --- |
| Directiva de Halcones: bandeja del Formato 1 | `OF-2026-0002` y `OF-2026-0001` (no ve `OF-2026-0005` de Toros) |
| Directiva de Halcones: bandeja del Formato 2 | Solo `OF-2026-0003`, sin la oferta del externo |
| Directiva de Toros: bandeja del Formato 1 | Solo `OF-2026-0005` |
| Secretario: Formato 2 filtrado por `estado=ACEPTADA` | Solo `OF-2026-0003` |
| Presidencia en la interfaz | Los trámites de Halcones y de Toros |
| Directiva de Halcones: préstamo a nombre de Club Toros | `404 NO_ENCONTRADO` |
| Directiva de Halcones: dictaminar | `403 VAL-015` (sin `APROBAR`) |
| Tesorero: abrir la bandeja | `403 VAL-015` (sin ninguno de los tres permisos) |
| Presidencia: contexto de captura | Listas vacías y `capturaExternos: false` (sin `CREAR`) |

**Resultado:** ✅ Aprobado.

### CA-6 · Cada captura y cada dictamen se auditan sin datos personales del formato

> Toda captura se audita como `REGISTRAR_SOLICITUD_CANCHA` y todo dictamen como
> `RESOLVER_SOLICITUD_CANCHA` en `M-06`, con identificadores, tipo, número de oficio y estados, pero
> nunca nombres, teléfonos ni correos del formato (RF-093).

**Resultado obtenido.** La auditoría tiene 5 `REGISTRAR_SOLICITUD_CANCHA`, uno por oficio y con el
perfil que lo capturó, y 4 `RESOLVER_SOLICITUD_CANCHA` del Presidente, con `estadoAnterior` y
`estado`. Las decisiones desfavorables llevan su motivo. Ninguno incluye nombres, cargos ni teléfonos
del formato: la búsqueda en `detalle` solo coincide con la bandera booleana `"oferenteExterno": true`
([auditoría](evidencias/B-14/historial-auditoria.txt)). Los intentos rechazados (`400`, `403`, `404`,
`409`, `422`) no dejaron eventos.

**Resultado:** ✅ Aprobado.

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 Formato 1: captura con número de oficio | ✅ Aprobado |
| CA-2 Formato 2: oferta de club o de externo | ✅ Aprobado |
| CA-3 Dictamen, motivo desfavorable y estados finales (DEC-138) | ✅ Aprobado |
| CA-4 Carta con número de oficio y permiso de generación (DEC-137) | ✅ Aprobado |
| CA-5 Bandeja, permiso y ámbito (DEC-136) | ✅ Aprobado |
| CA-6 Auditoría sin datos personales | ✅ Aprobado |

## 5. Pruebas automáticas relacionadas

Verificación ejecutada sobre `04e9949` el 19-nov-2026 (detalle en
[`pruebas-automaticas.txt`](evidencias/B-14/pruebas-automaticas.txt)):

| Subproyecto | Comando | Resultado |
| --- | --- | --- |
| Backend | `./mvnw verify` | **BUILD SUCCESS**: 2113 pruebas unitarias y de controlador, 0 fallos; 333 de integración, 0 fallos, 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS). Incluye `SolicitudCanchaServiceTest` (18), `SolicitudCanchaControllerTest` (31) y `SolicitudesCanchaIT` (5) |
| Frontend | `npm run lint` | Sin errores |
| Frontend | `npx vitest run --maxWorkers=2` | **90 archivos y 839 pruebas, 0 fallos**, incluidas las 6 de `PantallaTramitesCancha.test.tsx` |
| Frontend | `npm run build` | Correcto |

Se usaron 2 workers porque, con el paralelismo por omisión y la máquina cargada, la suite tiene
fallas intermitentes por tiempo de espera en archivos de varios módulos (ver la prueba de B-15).

| Criterio | Pruebas que ya lo cubren |
| --- | --- |
| CA-1 | `SolicitudCanchaServiceTest` «RF-117: el préstamo de un club nace PENDIENTE, con oficio del año de Morelia, y se audita sin datos personales» y «RF-117: la hora de término debe ser posterior al inicio»; `SolicitudesCanchaIT` «RF-117, RF-118: la Directiva captura un préstamo con oficio y el Presidente lo aprueba una sola vez»; `PantallaTramitesCancha.test.tsx` «RF-117: la Directiva captura un préstamo con los campos del Formato 1» |
| CA-2 | `SolicitudCanchaServiceTest` «RF-116: la oferta de un externo se autoriza sobre la liga y nace PENDIENTE_NEGOCIACION», «RF-116: una oferta con club y externo a la vez responde 400», «R8: cada tipo exige los campos de su formato y rechaza los del otro», «RF-117: un club inexistente o cerrado responde 422 REFERENCIA_INVALIDA» y «RF-117: una cancha que no es de la liga responde 422 REFERENCIA_INVALIDA»; `SolicitudesCanchaIT` «RF-116: el Secretario captura la oferta de un externo y el Presidente la rechaza con motivo» |
| CA-3 | `SolicitudCanchaServiceTest` «RF-117: el Presidente aprueba un préstamo PENDIENTE y se audita el cambio de estado», «RN-021: rechazar una oferta exige observaciones y las deja como motivo», «DEC-35: una decisión de otro formato responde 400 VALIDACION» y «RF-117: dictaminar un trámite ya resuelto responde 409 RF-117»; `SolicitudesCanchaIT` «DEC-35: la tabla rechaza estados de otro formato y un trámite sin solicitante (última garantía)»; `PantallaTramitesCancha.test.tsx` «RN-021: la Presidencia no aprueba sin motivo y lo envía con él» |
| CA-4 | `SolicitudCanchaServiceTest` «RF-118: la carta lleva el número de oficio, el asunto del formato y la fecha de captura en Morelia»; `SolicitudesCanchaIT` «RF-118: los oficios son consecutivos y la carta la genera quien tiene canchas.oficio:GENERAR»; `PantallaTramitesCancha.test.tsx` «RF-118: quien genera oficios ve la vista previa de la carta con su número» |
| CA-5 | `SolicitudCanchaServiceTest` «ADR-0007: la bandeja une los alcances de CREAR, APROBAR y GENERAR», «VAL-015: sin ninguno de los tres permisos la bandeja responde 403» y «B-14: el contexto de quien solo dictamina no ofrece clubes ni canchas»; `SolicitudesCanchaIT` «VAL-024: la Directiva solo captura y ve los trámites de su club; las ofertas de externos no»; `PantallaTramitesCancha.test.tsx` «RF-117: sin ninguno de los tres permisos no pide datos», «RF-117: la pestaña Formato 1 pide préstamos y comodatos…» y «RF-116: la pestaña Formato 2 pide las ofertas con sus estados» |
| CA-6 | `SolicitudCanchaServiceTest` «RF-117: el préstamo de un club nace PENDIENTE, … y se audita sin datos personales» y «VAL-015: sin canchas.solicitud:CREAR la captura responde 403 y no escribe» |

## 6. Defectos y observaciones

**Defectos:** ninguno.

**O-1 · La Directiva no imprime la carta de su propia solicitud (DEC-137).** Con la matriz inicial,
solo el Secretario tiene `canchas.oficio:GENERAR`, así que la Directiva y la Presidencia reciben
`403`, aunque CU-024 pone la carta antes del dictamen. El comportamiento es el que fija DEC-137, que
sigue **pendiente de confirmar con el PO**. Si la Directiva debe imprimirla, basta con otorgarle el
permiso con ámbito de club en la matriz, sin cambiar código.

**O-2 · Redacción de la carta del Formato 1 con algunos cargos.** La plantilla arma «en calidad de
{cargo} de {club}». Con el cargo «Secretario del club», la carta dice «en calidad de Secretario del
club de Club Halcones». Depende de lo que capture la Directiva y no cambia ningún dato de la carta.
Se deja anotado por si el equipo quiere ajustar la ayuda del campo «Cargo en el club» o la plantilla.

## 7. Conclusión

B-14 cumple sus criterios: los Formatos 1 y 2 se capturan con los campos de la plantilla y reciben
su número de oficio, la Presidencia los dictamina con los estados de cada formato y con motivo en la
decisión desfavorable, la carta sale con el número de oficio y el dictamen, cada rol actúa solo
dentro de su permiso y su ámbito, y todo queda auditado sin datos personales. **B-14 puede
cerrarse.** Las dos observaciones no bloquean el cierre.
