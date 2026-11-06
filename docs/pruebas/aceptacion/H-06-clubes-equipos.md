# Prueba de aceptación · H-06 Registrar clubes y sus equipos en la categoría y grupo de la temporada

| Campo | Valor |
| --- | --- |
| Historia | H-06 · subtarea H-06.3 (prueba de aceptación) |
| Requisitos | RF-008, RF-009, RN-005, RN-021, UI-005 |
| Pantallas | F-15 (clubes con detalle y pestaña de equipos) |
| Versión probada | `develop` en `1d7fd84`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000` y `20261103100000` |
| Fecha | 2026-11-06 |
| Ejecutó | Agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado** (3 de 3 criterios) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local (contenedor Docker) con la simulación del entorno de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cuatro migraciones del repositorio aplicadas |
| Autenticación | Servidor JWKS local que publica la clave con la que el backend valida el token de la sesión |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, contra la base local y el JWKS local |
| Frontend | `npm run dev` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Chromium controlado con Playwright, ventana de 1440 × 1100 px y zona `America/Mexico_City` |

La prueba se ejecutó de punta a punta sobre una **base local efímera**, recreada desde la
simulación de Supabase y las migraciones del repositorio: no se alteró el proyecto compartido de
Supabase y la corrida completa (sesiones, pantalla, API y base) es reproducible desde cero.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican en el repositorio.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Secretario** | Secretario · Liga | CA-1: registrar el club y su equipo (`organizacion.club:EDITAR`, `organizacion.equipo:EDITAR` de la migración `20261103100000`) |
| **Directiva de club** | Directiva de club · ámbito CLUB | CA-2 y CA-3: ver solo su club, editar y dar de baja el equipo |

Datos creados durante la corrida, todos el **6 de noviembre de 2026**:

- Club **«Club Fuego Nuevo»**, registrado por el Secretario desde la pantalla (nació **ACTIVO**).
- Equipo **«Fuego Nuevo A»** del club anterior, en **Libre · Grupo 1** de la temporada
  **«Temporada de aceptación 2026-27» (ACTIVA)**.
- Cambio de nombre del equipo a **«Fuego Nuevo F.C.»**, capturado desde el detalle para dejar el
  historial auditado antes de la baja.
- Baja del equipo con el motivo **«Se retiró del torneo por desintegración de la plantilla»**.
- La base de prueba ya sembraba el club rival **«Club Riviera Morelia»** con su equipo
  **«Riviera F.C.»**: no se tocaron por las interfaces y sirvieron para comprobar que la Directiva
  no alcanza recursos de otro club.

## 3. Escenarios

### CA-1 · El club y el equipo registrados aparecen en el detalle del club

> Dado el Secretario, cuando registra un club y un equipo en un grupo de la temporada activa,
> entonces ambos aparecen en el detalle del club.

**Pasos**

1. Iniciar sesión como **Secretario** y abrir `/app/organizacion/clubes`.
2. Pulsar «Registrar club», capturar el nombre «Club Fuego Nuevo» y guardar.
3. En el detalle abierto del club, pulsar «Registrar equipo», elegir la temporada activa, la
   categoría **Libre**, el **Grupo 1**, capturar el nombre «Fuego Nuevo A» y guardar.
4. Revisar el catálogo, la fila del equipo en la pestaña «Equipos» y las respuestas de la API.

**Resultado esperado**

- El club queda registrado en **ACTIVO** y su detalle se abre sin pasos extra.
- El aviso confirma «Equipo Fuego Nuevo A registrado.» y la fila aparece en la pestaña «Equipos»
  con la temporada, categoría y grupo elegidos y la insignia **ACTIVO**.
- El `POST /api/v1/clubes` responde `201` con `Location` y el cuerpo del club; el listado de
  equipos del club lo devuelve con sus referencias completas.

**Resultado obtenido:** **Aprobado.** El club quedó en el catálogo y su detalle se abrió en el mismo
paso; el registro del equipo mostró el aviso y la fila «Fuego Nuevo A · Libre · Grupo 1 · ACTIVO».
La API respondió `201` con el club en `ACTIVO` y el listado de equipos regresó el equipo con club,
grupo, categoría y temporada anidados.

**Evidencia:** [`CA1-catalogo-clubes.png`](evidencias/H-06/CA1-catalogo-clubes.png),
[`CA1-modal-registrar-club.png`](evidencias/H-06/CA1-modal-registrar-club.png),
[`CA1-detalle-club-registrado.png`](evidencias/H-06/CA1-detalle-club-registrado.png),
[`CA1-modal-registrar-equipo.png`](evidencias/H-06/CA1-modal-registrar-equipo.png),
[`CA1-club-con-equipo.png`](evidencias/H-06/CA1-club-con-equipo.png),
[`CA1-respuesta-club.json`](evidencias/H-06/CA1-respuesta-club.json) (respuesta cruda del alta del
club) y [`CA1-respuesta-equipos.json`](evidencias/H-06/CA1-respuesta-equipos.json) del listado

**Pruebas automáticas relacionadas**

- `ClubServiceTest` · «RF-008: el alta es de la liga, recorta el nombre, nace ACTIVO y se audita
  REGISTRAR_CLUB» y «RF-008: un nombre repetido en la liga responde 409 DUPLICADO y nombra el club».
- `EquipoServiceTest` · «RF-009: registra el equipo en el grupo, exige el ámbito del club y audita
  REGISTRAR_EQUIPO», «RF-009: un grupo inexistente responde 422 REFERENCIA_INVALIDA» y «RF-009: un
  nombre repetido en el grupo responde 409 DUPLICADO».
- `ClubesEquiposIT` · «H-06 criterio 1 · RF-008, RF-009: el SECRETARIO registra club y equipo en la
  temporada activa».
- `PantallaClubes.test.tsx` · «criterio 1: registrar un club cierra el modal y navega a su detalle»,
  «criterio 1: el Secretario registra un equipo eligiendo temporada → categoría → grupo» y
  «criterio 1: detalle con pestaña Equipos, temporada activa por omisión y orden del servidor
  (DEC-16)».

### CA-2 · La Directiva del club solo ve los equipos de su club

> Dada la Directiva de un club, cuando lista equipos, entonces solo ve los de su club.

**Pasos**

1. En la base de prueba local, insertar la asignación del rol **Directiva de club** con ámbito
   `CLUB` y el id del club del CA-1.
2. Iniciar sesión con la cuenta de la **Directiva** y abrir `/app/organizacion/clubes`.
3. Revisar la pantalla: detalle directo del club, lista de equipos y botones disponibles.
4. Consultar los listados `/api/v1/clubes` y `/api/v1/equipos` con el mismo token de la sesión.
5. Pedir el club y el equipo del club rival (recursos ajenos) con el mismo token.

**Resultado esperado**

- La pantalla abre directamente el detalle del club con el texto «Datos de tu club y sus equipos.»:
  sin catálogo de la liga, sin «Registrar club» y sin «Registrar equipo» (esta última acción
  requiere también `temporadas.temporada:CONSULTAR`, que la Directiva no tiene: DEC-54).
- `GET /api/v1/clubes` devuelve solo su club y `GET /api/v1/equipos` solo su equipo (el alcance del
  permiso se aplica en la consulta y en el total, ADR-0007).
- El club y el equipo ajenos responden **404** `NO_ENCONTRADO`, idéntico a un recurso inexistente
  (VAL-024).

**Resultado obtenido:** **Aprobado.** El detalle de «Club Fuego Nuevo» se abrió directo; la fila
«Fuego Nuevo A» fue el único equipo de la lista y el club ajeno no aparece en ninguna parte de la
página; los dos botones de alta están ausentes («Registrar club»: 0, «Registrar equipo»: 0). Los
listados por API devolvieron exactamente 1 club y 1 equipo, ambos propios, y los dos recursos del
club rival respondieron `404` con `codigo: "NO_ENCONTRADO"`.

**Evidencia:** [`CA2-asignacion-directiva.txt`](evidencias/H-06/CA2-asignacion-directiva.txt)
(asignación vigente), [`CA2-detalle-directiva.png`](evidencias/H-06/CA2-detalle-directiva.png),
[`CA2-respuesta-clubes.json`](evidencias/H-06/CA2-respuesta-clubes.json),
[`CA2-respuesta-equipos.json`](evidencias/H-06/CA2-respuesta-equipos.json),
[`CA2-respuesta-404-club-ajeno.json`](evidencias/H-06/CA2-respuesta-404-club-ajeno.json) y
[`CA2-respuesta-404-equipo-ajeno.json`](evidencias/H-06/CA2-respuesta-404-equipo-ajeno.json) (las
cuatro últimas son la respuesta cruda de la API); resumen en
[`CA2-api-resumen.txt`](evidencias/H-06/CA2-api-resumen.txt)

**Pruebas automáticas relacionadas**

- `ClubesEquiposIT` · «H-06 criterio 2 · ADR-0007, VAL-024: la DIRECTIVA_CLUB solo ve su club y sus
  equipos».
- `ClubServiceTest` y `EquipoServiceTest` · «ADR-0007: el listado usa el alcance del permiso en la
  consulta y en el total», «VAL-024: un club fuera de ámbito responde 404 y no consulta el grupo» y
  «VAL-024: un equipo de otro club responde 404 antes de revisar su estado».
- `ClubControllerTest`/`EquipoControllerTest` · «VAL-024: la consulta exige el permiso sobre el
  propio club» y «VAL-024: la consulta exige el permiso sobre el club del equipo».
- `PantallaClubes.test.tsx` · «criterio 2: la Directiva va directo a su club, sin lista, «Volver» ni
  «Registrar equipo»».

### CA-3 · La baja del equipo queda en BAJA con motivo y no se borra (RN-005)

> Dado un equipo con historial, cuando se da de baja, entonces queda en estado BAJA con motivo y no
> se borra (RN-005).

**Pasos**

1. Con la cuenta de la **Directiva**, en el detalle de su club, pulsar «Editar Fuego Nuevo A»,
   capturar el nombre «Fuego Nuevo F.C.» y guardar: el cambio queda auditado y constituye el
   historial mínimo del equipo (los jugadores y partidos llegan con H-09 a H-17).
2. Pulsar «Dar de baja Fuego Nuevo F.C.», capturar el motivo «Se retiró del torneo por
   desintegración de la plantilla» y confirmar.
3. Poner el filtro **Estado = Todos** y revisar la fila del equipo.
4. Verificar en la base la fila en `siglim.equipos` y su auditoría en `siglim.auditoria`.
5. Con el mismo token: consultar el detalle del equipo, intentar `PATCH` y una segunda baja.

**Resultado esperado**

- El modal avisa que el equipo quedará en BAJA, no se borra y conserva su historial.
- La fila sigue en la lista con el estado **Baja**, la fecha de la baja y el motivo; **sin acciones**
  (sin «Editar» ni «Dar de baja», RN-005).
- La fila persiste en `siglim.equipos` con `estado = BAJA`, su fecha y su motivo; la auditoría
  registra `EDITAR_EQUIPO` y `DAR_BAJA_EQUIPO` con el motivo (RN-021).
- El detalle por API muestra `BAJA`, `bajaEn` y `motivoBaja`; el `PATCH` y una segunda baja
  responden **409** con `codigo: "RF-009"`.

**Resultado obtenido:** **Aprobado.** La baja confirmó con el motivo y la fila quedó en la lista
como «Fuego Nuevo F.C. · Baja el 6 nov 2026: Se retiró del torneo por desintegración de la
plantilla · BAJA», sin botones de acción. En la base la fila sigue existiendo con
`estado = BAJA`, `baja_en = 2026-11-06` y el motivo, y la auditoría del equipo contiene
`REGISTRAR_EQUIPO`, `EDITAR_EQUIPO` y `DAR_BAJA_EQUIPO` con el motivo. La API devolvió el detalle en
`BAJA` y los dos intentos respondieron `409 RF-009` («está dado de baja: no admite cambios» y
«ya está dado de baja»).

**Evidencia:** [`CA3-modal-baja.png`](evidencias/H-06/CA3-modal-baja.png),
[`CA3-equipo-baja-en-lista.png`](evidencias/H-06/CA3-equipo-baja-en-lista.png),
[`CA3-fila-en-base.txt`](evidencias/H-06/CA3-fila-en-base.txt) (salida real de la consulta SQL),
[`CA3-auditoria.txt`](evidencias/H-06/CA3-auditoria.txt) (auditoría en orden),
[`CA3-respuesta-equipo-baja.json`](evidencias/H-06/CA3-respuesta-equipo-baja.json),
[`CA3-respuesta-409-patch.json`](evidencias/H-06/CA3-respuesta-409-patch.json) y
[`CA3-respuesta-409-baja-repetida.json`](evidencias/H-06/CA3-respuesta-409-baja-repetida.json);
resumen en [`CA3-resumen-api.txt`](evidencias/H-06/CA3-resumen-api.txt)

**Pruebas automáticas relacionadas**

- `EquipoServiceTest` · «RF-009, RN-021: la baja fija el motivo, usa la fecha de Morelia y audita
  DAR_BAJA_EQUIPO», «RF-009: una baja repetida responde 409», «RF-009: un equipo dado de baja no
  admite PATCH (409)» y «RN-005: no existe DELETE de equipos (405)».
- `EquipoControllerTest` · «RF-009: la baja responde 200 con el equipo en BAJA, su fecha y su
  motivo», «RN-021: el 400 por falta de motivo conserva el código» y «RF-009: una baja repetida
  responde 409 RF-009».
- `ClubesEquiposIT` · «H-06 criterio 3 · RF-009, RN-005, RN-021: la baja deja el equipo en BAJA y
  conserva la fila».
- `PantallaClubes.test.tsx` · «criterio 3: un equipo en BAJA muestra su fecha y motivo y no tiene
  acciones» y «criterio 3: la baja exige motivo de 10 caracteres y llama POST equipos/{id}/baja».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Club y equipo registrados aparecen en el detalle | Aprobado |
| CA-2 La Directiva solo ve los equipos de su club | Aprobado |
| CA-3 Baja: queda en BAJA con motivo y no se borra (RN-005) | Aprobado |

Pruebas automáticas de la misma versión: backend `./mvnw verify -Dtest='ClubServiceTest,
EquipoServiceTest,ClubControllerTest,EquipoControllerTest' -Dit.test='ClubesEquiposIT'` con
**98/98** pruebas (93 de servicio y controlador más 5 de `ClubesEquiposIT` con PostgreSQL real en
Testcontainers, sin fallos); frontend `npx vitest run src/features/organizacion` con **22/22**
pruebas en 2 archivos (`PantallaClubes.test.tsx` y `equipos.test.ts`).

## 5. Defectos encontrados

Ninguno. No se encontró un comportamiento que incumpla los criterios de H-06 ni una regresión de
RF-008, RF-009, RN-005, RN-021 o del alcance por club de ADR-0007.

## 6. Observaciones

1. **El «historial» del CA-3 es mínimo por alcance del sprint.** Los jugadores y los partidos del
   equipo corresponden a H-09 a H-17 (sprints posteriores); el historial demostrado aquí es el
   cambio de nombre auditado antes de la baja. La conservación de la fila y la inmutabilidad de la
   auditoría quedaron comprobadas igualmente en la base.
2. **La baja se ejecutó desde la Directiva del club**, que también puede dar de baja dentro de su
   ámbito; el Secretario usa el mismo endpoint con el ámbito de la liga.
3. La asignación del rol Directiva (ámbito `CLUB`) se insertó con SQL directo en la base local de
   prueba: la pantalla de asignaciones de roles (H-02) no edita esta asignación desde la interfaz.
4. La corrida confirmó también la decisión **DEC-54** en vivo: la Directiva de club no ve
   «Registrar equipo» aunque pueda consultar sus equipos, porque a esa acción le falta
   `temporadas.temporada:CONSULTAR`.
5. Tras la baja, la consola del navegador registró dos respuestas `409`: son los conflictos
   esperados del paso 5, no errores de la aplicación.
6. Las capturas no muestran correos, tokens ni contraseñas; las cuentas se identifican por su rol y
   el nombre del encabezado queda truncado.

## 7. Conclusión

La historia **H-06 cumple sus tres criterios de aceptación** sobre `develop` en `1d7fd84` con
frontend, backend y base de datos reales: el Secretario registra el club y su equipo y ambos
aparecen en el detalle del club, la Directiva de un club solo ve su club y sus equipos (sin acciones
de alta, con 404 en recursos ajenos) y la baja del equipo deja el registro en BAJA con motivo y
auditoría sin borrarse, rechazando toda modificación posterior con `409 RF-009`. H-06 puede pasar a
«Finalizado».
