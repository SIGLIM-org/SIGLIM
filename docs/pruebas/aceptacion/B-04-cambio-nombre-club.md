# Prueba de aceptación · B-04 Registrar el cambio de nombre de un club como club nuevo

| Campo | Valor |
| --- | --- |
| Historia | B-04 (SCRUM-213) · subtarea B-04.3 (SCRUM-374) · Sprint 2 · B · 1 SP |
| Requisitos | RF-105, RN-005, RN-021, RN-041, RN-052, DEC-15, DEC-49, DEC-61, VAL-015, VAL-024 |
| Pantallas | F-15 (clubes con detalle y pestaña de equipos) |
| Versión probada | `develop` en `5c319e7`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000`, `20261103100000` y `20261106160000` |
| Fecha | 2026-11-08 |
| Ejecutó | Agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado** (4 de 4 criterios; V1 y V2 aprobadas) |

La corrida valida de punta a punta el cambio de nombre de club y los cuatro puntos de **DEC-61**:
temporadas `ACTIVA` y `CONFIGURACION`, cobros `PENDIENTE` o `VENCIDO`, traslado de asignaciones de
ámbito `CLUB` y unicidad exacta del nombre contando los clubes cerrados. DEC-61 sigue marcada como
`[PROPUESTA]` en el registro de decisiones: esta prueba confirma su comportamiento, pero la
confirmación formal con el PO queda como observación al final.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17.11 local en un contenedor Docker efímero (puerto 55433), con la simulación del entorno de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cinco migraciones del repositorio aplicadas |
| Autenticación | Servidor JWKS local que publica una llave ES256 y responde el inicio de sesión que usa el frontend; las cuentas se sembraron en `auth.users` y `siglim.perfiles` |
| Backend | `java -jar target/siglim-0.1.0-SNAPSHOT.jar` en `http://localhost:8080`, Java 25.0.5.0.3 y Spring Boot 4.1.1, contra la base local y el JWKS local |
| Frontend | `npm run dev` en `http://localhost:5173` (Vite 8.3.5, proxy hacia el backend) |
| Navegador | Chromium 156.0.8078.4 sin interfaz, Playwright 1.65.0, ventana de 1440 × 1100 px, idioma `es-MX` y zona `America/Mexico_City` |

La prueba se ejecutó sobre una **base local efímera**, recreada desde la simulación de Supabase y las
migraciones del repositorio: no se alteró el proyecto compartido de Supabase. El arnés (JWKS local,
datos de siembra y recorrido de Playwright) vive fuera del repositorio; aquí solo se versionan este
documento y sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Secretario** | Secretario · LIGA | `organizacion.club:CONSULTAR`, `organizacion.club:EDITAR` (LIGA), `organizacion.equipo:CONSULTAR` |
| **Directiva** | Directiva de club · ámbito CLUB | `organizacion.club:CONSULTAR` (solo su club) y `organizacion.equipo:CONSULTAR` |

Datos sembrados en la base local; todos son ficticios y se conservaron sin cambios fuera de los
flujos descritos:

| Club | Situación inicial | Uso en la prueba |
| --- | --- | --- |
| **Club Halcones B-04** | `ACTIVO`, sin equipos ni cobros | CA-1: cambia de nombre a **Club Águilas B-04**; CA-4: su Directiva debe seguir al club nuevo |
| **Club con equipo en curso B-04** | `ACTIVO`, con el equipo `Equipo en curso B-04` en la temporada **ACTIVA** | CA-2: bloqueo por equipos (DEC-49, DEC-61.1) |
| **Club con equipo en preparación B-04** | `ACTIVO`, con el equipo `Equipo en preparación B-04` en una temporada en **CONFIGURACION** | CA-2: bloqueo por equipos en la temporada que se prepara (DEC-61.1) |
| **Club con adeudo B-04** | `ACTIVO`, con un cobro `PENDIENTE` y otro `VENCIDO` | CA-3: bloqueo por adeudos (DEC-61.2, RN-052) |
| **Club con cobros liquidados B-04** | `ACTIVO`, con un cobro `PAGADO` y otro `RECHAZADO` | CA-3 (control): sí cambia de nombre; los cobros se quedan en el anterior |

El estado inicial de clubes, asignaciones y auditoría está en
[`CA1-CA4-estado-inicial.txt`](evidencias/B-04/CA1-CA4-estado-inicial.txt).

## 3. Escenarios

### CA-1 · El cambio crea un club nuevo ACTIVO y cierra el anterior con fecha y motivo

> Dado un club activo sin equipos inscritos ni adeudos, cuando el Secretario registra su cambio de
> nombre, entonces se crea un club nuevo `ACTIVO` que proviene del anterior y el anterior queda
> `CERRADO` con fecha y motivo, sin borrar su historial (RF-105, RN-041, DEC-15).

**Pasos**

1. Iniciar sesión como **Secretario** y abrir Organización › Clubes › «Club Halcones B-04».
2. Pulsar **Cambiar nombre**, capturar «Club Águilas B-04» y el motivo «La asamblea del club aprobó
   el cambio de nombre»; revisar el aviso de que la operación no se puede deshacer.
3. Enviar el formulario y revisar el club nuevo, la respuesta de la API y el enlace **Proviene de**.
4. Abrir el club anterior desde ese enlace y revisar su estado, su fecha, su motivo y sus acciones.

**Resultado esperado**

- El `POST /api/v1/clubes/{id}/cambio-nombre` responde `201` con `Location` al club nuevo.
- El club nuevo nace `ACTIVO`, con `clubAnterior` y sin equipos; la pantalla navega a su detalle.
- El club anterior queda `CERRADO` con la fecha del día en `America/Mexico_City` y el motivo, y su
  detalle ya no ofrece «Cambiar nombre», «Registrar equipo», «Editar» ni «Dar de baja».

**Resultado obtenido:** **Aprobado.** La API respondió `201` con
`Location: /api/v1/clubes/292c5259-e288-469a-9d4d-fa109ec6728f` y el club nuevo
«Club Águilas B-04» en `ACTIVO`, con `clubAnterior: { id, nombre: "Club Halcones B-04" }`. La
pantalla navegó al club nuevo y mostró «Proviene de «Club Halcones B-04»». Al abrir el anterior, su
insignia decía **CERRADO**, con «Cerrado el 8 nov 2026» y el motivo capturado, el aviso «Un club
cerrado no recibe equipos nuevos.» y ninguna acción de escritura. El club anterior conservó su fila,
su nombre y su historial (RN-005).

**Evidencia**

- [`CA1-club-antes.png`](evidencias/B-04/CA1-club-antes.png)
- [`CA1-modal-aviso.png`](evidencias/B-04/CA1-modal-aviso.png)
- [`CA1-modal-capturado.png`](evidencias/B-04/CA1-modal-capturado.png)
- [`CA1-respuesta-cambio.json`](evidencias/B-04/CA1-respuesta-cambio.json)
- [`CA1-club-nuevo.png`](evidencias/B-04/CA1-club-nuevo.png)
- [`CA1-club-anterior-cerrado.png`](evidencias/B-04/CA1-club-anterior-cerrado.png)
- [`CA1-club-anterior-texto.txt`](evidencias/B-04/CA1-club-anterior-texto.txt)

**Pruebas automáticas relacionadas**

- `ClubServiceTest` · «RF-105, DEC-15: crea el club nuevo, cierra el anterior hoy en México, traslada
  asignaciones y audita».
- `ClubControllerTest` · «RF-105: POST cambio-nombre responde 201 con Location y el club nuevo con
  clubAnterior».
- `ClubesEquiposIT` · «B-04 · RF-105, DEC-15, DEC-61: cierra el club, crea el nuevo, traslada
  asignaciones y audita».
- `PantallaClubes.test.tsx` · «envía valores recortados, cierra el modal y navega al club nuevo, que
  enlaza al anterior» y «RN-041: un club CERRADO muestra fecha y motivo del cierre y no ofrece
  «Registrar equipo»».

### CA-2 · No se cambia el nombre con equipos inscritos en una temporada ACTIVA o en CONFIGURACION

> Dado un club con equipos no dados de baja en una temporada `ACTIVA` o en `CONFIGURACION`, cuando
> se intenta cambiar el nombre, entonces responde `409 RN-041` y no se crea ningún club nuevo
> (DEC-49, DEC-61.1).

**Pasos**

1. Como **Secretario**, abrir el club «Club con equipo en curso B-04», pulsar **Cambiar nombre** y
   enviar un nombre nuevo con motivo válido.
2. Revisar la alerta del modal y la respuesta de la API.
3. Repetir el intento con «Club con equipo en preparación B-04», cuyo equipo está en una temporada
   en `CONFIGURACION`.
4. Consultar en la base los clubes, los equipos y la auditoría.

**Resultado esperado**

- Ambos intentos responden `409 RN-041` con un mensaje que explica que el cambio se registra entre
  temporadas y cuántos equipos están inscritos.
- El modal sigue abierto con la alerta de bloqueo; no se crea ningún club y los equipos conservan su
  estado y su temporada.

**Resultado obtenido:** **Aprobado.** Los dos intentos respondieron `409 RN-041` con el detalle
«El cambio de nombre se registra entre temporadas, sin equipos inscritos en la temporada en curso o
en configuración; el club «…» tiene 1 equipo inscrito.». La alerta del modal mostró el código y el
mensaje del servidor, y el modal permaneció abierto. La consulta final confirmó los dos equipos en
`ACTIVO` en sus temporadas `ACTIVA` y `CONFIGURACION`, ningún club con el nombre intentado y cero
eventos de cambio de nombre para esos clubes.

**Evidencia**

- [`CA2-bloqueo-equipos.png`](evidencias/B-04/CA2-bloqueo-equipos.png)
- [`CA2-respuesta-bloqueo-equipos.json`](evidencias/B-04/CA2-respuesta-bloqueo-equipos.json)
- [`CA2b-bloqueo-equipos.png`](evidencias/B-04/CA2b-bloqueo-equipos.png)
- [`CA2b-respuesta-bloqueo-equipos.json`](evidencias/B-04/CA2b-respuesta-bloqueo-equipos.json)
- [`CA2-CA3-estado-bloqueos.txt`](evidencias/B-04/CA2-CA3-estado-bloqueos.txt)

**Pruebas automáticas relacionadas**

- `ClubServiceTest` · «DEC-49, DEC-61: con equipos inscritos en temporada activa o en configuración
  responde 409 RN-041».
- `ClubesEquiposIT` · «B-04 · DEC-49: con un equipo ACTIVO en la temporada ACTIVA responde 409 RN-041
  y nada cambia», «B-04 · DEC-61: con un equipo ACTIVO en una temporada en CONFIGURACION responde 409
  RN-041» y «B-04 · DEC-61: un equipo en BAJA o en una temporada CERRADA no impide el cambio de
  nombre».
- `PantallaClubes.test.tsx` · «409 RN-041 muestra la alerta de bloqueo con el detail y el modal sigue
  abierto».

### CA-3 · No se cambia con cobros PENDIENTE o VENCIDO; los adeudos se quedan en el club anterior

> Dado un club con cobros `PENDIENTE` o `VENCIDO`, cuando se intenta cambiar el nombre, entonces
> responde `409 RN-041`; los cobros se quedan en el club anterior y el cambio no sirve para evadir el
> adeudo (DEC-61.2, RN-052).

**Pasos**

1. Como **Secretario**, abrir «Club con adeudo B-04», pulsar **Cambiar nombre** y enviar un nombre
   nuevo con motivo válido.
2. Revisar la alerta y la respuesta de la API; consultar en la base los cobros y los clubes.
3. Abrir «Club con cobros liquidados B-04» (un cobro `PAGADO` y otro `RECHAZADO`) y cambiar su nombre
   a «Club con cobros liquidados F.C. B-04».
4. Consultar la relación entre el club anterior, el nuevo y sus cobros.

**Resultado esperado**

- El club con adeudos responde `409 RN-041` con el mensaje «El club tiene cobros pendientes o
  vencidos: liquídalos antes de registrar el cambio de nombre.»; no se crea ningún club.
- Los cobros `PENDIENTE` y `VENCIDO` siguen en el club original.
- El club con cobros liquidados sí cambia de nombre; sus cobros `PAGADO` y `RECHAZADO` permanecen en
  el club anterior, que queda `CERRADO`, y el nuevo no hereda cobros.

**Resultado obtenido:** **Aprobado.** El club con adeudo recibió `409 RN-041` y no generó club nuevo
ni auditoría. En la base, sus cobros siguieron `PENDIENTE` y `VENCIDO` en el mismo `club_id`. El club
con cobros liquidados cambió a «Club con cobros liquidados F.C. B-04» con `201`; los cobros `PAGADO`
y `RECHAZADO` continuaron ligados al club anterior, ya `CERRADO`, y la consulta
`club_anterior → club_nuevo` no devolvió ningún cobro para el club nuevo. El intento previo de usar
el nombre de un club ya cerrado también fue rechazado con `409 DUPLICADO` (V1).

**Evidencia**

- [`CA3-bloqueo-cobros.png`](evidencias/B-04/CA3-bloqueo-cobros.png)
- [`CA3-respuesta-bloqueo-cobros.json`](evidencias/B-04/CA3-respuesta-bloqueo-cobros.json)
- [`CA3-respuesta-cambio-cobros-liquidados.json`](evidencias/B-04/CA3-respuesta-cambio-cobros-liquidados.json)
- [`CA3-club-con-cobros-liquidados.png`](evidencias/B-04/CA3-club-con-cobros-liquidados.png)
- [`CA3-cobros-liquidados.txt`](evidencias/B-04/CA3-cobros-liquidados.txt)
- [`CA2-CA3-estado-bloqueos.txt`](evidencias/B-04/CA2-CA3-estado-bloqueos.txt)

**Pruebas automáticas relacionadas**

- `ClubServiceTest` · «DEC-61, RN-052: con cobros pendientes o vencidos responde 409 RN-041».
- `ClubControllerTest` · «RN-041: con equipos inscritos o adeudos responde 409 RN-041 en RFC 9457».
- `ClubesEquiposIT` · «B-04 · DEC-61, RN-052: con un cobro PENDIENTE responde 409 RN-041 y nada
  cambia».

### CA-4 · Las asignaciones de rol del club se trasladan al nuevo y la operación queda auditada

> Dadas las asignaciones de rol activas con ámbito `CLUB` del club anterior, cuando se cambia el
> nombre, entonces se revocan y se recrean en el club nuevo; la operación queda auditada en `M-01` y
> `M-03` (DEC-61.3, RF-093, RNF-012).

**Pasos**

1. Con la cuenta de la **Directiva** del club anterior, abrir Organización › Clubes.
2. Revisar que la sesión abre directamente el club nuevo y que la Directiva ya no alcanza el
   anterior.
3. Consultar en la base las asignaciones de rol y la auditoría de `CERRAR_CLUB`, `REGISTRAR_CLUB`,
   `REVOCAR_ASIGNACION` y `ASIGNAR_ROL`.

**Resultado esperado**

- La Directiva ve «Datos de tu club y sus equipos.» con **Club Águilas B-04**, sin «Cambiar nombre».
- La asignación anterior queda inactiva con fecha de revocación y se crea una equivalente en el club
  nuevo; no se reescribe el `club_id` de la asignación anterior.
- La auditoría registra el cierre y el alta del club, la revocación y la nueva asignación con el rol,
  el ámbito y el club, sin datos personales.
- El club anterior responde `404` a la Directiva (VAL-024) porque su asignación ya no lo alcanza.

**Resultado obtenido:** **Aprobado.** La Directiva abrió directamente «Club Águilas B-04»; la pantalla
mostró «Datos de tu club y sus equipos.» y el contador de botones «Cambiar nombre» fue **0**. En la
base, la asignación `DIRECTIVA_CLUB` del club anterior quedó `activa = false` con
`revocada_en = 2026-11-09 04:48:18.563653+00` y se creó otra asignación activa para el mismo perfil y
rol en el club nuevo. La auditoría registró `REVOCAR_ASIGNACION` y `ASIGNAR_ROL` en `M-01` y
`CERRAR_CLUB` y `REGISTRAR_CLUB` en `M-03`, todos con el usuario Secretario y con solo rol, ámbito y
club en el detalle. El `GET /clubes/{id}` del club anterior con el token de la Directiva respondió
`404 NO_ENCONTRADO`; el del club nuevo respondió `200`.

**Evidencia**

- [`CA4-directiva-ve-club-nuevo.png`](evidencias/B-04/CA4-directiva-ve-club-nuevo.png)
- [`CA4-directiva-botones.txt`](evidencias/B-04/CA4-directiva-botones.txt)
- [`CA4-asignaciones.txt`](evidencias/B-04/CA4-asignaciones.txt)
- [`CA4-auditoria.txt`](evidencias/B-04/CA4-auditoria.txt)
- [`V2-respuesta-directiva-clubes.json`](evidencias/B-04/V2-respuesta-directiva-clubes.json)

**Pruebas automáticas relacionadas**

- `ClubesEquiposIT` · «B-04 · RF-105, DEC-15, DEC-61: cierra el club, crea el nuevo, traslada
  asignaciones y audita» y «B-04 · RNF-004: si falla el traslado de asignaciones no queda club nuevo,
  cierre ni auditoría».
- `ClubServiceTest` · «RF-105, DEC-15: crea el club nuevo, cierra el anterior hoy en México, traslada
  asignaciones y audita».

## 4. Verificaciones adicionales

### V1 · El nombre de un club cerrado sigue ocupado y un club cerrado no vuelve a cambiar de nombre

**Pasos**

1. Antes del CA-1, intentar cambiar «Club Halcones B-04» al nombre de otro club existente.
2. Después del CA-1, intentar cambiar «Club con cobros liquidados B-04» al nombre del club ya
   cerrado «Club Halcones B-04».
3. Llamar `POST /clubes/{clubCerrado}/cambio-nombre` con el token del Secretario.

**Resultado esperado**

- Un nombre ya usado responde `409 DUPLICADO` y el error se marca en el campo **Nombre nuevo**.
- El nombre de un club `CERRADO` sigue ocupado (comparación exacta después de recortar espacios).
- Un club `CERRADO` responde `409 RN-041` y no se puede «revivir» cambiando su nombre.

**Resultado obtenido:** **Aprobado.** El nombre de otro club y el de un club cerrado respondieron
`409 DUPLICADO` con «Ya existe un club llamado «…» en la liga.» en el campo **Nombre nuevo**; el
segundo intento correspondió a «Club Halcones B-04», ya `CERRADO`. El club cerrado respondió
`409 RN-041` con «El club «Club Halcones B-04» ya está cerrado: no puede cambiar de nombre.». La
comparación sigue siendo sensible a mayúsculas; queda como deuda técnica de DEC-61.4.

**Evidencia**

- [`V1-modal-nombre-duplicado.png`](evidencias/B-04/V1-modal-nombre-duplicado.png)
- [`V1-nombre-duplicado-en-modal.png`](evidencias/B-04/V1-nombre-duplicado-en-modal.png)
- [`V1-respuesta-nombre-duplicado.json`](evidencias/B-04/V1-respuesta-nombre-duplicado.json)
- [`V1-nombre-cerrado-ocupado.png`](evidencias/B-04/V1-nombre-cerrado-ocupado.png)
- [`V1-respuesta-nombre-cerrado-ocupado.json`](evidencias/B-04/V1-respuesta-nombre-cerrado-ocupado.json)
- [`V1-respuesta-club-cerrado.json`](evidencias/B-04/V1-respuesta-club-cerrado.json)

**Pruebas automáticas relacionadas**

- `ClubServiceTest` · «RN-041: un club CERRADO no cambia de nombre (409), antes de validar el cuerpo»
  y «RF-105: el mismo nombre actual responde 409 DUPLICADO».
- `ClubesEquiposIT` · «B-04 · DEC-49, DEC-61: el nombre de un club CERRADO sigue ocupado (409
  DUPLICADO)».
- `PantallaClubes.test.tsx` · «409 DUPLICADO se muestra en el campo Nombre».

### V2 · Solo el ámbito de liga cambia el nombre; la Directiva no ve la acción ni puede llamar la API

**Pasos**

1. Revisar la pantalla de la **Directiva** en el club nuevo (CA-4).
2. Llamar `POST /clubes/{id}/cambio-nombre` con el token de la Directiva sobre el club anterior.
3. Repetir con el token del **Secretario** sobre un club fuera de su ámbito (no aplica: su permiso es
   de liga).

**Resultado esperado**

- La Directiva no ve «Cambiar nombre» porque su `organizacion.club:EDITAR` es de ámbito `CLUB` (y no
  lo tiene en la matriz inicial).
- La API responde `403 VAL-015` antes de buscar el club, sin revelar si existe.

**Resultado obtenido:** **Aprobado.** La Directiva no vio el botón y el `POST` respondió `403 VAL-015`
con «Tu cuenta no tiene el permiso necesario para esta operación.» antes de buscar el club. El
Secretario sí pudo cambiar el nombre de clubes de toda la liga.

**Evidencia**

- [`CA4-directiva-ve-club-nuevo.png`](evidencias/B-04/CA4-directiva-ve-club-nuevo.png)
- [`V2-respuesta-403-directiva.json`](evidencias/B-04/V2-respuesta-403-directiva.json)

**Pruebas automáticas relacionadas**

- `ClubServiceTest` · «RN-041: un club CERRADO…» y las pruebas de autorización de `ClubControllerTest`
  con el arnés de H-02.3 (`401`/`403`/`404`).
- `PantallaClubes.test.tsx` · ««Cambiar nombre» aparece con organizacion.club:EDITAR de ámbito LIGA y
  el club ACTIVO» y ««Cambiar nombre» no aparece con EDITAR_CLUB de ámbito CLUB / sin permiso / club
  CERRADO».

## 5. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Club nuevo `ACTIVO` y anterior `CERRADO` con fecha y motivo | Aprobado |
| CA-2 Bloqueo por equipos en temporada `ACTIVA` o `CONFIGURACION` | Aprobado |
| CA-3 Bloqueo por cobros `PENDIENTE` o `VENCIDO`; los cobros se quedan en el anterior | Aprobado |
| CA-4 Traslado de asignaciones `CLUB` y auditoría | Aprobado |
| V1 Unicidad del nombre con clubes cerrados y club cerrado sin nuevo cambio | Aprobado |
| V2 Permiso de liga y ausencia de la acción para la Directiva | Aprobado |

## 6. Verificación automática

| Comando | Dónde | Resultado |
| --- | --- | --- |
| `./mvnw verify` | `backend/` | **BUILD SUCCESS** · 1178 pruebas unitarias y de controlador, 202 de integración, 0 fallos; 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS) |
| `npm run lint` | `frontend/` | 0 advertencias y 0 errores |
| `npm test` | `frontend/` | 62 archivos y 510 pruebas aprobadas |
| `npm run build` | `frontend/` | compilación de producción correcta |

La corrida automática incluye las pruebas de `ClubesEquiposIT` sobre PostgreSQL real
(Testcontainers) que cubren el caso feliz, los bloqueos por equipos en `ACTIVA` y `CONFIGURACION`, el
equipo en `BAJA` o en temporada `CERRADA`, el cobro pendiente, el nombre de un club cerrado y la
atomicidad del traslado.

## 7. Defectos encontrados

Ninguno que incumpla los criterios de aceptación. El cambio de nombre, los bloqueos, el traslado de
asignaciones, la unicidad y la auditoría funcionaron como describen RF-105, RN-041, DEC-49, DEC-61 y
el contrato de `convenciones-api.md` §6.3.

Observaciones que no son defectos bloqueantes:

1. **DEC-61 sigue pendiente de confirmación del PO.** La corrida valida sus cuatro puntos, pero la
   decisión está marcada `[PROPUESTA]`; debe confirmarse o rechazarse formalmente en SCRUM-374 antes
   de cerrar B-04.
2. **El enlace «Proviene de» del club nuevo queda visible para la Directiva, pero el club anterior le
   responde 404.** Es coherente con el traslado de asignaciones (DEC-61.3) y con VAL-024, pero deja
   un enlace sin salida para ese perfil. Se recomienda evaluar en H-22 si el enlace debe ocultarse
   cuando el club anterior está fuera de ámbito o si la Directiva debe conservar acceso de solo
   lectura al historial.
3. **La unicidad del nombre es exacta y sensible a mayúsculas**, como registra DEC-61.4; el índice
   único sobre `lower(nombre)` queda como deuda técnica.
4. La corrida usó una base local efímera y cuentas ficticias; las capturas no muestran correos,
   tokens ni contraseñas.

## 8. Conclusión

Los cuatro criterios de aceptación de B-04 quedaron **aprobados** contra una base local efímera con el
backend, el frontend y la base de datos reales: el cambio de nombre crea un club nuevo `ACTIVO`,
cierra el anterior con fecha y motivo, bloquea los clubes con equipos inscritos en temporadas
`ACTIVA` o `CONFIGURACION` y con cobros `PENDIENTE` o `VENCIDO`, traslada las asignaciones de rol de
ámbito `CLUB` y deja la operación auditada. Las verificaciones V1 (unicidad y club cerrado) y V2
(permiso de liga) también pasaron.

Con esto, **B-04.3 queda terminada**. La historia B-04 puede pasar a «Finalizado» una vez que
DEC-61 se confirme con el PO y se registre la decisión; el comportamiento probado es el que describe
la propuesta vigente.
