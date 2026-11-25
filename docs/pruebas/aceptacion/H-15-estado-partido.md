# Prueba de aceptación · H-15 Gestionar el estado del partido con motivo e historial

| Campo | Valor |
| --- | --- |
| Historia | H-15 · subtarea H-15.4 · Sprint 4 |
| Requisitos | RF-043, RF-044, RF-045, RF-046, RF-127, VAL-008, RN-021, UI-010 (más RF-093, VAL-015, DEC-160 a DEC-163) |
| Pantallas | F-25 «Detalle del partido» (resumen, historial, barra de acciones y modal de reprogramación) |
| Implementó | Israel (H-15.1 #182, H-15.2 #183 y H-15.3 #185) |
| Versión probada | `develop` en `0a62518` |
| Fecha | 2026-11-24 |
| Ejecutó | Geovani (prueba cruzada, DEC-50), con un agente de IA en el entorno local |
| Resultado global | **Aprobado** (4 de 4 criterios); dos observaciones sin defecto |

La corrida valida de punta a punta la máquina de estados del partido (DEC-23). Un partido `PROGRAMADO`
se reprograma con nueva fecha, cancha y motivo, por la API y desde el modal de F-25. El historial guarda
quién hizo el cambio, cuándo y por qué. Las transiciones que el estado no admite se rechazan con
`409 RF-046` sin escribir nada. La tarea programada pasa el partido a `EN_CURSO` a su nueva hora una
sola vez, aunque corre cada minuto, y deja el cambio a nombre del perfil Sistema. F-25 muestra solo las
acciones que admiten el estado y la cuenta: el Programador ve «Reprogramar» y «Cancelar partido» con el
partido programado y solo «Suspender» con el partido en curso. El Presidente y la Directiva no ven
ninguna. Cada cambio manual y cada inicio automático quedan en la auditoría de M-08.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local y efímero (Docker `postgres:17-alpine`), con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las 14 migraciones del repositorio (H-15 no agrega migración) |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS (ES256) y responde el inicio de sesión con contraseña |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 25 y Spring Boot 4.1, con `DB_URL` y `SUPABASE_URL` apuntando a la base y a la autenticación locales. La tarea `inicio-partidos` quedó **habilitada** con su cron de cada minuto (`0 * * * * *`, `America/Mexico_City`), como en producción |
| Frontend | `npm run dev -- --port 5173` (Vite) con `VITE_SUPABASE_URL` apuntando a la autenticación local |
| Navegador | Panel de navegador del entorno de pruebas, 1280 × 800 px |
| Automatización | Backend `./mvnw verify`: **BUILD SUCCESS** (2296 + 362 pruebas, 0 fallos). Frontend: 40 pruebas de F-25 y la programación en verde. Detalle en la sección 5 y en [`pruebas-automaticas.txt`](evidencias/H-15/pruebas-automaticas.txt) |

La prueba corrió sobre una **base local efímera** y **no alteró el proyecto compartido de Supabase**. El
reloj de la tarea programada es el real: para el CA-3 se programó un partido unos minutos después de
preparar la jornada y se esperó a que llegara su hora. El arnés de autenticación, la semilla y los
guiones de la API viven fuera del repositorio. Aquí solo se versionan este documento y sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos son ficticios (dominio `.test`) y sus contraseñas
no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Programador de prueba** | Programador de partidos · LIGA (`partidos.partido:CONSULTAR` y `CAMBIAR_ESTADO`) | Prepara la jornada; CA-1, CA-2 y CA-4: reprograma, cancela y ve las acciones |
| **Presidente de prueba** | Presidente del Comité Ejecutivo · LIGA (`CONSULTAR`, sin `CAMBIAR_ESTADO`) | Publica la jornada; CA-4: consulta sin acciones; `403` al intentar un cambio |
| **Directiva de prueba** | Directiva de club · CLUB «Club Halcones» (`CONSULTAR`, sin `CAMBIAR_ESTADO`) | CA-4: ve solo el partido de su club y sin acciones; `404` con el de otro club |

La temporada «Temporada prueba H-15», la categoría «Libre Demostración», el grupo A, cuatro clubes con
un equipo cada uno, dos canchas de la liga y las cuentas se sembraron por SQL como dueño del esquema.
La jornada se armó y se publicó **por la API**, con el flujo real de H-12 y H-13:

| Id | Partido | Programación original | Uso en la prueba |
| --- | --- | --- | --- |
| P1 | H15 Halcones vs. H15 Toros | 24-nov 21:15, Cancha Demostración 1 | CA-1 (reprogramado por la API a 21:18 en la Cancha 2), CA-3 (inicio automático) y CA-4 |
| P2 | H15 Pumas vs. H15 Lobos | 28-nov 10:00, Cancha Demostración 1 | CA-2 (transiciones prohibidas desde `PROGRAMADO`) y CA-1 desde F-25 (reprogramado al 30-nov 17:00) |
| P3 | H15 Toros vs. H15 Lobos | 29-nov 10:00, Cancha Demostración 2 | CA-4 (cancelación permitida) y CA-2 (un cancelado ya no cambia) |

La jornada 1 se creó con el Programador, se envió a aprobación y el Presidente la publicó (`201`, `201`,
`201`, `201`, `200` y `200`; [respuestas](evidencias/H-15/respuestas-api.txt), sección «Preparación»).
Las transiciones solo se admiten con la jornada publicada (DEC-162).

## 3. Escenarios

### CA-1 · Reprogramar un partido programado guarda usuario, fecha y motivo (RF-043, RF-045, DEC-160)

> Dado un partido `PROGRAMADO`, cuando se reprograma con nueva fecha y motivo, entonces pasa a
> `REPROGRAMADO` y el historial guarda usuario, fecha y motivo.

**Resultado obtenido** ([respuestas](evidencias/H-15/respuestas-api.txt), [base y auditoría](evidencias/H-15/base-y-auditoria.txt)):

| Paso | Respuesta |
| --- | --- |
| Programador: `POST /partidos/{P1}/transiciones` con `REPROGRAMADO`, nuevo `inicio` (21:18), `canchaId` de la Cancha 2 y el motivo «El equipo visitante pidió cambio por el torneo regional» | `200` con el detalle en `REPROGRAMADO`, el inicio 21:18, la Cancha Demostración 2, el bloque 21:18–23:18 recalculado y `accionesPermitidas: []` (DEC-162: el reprogramado espera su hora) |
| `GET /partidos/{P1}/historial` | Dos cambios: alta `PROGRAMADO` («Programación del partido») y `PROGRAMADO → REPROGRAMADO` con el motivo, `cambiadoPor` «Programador de prueba» y `cambiadoEn` 21:09:48 |
| Auditoría | `CAMBIAR_ESTADO_PARTIDO` (M-08) a nombre del Programador, con el motivo y el detalle `estadoAnterior`, `estadoNuevo`, inicio y cancha anteriores y nuevos, sin datos personales |

Desde F-25, el Programador reprogramó P2 con el modal: nueva fecha 30-nov 17:00, la Cancha
Demostración 2 y el motivo «La cancha 1 estará ocupada por el torneo infantil», con el contador 49/500
([captura](evidencias/H-15/CA1-modal-reprogramar.png)). Al confirmar, el encabezado pasó a
«REPROGRAMADO» con la nueva fecha y cancha, y la pestaña «Historial» mostró el cambio con su motivo,
fecha y autor ([captura](evidencias/H-15/CA1-reprogramado-desde-f25-historial.png)). El historial de P1
se ve igual en F-25 ([captura](evidencias/H-15/CA1-reprogramado-historial.png)).

**Resultado:** ✅ Aprobado.

### CA-2 · Una transición no permitida se rechaza con 409 (RF-046, VAL-008, DEC-162)

> Dada una transición no permitida (por ejemplo, suspender un `PROGRAMADO`), cuando se intenta,
> entonces se rechaza con `409 VAL-008/RF-046`.

**Resultado obtenido** ([respuestas](evidencias/H-15/respuestas-api.txt)):

| Petición (Programador) | Respuesta |
| --- | --- |
| Suspender P2 `PROGRAMADO` | `409 RF-046` «Un partido PROGRAMADO no puede pasar a SUSPENDIDO.» con `estadoActual: PROGRAMADO` y `accionesPermitidas: [REPROGRAMAR, CANCELAR]` |
| Finalizar P2 a mano | `409 RF-046` «El partido no se pasa a FINALIZADO a mano: inicia a su hora y se finaliza al aprobarse su cédula (RF-127).» |
| Iniciar P2 a mano (`EN_CURSO`) | `409 RF-046` con el mismo mensaje para `EN_CURSO` |
| Cancelar P1 `REPROGRAMADO` | `409 RF-046` «Un partido REPROGRAMADO no puede pasar a CANCELADO. Su estado ya no admite cambios manuales.» con `accionesPermitidas: []` |
| Reprogramar P3 `CANCELADO` | `409 RF-046` «Un partido CANCELADO no puede pasar a REPROGRAMADO…» con `accionesPermitidas: []` |
| Pasar P1 `EN_CURSO` a `EN_CURSO` (después del CA-3) | `409 RF-046` con `estadoActual: EN_CURSO` y `accionesPermitidas: [SUSPENDER]` |
| Cancelar P2 con el motivo «corto» | `400 RN-021` «Explica el motivo del cambio de estado con 10 a 500 caracteres…» |

Después de los rechazos, el historial de P2 seguía con un solo cambio (su alta) y la auditoría no tiene
eventos de las peticiones rechazadas: solo hay uno por cada cambio aceptado
([base y auditoría](evidencias/H-15/base-y-auditoria.txt)). El código de error es `RF-046`, el que
también lanza el trigger `tg_partidos_estado`. DEC-162 lo fija así para cumplir VAL-008.

**Resultado:** ✅ Aprobado.

### CA-3 · La tarea programada inicia el partido a su hora una sola vez (RF-127, ADR-0009, DEC-163)

> Dada la hora de inicio, cuando corre la tarea programada, entonces el partido pasa a `EN_CURSO` una
> sola vez aunque la tarea se repita.

**Resultado obtenido** ([base y auditoría](evidencias/H-15/base-y-auditoria.txt), [respuestas](evidencias/H-15/respuestas-api.txt)):

- A las **21:15**, la hora original de P1, la tarea no lo inició: ya estaba reprogramado para las 21:18.
- A las **21:18:00** la tarea pasó P1 de `REPROGRAMADO` a `EN_CURSO`. La bitácora del backend registró
  «Inicio de partidos: 1 partido(s) en curso en 14 ms», sin datos personales.
- La tarea volvió a correr a las **21:19** y a las **21:20**. Después de las tres ejecuciones, P1 tiene
  **un** cambio a `EN_CURSO` en `partido_estado_historial` y **un** evento `INICIAR_PARTIDO` en la
  auditoría, y la bitácora no muestra más inicios.
- El cambio quedó a nombre del perfil **Sistema** con el motivo fijo «inicio automático por fecha y
  hora». La auditoría guarda `{"estadoAnterior": "REPROGRAMADO", "estadoNuevo": "EN_CURSO", "inicio": …}`.
- P2 (30-nov) y P3 (cancelado) no cambiaron.

En F-25, P1 aparece «EN CURSO» y su historial muestra el paso `REPROGRAMADO → EN CURSO` a las 21:18 por
«Sistema», debajo del cambio del Programador
([captura](evidencias/H-15/CA3-en-curso-por-sistema-presidente-sin-acciones.png)).

**Resultado:** ✅ Aprobado.

### CA-4 · El detalle muestra solo las acciones permitidas para el estado y el usuario (RF-046, RN-019, UI-010)

> El detalle muestra solo las acciones permitidas para el estado y el usuario.

**Resultado obtenido** ([respuestas](evidencias/H-15/respuestas-api.txt)):

| Cuenta y partido | `accionesPermitidas` (API) | F-25 |
| --- | --- | --- |
| Programador · `PROGRAMADO` (P1 antes del CA-1 y P2) | `[REPROGRAMAR, CANCELAR]` | Botones «Reprogramar» y «Cancelar partido» ([captura](evidencias/H-15/CA4-programador-programado-reprogramar-cancelar.png)) |
| Programador · `EN_CURSO` (P1) | `[SUSPENDER]` | Solo «Suspender» ([captura](evidencias/H-15/CA4-programador-en-curso-solo-suspender.png)) |
| Programador · `REPROGRAMADO` (P1 antes de su hora, P2) | `[]` | «Este estado ya no admite cambios manuales.» ([captura](evidencias/H-15/CA1-reprogramado-historial.png)) |
| Programador · `CANCELADO` (P3) | `[]` | Sin botones |
| Presidente · cualquier estado (listado y detalle) | `[]` | Sin barra de acciones, ni con P3 cancelado ([captura](evidencias/H-15/CA4-presidente-cancelado-sin-acciones.png)) ni con P1 en curso ([captura](evidencias/H-15/CA3-en-curso-por-sistema-presidente-sin-acciones.png)) |
| Directiva de Club Halcones · P1 | Solo P1 en el listado (`totalElementos: 1`), con `[]` | Sin barra de acciones ([captura](evidencias/H-15/CA4-directiva-su-partido-sin-acciones.png)) |
| Directiva de Club Halcones · P2 (de otro club) | `404 NO_ENCONTRADO` | «No encontramos esta información…» ([captura](evidencias/H-15/CA4-directiva-partido-de-otro-club-404.png)) |

La cancelación de P3 desde `PROGRAMADO` respondió `200` con el detalle en `CANCELADO` y
`accionesPermitidas: []`. El servidor sigue cerrando la puerta aunque la interfaz no muestre botones:

| Petición | Respuesta |
| --- | --- |
| Cancelar P2 sin sesión | `401 NO_AUTENTICADO` |
| `GET /partidos/{P2}` sin sesión | `401 NO_AUTENTICADO` |
| Presidente cancela P2 | `403 VAL-015` |
| Directiva cancela P1 (su propio partido) | `403 VAL-015` |
| Programador cancela un partido inexistente | `404 NO_ENCONTRADO` |
| Programador pide el historial de un partido inexistente | `404 NO_ENCONTRADO` |

**Resultado:** ✅ Aprobado.

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 Reprogramar con historial (API y F-25) | ✅ Aprobado |
| CA-2 Transición no permitida responde 409 RF-046 | ✅ Aprobado |
| CA-3 Inicio automático idempotente | ✅ Aprobado |
| CA-4 Acciones según estado y usuario | ✅ Aprobado |

## 5. Pruebas automáticas relacionadas

Verificación ejecutada sobre `0a62518` el 24-nov-2026 (detalle en
[`pruebas-automaticas.txt`](evidencias/H-15/pruebas-automaticas.txt)):

| Subproyecto | Comando | Resultado |
| --- | --- | --- |
| Backend | `./mvnw verify` | **BUILD SUCCESS**: 2296 pruebas unitarias y de controlador, 0 fallos; 362 de integración, 0 fallos, 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS). Incluye `PartidosControllerTest` (30), `PartidosServiceTest` (44), `InicioPartidosServiceTest` (5), `PartidosIT` (10) e `InicioPartidosIT` (5) |
| Frontend | `npx vitest run src/features/partidos src/features/programacion` | 3 archivos, 40 pruebas, 0 fallos (11 de `PantallaDetallePartido.test`) |

| Criterio | Pruebas que ya lo cubren |
| --- | --- |
| CA-1 | `PartidosIT` «RF-045: reprogramar guarda usuario, fecha y motivo, mueve el bloque y lo audita»; `PartidosServiceTest` «DEC-160: reprogramar a otra cancha valida conflictos con la cancha nueva y la audita»; `PartidosControllerTest` «RF-043: POST /partidos/{id}/transiciones reprograma y devuelve el detalle»; `PantallaDetallePartido.test` «RF-043: reprograma con nueva fecha, cancha y motivo y muestra el detalle actualizado» |
| CA-2 | `PartidosIT` «RF-046: suspender un partido programado y finalizarlo a mano responden 409 RF-046» y «RF-046: cancelado no admite más transiciones y la prohibida responde 409 sin escribir»; `PartidosServiceTest` «RF-046: cada transición prohibida responde 409 RF-046 sin escribir» y los de RN-021; `PartidosControllerTest` «RF-046: la transición no permitida responde 409 con estadoActual y accionesPermitidas»; `PantallaDetallePartido.test` «RF-046: una transición rechazada muestra la causa en el modal» |
| CA-3 | `InicioPartidosIT` «ADR-0009 §2: ejecutar dos veces seguidas inicia una sola vez y deja un solo historial y auditoría» y «RF-127: un REPROGRAMADO también inicia a su nueva hora…»; `InicioPartidosServiceTest` «ADR-0009 §4: el cambio corre con la sesión del perfil Sistema…». El cron real cada minuto solo se comprueba en esta corrida |
| CA-4 | `PartidosServiceTest` «RF-046: el detalle muestra solo las acciones que admite el estado» y «RF-046: sin partidos.partido:CAMBIAR_ESTADO las acciones van vacías»; `PartidosIT` «VAL-015: el Presidente consulta sin acciones…» y «ADR-0007: la Directiva ve los partidos de su club…»; `PartidosControllerTest` «RNF-003: seguridad de …» (401/403); `PantallaDetallePartido.test` «RF-046: la barra muestra solo las acciones permitidas del API» y «RF-046: sin acciones explica por qué a quien puede cambiar estados, y nada a quien solo consulta» |

## 6. Defectos y observaciones

**Defectos:** ninguno.

**O-1 · La auditoría mezcla dos formatos para la hora del partido.** En `CAMBIAR_ESTADO_PARTIDO`,
`inicioAnterior` se guarda con el desfase de Morelia (`2026-11-24T21:15-06:00`). En cambio, `inicioNuevo`
y el `inicio` de `INICIAR_PARTIDO` se guardan en UTC (`2026-11-25T03:18Z`). Las dos formas son el mismo
instante y la pantalla de auditoría no hace cálculos con ellas. Solo dificulta leer el detalle en crudo.
Se sugiere guardar todas en UTC, o todas con el desfase, en un cambio futuro.

**O-2 · La tarjeta «Cédula» de F-25 dice «La cédula se abre cuando el partido está en curso» aunque P1
ya está en curso.** Es el texto que dejó H-15.3 antes de que existiera la captura de la cédula. La cédula
es de H-16 (la pantalla de captura entró en `develop` con H-16.3, después de la versión probada), así
que se revisa en la prueba de aceptación de H-16. No afecta a los criterios de H-15.

## 7. Conclusión

H-15 cumple sus cuatro criterios. El partido se reprograma con motivo por la API y desde F-25, y el
historial guarda usuario, fecha y motivo. Las transiciones que la máquina de DEC-23 no admite se rechazan
con `409 RF-046` sin escribir nada. La tarea programada inicia el partido a su hora una sola vez, como el
perfil Sistema. El detalle muestra solo las acciones que admiten el estado y los permisos de la cuenta, y
el servidor responde `401`, `403` y `404` aunque alguien se salte la interfaz. **H-15 puede cerrarse.**
Las observaciones O-1 y O-2 no bloquean el cierre.
