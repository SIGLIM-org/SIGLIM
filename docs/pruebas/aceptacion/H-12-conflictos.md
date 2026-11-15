# Prueba de aceptación · H-12 Programar una jornada con detección de conflictos explicada

| Campo | Valor |
| --- | --- |
| Historia | H-12 · subtarea H-12.5 · Sprint 3 · Must · 5 SP |
| Requisitos | RF-033, RF-036, RF-039, RF-040, VAL-003, VAL-004, VAL-005, VAL-006, UI-009, RF-032, VAL-015, VAL-024 |
| Pantallas | F-22 (programación de jornadas) |
| Implementó | H-12.1 (#126), H-12.2 (#129), H-12.3 (#131) y H-12.4 (#132) |
| Versión probada | `develop` en `53cc39b` |
| Fecha | 2026-11-14 |
| Ejecutó | Agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado** (5 de 5 criterios y 2 complementarios) |

La corrida valida de punta a punta la historia H-12: el Programador crea una jornada en borrador,
registra partidos desde F-22 y el sistema rechaza cada conflicto duro con un mensaje que explica la
causa. La verificación lista todos los conflictos sin escribir y el ajuste exige motivo y deja traza
en la auditoría. Se usó el frontend, el backend y PostgreSQL reales sobre una base local efímera.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17.11 local y efímero, con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las migraciones del repositorio aplicadas |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS con una llave ES256 y responde el inicio de sesión con contraseña de las cuentas ficticias |
| Backend | `java -jar target/siglim-0.1.0-SNAPSHOT.jar` en `http://127.0.0.1:8080`, Java 25.0.5.0.3 y Spring Boot 4.1.1, contra la base local y la autenticación local |
| Frontend | `npm run dev -- --port 5173` en `http://localhost:5173` (Vite 8.3.5), con `VITE_API_URL` hacia el backend local |
| Navegador | Chromium 1248 sin interfaz (Playwright 1.64.1), ventana de 1440 × 1200 px, idioma `es-MX` |
| Automatización | Backend `./mvnw verify`: **269 pruebas, 0 fallos, 1 omitida** (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS), incluida `ProgramacionIT` con Testcontainers. Frontend `npm test`: **82 archivos y 741 pruebas, 0 fallos**; `npm run lint` y `npm run build` sin errores. |

La prueba corrió sobre una **base local efímera** creada desde la simulación de Supabase y las
migraciones del repositorio: **no se alteró el proyecto compartido de Supabase**. El detalle de la corrida automática está en
[`pruebas-automaticas.txt`](evidencias/H-12/pruebas-automaticas.txt). El arnés de
autenticación, las cuentas y el recorrido de Playwright viven fuera del repositorio; aquí solo se
versionan este documento y sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Programador de prueba** | Programador de partidos · LIGA | CA-1 a CA-6: crea la jornada, registra y ajusta partidos, verifica conflictos |
| **Tesorero de prueba** | Tesorero · LIGA | Complementario: 403 sin `programacion.jornada:EDITAR` |

Datos ficticios sembrados en la base local:

| Dato | Situación inicial | Uso en la prueba |
| --- | --- | --- |
| **Temporada 2026-2027** | `ACTIVA`, con parámetros y bloque de cancha [DEMO] de 120 min | Contexto de F-22 |
| **Libre Primera · Grupo A** | 12 equipos: Halcones, Toros, Águilas, Pumas, Leones, Tigres, Búhos, Zorros, Osos, Venados, Coyotes y Mapaches | Partidos de las dos jornadas |
| **Jornada 1** | Borrador, con Halcones vs. Toros y Águilas vs. Pumas el 21/11/2026 a las 10:00 | CA-4: verificación con conflictos posteriores |
| **Jornada 2** | Borrador, con Osos vs. Venados (Oriente) y Coyotes vs. Mapaches (Norte) el 22/11/2026 a las 10:00 | CA-1, CA-2, CA-3, CA-5 y CA-6 |
| **Canchas Morelos 1 y 2, Norte y Oriente** | Activas; Morelos 1 y 2 reciben bloqueo y mantenimiento después de programar, y Oriente un bloqueo el 22/11/2026 | Partidos, CA-3 y CA-4 |
| **Cancha Sur** | Activa y libre el 22/11/2026 | CA-2: cancha libre para aislar VAL-003 |
| **Cancha Auxiliar** | Inactiva | CA-3: cancha inactiva |
| **Toros, Zorros y Venados** | `BAJA` después de programar | CA-4 y CA-6: equipo no elegible |
| **Leones** | Suspensión vigente de 2 partidos | CA-6: sanción |

El recorrido no usa datos personales de personas reales: solo clubes, equipos, canchas y cuentas
ficticias. Los identificadores de la auditoría son UUID de la base local.

## 3. Escenarios

### CA-1 · Una cancha ocupada se rechaza con 409 VAL-004 y el mensaje nombra el otro partido, la cancha y el horario

> Dado un partido cuya cancha ya está ocupada en el bloque, cuando se guarda, entonces se rechaza
> con 409 VAL-004 y el mensaje nombra el otro partido, la cancha y el horario.

**Pasos**

1. Iniciar sesión como **Programador de prueba** y abrir **Programación › Jornadas**
   (`/app/programacion/jornadas`).
2. Elegir **Jornada 2 · Borrador** y pulsar **Programar partido**.
3. Elegir **Osos** vs. **Coyotes**, la **Cancha Oriente** y el inicio **22/11/2026 10:30** (el
   partido Osos vs. Venados ya ocupa esa cancha de 10:00 a 12:00).
4. Pulsar **Programar partido**.

**Resultado esperado**

- La API responde `409` con `codigo: "VAL-004"`.
- El mensaje nombra el partido que ocupa la cancha, la cancha y el horario completo.
- El partido no se guarda: la jornada conserva sus dos partidos.

**Resultado obtenido:** **Aprobado.** La pantalla mostró «La cancha «Cancha Oriente» ya está
ocupada por el partido «Osos vs. Venados» del 22/11/2026 10:00 a 22/11/2026 12:00.» y la API
respondió `409` con `codigo: "VAL-004"`. La jornada siguió con dos partidos.

**Evidencia:** [`CA1-cancha-ocupada.png`](evidencias/H-12/CA1-cancha-ocupada.png);
respuesta cruda en [`respuestas-api.txt`](evidencias/H-12/respuestas-api.txt) (VAL-004).

**Pruebas automáticas relacionadas:** `ProgramacionIT#canchaOcupada`,
`ProgramacionIT#triggerRespaldaCanchaOcupada`, `ValidadorConflictosTest#canchaOcupada` y
`PantallaProgramacion.test.tsx` (marca la fila y deshabilita el envío).

### CA-2 · Un equipo con otro partido en el bloque se rechaza con 409 VAL-003

> Dado un equipo con otro partido en el bloque, cuando se guarda, entonces se rechaza con VAL-003.

**Pasos**

1. En la misma jornada, pulsar **Programar partido**.
2. Elegir **Osos** vs. **Coyotes**, la **Cancha Sur** (libre) y el inicio **22/11/2026 10:30**.
3. Pulsar **Programar partido**.

**Resultado esperado**

- La API responde `409` con `codigo: "VAL-003"`.
- El mensaje nombra el equipo y el partido que ya lo ocupa, con cancha y horario.
- El partido no se guarda.

**Resultado obtenido:** **Aprobado.** La pantalla mostró «El equipo «Coyotes» ya tiene el partido
«Coyotes vs. Mapaches» del 22/11/2026 10:00 a 22/11/2026 12:00 en la cancha «Cancha Norte».» y la
API respondió `409` con `codigo: "VAL-003"`. No se agregó el partido.

**Evidencia:** [`CA2-equipo-ocupado.png`](evidencias/H-12/CA2-equipo-ocupado.png);
respuesta cruda en [`respuestas-api.txt`](evidencias/H-12/respuestas-api.txt) (VAL-003).

**Pruebas automáticas relacionadas:** `ProgramacionIT#equipoDuplicado`,
`ProgramacionIT#triggerRespaldaEquipoOcupado`, `ValidadorConflictosTest#equipoOcupado`.

### CA-3 · Una cancha bloqueada o inactiva se rechaza con 409 VAL-005

> Dada una cancha bloqueada o inactiva, cuando se asigna, entonces se rechaza con VAL-005.

**Pasos**

1. **Bloqueo:** en la Jornada 2, pulsar **Programar partido**, elegir **Venados** vs. **Coyotes**,
   la **Cancha Oriente** y el inicio **22/11/2026 12:00**. La cancha tiene un bloqueo de 12:00 a
   14:00 por «Riego del césped».
2. **Inactiva:** repetir el alta contra la **Cancha Auxiliar**, que está inactiva. La interfaz no
   la ofrece en el selector (solo lista canchas activas), así que este caso se comprobó con la API.

**Resultado esperado**

- El bloqueo responde `409` con `codigo: "VAL-005"` y explica el tipo, el periodo y el motivo.
- La cancha inactiva responde `409` con `codigo: "VAL-005"` y dice que está inactiva.
- Ninguno de los dos partidos se guarda.

**Resultado obtenido:** **Aprobado.** El bloqueo mostró «La cancha «Cancha Oriente» tiene un
bloqueo del 22/11/2026 12:00 a 22/11/2026 14:00: Riego del césped.» y la API respondió `409`
`VAL-005`. La cancha inactiva respondió `409` `VAL-005` con «La cancha «Cancha Auxiliar» está
inactiva y no se puede asignar.». F-22 solo ofrece canchas activas, por lo que el caso inactivo se
verificó por API.

**Evidencia:** [`CA3-cancha-bloqueada.png`](evidencias/H-12/CA3-cancha-bloqueada.png);
respuestas crudas en [`respuestas-api.txt`](evidencias/H-12/respuestas-api.txt) (VAL-005 bloqueo e
inactiva).

**Pruebas automáticas relacionadas:** `ProgramacionIT#canchaBloqueada`,
`ProgramacionIT#canchaEnMantenimiento`, `ProgramacionIT#canchaInactiva`,
`ValidadorConflictosTest#canchaBloqueada`, `ValidadorConflictosTest#canchaInactiva`.

### CA-4 · «Verificar conflictos» lista todos los conflictos sin guardar

> Dada una jornada, cuando se pide «Verificar conflictos», entonces se listan todos sin guardar.

**Pasos**

1. En la **Jornada 1**, con dos partidos ya programados, bloquear la Cancha Morelos 1 (bloqueo de
   09:00 a 11:00), poner en mantenimiento la Cancha Morelos 2 (10:00 a 11:00) y dar de baja al
   equipo Toros. Son hechos posteriores a la programación; la base ya los permite.
2. Abrir la jornada y revisar el panel **Validación de la jornada** y la marca de cada fila.
3. Comparar el número de partidos y de filas de auditoría antes y después de verificar.

**Resultado esperado**

- El panel lista los tres conflictos: dos de cancha (`VAL-005`) y uno de equipo (`VAL-006`).
- Las filas afectadas se marcan con su explicación y el envío a aprobación queda deshabilitado.
- La verificación no escribe partidos ni auditoría.

**Resultado obtenido:** **Aprobado.** El panel mostró «3 conflictos por resolver» (2 de «Cancha no
disponible» y 1 de «Equipo no elegible»), las dos filas quedaron marcadas con el mensaje completo y
el botón **Enviar a aprobación** permaneció deshabilitado. La jornada conservó 2 partidos y las
mismas 3 filas de auditoría antes y después de verificar; la respuesta trae `conflictos` y
`advertencias: []`.

**Evidencia:** [`CA4-verificacion-conflictos.png`](evidencias/H-12/CA4-verificacion-conflictos.png);
[`verificacion-conflictos.json`](evidencias/H-12/verificacion-conflictos.json);
conteos en [`historial-auditoria.txt`](evidencias/H-12/historial-auditoria.txt).

**Pruebas automáticas relacionadas:** `ProgramacionIT#verificarListaConflictosSinEscribir`,
`ProgramacionIT#verificarSancionesDeAmbosEquipos`,
`JornadaServiceTest#verificarAcumulaConflictosDeTodosLosPartidos`,
`JornadaServiceTest#verificarJornadaVacia`, `ProgramacionControllerTest#verificar`.

### CA-5 · El ajuste exige motivo y deja traza en la historia y la auditoría

> Dado un ajuste a un partido existente, cuando se guarda sin motivo, entonces se rechaza; con
> motivo, queda en el historial y la auditoría (RF-040).

**Pasos**

1. **Sin motivo:** en la Jornada 2, editar **Coyotes vs. Mapaches**, cambiar el inicio al
   23/11/2026 10:00 y dejar vacío el campo **Motivo del ajuste**. La pantalla mantiene
   **Guardar cambios** deshabilitado. Repetir la petición directa a la API sin `motivo`.
2. **Con motivo:** escribir «Cambio de fecha solicitado por la Liga para evitar el mantenimiento de
   la cancha.» y guardar.
3. Consultar `partido_estado_historial` y `siglim.auditoria` del partido.

**Resultado esperado**

- Sin motivo: la pantalla no permite guardar y la API responde `400` `RN-021`; el partido no cambia.
- Con motivo: la API responde `200`, la pantalla confirma que el cambio quedó en el historial y la
  auditoría, y `AJUSTAR_PARTIDO` registra el motivo y los campos cambiados.
- El partido conserva su estado `PROGRAMADO`; el historial de estados solo registra el alta.

**Resultado obtenido:** **Aprobado.** Sin motivo, **Guardar cambios** quedó deshabilitado y la API
respondió `400` `RN-021` («Escribe un motivo de al menos 10 caracteres para ajustar el partido.»).
Con motivo, la pantalla mostró «Partido ajustado. El cambio quedó en el historial y la auditoría.»
y la API respondió `200`; el partido quedó el 23/11/2026. La auditoría registró **un**
`AJUSTAR_PARTIDO` con su motivo y `{"campos":["inicio"]}`; `partido_estado_historial` conserva
solo el alta porque el ajuste no cambia de estado (RF-045 reserva esa tabla a las transiciones).
La traza de RF-040 es la auditoría.

**Evidencia:** [`CA5-sin-motivo.png`](evidencias/H-12/CA5-sin-motivo.png),
[`CA5-con-motivo.png`](evidencias/H-12/CA5-con-motivo.png);
respuestas y consultas en [`respuestas-api.txt`](evidencias/H-12/respuestas-api.txt) y
[`historial-auditoria.txt`](evidencias/H-12/historial-auditoria.txt).

**Pruebas automáticas relacionadas:** `ProgramacionIT#ajusteConMotivo`,
`ProgramacionIT#ajusteSinMotivo`, `JornadaServiceTest#ajustarConMotivo`,
`JornadaServiceTest#ajustarSinMotivo`, `PantallaProgramacion.test.tsx` (ajuste con motivo y aviso).

### CA-6 · Un equipo dado de baja o sancionado se rechaza con 409 VAL-006 (complementario)

> Dada una asignación con un equipo con baja o sanción, cuando se guarda, entonces se rechaza con
> VAL-006.

**Pasos**

1. Dar de baja al equipo **Venados** después de haber programado Osos vs. Venados.
2. Abrir el partido en F-22 y revisar el aviso del modal.
3. Intentar el alta por API con un equipo `BAJA` (Zorros) y con un equipo con suspensión vigente
   (Leones, 2 partidos).

**Resultado esperado**

- El modal avisa que el equipo está dado de baja y no permite guardar el ajuste.
- La API responde `409` `VAL-006` y explica la baja o la suspensión vigente.

**Resultado obtenido:** **Aprobado.** El modal mostró «El equipo «Venados» está dado de baja y no
puede jugar. Elige un equipo activo para guardar el ajuste.» y **Guardar cambios** quedó
deshabilitado. La API respondió `409` `VAL-006` con «El equipo «Zorros» está dado de baja y no se
puede programar.» y «El equipo «Leones» tiene una suspensión vigente de 2 partido(s) y no puede
jugar.».

**Evidencia:** [`CA6-equipo-baja.png`](evidencias/H-12/CA6-equipo-baja.png);
respuestas crudas en [`respuestas-api.txt`](evidencias/H-12/respuestas-api.txt) (VAL-006 baja y
sanción).

**Pruebas automáticas relacionadas:** `ProgramacionIT#equipoDadoDeBaja`,
`ProgramacionIT#equipoSancionado`, `ProgramacionIT#equipoConSuspensionCumplida`,
`ValidadorConflictosTest#evaluarAcumulaEquiposDeBaja`.

### Complementario · 401, 403 y 404 de los endpoints de programación (VAL-015, VAL-024)

**Pasos**

1. `POST /api/v1/jornadas` sin token.
2. `POST /api/v1/jornadas` con el token del **Tesorero**, que no tiene
   `programacion.jornada:EDITAR`.
3. `GET /api/v1/jornadas/{id}` con un id inexistente.

**Resultado esperado**

- `401` `NO_AUTENTICADO` sin token.
- `403` `VAL-015` sin el permiso, sin tocar el servicio.
- `404` `NO_ENCONTRADO` con un id inexistente o de otra liga (VAL-024).

**Resultado obtenido:** **Aprobado.** Las respuestas fueron `401` `NO_AUTENTICADO`, `403` `VAL-015`
y `404` `NO_ENCONTRADO`, con el mismo detalle para un id inexistente que para uno fuera de ámbito
(lo cubre el arnés de autorización de `ProgramacionControllerTest`).

**Evidencia:** [`respuestas-api.txt`](evidencias/H-12/respuestas-api.txt) (401, 403 y 404).

**Pruebas automáticas relacionadas:** `ProgramacionControllerTest` (seis `@TestFactory` de
seguridad con `ArnesAutorizacionEndpoint` para GET/POST de jornadas, partidos y verificación) y
`JornadaServiceTest#sinPermiso`.

## 4. Defectos encontrados

Ninguno. La corrida no encontró defectos que impidan cerrar H-12.

**Observación de diseño:** el ajuste de horario no escribe en `partido_estado_historial`; su traza
queda en `siglim.auditoria` (`AJUSTAR_PARTIDO`) con el motivo y los campos cambiados. Es coherente
con que esa tabla registre solo transiciones de estado (RF-045, RN-021) y con lo que ya prueban
`ProgramacionIT#ajusteConMotivo` y `JornadaServiceTest#ajustarConMotivo`.

## 5. Conclusión

H-12 cumple sus cinco criterios de aceptación y los dos escenarios complementarios (VAL-006 y
seguridad de los endpoints). El flujo de F-22 permite armar la jornada en borrador, rechaza cada
conflicto duro con un mensaje que explica la causa, lista todos los conflictos sin guardar y exige
motivo en cada ajuste con traza en la auditoría. La historia puede pasar a **Finalizado** una vez
aprobado el PR de esta subtarea.
