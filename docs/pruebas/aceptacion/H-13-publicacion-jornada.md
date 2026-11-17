# Prueba de aceptación · H-13 Enviar la jornada a aprobación y publicarla

| Campo | Valor |
| --- | --- |
| Historia | H-13 (SCRUM-190) · subtarea H-13.3 (SCRUM-300) |
| Requisitos | RF-121, RF-090, UI-009, DD-008, RF-039, RF-093, RN-053, VAL-015 |
| Pantallas | F-22 (Programación › Jornadas: envío), F-23 (revisión de la jornada), F-10 y F-11 (temporadas), F-12 (usuarios), F-15 (clubes y equipos), F-14 (auditoría) |
| Implementó | Israel (H-13.1 #145, H-13.2 #147) |
| Versión probada | `develop` en `25cc809` |
| Fecha | 2026-11-17 |
| Ejecutó | Leonardo López (prueba cruzada, DEC-50) |
| Resultado global | **Aprobado** (4 de 4 criterios de SCRUM-190), sin defectos |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | Supabase compartido (proyecto `SIGLIM`, rama `develop` según DEC-52), con la migración `20261116180000_parametros_localia` ya aplicada |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080` con `backend/.env` |
| Frontend | `npm run dev` en `http://localhost:5173` |
| Navegador | Google Chrome, ventana de 1440 px; dos sesiones a la vez (ventana normal e incógnito) |

La programación de jornadas (F-22) **exige una temporada `ACTIVA`**: sin ella la pantalla muestra
«No hay una temporada activa» y no deja elegir grupo. El backend no lo valida, pero la pantalla sí, y
la base solo admite **una temporada `ACTIVA` por liga** (`ux_temporadas_activa`, RN-058). Para no
tocar la temporada de trabajo del equipo («Temporada 2026–2027», en `CONFIGURACION`), la prueba usa
una **temporada de prueba aparte** que se activa para esta corrida y se cierra al final, igual que
en B-05.4. Si se quedara activa, impediría activar después la temporada real.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Administrador general** | Administrador general · LIGA | Prepara y cierra la temporada de prueba; invita las cuentas de prueba; consulta la auditoría |
| **Secretario** | Secretario de la Liga · LIGA | Registra los equipos de prueba |
| **Programador de prueba** (nueva) | Programador de partidos · LIGA (`CONSULTAR`, `EDITAR`, `ENVIAR`) | CA-1 y CA-3: crea, programa, envía y reenvía la jornada; CA-4: no puede dictaminar |
| **Presidente** | Presidente del Comité Ejecutivo · LIGA (`CONSULTAR`, `APROBAR`) | CA-2 y CA-3: aprueba y devuelve |
| **Doble rol de prueba** (nueva) | Programador de partidos **y** Presidente del Comité Ejecutivo · LIGA (`EDITAR` y `APROBAR` a la vez) | CA-4: con permiso de editar no puede aprobar |

La cuenta **Doble rol de prueba** existe solo para el CA-4: no crea ni envía la jornada, así que el
`403` que reciba se debe únicamente a que también tiene `programacion.jornada:EDITAR` (par
incompatible del Apéndice E), y no a que la haya programado. Así no se agrega el rol de Programador
a la cuenta real del Presidente.

Datos que se crean para la corrida:

| Id | Dato | Destino en la prueba |
| --- | --- | --- |
| T | Temporada **«Temporada prueba H-13»** (16-nov-2026 a 31-dic-2026), con parámetros v1 **sin** total de fechas de la fase regular, la categoría «Libre Demostración» y el grupo «Grupo A» | Se activa para la corrida y se cierra al final |
| L | Equipo **«H13 Halcones»** de Real Patzcuaro, en «Libre Demostración / Grupo A» de **T** | Local del partido |
| V | Equipo **«H13 Toros»** de CamiloSextos, en el mismo grupo | Visitante del partido |
| J | **Jornada 1** del grupo de **T** | Se envía, se devuelve, se reenvía y se publica |
| P | Partido **H13 Halcones vs. H13 Toros**, **28/11/2026 10:00**, en **«Cancha Real Pátzcuaro · Demostración»** | Único partido de **J** |

El partido se programa en la cancha del club local y la temporada no tiene total de fechas de la
fase regular, de modo que no genera advertencias de localía (B-15.1, VAL-027) ni pide motivo de
aceptación. La cancha no tiene bloqueos ni mantenimientos registrados.

Estado inicial verificado el 2026-11-16 en el Supabase compartido: temporadas «Temporada 2026–2027»
(`CONFIGURACION`) y «Temporada prueba B-05» (`CERRADA`); 0 jornadas y 0 partidos; ninguna cuenta
con rol Programador; la única cuenta con rol Presidente no tiene otros roles; los roles con permisos
de jornada son `PROGRAMADOR` (`CONSULTAR`, `EDITAR`, `ENVIAR`), `PRESIDENTE` (`CONSULTAR`,
`APROBAR`), `DIRECTIVA_CLUB` y `COMISION_ARBITRAJE` (`CONSULTAR`).

### Preparación (no es criterio; se documenta para poder repetirla)

1. Como **Administrador general**, abrir **Administración → Temporadas** (F-10) y crear la
   temporada **T**.
2. En su configuración (F-11), guardar los parámetros deportivos (v1) **dejando vacío el total de
   fechas de la fase regular**, asignar la categoría «Libre Demostración» y crear el grupo «Grupo A».
3. Comprobar que la lista de verificación de **T** está completa y pulsar **Activar**.
   **Antes de confirmar, verificar que la fila es «Temporada prueba H-13» y no «Temporada
   2026–2027».**
4. Como **Secretario**, registrar en F-15 el equipo **L** en Real Patzcuaro y el equipo **V** en
   CamiloSextos, ambos en «Libre Demostración / Grupo A» de **T**.
5. Como **Administrador general**, abrir **Administración → Usuarios** (F-12) e invitar:
   - **Programador de prueba**, con el rol Programador de partidos (ámbito LIGA);
   - **Doble rol de prueba**, con los roles Programador de partidos y Presidente del Comité
     Ejecutivo (ámbito LIGA).

   Como las demás cuentas de prueba, se usa un alias `+rol` del mismo Gmail del equipo
   (`…+programador@gmail.com` y `…+doblerol@gmail.com`) y los nombres «Programador de pruebas» y
   «Programador Presidente de pruebas». La invitación se completa desde el correo. En la corrida, la
   cuenta de doble rol se invitó como Presidente y después se le asignó el rol de Programador
   (**Asignar rol**).
6. Como **Programador de prueba**, abrir **Programación › Jornadas** (`/app/programacion/jornadas`):
   debe decir «Temporada activa: Temporada prueba H-13». Elegir el grupo, crear la **Jornada 1**
   («Jornada 1 creada en borrador.») y programar el partido **P** («Partido programado en la jornada
   1.»).

La preparación quedó registrada en la auditoría el 2026-11-17 entre las 14:05 y las 14:26:
`REGISTRAR_TEMPORADA`, `ASIGNAR_CATEGORIA_TEMPORADA`, `REGISTRAR_GRUPO`, `REGISTRAR_PARAMETROS` (v1,
bloque de cancha de 120 min, sin total de fechas de la fase regular) y `ACTIVAR_TEMPORADA` en M-02;
dos `REGISTRAR_EQUIPO` en M-03; dos `INVITAR_USUARIO` y un `ASIGNAR_ROL` en M-01; y
`REGISTRAR_JORNADA` y `REGISTRAR_PARTIDO` en M-07 (visibles en `CA2-auditoria.png`). No se tomaron
capturas aparte.

## 3. Escenarios

### CA-1 · Una jornada sin conflictos que envía el Programador pasa a REVISION

> Dada una jornada sin conflictos, cuando el Programador la envía, entonces pasa a REVISION.

**Pasos**

1. Como **Programador de prueba**, en **Programación › Jornadas**, elegir la **Jornada 1 ·
   Borrador** y pulsar **Verificar conflictos**.
2. Pulsar **Enviar a aprobación** y revisar el diálogo de confirmación.
3. Confirmar con **Enviar a aprobación**.
4. Revisar la jornada después del envío: el flujo de publicación, el botón de envío y las acciones
   del partido.

**Resultado esperado**

- Paso 1: «Sin conflictos».
- Paso 2: el diálogo **Enviar a aprobación** resume «La jornada 1 de Libre Demostración · Grupo A,
  con 1 partido…».
- Paso 3: mensaje «Jornada 1 enviada a aprobación. La Presidencia la revisará antes de publicarla.»
  (`POST /api/v1/jornadas/{id}/envio` responde `200` con `estadoPublicacion: "REVISION"`).
- Paso 4: aviso «Esta jornada está en revisión de la Presidencia…», el flujo marca «Dictamen de la
  Presidencia» como paso actual, **Enviar a aprobación** queda deshabilitado y el partido ya no
  ofrece **Editar** (RF-090: solo se edita en `BORRADOR`).

**Resultado obtenido:** **Aprobado.** La verificación de la Jornada 1 mostró «Sin conflictos» («No
hay choques de equipos ni de canchas en esta jornada.»). El diálogo **Enviar a aprobación** resumió
«La jornada 1 de Libre Demostración · Grupo A, con 1 partido, pasará a revisión de la Presidencia» y
advirtió que en revisión no se pueden programar ni ajustar partidos. Al confirmar, la jornada quedó
**En revisión** con «Esta jornada está en revisión de la Presidencia desde el 17 nov 2026, 14:30.
Podrás ajustar partidos si la devuelve a borrador.», y el partido dejó de ofrecer **Editar**
(RF-090). La auditoría registró `ENVIAR_JORNADA_A_APROBACION` con `estadoAnterior: BORRADOR`,
`estado: REVISION` y `partidos: 1`, y `historial_publicacion` el paso `BORRADOR → REVISION`.

**Evidencia:** `CA1-sin-conflictos.png`, `CA1-confirmar-envio.png`, `CA1-jornada-en-revision.png`

**Pruebas automáticas relacionadas:** `JornadaServiceTest` («RF-121: una jornada en borrador sin
conflictos pasa a REVISION con el Programador y se audita», «RF-039: una jornada con conflictos no
se envía y responde 409 con la lista», «RF-121: una jornada sin partidos vigentes no se envía (409
RF-121)», «RF-090: una jornada que ya está en revisión no se vuelve a enviar»);
`ProgramacionControllerTest` («RF-121: POST /jornadas/{id}/envio no lleva cuerpo y devuelve la
jornada en REVISION», «RNF-003: seguridad de POST /api/v1/jornadas/{id}/envio»);
`PantallaProgramacion.test.tsx` («criterio 1: envía la jornada sin conflictos a aprobación con
confirmación», «no permite enviar ni editar una jornada en revisión»); `ProgramacionIT` («RF-121: el
Programador envía, el Presidente la ve pendiente, la aprueba y se publica con historial»).

### CA-2 · El Presidente aprueba y la jornada queda PUBLICADO y visible en `v_publica_programacion`

> Dada una jornada en REVISION, cuando el Presidente la aprueba, entonces pasa a PUBLICADO y queda
> visible en `v_publica_programacion`.

Se ejecuta **al final**, después del CA-3 y del reenvío: publicar es definitivo y la jornada ya no
se puede devolver.

**Pasos**

1. Antes de aprobar, ejecutar en el **SQL Editor** del Supabase compartido (solo lectura):

   ```sql
   select temporada, categoria, grupo, jornada, inicio, equipo_local, equipo_visitante, cancha, estado
   from siglim.v_publica_programacion
   where temporada = 'Temporada prueba H-13';
   ```

2. En la ventana de incógnito, como **Presidente**, abrir **Programación › Jornadas**: debe aparecer
   la bandeja **Jornadas por revisar** con «Jornada 1 · 1 partido · Enviada por …».
3. Pulsar la jornada (abre F-23, `/app/programacion/jornadas/<id>/revision`) y revisar el rol de
   juegos de solo lectura.
4. Pulsar **Aprobar y publicar**, dejar vacía la **Nota de aprobación (opcional)** y confirmar.
5. Repetir la consulta del paso 1.
6. Como **Administrador general**, abrir **Auditoría** (F-14) y filtrar por módulo **M-07**.

**Resultado esperado**

- Paso 1: la consulta no devuelve filas (la jornada está en `REVISION`).
- Paso 2: la jornada aparece en la bandeja con su número de partidos y quién la envió.
- Paso 3: título «Revisión de la jornada 1», «Libre Demostración · Grupo A · Enviada por …» y la tabla
  **Rol de juegos** sin botones.
- Paso 4: el diálogo avisa que «Los 1 partido de la jornada 1 aparecerán en el portal público y la
  jornada ya no podrá editarse.»; al confirmar, mensaje «Jornada 1 aprobada y publicada. Sus partidos
  ya aparecen en el portal.» y «Esta jornada ya está publicada. No hay dictamen pendiente.»; la
  bandeja vuelve a «No hay jornadas por revisar».
- Paso 5: una fila con «Temporada prueba H-13», «Libre Demostración», «Grupo A», jornada 1, H13
  Halcones vs. H13 Toros, inicio 28/11/2026 12:00 (el ajuste del CA-3), «Cancha Real Pátzcuaro ·
  Demostración» y estado `PROGRAMADO`.
- Paso 6: `ENVIAR_JORNADA_A_APROBACION` (dos: el envío y el reenvío), `AJUSTAR_PARTIDO` con su
  motivo, `DEVOLVER_JORNADA` con las
  observaciones como motivo y `APROBAR_JORNADA` con `estadoAnterior: REVISION` y `estado: PUBLICADO`.

**Resultado obtenido:** **Aprobado.** El Presidente vio **Jornadas por revisar** con «1 jornada en
revisión»: «Jornada 1 · Temporada prueba H-13 · Libre Demostración · Grupo A · 1 partido · Enviada
por Programador de pruebas · 17 nov 2026, 15:21». El diálogo **Aprobar y publicar** avisó que el
partido aparecería en el portal público y que la jornada ya no podría editarse; se confirmó sin nota.
F-23 mostró «Jornada 1 aprobada y publicada. Sus partidos ya aparecen en el portal.», la insignia
**Publicado** y los tres pasos del flujo concluidos («Aprobada · 17 nov 2026, 15:26» y
«Publicación en el portal · 17 nov 2026, 15:26»). Antes de aprobar, la consulta a
`v_publica_programacion` no devolvía filas. Después devolvió una fila: «Temporada prueba H-13»,
«Libre Demostración», «Grupo A», jornada 1, inicio `2026-11-28 18:00:00+00` (las 12:00 de Morelia,
tras el ajuste del CA-3), H13 Halcones vs. H13 Toros, «Cancha Real Pátzcuaro · Demostración»,
`PROGRAMADO`. La auditoría de M-07 tiene los 7 eventos de la jornada, en orden:
`REGISTRAR_JORNADA`, `REGISTRAR_PARTIDO`, `ENVIAR_JORNADA_A_APROBACION`, `DEVOLVER_JORNADA`,
`AJUSTAR_PARTIDO`, `ENVIAR_JORNADA_A_APROBACION` y `APROBAR_JORNADA` (Presidente Lio,
`estadoAnterior: REVISION`, `estado: PUBLICADO`). Consulta directa al Supabase compartido:
`aprobada_por` es distinto de `enviada_por` (`ck_jornadas_aprobacion`), `aprobada_en` y
`publicada_en` son de las 15:26:50, y `historial_publicacion` registra los cuatro pasos
`BORRADOR → REVISION → BORRADOR → REVISION → PUBLICADO`.

**Evidencia:** `CA2-bandeja-pendientes.png`, `CA2-confirmar-aprobacion.png`,
`CA2-jornada-publicada.png`, `CA2-v-publica-programacion.png`, `CA2-auditoria.png` (reducida a
1440 px de ancho)

**Pruebas automáticas relacionadas:** `JornadaServiceTest` («RF-121: el Presidente aprueba una
jornada en revisión y queda PUBLICADO con su aprobación», «RF-090: solo una jornada en revisión se
dictamina; en borrador o publicada responde 409»); `ProgramacionControllerTest` («RF-121: POST
/jornadas/{id}/resolucion pasa la decisión y las observaciones al servicio»);
`PublicacionControllerTest` («RF-090, RF-121: GET /publicaciones/pendientes pagina jornadas y
comunicados en revisión»); `PantallaRevisionJornada.test.tsx` («criterio 2: aprueba y publica la
jornada en revisión», «una jornada publicada ya no ofrece dictamen y muestra su nota»);
`PantallaProgramacion.test.tsx` («a quien aprueba sin editar le muestra las jornadas por revisar»);
`ProgramacionIT` («RF-121: el Programador envía, el Presidente la ve pendiente, la aprueba y se
publica con historial»).

### CA-3 · La devolución sin observaciones se rechaza; con observaciones vuelve a BORRADOR

> Dada una devolución, cuando no lleva observaciones, entonces se rechaza; con observaciones vuelve
> a BORRADOR.

**Pasos**

1. Como **Presidente**, con la **Jornada 1** en `REVISION`, abrir F-23 desde la bandeja y pulsar
   **Devolver con observaciones**.
2. **Sin observaciones:** dejar vacío **Observaciones para el Programador** y pulsar **Devolver con
   observaciones**. Repetir con `Revisar` (7 caracteres).
3. **Con observaciones:** escribir `Cambiar el partido de H13 Halcones a las 12:00.` y confirmar.
4. Como **Programador de prueba**, recargar **Programación › Jornadas** y abrir la Jornada 1.
5. Atender la observación: pulsar el lápiz del partido, cambiar solo el inicio a **28/11/2026
   12:00**, escribir el motivo `Ajuste solicitado por la Presidencia en la devolución.` y pulsar
   **Guardar cambios**.
6. Pulsar **Enviar a aprobación** y confirmar (reenvío, necesario para el CA-2).

**Resultado esperado**

- Paso 2: el diálogo no se envía; el campo queda marcado como inválido con «Escribe observaciones de
  al menos 10 caracteres…» y no hay petición a `/resolucion` (por API respondería
  `400 RF-121`). La jornada sigue en `REVISION`.
- Paso 3: mensaje «Jornada 1 devuelta a borrador. El Programador verá tus observaciones.»; F-23
  muestra «Esta jornada está en borrador con el Programador. Podrás dictaminarla cuando la envíe a
  aprobación.» y la jornada sale de **Jornadas por revisar**.
- Paso 4: la Jornada 1 vuelve a **Borrador**, con la nota **Observaciones de la Presidencia** y el
  texto de la devolución; el partido vuelve a ofrecer **Editar** y **Enviar a aprobación** está
  habilitado.
- Paso 5: «Partido ajustado. El cambio quedó en el historial y la auditoría.»; la verificación
  sigue en «Sin conflictos». Ajustar es posible porque la jornada volvió a `BORRADOR` (RF-090).
- Paso 6: «Jornada 1 enviada a aprobación. La Presidencia la revisará antes de publicarla.»; la
  jornada vuelve a `REVISION`.

**Resultado obtenido:** **Aprobado.** Al pulsar **Devolver con observaciones** con el campo vacío
(0/500), el diálogo marcó el campo en rojo con «Escribe observaciones de al menos 10 caracteres para
devolver la jornada.» y no se registró ningún dictamen. Con las observaciones «Cambiar el partido de
H13 Halcones a las 12:00.», la jornada volvió a **Borrador** (15:09). En F-22 el Programador vio el
aviso «La Presidencia devolvió esta jornada» con el texto de la devolución y «Corrige lo señalado y
vuelve a enviarla a aprobación.», el partido volvió a ofrecer **Editar** y **Enviar a aprobación**
quedó habilitado. El ajuste del inicio a 28/11/2026 12:00, con su motivo, mostró «Partido ajustado.
El cambio quedó en el historial y la auditoría.» y el partido pasó a 12:00 – 14:00; el reenvío dejó
la jornada otra vez en `REVISION` (15:21). Auditoría: `DEVOLVER_JORNADA` con las observaciones como
motivo (`estadoAnterior: REVISION`, `estado: BORRADOR`), `AJUSTAR_PARTIDO` (`campos: [inicio]`) con
su motivo y el segundo `ENVIAR_JORNADA_A_APROBACION`; `historial_publicacion` guarda
`REVISION → BORRADOR` con el mismo motivo.

**Evidencia:** `CA3-devolucion-sin-observaciones.png` (diálogo con el campo vacío),
`CA3-devuelta-borrador.png` (F-22 del Programador con la jornada devuelta),
`CA3-observaciones-programador.png` (diálogo **Ajustar partido** que atiende la observación),
`CA3-partido-ajustado.png`

**Pruebas automáticas relacionadas:** `JornadaServiceTest` («RF-121: devolver con observaciones
vuelve a BORRADOR, fija el motivo y lo audita», «RF-121: devolver sin observaciones se rechaza con
400 y la jornada no cambia»); `ProgramacionControllerTest` («RF-121: la resolución sin decisión
responde 400 sin llegar al servicio»); `PantallaRevisionJornada.test.tsx` («criterio 3: no devuelve
sin observaciones y con ellas vuelve a borrador»); `PantallaProgramacion.test.tsx` («muestra las
observaciones de una jornada devuelta»); `ProgramacionIT` («RF-121: devolver sin observaciones se
rechaza; con observaciones vuelve a BORRADOR y admite cambios»).

### CA-4 · Un usuario con permiso de editar la jornada no puede aprobarla

> Un usuario con permiso de editar la jornada no puede aprobarla.

Se ejecuta **con la jornada en `REVISION`**, justo después del CA-1 y antes del CA-3.

F-23 solo se abre desde la bandeja **Jornadas por revisar**, que ve quien puede aprobar **sin**
poder editar. El Programador y la cuenta de doble rol tienen `EDITAR`, así que en Programación ven
F-22 y no tienen ningún enlace a F-23. Por eso aquí se escribe la URL directamente: se comprueba que,
aunque alguien llegue a la pantalla, no puede dictaminar, ni en la interfaz ni en la API.

**Pasos**

1. **Programador:** como **Programador de prueba**, abrir la Jornada 1 en F-23
   (`/app/programacion/jornadas/0a2d8c1c-ac93-45d0-85df-9c925b13493a/revision`, el `id` está en la URL o en la pestaña **Red**).
2. **Doble rol, pantalla:** iniciar sesión como **Doble rol de prueba** y abrir la misma URL de F-23.
3. **Doble rol, API:** con las herramientas del navegador (pestaña **Red**), recargar F-23, clic
   derecho sobre la petición `GET /api/v1/jornadas/<id>` → **Copy → Copy as fetch**. Pegarla en la
   **Consola**, cambiar la URL por `/api/v1/jornadas/<id>/resolucion`, el método por `"POST"`, agregar
   el encabezado `"Content-Type": "application/json"` y el cuerpo `JSON.stringify({ decision:
   "PUBLICADO" })`, y al final `.then(r => r.json()).then(console.log)`.
4. Repetir el paso 3 en la sesión del **Programador de prueba**.
5. Comprobar en F-23 (como Presidente) que la jornada sigue en revisión.

**Resultado esperado**

- Paso 1: F-23 muestra la jornada en solo lectura, **sin** los botones **Aprobar y publicar** ni
  **Devolver con observaciones** (el rol Programador no tiene `APROBAR`).
- Paso 2: los botones **Aprobar y publicar** y **Devolver con observaciones** aparecen
  **deshabilitados** con «Tu cuenta también puede editar jornadas. Quien programa no puede aprobar ni
  devolver una jornada: el dictamen corresponde a otra persona.»
- Paso 3: la consola imprime `status: 403` y `codigo: "RF-121"` con «Tu cuenta también puede editar
  la programación; quien programa no puede aprobar la jornada.» (separación de funciones, RN-053).
- Paso 4: `status: 403` y `codigo: "VAL-015"` (sin `programacion.jornada:APROBAR`).
- Paso 5: la jornada sigue en `REVISION`; no hay `APROBAR_JORNADA` en la auditoría.

**Resultado obtenido:** **Aprobado.** El **Programador de pruebas** abrió F-23 por URL y vio la
jornada «En revisión», «Enviada por Programador de pruebas (tú)», el rol de juegos en solo lectura y
**ningún** botón de dictamen. La cuenta **Programador Presidente de pruebas** vio **Devolver con
observaciones** y **Aprobar y publicar** deshabilitados con «Tu cuenta también puede editar
jornadas. Quien programa no puede aprobar ni devolver una jornada: el dictamen corresponde a otra
persona.». Su `POST /api/v1/jornadas/0a2d8c1c-…/resolucion` con `decision: PUBLICADO` respondió
**403 (Forbidden)** con `type: https://siglim.mx/errores/rf-121`, título «Acción no permitida» y
«Tu cuenta también puede editar la programación; quien programa no puede aprobar la jornada.». La
jornada siguió en `REVISION` y la auditoría no tiene ningún `APROBAR_JORNADA` de esa cuenta: el
rechazo no escribe. El intento por API con la sesión del Programador (paso 4, `403 VAL-015`) no se
capturó; lo cubren `ProgramacionIT` y `JornadaServiceTest`. El token se ocultó en la captura.

**Evidencia:** `CA4-programador-sin-dictamen.png`, `CA4-doble-rol-bloqueado.png`,
`CA4-consola-403.png` (recortar el encabezado `Authorization` y cualquier correo antes de guardar la
captura)

**Pruebas automáticas relacionadas:** `JornadaServiceTest` («RF-121: un usuario con permiso de
editar la jornada no puede aprobarla (403)», «RF-121: quien envió o creó la jornada no puede
dictaminarla (403)», «VAL-015: sin permiso de aprobar no se lee ni se escribe la jornada»);
`ProgramacionControllerTest` («RF-121: el 403 de la separación de funciones conserva el código»);
`PantallaRevisionJornada.test.tsx` («criterio 4: quien también puede editar jornadas no puede
dictaminar», «no deja dictaminar a quien envió la jornada»); `ProgramacionIT` («RF-121: un usuario
que además puede editar la jornada no puede aprobarla, ni el Programador», «RF-121: la base respalda
la separación: quien envió no queda como aprobador»).

### Limpieza (no es criterio)

1. Como **Administrador general**, en F-12, **desactivar** la cuenta **Doble rol de prueba** con el
   motivo `Cuenta temporal de la prueba de aceptación H-13.` (no se borra, RN-005). La cuenta
   **Programador de prueba** puede quedarse para las siguientes pruebas de M-07, o desactivarse
   igual.
2. En **Administración → Temporadas**, pulsar **Cerrar** en **«Temporada prueba H-13»** (verificar el
   nombre) con el motivo `Cierre de la temporada de prueba de aceptación H-13.`
3. Comprobar que «Temporada 2026–2027» sigue en `CONFIGURACION`.

Ejecutada el 2026-11-17: `DESACTIVAR_CUENTA` de la cuenta de doble rol (15:34) y `CERRAR_TEMPORADA`
de **T** (15:35, `operacionesAplicadas: 0`), ambos con su motivo. La cuenta **Programador de
pruebas** sigue activa para las pruebas de M-07. «Temporada 2026–2027» sigue en `CONFIGURACION`, sin
cambios.

## 4. Defectos encontrados

| Clave | Severidad | Descripción | Estado |
| --- | --- | --- | --- |
| — | — | Ninguno | — |

## 5. Conclusión

H-13 cumple sus cuatro criterios de aceptación. El Programador envía a aprobación una jornada sin
conflictos y queda en `REVISION`, sin edición de partidos. El Presidente la ve en su bandeja, la
devuelve solo con observaciones suficientes y, tras el ajuste y el reenvío, la aprueba. Entonces
queda `PUBLICADO` y aparece en `v_publica_programacion`. Quien también puede editar la programación
no puede dictaminar, ni en la interfaz ni en la API (`403 RF-121`). Cada paso queda en la auditoría
y en `historial_publicacion`. No se encontraron defectos. La historia puede pasar a **Finalizado** al
fusionar este documento.

Observaciones (no son defectos):

- F-23 solo se abre desde la bandeja de quien aprueba sin editar. El Programador y la cuenta de doble
  rol llegan solo por URL; en ese caso el enlace «← Jornadas por revisar» lleva a F-22, donde no
  tienen bandeja.
- Al aprobar sin nota, `jornadas.observaciones` queda vacía y la jornada publicada ya no muestra las
  observaciones de la devolución anterior: el campo guarda la nota del último dictamen. La devolución
  se conserva en `historial_publicacion` y en la auditoría.
- El diálogo de aprobación dice «Los 1 partido de la jornada 1…»; falta concordar el singular.
- En el Supabase compartido quedan como datos de demostración la «Temporada prueba H-13»
  (`CERRADA`), su categoría y su grupo, los equipos H13 Halcones y H13 Toros, la Jornada 1
  `PUBLICADO` con su partido (visible en `v_publica_programacion`, que no filtra por estado de la
  temporada), la cuenta **Programador de pruebas** activa y la de doble rol desactivada.

## Orden de ejecución recomendado

Preparación → CA-1 → CA-4 → CA-3 (incluye el reenvío) → CA-2 → Limpieza.

El CA-4 va con la jornada en revisión y antes de la devolución; el CA-2 va al final porque publicar
es definitivo; el cierre de la temporada va en la limpieza porque es irreversible (RF-003) y libera
el lugar de temporada activa para la «Temporada 2026–2027».
